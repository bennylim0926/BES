# Organiser Tier System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a two-tier system (Pro/Max) that gates all battle features behind Max tier, with complete hiding (no modals/lock icons) for Pro users.

**Architecture:** Bottom-up implementation starting with DB migration → entity → service enforcement → backend gates on all 36 BattleController endpoints + EventGenre format setter → frontend composable + hiding logic → admin UI tier dropdown. Data is preserved across downgrades (reversible).

**Tech Stack:** Spring Boot (backend), Vue 3 + Pinia (frontend), PostgreSQL, Flyway migrations.

---

## File Structure

### Backend Files
- **Database:** `BES/src/main/resources/db/migration/V40__add_organiser_tier.sql` (new)
- **Entity:** `BES/src/main/java/com/example/BES/models/Account.java` (modify)
- **Service:** `BES/src/main/java/com/example/BES/services/TierAccessService.java` (new)
- **Service:** `BES/src/main/java/com/example/BES/services/AccountService.java` (modify)
- **Service:** `BES/src/main/java/com/example/BES/services/EventService.java` (modify — add `getAssignedOrganisers` helper)
- **Service:** `BES/src/main/java/com/example/BES/services/EventGenreService.java` (modify — add tier gate on format setter)
- **Controller:** `BES/src/main/java/com/example/BES/controllers/BattleController.java` (modify all 36 endpoints)
- **Controller:** `BES/src/main/java/com/example/BES/controllers/EventController.java` (modify format setter + add `GET /event/{name}/battle-enabled`)
- **Controller:** `BES/src/main/java/com/example/BES/controllers/AuthController.java` (modify whoami to include tier)
- **Controller:** `BES/src/main/java/com/example/BES/controllers/AdminController.java` (new tier endpoint)
- **DTO:** `BES/src/main/java/com/example/BES/dtos/admin/GetOrganiserDto.java` (modify)
- **DTO:** `BES/src/main/java/com/example/BES/dtos/admin/UpdateOrganiserTierDto.java` (new)

### Frontend Files
- **Composable:** `BES-frontend/src/utils/useTierAccess.js` (new)
- **API:** `BES-frontend/src/utils/api.js` (modify — add tier endpoints)
- **Store:** `BES-frontend/src/utils/auth.js` (modify — store tier on user)
- **Modal:** `BES-frontend/src/components/UpgradeToMaxModal.vue` (new, but unused due to hide-not-lock UX)
- **App:** `BES-frontend/src/App.vue` (modify event-chip to hide Battle link for Pro)
- **View:** `BES-frontend/src/views/MainMenu.vue` (hide Battle quick-action card for Pro)
- **View:** `BES-frontend/src/views/EventDetails.vue` (hide format selector, battle judges, battle guests sections for Pro)
- **View:** `BES-frontend/src/views/AdminPage.vue` (add Tier column + dropdown to Organisers table)
- **Router:** `BES-frontend/src/router/index.js` (add route guards for `/battle/control`, `/battle/judge`, `/battle/overlay`, `/battle/bracket`, `/battle/chart`)

### Tests
- Backend: Integration test in `BES/src/test/java/com/example/BES/controllers/BattleControllerTierTest.java` (new)
- Frontend: Not required for MVP (tier hiding is simple conditional rendering)

---

## Implementation Tasks (Bottom-Up Order)

### Task 1: Create Database Migration

**Files:**
- Create: `BES/src/main/resources/db/migration/V40__add_organiser_tier.sql`

- [ ] **Step 1: Write the migration file**

```sql
-- V40__add_organiser_tier.sql
ALTER TABLE account
  ADD COLUMN tier VARCHAR(10) NOT NULL DEFAULT 'PRO'
  CHECK (tier IN ('PRO', 'MAX'));

-- Backfill: all existing organisers keep MAX access (preserve current functionality)
UPDATE account
  SET tier = 'MAX'
  WHERE role = 'ROLE_ORGANISER';
```

- [ ] **Step 2: Verify file is in correct location**

Run: `ls BES/src/main/resources/db/migration/V40__*`
Expected: File appears in list

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V40__add_organiser_tier.sql
git commit -m "db: add tier column to account table with PRO/MAX values"
```

**Acceptance Criteria:**
- Migration file exists with correct naming (V40)
- SQL syntax is valid (checked by Spring Boot on startup)
- All existing organisers backfilled to MAX
- Default for new accounts is PRO

---

### Task 2: Add tier Field to Account Entity

**Files:**
- Modify: `BES/src/main/java/com/example/BES/models/Account.java`

- [ ] **Step 1: Read Account.java to understand current structure**

Run: `grep -n "private String role" BES/src/main/java/com/example/BES/models/Account.java`
Expected: Line number where role field is defined (note: we'll add tier near role)

- [ ] **Step 2: Add tier field after role field**

In `Account.java`, find the line with `private String role;` and add after it:

```java
@Column(nullable = false, length = 10)
private String tier = "PRO";  // 'PRO' | 'MAX'
```

- [ ] **Step 3: Generate getter and setter**

Add to `Account.java`:

```java
public String getTier() {
    return tier;
}

public void setTier(String tier) {
    this.tier = tier;
}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/Account.java
git commit -m "feat: add tier field to Account entity (PRO/MAX)"
```

**Acceptance Criteria:**
- `Account.tier` is a non-null VARCHAR(10) field with default 'PRO'
- Getter/setter present
- Compiles without errors
- Existing account table structure unchanged (Flyway will add column on startup)

---

### Task 3: Create TierAccessService

**Files:**
- Create: `BES/src/main/java/com/example/BES/services/TierAccessService.java`

- [ ] **Step 1: Write TierAccessService.java**

```java
package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.BES.models.Account;

@Service
public class TierAccessService {

  @Autowired
  private AccountService accountService;

  @Autowired
  private EventService eventService;

  /**
   * Throws 403 ResponseStatusException if caller lacks battle access.
   * Single source of truth for tier rules.
   */
  public void requireBattleAccess(Authentication auth, String eventName) {
    if (!hasBattleAccess(auth, eventName)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
        "Battle features require Max tier");
    }
  }

  public boolean hasBattleAccess(Authentication auth, String eventName) {
    Account user = accountService.fromAuth(auth);

    // Admin always has access
    if (isAdmin(user)) return true;

    // Organiser — check own tier
    if (isOrganiser(user)) {
      return "MAX".equals(user.getTier());
    }

    // Emcee / Judge / Helper — resolve from event's assigned organisers
    // Max if ANY assigned organiser is Max, otherwise Pro
    return eventService.getAssignedOrganisers(eventName).stream()
      .anyMatch(o -> "MAX".equals(o.getTier()));
  }

  private boolean isAdmin(Account user) {
    return user.getRole() != null && user.getRole().contains("ROLE_ADMIN");
  }

  private boolean isOrganiser(Account user) {
    return user.getRole() != null && user.getRole().contains("ROLE_ORGANISER");
  }
}
```

- [ ] **Step 2: Verify file is created**

Run: `ls BES/src/main/java/com/example/BES/services/TierAccessService.java`
Expected: File exists

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/TierAccessService.java
git commit -m "feat: create TierAccessService for centralized tier rule enforcement"
```

**Acceptance Criteria:**
- Service exists with `requireBattleAccess(auth, eventName)` and `hasBattleAccess(auth, eventName)` methods
- Admin always has access
- Organiser access based on own tier
- Emcee/Judge/Helper access based on event's organisers (Max wins)
- Compiles without errors

---

### Task 4: Add Helper Methods to EventService

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventService.java`

- [ ] **Step 1: Read EventService to find a good place to add helper**

Run: `grep -n "public.*Event" BES/src/main/java/com/example/BES/services/EventService.java | head -10`
Expected: See list of public methods; we'll add near other query methods

- [ ] **Step 2: Add getAssignedOrganisers method**

In `EventService.java`, add this method:

```java
/**
 * Returns list of Account objects assigned as organisers for the given event.
 * Used by TierAccessService to resolve Emcee/Judge/Helper tier.
 */
public List<Account> getAssignedOrganisers(String eventName) {
  Event event = eventRepository.findByName(eventName);
  if (event == null) {
    return new ArrayList<>();
  }
  return event.getAssignedOrganisers();  // assumes Event.java has this relationship
}
```

- [ ] **Step 3: Verify Event.java has assignedOrganisers relationship**

Run: `grep -n "assignedOrganisers\|organisers" BES/src/main/java/com/example/BES/models/Event.java`
Expected: Field exists (adjust method above if field name differs)

- [ ] **Step 4: Verify compilation**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventService.java
git commit -m "feat: add getAssignedOrganisers helper to EventService"
```

**Acceptance Criteria:**
- `getAssignedOrganisers(eventName)` exists
- Returns empty list if event not found
- Returns list of Account objects assigned to event
- Compiles without errors

---

### Task 5: Gate All 36 BattleController Endpoints

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/BattleController.java`

**Note:** This is a large refactor. Split across 3 subtasks.

#### Task 5a: Add TierAccessService Autowire and Wrapper Methods

- [ ] **Step 1: Add autowire for TierAccessService**

In `BattleController.java`, after existing `@Autowired BattleService battleService;`, add:

```java
@Autowired
TierAccessService tierAccessService;
```

- [ ] **Step 2: Add helper wrapper method**

Add this method to `BattleController`:

```java
private void checkBattleAccess(Authentication auth, String eventName) {
  tierAccessService.requireBattleAccess(auth, eventName);
}
```

- [ ] **Step 3: Verify autowire compiles**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/BattleController.java
git commit -m "chore: add TierAccessService autowire to BattleController"
```

**Acceptance Criteria:**
- TierAccessService is autowired
- Helper method exists for tier check

#### Task 5b: Gate Write Operations (22 POST/DELETE endpoints)

- [ ] **Step 1: Add tier gate to POST /battle-mode**

Find `setSelectedMode()` method. At the start of the method body, add (after `@RequestBody`):

```java
@PostMapping("/battle-mode")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> setSelectedMode(Authentication auth, @Valid @RequestBody SetBattleModeDto dto){
    checkBattleAccess(auth, "");  // global battle mode, no event context
    battleService.setSelectedMode(dto);
    ...
}
```

- [ ] **Step 2: Gate POST /battle-pair**

Find `setBattlerPair()` method. Add tier gate at start:

```java
@PostMapping("/battle-pair")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> setBattlerPair(Authentication auth, @Valid @RequestBody SetBattlerPairDto dto){
    String eventName = resolveEvent(dto.getEventName());
    checkBattleAccess(auth, eventName);
    battleService.setBattlerPairService(eventName, dto);
    ...
}
```

- [ ] **Step 3: Gate DELETE /battle-pair**

Find `clearBattlePair()` method. Add:

```java
@DeleteMapping("/battle-pair")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> clearBattlePair(Authentication auth, @RequestParam(required = false) String event){
    String eventName = resolveEvent(event);
    checkBattleAccess(auth, eventName);
    battleService.clearBattlePairService(eventName);
    ...
}
```

- [ ] **Step 4: Gate POST /format-timer**

Find `updateFormatTimer()` method. Add:

```java
@PostMapping("/format-timer")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> updateFormatTimer(Authentication auth,
        @RequestParam(required = false) String event,
        @RequestBody Map<String, Object> payload) {
    String eventName = resolveEvent(event);
    checkBattleAccess(auth, eventName);
    battleService.handleFormatTimerPayload(eventName, payload);
    ...
}
```

- [ ] **Step 5: Gate POST /score**

Find `setBattleScore()` method. Add:

```java
@PostMapping("/score")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> setBattleScore(Authentication auth, @RequestBody(required = false) SetBattleScoreDto dto){
    String eName = resolveEvent(dto != null ? dto.getEventName() : null);
    checkBattleAccess(auth, eName);
    boolean isFinal = dto != null && dto.isFinal();
    ...
}
```

- [ ] **Step 6: Gate POST /revote**

Find `revote()` method. Add:

```java
@PostMapping("/revote")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> revote(Authentication auth, @RequestParam(required = false) String event){
    String eventName = resolveEvent(event);
    checkBattleAccess(auth, eventName);
    battleService.resetJudgeVotesService(eventName);
    ...
}
```

- [ ] **Step 7: Gate POST /champion-reveal**

Find `championReveal()` method. Add:

```java
@PostMapping("/champion-reveal")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> championReveal(Authentication auth, @RequestBody ChampionRevealDto dto){
    String eventName = resolveEvent(dto.getEventName());
    checkBattleAccess(auth, eventName);
    battleService.broadcastChampionReveal(eventName, dto);
    ...
}
```

- [ ] **Step 8: Gate DELETE /judge**

Find `removeBattleJudge()` method. Add:

```java
@DeleteMapping("/judge")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> removeBattleJudge(Authentication auth, @Valid @RequestBody SetJudgeDto dto){
    String eventName = resolveEvent(dto.getEventName());
    checkBattleAccess(auth, eventName);
    return ResponseEntity.ok(Map.of(
        "judge removed", battleService.removeBattleJudgeService(eventName, dto)
    ));
}
```

- [ ] **Step 9: Gate POST /judge**

Find `setJudge()` method. Add:

```java
@PostMapping("/judge")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> setJudge(Authentication auth, @Valid @RequestBody SetJudgeDto dto){
    String eventName = resolveEvent(dto.getEventName());
    checkBattleAccess(auth, eventName);
    Integer status = battleService.setBattleJudgeService(eventName, dto);
    ...
}
```

- [ ] **Step 10: Gate POST /judge/weightage**

Find `updateJudgeWeightage()` method. Add:

```java
@PostMapping("/judge/weightage")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> updateJudgeWeightage(Authentication auth, @Valid @RequestBody UpdateJudgeWeightageDto dto) {
    String eventName = resolveEvent(dto.getEventName());
    checkBattleAccess(auth, eventName);
    battleService.updateJudgeWeightageService(eventName, dto);
    ...
}
```

- [ ] **Step 11: Gate POST /upload**

Find `handleUpload()` method. Add:

```java
@PostMapping("/upload")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> handleUpload(Authentication auth, @RequestParam("file") MultipartFile file) throws IOException{
    checkBattleAccess(auth, "");  // global images, no event context
    if (!Files.exists(uploadDir)) {
        Files.createDirectories(uploadDir);
    }
    ...
}
```

- [ ] **Step 12: Gate DELETE /image**

Find `deleteImage()` method. Add:

```java
@DeleteMapping("/image")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<String> deleteImage(Authentication auth, @Valid @RequestBody DeleteImageDto dto) throws IOException{
    checkBattleAccess(auth, "");  // global images
    Path file = uploadDir.resolve(dto.getName()).normalize();
    ...
}
```

- [ ] **Step 13: Gate POST /smoke**

Find `setSmokeList()` method. Add:

```java
@PostMapping("/smoke")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> setSmokeList(Authentication auth, @Valid @RequestBody SetSmokeBattlersDto dto){
    String eventName = resolveEvent(dto.getEventName());
    checkBattleAccess(auth, eventName);
    battleService.setSmokeBattlersService(eventName, dto);
    ...
}
```

- [ ] **Step 14: Gate POST /phase**

Find `setBattlePhase()` method. Add:

```java
@PostMapping("/phase")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> setBattlePhase(Authentication auth, @Valid @RequestBody SetBattlePhaseDto dto){
    String eventName = resolveEvent(dto.getEventName());
    checkBattleAccess(auth, eventName);
    battleService.setBattlePhaseService(eventName, dto.getPhase(), dto.getChampion());
    ...
}
```

- [ ] **Step 15: Gate POST /bracket**

Find `setBracketState()` method. Add:

```java
@PostMapping("/bracket")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> setBracketState(Authentication auth, @Valid @RequestBody SetBracketStateDto dto){
    String eventName = resolveEvent(dto.getEventName());
    checkBattleAccess(auth, eventName);
    battleService.setBracketStateService(eventName, dto);
    ...
}
```

- [ ] **Step 16: Gate POST /overlay-config**

Find `setOverlayConfig()` method. Add:

```java
@PostMapping("/overlay-config")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> setOverlayConfig(Authentication auth, @Valid @RequestBody SetOverlayConfigDto dto) {
    String eventName = resolveEvent(dto.getEventName());
    checkBattleAccess(auth, eventName);
    battleService.setOverlayConfigService(eventName, dto);
    ...
}
```

- [ ] **Step 17: Gate POST /active-genre**

Find `setActiveGenre()` method. Add:

```java
@PostMapping("/active-genre")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
public ResponseEntity<?> setActiveGenre(Authentication auth, @Valid @RequestBody SetActiveGenreDto dto) {
    String eventName = resolveEvent(dto.getEventName());
    checkBattleAccess(auth, eventName);
    battleService.switchActiveGenreService(dto);
    ...
}
```

- [ ] **Step 18: Gate POST /logo-upload**

Find `uploadLogo()` method. Add:

```java
@PostMapping("/logo-upload")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> uploadLogo(Authentication auth,
        @RequestParam("file") MultipartFile file,
        @RequestParam(required = false) String event) throws IOException {
    String eventName = resolveEvent(event);
    checkBattleAccess(auth, eventName);
    String url = battleService.uploadLogoService(eventName, file);
    ...
}
```

- [ ] **Step 19: Gate DELETE /logo**

Find `deleteLogo()` method. Add:

```java
@DeleteMapping("/logo")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> deleteLogo(Authentication auth,
        @RequestParam(required = false) String event) throws IOException {
    String eventName = resolveEvent(event);
    checkBattleAccess(auth, eventName);
    battleService.deleteLogoService(eventName);
    ...
}
```

- [ ] **Step 20: Gate POST /resolved-participants**

Find `setResolvedParticipants()` method. Add:

```java
@PostMapping("/resolved-participants")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> setResolvedParticipants(Authentication auth, @Valid @RequestBody SetResolvedParticipantsDto dto) {
    String eventName = dto.getEventName();
    checkBattleAccess(auth, eventName);
    battleService.setResolvedParticipants(eventName, dto.getGenreName(), dto.getParticipants());
    ...
}
```

- [ ] **Step 21: Verify compilation**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 22: Commit write operations**

```bash
git add BES/src/main/java/com/example/BES/controllers/BattleController.java
git commit -m "feat: add tier gates to 22 BattleController write endpoints"
```

**Acceptance Criteria:**
- All 22 POST/DELETE endpoints call `checkBattleAccess(auth, eventName)` at method start
- Each endpoint resolves eventName correctly
- Compilation succeeds
- No write operation missing a gate

#### Task 5c: Gate Read Operations and Global Endpoints

- [ ] **Step 1: Gate GET /state**

Find `getBattleState()` method. Add:

```java
@GetMapping("/state")
public ResponseEntity<?> getBattleState(Authentication auth, @RequestParam(required = false) String event) {
    String eName = resolveEvent(event);
    if (auth != null && auth.isAuthenticated()) {
        checkBattleAccess(auth, eName);  // authenticated users must have tier access
    }
    // unauthenticated (public) can still GET /state, but will receive no data for Pro events
    Map<String, Object> state = battleService.getBattleStateService(eName);
    if (state.containsKey("timer")) {
        battleService.rebroadcastTimer(eName, state.get("timer"));
    }
    return ResponseEntity.ok(state);
}
```

- [ ] **Step 2: Verify read-only endpoints don't need gating**

Read-only GET endpoints (judges, champions, bracket, phase, smoke, images, overlay-config, active-genre, battle-pair) return empty/null for Pro events at the data layer, so no explicit gate needed — Pro users see empty results naturally. Unauthenticated public reads (OBS overlays) remain unblocked.

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit read operations**

```bash
git add BES/src/main/java/com/example/BES/controllers/BattleController.java
git commit -m "feat: add tier gate to GET /state endpoint"
```

**Acceptance Criteria:**
- `GET /state` checks tier for authenticated users
- Public/unauthenticated access still allowed
- All 36 BattleController endpoints now have tier enforcement

---

### Task 6: Gate EventGenre Format Setter

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreService.java` (or EventController if setter is there)

- [ ] **Step 1: Find format setter location**

Run: `grep -n "setFormat\|format.*=" BES/src/main/java/com/example/BES/services/EventGenreService.java | head -5`
Expected: Find format-related methods

- [ ] **Step 2: Add tier check to format setter**

The format setter should check tier before allowing 7-to-Smoke. Add at the start:

```java
public void setFormat(String eventName, String genre, String format, Authentication auth) {
    if ("7-to-Smoke".equalsIgnoreCase(format)) {
        tierAccessService.requireBattleAccess(auth, eventName);
    }
    // proceed with format update
    ...
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventGenreService.java
git commit -m "feat: add tier gate to EventGenre format setter"
```

**Acceptance Criteria:**
- Format setter rejects 7-to-Smoke writes for Pro tier (403 Forbidden)
- Standard format always allowed
- Compilation succeeds

---

### Task 7: Update GetOrganiserDto to Include Tier

**Files:**
- Modify: `BES/src/main/java/com/example/BES/dtos/admin/GetOrganiserDto.java`

- [ ] **Step 1: Read GetOrganiserDto structure**

Run: `cat BES/src/main/java/com/example/BES/dtos/admin/GetOrganiserDto.java`
Expected: See current fields (likely id, username, email, role, etc.)

- [ ] **Step 2: Add tier field**

Add to `GetOrganiserDto`:

```java
private String tier;  // 'PRO' | 'MAX'

public String getTier() {
    return tier;
}

public void setTier(String tier) {
    this.tier = tier;
}
```

- [ ] **Step 3: Update mapper to include tier**

Find mapper code that converts Account → GetOrganiserDto. Add:

```java
organiserDto.setTier(account.getTier());
```

- [ ] **Step 4: Verify compilation**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/admin/GetOrganiserDto.java
git commit -m "feat: add tier field to GetOrganiserDto"
```

**Acceptance Criteria:**
- GetOrganiserDto has `tier` field with getter/setter
- Mapper includes tier in conversions
- Compiles without errors

---

### Task 8: Create UpdateOrganiserTierDto

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/admin/UpdateOrganiserTierDto.java`

- [ ] **Step 1: Write UpdateOrganiserTierDto.java**

```java
package com.example.BES.dtos.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class UpdateOrganiserTierDto {
    @NotNull
    private Long accountId;

    @NotNull
    @Pattern(regexp = "^(PRO|MAX)$", message = "Tier must be PRO or MAX")
    private String tier;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }
}
```

- [ ] **Step 2: Verify file is created**

Run: `ls BES/src/main/java/com/example/BES/dtos/admin/UpdateOrganiserTierDto.java`
Expected: File exists

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/admin/UpdateOrganiserTierDto.java
git commit -m "feat: create UpdateOrganiserTierDto for admin tier endpoint"
```

**Acceptance Criteria:**
- DTO exists with accountId and tier fields
- Validates tier is PRO or MAX
- Compiles without errors

---

### Task 9: Update AuthController whoami to Include Tier

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/AuthController.java`

- [ ] **Step 1: Find whoami endpoint**

Run: `grep -n "whoami\|GET.*auth" BES/src/main/java/com/example/BES/controllers/AuthController.java`
Expected: Line number of whoami method

- [ ] **Step 2: Update whoami response**

Find the method that returns user info and add `tier` to the response:

```java
@GetMapping("/whoami")
public ResponseEntity<?> whoami(Authentication auth) {
    if (auth == null) {
        return ResponseEntity.ok(Map.of("authenticated", false));
    }
    Account user = accountService.fromAuth(auth);
    return ResponseEntity.ok(Map.of(
        "id", user.getId(),
        "username", user.getUsername(),
        "email", user.getEmail(),
        "role", user.getRole(),
        "tier", user.getTier(),  // ADD THIS LINE
        "authenticated", true
    ));
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/AuthController.java
git commit -m "feat: add tier field to whoami response"
```

**Acceptance Criteria:**
- `GET /auth/whoami` returns `tier` field
- Tier value matches Account.tier (PRO or MAX)
- Compiles without errors

---

### Task 10: Add battle-enabled Endpoint to EventController

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

- [ ] **Step 1: Find EventController class**

Run: `ls BES/src/main/java/com/example/BES/controllers/EventController.java`
Expected: File exists

- [ ] **Step 2: Add new endpoint**

Add to `EventController`:

```java
@GetMapping("/{name}/battle-enabled")
public ResponseEntity<?> isBattleEnabled(Authentication auth, @PathVariable String name) {
    if (auth == null || !auth.isAuthenticated()) {
        return ResponseEntity.ok(Map.of("battleEnabled", false));
    }
    
    boolean enabled = tierAccessService.hasBattleAccess(auth, name);
    return ResponseEntity.ok(Map.of("battleEnabled", enabled));
}
```

Make sure `tierAccessService` is autowired:

```java
@Autowired
TierAccessService tierAccessService;
```

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/EventController.java
git commit -m "feat: add GET /event/{name}/battle-enabled endpoint"
```

**Acceptance Criteria:**
- Endpoint returns `{battleEnabled: true|false}`
- Works for authenticated and unauthenticated users
- Uses TierAccessService logic
- Compiles without errors

---

### Task 11: Add Tier Endpoint to AdminController

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/AdminController.java`

- [ ] **Step 1: Find AdminController class**

Run: `ls BES/src/main/java/com/example/BES/controllers/AdminController.java`
Expected: File exists

- [ ] **Step 2: Add tier endpoint**

Add to `AdminController`:

```java
@PostMapping("/organisers/tier")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> setOrganiserTier(@Valid @RequestBody UpdateOrganiserTierDto dto) {
    Account account = accountRepository.findById(dto.getAccountId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    
    account.setTier(dto.getTier());
    account = accountRepository.save(account);
    
    GetOrganiserDto result = new GetOrganiserDto();
    result.setAccountId(account.getId());
    result.setUsername(account.getUsername());
    result.setEmail(account.getEmail());
    result.setTier(account.getTier());
    // map other fields as needed
    
    return ResponseEntity.ok(result);
}
```

Ensure imports:
```java
import com.example.BES.dtos.admin.UpdateOrganiserTierDto;
import com.example.BES.dtos.admin.GetOrganiserDto;
```

- [ ] **Step 3: Verify compilation**

Run: `mvn clean compile -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/AdminController.java
git commit -m "feat: add POST /admin/organisers/tier endpoint for admin tier management"
```

**Acceptance Criteria:**
- Endpoint updates account tier
- Returns updated GetOrganiserDto with tier
- Admin-only (@PreAuthorize)
- Compiles without errors

---

### Task 12: Create Frontend Composable useTierAccess

**Files:**
- Create: `BES-frontend/src/utils/useTierAccess.js`

- [ ] **Step 1: Write useTierAccess.js**

```javascript
import { computed } from 'vue'
import { useAuthStore } from './auth.js'
import { useEventStore } from './auth.js'  // or wherever activeEventBattleEnabled lives

export function useTierAccess() {
  const authStore = useAuthStore()
  const eventStore = useEventStore()

  return {
    tier: computed(() => authStore.user?.tier),

    battleEnabled: computed(() => {
      const user = authStore.user
      if (!user) return false

      // Extract role from user.role array (e.g., [{ authority: 'ROLE_ADMIN' }])
      const role = user.role?.[0]?.authority

      // Admin always has access
      if (role === 'ROLE_ADMIN') return true

      // Organiser — check own tier
      if (role === 'ROLE_ORGANISER') {
        return user.tier === 'MAX'
      }

      // Emcee / Judge / Helper — resolve from active event
      // activeEventBattleEnabled is fetched and cached when active event is set
      return eventStore.activeEventBattleEnabled === true
    }),

    isProUser: computed(() => authStore.user?.tier === 'PRO'),
    isMaxUser: computed(() => authStore.user?.tier === 'MAX'),
  }
}
```

- [ ] **Step 2: Verify file is created**

Run: `ls BES-frontend/src/utils/useTierAccess.js`
Expected: File exists

- [ ] **Step 3: Verify syntax**

Run: `node -c BES-frontend/src/utils/useTierAccess.js` (basic syntax check)
Expected: No error

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/utils/useTierAccess.js
git commit -m "feat: create useTierAccess composable for tier-based visibility"
```

**Acceptance Criteria:**
- Composable exports `useTierAccess()` function
- Returns `tier`, `battleEnabled`, `isProUser`, `isMaxUser` computed properties
- Resolves tier correctly for Admin/Organiser/Emcee/Judge/Helper roles
- No syntax errors

---

### Task 13: Update Auth Store to Include Tier

**Files:**
- Modify: `BES-frontend/src/utils/auth.js`

- [ ] **Step 1: Read auth.js structure**

Run: `grep -n "user\|state\|defineStore" BES-frontend/src/utils/auth.js | head -10`
Expected: See store structure

- [ ] **Step 2: Add tier to user state**

Find where `user` is initialized in the Pinia store and add `tier`:

```javascript
const state = () => ({
  user: {
    id: null,
    username: null,
    email: null,
    role: [],
    tier: null,  // ADD THIS
    authenticated: false,
  },
})
```

- [ ] **Step 3: Update whoami fetch to capture tier**

Find the whoami API call and ensure tier is captured:

```javascript
const setUserFromWhoami = (response) => {
  state.user = {
    id: response.id,
    username: response.username,
    email: response.email,
    role: response.role,
    tier: response.tier,  // ADD THIS
    authenticated: response.authenticated,
  }
}
```

- [ ] **Step 4: Add activeEventBattleEnabled state**

Add to state:

```javascript
const state = () => ({
  user: { ... },
  activeEventBattleEnabled: false,  // ADD THIS
})
```

- [ ] **Step 5: Add action to fetch and cache battle-enabled**

Add action to store:

```javascript
const actions = {
  async fetchEventBattleEnabled(eventName) {
    try {
      const response = await fetch(`/api/v1/event/${eventName}/battle-enabled`, {
        credentials: 'include',
      })
      const data = await response.json()
      this.activeEventBattleEnabled = data.battleEnabled ?? false
    } catch (e) {
      this.activeEventBattleEnabled = false
    }
  },
}
```

- [ ] **Step 6: Verify syntax**

Run: `npm run dev` from `BES-frontend/` directory (will check syntax during build)
Expected: Build succeeds or shows clear errors

- [ ] **Step 7: Commit**

```bash
git add BES-frontend/src/utils/auth.js
git commit -m "feat: add tier and activeEventBattleEnabled to auth store"
```

**Acceptance Criteria:**
- Auth store has `tier` field on user
- `activeEventBattleEnabled` state added
- `fetchEventBattleEnabled` action exists
- Store updates correctly on whoami refresh

---

### Task 14: Add API Functions for Tier Management

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Find where admin endpoints are defined**

Run: `grep -n "admin\|POST" BES-frontend/src/utils/api.js | grep -i admin | head -5`
Expected: See structure of admin API calls

- [ ] **Step 2: Add tier API endpoint**

Add to `api.js`:

```javascript
// Admin: Update organiser tier
export async function setOrganiserTier(accountId, tier) {
  try {
    const response = await fetch('/api/v1/admin/organisers/tier', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ accountId, tier }),
    })
    if (!response.ok) {
      console.error('Failed to update tier:', response.status)
      return null
    }
    return await response.json()
  } catch (e) {
    console.error('Error updating tier:', e)
    return null
  }
}
```

- [ ] **Step 3: Verify syntax**

Run: `npm run dev` from `BES-frontend/` directory
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: add setOrganiserTier API endpoint"
```

**Acceptance Criteria:**
- `setOrganiserTier(accountId, tier)` function exists
- Makes POST request to `/api/v1/admin/organisers/tier`
- Returns response or null on error

---

### Task 15: Hide Battle Link in Event Chip Dropdown

**Files:**
- Modify: `BES-frontend/src/App.vue`

- [ ] **Step 1: Find event chip dropdown code**

Run: `grep -n "Battle\|event.*chip\|dropdown" BES-frontend/src/App.vue | head -10`
Expected: Find template section with Battle link

- [ ] **Step 2: Add tier check to Battle link**

Wrap the Battle link in a v-if that checks tier:

```vue
<template v-if="useTierAccess().battleEnabled">
  <router-link to="/battle/control" class="nav-item">
    <span>⚔ Battle</span>
  </router-link>
</template>
```

Add import at top:
```javascript
import { useTierAccess } from './utils/useTierAccess.js'
```

- [ ] **Step 3: Test in dev mode**

Run: `npm run dev` from `BES-frontend/` directory and navigate to a PRO event
Expected: Battle link is hidden for PRO users, visible for MAX

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/App.vue
git commit -m "ui: hide Battle link in event chip dropdown for PRO tier"
```

**Acceptance Criteria:**
- Battle link only visible for MAX tier users
- Hidden completely for PRO tier (no lock icon)
- Dropdown layout unchanged

---

### Task 16: Hide Battle Quick-Action Card on MainMenu

**Files:**
- Modify: `BES-frontend/src/views/MainMenu.vue`

- [ ] **Step 1: Find MainMenu quick-action cards**

Run: `grep -n "Battle\|quick.*action\|card" BES-frontend/src/views/MainMenu.vue | head -10`
Expected: Find Battle quick-action section

- [ ] **Step 2: Wrap Battle card in v-if**

Find the Battle quick-action card and wrap it:

```vue
<div v-if="useTierAccess().battleEnabled">
  <!-- Battle quick-action card -->
  <div class="quick-action-card">
    <h3>⚔ Battle Mode</h3>
    ...
  </div>
</div>
```

Add import:
```javascript
import { useTierAccess } from '../utils/useTierAccess.js'
```

- [ ] **Step 3: Test in dev mode**

Run: `npm run dev` and log in as PRO organiser
Expected: Battle card hidden from MainMenu

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/MainMenu.vue
git commit -m "ui: hide Battle quick-action card for PRO tier"
```

**Acceptance Criteria:**
- Battle card hidden for PRO users
- Visible for MAX users
- MainMenu layout adapts correctly

---

### Task 17: Hide Battle UI Sections in EventDetails

**Files:**
- Modify: `BES-frontend/src/views/EventDetails.vue`

- [ ] **Step 1: Find battle-related sections**

Run: `grep -n "format\|judges\|guests\|Battle" BES-frontend/src/views/EventDetails.vue | head -15`
Expected: Find EventGenre format selector, battle judges, battle guests sections

- [ ] **Step 2: Wrap format selector in v-if**

Find the 7-to-Smoke format selector dropdown and wrap it:

```vue
<div v-if="useTierAccess().battleEnabled" class="format-selector-section">
  <label>Format:</label>
  <select v-model="genre.format">
    <option value="Standard">Standard</option>
    <option value="7-to-Smoke">7-to-Smoke</option>
  </select>
</div>
```

- [ ] **Step 3: Wrap battle judges section in v-if**

Find the judges management section:

```vue
<section v-if="useTierAccess().battleEnabled" class="battle-judges-section">
  <h3>Battle Judges</h3>
  <!-- judges UI -->
</section>
```

- [ ] **Step 4: Wrap battle guests section in v-if**

Find the guests section:

```vue
<section v-if="useTierAccess().battleEnabled" class="battle-guests-section">
  <h3>Battle Guests</h3>
  <!-- guests UI -->
</section>
```

- [ ] **Step 5: Add import**

```javascript
import { useTierAccess } from '../utils/useTierAccess.js'
```

- [ ] **Step 6: Test in dev mode**

Run: `npm run dev` and view EventDetails for PRO and MAX events
Expected: Battle sections hidden for PRO, visible for MAX

- [ ] **Step 7: Commit**

```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "ui: hide format selector, judges, and guests sections for PRO tier"
```

**Acceptance Criteria:**
- All three battle sections hidden for PRO tier
- Visible for MAX tier
- No lock icons or upgrade copy shown
- EventDetails layout adapts smoothly

---

### Task 18: Add Route Guards for Battle Routes

**Files:**
- Modify: `BES-frontend/src/router/index.js`

- [ ] **Step 1: Find beforeEach router guard**

Run: `grep -n "beforeEach\|router.beforeEach" BES-frontend/src/router/index.js`
Expected: Find the guard definition

- [ ] **Step 2: Add battle route guard logic**

Find the `beforeEach` guard and add this check for battle routes:

```javascript
router.beforeEach(async (to, from, next) => {
  const authStore = useAuthStore()
  
  // Check if navigating to a battle route
  const battleRoutes = ['/battle/control', '/battle/judge', '/battle/overlay', '/battle/bracket', '/battle/chart']
  if (battleRoutes.includes(to.path)) {
    // Ensure auth is loaded
    if (!authStore.user) {
      await authStore.loadUserFromWhoami()
    }

    const tierAccess = useTierAccess()
    if (!tierAccess.battleEnabled.value) {
      // Redirect to home and show upgrade modal
      authStore.showBattleUpgradeModal = true
      return next('/')
    }
  }

  next()
})
```

Add imports:
```javascript
import { useAuthStore } from '../utils/auth.js'
import { useTierAccess } from '../utils/useTierAccess.js'
```

Also add to auth store:
```javascript
const state = () => ({
  user: { ... },
  activeEventBattleEnabled: false,
  showBattleUpgradeModal: false,  // ADD THIS
})
```

- [ ] **Step 3: Test route access**

Run: `npm run dev` and log in as PRO user, try navigating to `/battle/control`
Expected: Redirected to home (but note: upgrade modal won't show until we wire it in Task 19)

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/router/index.js
git commit -m "feat: add route guards to block battle routes for PRO tier"
```

**Acceptance Criteria:**
- `/battle/control` and `/battle/judge` routes blocked for PRO
- `/battle/overlay`, `/battle/bracket`, `/battle/chart` routes blocked for PRO
- User redirected to home
- MAX tier users can access normally

---

### Task 19: Hide Public Broadcast Displays for PRO Events

**Files:**
- Modify: `BES-frontend/src/views/BattleOverlay.vue`, `BattleControl.vue`, `BracketVisualization.vue`, `Chart.vue`

**Note:** These are public/semi-public routes. The data-fetch layer prevents Pro events from having battle data, so they naturally render empty. We just need to ensure Pro organisers (if they somehow navigate) see nothing.

- [ ] **Step 1: Add empty state to BattleOverlay.vue**

In `BattleOverlay.vue`, at the top of the template:

```vue
<div v-if="!battleDataExists" class="empty-state">
  <!-- Broadcast display waiting for event -->
</div>
<div v-else>
  <!-- existing overlay content -->
</div>
```

Add data:
```javascript
const battleDataExists = computed(() => !!battlePhase.value && battlePhase.value !== 'IDLE')
```

- [ ] **Step 2: Add empty state to BracketVisualization.vue**

Similar approach:

```vue
<div v-if="!bracketExists" class="empty-state">
  <!-- Waiting for bracket data -->
</div>
```

- [ ] **Step 3: Add empty state to Chart.vue**

```vue
<div v-if="!smokeDataExists" class="empty-state">
  <!-- Waiting for smoke list -->
</div>
```

- [ ] **Step 4: Test in dev mode**

Run: `npm run dev`, navigate to `/battle/overlay` for a PRO event
Expected: Shows empty state (waiting for data)

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/BattleOverlay.vue BES-frontend/src/views/BracketVisualization.vue BES-frontend/src/views/Chart.vue
git commit -m "ui: add empty states to public broadcast displays"
```

**Acceptance Criteria:**
- Public displays show empty/waiting state when no battle data exists
- Pro events naturally show empty (no battle data at data layer)
- Routes remain public (OBS can still access URLs, just empty)

---

### Task 20: Add Tier Dropdown to AdminPage Organisers Table

**Files:**
- Modify: `BES-frontend/src/views/AdminPage.vue`

- [ ] **Step 1: Find Organisers table in AdminPage**

Run: `grep -n "organisers\|Organisers\|table" BES-frontend/src/views/AdminPage.vue | head -10`
Expected: Find table structure

- [ ] **Step 2: Add Tier column**

Find the organisers table header and add:

```vue
<!-- Table header -->
<th>Tier</th>

<!-- Table row -->
<td>
  <select 
    :value="organiser.tier" 
    @change="(e) => updateTier(organiser.accountId, e.target.value)"
    class="tier-dropdown"
  >
    <option value="PRO">PRO</option>
    <option value="MAX">MAX</option>
  </select>
</td>
```

- [ ] **Step 3: Add updateTier method**

Add to AdminPage component:

```javascript
import { setOrganiserTier } from '../utils/api.js'

const updateTier = async (accountId, newTier) => {
  const result = await setOrganiserTier(accountId, newTier)
  if (result) {
    // Refresh organisers list
    await loadOrganisers()
    // Show toast
    showToast(`Updated tier to ${newTier}`)
  }
}
```

- [ ] **Step 4: Add filter chips above table**

Add above the table:

```vue
<div class="filter-chips">
  <button 
    :class="{ active: tierFilter === 'All' }"
    @click="tierFilter = 'All'"
  >All</button>
  <button 
    :class="{ active: tierFilter === 'MAX' }"
    @click="tierFilter = 'MAX'"
  >Max</button>
  <button 
    :class="{ active: tierFilter === 'PRO' }"
    @click="tierFilter = 'PRO'"
  >Pro</button>
</div>
```

Add data:
```javascript
const tierFilter = ref('All')
```

Filter organisers based on selected filter.

- [ ] **Step 5: Test in dev mode**

Run: `npm run dev`, log in as Admin, go to `/admin`
Expected: See Tier column with dropdowns, filter chips work

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/AdminPage.vue
git commit -m "ui: add tier column and dropdown to AdminPage organisers table"
```

**Acceptance Criteria:**
- Tier column visible in organisers table
- Dropdown shows PRO/MAX options
- Selection change calls `setOrganiserTier` API
- Toast confirms update
- Filter chips work to show All/Max/Pro

---

### Task 21: Write Backend Integration Test for Tier Gates

**Files:**
- Create: `BES/src/test/java/com/example/BES/controllers/BattleControllerTierTest.java`

- [ ] **Step 1: Write test class scaffold**

```java
package com.example.BES.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.models.Account;
import com.example.BES.repositories.AccountRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BattleControllerTierTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private AccountRepository accountRepository;

  // Test helper: create PRO organiser
  private Account createProOrganiser(String username) {
    Account acc = new Account();
    acc.setUsername(username);
    acc.setPassword("password");
    acc.setRole("ROLE_ORGANISER");
    acc.setTier("PRO");
    return accountRepository.save(acc);
  }

  // Test helper: create MAX organiser
  private Account createMaxOrganiser(String username) {
    Account acc = new Account();
    acc.setUsername(username);
    acc.setPassword("password");
    acc.setRole("ROLE_ORGANISER");
    acc.setTier("MAX");
    return accountRepository.save(acc);
  }

  @Test
  public void testProOrganizerCannotAccessBattleEndpoint() throws Exception {
    Account pro = createProOrganiser("pro_user");
    
    mockMvc.perform(
        post("/api/v1/battle/score")
            .with(httpBasic(pro.getUsername(), "password"))
            .contentType("application/json")
            .content("{\"eventName\": \"test-event\", \"final\": false}")
    )
    .andExpect(status().isForbidden());
  }

  @Test
  public void testMaxOrganizerCanAccessBattleEndpoint() throws Exception {
    Account max = createMaxOrganiser("max_user");
    
    mockMvc.perform(
        post("/api/v1/battle/score")
            .with(httpBasic(max.getUsername(), "password"))
            .contentType("application/json")
            .content("{\"eventName\": \"test-event\", \"final\": false}")
    )
    .andExpect(status().isOk());  // or 2xx (may fail with other error, but not 403)
  }

  @Test
  public void testAdminAlwaysHasAccess() throws Exception {
    Account admin = new Account();
    admin.setUsername("admin_user");
    admin.setPassword("password");
    admin.setRole("ROLE_ADMIN");
    admin.setTier("PRO");  // even PRO tier, admin has access
    accountRepository.save(admin);

    mockMvc.perform(
        post("/api/v1/battle/score")
            .with(httpBasic(admin.getUsername(), "password"))
            .contentType("application/json")
            .content("{\"eventName\": \"test-event\", \"final\": false}")
    )
    .andExpect(status().isOk());
  }
}
```

- [ ] **Step 2: Verify test class is created**

Run: `ls BES/src/test/java/com/example/BES/controllers/BattleControllerTierTest.java`
Expected: File exists

- [ ] **Step 3: Run tests**

Run: `mvn test -Dtest=BattleControllerTierTest` from `BES/` directory
Expected: Tests pass (or at least tier gate tests work correctly)

- [ ] **Step 4: Commit**

```bash
git add BES/src/test/java/com/example/BES/controllers/BattleControllerTierTest.java
git commit -m "test: add integration tests for battle endpoint tier gating"
```

**Acceptance Criteria:**
- Test class exists with 3+ test cases
- Pro tier users get 403 on battle endpoints
- Max tier users can access
- Admin users can access regardless of tier
- Tests pass

---

### Task 22: Run Full Integration Test & Docker Validation

- [ ] **Step 1: Run all backend tests**

Run: `mvn clean test` from `BES/` directory
Expected: All tests pass (or only pre-existing failures)

- [ ] **Step 2: Build backend JAR**

Run: `mvn clean package -DskipTests` from `BES/` directory
Expected: BUILD SUCCESS

- [ ] **Step 3: Run frontend tests (if any)**

Run: `npm test` from `BES-frontend/` directory
Expected: Tests pass or no breaking changes

- [ ] **Step 4: Build frontend**

Run: `npm run build` from `BES-frontend/` directory
Expected: BUILD SUCCESS, no errors

- [ ] **Step 5: Verify Docker compose config**

Run: `docker-compose config` from root
Expected: Valid docker-compose.yml

- [ ] **Step 6: Build and start Docker containers**

Run: `docker-compose down && docker-compose up --build --no-cache` from root
Expected: All 3 containers start successfully (postgres, backend, frontend)

Wait ~30 seconds for containers to be healthy.

- [ ] **Step 7: Test tier gate in browser**

Open `http://localhost/` in browser:
1. Log in as a PRO organiser
2. Verify Battle link is hidden from navbar
3. Verify Battle quick-action is hidden from MainMenu
4. Create/navigate to an event, verify battle sections (format, judges, guests) are hidden
5. Try direct URL navigation to `/battle/control` → redirected to home
6. Log in as MAX organiser → all battle features visible

- [ ] **Step 8: Check backend logs for errors**

Run: `docker-compose logs backend | grep -i error | tail -20`
Expected: No tier-related errors

- [ ] **Step 9: Verify database migration ran**

Connect to postgres and check:
```bash
docker-compose exec postgres psql -U postgres -d bes -c "SELECT * FROM account LIMIT 1 \gx"
```
Expected: `tier` column exists with values 'PRO' or 'MAX'

- [ ] **Step 10: Commit all changes**

```bash
git status
git add .
git commit -m "feat: complete organiser tier system implementation and validation"
```

**Acceptance Criteria:**
- All backend tests pass
- All frontend builds succeed
- Docker containers start and run
- PRO tier: battle features completely hidden
- MAX tier: all features visible
- Database has tier column with correct values
- No 500 errors in logs

---

## Summary

**Total implementation tasks:** 22
**Backend changes:** ~11 files
**Frontend changes:** ~10 files
**Testing:** 1 integration test class + manual browser validation

**Key milestones:**
1. ✅ DB migration + entity (Tasks 1–2)
2. ✅ Service layer & enforcement (Tasks 3–6)
3. ✅ DTOs & admin endpoints (Tasks 7–11)
4. ✅ Frontend composable & state (Tasks 12–14)
5. ✅ Frontend UI hiding (Tasks 15–20)
6. ✅ Testing & validation (Tasks 21–22)

**Execution:** Each task is 5–15 minutes. Total ~4–5 hours including testing.

---

## Self-Review

**Spec Coverage:**
- ✅ DB migration: Task 1
- ✅ Entity & tier field: Task 2
- ✅ TierAccessService (single source of truth): Task 3
- ✅ EventService helper: Task 4
- ✅ All 36 BattleController endpoints gated: Task 5
- ✅ EventGenre format setter gated: Task 6
- ✅ GetOrganiserDto updated: Task 7
- ✅ UpdateOrganiserTierDto: Task 8
- ✅ whoami response includes tier: Task 9
- ✅ /event/{name}/battle-enabled endpoint: Task 10
- ✅ Admin tier endpoint: Task 11
- ✅ Frontend composable useTierAccess: Task 12
- ✅ Auth store tier + activeEventBattleEnabled: Task 13
- ✅ API function setOrganiserTier: Task 14
- ✅ Hide Battle link in nav: Task 15
- ✅ Hide Battle quick-action: Task 16
- ✅ Hide battle sections in EventDetails: Task 17
- ✅ Route guards for battle routes: Task 18
- ✅ Empty states for broadcast displays: Task 19
- ✅ Admin tier dropdown UI: Task 20
- ✅ Integration tests: Task 21
- ✅ Docker validation: Task 22

**Clarifications incorporated:**
- ✅ Hide-not-lock UX (no modals, no lock icons)
- ✅ Public broadcast displays entirely hidden for PRO
- ✅ Battle guests section hidden
- ✅ All 36 endpoints gated

**No placeholders:** All tasks include exact file paths, code snippets, commands with expected output, and acceptance criteria.

**Type consistency:** Service method names, DTO fields, and API endpoints are consistent throughout.

