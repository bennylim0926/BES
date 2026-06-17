# Granular Results Release — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single `results_released` boolean on `Event` with a 4-mode VARCHAR enum (`NONE` | `SCORE_ONLY` | `FEEDBACK_ONLY` | `BOTH`) so organisers can independently release scores, feedback, both, or neither.

**Architecture:** Bottom-up: DB migration → entity → DTOs → services → controllers → frontend API → views. The pattern mirrors the existing `feedbackEnabled` toggle (#131) — `EventService` broadcasts mode changes via WebSocket to `/topic/release-mode/`. `ResultsService` always builds the full DTO; the `resultsReleaseMode` field tells the frontend which blocks to conditionally render. Hard cut on old endpoints (no external consumers).

**Tech Stack:** Spring Boot, Flyway, Vue 3, STOMP WebSocket

---

### Task 1: DB Migration — `V45__add_results_release_mode.sql`

**Files:**
- Create: `BES/src/main/resources/db/migration/V45__add_results_release_mode.sql`

- [ ] **Step 1: Write the migration**

```sql
-- V45__add_results_release_mode.sql
-- Replaces boolean results_released with a 4-mode VARCHAR enum.
-- NONE = nothing released, SCORE_ONLY = scores only, FEEDBACK_ONLY = feedback only, BOTH = everything.

ALTER TABLE event ADD COLUMN results_release_mode VARCHAR(20) DEFAULT 'NONE';

UPDATE event SET results_release_mode = 'BOTH' WHERE results_released = true;
UPDATE event SET results_release_mode = 'NONE' WHERE results_released = false;

ALTER TABLE event DROP COLUMN results_released;
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/resources/db/migration/V45__add_results_release_mode.sql
git commit -m "feat: add results_release_mode column, backfill from results_released (#156)"
```

---

### Task 2: Entity — `Event.java`

**Files:**
- Modify: `BES/src/main/java/com/example/BES/models/Event.java`

- [ ] **Step 1: Replace `resultsReleased` boolean with `resultsReleaseMode` String + add `ReleaseMode` constants**

Remove lines 29-30:
```java
    @Column(name = "results_released")
    private boolean resultsReleased = false;
```

Add in their place (after `judgingMode` field, before `feedbackEnabled` field):
```java
    @Column(name = "results_release_mode", length = 20)
    private String resultsReleaseMode = "NONE";

    /** Constants for the {@link #resultsReleaseMode} field. */
    public static class ReleaseMode {
        public static final String NONE = "NONE";
        public static final String SCORE_ONLY = "SCORE_ONLY";
        public static final String FEEDBACK_ONLY = "FEEDBACK_ONLY";
        public static final String BOTH = "BOTH";

        private ReleaseMode() {}
    }
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/Event.java
git commit -m "feat: replace resultsReleased boolean with resultsReleaseMode enum (#156)"
```

---

### Task 3: DTOs

**Files:**
- Modify: `BES/src/main/java/com/example/BES/dtos/GetEventDto.java`
- Modify: `BES/src/main/java/com/example/BES/dtos/GetResultsDto.java`
- Create: `BES/src/main/java/com/example/BES/dtos/UpdateResultsReleaseModeDto.java`

- [ ] **Step 1: Update `GetEventDto` — add `resultsReleaseMode` field**

```java
package com.example.BES.dtos;

public class GetEventDto {
    Long id;
    String name;
    boolean paymentRequired;
    boolean feedbackEnabled = true;
    String resultsReleaseMode;

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isPaymentRequired() { return paymentRequired; }
    public boolean isFeedbackEnabled() { return feedbackEnabled; }
    public String getResultsReleaseMode() { return resultsReleaseMode; }
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPaymentRequired(boolean paymentRequired) { this.paymentRequired = paymentRequired; }
    public void setFeedbackEnabled(boolean feedbackEnabled) { this.feedbackEnabled = feedbackEnabled; }
    public void setResultsReleaseMode(String resultsReleaseMode) { this.resultsReleaseMode = resultsReleaseMode; }
}
```

- [ ] **Step 2: Update `GetResultsDto` — add `resultsReleaseMode` at top level**

Add field to the class (after `eventName`, before `categories`):
```java
    private String participantName;
    private String eventName;
    private String resultsReleaseMode;
    private List<CategoryResult> categories;

    public GetResultsDto(String participantName, String eventName, String resultsReleaseMode, List<CategoryResult> categories) {
        this.participantName = participantName;
        this.eventName = eventName;
        this.resultsReleaseMode = resultsReleaseMode;
        this.categories = categories;
    }

    public String getParticipantName() { return participantName; }
    public String getEventName() { return eventName; }
    public String getResultsReleaseMode() { return resultsReleaseMode; }
    public List<CategoryResult> getCategories() { return categories; }
```

- [ ] **Step 3: Create `UpdateResultsReleaseModeDto`**

```java
package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;

public class UpdateResultsReleaseModeDto {
    @NotBlank
    public String eventName;

    @NotBlank
    public String mode;
}
```

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/GetEventDto.java \
        BES/src/main/java/com/example/BES/dtos/GetResultsDto.java \
        BES/src/main/java/com/example/BES/dtos/UpdateResultsReleaseModeDto.java
git commit -m "feat: add resultsReleaseMode to DTOs, create UpdateResultsReleaseModeDto (#156)"
```

---

### Task 4: EventService

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventService.java`

- [ ] **Step 1: Update `getAllEvents` to include `resultsReleaseMode` in DTO mapping**

Line ~88, add after `dto.setFeedbackEnabled(event.isFeedbackEnabled());`:
```java
            dto.setResultsReleaseMode(event.getResultsReleaseMode());
```

- [ ] **Step 2: Replace `releaseResults` + `isResultsReleased` with `setResultsReleaseMode` + `getResultsReleaseMode`**

Remove lines 124-135 (the old `releaseResults` and `isResultsReleased` methods). Add in their place:
```java
    public GetResultsReleaseModeDto getResultsReleaseMode(String eventName) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        return new GetResultsReleaseModeDto(event.getEventName(), event.getResultsReleaseMode());
    }

    public void setResultsReleaseMode(String eventName, String mode) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        event.setResultsReleaseMode(mode);
        repo.save(event);
        messagingTemplate.convertAndSend("/topic/release-mode",
            java.util.Map.of("eventName", eventName, "mode", mode));
    }
```

- [ ] **Step 3: Add import for `GetResultsReleaseModeDto`**

Add this import near the other `Get*` DTO imports (line ~19):
```java
import com.example.BES.dtos.GetResultsReleaseModeDto;
```

- [ ] **Step 4: Create `GetResultsReleaseModeDto`**

Create `BES/src/main/java/com/example/BES/dtos/GetResultsReleaseModeDto.java`:
```java
package com.example.BES.dtos;

public class GetResultsReleaseModeDto {
    public String eventName;
    public String mode;

    public GetResultsReleaseModeDto(String eventName, String mode) {
        this.eventName = eventName;
        this.mode = mode;
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventService.java \
        BES/src/main/java/com/example/BES/dtos/GetResultsReleaseModeDto.java
git commit -m "feat: replace releaseResults with setResultsReleaseMode + WS broadcast (#156)"
```

---

### Task 5: ResultsService

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/ResultsService.java`

- [ ] **Step 1: Gate on mode ≠ NONE instead of boolean; pass `resultsReleaseMode` to DTO**

Replace the early-return check at line 40:
```java
        if (!ep.getEvent().isResultsReleased()) return null;
```

With:
```java
        String mode = ep.getEvent().getResultsReleaseMode();
        if ("NONE".equals(mode)) return null;
```

Then update the constructor call at lines 79-83:
```java
        return new GetResultsDto(
            ep.getDisplayName(),
            ep.getEvent().getEventName(),
            mode,
            categoryResults
        );
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/ResultsService.java
git commit -m "feat: gate ResultsService on resultsReleaseMode enum (#156)"
```

---

### Task 6: EventController

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

- [ ] **Step 1: Replace `release-results` and `results-status` endpoints**

Remove lines 752-777 (the `getResultsStatus` and `releaseResults` endpoint methods).

Add in their place:
```java
    @Operation(summary = "Get Results Release Mode", description = "Returns the current results release mode for the event")
    @GetMapping("/{eventName}/results-release-mode")
    public ResponseEntity<?> getResultsReleaseMode(@PathVariable String eventName) {
        try {
            GetResultsReleaseModeDto dto = eventService.getResultsReleaseMode(eventName);
            return ResponseEntity.ok(dto);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        }
    }

    @Operation(summary = "Set Results Release Mode", description = "Sets which results are visible on the public portal — NONE, SCORE_ONLY, FEEDBACK_ONLY, or BOTH")
    @PostMapping("/{eventName}/results-release-mode")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setResultsReleaseMode(
            @PathVariable String eventName,
            @Valid @RequestBody UpdateResultsReleaseModeDto body) {
        try {
            eventService.setResultsReleaseMode(eventName, body.mode);
            return ResponseEntity.ok(Map.of("message", "Results release mode updated", "mode", body.mode));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        }
    }
```

- [ ] **Step 2: Add import for `GetResultsReleaseModeDto`**

Add near the other DTO imports:
```java
import com.example.BES.dtos.GetResultsReleaseModeDto;
import com.example.BES.dtos.UpdateResultsReleaseModeDto;
```

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/EventController.java
git commit -m "feat: replace /release-results + /results-status with /results-release-mode endpoints (#156)"
```

---

### Task 7: Backend Tests

**Files:**
- Modify: `BES/src/test/java/com/example/BES/services/EventServiceTest.java`

- [ ] **Step 1: Replace old releaseResults tests with tests for all 4 modes + backfill**

Remove lines 60-77 (the `releaseResults_setsFlag` and `releaseResults_throwsWhenNotFound` tests).

Add:
```java
    @Test
    void setResultsReleaseMode_updatesMode() {
        Event e = event("Fest");
        when(repo.findByEventName("Fest")).thenReturn(Optional.of(e));

        service.setResultsReleaseMode("Fest", "SCORE_ONLY");

        assertThat(e.getResultsReleaseMode()).isEqualTo("SCORE_ONLY");
        verify(repo).save(e);
        verify(messagingTemplate).convertAndSend("/topic/release-mode",
            java.util.Map.of("eventName", "Fest", "mode", "SCORE_ONLY"));
    }

    @Test
    void setResultsReleaseMode_allFourModes() {
        Event e = event("Fest");
        when(repo.findByEventName("Fest")).thenReturn(Optional.of(e));

        for (String mode : new String[]{"NONE", "SCORE_ONLY", "FEEDBACK_ONLY", "BOTH"}) {
            service.setResultsReleaseMode("Fest", mode);
            assertThat(e.getResultsReleaseMode()).isEqualTo(mode);
        }
    }

    @Test
    void setResultsReleaseMode_throwsWhenNotFound() {
        when(repo.findByEventName("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setResultsReleaseMode("Missing", "BOTH"))
            .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getResultsReleaseMode_returnsDto() {
        Event e = event("Fest");
        e.setResultsReleaseMode("BOTH");
        when(repo.findByEventName("Fest")).thenReturn(Optional.of(e));

        assertThat(service.getResultsReleaseMode("Fest").mode).isEqualTo("BOTH");
    }

    @Test
    void newEvent_defaultsToNone() {
        Event e = new Event();
        assertThat(e.getResultsReleaseMode()).isEqualTo("NONE");
    }
```

- [ ] **Step 2: Run tests to confirm they pass**

```bash
cd BES && mvn test -Dtest=EventServiceTest -q
```
Expected: all tests pass (5 new tests + existing non-removed tests).

- [ ] **Step 3: Commit**

```bash
git add BES/src/test/java/com/example/BES/services/EventServiceTest.java
git commit -m "test: update EventServiceTest for resultsReleaseMode enum (#156)"
```

---

### Task 8: Frontend API — `api.js`

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Replace `releaseResults` and `getResultsStatus` with new functions**

Remove lines 1075-1100.

In their place add:
```js
export const getResultsReleaseMode = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/results-release-mode`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const setResultsReleaseMode = async (eventName, mode) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/results-release-mode`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, mode })
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: add getResultsReleaseMode / setResultsReleaseMode API functions (#156)"
```

---

### Task 9: Frontend — `Score.vue`

**Files:**
- Modify: `BES-frontend/src/views/Score.vue`

- [ ] **Step 1: Update import — replace `releaseResults` + `getResultsStatus` with `getResultsReleaseMode` + `setResultsReleaseMode`**

Line 3, change:
```js
import { getParticipantScore, getParticipantFeedback, getResultsStatus, releaseResults, getParticipantRefs, getScoringCriteria, setResolvedParticipants, getCategoriesByEvent } from '@/utils/api';
```

To:
```js
import { getParticipantScore, getParticipantFeedback, getResultsReleaseMode, setResultsReleaseMode, getParticipantRefs, getScoringCriteria, setResolvedParticipants, getCategoriesByEvent } from '@/utils/api';
```

- [ ] **Step 2: Replace `resultsReleased` boolean with `resultsReleaseMode` string**

Line 38: replace:
```js
const resultsReleased = ref(false)
```

With:
```js
const resultsReleaseMode = ref('NONE')
```

- [ ] **Step 3: Update `loadAdminData` to use new API**

Lines 115-122, replace:
```js
const loadAdminData = async (eventName) => {
  if (!isAdminOrOrganiser.value || !eventName) return
  const [status, refs] = await Promise.all([
    getResultsStatus(eventName),
    getParticipantRefs(eventName)
  ])
  resultsReleased.value = status?.released ?? false
  refsMap.value = Object.fromEntries((refs || []).map(r => [r.participantId, { name: r.participantName, ref: r.referenceCode }]))
}
```

With:
```js
const loadAdminData = async (eventName) => {
  if (!isAdminOrOrganiser.value || !eventName) return
  const [modeRes, refs] = await Promise.all([
    getResultsReleaseMode(eventName),
    getParticipantRefs(eventName)
  ])
  resultsReleaseMode.value = modeRes?.mode ?? 'NONE'
  refsMap.value = Object.fromEntries((refs || []).map(r => [r.participantId, { name: r.participantName, ref: r.referenceCode }]))
}
```

- [ ] **Step 4: Replace `toggleRelease` with `setMode`**

Lines 415-427, replace:
```js
// Release results toggle
const toggleRelease = async () => {
  const newVal = !resultsReleased.value
  const res = await releaseResults(selectedEvent.value, newVal)
  if (res !== null) {
    resultsReleased.value = newVal
    if (newVal) {
      // Refresh refs in case new participants were added
      const refs = await getParticipantRefs(selectedEvent.value)
      refsMap.value = Object.fromEntries((refs || []).map(r => [r.participantId, { name: r.participantName, ref: r.referenceCode }]))
    }
  }
}
```

With:
```js
// Results release mode control
const MODES = ['NONE', 'SCORE_ONLY', 'FEEDBACK_ONLY', 'BOTH']
const modeLabel = (m) => ({ NONE: 'NOT RELEASED', SCORE_ONLY: 'SCORE ONLY', FEEDBACK_ONLY: 'FEEDBACK ONLY', BOTH: 'SCORE + FEEDBACK' }[m] || m)
const setReleaseMode = async (mode) => {
  const res = await setResultsReleaseMode(selectedEvent.value, mode)
  if (res !== null) {
    resultsReleaseMode.value = mode
    if (mode !== 'NONE') {
      const refs = await getParticipantRefs(selectedEvent.value)
      refsMap.value = Object.fromEntries((refs || []).map(r => [r.participantId, { name: r.participantName, ref: r.referenceCode }]))
    }
  }
}
```

- [ ] **Step 5: Replace the Release Results pill in the template (lines 620-632)**

Replace:
```html
        <!-- Release Results pill (admin/organiser only, Control mode only) -->
        <button
          v-if="isAdminOrOrganiser && mode === 'control'"
          @click="toggleRelease"
          :aria-pressed="resultsReleased"
          class="para-chip-sm px-3 py-1.5 type-label inline-flex items-center gap-1.5 transition-all"
          :class="resultsReleased
            ? 'border-emerald-500/40 text-emerald-400'
            : 'text-content-muted hover:text-content-primary'"
        >
          <span class="w-1.5 h-1.5 rounded-full" :class="resultsReleased ? 'bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.7)]' : 'bg-content-muted/40'"></span>
          {{ resultsReleased ? 'RELEASED' : 'HIDDEN' }}
        </button>
```

With:
```html
        <!-- Results Release mode selector (admin/organiser only, Control mode only) -->
        <div
          v-if="isAdminOrOrganiser && mode === 'control'"
          class="flex p-0.5 border border-surface-600 bg-surface-800/50"
          role="group"
          aria-label="Results release mode"
        >
          <button
            v-for="m in MODES"
            :key="m"
            @click="setReleaseMode(m)"
            :aria-pressed="resultsReleaseMode === m"
            class="para-chip-sm px-3 py-1.5 type-label transition-all"
            :class="resultsReleaseMode === m
              ? 'text-accent border-[color:var(--accent-muted)]'
              : 'text-content-muted hover:text-content-primary'"
          >{{ modeLabel(m) }}</button>
        </div>
```

- [ ] **Step 6: Update references from `resultsReleased` to `resultsReleaseMode` in the template**

Lines that reference `resultsReleased` in the template (lines 765, 789, 1252): change `v-if="resultsReleased && ..."` to `v-if="resultsReleaseMode !== 'NONE' && ..."`.

Line 765:
```html
              <button v-if="resultsReleaseMode !== 'NONE' && refsMap[finalRows[0].participantId]" ...
```

Line 789:
```html
                <button v-if="resultsReleaseMode !== 'NONE' && refsMap[row.participantId]" ...
```

Line 1252:
```html
        <button v-if="resultsReleaseMode !== 'NONE' && refsMap[rowActionSheet.participantId]" ...
```

- [ ] **Step 7: Add WebSocket subscription for release mode updates**

In the `watch(selectedEvent, ...)` block at line ~170, after `subscribeScoreUpdates(newVal)`, add a release-mode subscription. Add a new function:

```js
const subscribeReleaseMode = (eventName) => {
  if (!wsClient.value || !eventName) return
  subscribeToChannel(wsClient.value, `/topic/release-mode`, (msg) => {
    try {
      const body = typeof msg === 'string' ? JSON.parse(msg) : msg
      if (body.eventName === eventName && body.mode) {
        resultsReleaseMode.value = body.mode
      }
    } catch (_) { /* ignore malformed messages */ }
  })
}
```

Call it in the `watch(selectedEvent, ...)` callback at line ~173, right after `subscribeScoreUpdates(newVal)`:
```js
    subscribeReleaseMode(newVal)
```

- [ ] **Step 8: Run frontend build check**

```bash
cd BES-frontend && npm run build
```
Expected: no build errors. (Functional verification needs the full stack.)

- [ ] **Step 9: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "feat: 4-chip release mode selector on Score.vue Control mode (#156)"
```

---

### Task 10: Frontend — `Results.vue`

**Files:**
- Modify: `BES-frontend/src/views/Results.vue`

- [ ] **Step 1: Add computed helpers for mode-based rendering**

Add these computed properties after `groupTags` (line 84, before `</script>`):
```js
const showScores = computed(() => {
  const m = results.value?.resultsReleaseMode
  return m === 'SCORE_ONLY' || m === 'BOTH'
})
const showFeedback = computed(() => {
  const m = results.value?.resultsReleaseMode
  return m === 'FEEDBACK_ONLY' || m === 'BOTH'
})
const isFeedbackOnly = computed(() => results.value?.resultsReleaseMode === 'FEEDBACK_ONLY')
```

- [ ] **Step 2: Wrap the total-score display with conditional**

Line 234: change:
```html
                  <div v-if="category.scores && category.scores.length > 0" class="text-right ml-2 flex-shrink-0">
                    <div class="type-label text-content-muted">Total</div>
                    <div class="type-stat text-[28px]">{{ totalScore(category.scores) }}</div>
                  </div>
```

To:
```html
                  <div v-if="!isFeedbackOnly && category.scores && category.scores.length > 0" class="text-right ml-2 flex-shrink-0">
                    <div class="type-label text-content-muted">Total</div>
                    <div class="type-stat text-[28px]">{{ totalScore(category.scores) }}</div>
                  </div>
```

- [ ] **Step 3: Wrap the Scores section with conditional**

Lines 241-284: wrap the entire Scores block with a condition:

The scores section (currently `v-if="category.scores && category.scores.length > 0"`) should become:
```html
                <!-- Scores -->
                <template v-if="showScores && category.scores && category.scores.length > 0">
                  <!-- existing scores content unchanged -->
                </template>
                <div v-else-if="showScores" class="mb-4">
                  <p class="type-body text-content-muted">No scores recorded yet</p>
                </div>
```

Implement this by changing line 241 from:
```html
                <div v-if="category.scores && category.scores.length > 0" class="mb-4">
```
To:
```html
                <div v-if="showScores && category.scores && category.scores.length > 0" class="mb-4">
```

And line 282-284 from:
```html
                <div v-else class="mb-4">
                  <p class="type-body text-content-muted">No scores recorded yet</p>
                </div>
```
To:
```html
                <div v-else-if="showScores" class="mb-4">
                  <p class="type-body text-content-muted">No scores recorded yet</p>
                </div>
```

- [ ] **Step 4: Wrap the Feedback section with conditional**

Lines 286-332: the feedback `v-if` / `v-else` block:

Line 287: change:
```html
                <template v-if="category.feedback && category.feedback.length > 0">
```
To:
```html
                <template v-if="showFeedback && category.feedback && category.feedback.length > 0">
```

Lines 330-332: change:
```html
                <div v-else class="pt-4" style="border-top: 1px solid rgba(255,255,255,0.07)">
                  <p class="type-label text-content-muted">No judge feedback for this category</p>
                </div>
```
To:
```html
                <div v-else-if="showFeedback" class="pt-4" style="border-top: 1px solid rgba(255,255,255,0.07)">
                  <p class="type-label text-content-muted">No judge feedback for this category</p>
                </div>
```

- [ ] **Step 5: Run build check**

```bash
cd BES-frontend && npm run build
```
Expected: no build errors.

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/Results.vue
git commit -m "feat: conditionally render scores/feedback on Results.vue based on release mode (#156)"
```

---

### Task 11: Frontend — `EventDetails.vue`

**Files:**
- Modify: `BES-frontend/src/views/EventDetails.vue`

Three separate spots need updating in this file.

**Spot 1: Import `getResultsReleaseMode`**

- [ ] **Step 1: Update import line 4**

In the long import statement on line 4, replace `getResultsStatus` with `getResultsReleaseMode`:

```js
import { checkTableExist, getFileId, getResponseDetails, getCategoriesByEvent, getVerifiedParticipantsByEvent, addParticipantToSystem, getSheetSize, getRegisteredParticipantsByEvent, removeParticipantCategory, addCategoryToParticipant, getUnverifiedParticipantsDB, verifyPayment, verifyPaymentBatch, updateEventCategoryFormat, getJudgesByEvent, getJudgesByDivision, addJudgeToEvent, assignJudgeToDivision, removeJudgeFromDivision, removeEventJudge, getScoringCriteria, fetchAllFolderEvents, fetchAllEvents, getCheckinList, checkInParticipant, sendCheckinPreview, getCheckinPreviews, addDivision, renameDivision, updateDivisionSoloAllowed, deleteDivision, getSheetCategories, getSessionTokens, revokeSessionToken, generateToken, getFeedbackEnabled, setFeedbackEnabled, getResultsReleaseMode, getParticipantRefs, insertEventInTable, getEventFeedbackTags, createEventFeedbackGroup, createEventFeedbackTag, updateEventFeedbackTag, deleteEventFeedbackTag, deleteEventFeedbackGroup } from '@/utils/api';
```

- [ ] **Step 2: Replace `resultsReleased` ref with `resultsReleaseMode` ref**

Line 66: change:
```js
const resultsReleased = ref(false)
```
To:
```js
const resultsReleaseMode = ref('NONE')
```

**Spot 2: `loadResultsAndQr` function**

- [ ] **Step 3: Decouple QR visibility from `feedbackEnabled`**

Lines 168-199, replace the function:

```js
// Load results release mode and generate participant QR if anything is released
const loadResultsAndQr = async () => {
  if (!props.eventName) return
  try {
    const modeRes = await getResultsReleaseMode(props.eventName)
    resultsReleaseMode.value = modeRes?.mode ?? 'NONE'

    // Find this user's participant record to get their ref code
    const currentUserParticipantId = authStore.user?.participant?.participantId
    if (currentUserParticipantId && resultsReleaseMode.value !== 'NONE') {
      const refs = await getParticipantRefs(props.eventName)
      const userRef = refs?.find(r => String(r.participantId) === String(currentUserParticipantId))
      if (userRef) {
        // Generate QR code
        loadingQr.value = true
        try {
          const res = await fetch(`/api/v1/results/qr?ref=${encodeURIComponent(userRef.ref)}`, {
            credentials: 'include'
          })
          if (res.ok) {
            const blob = await res.blob()
            qrImageUrl.value = URL.createObjectURL(blob)
          }
        } finally {
          loadingQr.value = false
        }
      }
    }
  } catch (e) {
    console.error('Error loading results QR:', e)
  }
}
```

- [ ] **Step 4: Update the call site in `onMounted`**

Line 1288: the call `await loadResultsAndQr()` is currently inside a condition. Since the function now handles its own guards (no longer checking `feedbackEnabled`), it should always be called. Keep the call where it is but remove any surrounding conditional — the function itself checks `props.eventName`.

At line ~1287, the call is currently:
```js
      // Load results status and participant QR if feedback is enabled
      await loadResultsAndQr()
```
Keep this call, just update the comment:
```js
      // Load results release mode and participant QR
      await loadResultsAndQr()
```

**Spot 3: Template conditionals**

- [ ] **Step 5: Update the check-in confirm QR section (line 3083)**

Change line 3083 from:
```html
          <div v-if="checkinConfirm.phase === 'done' && feedbackEnabled && checkinConfirm.qrImageUrl" class="mb-5">
```
To:
```html
          <div v-if="checkinConfirm.phase === 'done' && resultsReleaseMode !== 'NONE' && checkinConfirm.qrImageUrl" class="mb-5">
```

- [ ] **Step 6: Update the check-in confirm ref-code fallback (line 3095)**

Change line 3095 from:
```html
          <div v-if="checkinConfirm.phase === 'done' && checkinConfirm.refCode && (!feedbackEnabled || !checkinConfirm.qrImageUrl)" class="mb-5">
```
To:
```html
          <div v-if="checkinConfirm.phase === 'done' && checkinConfirm.refCode && resultsReleaseMode !== 'NONE' && !checkinConfirm.qrImageUrl" class="mb-5">
```

- [ ] **Step 7: Update the judge session results QR section (line 2380)**

Line 2380: change from:
```html
    <template v-if="feedbackEnabled && !isAdminOrOrganiser && !isHelper">
```
To:
```html
    <template v-if="resultsReleaseMode !== 'NONE' && !isAdminOrOrganiser && !isHelper">
```

Lines 2386, 2393: change `resultsReleased` to `resultsReleaseMode !== 'NONE'`:

Line 2386:
```html
          <div v-if="resultsReleaseMode !== 'NONE' && qrImageUrl" class="flex flex-col items-center gap-3">
```

Line 2393:
```html
            <template v-if="resultsReleaseMode !== 'NONE'">
```

Lines 2402-2403: the `v-else` branch (the "Waiting for organiser..." message) should show when mode is NONE. Since `resultsReleaseMode !== 'NONE'` handles the positive case, the `v-else` on line 2401 is correct and needs no change.

**Spot 4: Generate QR on check-in confirm**

- [ ] **Step 8: Update the QR generation in the check-in flow (line 1200)**

Change line 1200 from:
```js
  if (feedbackEnabled.value && checkinConfirm.value.refCode) {
```
To:
```js
  if (resultsReleaseMode.value !== 'NONE' && checkinConfirm.value.refCode) {
```

- [ ] **Step 9: Add WebSocket subscription for release mode updates**

In the WebSocket subscription block (around lines 1299-1326, where other `/topic/` subscriptions live), add:

```js
    subscribeToChannel(wsClient, '/topic/release-mode', (msg) => {
      try {
        const body = typeof msg === 'string' ? JSON.parse(msg) : msg
        if (body.eventName === props.eventName && body.mode) {
          resultsReleaseMode.value = body.mode
          // Re-generate QR when mode changes to non-NONE
          if (body.mode !== 'NONE') loadResultsAndQr()
        }
      } catch (_) { /* ignore malformed messages */ }
    })
```

- [ ] **Step 10: Run build check**

```bash
cd BES-frontend && npm run build
```
Expected: no build errors.

- [ ] **Step 11: Commit**

```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "feat: decouple EventDetails QR visibility from feedbackEnabled, use resultsReleaseMode (#156)"
```

---

### Task 12: Final Verification

- [ ] **Step 1: Run all backend tests**

```bash
cd BES && mvn test -q
```
Expected: all tests pass.

- [ ] **Step 2: Run all frontend tests**

```bash
cd BES-frontend && npm test -- --run
```
Expected: all tests pass.

- [ ] **Step 3: Build backend**

```bash
cd BES && mvn clean package -DskipTests -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Build frontend**

```bash
cd BES-frontend && npm run build
```
Expected: no errors; `dist/` produced.

- [ ] **Step 5: Commit any remaining changes**

```bash
git status
```
If clean, confirm done. If any changes remain, commit them.
