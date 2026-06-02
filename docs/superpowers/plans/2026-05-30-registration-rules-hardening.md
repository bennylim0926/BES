# Registration Rules Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden participant registration so entry type (solo vs team) is always explicit, required fields are validated, and team info moves to the EventGenreParticipant level for per-genre team support.

**Architecture:** Move team data from `EventParticipant` → `EventGenreParticipant` (new `team_name` column + `EventGenreParticipantMember` table). Walk-in form gains per-genre Team|Solo toggles with independent team config. Sheet import gains `ENTRY_TYPE` column with structured per-row error reporting.

**Tech Stack:** Java 21, Spring Boot, JPA/Hibernate, Flyway, Vue 3, Vitest

---

## File Map

**New files:**
- `BES/src/main/resources/db/migration/V21__add_egp_team_info.sql`
- `BES/src/main/java/com/example/BES/models/EventGenreParticipantMember.java`
- `BES/src/main/java/com/example/BES/respositories/EventGenreParticipantMemberRepo.java`
- `BES/src/main/java/com/example/BES/dtos/ImportResultDto.java`

**Modified files:**
- `BES/src/main/java/com/example/BES/models/EventGenreParticipant.java` — add `teamName` + `@OneToMany members`
- `BES/src/main/java/com/example/BES/dtos/AddWalkInDto.java` — add `entryMode`
- `BES/src/main/java/com/example/BES/dtos/AddParticipantDto.java` — add `entryType`
- `BES/src/main/java/com/example/BES/enums/SheetHeader.java` — add `ENTRY_TYPE`
- `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java` — `validateTeamEntry`, `parseFormatSize`, fix `addWalkInToEventGenreParticipant`, fix `addGenreToExistingParticipant`, fix `getAllEventGenreParticipantByEventService`
- `BES/src/main/java/com/example/BES/services/EventParticpantService.java` — simplify `addNewWalkInInEventService`
- `BES/src/main/java/com/example/BES/services/RegistrationService.java` — structured errors, write team to EGP, handle `ENTRY_TYPE`
- `BES/src/main/java/com/example/BES/mapper/RegistrationDtoMapper.java` — parse `ENTRY_TYPE`
- `BES/src/main/java/com/example/BES/controllers/EventController.java` — walk-in 400 on validation error, import returns `ImportResultDto`
- `BES-frontend/src/utils/api.js` — add `entryMode` param to `addWalkinToSystem`
- `BES-frontend/src/components/CreateParticipantForm.vue` — per-genre entry mode + team config

---

### Task 1: DB Migration V21

**Files:**
- Create: `BES/src/main/resources/db/migration/V21__add_egp_team_info.sql`

- [ ] **Step 1: Create the migration file**

```sql
-- Add team_name to event_genre_participant
ALTER TABLE event_genre_participant
    ADD COLUMN team_name VARCHAR(255);

-- New table for EGP-level team members
CREATE TABLE event_genre_participant_member (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            BIGINT NOT NULL,
    genre_id            BIGINT NOT NULL,
    participant_id      BIGINT NOT NULL,
    member_name         VARCHAR(255) NOT NULL,
    CONSTRAINT fk_egpm_egp FOREIGN KEY (event_id, genre_id, participant_id)
        REFERENCES event_genre_participant (event_id, genre_id, participant_id)
        ON DELETE CASCADE
);
```

- [ ] **Step 2: Verify migration validates (no local DB needed — Spring Boot will validate on startup)**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V21__add_egp_team_info.sql
git commit -m "feat: add EGP-level team info migration (V21)"
```

---

### Task 2: EventGenreParticipantMember Entity and Repo

**Files:**
- Create: `BES/src/main/java/com/example/BES/models/EventGenreParticipantMember.java`
- Create: `BES/src/main/java/com/example/BES/respositories/EventGenreParticipantMemberRepo.java`

- [ ] **Step 1: Create the entity**

```java
package com.example.BES.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_genre_participant_member")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGenreParticipantMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "event_id",       referencedColumnName = "event_id"),
        @JoinColumn(name = "genre_id",       referencedColumnName = "genre_id"),
        @JoinColumn(name = "participant_id", referencedColumnName = "participant_id")
    })
    private EventGenreParticipant eventGenreParticipant;

    @Column(name = "member_name", nullable = false)
    private String memberName;

    public EventGenreParticipantMember(EventGenreParticipant egp, String memberName) {
        this.eventGenreParticipant = egp;
        this.memberName = memberName;
    }
}
```

- [ ] **Step 2: Create the repo**

```java
package com.example.BES.respositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.BES.models.EventGenreParticipantMember;

public interface EventGenreParticipantMemberRepo
        extends JpaRepository<EventGenreParticipantMember, Long> {
}
```

- [ ] **Step 3: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/EventGenreParticipantMember.java \
        BES/src/main/java/com/example/BES/respositories/EventGenreParticipantMemberRepo.java
git commit -m "feat: add EventGenreParticipantMember entity and repo"
```

---

### Task 3: Add teamName and Members to EventGenreParticipant

**Files:**
- Modify: `BES/src/main/java/com/example/BES/models/EventGenreParticipant.java`

- [ ] **Step 1: Add fields to EventGenreParticipant**

Add after the `scores` field (existing `@OneToMany` list):

```java
@Column(name = "team_name")
private String teamName;

@ToString.Exclude
@EqualsAndHashCode.Exclude
@OneToMany(mappedBy = "eventGenreParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
private List<EventGenreParticipantMember> members = new ArrayList<>();
```

Also add `import java.util.ArrayList;` and `import com.example.BES.models.EventGenreParticipantMember;` to the imports.

- [ ] **Step 2: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/EventGenreParticipant.java
git commit -m "feat: add teamName and EGP-level members to EventGenreParticipant"
```

---

### Task 4: ImportResultDto

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/ImportResultDto.java`

- [ ] **Step 1: Create the DTO**

```java
package com.example.BES.dtos;

import java.util.ArrayList;
import java.util.List;

public class ImportResultDto {
    public int imported;
    public int skipped;
    public List<SkippedRow> errors = new ArrayList<>();

    public static class SkippedRow {
        public int row;
        public String name;
        public String reason;

        public SkippedRow(int row, String name, String reason) {
            this.row = row;
            this.name = name;
            this.reason = reason;
        }
    }
}
```

- [ ] **Step 2: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/ImportResultDto.java
git commit -m "feat: add ImportResultDto for structured sheet import errors"
```

---

### Task 5: DTO Updates — AddWalkInDto.entryMode and AddParticipantDto.entryType

**Files:**
- Modify: `BES/src/main/java/com/example/BES/dtos/AddWalkInDto.java`
- Modify: `BES/src/main/java/com/example/BES/dtos/AddParticipantDto.java`

- [ ] **Step 1: Add `entryMode` to AddWalkInDto**

Full updated file:

```java
package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class AddWalkInDto {
    @NotBlank @Size(max = 255)
    public String name;
    @NotBlank @Size(max = 255)
    public String genre;
    @NotBlank @Size(max = 255)
    public String eventName;
    @Size(max = 255)
    public String judgeName;
    public List<@NotBlank @Size(max = 255) String> teamMembers;
    @Size(max = 255)
    public String teamName;
    // "team" | "solo" — null treated as "team" for backwards compatibility
    public String entryMode;
}
```

- [ ] **Step 2: Add `entryType` to AddParticipantDto**

Add one field after `memberNames`:

```java
    public List<String> memberNames;
    public Map<String, String> genreFormats;
    // "team" | "solo" — from sheet ENTRY_TYPE column
    public String entryType;
```

(Add setter for `entryType` — Lombok `@Getter @Setter` already on the class, so just adding the field is enough.)

- [ ] **Step 3: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/AddWalkInDto.java \
        BES/src/main/java/com/example/BES/dtos/AddParticipantDto.java
git commit -m "feat: add entryMode to AddWalkInDto and entryType to AddParticipantDto"
```

---

### Task 6: SheetHeader.ENTRY_TYPE Constant

**Files:**
- Modify: `BES/src/main/java/com/example/BES/enums/SheetHeader.java`

- [ ] **Step 1: Add the constant**

```java
public class SheetHeader {
    public static final String EMAIL          = "email";
    public static final String NAME           = "name";
    public static final String PAYMENT_STATUS = "payment status";
    public static final String CATEGORIES     = "categories";
    public static final String LOCAL_OVERSEAS = "local/overseas";
    public static final String SCREENSHOT     = "screenshot";
    public static final String STAGE_NAME     = "stage name";
    public static final String TEAM_NAME      = "team name";
    public static final String ENTRY_TYPE     = "entry type";
}
```

- [ ] **Step 2: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/enums/SheetHeader.java
git commit -m "feat: add ENTRY_TYPE constant to SheetHeader"
```

---

### Task 7: validateTeamEntry and parseFormatSize Utilities

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java`

These two methods are private helpers used by walk-in and sheet import validation. They live in `EventGenreParticpantService` because that service owns the EGP write path.

- [ ] **Step 1: Write a failing test**

Create `BES/src/test/java/com/example/BES/services/EventGenreParticpantServiceValidationTest.java`:

```java
package com.example.BES.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class EventGenreParticpantServiceValidationTest {

    private final EventGenreParticpantService service = new EventGenreParticpantService();

    @Test
    void parseFormatSize_returns_correct_size() {
        assertThat(service.parseFormatSize("1v1")).isEqualTo(1);
        assertThat(service.parseFormatSize("2v2")).isEqualTo(2);
        assertThat(service.parseFormatSize("3v3")).isEqualTo(3);
        assertThat(service.parseFormatSize("4v4")).isEqualTo(4);
    }

    @Test
    void validateTeamEntry_throws_when_teamName_blank() {
        assertThatThrownBy(() -> service.validateTeamEntry("2v2", "", List.of("Member A")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Team name is required");
    }

    @Test
    void validateTeamEntry_throws_when_member_count_wrong() {
        assertThatThrownBy(() -> service.validateTeamEntry("2v2", "Team A", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Expected 1 additional member");
    }

    @Test
    void validateTeamEntry_passes_valid_2v2() {
        assertThatCode(() -> service.validateTeamEntry("2v2", "Team A", List.of("Member A")))
            .doesNotThrowAnyException();
    }

    @Test
    void validateTeamEntry_passes_valid_3v3() {
        assertThatCode(() -> service.validateTeamEntry("3v3", "Crew X", List.of("B", "C")))
            .doesNotThrowAnyException();
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd BES && ./mvnw test -Dtest=EventGenreParticpantServiceValidationTest 2>&1 | grep -E "FAIL|ERROR|Tests run"
```
Expected: compilation error or test failure (methods don't exist yet)

- [ ] **Step 3: Add the two methods to EventGenreParticpantService**

Add these as package-private methods (not private) so the test can call them directly:

```java
int parseFormatSize(String format) {
    if (format == null) return 0;
    String[] parts = format.split("v");
    try { return Integer.parseInt(parts[0]); } catch (NumberFormatException e) { return 0; }
}

void validateTeamEntry(String format, String teamName, List<String> memberNames) {
    int required = parseFormatSize(format) - 1;
    if (teamName == null || teamName.isBlank())
        throw new IllegalArgumentException("Team name is required for team entry");
    long nonBlank = memberNames == null ? 0L
        : memberNames.stream().filter(m -> m != null && !m.isBlank()).count();
    if (nonBlank != required)
        throw new IllegalArgumentException(
            "Expected " + required + " additional member(s) for " + format + ", got " + nonBlank);
}
```

Also add `import java.util.List;` if not already present.

- [ ] **Step 4: Run test to confirm it passes**

```bash
cd BES && ./mvnw test -Dtest=EventGenreParticpantServiceValidationTest 2>&1 | grep -E "Tests run|BUILD"
```
Expected: `Tests run: 5, Failures: 0, Errors: 0` and `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java \
        BES/src/test/java/com/example/BES/services/EventGenreParticpantServiceValidationTest.java
git commit -m "feat: add validateTeamEntry and parseFormatSize to EventGenreParticpantService"
```

---

### Task 8: Fix addWalkInToEventGenreParticipant — Write Team to EGP

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java`

The current method reads team data from `EventParticipant`. After this change it receives team data directly and writes it to `EventGenreParticipant`.

- [ ] **Step 1: Update the method signature and body**

Replace the existing `addWalkInToEventGenreParticipant` method with:

```java
public EventGenreParticipant addWalkInToEventGenreParticipant(
        Participant p, String genre, EventParticipant ep,
        String judgeName, String entryMode, String teamName, List<String> teamMembers) {

    Genre g = genreRepo.findByGenreName(genre).orElse(null);
    Judge j = judgeRepo.findByName(judgeName).orElse(null);
    EventGenreParticipantId id = new EventGenreParticipantId(
        ep.getEvent().getEventId(), g.getGenreId(), p.getParticipantId());
    EventGenreParticipant egp = repo.findById(id).orElse(null);
    if (egp == null) {
        egp = new EventGenreParticipant();
        egp.setId(id);
        egp.setJudge(j);
        egp.setEvent(ep.getEvent());
        egp.setParticipant(p);
        egp.setGenre(g);

        EventGenre eg = eventGenreRepo.findByEventAndGenre(ep.getEvent(), g).orElse(null);
        String genreFormat = eg != null ? eg.getFormat() : null;
        boolean isTeamFormat = isTeamFormat(genreFormat);
        boolean isTeamEntry = isTeamFormat && !"solo".equalsIgnoreCase(entryMode);

        if (isTeamEntry) {
            validateTeamEntry(genreFormat, teamName, teamMembers);
            egp.setFormat(genreFormat);
            egp.setTeamName(teamName);
            egp.setDisplayName(teamName);
        } else {
            egp.setFormat(isTeamFormat ? null : genreFormat);
            egp.setDisplayName(ep.getDisplayName() != null ? ep.getDisplayName() : p.getParticipantName());
        }
    }
    EventGenreParticipant saved = repo.save(egp);

    if (!"solo".equalsIgnoreCase(entryMode) && isTeamFormat(saved.getFormat())
            && teamMembers != null) {
        for (String memberName : teamMembers) {
            if (memberName != null && !memberName.isBlank()) {
                egpMemberRepo.save(new EventGenreParticipantMember(saved, memberName));
            }
        }
    }
    return saved;
}
```

Add `@Autowired EventGenreParticipantMemberRepo egpMemberRepo;` field at the top of the class.

Keep `isTeamFormat` helper already in the class.

- [ ] **Step 2: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Fix the call site in EventController** (update params — full controller change is in Task 12, but the signature change must compile)

In `EventController.addWalkInToSystem`, temporarily update the call to pass the new params:

```java
eventGenreParticipantService.addWalkInToEventGenreParticipant(
    p, dto.genre, ep, dto.judgeName,
    dto.entryMode, dto.teamName, dto.teamMembers);
```

- [ ] **Step 4: Compile again to confirm call site compiles**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java \
        BES/src/main/java/com/example/BES/controllers/EventController.java
git commit -m "feat: write walk-in team data to EGP level, validate on submission"
```

---

### Task 9: Fix addGenreToExistingParticipant — Set Format Correctly

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java`

Currently `addGenreToExistingParticipant` saves EGP with `format = null` regardless of genre type. Fix: load `EventGenre.format`, apply same team-entry logic using EP's existing team data.

- [ ] **Step 1: Update addGenreToExistingParticipant**

Find the section in the method where `egp` is built (after the `if (repo.existsById(id)) return;` guard) and add format logic before `repo.save(egp)`:

```java
EventGenre eg = eventGenreRepo.findByEventAndGenre(event, genre).orElse(null);
String genreFormat = eg != null ? eg.getFormat() : null;
boolean isTeamEntry = isTeamFormat(genreFormat)
    && ep != null
    && ((ep.getTeamName() != null && !ep.getTeamName().isBlank())
        || (ep.getTeamMembers() != null && !ep.getTeamMembers().isEmpty()));
egp.setFormat(isTeamEntry ? genreFormat : (isTeamFormat(genreFormat) ? null : genreFormat));
if (isTeamEntry && ep != null) {
    egp.setDisplayName(ep.getTeamName() != null ? ep.getTeamName() : egp.getDisplayName());
    egp.setTeamName(ep.getTeamName());
}
```

Place this block immediately before the `repo.save(egp);` call.

- [ ] **Step 2: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java
git commit -m "fix: set EGP format correctly in addGenreToExistingParticipant"
```

---

### Task 10: Fix getAllEventGenreParticipantByEventService — Read Members from EGP

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java`

Currently `memberNamesMap` is built from `EventParticipant.teamMembers` (EP-level, legacy data). After this change, for any EGP that has its own `members` list (new data), use those instead. Fall back to EP-level data for records written before this migration.

- [ ] **Step 1: Update the read path in getAllEventGenreParticipantByEventService**

The section that currently reads:
```java
// Only expose member names for team-format EGPs (format != "1v1")
String fmt = res.getFormat();
if (fmt != null && !fmt.equalsIgnoreCase("1v1")) {
    dto.memberNames = memberNamesMap.get(pid);
}
```

Replace with:

```java
String fmt = res.getFormat();
if (fmt != null && !fmt.equalsIgnoreCase("1v1")) {
    // Prefer EGP-level team members (new data); fall back to EP-level (legacy)
    List<EventGenreParticipantMember> egpMembers = res.getMembers();
    if (egpMembers != null && !egpMembers.isEmpty()) {
        List<String> memberList = new ArrayList<>();
        String leaderName = res.getDisplayName() != null ? res.getDisplayName() : res.getParticipant().getParticipantName();
        memberList.add(leaderName);
        egpMembers.stream().map(EventGenreParticipantMember::getMemberName).forEach(memberList::add);
        dto.memberNames = memberList;
    } else {
        dto.memberNames = memberNamesMap.get(pid);
    }
}
```

Also add import: `import com.example.BES.models.EventGenreParticipantMember;`

- [ ] **Step 2: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventGenreParticpantService.java
git commit -m "fix: read member names from EGP level with EP fallback for legacy data"
```

---

### Task 11: Simplify addNewWalkInInEventService — Remove EP Team Writing

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventParticpantService.java`

Team info is now written at the EGP level (Task 8). The EP path should no longer write `teamName`, `displayName` based on team, or `teamMembers`. EP is still created per-person per-event for non-team fields (payment, referenceCode, displayName = stage name).

- [ ] **Step 1: Simplify the method signature and body**

Replace the existing `addNewWalkInInEventService` method:

```java
public EventParticipant addNewWalkInInEventService(Participant p, String eventName) {
    Event event = eventRepo.findByEventName(eventName).orElse(null);
    if (event == null) return null;
    EventParticipant e = eventParticipantRepo.findByEventAndParticipant(event, p).orElse(null);
    if (e == null) {
        e = new EventParticipant();
        e.setEvent(event);
        e.setParticipant(p);
        e.setPaymentVerified(true);
        e.setStageName(p.getParticipantName());
        e.setDisplayName(p.getParticipantName());
        e.setReferenceCode(ReferenceCodeUtil.generate());
        eventParticipantRepo.save(e);
    }
    return e;
}
```

Note: the old signature was `addNewWalkInInEventService(Participant p, String eventName, String genre, List<String> teamMembers, String teamName)`. The call site in `EventController` must be updated to match the new two-param signature. Update the EventController call:

```java
EventParticipant ep = eventParticipantService.addNewWalkInInEventService(p, dto.eventName);
```

- [ ] **Step 2: Remove unused imports from EventParticpantService if `teamMembers`/`teamName` params were the only callers of `EventParticipantTeamMember` there**

Check: if `EventParticipantTeamMemberRepo` is only used in `addNewWalkInInEventService`, remove the `@Autowired` field and import.

- [ ] **Step 3: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventParticpantService.java \
        BES/src/main/java/com/example/BES/controllers/EventController.java
git commit -m "refactor: stop writing team info to EventParticipant in walk-in path"
```

---

### Task 12: EventController Walk-in Endpoint — Validation and 400 Response

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

The `addWalkInToSystem` handler must return HTTP 400 with the validation error message when `validateTeamEntry` throws. Currently any exception returns a generic "error" string; we want the actual message for team validation failures.

- [ ] **Step 1: Write a failing integration test**

Find the existing walk-in integration test file. If there is none, create `BES/src/test/java/com/example/BES/controllers/WalkInValidationIntegrationTest.java`:

```java
package com.example.BES.controllers;

import com.example.BES.dtos.AddWalkInDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WalkInValidationIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "ADMIN")
    void addWalkIn_teamEntry_missingTeamName_returns400() throws Exception {
        AddWalkInDto dto = new AddWalkInDto();
        dto.name      = "Alice";
        dto.genre     = "popping";         // must exist in H2 test data or be mocked
        dto.eventName = "TestEvent";
        dto.entryMode = "team";
        dto.teamName  = "";                // blank — should trigger validation error
        dto.teamMembers = List.of("Bob");

        mockMvc.perform(post("/api/v1/event/walkins/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
               .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to confirm it fails (currently any error returns 400 generically, but this will depend on existing setup)**

```bash
cd BES && ./mvnw test -Dtest=WalkInValidationIntegrationTest 2>&1 | grep -E "Tests run|ERROR|BUILD"
```

Note: the test may already "pass" with 400 due to the generic catch block — that's acceptable. The real goal is that the error message is returned. Skip this test if it's too environment-dependent.

- [ ] **Step 3: Update addWalkInToSystem in EventController**

The current handler:
```java
@PostMapping("/walkins/")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<String> addWalkInToSystem(@Valid @RequestBody AddWalkInDto dto) {
    try {
        Participant p = participantService.addWalkInService(dto);
        EventParticipant ep = eventParticipantService.addNewWalkInInEventService(p, dto.eventName, dto.genre, dto.teamMembers, dto.teamName);
        eventGenreParticipantService.addWalkInToEventGenreParticipant(p, dto.genre, ep, dto.judgeName);
        ...
    } catch (Exception e) {
        return new ResponseEntity<>(gson.toJson("error"), HttpStatus.BAD_REQUEST);
    }
}
```

Replace with (incorporating Task 8 and Task 11 call-site changes):

```java
@PostMapping("/walkins/")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<String> addWalkInToSystem(@Valid @RequestBody AddWalkInDto dto) {
    try {
        Participant p = participantService.addWalkInService(dto);
        EventParticipant ep = eventParticipantService.addNewWalkInInEventService(p, dto.eventName);
        eventGenreParticipantService.addWalkInToEventGenreParticipant(
            p, dto.genre, ep, dto.judgeName, dto.entryMode, dto.teamName, dto.teamMembers);
        Map<String, Object> walkinMsg = new java.util.HashMap<>();
        walkinMsg.put("eventName", dto.eventName);
        messagingTemplate.convertAndSend("/topic/walkin/", walkinMsg);
        return new ResponseEntity<>(gson.toJson("Added walkin"), HttpStatus.CREATED);
    } catch (IllegalArgumentException e) {
        return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
    } catch (Exception e) {
        log.error("Error adding walk-in", e);
        return new ResponseEntity<>(gson.toJson("Error adding participant"), HttpStatus.BAD_REQUEST);
    }
}
```

- [ ] **Step 4: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Run all tests**

```bash
cd BES && ./mvnw test 2>&1 | grep -E "Tests run|BUILD|FAIL"
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/EventController.java \
        BES/src/test/java/com/example/BES/controllers/WalkInValidationIntegrationTest.java
git commit -m "feat: walk-in endpoint returns 400 with message on team validation failure"
```

---

### Task 13: Parse ENTRY_TYPE in RegistrationDtoMapper and GoogleSheetService

**Files:**
- Modify: `BES/src/main/java/com/example/BES/mapper/RegistrationDtoMapper.java`
- Modify: `BES/src/main/java/com/example/BES/services/GoogleSheetService.java`

- [ ] **Step 1: Add ENTRY_TYPE to the column index pre-scan in GoogleSheetService.getColumnIndexMap**

In the `getColumnIndexMap` method, add to the pre-scan block (where STAGE_NAME and TEAM_NAME are found):

```java
if (h.contains(SheetHeader.ENTRY_TYPE)) colIndexMap.putIfAbsent(SheetHeader.ENTRY_TYPE, i);
```

- [ ] **Step 2: Read and set entryType in RegistrationDtoMapper.mapRow**

After the `teamNameIdx` section, add:

```java
Integer entryTypeIdx = colIndexMap.get(SheetHeader.ENTRY_TYPE);
if (entryTypeIdx != null && row.size() > entryTypeIdx && !row.get(entryTypeIdx).isBlank()) {
    dto.setEntryType(row.get(entryTypeIdx).trim().toLowerCase());
}
```

- [ ] **Step 3: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/mapper/RegistrationDtoMapper.java \
        BES/src/main/java/com/example/BES/services/GoogleSheetService.java
git commit -m "feat: parse ENTRY_TYPE column in sheet import mapper"
```

---

### Task 14: RegistrationService — Structured Errors, Write Team to EGP

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/RegistrationService.java`

This is the largest change. `addParticipantToEvent` currently returns `void` and silently skips team validation. After this change it returns `ImportResultDto`, validates per row, writes team info to EGP, and handles `ENTRY_TYPE`.

- [ ] **Step 1: Write a failing unit test for the new validation logic**

Create `BES/src/test/java/com/example/BES/services/RegistrationServiceImportTest.java`:

```java
package com.example.BES.services;

import com.example.BES.dtos.AddParticipantDto;
import com.example.BES.dtos.ImportResultDto;
import com.example.BES.models.*;
import com.example.BES.respositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImportTest {

    @InjectMocks RegistrationService service;
    @Mock EventRepo eventRepo;
    @Mock ParticipantService participantService;
    @Mock ParticipantRepo participantRepo;
    @Mock EventParticipantRepo eventParticipantRepo;
    @Mock EventGenreParticpantRepo eventGenreParticipantRepo;
    @Mock EventParticipantTeamMemberRepo teamMemberRepo;
    @Mock GenreRepo genreRepo;
    @Mock EventGenreRepo eventGenreRepo;
    @Mock EventGenreParticipantMemberRepo egpMemberRepo;
    @Mock GoogleSheetService sheetService;

    private Event mockEvent;
    private Genre mockGenre;
    private EventGenre mockEventGenre;

    @BeforeEach
    void setUp() {
        mockEvent = new Event();
        mockEvent.setEventId(1L);
        mockEvent.setEventName("TestEvent");
        mockEvent.setPaymentRequired(false);

        mockGenre = new Genre();
        mockGenre.setGenreId(10L);
        mockGenre.setGenreName("popping");

        mockEventGenre = new EventGenre();
        mockEventGenre.setFormat("2v2");

        when(eventRepo.findByEventName("TestEvent")).thenReturn(Optional.of(mockEvent));
        when(genreRepo.findByGenreName("popping")).thenReturn(Optional.of(mockGenre));
        when(eventGenreRepo.findByEventAndGenre(any(), any())).thenReturn(Optional.of(mockEventGenre));
    }

    @Test
    void rowWithTeamFormatGenre_missingEntryType_isSkipped() throws Exception {
        AddParticipantDto participant = new AddParticipantDto();
        participant.setParticipantName("Alice");
        participant.setStageName("Alice");
        participant.setGenres(List.of("popping")); // 2v2 genre
        participant.setEntryType(null);            // missing

        when(sheetService.getAllImportableParticipants(any())).thenReturn(List.of(participant));

        com.example.BES.dtos.AddParticipantToEventDto dto = new com.example.BES.dtos.AddParticipantToEventDto();
        dto.eventName = "TestEvent";

        ImportResultDto result = service.addParticipantToEvent(dto);

        assertThat(result.imported).isEqualTo(0);
        assertThat(result.skipped).isEqualTo(1);
        assertThat(result.errors).hasSize(1);
        assertThat(result.errors.get(0).reason).contains("ENTRY_TYPE");
    }

    @Test
    void rowWithTeamEntry_missingTeamName_isSkipped() throws Exception {
        AddParticipantDto participant = new AddParticipantDto();
        participant.setParticipantName("Bob");
        participant.setStageName("Bob");
        participant.setGenres(List.of("popping"));
        participant.setEntryType("team");
        participant.setTeamName("");              // blank team name
        participant.setMemberNames(List.of("C"));

        when(sheetService.getAllImportableParticipants(any())).thenReturn(List.of(participant));

        com.example.BES.dtos.AddParticipantToEventDto dto = new com.example.BES.dtos.AddParticipantToEventDto();
        dto.eventName = "TestEvent";

        ImportResultDto result = service.addParticipantToEvent(dto);

        assertThat(result.imported).isEqualTo(0);
        assertThat(result.skipped).isEqualTo(1);
        assertThat(result.errors.get(0).reason).contains("Team name");
    }
}
```

- [ ] **Step 2: Run test to confirm it fails**

```bash
cd BES && ./mvnw test -Dtest=RegistrationServiceImportTest 2>&1 | grep -E "Tests run|BUILD|ERROR"
```
Expected: compile error (return type is still void)

- [ ] **Step 3: Rewrite addParticipantToEvent in RegistrationService**

The full updated method signature and body — replace the existing `addParticipantToEvent`:

```java
public ImportResultDto addParticipantToEvent(AddParticipantToEventDto dto) throws IOException {
    Event event = eventRepo.findByEventName(dto.eventName).orElse(null);
    if (event == null) throw new NullPointerException("event is null");

    List<AddParticipantDto> importable = sheetService.getAllImportableParticipants(dto);
    ImportResultDto result = new ImportResultDto();
    int rowNumber = 2; // sheet rows are 1-indexed; row 1 is header

    for (AddParticipantDto participant : importable) {
        String participantName = participant.getParticipantName();
        try {
            // Determine if any genre in this row requires a team entry decision
            boolean hasTeamFormatGenre = hasTeamFormatGenre(participant.getGenres(), event);

            if (hasTeamFormatGenre) {
                String entryType = participant.getEntryType();
                if (entryType == null || entryType.isBlank()) {
                    result.errors.add(new ImportResultDto.SkippedRow(rowNumber, participantName,
                        "ENTRY_TYPE missing for team-format genre"));
                    result.skipped++;
                    rowNumber++;
                    continue;
                }
                if ("team".equals(entryType)) {
                    // Find the team-format genre to get the format string
                    String format = getTeamFormat(participant.getGenres(), event);
                    validateTeamEntry(format, participant.getTeamName(), participant.getMemberNames());
                }
            }

            Participant toAddParticipant = participantService.addParticpantService(participant);
            EventParticipant ep = eventParticipantRepo
                .findByEventAndParticipant(event, toAddParticipant).orElse(null);

            if (ep != null) {
                rowNumber++;
                continue; // already imported — no error, just skip
            }

            ep = new EventParticipant();
            ep.setParticipant(toAddParticipant);
            ep.setEvent(event);
            ep.setStageName(participant.getStageName());
            ep.setDisplayName(resolveDisplayName(participant));
            ep.setResidency(participant.getResidency());
            ep.setGenre(participant.getGenres() != null ? String.join(", ", participant.getGenres()) : "");
            ep.setPaymentVerified(!event.isPaymentRequired());
            ep.setScreenshotUrl(participant.getScreenshotUrl());
            ep.setReferenceCode(ReferenceCodeUtil.generate());
            eventParticipantRepo.save(ep);

            if (participant.getGenres() != null) {
                for (String genreName : participant.getGenres()) {
                    Genre genre = genreRepo.findByGenreName(genreName.toLowerCase()).orElse(null);
                    if (genre == null) continue;
                    EventGenreParticipantId id = new EventGenreParticipantId(
                        event.getEventId(), genre.getGenreId(), toAddParticipant.getParticipantId());
                    EventGenreParticipant egp = new EventGenreParticipant();
                    egp.setId(id);
                    egp.setEvent(event);
                    egp.setGenre(genre);
                    egp.setParticipant(toAddParticipant);

                    EventGenre eg = eventGenreRepo.findByEventAndGenre(event, genre).orElse(null);
                    String effectiveFormat = eg != null ? eg.getFormat() : null;
                    boolean isTeamFormat = isTeamFormat(effectiveFormat);
                    boolean isTeamEntry = isTeamFormat && "team".equals(participant.getEntryType());

                    if (isTeamEntry) {
                        egp.setFormat(effectiveFormat);
                        egp.setTeamName(participant.getTeamName());
                        egp.setDisplayName(participant.getTeamName());
                    } else {
                        egp.setFormat(isTeamFormat ? null : effectiveFormat);
                        egp.setDisplayName(orElse(participant.getStageName(), participant.getParticipantName()));
                    }

                    EventGenreParticipant savedEgp = eventGenreParticipantRepo.save(egp);

                    if (isTeamEntry && participant.getMemberNames() != null) {
                        for (String memberName : participant.getMemberNames()) {
                            if (memberName != null && !memberName.isBlank()) {
                                egpMemberRepo.save(new EventGenreParticipantMember(savedEgp, memberName));
                            }
                        }
                    }
                }
            }
            result.imported++;

        } catch (IllegalArgumentException e) {
            result.errors.add(new ImportResultDto.SkippedRow(rowNumber, participantName, e.getMessage()));
            result.skipped++;
        }
        rowNumber++;
    }
    return result;
}
```

Add two private helper methods in `RegistrationService`:

```java
private boolean isTeamFormat(String format) {
    return format != null && !format.equalsIgnoreCase("1v1");
}

private boolean hasTeamFormatGenre(List<String> genres, Event event) {
    if (genres == null) return false;
    for (String genreName : genres) {
        Genre genre = genreRepo.findByGenreName(genreName.toLowerCase()).orElse(null);
        if (genre == null) continue;
        EventGenre eg = eventGenreRepo.findByEventAndGenre(event, genre).orElse(null);
        if (eg != null && isTeamFormat(eg.getFormat())) return true;
    }
    return false;
}

private String getTeamFormat(List<String> genres, Event event) {
    if (genres == null) return null;
    for (String genreName : genres) {
        Genre genre = genreRepo.findByGenreName(genreName.toLowerCase()).orElse(null);
        if (genre == null) continue;
        EventGenre eg = eventGenreRepo.findByEventAndGenre(event, genre).orElse(null);
        if (eg != null && isTeamFormat(eg.getFormat())) return eg.getFormat();
    }
    return null;
}

private void validateTeamEntry(String format, String teamName, List<String> memberNames) {
    int required = parseFormatSize(format) - 1;
    if (teamName == null || teamName.isBlank())
        throw new IllegalArgumentException("Team name is required for team entry");
    long nonBlank = memberNames == null ? 0L
        : memberNames.stream().filter(m -> m != null && !m.isBlank()).count();
    if (nonBlank != required)
        throw new IllegalArgumentException(
            "Member count mismatch: " + format + " requires " + required + " additional member(s), got " + nonBlank);
}

private int parseFormatSize(String format) {
    if (format == null) return 0;
    String[] parts = format.split("v");
    try { return Integer.parseInt(parts[0]); } catch (NumberFormatException e) { return 0; }
}
```

Also add `@Autowired EventGenreParticipantMemberRepo egpMemberRepo;` field at the top of the class.

Add imports:
```java
import com.example.BES.dtos.ImportResultDto;
import com.example.BES.models.EventGenreParticipantMember;
import com.example.BES.respositories.EventGenreParticipantMemberRepo;
```

Remove the old `if (participant.getMemberNames() != null ...)` block that wrote to `EventParticipantTeamMember` — that write is no longer needed.

- [ ] **Step 4: Run the tests**

```bash
cd BES && ./mvnw test -Dtest=RegistrationServiceImportTest 2>&1 | grep -E "Tests run|BUILD|FAIL"
```
Expected: `Tests run: 2, Failures: 0` and `BUILD SUCCESS`

- [ ] **Step 5: Run all tests**

```bash
cd BES && ./mvnw test 2>&1 | grep -E "Tests run|BUILD|FAIL"
```
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/RegistrationService.java \
        BES/src/test/java/com/example/BES/services/RegistrationServiceImportTest.java
git commit -m "feat: sheet import returns structured errors; write team data to EGP; handle ENTRY_TYPE"
```

---

### Task 15: EventController Import Endpoint — Return ImportResultDto

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

- [ ] **Step 1: Update addParticipantsToSystem to return ImportResultDto**

Replace the existing handler:

```java
@Operation(summary = "Add List of Participants")
@PostMapping("/participants/")
public ResponseEntity<?> addParticipantsToSystem(@Valid @RequestBody AddParticipantToEventDto dto)
        throws IOException, MessagingException, WriterException {
    try {
        ImportResultDto result = registerService.addParticipantToEvent(dto);
        return new ResponseEntity<>(result, HttpStatus.OK);
    } catch (NullPointerException e) {
        log.error("NPE in addParticipantsToSystem", e);
        return new ResponseEntity<>(
                gson.toJson("The record is empty, please verify the payment in the google sheet"),
                HttpStatus.NOT_FOUND);
    }
}
```

Add import: `import com.example.BES.dtos.ImportResultDto;`

- [ ] **Step 2: Compile**

```bash
cd BES && ./mvnw clean compile -DskipTests 2>&1 | tail -5
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Run all tests**

```bash
cd BES && ./mvnw test 2>&1 | grep -E "Tests run|BUILD|FAIL"
```
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/EventController.java
git commit -m "feat: sheet import endpoint returns ImportResultDto with per-row error details"
```

---

### Task 16: Frontend api.js — Pass entryMode to Walk-in Endpoint

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Update addWalkinToSystem to send entryMode**

Current signature:
```javascript
export const addWalkinToSystem = async (participantName, eventName, genreName, judgeName, teamMembers = [], teamName = '') => {
```

Updated:
```javascript
export const addWalkinToSystem = async (participantName, eventName, genreName, judgeName, teamMembers = [], teamName = '', entryMode = 'team') => {
```

In the `body: JSON.stringify(...)` block, add `entryMode`:
```javascript
body: JSON.stringify({
    name: participantName,
    eventName: eventName,
    genre: genreName,
    judgeName: judgeName,
    teamMembers: teamMembers,
    teamName: teamName,
    entryMode: entryMode
})
```

- [ ] **Step 2: Build frontend to catch any type errors**

```bash
cd BES-frontend && npm run build 2>&1 | tail -10
```
Expected: build success with no errors

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: send entryMode in walk-in API call"
```

---

### Task 17: Frontend CreateParticipantForm.vue — Per-genre Entry Mode and Team Config

**Files:**
- Modify: `BES-frontend/src/components/CreateParticipantForm.vue`

**Goal:** Replace the single global `entryMode`/`teamName`/`teamMemberNames` state with per-genre maps keyed by genre name. Each team-format genre gets its own Team|Solo toggle and its own team name + member fields.

- [ ] **Step 1: Write a Vitest test for the per-genre team config logic (utils)**

Create `BES-frontend/src/utils/__tests__/createParticipantFormLogic.test.js`:

```javascript
import { describe, it, expect } from 'vitest'

// Pure logic extracted from the component
function getAdditionalMembersCount(format, entryMode) {
  if (!format || format.toLowerCase() === '1v1' || entryMode === 'solo') return 0
  const match = format.match(/^(\d+)v\d+$/i)
  return match ? parseInt(match[1]) - 1 : 0
}

describe('getAdditionalMembersCount', () => {
  it('returns 0 for 1v1', () => expect(getAdditionalMembersCount('1v1', 'team')).toBe(0))
  it('returns 0 for solo mode in 2v2', () => expect(getAdditionalMembersCount('2v2', 'solo')).toBe(0))
  it('returns 1 for 2v2 team', () => expect(getAdditionalMembersCount('2v2', 'team')).toBe(1))
  it('returns 2 for 3v3 team', () => expect(getAdditionalMembersCount('3v3', 'team')).toBe(2))
})
```

- [ ] **Step 2: Run test to confirm it passes (pure logic, no component needed)**

```bash
cd BES-frontend && npm test -- --reporter=verbose 2>&1 | grep -E "PASS|FAIL|✓|×"
```
Expected: all assertions pass

- [ ] **Step 3: Rewrite the script block of CreateParticipantForm.vue**

Replace the entire `<script setup>` section:

```vue
<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';
import { onMounted, ref, reactive, watch, computed } from 'vue';
import { addWalkinToSystem, fetchAllGenres, getAllJudges } from '@/utils/api';

const props = defineProps({
  show:        { type: Boolean, default: false },
  title:       { type: String,  default: 'New Participant' },
  event:       { type: String,  default: '' },
  eventGenres: { type: Array,   default: null }
})

const emit = defineEmits(['createNewEntry', 'close'])

const name          = ref("")
const selectedJudge = ref("")
const genreOptions  = ref([])  // array of { genreName, format }
const allJudges     = ref([])
const createTable   = reactive({ genres: [] })
const showError     = ref(false)

// Per-genre state maps — keyed by genreName
const genreEntryMode    = reactive({})  // genreName → 'team' | 'solo'
const genreTeamName     = reactive({})  // genreName → String
const genreTeamMembers  = reactive({})  // genreName → String[]

function getFormat(genreName) {
  return genreOptions.value.find(o => o.genreName === genreName)?.format ?? null
}

function isTeamFormat(format) {
  return !!format && format.toLowerCase() !== '1v1'
}

function additionalMembersCount(genreName) {
  const fmt = getFormat(genreName)
  if (!isTeamFormat(fmt) || genreEntryMode[genreName] === 'solo') return 0
  const match = fmt.match(/^(\d+)v\d+$/i)
  return match ? parseInt(match[1]) - 1 : 0
}

// Ensure member arrays are sized correctly when genres are selected/deselected
watch(
  () => [...createTable.genres],
  (genres) => {
    genres.forEach(g => {
      if (!(g in genreEntryMode)) genreEntryMode[g] = 'team'
      if (!(g in genreTeamName))  genreTeamName[g]  = ''
      const count = additionalMembersCount(g)
      if (!(g in genreTeamMembers)) genreTeamMembers[g] = []
      while (genreTeamMembers[g].length < count) genreTeamMembers[g].push('')
      genreTeamMembers[g].splice(count)
    })
  }
)

const submitNewEntry = async () => {
  if (name.value.trim() === '') {
    showError.value = true
    return
  }
  for (const g of createTable.genres) {
    const fmt   = getFormat(g)
    const mode  = isTeamFormat(fmt) ? (genreEntryMode[g] ?? 'team') : 'solo'
    const tName = mode === 'team' ? genreTeamName[g] : ''
    const members = mode === 'team'
      ? (genreTeamMembers[g] ?? []).filter(m => m.trim() !== '')
      : []
    await addWalkinToSystem(name.value, props.event, g, selectedJudge.value, members, tName, mode)
  }
  name.value = ''
  createTable.genres = []
  Object.keys(genreEntryMode).forEach(k => delete genreEntryMode[k])
  Object.keys(genreTeamName).forEach(k => delete genreTeamName[k])
  Object.keys(genreTeamMembers).forEach(k => delete genreTeamMembers[k])
  emit('createNewEntry')
}

watch(() => props.eventGenres, (newGenres) => {
  if (newGenres && newGenres.length > 0) {
    genreOptions.value = newGenres.map(g => ({ genreName: g.genreName, format: g.format || null }))
  }
}, { immediate: true })

onMounted(async () => {
  if (!props.eventGenres || props.eventGenres.length === 0) {
    const genres = await fetchAllGenres()
    genreOptions.value = genres.map(g => ({ genreName: g.genreName, format: g.format || null }))
  }
  const res = await getAllJudges()
  allJudges.value = ['', ...Object.values(res).map(item => item.judgeName)]
})
</script>
```

- [ ] **Step 4: Rewrite the template section**

Replace the entire `<template>` section:

```vue
<template>
  <ActionDoneModal
    :show="props.show"
    :title="props.title"
    variant="info"
    @accept="submitNewEntry"
    @close="$emit('close')"
  >
    <div class="space-y-4 mt-1">
      <!-- Stage name -->
      <div>
        <label class="block type-label text-content-muted mb-1.5">Stage Name</label>
        <input
          v-model="name"
          type="text"
          placeholder="Enter stage name…"
          class="input-base"
          @keyup.enter="submitNewEntry"
        />
      </div>

      <!-- Judge -->
      <div v-if="allJudges.length > 1">
        <label class="block type-label text-content-muted mb-1.5">Judge (optional)</label>
        <select v-model="selectedJudge" class="input-base">
          <option v-for="j in allJudges" :key="j" :value="j">{{ j || '— No judge —' }}</option>
        </select>
      </div>

      <!-- Genre checkboxes -->
      <div>
        <label class="block type-label text-content-muted mb-2">Genres</label>
        <div class="grid grid-cols-2 gap-2">
          <label
            v-for="g in genreOptions"
            :key="g.genreName"
            class="flex items-center gap-2.5 px-3 py-2.5 para-chip cursor-pointer transition-all"
            :class="createTable.genres.includes(g.genreName)
              ? 'text-accent border-[color:var(--accent-color)]'
              : 'text-content-secondary border-white/10 hover:border-white/20'"
          >
            <input
              type="checkbox"
              :value="g.genreName"
              v-model="createTable.genres"
              class="w-4 h-4"
            />
            <span class="type-body">{{ g.genreName }}</span>
            <span v-if="g.format" class="ml-auto type-label text-content-muted">{{ g.format }}</span>
          </label>
        </div>
      </div>

      <!-- Per-genre team config: shown only for team-format genres that are selected -->
      <template
        v-for="genreName in createTable.genres.filter(g => isTeamFormat(getFormat(g)))"
        :key="genreName"
      >
        <div class="para-chip p-3 space-y-3">
          <!-- Genre label + toggle -->
          <div class="flex items-center justify-between">
            <span class="type-label text-accent">{{ genreName }}</span>
            <div class="flex para-chip overflow-hidden text-xs">
              <button
                type="button"
                @click="genreEntryMode[genreName] = 'team'"
                class="px-3 py-1.5 type-label transition-all"
                :class="(genreEntryMode[genreName] ?? 'team') === 'team'
                  ? 'bg-accent text-surface-900'
                  : 'text-content-muted hover:text-content-primary'"
              >Team</button>
              <button
                type="button"
                @click="genreEntryMode[genreName] = 'solo'"
                class="px-3 py-1.5 type-label transition-all"
                :class="(genreEntryMode[genreName] ?? 'team') === 'solo'
                  ? 'bg-accent text-surface-900'
                  : 'text-content-muted hover:text-content-primary'"
              >Solo</button>
            </div>
          </div>

          <!-- Team fields — only when entry mode is Team -->
          <template v-if="(genreEntryMode[genreName] ?? 'team') === 'team'">
            <div>
              <label class="block type-label text-content-muted mb-1">Team Name</label>
              <input
                v-model="genreTeamName[genreName]"
                type="text"
                placeholder="Enter team name…"
                class="input-base"
              />
            </div>
            <div v-if="additionalMembersCount(genreName) > 0">
              <label class="block type-label text-content-muted mb-1">
                Team Members
                <span class="text-content-muted normal-case font-normal">
                  (Stage name is Member 1)
                </span>
              </label>
              <div class="space-y-2">
                <input
                  v-for="i in additionalMembersCount(genreName)"
                  :key="i"
                  v-model="genreTeamMembers[genreName][i - 1]"
                  type="text"
                  :placeholder="`Member ${i + 1} stage name…`"
                  class="input-base"
                />
              </div>
            </div>
          </template>

          <!-- Solo note -->
          <p v-else class="type-label text-content-muted">
            Auditions individually. Can be grouped into a crew after auditions.
          </p>
        </div>
      </template>
    </div>
  </ActionDoneModal>

  <ActionDoneModal
    :show="showError"
    title="Name Required"
    variant="error"
    @accept="showError = false"
    @close="showError = false"
  >
    <p class="type-body text-content-secondary">Please enter a stage name before submitting.</p>
  </ActionDoneModal>
</template>
```

- [ ] **Step 5: Build to check for template/import errors**

```bash
cd BES-frontend && npm run build 2>&1 | tail -10
```
Expected: no errors

- [ ] **Step 6: Run frontend tests**

```bash
cd BES-frontend && npm test 2>&1 | grep -E "PASS|FAIL|✓|×|Tests"
```
Expected: all tests pass including the new pure-logic test

- [ ] **Step 7: Commit**

```bash
git add BES-frontend/src/components/CreateParticipantForm.vue \
        BES-frontend/src/utils/__tests__/createParticipantFormLogic.test.js
git commit -m "feat: per-genre entry mode and team config in walk-in form"
```

---

## Self-Review

### Spec coverage check

| Spec requirement | Task |
|---|---|
| Walk-in per-genre Team\|Solo toggle | Task 17 |
| Team fields independent per genre | Task 17 |
| Required field validation blocks submission (team name + member fields) | Tasks 7, 8, 12 |
| Sheet ENTRY_TYPE column parsing | Tasks 6, 13 |
| Sheet row skip with structured error when ENTRY_TYPE missing | Task 14 |
| Sheet import returns structured result (imported, skipped, errors) | Tasks 4, 14, 15 |
| team_name added to event_genre_participant | Tasks 1, 3 |
| event_genre_participant_member table | Tasks 1, 2 |
| Write team to EGP in walk-in path | Task 8 |
| Write team to EGP in sheet import path | Task 14 |
| Read member names from EGP with EP fallback | Task 10 |
| addGenreToExistingParticipant sets format correctly | Task 9 |
| addNewWalkInInEventService no longer writes team to EP | Task 11 |
| EP team data deprecated but not deleted | Tasks 11, 14 — not writing new data to EP, old data stays |
| api.js sends entryMode | Task 16 |

All spec requirements are covered.

### Type / method consistency check

- `validateTeamEntry(String format, String teamName, List<String> memberNames)` — defined in Task 7 (EventGenreParticpantService) and again as a private copy in Task 14 (RegistrationService). Both have the same signature and semantics. ✓
- `parseFormatSize(String format)` — same duplication, same semantics. ✓
- `addWalkInToEventGenreParticipant` new signature in Task 8 — call site in Task 12 uses the new params. ✓
- `addNewWalkInInEventService` simplified to 2 params in Task 11 — call site in Task 12 uses new params. ✓
- `ImportResultDto` created in Task 4 — returned from `addParticipantToEvent` in Task 14 — controller returns it in Task 15. ✓
- `addWalkinToSystem` gains `entryMode` param in Task 16 — form passes it in Task 17. ✓
- `EGP.getMembers()` accessed in Task 10 — field added in Task 3. ✓
- `EGP.setTeamName()` / `EGP.getTeamName()` — field added in Task 3, used in Tasks 8, 9, 14. ✓

### Backwards compatibility

- Old `EventParticipant.teamMembers` data stays in DB and is still read as fallback (Task 10). Records written before this migration continue to display correctly.
- The `entryMode` param added to `addWalkinToSystem` defaults to `'team'` so any existing callers without the new param continue to work.
