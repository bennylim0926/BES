# Battle Audition Sub-Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `pairSubMode` field to `EventCategory` (SHOWCASE | BATTLE) that changes how PAIR-mode auditions build rounds — BATTLE skips gaps, supports 3-way rounds, and shows LEFT/MIDDLE/RIGHT position labels across emcee, judge, and OBS display screens.

**Architecture:** A shared `buildPairs(participants, pairSubMode)` helper in `utils/auditionPairs.js` centralises the pair-building logic currently duplicated in `EmceeRoundView` and `PairScoreCards`. The sub-mode is persisted on `EventCategory`, exposed via a new API endpoint, and flows into the display state WebSocket payload so `AuditionDisplay` receives it automatically. All three consumer components (`EmceeRoundView`, `PairScoreCards`, `AuditionDisplay`) call the same position-label helper and conditionally render LEFT/MIDDLE/RIGHT badges.

**Tech Stack:** Java 21 / Spring Boot / Flyway (backend); Vue 3 Composition API / Vitest (frontend)

## Global Constraints

- `pairSubMode` values are exactly `"SHOWCASE"` (default) and `"BATTLE"` — no other values.
- DB migration filename is `V49__add_pair_sub_mode_to_event_category.sql` (V48 is the latest existing migration).
- All existing PAIR-mode behaviour is unchanged when `pairSubMode === 'SHOWCASE'`.
- Position label color scheme: LEFT = `text-amber-400`, MIDDLE = `text-accent`, RIGHT = `text-content-muted`.
- Follow the design system: parallelogram chip shape (`clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%)`), Oswald font, uppercase labels.
- Backend layer order: migration → model → service → controller → DTO.
- Never edit existing Flyway migration files.

---

## File Map

| Action | File |
|--------|------|
| Create | `BES/src/main/resources/db/migration/V49__add_pair_sub_mode_to_event_category.sql` |
| Modify | `BES/src/main/java/com/example/BES/models/EventCategory.java` |
| Modify | `BES/src/main/java/com/example/BES/dtos/GetEventCategoryDto.java` |
| Modify | `BES/src/main/java/com/example/BES/services/EventCategoryService.java` |
| Modify | `BES/src/main/java/com/example/BES/controllers/EventController.java` |
| Create | `BES-frontend/src/utils/auditionPairs.js` |
| Create | `BES-frontend/src/utils/__tests__/auditionPairs.test.js` |
| Modify | `BES-frontend/src/utils/api.js` |
| Modify | `BES-frontend/src/views/EventDetails.vue` |
| Modify | `BES-frontend/src/views/AuditionList.vue` |
| Modify | `BES-frontend/src/components/EmceeRoundView.vue` |
| Modify | `BES-frontend/src/components/PairScoreCards.vue` |
| Modify | `BES-frontend/src/views/AuditionDisplay.vue` |

---

### Task 1: DB migration + backend model, service, controller, DTO

**Files:**
- Create: `BES/src/main/resources/db/migration/V49__add_pair_sub_mode_to_event_category.sql`
- Modify: `BES/src/main/java/com/example/BES/models/EventCategory.java`
- Modify: `BES/src/main/java/com/example/BES/dtos/GetEventCategoryDto.java`
- Modify: `BES/src/main/java/com/example/BES/services/EventCategoryService.java`
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

**Interfaces:**
- Produces: `GET /api/v1/event/{eventName}/categories` returns `pairSubMode` in each category object
- Produces: `POST /api/v1/event/{eventName}/categories/{categoryId}/pair-sub-mode` accepts `{ "pairSubMode": "BATTLE" }`

- [ ] **Step 1: Create the migration file**

Create `BES/src/main/resources/db/migration/V49__add_pair_sub_mode_to_event_category.sql`:

```sql
ALTER TABLE event_category ADD COLUMN pair_sub_mode VARCHAR(20) NOT NULL DEFAULT 'SHOWCASE';
```

- [ ] **Step 2: Add field to EventCategory model**

In `BES/src/main/java/com/example/BES/models/EventCategory.java`, add after the `numberColor` field:

```java
@Column(name = "pair_sub_mode")
private String pairSubMode = "SHOWCASE";
```

- [ ] **Step 3: Add field to GetEventCategoryDto**

In `BES/src/main/java/com/example/BES/dtos/GetEventCategoryDto.java`, add after `numberColor`:

```java
public String pairSubMode;
```

The full file should now be:

```java
package com.example.BES.dtos;

public class GetEventCategoryDto {
    public Long eventCategoryId;
    public String name;
    public String format;
    public String roundLabel;
    public String numberColor;
    public String pairSubMode;
    public String sheetAliases;
    public boolean soloAllowed = true;
    public long participantCount;
}
```

- [ ] **Step 4: Map pairSubMode in getCategoriesByEventService**

In `EventCategoryService.java`, inside `getCategoriesByEventService`, add one line after `dto.numberColor = ec.getNumberColor();`:

```java
dto.pairSubMode = ec.getPairSubMode() != null ? ec.getPairSubMode() : "SHOWCASE";
```

- [ ] **Step 5: Add updatePairSubMode service method**

In `EventCategoryService.java`, add after `updateNumberColor`:

```java
public void updatePairSubMode(Long id, String pairSubMode) {
    EventCategory ec = eventCategoryRepo.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
    ec.setPairSubMode("BATTLE".equals(pairSubMode) ? "BATTLE" : "SHOWCASE");
    eventCategoryRepo.save(ec);
}
```

- [ ] **Step 6: Add controller endpoint**

In `EventController.java`, add after the `updateCategoryNumberColor` endpoint (around line 280):

```java
@Operation(summary = "Update Category Pair Sub-Mode", description = "Sets whether PAIR mode uses SHOWCASE or BATTLE audition rules")
@PostMapping("/{eventName}/categories/{categoryId}/pair-sub-mode")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<?> updateCategoryPairSubMode(
        @PathVariable String eventName,
        @PathVariable Long categoryId,
        @RequestBody Map<String, String> body) {
    try {
        eventCategoryService.updatePairSubMode(categoryId, body.get("pairSubMode"));
        return ResponseEntity.ok().build();
    } catch (RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
```

- [ ] **Step 7: Build backend and verify migration runs**

```bash
cd BES
mvn clean package -DskipTests
```

Expected: `BUILD SUCCESS`. Then run the app and confirm Flyway applies V49 without error:

```bash
mvn spring-boot:run
```

Look for: `Successfully applied 1 migration to schema "public", now at version v49`

- [ ] **Step 8: Smoke-test the endpoints**

```bash
# Get categories (check pairSubMode appears, defaults to "SHOWCASE")
curl -s -b <session-cookie> http://localhost:5050/api/v1/event/<eventName>/categories | python3 -m json.tool | grep pairSubMode

# Update to BATTLE
curl -s -X POST -b <session-cookie> -H 'Content-Type: application/json' \
  -d '{"pairSubMode":"BATTLE"}' \
  http://localhost:5050/api/v1/event/<eventName>/categories/<categoryId>/pair-sub-mode
# Expected: HTTP 200, empty body

# Verify it persisted
curl -s -b <session-cookie> http://localhost:5050/api/v1/event/<eventName>/categories | python3 -m json.tool | grep pairSubMode
# Expected: "pairSubMode": "BATTLE"
```

- [ ] **Step 9: Commit**

```bash
git add BES/src/main/resources/db/migration/V49__add_pair_sub_mode_to_event_category.sql \
        BES/src/main/java/com/example/BES/models/EventCategory.java \
        BES/src/main/java/com/example/BES/dtos/GetEventCategoryDto.java \
        BES/src/main/java/com/example/BES/services/EventCategoryService.java \
        BES/src/main/java/com/example/BES/controllers/EventController.java
git commit -m "feat: add pairSubMode to EventCategory (SHOWCASE/BATTLE)"
```

---

### Task 2: Shared pair-building helper + tests

**Files:**
- Create: `BES-frontend/src/utils/auditionPairs.js`
- Create: `BES-frontend/src/utils/__tests__/auditionPairs.test.js`

**Interfaces:**
- Produces: `buildPairs(participants, pairSubMode)` → `Array<Array<slot>>` where `slot` is `{ auditionNumber, participantName, ... }` or `{ _placeholder: true, auditionNumber }`
- Produces: `getPositionLabel(slotIndex, roundLength)` → `'LEFT' | 'MIDDLE' | 'RIGHT'`
- Consumes: nothing from earlier tasks (pure functions)

- [ ] **Step 1: Write the failing tests**

Create `BES-frontend/src/utils/__tests__/auditionPairs.test.js`:

```js
import { describe, it, expect } from 'vitest'
import { buildPairs, getPositionLabel } from '../auditionPairs.js'

const p = (n) => ({ auditionNumber: n, participantName: `P${n}` })

describe('buildPairs — SHOWCASE', () => {
  it('returns empty array for no participants', () => {
    expect(buildPairs([], 'SHOWCASE')).toEqual([])
  })

  it('fills gaps with placeholders', () => {
    const result = buildPairs([p(1), p(4)], 'SHOWCASE')
    expect(result).toHaveLength(2)
    expect(result[0][0].auditionNumber).toBe(1)
    expect(result[0][1]).toMatchObject({ _placeholder: true, auditionNumber: 2 })
    expect(result[1][0]).toMatchObject({ _placeholder: true, auditionNumber: 3 })
    expect(result[1][1].auditionNumber).toBe(4)
  })

  it('handles even participant count with no gaps', () => {
    const result = buildPairs([p(1), p(2), p(3), p(4)], 'SHOWCASE')
    expect(result).toHaveLength(2)
    expect(result[0].map(s => s.auditionNumber)).toEqual([1, 2])
    expect(result[1].map(s => s.auditionNumber)).toEqual([3, 4])
  })
})

describe('buildPairs — BATTLE', () => {
  it('returns empty array for no participants', () => {
    expect(buildPairs([], 'BATTLE')).toEqual([])
  })

  it('pairs real participants compactly, skipping gaps', () => {
    const result = buildPairs([p(1), p(4)], 'BATTLE')
    expect(result).toHaveLength(1)
    expect(result[0].map(s => s.auditionNumber)).toEqual([1, 4])
  })

  it('even count: creates n/2 two-way rounds', () => {
    const result = buildPairs([p(1), p(2), p(5), p(6)], 'BATTLE')
    expect(result).toHaveLength(2)
    expect(result[0].map(s => s.auditionNumber)).toEqual([1, 2])
    expect(result[1].map(s => s.auditionNumber)).toEqual([5, 6])
  })

  it('odd count ≥ 3: last participant joins previous pair as 3-way', () => {
    const result = buildPairs([p(1), p(2), p(3), p(4), p(5)], 'BATTLE')
    expect(result).toHaveLength(2)
    expect(result[0].map(s => s.auditionNumber)).toEqual([1, 2])
    expect(result[1].map(s => s.auditionNumber)).toEqual([3, 4, 5])
  })

  it('odd count = 1: single participant in its own round (no 3-way without a previous pair)', () => {
    const result = buildPairs([p(3)], 'BATTLE')
    expect(result).toHaveLength(1)
    expect(result[0].map(s => s.auditionNumber)).toEqual([3])
  })

  it('no _placeholder slots ever appear in BATTLE mode', () => {
    const result = buildPairs([p(1), p(3), p(7)], 'BATTLE')
    result.flat().forEach(slot => expect(slot._placeholder).toBeFalsy())
  })

  it('sorts by auditionNumber before pairing even if input is unsorted', () => {
    const result = buildPairs([p(5), p(1), p(3)], 'BATTLE')
    expect(result[1].map(s => s.auditionNumber)).toEqual([3, 5])
  })
})

describe('getPositionLabel', () => {
  it('2-way: index 0 = LEFT, index 1 = RIGHT', () => {
    expect(getPositionLabel(0, 2)).toBe('LEFT')
    expect(getPositionLabel(1, 2)).toBe('RIGHT')
  })

  it('3-way: index 0 = LEFT, index 1 = MIDDLE, index 2 = RIGHT', () => {
    expect(getPositionLabel(0, 3)).toBe('LEFT')
    expect(getPositionLabel(1, 3)).toBe('MIDDLE')
    expect(getPositionLabel(2, 3)).toBe('RIGHT')
  })
})
```

- [ ] **Step 2: Run tests — verify they fail**

```bash
cd BES-frontend
npm test -- auditionPairs
```

Expected: FAIL with `Cannot find module '../auditionPairs.js'`

- [ ] **Step 3: Create the helper**

Create `BES-frontend/src/utils/auditionPairs.js`:

```js
/**
 * Build pair rounds from a flat participant list.
 *
 * SHOWCASE: iterate 1..maxAuditionNumber, fill gaps with _placeholder slots.
 * BATTLE:   compact sequential pairing over real participants only.
 *           Odd count → last participant folds into previous pair (3-way).
 *
 * @param {Array} participants  Objects with at minimum { auditionNumber }
 * @param {string} pairSubMode  'SHOWCASE' | 'BATTLE'
 * @returns {Array<Array>}      Each inner array is one round (1–3 slots)
 */
export function buildPairs(participants, pairSubMode) {
  const sorted = [...participants].sort((a, b) => a.auditionNumber - b.auditionNumber)
  if (!sorted.length) return []

  if (pairSubMode !== 'BATTLE') {
    const maxNum = sorted[sorted.length - 1].auditionNumber
    const result = []
    for (let i = 1; i <= maxNum; i += 2) {
      result.push([
        sorted.find(p => p.auditionNumber === i) ?? { _placeholder: true, auditionNumber: i },
        sorted.find(p => p.auditionNumber === i + 1) ?? { _placeholder: true, auditionNumber: i + 1 },
      ])
    }
    return result
  }

  // BATTLE: compact sequential pairing
  const rounds = []
  for (let i = 0; i < sorted.length; i += 2) {
    rounds.push([sorted[i], sorted[i + 1]].filter(Boolean))
  }
  // Odd count ≥ 3: fold last lone participant into previous pair → 3-way
  if (sorted.length % 2 !== 0 && rounds.length >= 2) {
    const lone = rounds.pop()[0]
    rounds[rounds.length - 1].push(lone)
  }
  return rounds
}

/**
 * Map a slot's index within its round to a position label.
 * For 2-way: LEFT | RIGHT
 * For 3-way: LEFT | MIDDLE | RIGHT
 *
 * @param {number} slotIndex   0-based index within the round array
 * @param {number} roundLength 2 or 3
 * @returns {'LEFT'|'MIDDLE'|'RIGHT'}
 */
export function getPositionLabel(slotIndex, roundLength) {
  if (roundLength === 3) {
    if (slotIndex === 0) return 'LEFT'
    if (slotIndex === 1) return 'MIDDLE'
    return 'RIGHT'
  }
  return slotIndex === 0 ? 'LEFT' : 'RIGHT'
}
```

- [ ] **Step 4: Run tests — verify they pass**

```bash
cd BES-frontend
npm test -- auditionPairs
```

Expected: all 10 tests PASS

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/utils/auditionPairs.js \
        BES-frontend/src/utils/__tests__/auditionPairs.test.js
git commit -m "feat: add buildPairs / getPositionLabel helper for battle audition sub-mode"
```

---

### Task 3: Frontend API function + EventDetails sub-mode picker

**Files:**
- Modify: `BES-frontend/src/utils/api.js`
- Modify: `BES-frontend/src/views/EventDetails.vue`

**Interfaces:**
- Consumes: Task 1's `POST /api/v1/event/{eventName}/categories/{categoryId}/pair-sub-mode`
- Produces: `updateCategoryPairSubMode(eventName, eventCategoryId, pairSubMode)` exported from `api.js`

- [ ] **Step 1: Add API function to api.js**

In `BES-frontend/src/utils/api.js`, add after `updateCategoryNumberColor` (around line 1365):

```js
export const updateCategoryPairSubMode = async (eventName, eventCategoryId, pairSubMode) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/categories/${eventCategoryId}/pair-sub-mode`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ pairSubMode })
    })
    return res.ok
  } catch (e) {
    console.error(e)
    return false
  }
}
```

- [ ] **Step 2: Import updateCategoryPairSubMode in EventDetails.vue**

In `EventDetails.vue`, find the import line for `updateCategoryRoundLabel` / `updateCategoryNumberColor` and add `updateCategoryPairSubMode` to it:

```js
import { ..., updateCategoryRoundLabel, updateCategoryNumberColor, updateCategoryPairSubMode } from '@/utils/api'
```

- [ ] **Step 3: Add onPairSubModeChange handler in EventDetails.vue**

In `EventDetails.vue`, in the `<script setup>` block, add this function near the `onJudgingModeChange` function:

```js
const pairSubModeSaving = ref({}) // keyed by eventCategoryId

async function onPairSubModeChange(categoryId, mode) {
  pairSubModeSaving.value = { ...pairSubModeSaving.value, [categoryId]: true }
  const div = eventDivisions.value.find(d => d.eventCategoryId === categoryId)
  if (!div) return
  const prev = div.pairSubMode ?? 'SHOWCASE'
  div.pairSubMode = mode
  const ok = await updateCategoryPairSubMode(selectedEvent.value, categoryId, mode)
  if (!ok) div.pairSubMode = prev
  pairSubModeSaving.value = { ...pairSubModeSaving.value, [categoryId]: false }
}
```

- [ ] **Step 4: Add per-category sub-mode picker in EventDetails template**

In `EventDetails.vue`, find the PRESENTATION section. It currently ends at approximately:

```html
      <p class="type-prose-sm text-content-muted mt-3">
        <template v-if="judgingMode === 'SOLO'">
          Each participant appears individually on the audition display screen.
          Judges score one dancer at a time.
        </template>
        <template v-else>
          Two participants appear side by side on the audition display screen.
          Judges compare and score both dancers simultaneously.
        </template>
      </p>
    </div>
```

Replace the `<template v-else>` block (inside the `<p>` tag) and add a new section after the closing `</p>`, so it becomes:

```html
      <p class="type-prose-sm text-content-muted mt-3">
        <template v-if="judgingMode === 'SOLO'">
          Each participant appears individually on the audition display screen.
          Judges score one dancer at a time.
        </template>
        <template v-else>
          Two participants appear side by side on the audition display screen.
          Judges compare and score both dancers simultaneously.
        </template>
      </p>

      <!-- Per-category pair sub-mode when PAIR is active -->
      <template v-if="judgingMode === 'PAIR' && eventDivisions.length > 0">
        <div class="section-rule mt-5 mb-3">
          <span class="section-rule-label">Audition Style Per Category</span>
          <div class="section-rule-line"></div>
        </div>
        <div class="flex flex-col gap-2">
          <div
            v-for="div in eventDivisions"
            :key="div.eventCategoryId"
            class="flex items-center justify-between gap-3 para-chip-sm px-3 py-2"
          >
            <span class="type-name text-content-primary">{{ div.name }}</span>
            <div class="flex items-center gap-1.5">
              <button
                v-for="mode in ['SHOWCASE', 'BATTLE']"
                :key="mode"
                @click="onPairSubModeChange(div.eventCategoryId, mode)"
                :disabled="pairSubModeSaving[div.eventCategoryId]"
                class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150"
                :class="(div.pairSubMode ?? 'SHOWCASE') === mode
                  ? 'text-accent border-[color:var(--accent-muted)] bg-[var(--accent-subtle)]'
                  : 'text-content-muted hover:text-content-primary'"
              >{{ mode }}</button>
              <span v-if="pairSubModeSaving[div.eventCategoryId]" class="type-label text-content-muted text-[10px]">Saving…</span>
            </div>
          </div>
        </div>
      </template>
    </div>
```

- [ ] **Step 5: Manual verify in browser**

Start the dev server (`npm run dev`), open EventDetails for an event, go to the Scoring Settings sub-tab. Switch to PAIR mode. Confirm the "Audition Style Per Category" section appears with SHOWCASE/BATTLE buttons for each category. Click BATTLE — confirm it saves (network tab shows 200) and the button highlights. Refresh — confirm the selection persists (loaded from `GetEventCategoryDto.pairSubMode`).

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/utils/api.js \
        BES-frontend/src/views/EventDetails.vue
git commit -m "feat: per-category pair sub-mode picker in EventDetails"
```

---

### Task 4: AuditionList.vue — pass pairSubMode prop

**Files:**
- Modify: `BES-frontend/src/views/AuditionList.vue`

**Interfaces:**
- Consumes: `currentDivision` computed (already exists: `eventDivisions.value.find(d => d.name.toLowerCase() === selectedCategory.value.toLowerCase()) ?? null`)
- Produces: `:pairSubMode` prop on `EmceeRoundView` and `PairScoreCards`

- [ ] **Step 1: Pass pairSubMode to EmceeRoundView**

Find the `<EmceeRoundView` block in `AuditionList.vue` (around line 994). Add `:pairSubMode` after `:numberColor`:

```html
<EmceeRoundView
  ref="emceeRoundRef"
  :key="selectedCategory"
  :participants="filteredParticipantsForEmceeView"
  :mode="judgingMode"
  :eventName="selectedEvent"
  :categoryName="selectedCategory"
  :roundLabel="currentDivision?.roundLabel ?? null"
  :numberColor="currentDivision?.numberColor ?? null"
  :pairSubMode="currentDivision?.pairSubMode ?? 'SHOWCASE'"
/>
```

- [ ] **Step 2: Pass pairSubMode to PairScoreCards**

Find the `<PairScoreCards` block (around line 1067). Add `:pairSubMode`:

```html
<PairScoreCards
  v-if="judgingMode === 'PAIR'"
  ref="pairCardsRef"
  :cards="filteredParticipantsForJudge"
  :feedbackData="feedbackGiven"
  :criteria="criteria"
  :feedbackEnabled="feedbackEnabled"
  :pairSubMode="currentDivision?.pairSubMode ?? 'SHOWCASE'"
  @open-feedback="openFeedbackPopout"
  @remove-tag="removeTag"
  @score-change="autoSave"
  @submit="confirmSubmit('Submit Scores', 'Are you sure you want to submit all scores now?')"
  @jump="showMiniMenu = true"
/>
```

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/AuditionList.vue
git commit -m "feat: thread pairSubMode prop from AuditionList to EmceeRoundView and PairScoreCards"
```

---

### Task 5: EmceeRoundView.vue — battle pair logic + position badges

**Files:**
- Modify: `BES-frontend/src/components/EmceeRoundView.vue`

**Interfaces:**
- Consumes: `buildPairs(participants, pairSubMode)` and `getPositionLabel(slotIndex, roundLength)` from `../utils/auditionPairs.js`
- Consumes: `:pairSubMode` prop from Task 4

- [ ] **Step 1: Add import and prop**

At the top of `<script setup>` in `EmceeRoundView.vue`, add the import:

```js
import { buildPairs, getPositionLabel } from '@/utils/auditionPairs'
```

Add `pairSubMode` to `defineProps`:

```js
const props = defineProps({
  participants: { type: Array, required: true },
  mode:         { type: String, default: 'SOLO' },
  eventName:    { type: String, default: '' },
  categoryName: { type: String, default: '' },
  roundLabel:   { type: String, default: null },
  numberColor:  { type: String, default: null },
  pairSubMode:  { type: String, default: 'SHOWCASE' },
})
```

- [ ] **Step 2: Replace the rounds computed**

Find the `rounds` computed (currently lines 37–52). Replace the entire computed with:

```js
const rounds = computed(() => {
  if (props.mode !== 'PAIR') {
    return [...props.participants]
      .sort((a, b) => a.auditionNumber - b.auditionNumber)
      .map(p => [p])
  }
  return buildPairs(props.participants, props.pairSubMode)
})
```

- [ ] **Step 3: Gate gapAfterCurrent in BATTLE mode**

Find the `gapAfterCurrent` computed. Wrap it so it returns `[]` immediately in BATTLE mode:

```js
const gapAfterCurrent = computed(() => {
  if (props.pairSubMode === 'BATTLE') return []
  const current = currentRoundSlots.value
  if (!current.length) return []
  const nextRound = rounds.value[currentRound.value]
  if (!nextRound || !nextRound.length) return []
  const currentReals = current.filter(s => !s._placeholder).map(s => s.auditionNumber)
  const nextReals = nextRound.filter(s => !s._placeholder).map(s => s.auditionNumber)
  if (!currentReals.length || !nextReals.length) return []
  const lastCurrent = Math.max(...currentReals)
  const firstNext = Math.min(...nextReals)
  if (firstNext - lastCurrent <= 1) return []
  const missing = []
  for (let i = lastCurrent + 1; i < firstNext; i++) missing.push(i)
  return missing
})
```

- [ ] **Step 4: Add pairSubMode to buildStatePayload**

In `buildStatePayload`, add `pairSubMode` to the returned object:

```js
return {
  eventName: props.eventName,
  categoryName: props.categoryName,
  mode: props.mode,
  currentRound: currentRound.value,
  totalRounds: totalRounds.value,
  currentSlots: current,
  nextSlots: next,
  timerStartedAt: timerState.startedAt ?? null,
  timerDuration: timerState.duration ?? null,
  timerRunning: timerState.running ?? false,
  baselineDuration: baselineDuration.value,
  roundLabel: props.roundLabel ?? null,
  numberColor: props.numberColor ?? null,
  pairSubMode: props.pairSubMode,
}
```

- [ ] **Step 5: Add position badges to the NOW card template**

In the `<template>` section, find the NOW card's slot loop (`<template v-for="(slot, sIdx) in currentRoundSlots">`). The existing block looks like:

```html
<template v-for="(slot, sIdx) in currentRoundSlots" :key="sIdx">
  <div v-if="slot._placeholder" class="text-amber-400/60 text-sm italic py-2">
    #{{ slot.auditionNumber }} — Not Registered
  </div>
  <div v-else>
    <div v-if="mode === 'PAIR' && sIdx > 0" class="text-white/20 text-sm my-1 pl-1">&amp;</div>
    <div class="flex items-baseline gap-2">
      <span class="type-stat" style="font-size: 2rem;">#{{ slot.auditionNumber }}</span>
      <span class="type-name text-content-primary" style="font-size: clamp(1.5rem, 6vw, 2.8rem);">{{ slot.participantName }}</span>
    </div>
    <div v-if="slot.memberNames?.length" class="type-prose text-content-muted mt-0.5 pl-1" style="font-size:14px;">{{ slot.memberNames.join(' · ') }}</div>
    <div v-if="slot.judgeName" class="text-xs text-white/25 mt-0.5 pl-1">{{ slot.judgeName }}</div>
  </div>
</template>
```

Replace it with:

```html
<template v-for="(slot, sIdx) in currentRoundSlots" :key="sIdx">
  <div v-if="slot._placeholder" class="text-amber-400/60 text-sm italic py-2">
    #{{ slot.auditionNumber }} — Not Registered
  </div>
  <div v-else>
    <div v-if="mode === 'PAIR' && sIdx > 0" class="text-white/20 text-sm my-1 pl-1">&amp;</div>
    <div class="flex items-baseline gap-2 flex-wrap">
      <!-- Position badge (BATTLE mode only) -->
      <span
        v-if="pairSubMode === 'BATTLE'"
        class="type-label px-1.5 py-0.5 flex-shrink-0"
        style="clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%); font-size: 10px; border: 1px solid currentColor; opacity: 0.85;"
        :class="{
          'text-amber-400':       getPositionLabel(sIdx, currentRoundSlots.length) === 'LEFT',
          'text-accent':          getPositionLabel(sIdx, currentRoundSlots.length) === 'MIDDLE',
          'text-content-muted':   getPositionLabel(sIdx, currentRoundSlots.length) === 'RIGHT',
        }"
      >{{ getPositionLabel(sIdx, currentRoundSlots.length) }}</span>
      <span class="type-stat" style="font-size: 2rem;">#{{ slot.auditionNumber }}</span>
      <span class="type-name text-content-primary" style="font-size: clamp(1.5rem, 6vw, 2.8rem);">{{ slot.participantName }}</span>
    </div>
    <div v-if="slot.memberNames?.length" class="type-prose text-content-muted mt-0.5 pl-1" style="font-size:14px;">{{ slot.memberNames.join(' · ') }}</div>
    <div v-if="slot.judgeName" class="text-xs text-white/25 mt-0.5 pl-1">{{ slot.judgeName }}</div>
  </div>
</template>
```

- [ ] **Step 6: Add position badges to the queue template**

In the queue section, find the slot loop inside `v-for="({ slots, roundNumber }, uIdx) in visibleRounds"`:

```html
<div v-else class="flex items-start gap-2 flex-wrap">
  <span class="type-stat text-[18px] flex-shrink-0" ...>#{{ slot.auditionNumber }}</span>
  ...
</div>
```

Replace with:

```html
<div v-else class="flex items-start gap-2 flex-wrap">
  <!-- Position badge in queue (BATTLE mode, reduced opacity) -->
  <span
    v-if="pairSubMode === 'BATTLE'"
    class="type-label px-1 py-0.5 flex-shrink-0"
    style="clip-path: polygon(3px 0%, 100% 0%, calc(100% - 3px) 100%, 0% 100%); font-size: 9px; border: 1px solid currentColor; opacity: 0.5;"
    :class="{
      'text-amber-400':     getPositionLabel(sIdx, slots.length) === 'LEFT',
      'text-accent':        getPositionLabel(sIdx, slots.length) === 'MIDDLE',
      'text-content-muted': getPositionLabel(sIdx, slots.length) === 'RIGHT',
    }"
  >{{ getPositionLabel(sIdx, slots.length) }}</span>
  <span class="type-stat text-[18px] flex-shrink-0" :class="uIdx === visibleRounds.length - 1 ? 'text-content-primary' : 'text-content-muted'">#{{ slot.auditionNumber }}</span>
  <div class="min-w-0">
    <span class="type-name block" style="font-size:18px" :class="uIdx === visibleRounds.length - 1 ? 'text-content-primary' : 'text-content-muted'">{{ slot.participantName }}</span>
    <span v-if="slot.memberNames?.length" class="type-prose text-content-muted block" style="font-size:14px;">{{ slot.memberNames.join(' · ') }}</span>
  </div>
  <span v-if="mode === 'PAIR' && sIdx === 0" class="text-white/20 text-xs">&amp;</span>
</div>
```

- [ ] **Step 7: Manual verify**

In the browser, set a category to BATTLE mode (Task 3). Open the emcee view. Confirm:
- Rounds build without placeholder slots
- NOW card shows LEFT/RIGHT amber/muted badges
- Queue shows faint position badges
- Switching to SHOWCASE removes all badges and placeholders return

- [ ] **Step 8: Commit**

```bash
git add BES-frontend/src/components/EmceeRoundView.vue
git commit -m "feat: battle audition pair logic + position badges in EmceeRoundView"
```

---

### Task 6: PairScoreCards.vue — battle pair logic + position labels

**Files:**
- Modify: `BES-frontend/src/components/PairScoreCards.vue`

**Interfaces:**
- Consumes: `buildPairs` and `getPositionLabel` from `@/utils/auditionPairs`
- Consumes: `:pairSubMode` prop from Task 4

- [ ] **Step 1: Add import and prop**

In `<script setup>` of `PairScoreCards.vue`, add:

```js
import { buildPairs, getPositionLabel } from '@/utils/auditionPairs'
```

Add to `defineProps`:

```js
const props = defineProps({
  cards:           { type: Array,   required: true },
  feedbackData:    { type: Object,  default: () => new Map() },
  criteria:        { type: Array,   default: () => [] },
  feedbackEnabled: { type: Boolean, default: true },
  pairSubMode:     { type: String,  default: 'SHOWCASE' },
})
```

- [ ] **Step 2: Replace the pairs computed**

Find the `pairs` computed (currently lines 71–83). Replace entirely with:

```js
const pairs = computed(() => buildPairs(props.cards, props.pairSubMode))
```

- [ ] **Step 3: Add position label above each number selector button**

Find the audition number selector buttons block. It currently starts with:

```html
<button
  v-for="(card, slotIdx) in pair"
  :key="slotIdx"
  @click="activeParticipantNum = card.auditionNumber"
  ...
>
  <span class="type-stat leading-none" style="font-size: 1.6rem; color: inherit">#{{ card.auditionNumber }}</span>
</button>
```

Replace the inner content of the button with:

```html
<button
  v-for="(card, slotIdx) in pair"
  :key="slotIdx"
  @click="activeParticipantNum = card.auditionNumber"
  :disabled="card._placeholder"
  class="flex-shrink-0 flex flex-col items-center justify-center px-3 py-2 para-chip-sm transition-all duration-150 active:scale-95 disabled:opacity-20 disabled:cursor-not-allowed min-w-0"
  :class="!card._placeholder && activeParticipantNum === card.auditionNumber && isActivePair(pairIdx)
    ? ''
    : !card._placeholder && card.submitted
      ? 'text-emerald-300 border-emerald-500/50'
      : !card._placeholder && card.saving
        ? 'text-amber-300 border-amber-400/50'
        : 'text-white/70 border-white/30'"
  :style="!card._placeholder && activeParticipantNum === card.auditionNumber && isActivePair(pairIdx)
    ? 'font-size: 1.6rem; background: var(--accent-color); border-color: var(--accent-color); box-shadow: 0 0 14px var(--accent-muted); color: #111111'
    : !card._placeholder && card.submitted
      ? 'font-size: 1.6rem; background: rgba(52,211,153,0.12); box-shadow: 0 0 8px rgba(52,211,153,0.2)'
      : !card._placeholder && card.saving
        ? 'font-size: 1.6rem; background: rgba(245,158,11,0.08)'
        : 'font-size: 1.6rem; background: rgba(255,255,255,0.06)'"
>
  <!-- Position label (BATTLE mode only) -->
  <span
    v-if="pairSubMode === 'BATTLE' && !card._placeholder"
    class="type-label leading-none mb-1"
    style="font-size: 9px; letter-spacing: 0.18em; opacity: 0.75; color: inherit;"
  >{{ getPositionLabel(slotIdx, pair.length) }}</span>
  <span class="type-stat leading-none" style="font-size: 1.6rem; color: inherit">#{{ card.auditionNumber }}</span>
</button>
```

- [ ] **Step 4: Add position badge to the active participant detail panel**

Find the active participant detail panel. It currently contains:

```html
<span class="type-stat flex-shrink-0 leading-none text-accent" style="font-size: 2rem">#{{ activeCard.auditionNumber }}</span>
```

Replace with:

```html
<div class="flex items-center gap-1.5 flex-shrink-0">
  <span
    v-if="pairSubMode === 'BATTLE'"
    class="type-label px-1.5 py-0.5"
    style="clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%); font-size: 9px; border: 1px solid currentColor;"
    :class="{
      'text-amber-400':     getPositionLabel(activePair.findIndex(c => c.auditionNumber === activeCard.auditionNumber), activePair.length) === 'LEFT',
      'text-accent':        getPositionLabel(activePair.findIndex(c => c.auditionNumber === activeCard.auditionNumber), activePair.length) === 'MIDDLE',
      'text-content-muted': getPositionLabel(activePair.findIndex(c => c.auditionNumber === activeCard.auditionNumber), activePair.length) === 'RIGHT',
    }"
  >{{ getPositionLabel(activePair.findIndex(c => c.auditionNumber === activeCard.auditionNumber), activePair.length) }}</span>
  <span class="type-stat leading-none text-accent" style="font-size: 2rem">#{{ activeCard.auditionNumber }}</span>
</div>
```

- [ ] **Step 5: Manual verify**

In the browser, open the judge view with a category set to BATTLE mode. Confirm:
- Pairs build without placeholder slots
- Number selector buttons show LEFT/RIGHT/MIDDLE labels above the numbers
- Active participant panel shows position badge
- 3-way round shows three stacked buttons: LEFT / MIDDLE / RIGHT
- SHOWCASE categories are completely unaffected

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/components/PairScoreCards.vue
git commit -m "feat: battle audition pair logic + position labels in PairScoreCards"
```

---

### Task 7: AuditionDisplay.vue — position labels + 3-way layout

**Files:**
- Modify: `BES-frontend/src/views/AuditionDisplay.vue`

**Interfaces:**
- Consumes: `pairSubMode` from WebSocket state payload (Task 5 adds it to `buildStatePayload`)
- Consumes: `getPositionLabel` from `@/utils/auditionPairs`

- [ ] **Step 1: Import getPositionLabel**

In `AuditionDisplay.vue`, add to the imports at the top of `<script setup>`:

```js
import { getPositionLabel } from '@/utils/auditionPairs'
```

- [ ] **Step 2: Expose pairSubMode as a computed**

In the computed display values section (around line 76), add:

```js
const pairSubMode = computed(() => state.value?.pairSubMode ?? 'SHOWCASE')
```

- [ ] **Step 3: Add position labels to the PAIR layout in the template**

Find the PAIR mode slot loop in the template:

```html
<template v-for="(slot, sIdx) in currentSlots" :key="sIdx">
  <div class="pair-name-entry">
    <span class="type-stat audition-number" :style="numberColor ? { color: numberColor } : {}">#{{ slot.auditionNumber }}</span>
    <span v-if="slot.placeholder" class="participant-name" style="opacity:0.3">TBD</span>
    <span v-else class="type-body participant-name">{{ slot.participantName }}</span>
  </div>
  <div v-if="sIdx === 0 && currentSlots.length > 1" class="pair-divider" aria-hidden="true"></div>
</template>
```

Replace with:

```html
<template v-for="(slot, sIdx) in currentSlots" :key="sIdx">
  <div class="pair-name-entry">
    <!-- Position label (BATTLE mode only) -->
    <span
      v-if="pairSubMode === 'BATTLE'"
      class="position-label"
      :class="{
        'position-left':   getPositionLabel(sIdx, currentSlots.length) === 'LEFT',
        'position-middle': getPositionLabel(sIdx, currentSlots.length) === 'MIDDLE',
        'position-right':  getPositionLabel(sIdx, currentSlots.length) === 'RIGHT',
      }"
    >{{ getPositionLabel(sIdx, currentSlots.length) }}</span>
    <span class="type-stat audition-number" :style="numberColor ? { color: numberColor } : {}">#{{ slot.auditionNumber }}</span>
    <span v-if="slot.placeholder" class="participant-name" style="opacity:0.3">TBD</span>
    <span v-else class="type-body participant-name">{{ slot.participantName }}</span>
  </div>
  <!-- Divider between entries (including 3-way) -->
  <div v-if="sIdx < currentSlots.length - 1" class="pair-divider" aria-hidden="true"></div>
</template>
```

- [ ] **Step 4: Add position label CSS to AuditionDisplay.vue `<style scoped>`**

Add after the existing `.pair-divider` styles:

```css
/* ── Battle position labels ──────────────────────────────────────────────── */
.position-label {
  font-family: 'Oswald', sans-serif;
  font-size: clamp(11px, 1.2vw, 16px);
  letter-spacing: 0.22em;
  text-transform: uppercase;
  opacity: 0.55;
  align-self: center;
  flex-shrink: 0;
}
.position-left   { color: rgb(251, 191, 36); } /* amber-400 */
.position-middle { color: var(--accent-color); }
.position-right  { color: rgba(255, 255, 255, 0.6); }
```

- [ ] **Step 5: Manual verify**

Open the OBS display URL in a browser while the emcee is on a BATTLE-mode category. Confirm:
- Position labels appear to the left of each audition number
- 3-way round shows three names with LEFT/MIDDLE/RIGHT labels and two dividers
- SHOWCASE categories show no position labels
- Timer, UP NEXT, and SOLO mode are unaffected

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/AuditionDisplay.vue
git commit -m "feat: battle audition position labels + 3-way layout in AuditionDisplay"
```

---

### Task 8: End-to-end verification

- [ ] **Step 1: Run all frontend tests**

```bash
cd BES-frontend
npm test
```

Expected: all tests pass including the new `auditionPairs.test.js`

- [ ] **Step 2: Build frontend**

```bash
npm run build
```

Expected: `dist/` produced with no errors

- [ ] **Step 3: Build backend**

```bash
cd ../BES
mvn clean package -DskipTests
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Full flow test**

1. Set an event to PAIR mode in EventDetails
2. Set one category to BATTLE, one to SHOWCASE
3. Open the emcee view — confirm BATTLE category shows compact rounds with position badges; SHOWCASE shows placeholder gaps as before
4. Open the judge view — confirm BATTLE category shows position labels on number buttons; SHOWCASE is unchanged
5. Open the OBS display — confirm BATTLE category shows position labels; SHOWCASE is unchanged
6. Register an odd number of participants in BATTLE category — confirm the last one joins the previous pair as a 3-way with LEFT/MIDDLE/RIGHT labels on all three screens
