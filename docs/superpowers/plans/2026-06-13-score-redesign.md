# Score.vue Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite `BES-frontend/src/views/Score.vue` as a Top N qualifier tool: mode toggle (Control / Broadcast), Top N hero picker, glowing Top N line, inline tie resolver with expandable pool stepper.

**Architecture:** Single-file Vue 3 `<script setup>` redesign with one tiny pure-logic helper extracted for unit testing. One precondition backend change: add `auditionNumber` to the existing participant-score DTO (the field already exists on the entity). No other API or schema changes.

**Tech Stack:** Vue 3 (`<script setup>`), Tailwind v3, Anton SC display font, Vitest + Vue Test Utils, Spring Boot backend (Java 17).

**Spec:** `docs/superpowers/specs/2026-06-13-score-redesign-design.md` — the source of truth for layout, wording, semantics. Reference it from each task.

---

## File Structure

| File | Status | Responsibility |
|------|--------|----------------|
| `BES/src/main/java/com/example/BES/dtos/GetParticipatnScoreDto.java` | Modify | Add `auditionNumber` field (precondition) |
| `BES/src/main/java/com/example/BES/services/ScoreService.java` | Modify | Populate `dto.auditionNumber` in `getAllScore` |
| `BES-frontend/src/utils/scoreTiePool.js` | Create | Pure helpers: `sortRowsForPool`, `computeNextEligibleAdd`, `computeNextEligibleRemove`, `addedPoolOrdered` |
| `BES-frontend/src/utils/__tests__/scoreTiePool.test.js` | Create | Vitest unit tests for the above helpers |
| `BES-frontend/src/views/Score.vue` | Rewrite | Whole-file redesign per spec |

Note: the existing Java DTO filename is `GetParticipatnScoreDto.java` (misspelled "Participatn" in the codebase). Do not rename it as part of this plan — it's out of scope.

---

## Task 1: Backend precondition — expose auditionNumber on the score DTO

**Files:**
- Modify: `BES/src/main/java/com/example/BES/dtos/GetParticipatnScoreDto.java`
- Modify: `BES/src/main/java/com/example/BES/services/ScoreService.java:33` (the `getAllScore` method body)

The frontend stepper needs `auditionNumber` per row to sort same-score participants deterministically (§9.3 of spec). The field exists on `EventGenreParticipant` but isn't exposed by the DTO yet.

- [ ] **Step 1.1: Add the field to the DTO**

Open `GetParticipatnScoreDto.java`. The class currently looks like:

```java
package com.example.BES.dtos;

public class GetParticipatnScoreDto {
    public Long participantId;
    public String participantName;
    public String eventName;
    public String genreName;
    public String judgeName;
    public Double score;
    public String aspect;
    public String format;
}
```

Add `auditionNumber` after `format`:

```java
package com.example.BES.dtos;

public class GetParticipatnScoreDto {
    public Long participantId;
    public String participantName;
    public String eventName;
    public String genreName;
    public String judgeName;
    public Double score;
    public String aspect;
    public String format;
    public Integer auditionNumber;
}
```

- [ ] **Step 1.2: Populate the field in the service**

Open `ScoreService.java`, find the `getAllScore` method (around line 33). Inside the for-loop where the DTO is built, add the assignment immediately after `dto.format`:

```java
dto.format = s.getEventGenreParticipant().getFormat();
dto.auditionNumber = s.getEventGenreParticipant().getAuditionNumber();
```

- [ ] **Step 1.3: Run backend tests**

Run from `BES/`:
```bash
cd BES && mvn test -DskipITs=false 2>&1 | tail -40
```

Expected: all tests pass. If `EventControllerIntegrationTest` covers `/event/{name}/score`, the new field appears in the response but does not break the assertion (the test would only fail if it asserts on field-set exactness, which it doesn't in the current codebase).

- [ ] **Step 1.4: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/GetParticipatnScoreDto.java \
        BES/src/main/java/com/example/BES/services/ScoreService.java
git commit -m "feat(score): expose auditionNumber on participant-score DTO

Precondition for the Score.vue redesign: the tie resolver pool stepper
sorts same-score participants by audition number (spec §9.3). Field
already existed on EventGenreParticipant; this surfaces it on the DTO."
```

---

## Task 2: Create the pure pool-logic helper (TDD)

**Files:**
- Create: `BES-frontend/src/utils/scoreTiePool.js`
- Create: `BES-frontend/src/utils/__tests__/scoreTiePool.test.js`

The pool stepper logic — "what's the next eligible to add", "what's the last to remove", "how do I sort the pool" — is pure functions of `(allRows, includedNames)`. Extract it into a small util so it can be Vitest-unit-tested before we wire it into the Vue component.

- [ ] **Step 2.1: Write the failing test file**

Create `BES-frontend/src/utils/__tests__/scoreTiePool.test.js`:

```js
import { describe, it, expect } from 'vitest'
import {
  sortRowsForPool,
  computeNextEligibleAdd,
  computeNextEligibleRemove,
  addedPoolOrdered,
} from '../scoreTiePool.js'

const row = (name, totalScore, auditionNumber) => ({
  participantName: name, totalScore, auditionNumber,
})

describe('sortRowsForPool', () => {
  it('sorts by totalScore DESC', () => {
    const rows = [row('A', 5.0, 1), row('B', 7.0, 2), row('C', 6.0, 3)]
    expect(sortRowsForPool(rows).map(r => r.participantName))
      .toEqual(['B', 'C', 'A'])
  })

  it('breaks score ties with auditionNumber ASC', () => {
    const rows = [row('A', 7.0, 5), row('B', 7.0, 2), row('C', 7.0, 8)]
    expect(sortRowsForPool(rows).map(r => r.participantName))
      .toEqual(['B', 'A', 'C'])
  })

  it('falls back to alphabetical name when score AND auditionNumber tie', () => {
    const rows = [row('Charlie', 7.0, 3), row('Alpha', 7.0, 3), row('Bravo', 7.0, 3)]
    expect(sortRowsForPool(rows).map(r => r.participantName))
      .toEqual(['Alpha', 'Bravo', 'Charlie'])
  })

  it('treats null/undefined auditionNumber as Infinity (sorts last within tied score)', () => {
    const rows = [row('A', 7.0, null), row('B', 7.0, 2), row('C', 7.0, 5)]
    expect(sortRowsForPool(rows).map(r => r.participantName))
      .toEqual(['B', 'C', 'A'])
  })

  it('does not mutate the input array', () => {
    const rows = [row('A', 5.0, 1), row('B', 7.0, 2)]
    const copy = [...rows]
    sortRowsForPool(rows)
    expect(rows).toEqual(copy)
  })
})

describe('computeNextEligibleAdd', () => {
  const allRows = [
    row('Drift', 7.3, 4),
    row('Echo',  7.3, 18),
    row('Halo',  7.2, 7),
    row('Pulse', 7.2, 22),
    row('Mirage', 7.1, 12),
  ]

  it('returns the highest-score not-yet-included row', () => {
    const result = computeNextEligibleAdd(allRows, new Set(['Drift', 'Echo']))
    expect(result).toEqual({ participantName: 'Halo', totalScore: 7.2, auditionNumber: 7 })
  })

  it('uses auditionNumber ASC to break score ties', () => {
    const result = computeNextEligibleAdd(allRows, new Set(['Drift', 'Echo', 'Halo']))
    expect(result.participantName).toBe('Pulse')
  })

  it('returns null when no eligible rows remain', () => {
    const all = new Set(['Drift', 'Echo', 'Halo', 'Pulse', 'Mirage'])
    expect(computeNextEligibleAdd(allRows, all)).toBeNull()
  })
})

describe('computeNextEligibleRemove', () => {
  const allRows = [
    row('Drift', 7.3, 4),
    row('Echo',  7.3, 18),
    row('Halo',  7.2, 7),
    row('Pulse', 7.2, 22),
    row('Mirage', 7.1, 12),
  ]
  const tieBase = new Set(['Drift', 'Echo'])

  it('returns the last (lowest-rank) entry in the added pool', () => {
    const added = new Set(['Halo', 'Pulse', 'Mirage'])
    const result = computeNextEligibleRemove(allRows, tieBase, added)
    expect(result).toEqual({ participantName: 'Mirage', totalScore: 7.1, auditionNumber: 12 })
  })

  it('returns the lowest of a same-score tier when that tier is the last added', () => {
    const added = new Set(['Halo', 'Pulse'])
    const result = computeNextEligibleRemove(allRows, tieBase, added)
    // Halo (#07) ranks above Pulse (#22) within score 7.2, so Pulse is the lowest-rank added
    expect(result.participantName).toBe('Pulse')
  })

  it('returns null when added pool is empty (would touch the locked base)', () => {
    expect(computeNextEligibleRemove(allRows, tieBase, new Set())).toBeNull()
  })
})

describe('addedPoolOrdered', () => {
  const allRows = [
    row('Drift', 7.3, 4),
    row('Halo',  7.2, 7),
    row('Pulse', 7.2, 22),
    row('Mirage', 7.1, 12),
  ]

  it('orders the added members by score DESC, then auditionNumber ASC', () => {
    const result = addedPoolOrdered(allRows, new Set(['Mirage', 'Pulse', 'Halo']))
    expect(result.map(r => r.participantName)).toEqual(['Halo', 'Pulse', 'Mirage'])
  })

  it('returns empty array when set is empty', () => {
    expect(addedPoolOrdered(allRows, new Set())).toEqual([])
  })

  it('silently skips names not present in allRows', () => {
    const result = addedPoolOrdered(allRows, new Set(['Halo', 'Ghost', 'Pulse']))
    expect(result.map(r => r.participantName)).toEqual(['Halo', 'Pulse'])
  })
})
```

- [ ] **Step 2.2: Run the test to verify it fails**

Run from `BES-frontend/`:
```bash
cd BES-frontend && npx vitest run src/utils/__tests__/scoreTiePool.test.js 2>&1 | tail -30
```

Expected: FAIL with "Failed to resolve import './scoreTiePool.js'" — the source file doesn't exist yet.

- [ ] **Step 2.3: Implement the helpers**

Create `BES-frontend/src/utils/scoreTiePool.js`:

```js
// Pure helpers for the Score.vue tie-resolver pool.
// See docs/superpowers/specs/2026-06-13-score-redesign-design.md §3.5 + §9.3.

const cmp = (a, b) => {
  const sa = b.totalScore - a.totalScore                 // score DESC
  if (sa !== 0) return sa
  const ana = a.auditionNumber == null ? Infinity : a.auditionNumber
  const anb = b.auditionNumber == null ? Infinity : b.auditionNumber
  if (ana !== anb) return ana - anb                      // auditionNumber ASC
  return a.participantName.localeCompare(b.participantName) // name ASC fallback
}

export function sortRowsForPool(rows) {
  return [...rows].sort(cmp)
}

export function computeNextEligibleAdd(allRows, includedNames) {
  const sorted = sortRowsForPool(allRows)
  for (const r of sorted) {
    if (!includedNames.has(r.participantName)) return r
  }
  return null
}

export function computeNextEligibleRemove(allRows, tieBaseNames, addedNames) {
  if (addedNames.size === 0) return null
  // Last-in-order = lowest-ranked member of the added set
  const sorted = sortRowsForPool(allRows).filter(r => addedNames.has(r.participantName))
  return sorted.length ? sorted[sorted.length - 1] : null
}

export function addedPoolOrdered(allRows, addedNames) {
  return sortRowsForPool(allRows).filter(r => addedNames.has(r.participantName))
}
```

- [ ] **Step 2.4: Run the test to verify it passes**

```bash
cd BES-frontend && npx vitest run src/utils/__tests__/scoreTiePool.test.js 2>&1 | tail -30
```

Expected: all tests PASS.

- [ ] **Step 2.5: Run the full frontend test suite (regression guard)**

```bash
cd BES-frontend && npm test -- --run 2>&1 | tail -20
```

Expected: all existing tests still pass.

- [ ] **Step 2.6: Commit**

```bash
git add BES-frontend/src/utils/scoreTiePool.js \
        BES-frontend/src/utils/__tests__/scoreTiePool.test.js
git commit -m "feat(score): tie-pool helpers — sort + next-eligible logic

Pure functions extracted for Score.vue tie resolver. Sort order is
(totalScore DESC, auditionNumber ASC, name ASC) per spec §9.3."
```

---

## Task 3: Score.vue — add new script state (additive, no template change yet)

**Files:**
- Modify: `BES-frontend/src/views/Score.vue` (script setup section only)

Goal: introduce new reactive state and computeds **alongside** the existing ones. Template still renders the old UI. This isolates state-shape changes from template churn.

- [ ] **Step 3.1: Add new imports**

In `BES-frontend/src/views/Score.vue`, change the imports at the top of `<script setup>`:

```js
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { getParticipantScore, getParticipantFeedback, getResultsStatus, releaseResults, getParticipantRefs, getScoringCriteria, setResolvedParticipants } from '@/utils/api';
import { computeNextEligibleAdd, computeNextEligibleRemove, addedPoolOrdered } from '@/utils/scoreTiePool';
import { useAuthStore } from '@/utils/auth';
```

(Removed `DynamicTable` import — Task 9 confirms it's no longer used.)

- [ ] **Step 3.2: Add mode state with role-aware default + localStorage persistence**

Add immediately after the existing `authStore`/`userRole`/`isAdminOrOrganiser` block:

```js
// ── Mode toggle: Control (organiser) vs Broadcast (emcee) ─────────────────
// Default is role-driven; user choice persists per event in localStorage.
const modeKey = computed(() => `score_mode_${selectedEvent.value || 'global'}`)
const initialMode = (() => {
  const saved = localStorage.getItem(`score_mode_${authStore.activeEvent?.name || localStorage.getItem('selectedEvent') || 'global'}`)
  if (saved === 'control' || saved === 'broadcast') return saved
  return userRole.value === 'ROLE_EMCEE' ? 'broadcast' : 'control'
})()
const mode = ref(initialMode)
watch([mode, modeKey], ([m, k]) => { if (k && (m === 'control' || m === 'broadcast')) localStorage.setItem(k, m) })
```

- [ ] **Step 3.3: Extend `tbKey` persistence shape to include addedToPool**

Find the existing `addedToPool`-less block (around line 38–67 of the current file):

```js
const tieBreakerWinners = ref(new Set())
const tieBreakerConfirmed = ref(false)
```

Add a new ref next to them:

```js
const tieBreakerWinners = ref(new Set())
const tieBreakerConfirmed = ref(false)
const addedToPool = ref(new Set()) // manually-added pool members for the tie resolver
```

Update `saveTieBreaker` to include `addedToPool`:

```js
const saveTieBreaker = () => {
  localStorage.setItem(tbKey.value, JSON.stringify({
    winners: [...tieBreakerWinners.value],
    confirmed: tieBreakerConfirmed.value,
    addedToPool: [...addedToPool.value],
  }))
}
```

Update `loadTieBreaker` to read it (with safe defaults for old payloads):

```js
const loadTieBreaker = () => {
  const saved = localStorage.getItem(tbKey.value)
  if (saved) {
    const { winners, confirmed, addedToPool: added } = JSON.parse(saved)
    tieBreakerWinners.value = new Set(winners)
    tieBreakerConfirmed.value = confirmed
    addedToPool.value = new Set(Array.isArray(added) ? added : [])
  } else {
    tieBreakerWinners.value = new Set()
    tieBreakerConfirmed.value = false
    addedToPool.value = new Set()
  }
}
```

Update `resetTieBreaker` to also clear `addedToPool`:

```js
const resetTieBreaker = () => {
  tieBreakerWinners.value = new Set()
  tieBreakerConfirmed.value = false
  addedToPool.value = new Set()
  localStorage.removeItem(tbKey.value)
}
```

- [ ] **Step 3.4: Add the eligibility pool computeds**

Below the existing `tiedParticipants` computed (around line 171–176), add:

```js
// Full ranked rows for the current genre+type, used to feed the pool helpers.
// `filteredParticipantsForScore.value.rows` already contains rank-sorted rows
// with totalScore. The auditionNumber field comes from the new backend DTO.
const allRowsForPool = computed(() => filteredParticipantsForScore.value.rows ?? [])

// Tie base = the auto-detected tied participants (cannot be removed from pool).
const tieBaseNames = computed(() => new Set(tiedParticipants.value.map(r => r.participantName)))

// Full pool = base + added (set), used to derive the eligibility list rendered
// in the band and to compute selection limits.
const eligibilityPoolNames = computed(() => {
  const s = new Set(tieBaseNames.value)
  for (const n of addedToPool.value) s.add(n)
  return s
})

// Ordered list of added members, rank-sorted (for rendering below the base in the band).
const addedToPoolOrdered = computed(() =>
  addedPoolOrdered(allRowsForPool.value, addedToPool.value)
)

// Next eligible to add — drives the "+ INCLUDE <name> · <score>" button label.
const nextEligibleAdd = computed(() =>
  topNResult.value.hasTieBreaker
    ? computeNextEligibleAdd(allRowsForPool.value, eligibilityPoolNames.value)
    : null
)

// Next eligible to remove — drives the "− EXCLUDE <name> · <score>" button label.
const nextEligibleRemove = computed(() =>
  computeNextEligibleRemove(allRowsForPool.value, tieBaseNames.value, addedToPool.value)
)
```

- [ ] **Step 3.5: Add stepper methods**

Below `confirmTieBreaker` (around line 202), add:

```js
const includeNextInPool = () => {
  const next = nextEligibleAdd.value
  if (!next) return
  const s = new Set(addedToPool.value)
  s.add(next.participantName)
  addedToPool.value = s
  tieBreakerConfirmed.value = false
  saveTieBreaker()
}

const excludeLastFromPool = () => {
  const last = nextEligibleRemove.value
  if (!last) return
  const s = new Set(addedToPool.value)
  s.delete(last.participantName)
  // Also drop them from the winners selection if they were picked.
  const w = new Set(tieBreakerWinners.value)
  w.delete(last.participantName)
  addedToPool.value = s
  tieBreakerWinners.value = w
  tieBreakerConfirmed.value = false
  saveTieBreaker()
}
```

- [ ] **Step 3.6: Add the status banner, eliminated summary, and broadcast columns computeds**

Add anywhere in the script (after `finalRows` is fine):

```js
// Control-mode status banner — sits below the Top N picker.
const statusBanner = computed(() => {
  const t = topNResult.value
  const total = filteredParticipantsForScore.value.rows?.length ?? 0
  const n = selectedTopN.value === 'All' ? Infinity : parseInt(selectedTopN.value.replace('Top ', ''))
  if (!Number.isFinite(n)) return null
  if (total < n) {
    return { tone: 'insufficient', message: `ONLY ${total} SCORED — TOP ${n} NOT YET REACHABLE` }
  }
  if (t.hasTieBreaker) {
    return { tone: 'tie', message: `TIE AT RANK ${t.cutoff} — ${t.tiedCount} AT ${t.tieBreakerScore} — RESOLVE BELOW` }
  }
  return { tone: 'clean', message: `TOP ${n} READY — 0 TIES` }
})

// Summary of eliminated participants — drives the "⋯ N MORE ELIMINATED ⋯" collapse.
const eliminatedSummary = computed(() => {
  const all = allRowsForPool.value
  const visible = new Set(finalRows.value.map(r => r.participantName))
  const eliminated = all.filter(r => !visible.has(r.participantName))
  if (eliminated.length === 0) return null
  const lowestScore = Math.min(...eliminated.map(r => r.totalScore))
  return { count: eliminated.length, lowestScore }
})

const eliminatedExpanded = ref(false)

// Broadcast-mode column count — 1 below 640px or for very small N, 2 otherwise.
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const onResize = () => { viewportWidth.value = window.innerWidth }
onMounted(() => { window.addEventListener('resize', onResize) })
onUnmounted(() => { window.removeEventListener('resize', onResize) })

const broadcastColumns = computed(() => {
  if (viewportWidth.value < 640) return '1col'
  const n = selectedTopN.value === 'All'
    ? (allRowsForPool.value?.length ?? 0)
    : parseInt(selectedTopN.value.replace('Top ', ''))
  return n <= 12 ? '1col' : '2col'
})
```

- [ ] **Step 3.7: Lint-check the file**

```bash
cd BES-frontend && npx eslint src/views/Score.vue 2>&1 | tail -20
```

Expected: zero errors. (Warnings about unused variables for old state are fine — they'll be removed in Task 9.)

- [ ] **Step 3.8: Run dev server smoke check**

```bash
cd BES-frontend && timeout 12 npm run dev > /tmp/score-dev.log 2>&1 &
sleep 8 && curl -sf http://localhost:5173 > /dev/null && echo "dev server OK"
```

Expected: `dev server OK`. The Score page still renders the old UI (template unchanged); the new state is dormant.

- [ ] **Step 3.9: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "feat(score): add mode toggle + eligibility-pool state

Additive script changes only — template still renders old UI.
- mode ref + localStorage persistence (role-aware default)
- addedToPool ref + extended tbKey payload (winners/confirmed/addedToPool)
- statusBanner, eliminatedSummary, broadcastColumns computeds
- nextEligibleAdd/Remove + includeNextInPool/excludeLastFromPool methods"
```

---

## Task 4: Score.vue — replace template, Control mode top section

**Files:**
- Modify: `BES-frontend/src/views/Score.vue` (template only)

This task replaces the page-header + filter-card block with the new Control-mode header (event/genre title, Release pill, Mode toggle) and the secondary filter row.

Refer to spec §3.1 + §3.2.

- [ ] **Step 4.1: Replace the page-header + filter card**

In the template, find the existing block:

```vue
<!-- Page header -->
<div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-8">
  <div>
    <h1 class="type-page-title mb-1">Scoreboard</h1>
    ...
  </div>
</div>

<!-- Filter card -->
<div class="card p-5 mb-6">
  ...
</div>
```

Replace it (everything from `<!-- Page header -->` through the closing `</div>` of the Filter card, including the nested `Release Results toggle` block) with:

```vue
<!-- Header row -->
<div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between mb-6">
  <div>
    <p class="type-label text-content-muted mb-1">SCOREBOARD · {{ selectedEvent }}</p>
    <h1 class="type-page-title">
      {{ selectedGenre || 'No Genre' }}
      <template v-if="hasTeamAndSoloMix"> — {{ selectedEntryType.toUpperCase() }}</template>
    </h1>
  </div>
  <div class="flex flex-wrap items-center gap-2">
    <!-- Release Results pill (admin/organiser only) -->
    <button
      v-if="isAdminOrOrganiser"
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
    <!-- Mode toggle -->
    <div class="flex p-0.5 border border-surface-600 bg-surface-800/50" role="group" aria-label="Display mode">
      <button
        v-for="m in ['control', 'broadcast']"
        :key="m"
        @click="mode = m"
        :aria-pressed="mode === m"
        class="para-chip-sm px-3 py-1.5 type-label transition-all"
        :class="mode === m
          ? 'text-accent border-[color:var(--accent-muted)]'
          : 'text-content-muted hover:text-content-primary'"
      >{{ m.toUpperCase() }}</button>
    </div>
  </div>
</div>

<!-- Secondary filter row (Control mode only) -->
<div v-if="mode === 'control'" class="card p-3 mb-6">
  <div class="flex flex-wrap items-center gap-3">
    <!-- Genre -->
    <div class="flex flex-wrap gap-1" role="group" aria-label="Filter by genre">
      <span class="type-label text-content-muted self-center mr-1">GENRE</span>
      <button
        v-for="g in uniqueGenres"
        :key="g"
        @click="selectedGenre = g"
        :aria-pressed="selectedGenre === g"
        class="para-chip-sm px-3 py-1.5 type-label transition-all"
        :class="selectedGenre === g ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
      >{{ g }}</button>
    </div>
    <span class="text-surface-600 select-none" aria-hidden="true">|</span>
    <!-- View (formerly "Group By") -->
    <div class="flex flex-wrap gap-1" role="group" aria-label="View">
      <span class="type-label text-content-muted self-center mr-1">VIEW</span>
      <button
        v-for="t in tabulationMethod"
        :key="t"
        @click="selectedTabulation = t"
        :aria-pressed="selectedTabulation === t"
        class="para-chip-sm px-3 py-1.5 type-label transition-all"
        :class="selectedTabulation === t ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
      >{{ t.toUpperCase() }}</button>
    </div>
    <template v-if="hasTeamAndSoloMix">
      <span class="text-surface-600 select-none" aria-hidden="true">|</span>
      <div class="flex flex-wrap gap-1" role="group" aria-label="Entry type">
        <span class="type-label text-content-muted self-center mr-1">TYPE</span>
        <button
          v-for="t in ['Teams', 'Solo']"
          :key="t"
          @click="selectedEntryType = t"
          :aria-pressed="selectedEntryType === t"
          class="para-chip-sm px-3 py-1.5 type-label transition-all"
          :class="selectedEntryType === t ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
        >{{ t.toUpperCase() }}</button>
      </div>
    </template>
  </div>
</div>
```

- [ ] **Step 4.2: Manual visual check**

Start the dev server and open `/event/score` in a browser:
```bash
cd BES-frontend && npm run dev
```
Open http://localhost:5173/event/score.

Expected: the new header (event label + genre title + Release/Mode chips) renders. The old podium/table are still visible below — that's fine; later tasks replace them. The Mode toggle flips between `control` and `broadcast` (no template branch wired yet, but the toggle button responds).

Click Mode toggle to verify it persists across refresh.

- [ ] **Step 4.3: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "feat(score): new header row + secondary filter (Control mode)

- Top-right: Release pill (admin/organiser) + Mode toggle
- Below: compact filter strip (Genre · View · Type)
- Top N promoted out of filter row (next task)
- Release toggle relocated from filter card to header"
```

---

## Task 5: Score.vue — Top N hero picker + status banner

**Files:**
- Modify: `BES-frontend/src/views/Score.vue` (template only)

Refer to spec §3.3.

- [ ] **Step 5.1: Insert the Top N picker + status banner**

Immediately after the secondary filter row closing `</div>`, add (still inside `v-if="mode === 'control'"` block — wrap if needed):

```vue
<!-- Top N hero picker (Control mode only) -->
<div v-if="mode === 'control' && selectedTabulation === 'By Total'" class="mb-6">
  <p class="type-label text-content-muted mb-3">QUALIFY</p>
  <div class="grid grid-cols-4 gap-2" role="group" aria-label="Qualify top N">
    <button
      v-for="n in topNOptions"
      :key="n"
      @click="selectedTopN = n"
      :aria-pressed="selectedTopN === n"
      class="para-chip py-5 flex flex-col items-center justify-center gap-1 transition-all"
      :class="selectedTopN === n
        ? 'border-[color:var(--accent-color)] shadow-[0_0_20px_var(--accent-subtle)]'
        : 'opacity-50 hover:opacity-90'"
    >
      <span class="type-stat" style="font-size: 28px; line-height: 1">{{ n === 'All' ? 'ALL' : n.replace('Top ', '') }}</span>
      <span class="type-label" style="font-size: 9px">{{ n === 'All' ? `· ${allRowsForPool.length}` : 'TOP' }}</span>
    </button>
  </div>

  <!-- Status banner -->
  <div
    v-if="statusBanner"
    role="status"
    aria-live="polite"
    class="mt-4 px-4 py-3 flex items-center gap-3 border-l-[3px]"
    :class="{
      'border-emerald-400 bg-emerald-500/8': statusBanner.tone === 'clean',
      'border-amber-400 bg-amber-500/8':    statusBanner.tone === 'tie',
      'border-rose-400 bg-rose-500/8':      statusBanner.tone === 'insufficient',
    }"
  >
    <span
      class="w-1.5 h-1.5 rounded-full flex-shrink-0"
      :class="{
        'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.6)]': statusBanner.tone === 'clean',
        'bg-amber-400 shadow-[0_0_8px_rgba(245,158,11,0.6)]':   statusBanner.tone === 'tie',
        'bg-rose-400 shadow-[0_0_8px_rgba(244,63,94,0.6)]':     statusBanner.tone === 'insufficient',
      }"
    ></span>
    <p class="type-label flex flex-wrap gap-2" :class="{
      'text-emerald-400': statusBanner.tone === 'clean',
      'text-amber-400':   statusBanner.tone === 'tie',
      'text-rose-400':    statusBanner.tone === 'insufficient',
    }">{{ statusBanner.message }}</p>
  </div>
</div>
```

- [ ] **Step 5.2: Visual check**

Refresh the browser. Expected:
- 4-tile picker (8 / 16 / 32 / ALL · count). Selected tile glows.
- Below the picker, an emerald "TOP N READY — 0 TIES" banner when no tie, amber "TIE AT RANK N …" when a tie exists in your test data, or red "ONLY X SCORED" if fewer participants than N.

- [ ] **Step 5.3: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "feat(score): Top N hero picker + semantic status banner"
```

---

## Task 6: Score.vue — Control mode leaderboard

**Files:**
- Modify: `BES-frontend/src/views/Score.vue` (template only)

Refer to spec §3.4 + §3.6 (Top N line) + §3.7 (By-Judge sub-view).

The current template has two big blocks: a custom admin table and a `<DynamicTable>` for non-admin. Both get replaced with a single row-based renderer that branches by `selectedTabulation`.

- [ ] **Step 6.1: Replace the existing leaderboard block**

Find the existing block:
```vue
<!-- By Total: ranked leaderboard -->
<template v-if="selectedTabulation === 'By Total'">
  ...
</template>

<!-- By Judge: separate tables per judge -->
<template v-if="selectedTabulation === 'By Judge'">
  ...
</template>
```

Replace **only the By Total branch** (the By Judge branch stays for now; Step 6.4 replaces it) with:

```vue
<!-- By Total: cut-line leaderboard -->
<template v-if="mode === 'control' && selectedTabulation === 'By Total'">
  <div v-if="topNResult.rows && topNResult.rows.length > 0">
    <p class="type-label text-content-muted mb-3 flex justify-between">
      <span>RANKINGS · {{ allRowsForPool.length }} SCORED</span>
    </p>

    <!-- Rows -->
    <div class="flex flex-col gap-1">
      <!-- Leader row: rank 1, in cut, larger -->
      <div
        v-if="finalRows[0]"
        class="para-chip flex items-start gap-3 px-4 py-3 border-l-[3px] border-[color:var(--accent-color)]"
      >
        <span class="type-stat flex-shrink-0" style="font-size: 24px; line-height: 1; min-width: 40px">{{ finalRows[0].id }}</span>
        <div class="flex-1 min-w-0">
          <p class="type-body text-content-primary" style="font-size: 16px">{{ finalRows[0].participantName }}</p>
          <p v-if="judgeMetaLine(finalRows[0])" class="type-label text-content-muted/70 mt-0.5">{{ judgeMetaLine(finalRows[0]) }}</p>
        </div>
        <span class="type-stat flex-shrink-0" style="font-size: 28px; line-height: 1">{{ finalRows[0].totalScore }}</span>
        <div v-if="isAdminOrOrganiser" class="flex gap-1 flex-shrink-0 opacity-50 hover:opacity-100 transition-opacity">
          <button v-if="topNResult.isMultiAspect" @click="viewBreakdown(finalRows[0].participantName)" :aria-label="`View score breakdown for ${finalRows[0].participantName}`" class="p-2 rounded text-content-muted hover:text-accent"><i class="pi pi-chart-bar text-sm" aria-hidden="true"></i></button>
          <button @click="viewFeedback(finalRows[0].participantName)" :aria-label="`View judge feedback for ${finalRows[0].participantName}`" class="p-2 rounded text-content-muted hover:text-accent"><i class="pi pi-comment text-sm" aria-hidden="true"></i></button>
          <button v-if="resultsReleased && refsMap[finalRows[0].participantName]" @click="openQR(finalRows[0].participantName)" :aria-label="`Show results QR code for ${finalRows[0].participantName}`" class="p-2 rounded text-content-muted hover:text-emerald-400"><i class="pi pi-qrcode text-sm" aria-hidden="true"></i></button>
        </div>
      </div>

      <!-- Standard rows: rank 2 through N (excluding tied rows when band is rendering) -->
      <template v-for="row in inCutStandardRows" :key="row.participantName">
        <div class="para-chip flex items-start gap-3 px-4 py-2 bg-surface-700/10">
          <span class="type-stat flex-shrink-0 text-content-secondary" style="font-size: 18px; line-height: 1; min-width: 40px">{{ row.id }}</span>
          <div class="flex-1 min-w-0">
            <p class="type-body text-content-primary">{{ row.participantName }}</p>
            <p v-if="judgeMetaLine(row)" class="type-label text-content-muted/50 mt-0.5">{{ judgeMetaLine(row) }}</p>
          </div>
          <span class="type-stat flex-shrink-0" style="font-size: 20px; line-height: 1">{{ row.totalScore }}</span>
          <div v-if="isAdminOrOrganiser" class="flex gap-1 flex-shrink-0 opacity-50 hover:opacity-100 transition-opacity">
            <button v-if="topNResult.isMultiAspect" @click="viewBreakdown(row.participantName)" :aria-label="`View score breakdown for ${row.participantName}`" class="p-2 rounded text-content-muted hover:text-accent"><i class="pi pi-chart-bar text-sm" aria-hidden="true"></i></button>
            <button @click="viewFeedback(row.participantName)" :aria-label="`View judge feedback for ${row.participantName}`" class="p-2 rounded text-content-muted hover:text-accent"><i class="pi pi-comment text-sm" aria-hidden="true"></i></button>
            <button v-if="resultsReleased && refsMap[row.participantName]" @click="openQR(row.participantName)" :aria-label="`Show results QR code for ${row.participantName}`" class="p-2 rounded text-content-muted hover:text-emerald-400"><i class="pi pi-qrcode text-sm" aria-hidden="true"></i></button>
          </div>
        </div>
      </template>

      <!-- Tie band slot — rendered in Task 7 -->
      <div v-if="topNResult.hasTieBreaker && !tieBreakerConfirmed" data-tie-band-placeholder class="opacity-50 px-4 py-2 text-xs text-amber-400">[ tie band — Task 7 ]</div>
      <div v-else-if="topNResult.hasTieBreaker && tieBreakerConfirmed" class="px-4 py-2 border-l-2 border-emerald-400 bg-emerald-500/8 type-label text-emerald-400 flex items-center justify-between">
        <span>✓ TOP {{ topNResult.cutoff }} CONFIRMED — {{ tieBreakerWinners.size }} ADVANCED</span>
        <button @click="resetTieBreaker" class="type-label text-content-muted hover:text-content-primary">RESET</button>
      </div>

      <!-- Top N line (separator) -->
      <div
        v-if="topNResult.rows.length >= (selectedTopN === 'All' ? 0 : parseInt(selectedTopN.replace('Top ','')))
              && selectedTopN !== 'All'
              && (!topNResult.hasTieBreaker || tieBreakerConfirmed)"
        role="separator"
        :aria-label="`Top ${topNResult.cutoff} line`"
        class="relative h-px my-3"
        style="background: linear-gradient(90deg, transparent, var(--accent-color) 50%, transparent); box-shadow: 0 0 12px var(--accent-muted);"
      >
        <span class="absolute right-0 -top-5 type-label text-content-muted">▲ TOP {{ topNResult.cutoff }}</span>
      </div>

      <!-- Eliminated rows (collapsed by default) -->
      <template v-if="eliminatedSummary">
        <template v-if="eliminatedExpanded">
          <div v-for="row in eliminatedRows" :key="row.participantName" class="para-chip flex items-center gap-3 px-4 py-2 opacity-30">
            <span class="type-stat flex-shrink-0" style="font-size: 16px; line-height: 1; min-width: 40px">{{ row.id }}</span>
            <span class="flex-1 type-body text-content-primary">{{ row.participantName }}</span>
            <span class="type-stat flex-shrink-0" style="font-size: 18px; line-height: 1">{{ row.totalScore }}</span>
          </div>
        </template>
        <button
          @click="eliminatedExpanded = !eliminatedExpanded"
          class="type-label text-content-muted/60 hover:text-content-primary text-center py-2 mt-1"
        >⋯ {{ eliminatedSummary.count }} {{ eliminatedExpanded ? 'ELIMINATED · COLLAPSE' : `MORE ELIMINATED · LOWEST ${eliminatedSummary.lowestScore} · EXPAND` }} ⋯</button>
      </template>
    </div>
  </div>

  <!-- Empty state -->
  <div v-else class="flex flex-col items-center justify-center py-20 text-center">
    <div class="para-chip-sm w-14 h-14 flex items-center justify-center mb-4">
      <i class="pi pi-chart-bar text-content-muted text-xl"></i>
    </div>
    <p class="type-body text-content-secondary">{{ selectedGenre ? `No scores for ${selectedGenre}` : 'No scores yet' }}</p>
    <p class="type-label text-content-muted mt-1">{{ selectedGenre ? 'Judges need to submit scores for this genre' : 'Select an event and genre to view scores' }}</p>
  </div>
</template>
```

- [ ] **Step 6.2: Add the supporting computeds and the `judgeMetaLine` helper to the script**

In `<script setup>`, add:

```js
// Per-row judge-score meta line — "A 7.2 · J 7.1 · M 7.0 · S 7.1".
function judgeMetaLine(row) {
  if (!topNResult.value.columns) return ''
  const judgeKeys = topNResult.value.columns
    .filter(c => !['id', 'participantName', 'totalScore'].includes(c.key))
    .map(c => c.key)
  if (judgeKeys.length === 0) return ''
  return judgeKeys.map(j => {
    const v = row[j]
    return v != null ? `${j[0].toUpperCase()} ${v}` : `${j[0].toUpperCase()} —`
  }).join(' · ')
}

// Standard in-cut rows (after rank 1, excluding the tied band rows when they're rendering).
const inCutStandardRows = computed(() => {
  if (!finalRows.value.length) return []
  const skipFromEnd = (topNResult.value.hasTieBreaker && !tieBreakerConfirmed.value)
    ? topNResult.value.tiedCount
    : 0
  return finalRows.value.slice(1, finalRows.value.length - skipFromEnd)
})

// Eliminated rows (below the cut), only computed when expanded.
const eliminatedRows = computed(() => {
  const visible = new Set(finalRows.value.map(r => r.participantName))
  return allRowsForPool.value
    .filter(r => !visible.has(r.participantName))
    .map((r, i) => ({ ...r, id: finalRows.value.length + i + 1 }))
})
```

- [ ] **Step 6.3: Visual check**

Refresh the browser. Expected:
- Leader row (rank 1) larger, with accent left-stripe.
- Standard rows underneath, compact.
- Glowing Top N line above eliminated rows.
- `⋯ N MORE ELIMINATED ⋯` collapse — click to expand.
- Row actions appear on hover for admin/organiser.

When a tie exists, you'll see the `[ tie band — Task 7 ]` placeholder. That's expected.

- [ ] **Step 6.4: Replace the By-Judge sub-view**

Find the existing block:

```vue
<!-- By Judge: separate tables per judge -->
<template v-if="selectedTabulation === 'By Judge'">
  ...
</template>
```

Replace it with:

```vue
<!-- By Judge sub-view (Control mode only) -->
<template v-if="mode === 'control' && selectedTabulation === 'By Judge'">
  <template v-if="filteredParticipantsForScore.byJudge && Object.keys(filteredParticipantsForScore.byJudge).length > 0">
    <div
      v-for="(group, judge) in filteredParticipantsForScore.byJudge"
      :key="judge"
      class="mb-8"
    >
      <div class="section-rule mb-3">
        <span class="section-rule-label">{{ judge }}</span>
        <div class="section-rule-line"></div>
      </div>
      <div class="flex flex-col gap-1">
        <div
          v-for="row in group.rows"
          :key="row.participantName"
          class="para-chip flex items-center gap-3 px-4 py-2 bg-surface-700/10"
        >
          <span class="type-stat flex-shrink-0 text-content-secondary" style="font-size: 18px; line-height: 1; min-width: 40px">{{ row.id }}</span>
          <span class="flex-1 type-body text-content-primary">{{ row.participantName }}</span>
          <span class="type-stat flex-shrink-0" style="font-size: 20px; line-height: 1">{{ row.score }}</span>
        </div>
      </div>
    </div>
  </template>
  <div v-else class="flex flex-col items-center justify-center py-20 text-center">
    <div class="para-chip-sm w-14 h-14 flex items-center justify-center mb-4">
      <i class="pi pi-chart-bar text-content-muted text-xl"></i>
    </div>
    <p class="type-body text-content-secondary">{{ selectedGenre ? `No scores for ${selectedGenre}` : 'No scores yet' }}</p>
  </div>
</template>
```

- [ ] **Step 6.5: Visual check**

Click View → BY JUDGE. Expected: one mini-leaderboard per judge, rank-name-score rows, no row actions, no Top N line.

- [ ] **Step 6.6: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "feat(score): Control leaderboard + Top N line + By-Judge sub-view

Leader row (accent stripe), compact standard rows with per-judge meta
line, glowing Top N separator, eliminated tail collapse. By-Judge view
is now row-based (no DynamicTable). Tie band still placeholder."
```

---

## Task 7: Score.vue — tie resolver band with pool stepper

**Files:**
- Modify: `BES-frontend/src/views/Score.vue` (template + small script tweak)

Refer to spec §3.5.

- [ ] **Step 7.1: Replace the tie-band placeholder with the full band**

Find this from Task 6:
```vue
<div v-if="topNResult.hasTieBreaker && !tieBreakerConfirmed" data-tie-band-placeholder class="opacity-50 px-4 py-2 text-xs text-amber-400">[ tie band — Task 7 ]</div>
```

Replace with:

```vue
<!-- Tie resolver band -->
<div
  v-if="topNResult.hasTieBreaker && !tieBreakerConfirmed"
  role="region"
  aria-label="Tie-breaker resolution"
  class="border border-amber-500/40 bg-amber-500/[0.05] p-4 my-3"
>
  <!-- Header -->
  <div class="flex justify-between items-baseline mb-3 flex-wrap gap-2">
    <span class="type-label text-amber-400 font-bold">
      RESOLVE TIE @ {{ topNResult.tieBreakerScore }} — POOL OF {{ eligibilityPoolNames.size }} FOR {{ spotsFromTie }} SPOT{{ spotsFromTie > 1 ? 'S' : '' }}
    </span>
    <span class="type-label text-content-muted">{{ tieBreakerWinners.size }} SELECTED</span>
  </div>

  <!-- Tie base section -->
  <p class="type-label text-content-muted/60 mb-1" style="font-size: 8px; letter-spacing: 0.24em">TIE BASE · LOCKED</p>
  <div class="flex flex-col gap-1 mb-3">
    <button
      v-for="p in tiedParticipants"
      :key="p.participantName"
      @click="toggleWinner(p.participantName)"
      :disabled="!tieBreakerWinners.has(p.participantName) && tieBreakerWinners.size >= spotsFromTie"
      :aria-pressed="tieBreakerWinners.has(p.participantName)"
      class="flex items-center justify-between px-3 py-2 border-l-2 transition-all disabled:opacity-40 disabled:cursor-not-allowed text-left"
      :class="tieBreakerWinners.has(p.participantName)
        ? 'bg-emerald-500/10 border-emerald-400 ring-1 ring-emerald-500/30'
        : 'bg-surface-700/10 border-surface-600/40 hover:border-surface-500'"
    >
      <div class="flex items-center gap-3">
        <span class="w-4 h-4 rounded-full border-2 flex items-center justify-center flex-shrink-0"
          :class="tieBreakerWinners.has(p.participantName) ? 'bg-emerald-500 border-emerald-500' : 'border-surface-500'">
          <i v-if="tieBreakerWinners.has(p.participantName)" class="pi pi-check text-white" style="font-size: 9px"></i>
        </span>
        <span class="type-body" :class="tieBreakerWinners.has(p.participantName) ? 'text-emerald-400 font-bold' : 'text-content-primary'">
          {{ p.participantName }}
        </span>
      </div>
      <div class="flex items-center gap-2">
        <span class="type-stat" style="font-size: 14px">{{ p.totalScore }}</span>
        <span v-if="tieBreakerWinners.has(p.participantName)" class="type-label px-2 py-0.5 bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">ADVANCES</span>
      </div>
    </button>
  </div>

  <!-- Added pool section -->
  <template v-if="addedToPoolOrdered.length > 0">
    <div class="relative h-px my-3" style="background: linear-gradient(90deg, transparent, rgba(245,158,11,0.4), transparent);">
      <span class="absolute left-1/2 -translate-x-1/2 -top-2.5 bg-surface-900 px-2 type-label text-amber-400" style="font-size: 8px">ADDED · {{ addedToPoolOrdered.length }}</span>
    </div>
    <div class="flex flex-col gap-1 mb-3">
      <button
        v-for="p in addedToPoolOrdered"
        :key="p.participantName"
        @click="toggleWinner(p.participantName)"
        :disabled="!tieBreakerWinners.has(p.participantName) && tieBreakerWinners.size >= spotsFromTie"
        :aria-pressed="tieBreakerWinners.has(p.participantName)"
        class="flex items-center justify-between px-3 py-2 border-l-2 border-amber-500/50 transition-all disabled:opacity-40 disabled:cursor-not-allowed text-left"
        :class="tieBreakerWinners.has(p.participantName) ? 'bg-emerald-500/10 ring-1 ring-emerald-500/30' : 'bg-surface-700/10 hover:bg-surface-700/30'"
      >
        <div class="flex items-center gap-3">
          <span class="w-4 h-4 rounded-full border-2 flex items-center justify-center flex-shrink-0"
            :class="tieBreakerWinners.has(p.participantName) ? 'bg-emerald-500 border-emerald-500' : 'border-surface-500'">
            <i v-if="tieBreakerWinners.has(p.participantName)" class="pi pi-check text-white" style="font-size: 9px"></i>
          </span>
          <span class="type-body" :class="tieBreakerWinners.has(p.participantName) ? 'text-emerald-400 font-bold' : 'text-content-primary'">
            {{ p.participantName }}
          </span>
        </div>
        <div class="flex items-center gap-2">
          <span class="type-stat" style="font-size: 14px">{{ p.totalScore }}</span>
          <span v-if="tieBreakerWinners.has(p.participantName)" class="type-label px-2 py-0.5 bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">ADVANCES</span>
        </div>
      </button>
    </div>
  </template>

  <!-- Stepper buttons -->
  <div class="grid grid-cols-2 gap-2 mb-3">
    <button
      @click="excludeLastFromPool"
      :disabled="!nextEligibleRemove"
      :aria-label="nextEligibleRemove ? `Exclude ${nextEligibleRemove.participantName} from tie pool, score ${nextEligibleRemove.totalScore}` : 'Exclude (disabled)'"
      :aria-disabled="!nextEligibleRemove"
      class="para-chip-sm px-3 py-2.5 type-label text-left disabled:opacity-30 disabled:cursor-not-allowed text-content-muted hover:text-content-primary"
    >
      <span class="opacity-50">−  EXCLUDE</span>&nbsp;
      <span v-if="nextEligibleRemove">{{ nextEligibleRemove.participantName }} · {{ nextEligibleRemove.totalScore }}</span>
      <span v-else>—</span>
    </button>
    <button
      @click="includeNextInPool"
      :disabled="!nextEligibleAdd"
      :aria-label="nextEligibleAdd ? `Include ${nextEligibleAdd.participantName} in tie pool, score ${nextEligibleAdd.totalScore}` : 'Include (disabled)'"
      :aria-disabled="!nextEligibleAdd"
      class="para-chip-sm px-3 py-2.5 type-label text-left bg-amber-500/10 border-amber-500/40 text-amber-400 hover:bg-amber-500/15 disabled:opacity-30 disabled:cursor-not-allowed"
    >
      <span class="opacity-70">+  INCLUDE</span>&nbsp;
      <span v-if="nextEligibleAdd">{{ nextEligibleAdd.participantName }} · {{ nextEligibleAdd.totalScore }}</span>
      <span v-else>—</span>
    </button>
  </div>

  <!-- Confirm + Reset -->
  <div class="flex gap-2">
    <button
      @click="confirmTieBreaker"
      :disabled="tieBreakerWinners.size !== spotsFromTie"
      class="flex-1 py-2.5 type-label font-bold disabled:opacity-30 disabled:cursor-not-allowed transition-all"
      :class="tieBreakerWinners.size === spotsFromTie ? 'bg-emerald-500 text-surface-900 hover:bg-emerald-400' : 'bg-surface-700 text-content-muted border border-surface-600'"
    >
      <i class="pi pi-check mr-1"></i>CONFIRM TOP {{ topNResult.cutoff }}
    </button>
    <button @click="resetTieBreaker" class="px-4 py-2.5 type-label border border-surface-600 text-content-muted hover:text-content-primary">RESET</button>
  </div>
</div>
```

- [ ] **Step 7.2: Update `confirmTieBreaker` to use the full pool (base + added)**

Find the existing `confirmTieBreaker`:

```js
const confirmTieBreaker = async () => {
  if (tieBreakerWinners.value.size === spotsFromTie.value) {
    const resolved = finalRows.value.map(r => r.participantName)
    const res = await setResolvedParticipants(selectedEvent.value, selectedGenre.value, resolved)
    if (res && res.ok) {
      tieBreakerConfirmed.value = true
      saveTieBreaker()
    }
  }
}
```

The behavior is correct, but verify that `finalRows` reflects the chosen winners from the *entire* pool. Update the `finalRows` computed (around line 207–215) to include manually-added winners:

```js
const finalRows = computed(() => {
  if (!topNResult.value.rows) return []
  if (!topNResult.value.hasTieBreaker || !tieBreakerConfirmed.value) {
    return topNResult.value.rows
  }
  const above = topNResult.value.rows.slice(0, topNResult.value.aboveCount)
  // Winners can come from the tie base OR the manually-added pool.
  const poolRows = [...tiedParticipants.value, ...addedToPoolOrdered.value]
  const winners = poolRows.filter(r => tieBreakerWinners.value.has(r.participantName))
  return [...above, ...winners].map((r, i) => ({ ...r, id: i + 1 }))
})
```

- [ ] **Step 7.3: Visual check — happy path**

Find or create an event with a tie at Top 16 (or set Top N to a value that creates a tie in your test data). Expected:
- Tie base rows render with neutral border, locked.
- `+ INCLUDE <name>` is enabled, label shows the next eligible.
- Click `+ INCLUDE`: a new row appears with an amber left-border, divider `ADDED · 1` above it. Buttons update.
- Click `+ INCLUDE` again to add a second.
- Click `− EXCLUDE`: removes the last added.
- Tick a winner from the added pool: `CONFIRM TOP N` becomes enabled.
- Click Confirm: band collapses to the emerald summary row.

- [ ] **Step 7.4: Visual check — edge cases**

- Set Top N to a value where there's no tie: tie band does not render.
- With a tie: try to click `− EXCLUDE` when no manually-added members exist — button is disabled.
- Confirm a tie, then click RESET on the summary row — band reappears, addedToPool cleared.

- [ ] **Step 7.5: Refresh persistence check**

With an added pool not yet confirmed, refresh the page. Expected: pool state restored from localStorage (the `tbKey` payload now includes `addedToPool`).

- [ ] **Step 7.6: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "feat(score): tie resolver band with pool stepper

- Locked tie base + amber-bordered added pool
- + INCLUDE / − EXCLUDE buttons with named target + score
- Same-score order: audition number ASC (deterministic stepper)
- CONFIRM TOP N replaces \"confirm cut\" wording
- finalRows now reflects winners from base + added pool"
```

---

## Task 8: Score.vue — Broadcast mode

**Files:**
- Modify: `BES-frontend/src/views/Score.vue` (template only)

Refer to spec §4.

- [ ] **Step 8.1: Add the Broadcast template branch**

Below the existing By-Judge template (still inside the main page container, alongside the Control mode templates), add:

```vue
<!-- BROADCAST mode -->
<template v-if="mode === 'broadcast'">
  <!-- Header -->
  <div class="mb-6">
    <p class="type-label text-content-muted mb-1">{{ selectedEvent }}<template v-if="hasTeamAndSoloMix"> · {{ selectedEntryType.toUpperCase() }}</template></p>
    <h1 class="type-page-title">{{ selectedGenre }}<span v-if="selectedTopN !== 'All'"> · TOP {{ selectedTopN.replace('Top ', '') }}</span></h1>
    <p class="type-label text-content-muted mt-1">{{ allRowsForPool.length }} SCORED</p>
  </div>

  <!-- Leaderboard -->
  <div v-if="finalRows.length > 0">
    <div :class="broadcastColumns === '2col' ? 'grid grid-cols-2 gap-x-4 gap-y-1.5' : 'flex flex-col gap-1.5'">
      <template v-for="(row, idx) in finalRows" :key="row.participantName">
        <div
          class="para-chip flex items-start gap-3 px-4 py-3"
          :class="idx === 0 ? 'border-l-[3px] border-[color:var(--accent-color)]' : 'bg-surface-700/10'"
          :style="broadcastColumns === '2col' && idx < Math.ceil(finalRows.length / 2)
            ? `order: ${idx * 2}`
            : broadcastColumns === '2col'
              ? `order: ${(idx - Math.ceil(finalRows.length / 2)) * 2 + 1}`
              : ''"
        >
          <span class="type-stat flex-shrink-0" style="font-size: 22px; line-height: 1; min-width: 38px">{{ row.id }}</span>
          <span class="flex-1 type-body text-content-primary" style="font-size: 16px">{{ row.participantName }}</span>
          <span class="type-stat flex-shrink-0" style="font-size: 22px; line-height: 1">{{ row.totalScore }}</span>
        </div>
      </template>
    </div>

    <!-- Top N line -->
    <div
      v-if="selectedTopN !== 'All' && (!topNResult.hasTieBreaker || tieBreakerConfirmed)"
      role="separator"
      :aria-label="`Top ${topNResult.cutoff} line`"
      class="relative h-px my-6"
      style="background: linear-gradient(90deg, transparent, var(--accent-color) 50%, transparent); box-shadow: 0 0 14px var(--accent-muted);"
    >
      <span class="absolute right-0 -top-6 type-label text-content-muted">▲ TOP {{ topNResult.cutoff }}</span>
    </div>

    <!-- Eliminated summary (collapsed) -->
    <p
      v-if="eliminatedSummary"
      class="type-label text-content-muted/50 text-center py-3"
    >⋯ {{ eliminatedSummary.count }} ELIMINATED · LOWEST {{ eliminatedSummary.lowestScore }} ⋯</p>

    <!-- Unresolved tie pending strip (Broadcast read-only) -->
    <div
      v-if="topNResult.hasTieBreaker && !tieBreakerConfirmed"
      role="status"
      class="mt-4 px-4 py-3 border-l-[3px] border-amber-400 bg-amber-500/8 flex flex-wrap items-center gap-3"
    >
      <span class="w-1.5 h-1.5 rounded-full bg-amber-400 shadow-[0_0_8px_rgba(245,158,11,0.6)]"></span>
      <p class="type-label text-amber-400">{{ topNResult.tiedCount }} TIED AT {{ topNResult.tieBreakerScore }} · TIES PENDING — RESOLVE IN CONTROL</p>
    </div>
  </div>

  <!-- Empty state -->
  <div v-else class="flex flex-col items-center justify-center py-20 text-center">
    <p class="type-body text-content-secondary">{{ selectedGenre ? `No scores for ${selectedGenre}` : 'No scores yet' }}</p>
  </div>
</template>
```

- [ ] **Step 8.2: Verify the 2-col column-major order**

Refresh, set Top N to 16, switch to Broadcast. Expected:
- Header reads `<GENRE> · TOP 16` with `<count> SCORED` below.
- 2-column grid. Ranks 1–8 in the left column (top to bottom), 9–16 in the right column. Each row uniform height; score numbers all same size.
- Top N line glows below both columns.
- If a tie is unresolved, an amber strip below says `TIES PENDING — RESOLVE IN CONTROL`.

Set Top N to 8: layout should collapse to a single column.

Resize the window below 640px: 2-col grid should drop to single column even at Top 16.

- [ ] **Step 8.3: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "feat(score): Broadcast mode — uniform rows, 2-col packing

Column-major reading order (1-8 left, 9-16 right for Top 16). All
rows uniform height + same score-number size per spec. Glowing Top
N line below the grid. Unresolved tie shows as read-only amber strip
referencing Control mode for resolution (black-box rule, spec §3.5)."
```

---

## Task 9: Score.vue — remove old code

**Files:**
- Modify: `BES-frontend/src/views/Score.vue`

Now that the new template covers all rendering paths, remove the dead code from the old design.

- [ ] **Step 9.1: Remove pagination state**

In `<script setup>`, delete these lines:

```js
// Pagination for the admin/organiser table
const PAGE_SIZE = 10
const tablePage = ref(1)
watch(finalRows, () => { tablePage.value = 1 })
const totalTablePages = computed(() => Math.max(1, Math.ceil(finalRows.value.length / PAGE_SIZE)))
const pagedFinalRows = computed(() =>
  finalRows.value.slice((tablePage.value - 1) * PAGE_SIZE, tablePage.value * PAGE_SIZE)
)

// Judge column keys for the custom admin table
const judgeColumnKeys = computed(() => {
  if (!topNResult.value.columns) return []
  return topNResult.value.columns
    .filter(c => !['id', 'participantName', 'totalScore'].includes(c.key))
    .map(c => c.key)
})
```

- [ ] **Step 9.2: Remove the DynamicTable import**

Remove if still present:
```js
import DynamicTable from '@/components/DynamicTable.vue';
```

- [ ] **Step 9.3: Remove the old podium template block**

In the template, delete the entire `<!-- Top 3 podium cards -->` block (the `v-if="finalRows.length >= 3"` grid of three `stat-card`s).

- [ ] **Step 9.4: Remove the old "Full Rankings" admin table + DynamicTable fallback**

Delete the entire block from `<!-- Full rankings table: custom for admin/organiser, standard for others -->` down through the `</template>` of the `<template v-else>` non-admin DynamicTable. Also delete the pagination bar (`<div v-if="totalTablePages > 1" ...>`). And delete the `<p v-if="topNResult.hasTieBreaker && !tieBreakerConfirmed" ...>` "Showing N participants…" line.

Also remove the existing inline tie-breaker resolved/pending banner block (`<!-- Resolved banner -->` and `<!-- Pending banner + resolution panel -->`) — those are now part of the new tie band.

- [ ] **Step 9.5: Lint + tests**

```bash
cd BES-frontend && npx eslint src/views/Score.vue 2>&1 | tail -20 && npm test -- --run 2>&1 | tail -10
```

Expected: lint clean, all tests pass.

- [ ] **Step 9.6: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "refactor(score): remove old podium, table, pagination

- Pagination, judgeColumnKeys, podium block, DynamicTable, custom admin
  table, and old tie-breaker banners all removed
- Replaced by the new mode-aware leaderboard + tie band from earlier
  commits"
```

---

## Task 10: Reskin feedback + score breakdown modals

**Files:**
- Modify: `BES-frontend/src/views/Score.vue` (template only — the modal blocks at the bottom)

Refer to spec §5.

- [ ] **Step 10.1: Reskin the feedback panel**

Find the `<!-- Feedback Panel Modal -->` block. Update the panel container to use `clip-path` (parallelogram) and the header to use the section-rule pattern:

Replace the panel's outer `<div role="dialog" ...>` opening:
```vue
<div role="dialog" aria-modal="true" aria-label="Judge feedback"
  class="relative z-10 w-full sm:max-w-lg bg-surface-800 rounded-t-2xl sm:rounded-2xl border border-surface-600/50 shadow-xl max-h-[85vh] flex flex-col">
```
With:
```vue
<div role="dialog" aria-modal="true" aria-label="Judge feedback"
  class="relative z-10 w-full sm:max-w-lg bg-surface-800 border border-surface-600/50 shadow-xl max-h-[85vh] flex flex-col"
  style="clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);">
```

Replace the header block (the `<div class="flex items-center justify-between px-5 py-4 border-b border-surface-700/50">`) with:
```vue
<div class="px-5 py-4">
  <div class="flex items-center justify-between mb-2">
    <h3 class="type-page-title" style="font-size: 18px">JUDGE FEEDBACK</h3>
    <button
      @click="showFeedbackPanel = false"
      aria-label="Close feedback panel"
      class="w-11 h-11 flex items-center justify-center rounded-lg text-content-muted hover:text-content-primary hover:bg-surface-700 transition-colors"
    >
      <i class="pi pi-times text-sm" aria-hidden="true"></i>
    </button>
  </div>
  <div class="section-rule"><div class="section-rule-line"></div></div>
  <p class="type-label text-content-muted mt-2">
    {{ feedbackParticipant }}<span v-if="selectedGenre" class="opacity-60"> · {{ selectedGenre }}</span>
  </p>
</div>
```

Update the tag chips inside the feedback list — replace:
```vue
<span ... class="text-xs px-2.5 py-1 rounded-full font-medium border" :class="...">{{ tag.label }}</span>
```
With a parallelogram chip variant:
```vue
<span
  v-for="tag in tags"
  :key="tag.label"
  class="para-chip-sm type-label px-3 py-1"
  :class="groupName === 'Strengths'
    ? 'border-emerald-500/30 text-emerald-400 bg-emerald-500/10'
    : 'border-amber-500/30 text-amber-400 bg-amber-500/10'"
>{{ tag.label }}</span>
```

- [ ] **Step 10.2: Reskin the score breakdown modal**

In the `<!-- Score Breakdown Modal -->` block, apply the same parallelogram clip to the panel container and convert the header to the section-rule pattern (mirror Step 10.1 with title `SCORE BREAKDOWN`).

- [ ] **Step 10.3: Visual check**

Open Score.vue, click the feedback icon on a row. Modal opens with the new chrome (parallelogram, section-rule). Close button still works. Tag chips have parallelogram shape. Repeat for score breakdown.

- [ ] **Step 10.4: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "ui(score): reskin feedback + breakdown modals

- Parallelogram clip on panel container
- Section-rule pattern in header
- Para-chip-sm shape on feedback tag chips
No behavior changes; same data fetches and close affordance (44px hit
area + aria-label) preserved."
```

---

## Task 11: Accessibility + mobile pass

**Files:**
- Modify: `BES-frontend/src/views/Score.vue`

Refer to spec §8 + §6.

- [ ] **Step 11.1: Audit a11y attributes**

Verify each is present in the file (search the template):

| Attribute | Element |
|-----------|---------|
| `role="group" aria-label="Display mode"` | Mode toggle wrapper |
| `aria-pressed` on each chip | Mode toggle, Genre, View, Type, Top N tiles |
| `role="group" aria-label="Qualify top N"` | Top N picker wrapper |
| `role="status" aria-live="polite"` | Status banner |
| `role="separator" aria-label="Top <N> line"` | Top N line |
| `role="region" aria-label="Tie-breaker resolution"` | Tie band |
| `aria-label="Include <name> in tie pool, score <score>"` | `+ INCLUDE` button |
| `aria-label="Exclude <name> from tie pool, score <score>"` | `− EXCLUDE` button |
| `aria-disabled` on stepper buttons | Both stepper buttons |
| Existing row-action `aria-label` strings | Breakdown / feedback / QR icons |

If any are missing, add them. (Most should be present from Tasks 4–8.)

- [ ] **Step 11.2: Mobile responsive — header**

On mobile (< 640px width), the header row should stack vertically. Verify the `flex-col sm:flex-row` class in the header block from Task 4 — adjust if it doesn't stack.

- [ ] **Step 11.3: Mobile responsive — row actions overflow**

On narrow widths, the 3-icon row-action group can crowd the leader row. Add a media-query-style class to collapse them into a single overflow icon on small screens. In the leader-row template, wrap the icon group:

```vue
<div v-if="isAdminOrOrganiser" class="hidden sm:flex gap-1 flex-shrink-0 opacity-50 hover:opacity-100 transition-opacity">
  <!-- existing buttons -->
</div>
<button
  v-if="isAdminOrOrganiser"
  @click="showRowActions(finalRows[0].participantName)"
  :aria-label="`Actions for ${finalRows[0].participantName}`"
  class="sm:hidden p-2 rounded text-content-muted hover:text-content-primary"
>
  <i class="pi pi-ellipsis-v text-sm" aria-hidden="true"></i>
</button>
```

Add a small `showRowActions` method to the script that opens a simple bottom sheet — for this redesign, the simplest implementation is to reuse the feedback modal style with a list of action buttons. Implement as a `showRowActionSheet` ref + a Teleport:

```js
const rowActionSheet = ref({ open: false, name: '' })
function showRowActions(name) { rowActionSheet.value = { open: true, name } }
function closeRowActions() { rowActionSheet.value.open = false }
```

Add a Teleport at the bottom of the template:

```vue
<Teleport to="body">
  <div v-if="rowActionSheet.open" class="fixed inset-0 z-50 flex items-end justify-center p-0">
    <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="closeRowActions"></div>
    <div role="dialog" aria-modal="true" aria-label="Row actions" class="relative z-10 w-full bg-surface-800 border-t border-surface-600/50 p-4 flex flex-col gap-2" style="clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);">
      <p class="type-label text-content-muted mb-2">{{ rowActionSheet.name.toUpperCase() }}</p>
      <button v-if="topNResult.isMultiAspect" @click="viewBreakdown(rowActionSheet.name); closeRowActions()" class="para-chip-sm type-label px-3 py-3 text-left flex items-center gap-3"><i class="pi pi-chart-bar"></i>VIEW SCORE BREAKDOWN</button>
      <button @click="viewFeedback(rowActionSheet.name); closeRowActions()" class="para-chip-sm type-label px-3 py-3 text-left flex items-center gap-3"><i class="pi pi-comment"></i>VIEW JUDGE FEEDBACK</button>
      <button v-if="resultsReleased && refsMap[rowActionSheet.name]" @click="openQR(rowActionSheet.name); closeRowActions()" class="para-chip-sm type-label px-3 py-3 text-left flex items-center gap-3 text-emerald-400"><i class="pi pi-qrcode"></i>SHOW RESULTS QR</button>
      <button @click="closeRowActions" class="para-chip-sm type-label px-3 py-3 text-center text-content-muted">CANCEL</button>
    </div>
  </div>
</Teleport>
```

- [ ] **Step 11.4: Apply the same pattern to standard rows**

In `inCutStandardRows` template branch, mirror the same hidden/show class split for the row action icons + the overflow button.

- [ ] **Step 11.5: Mobile visual check**

Use browser dev tools to set viewport to 375px. Expected:
- Header stacks vertically.
- Filter chips wrap to multiple lines.
- Top N tiles stay 4-across but shrink (each ~70px tall).
- Row actions collapse to a single `⋮` icon; tapping it opens the bottom sheet with each action as a full-width button.
- Tie band still readable; stepper buttons stack vertically if needed (the `grid-cols-2` may need `sm:grid-cols-2` and `grid-cols-1` on mobile — adjust if cramped).
- Broadcast mode is single column.

- [ ] **Step 11.6: Commit**

```bash
git add BES-frontend/src/views/Score.vue
git commit -m "a11y+mobile(score): aria audit + mobile row-action sheet

- Verified all aria roles/labels from spec §8 are present
- Row actions collapse to overflow icon under sm: breakpoint
- Bottom-sheet pattern for the overflow (parallelogram clip,
  Teleport, click-outside dismiss)"
```

---

## Task 12: Manual verification + screenshot evidence

**Files:** none

Refer to spec §1 (use moments) + §9 (layout invariants).

- [ ] **Step 12.1: Run lint + full test suite**

```bash
cd BES-frontend && npx eslint src/views/Score.vue && npm test -- --run 2>&1 | tail -10
cd BES && mvn test 2>&1 | tail -10
```

Expected: lint clean, all frontend tests pass, all backend tests pass.

- [ ] **Step 12.2: Run the pre-push-verify skill**

This codebase's CLAUDE.md memory says to invoke `pre-push-verify` before pushing. Run it:
```
(invoke the pre-push-verify skill via Skill tool)
```
Expected: skill reports lint + build + tests all green.

- [ ] **Step 12.3: Cinematic verification — Control mode**

Start the dev server and open `/event/score`. Test the golden path against the spec:

1. Header shows `SCOREBOARD · <Event>` label and `<Genre>` title. Release pill and Mode toggle on the right.
2. Switch genre — header title updates.
3. Filter row works (Genre · View · Type).
4. Top N picker — pick 8 / 16 / 32 / ALL; selected tile glows.
5. Status banner reacts: emerald clean, amber tie, red insufficient.
6. Leader row larger with accent stripe; per-judge meta line below the name.
7. Standard rows compact; row actions appear on hover (admin/organiser only).
8. Top N line glows above eliminated tail.
9. Eliminated tail collapses; click to expand.
10. With a tie: tie band renders with locked base + stepper. `+ INCLUDE <name>` adds, `− EXCLUDE <name>` removes. Confirm Top N is disabled until selection equals slot count.
11. Reset clears everything (winners + addedToPool).
12. Refresh: state persists from localStorage.

- [ ] **Step 12.4: Cinematic verification — Broadcast mode**

1. Toggle to Broadcast. Filter chrome and Release pill disappear.
2. Header reads `<GENRE> · TOP N` with `<count> SCORED` below.
3. Top 16: 2-column packing, ranks 1–8 left, 9–16 right.
4. Top 8: single column.
5. Resize to 375px: drops to single column.
6. Unresolved tie shows the amber pending strip — no interactive resolver.

- [ ] **Step 12.5: Layout invariants (§9)**

1. **No truncation:** open an event with a long team name (e.g. > 25 chars). Name wraps to two lines; rank and score stay aligned.
2. **No score truncation:** verify e.g. `28.4` always renders fully — not `28` or `28.`.
3. **No 2-col cramping:** resize browser between 640px and 1200px in Broadcast mode; both columns remain top-aligned even when one has taller wrapped rows.

- [ ] **Step 12.6: Black-box rule (§3.5)**

In Control mode, expand the tie pool with `+ INCLUDE` twice. Switch to Broadcast. Confirm the Broadcast amber strip says `<tiedCount> TIED AT <score>` using the auto-detected count only — NOT the expanded pool size. Switch back to Control: expanded pool still there.

- [ ] **Step 12.7: Mode persistence**

In Control mode, refresh — still Control. Switch to Broadcast, refresh — still Broadcast. Open in a different event — defaults reset (per-event key).

- [ ] **Step 12.8: Manual checklist sign-off**

If all checks pass, mark this task complete. If any check fails, file a follow-up note in the PR description before pushing.

---

## Task 13: Push branch + open PR

**Files:** none

- [ ] **Step 13.1: Push the branch**

```bash
git push -u origin feat/score-redesign
```

- [ ] **Step 13.2: Open PR**

```bash
gh pr create --title "feat: Score.vue redesign — Top N qualifier tool" --body "$(cat <<'EOF'
## Summary

Rewrites the Scoreboard page (`Score.vue`) as a Top N qualifier tool.

- **Mode toggle** — Control (organiser default) vs Broadcast (emcee default); persists per event.
- **Top N hero picker** with semantic status banner (clean / tie / insufficient).
- **Cut-line leaderboard** with leader row + uniform standard rows; glowing Top N separator; eliminated tail collapses.
- **Inline tie resolver** with expandable pool stepper (`+ INCLUDE <name>` / `− EXCLUDE <name>`). Auto-detected tie base is locked; same-score ordering uses audition number ASC. Black-box rule keeps the expanded pool invisible to Broadcast and the public results portal.
- **Broadcast mode** — uniform rows, column-major 2-col packing for Top 16/32, glowing Top N line.
- **Reskinned modals** (feedback + score breakdown) to match cinematic chrome.

## Spec & Plan

- Spec: \`docs/superpowers/specs/2026-06-13-score-redesign-design.md\`
- Plan: \`docs/superpowers/plans/2026-06-13-score-redesign.md\`

## Backend change

One field added: `auditionNumber` on `GetParticipatnScoreDto` (already exists on `EventGenreParticipant`). Required by the tie-pool stepper for deterministic same-score ordering.

## Test plan

- [ ] `npm test -- --run` (Vitest) green — includes new `scoreTiePool.test.js`
- [ ] `mvn test` (backend) green
- [ ] Manual: Control mode — leader row, status banner, Top N picker, tie band stepper (include / exclude / reset / confirm), persistence across refresh
- [ ] Manual: Broadcast mode — uniform rows, 2-col packing at Top 16, mobile single-column under 640px
- [ ] Manual: long team name wraps, never truncates
- [ ] Manual: black-box rule — expanded pool invisible in Broadcast
- [ ] Manual: mode persistence per event

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 13.3: Verify PR**

Open the PR URL `gh pr view --web` and confirm the description renders, CI is running, and the branch shows the expected commits (~10–12 commits).

- [ ] **Step 13.4: Stop the brainstorming server**

```bash
/Users/bennylim/.claude/plugins/cache/claude-plugins-official/superpowers/5.1.0/skills/brainstorming/scripts/stop-server.sh /Users/bennylim/Documents/BES/.superpowers/brainstorm/$(ls -t /Users/bennylim/Documents/BES/.superpowers/brainstorm/ | head -1)
```

---

## Self-review notes

**Spec coverage:**

| Spec § | Task |
|--------|------|
| §1 audience | §12.3 verification |
| §2 mode toggle | Task 3.2 (state) + Task 4 (toggle UI) |
| §3.1 header | Task 4.1 |
| §3.2 filter row | Task 4.1 |
| §3.3 Top N picker + status banner | Task 5 |
| §3.4 leaderboard rows | Task 6.1 + 6.2 |
| §3.5 tie resolver pool stepper | Task 7 (build on helpers from Task 2 + state from Task 3) |
| §3.6 Top N line | Task 6.1 (included in template) |
| §3.7 By-Judge sub-view | Task 6.4 |
| §4 Broadcast mode | Task 8 |
| §5 modal reskin | Task 10 |
| §6 mobile | Task 11 |
| §7 state added | Task 3 |
| §7 backend conditional precondition | Task 1 |
| §8 accessibility | Task 11.1 |
| §9.1 no truncation | Verified §12.5 |
| §9.2 no score truncation | Verified §12.5 |
| §9.3 audition-ASC ordering | Implemented in Task 2 helpers |
| §10 out of scope | (out of scope, no task) |

**Placeholders:** none. Every step has either complete code or an exact command.

**Type consistency:**
- `nextEligibleAdd` / `nextEligibleRemove` returns `{ participantName, totalScore, auditionNumber } | null` everywhere.
- `addedToPool` is `Set<string>` of participant names everywhere.
- `tieBreakerWinners` remains `Set<string>` (existing).
- `tbKey` localStorage payload shape: `{ winners: string[], confirmed: boolean, addedToPool: string[] }`.
- `mode` is `'control' | 'broadcast'` everywhere.
- `broadcastColumns` is `'1col' | '2col'` everywhere.
- Names are consistent: `includeNextInPool` / `excludeLastFromPool` (matching spec §7).
