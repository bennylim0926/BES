# Battle Views Branding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add event logo + active genre display to both battle views, standardize BattleOverlay background, rework bracket slot colors, and redesign the judge panel's post-reveal resting position.

**Architecture:** Bottom-up: DB migration → entity → service (logoUrl field, upload/delete methods) → controller (two new endpoints) → api.js (two new functions) → BattleControl.vue (logo UI in overlay settings) → BracketVisualization.vue (remove header+ticker, add logo-banner, rework slot colors) → BattleOverlay.vue (background, logo watermark, timer reposition, judge panel rework).

**Tech Stack:** Spring Boot (Java), Flyway migrations, Vue 3 Composition API, scoped CSS animations.

---

## File Map

| File | Action | What changes |
|------|--------|--------------|
| `BES/src/main/resources/db/migration/V34__add_logo_url.sql` | CREATE | Add `logo_url` column |
| `BES/src/main/java/com/example/BES/models/BattleGenreState.java` | MODIFY | Add `logoUrl` JPA field |
| `BES/src/main/java/com/example/BES/services/BattleService.java` | MODIFY | Add `logoUrl` to `EventBattleState`; update persist/load/get/broadcast; add upload/delete service methods |
| `BES/src/main/java/com/example/BES/controllers/BattleController.java` | MODIFY | Add `POST /battle/logo-upload` and `DELETE /battle/logo` endpoints |
| `BES-frontend/src/utils/api.js` | MODIFY | Add `uploadBattleLogo`, `deleteBattleLogo` |
| `BES-frontend/src/views/BattleControl.vue` | MODIFY | Logo upload/delete UI in overlay settings panel |
| `BES-frontend/src/views/BracketVisualization.vue` | MODIFY | Remove header + ticker; add logo-banner; rework `slotClass()`; update CSS |
| `BES-frontend/src/views/BattleOverlay.vue` | MODIFY | Remove transparent bg; add logo watermark + genre; rework timer; rework judge panel exit/rest animations |

---

## Task 1: DB migration — add logo_url

**Files:**
- Create: `BES/src/main/resources/db/migration/V34__add_logo_url.sql`

- [ ] **Step 1: Create migration file**

```sql
ALTER TABLE battle_genre_state ADD COLUMN logo_url VARCHAR(512);
```

- [ ] **Step 2: Verify migration naming**

Run: `ls BES/src/main/resources/db/migration/ | sort -V | tail -3`
Expected: `V32__add_tiebreaker_to_battle_genre_state.sql`, `V33__add_format_timer.sql`, `V34__add_logo_url.sql`

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V34__add_logo_url.sql
git commit -m "chore: add logo_url column to battle_genre_state (V34)"
```

---

## Task 2: Backend — entity, service, controller

**Files:**
- Modify: `BES/src/main/java/com/example/BES/models/BattleGenreState.java`
- Modify: `BES/src/main/java/com/example/BES/services/BattleService.java`
- Modify: `BES/src/main/java/com/example/BES/controllers/BattleController.java`

### Step 1: Add logoUrl field to BattleGenreState entity

- [ ] Open `BES/src/main/java/com/example/BES/models/BattleGenreState.java`. After the `formatTimerJson` field (line ~47), add:

```java
@Column(name = "logo_url", length = 512)
private String logoUrl;
```

Lombok `@Data` generates the getter/setter automatically. No other changes needed in this file.

### Step 2: Add logoUrl to EventBattleState in-memory state

- [ ] In `BES/src/main/java/com/example/BES/services/BattleService.java`, find the `EventBattleState` inner class (around line 738). After the `overlayConfig` field, add:

```java
String logoUrl = null;
```

The full block should now read:
```java
Map<String, Object> overlayConfig = new HashMap<>(Map.of(
    "showImages", true,
    "leftColor",  "#dc2626",
    "rightColor", "#2563eb"
));
String logoUrl = null;
```

### Step 3: Add broadcastOverlayConfig helper and update getOverlayConfig

- [ ] In `BattleService.java`, find `getOverlayConfig(String eventName)` (around line 370). Replace both overloads:

```java
public Map<String, Object> getOverlayConfig() {
    return getOverlayConfig(resolveEvent(null));
}

public Map<String, Object> getOverlayConfig(String eventName) {
    EventBattleState s = stateFor(eventName);
    Map<String, Object> cfg = new HashMap<>(s.overlayConfig);
    cfg.put("logoUrl", s.logoUrl);
    return cfg;
}

private void broadcastOverlayConfig(String eventName) {
    messagingTemplate.convertAndSend(
        "/topic/battle/" + eventName + "/overlay-config",
        getOverlayConfig(eventName)
    );
}
```

### Step 4: Update setOverlayConfigService to use broadcastOverlayConfig

- [ ] Find `setOverlayConfigService` (around line 374). Replace the `messagingTemplate.convertAndSend(...)` call:

```java
public void setOverlayConfigService(String eventName, SetOverlayConfigDto dto) {
    EventBattleState s = stateFor(eventName);
    Map<String, Object> newConfig = new HashMap<>();
    newConfig.put("showImages", dto.isShowImages());
    newConfig.put("leftColor",  dto.getLeftColor());
    newConfig.put("rightColor", dto.getRightColor());
    s.overlayConfig = newConfig;
    broadcastOverlayConfig(eventName);
}
```

### Step 5: Persist logoUrl in persistActiveState

- [ ] Find `persistActiveState` (around line 609). After `st.setUpdatedAt(LocalDateTime.now());` and before `battleGenreStateRepository.save(st);`, add:

```java
st.setLogoUrl(s.logoUrl);
```

### Step 6: Restore logoUrl in loadGenreStateIntoMemory

- [ ] Find `loadGenreStateIntoMemory` (around line 650). After `s.lastFormatTimerPayload = null;` block (the final else in the formatTimer block), add:

```java
s.logoUrl = dbState.getLogoUrl();
```

### Step 7: Add uploadLogoService and deleteLogoService

- [ ] In `BattleService.java`, add these two public methods after `setOverlayConfigService`:

```java
public String uploadLogoService(String eventName, MultipartFile file) throws IOException {
    String original = file.getOriginalFilename();
    String ext = (original != null && original.contains("."))
        ? original.substring(original.lastIndexOf('.'))
        : ".png";
    String safeEvent = eventName.replaceAll("[^a-zA-Z0-9_-]", "_");
    String filename = "__logo_" + safeEvent + ext;

    Path uploadDir = Paths.get("uploads");
    if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);
    Path dest = uploadDir.resolve(filename).normalize();
    if (!dest.startsWith(uploadDir.normalize())) throw new IOException("Invalid path");
    Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

    EventBattleState s = stateFor(eventName);
    s.logoUrl = "/api/v1/battle/uploads/" + filename;
    persistActiveState(eventName);
    broadcastOverlayConfig(eventName);
    return s.logoUrl;
}

public void deleteLogoService(String eventName) throws IOException {
    EventBattleState s = stateFor(eventName);
    if (s.logoUrl != null) {
        String filename = s.logoUrl.substring(s.logoUrl.lastIndexOf('/') + 1);
        Path file = Paths.get("uploads").resolve(filename).normalize();
        if (file.startsWith(Paths.get("uploads").normalize())) {
            Files.deleteIfExists(file);
        }
    }
    s.logoUrl = null;
    persistActiveState(eventName);
    broadcastOverlayConfig(eventName);
}
```

- [ ] Add missing imports to `BattleService.java` if not already present (check top of file):

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.web.multipart.MultipartFile;
```

### Step 8: Add logo endpoints to BattleController

- [ ] In `BattleController.java`, after the `setOverlayConfig` endpoint (around line 381), add:

```java
@PostMapping("/logo-upload")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> uploadLogo(
        @RequestParam("file") MultipartFile file,
        @RequestParam(required = false) String event) throws IOException {
    String url = battleService.uploadLogoService(resolveEvent(event), file);
    return ResponseEntity.ok(Map.of("logoUrl", url));
}

@DeleteMapping("/logo")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> deleteLogo(
        @RequestParam(required = false) String event) throws IOException {
    battleService.deleteLogoService(resolveEvent(event));
    return ResponseEntity.ok(Map.of("message", "Logo deleted"));
}
```

### Step 9: Build and verify

- [ ] Run from `BES/` directory:

```bash
mvn clean package -DskipTests
```

Expected: `BUILD SUCCESS`

- [ ] **Commit**

```bash
git add BES/src/main/java/com/example/BES/models/BattleGenreState.java \
        BES/src/main/java/com/example/BES/services/BattleService.java \
        BES/src/main/java/com/example/BES/controllers/BattleController.java
git commit -m "feat: add logo upload/delete endpoints and persist logoUrl in overlay config (#109)"
```

---

## Task 3: Frontend API functions

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Add uploadBattleLogo and deleteBattleLogo after the existing `uploadImage` function**

Find `export const uploadImage` (around line 697). After that entire function, add:

```js
export const uploadBattleLogo = async (eventName, file) => {
  try {
    const formData = new FormData()
    formData.append('file', file)
    const url = eventName
      ? `/api/v1/battle/logo-upload?event=${encodeURIComponent(eventName)}`
      : '/api/v1/battle/logo-upload'
    return await fetch(`${domain}${url}`, {
      method: 'POST',
      credentials: 'include',
      body: formData,
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const deleteBattleLogo = async (eventName) => {
  try {
    const url = eventName
      ? `/api/v1/battle/logo?event=${encodeURIComponent(eventName)}`
      : '/api/v1/battle/logo'
    return await fetch(`${domain}${url}`, {
      method: 'DELETE',
      credentials: 'include',
    })
  } catch (e) {
    console.log(e)
    return null
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: add uploadBattleLogo and deleteBattleLogo API functions (#109)"
```

---

## Task 4: BattleControl.vue — logo upload/delete UI

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

- [ ] **Step 1: Import new API functions**

Find the import line at the top of `BattleControl.vue` (line 2) that imports from `@/utils/api`. Add `uploadBattleLogo` and `deleteBattleLogo` to the existing import:

```js
import { ..., uploadBattleLogo, deleteBattleLogo } from '@/utils/api'
```

- [ ] **Step 2: Add logo upload handlers in the `<script setup>` section**

Find `const onFileChange = async (e) => {` (around line 376). After that function's closing brace, add:

```js
const onLogoUpload = async (e) => {
  const file = e.target.files[0]
  if (!file) return
  await uploadBattleLogo(selectedEvent.value, file)
  e.target.value = ''
  // overlayConfig.logoUrl is updated via WS broadcast from backend
}

const onLogoDelete = async () => {
  await deleteBattleLogo(selectedEvent.value)
}
```

- [ ] **Step 3: Add logo UI inside the overlay settings panel**

Find the overlay settings `<div class="overlay-settings-body">` section (around line 2612). Inside that div, after the last `overlay-setting-row` (the Show Images toggle row), add:

```html
<div class="overlay-setting-row overlay-setting-logo">
  <span class="overlay-setting-label">Event Logo</span>
  <div class="logo-upload-group">
    <img
      v-if="overlayConfig.logoUrl"
      :src="overlayConfig.logoUrl"
      class="logo-preview-thumb"
      alt="Event logo"
    />
    <label class="para-chip-sm px-3 py-1.5 type-label inline-flex items-center gap-1.5 cursor-pointer">
      <i class="pi pi-upload text-xs"></i>
      {{ overlayConfig.logoUrl ? 'Replace' : 'Upload' }}
      <input type="file" accept="image/*" @change="onLogoUpload" class="hidden" />
    </label>
    <button
      v-if="overlayConfig.logoUrl"
      @click="onLogoDelete"
      class="para-chip-sm px-3 py-1.5 type-label inline-flex items-center gap-1.5"
    >
      <i class="pi pi-trash text-xs"></i>
      Remove
    </button>
  </div>
</div>
```

- [ ] **Step 4: Add logo UI styles**

Find the `<style scoped>` section of `BattleControl.vue`. At the end of the existing styles, add:

```css
.overlay-setting-logo { align-items: flex-start; padding-top: 6px; }
.logo-upload-group { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.logo-preview-thumb {
  width: 48px; height: 48px;
  object-fit: contain;
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 4px;
  background: rgba(255,255,255,0.04);
}
```

- [ ] **Step 5: Verify overlayConfig default includes logoUrl**

Find the `overlayConfig` ref (around line 38):
```js
const overlayConfig = ref({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' })
```

Change it to:
```js
const overlayConfig = ref({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb', logoUrl: null })
```

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: add logo upload/delete UI to BattleControl overlay settings (#109)"
```

---

## Task 5: BracketVisualization.vue — remove header + ticker, add logo banner, rework slot colors

**Files:**
- Modify: `BES-frontend/src/views/BracketVisualization.vue`

### Step 1: Update overlayConfig default to include logoUrl

- [ ] Find the `overlayConfig` ref (line 9):

```js
const overlayConfig  = ref({ leftColor: '#dc2626', rightColor: '#2563eb' })
```

Replace with:

```js
const overlayConfig  = ref({ leftColor: '#dc2626', rightColor: '#2563eb', logoUrl: null })
```

### Step 2: Update slotClass() to use slot-winner-round

- [ ] Find `const slotClass = (match, slot, isFinal = false)` (around line 75). Replace the winner return:

**Old:**
```js
if (match[2] === match[slot]) return isFinal ? 'slot-winner' : (slot === 0 ? 'slot-winner-left' : 'slot-winner-right')
```

**New:**
```js
if (match[2] === match[slot]) return isFinal ? 'slot-winner' : 'slot-winner-round'
```

### Step 3: Remove the header element from the template

- [ ] Find and remove the entire `<header class="bracket-header">` block (lines 470–482):

```html
<!-- ── Header ─────────────────────────────────────── -->
<header class="bracket-header">
  <div class="header-brand">
    <span class="brand-dot"></span>
    <span class="brand-title">LIVE BRACKET</span>
    <span class="brand-dot"></span>
  </div>
  <div v-if="activePair" class="active-pill">
    <span class="pill-dot"></span>
    {{ activePair.left }}
    <span class="vs-sep">vs</span>
    {{ activePair.right }}
  </div>
</header>
```

### Step 4: Add the logo-banner element in its place

- [ ] Immediately after the champion reveal `</Transition>` block (and before the empty-state `v-if`), add:

```html
<!-- ── Logo banner ───────────────────────────────── -->
<div class="logo-banner" aria-hidden="true">
  <img
    v-if="overlayConfig.logoUrl"
    :src="overlayConfig.logoUrl"
    class="logo-banner-img"
    alt=""
  />
  <span v-if="currentGenre" class="logo-banner-genre">{{ currentGenre }}</span>
</div>
```

### Step 5: Remove the ticker-bar element from the template

- [ ] Find and remove the entire `<div class="ticker-bar">` block (lines 635–657 approximately). Remove from `<!-- ── LED Ticker` comment through the closing `</div>`.

### Step 6: Update bracket-area bottom padding

- [ ] Find `.bracket-area` CSS (around line 786):

```css
.bracket-area  { flex: 1; display: flex; flex-direction: row; gap: var(--col-gap); padding: 8px 16px 12px; ... }
```

Change `padding: 8px 16px 12px` to `padding: 8px 16px 20px`.

### Step 7: Replace slot-winner-left and slot-winner-right CSS with slot-winner-round

- [ ] Find and remove the `slot-winner-left` block:
```css
.slot-winner-left {
  background: color-mix(in srgb, var(--left-color) 20%, transparent) !important;
  border-color: color-mix(in srgb, var(--left-color) 55%, transparent) !important;
  box-shadow: 0 0 14px color-mix(in srgb, var(--left-color) 25%, transparent) !important;
}
.slot-winner-left .battler-name {
  color: color-mix(in srgb, var(--left-color) 85%, #fff) !important;
}
```

- [ ] Find and remove the `slot-winner-right` block:
```css
.slot-winner-right {
  background: color-mix(in srgb, var(--right-color) 20%, transparent) !important;
  border-color: color-mix(in srgb, var(--right-color) 55%, transparent) !important;
  box-shadow: 0 0 14px color-mix(in srgb, var(--right-color) 25%, transparent) !important;
}
.slot-winner-right .battler-name {
  color: color-mix(in srgb, var(--right-color) 85%, #fff) !important;
}
```

- [ ] In their place add the `slot-winner-round` style:

```css
/* Non-final round winner — generic white/grey */
.slot-winner-round {
  background: rgba(255,255,255,0.07) !important;
  border-color: rgba(255,255,255,0.38) !important;
  box-shadow: 0 0 10px rgba(255,255,255,0.12) !important;
}
.slot-winner-round .battler-name {
  color: rgba(255,255,255,0.95) !important;
  opacity: 1;
}
```

### Step 8: Update slot-loser opacity

- [ ] Find `.slot-loser` (around line 875):

```css
.slot-loser { background: rgba(255,255,255,0.02) !important; border-color: rgba(255,255,255,0.03) !important; opacity: 0.42; }
```

Change `opacity: 0.42` to `opacity: 0.62`.

### Step 9: Remove header/ticker/pill CSS and add logo-banner CSS

- [ ] Find and remove the `/* ── Header ───────────────────────────────────────────── */` CSS block including `.bracket-header`, `.header-brand`, `.brand-dot`, `.brand-title`, `.active-pill`, `.pill-dot`, `.vs-sep`.

- [ ] Find and remove the `/* ── LED Ticker ───────────────────────────────────────── */` CSS block including `.ticker-bar`, `.ticker-label`, `.ticker-dot`, `.ticker-track`, `.ticker-reel`, `.ticker-item`, `.ticker-tag`, `.ticker-now`, `.ticker-next`, `.ticker-genre`, `.ticker-text`, `.ticker-sep`.

- [ ] Remove the `@keyframes tickerScroll` keyframe.

- [ ] Remove the `@keyframes dotPulse` keyframe (was used by brand-dot and pill-dot — check if used elsewhere; if `.slot-glow` doesn't use it, remove it).

- [ ] Add logo-banner CSS. Place it after the `/* ── Root ───────────────────── */` block:

```css
/* ── Logo banner ─────────────────────────────────── */
.logo-banner {
  flex-shrink: 0;
  height: 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px 20px;
  position: relative;
  z-index: 10;
}
.logo-banner-img {
  max-height: 52px;
  max-width: 240px;
  object-fit: contain;
  filter: drop-shadow(0 0 8px rgba(255,255,255,0.12));
}
.logo-banner-genre {
  font-family: 'Anton SC', sans-serif;
  font-size: 11px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.48);
  text-align: center;
}
/* When no logo: show hairline rules either side of genre text */
.logo-banner:not(:has(.logo-banner-img)) .logo-banner-genre {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  letter-spacing: 0.32em;
  color: rgba(255,255,255,0.55);
}
.logo-banner:not(:has(.logo-banner-img)) .logo-banner-genre::before,
.logo-banner:not(:has(.logo-banner-img)) .logo-banner-genre::after {
  content: '';
  flex: 1;
  max-width: 80px;
  height: 1px;
  background: rgba(255,255,255,0.12);
}
```

- [ ] Update `--header-h` CSS token (it's set on `.bracket-root` but no longer used since header is removed). Remove `--header-h: 52px;` from the token block.

- [ ] **Step 10: Commit**

```bash
git add BES-frontend/src/views/BracketVisualization.vue
git commit -m "feat: remove header+ticker, add logo banner, rework slot colors in BracketVisualization (#109)"
```

---

## Task 6: BattleOverlay.vue — background, logo watermark, timer, judge panel

**Files:**
- Modify: `BES-frontend/src/views/BattleOverlay.vue`

### Step 1: Add judgeRestSide reactive ref

- [ ] Find `const judgeAnim = ref('')` in the `<script setup>` section (around line 107). After it, add:

```js
// judgeRestSide: '' | 'left' | 'right' — where the judge panel rests after score reveal
const judgeRestSide = ref('')
```

### Step 2: Update judgePanelClass computed

- [ ] Find `const judgePanelClass = computed(...)` (around line 158). Replace it:

```js
const judgePanelClass = computed(() => {
  if (isSmoke.value) return 'smoke-judge-always-on'
  if (judgeRestSide.value === 'right') return 'judge-rest-right'
  if (judgeRestSide.value === 'left')  return 'judge-rest-left'
  return judgeAnim.value
})
```

### Step 3: Update updateScore() — replace judge retreat with side-rest

- [ ] Find `updateScore` (around line 326). Find the "Phase 2: panel scales down and retreats to top" block:

```js
// Phase 2: panel scales down and retreats to top
judgeAnim.value = 'judge-retreat-top'
await useDelay().wait(550)
if (!ok()) return
judgeAnim.value = 'judge-at-top'

// Brief pause before winner reveal
await useDelay().wait(150)
if (!ok()) return

currentWinner.value = msg.message
```

Replace it with:

```js
// Phase 2: panel retreats to beside winner name
judgeAnim.value = ''  // stop slam animation
judgeRestSide.value = msg.message === 0 ? 'right' : 'left'
await useDelay().wait(550)  // let rest animation complete
if (!ok()) return

currentWinner.value = msg.message
```

### Step 4: Update updateBattlePair() — add side-exit animation

- [ ] Find the judge cleanup block inside `updateBattlePair` (the `if (!hideJudgeDecision.value)` block, around line 231):

```js
if (!hideJudgeDecision.value) {
  glitching.value = true
  judgeAnim.value = 'slide-up'
  await useDelay().wait(100)
  if (unmounted) return

  if (currentWinner.value === 0) {
    leftReset.value = true
  } else if (currentWinner.value === 1) {
    rightReset.value = true
  }
  vsAnim.value = ''

  await useDelay().wait(280)
  if (unmounted) return
  glitching.value          = false
  hideJudgeDecision.value  = true
  judgeAnim.value          = ''
  votesVisible.value       = false
  winnerTagVisible.value   = false
  await useDelay().wait(50)
  if (unmounted) return
  leftReset.value  = false
  rightReset.value = false
}
```

Replace with:

```js
if (!hideJudgeDecision.value) {
  glitching.value = true
  if (judgeRestSide.value) {
    judgeAnim.value = judgeRestSide.value === 'right' ? 'judge-exit-right' : 'judge-exit-left'
    judgeRestSide.value = ''
  } else {
    judgeAnim.value = 'slide-up'
  }
  await useDelay().wait(100)
  if (unmounted) return

  if (currentWinner.value === 0) {
    leftReset.value = true
  } else if (currentWinner.value === 1) {
    rightReset.value = true
  }
  vsAnim.value = ''

  await useDelay().wait(280)
  if (unmounted) return
  glitching.value          = false
  hideJudgeDecision.value  = true
  judgeAnim.value          = ''
  votesVisible.value       = false
  winnerTagVisible.value   = false
  await useDelay().wait(50)
  if (unmounted) return
  leftReset.value  = false
  rightReset.value = false
}
```

### Step 5: Clear judgeRestSide in LOCKED phase handler

- [ ] Find the LOCKED phase handler (inside `subscribeToChannel(cPhase, topic('phase'), ...)`, around line 450):

```js
if (msg.phase === 'LOCKED' && prevPhase !== 'LOCKED') {
  animToken++
  hideJudgeDecision.value = true
  judgeAnim.value         = ''
  votesVisible.value      = false
  winnerTagVisible.value  = false
  leftWin.value           = false
  rightWin.value          = false
  leftReset.value         = false
  rightReset.value        = false
```

Add `judgeRestSide.value = ''` after `judgeAnim.value = ''`:

```js
if (msg.phase === 'LOCKED' && prevPhase !== 'LOCKED') {
  animToken++
  hideJudgeDecision.value = true
  judgeAnim.value         = ''
  judgeRestSide.value     = ''
  votesVisible.value      = false
  ...
```

### Step 6: Remove transparent-page logic from onMounted and onUnmounted

- [ ] In `onMounted`, find and remove these two lines:

```js
document.documentElement.classList.add('transparent-page')
document.body.classList.add('transparent-page')
```

- [ ] In `onUnmounted`, find and remove:

```js
document.documentElement.classList.remove('transparent-page')
document.body.classList.remove('transparent-page')
```

The `onUnmounted` block that removes the app background can stay.

### Step 7: Add logo watermark + genre to template

- [ ] In the `<template>`, find the `<!-- Screen-reader live region -->` comment. Immediately **before** it, add:

```html
<!-- Logo + genre watermark (top center, always visible) -->
<div class="logo-watermark" aria-hidden="true">
  <img
    v-if="overlayConfig.showImages !== undefined && overlayConfig.logoUrl"
    :src="overlayConfig.logoUrl"
    class="logo-watermark-img"
    alt=""
  />
  <span v-if="activeGenreName" class="logo-watermark-genre">{{ activeGenreName }}</span>
</div>
```

### Step 8: Rework timer element in template

- [ ] Find the `<!-- Broadcast timer bar -->` `<Transition>` block. Replace the entire inner div:

**Old:**
```html
<div v-if="showTimer" class="timer-overlay">
  <div class="timer-bar-container">
    <div class="timer-progress-track">
      <div
        class="timer-progress-fill"
        :style="{ width: (timerState.totalDuration > 0 ? (timerState.timeLeft / timerState.totalDuration) * 100 : 0) + '%' }"
        :class="{ 'timer-fill-warning': timerState.timeLeft <= 10 }"
      ></div>
    </div>
    <div class="timer-countdown" :class="{ 'timer-text-warning': timerState.timeLeft <= 10 }">
      {{ Math.floor(timerState.timeLeft / 60) }}:{{ String(timerState.timeLeft % 60).padStart(2, '0') }}
    </div>
  </div>
</div>
```

**New:**
```html
<div v-if="showTimer" class="timer-overlay">
  <div class="timer-countdown" :class="{ 'timer-text-warning': timerState.timeLeft <= 10 }">
    {{ Math.floor(timerState.timeLeft / 60) }}:{{ String(timerState.timeLeft % 60).padStart(2, '0') }}
  </div>
</div>
```

### Step 9: Update global `<style>` block — remove transparent-page rules

- [ ] Find the global `<style>` block (around line 906–915):

```html
<style>
/* ── Transparent background (OBS) — must be global ─────────── */
html.transparent-page,
body.transparent-page,
html.transparent-page #app,
body.transparent-page #app {
  background: transparent !important;
  background-color: transparent !important;
}
</style>
```

Remove this entire `<style>` block.

### Step 10: Update scoped CSS — overlay-root background, timer, logo watermark, judge animations

- [ ] In the `<style scoped>` section, find `.overlay-root` (around line 961). Add a background:

```css
.overlay-root {
  position: fixed;
  top: 0; left: 0;
  width: 100vw; height: 100vh;
  overflow: hidden;
  font-family: 'Anton SC', sans-serif;
  background: #060818;
  --left-color: #dc2626;
  --right-color: #2563eb;
}
```

- [ ] Find the `/* ── Broadcast timer (top center bar) ─────────────── */` block. Replace the entire block:

```css
/* ── Broadcast timer (top right, text-only) ──────────── */
.timer-overlay {
  position: absolute;
  top: 14px; right: 20px;
  z-index: 100;
  pointer-events: none;
}
.timer-countdown {
  font-family: 'Anton SC', sans-serif;
  font-size: 26px;
  letter-spacing: 0.06em;
  color: rgba(255,255,255,0.82);
  text-shadow: 0 0 12px rgba(255,255,255,0.18);
}
.timer-text-warning { color: #ef4444; text-shadow: 0 0 12px rgba(239,68,68,0.5); animation: timer-pulse-text 0.5s ease-in-out infinite alternate; }
.timer-enter-enter-active { animation: timer-slide-in 200ms ease-out; }
.timer-enter-leave-active { animation: timer-slide-out 160ms ease-in; }
@keyframes timer-slide-in  { from { opacity: 0; transform: translateX(12px); } to { opacity: 1; transform: translateX(0); } }
@keyframes timer-slide-out { from { opacity: 1; transform: translateX(0); }   to { opacity: 0; transform: translateX(12px); } }
@keyframes timer-pulse-text { from { opacity: 0.7; } to { opacity: 1; } }
```

- [ ] Add logo watermark CSS after the timer block:

```css
/* ── Logo + genre watermark (top center) ─────────────── */
.logo-watermark {
  position: absolute;
  top: 0; left: 50%;
  transform: translateX(-50%);
  z-index: 45;
  pointer-events: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 10px 0 0;
}
.logo-watermark-img {
  max-height: 38px;
  max-width: 160px;
  object-fit: contain;
  filter: drop-shadow(0 0 6px rgba(255,255,255,0.10));
}
.logo-watermark-genre {
  font-family: 'Anton SC', sans-serif;
  font-size: 9px;
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.38);
}
```

- [ ] Find and remove the `.judge-retreat-top`, `.judge-at-top` CSS classes and the `@keyframes judgeRetreatTop` keyframe (they are replaced by the new side-rest animations).

- [ ] Add new judge rest/exit CSS. Place it after the `.judge-slam-center` utility class:

```css
/* Judge rests to right (left wins) — slides down + drifts right */
.judge-rest-right {
  animation: judgeRestRight 520ms cubic-bezier(0.34, 1.1, 0.64, 1) forwards;
}
@keyframes judgeRestRight {
  0%   { transform: translateX(0)    translateY(42vh) scale(1.38); }
  100% { transform: translateX(28vw) translateY(74vh) scale(0.72); }
}

/* Judge rests to left (right wins) */
.judge-rest-left {
  animation: judgeRestLeft 520ms cubic-bezier(0.34, 1.1, 0.64, 1) forwards;
}
@keyframes judgeRestLeft {
  0%   { transform: translateX(0)     translateY(42vh) scale(1.38); }
  100% { transform: translateX(-28vw) translateY(74vh) scale(0.72); }
}

/* Exit: slide off same side with fade */
.judge-exit-right {
  animation: judgeExitRight 280ms cubic-bezier(0.55, 0, 1, 0.45) forwards;
}
@keyframes judgeExitRight {
  0%   { transform: translateX(28vw)  translateY(74vh) scale(0.72); opacity: 1; }
  100% { transform: translateX(110vw) translateY(74vh) scale(0.5);  opacity: 0; }
}
.judge-exit-left {
  animation: judgeExitLeft 280ms cubic-bezier(0.55, 0, 1, 0.45) forwards;
}
@keyframes judgeExitLeft {
  0%   { transform: translateX(-28vw)  translateY(74vh) scale(0.72); opacity: 1; }
  100% { transform: translateX(-110vw) translateY(74vh) scale(0.5);  opacity: 0; }
}
```

- [ ] Remove the now-unused `.timer-bar-container`, `.timer-progress-track`, `.timer-progress-fill`, `.timer-fill-warning` CSS rules. Also remove `@keyframes timer-pulse-bar` if present.

- [ ] **Commit**

```bash
git add BES-frontend/src/views/BattleOverlay.vue
git commit -m "feat: standardize BattleOverlay background, add logo watermark, rework timer and judge panel animations (#109)"
```

---

## Task 7: Local Docker verify

- [ ] Run the local Docker verification skill to rebuild and confirm all containers are healthy:

```
/local-docker-verify
```

Expected: frontend and backend containers healthy, no build errors.

- [ ] Open `http://localhost/battle/bracket?event=<your-test-event>` in a browser.
  - Confirm: no header bar, no LED ticker, logo banner visible at top center with genre text
  - Confirm: active pair slots are red/blue, round winners are white-grey, final winner is gold, losers are less dim
  - Confirm: dark `#060818` background (no transparency)

- [ ] Open `http://localhost/battle/overlay?event=<your-test-event>` in browser.
  - Confirm: dark `#060818` background
  - Logo watermark visible at top center when logo is uploaded
  - Timer shows top-right (text only, no progress bar) when a countdown is active

- [ ] Open `http://localhost/battle/control` and expand Overlay Settings.
  - Confirm: logo upload/delete controls visible
  - Upload a test image → confirm logo appears in BracketVisualization and BattleOverlay immediately
  - Delete the logo → confirm it disappears from both views

---

## Task 8: Judge panel animation smoke test

This task is manual — it cannot be unit-tested because it requires a live battle.

- [ ] In BattleControl, set up a battle with 2 judges, start a VOTING phase, have both judges vote.

- [ ] Click "Reveal Score" → confirm:
  - Judge panel slams to center (existing behaviour, unchanged)
  - Votes appear
  - After 1500ms pause, panel animates to bottom-right (if left wins) or bottom-left (if right wins)
  - Winner panel expands simultaneously
  - Panel rests beside winner name at ~74vh, scaled to ~72%

- [ ] Click "Next" (new pair) → confirm:
  - Judge panel exits to the same side it was resting on (right→slides further right, left→slides further left)
  - Glitch overlay fires
  - New pair slams in cleanly

- [ ] Confirm LOCKED phase reset: if operator clicks Next mid-score-reveal, judge panel disappears cleanly (animToken aborts updateScore, LOCKED handler clears judgeRestSide).
