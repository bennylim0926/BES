# Battle Control Flow Enforcement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure `BattleControl.vue` into a collapsible Setup panel and an always-visible Live panel, then enforce the battle program flow by locking setup controls once the first match starts and hard-blocking dangerous out-of-sequence actions.

**Architecture:** Single-file change (`BattleControl.vue`, 3033 lines). Add a `setupLocked` computed derived from existing state, restructure the template into two cards (Setup + Live), and replace direct action calls with guarded versions. No backend changes, no new API calls.

**Tech Stack:** Vue 3 Composition API, Vitest (frontend tests at `BES-frontend/src/utils/__tests__/`)

---

## File Map

| File | Change |
|------|--------|
| `BES-frontend/src/views/BattleControl.vue` | All changes — script additions + template restructure |

---

### Task 1: Create branch

**Files:**
- No file changes

- [ ] **Step 1: Create and switch to feature branch**

```bash
git checkout master && git pull && git checkout -b feat/battle-control-flow-enforcement
```

Expected: `Switched to a new branch 'feat/battle-control-flow-enforcement'`

---

### Task 2: Add new script refs and computed values

Add all new reactive state and computed values to the `<script setup>` block. No template changes yet — verify the app still compiles.

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (script section, after line 37 where `saveStatus` is declared)

- [ ] **Step 1: Add `setupExpanded`, `setupLocked`, `resetConfirmStep`, `pendingWin`, `pendingStartAt` to script**

Open `BES-frontend/src/views/BattleControl.vue`. After the line:

```js
const _markSaveError = () => { saveStatus.value = 'error' }
```

Insert:

```js
// ── Setup panel state ──────────────────────────────────────────
const setupExpanded = ref(true)

const setupLocked = computed(() =>
  battlePhase.value !== 'IDLE' ||
  currentBattle.value.length > 0 ||
  (!isSmoke.value && Object.values(rounds.value).some(
    list => Array.isArray(list) && list.some(m => Array.isArray(m) && m[2])
  )) ||
  (isSmoke.value && Array.isArray(rounds.value) && rounds.value.some(r => (r?.score ?? 0) > 0))
)

// Auto-collapse setup panel the first time a battle starts
watch(setupLocked, (locked) => {
  if (locked) setupExpanded.value = false
}, { once: true })

// ── Reset bracket inline two-step ─────────────────────────────
const resetConfirmStep = ref(0) // 0 = idle, 1 = awaiting confirm

// ── WIN button confirmation ────────────────────────────────────
// { roundKey, matchIdx, slotIdx, name, replacing } — null when no pending confirm
const pendingWin = ref(null)

const requestWin = (roundKey, matchIdx, slotIdx, name) => {
  const currentWinnerName = rounds.value[roundKey]?.[matchIdx]?.[2] ?? null
  pendingWin.value = { roundKey, matchIdx, slotIdx, name, replacing: currentWinnerName }
}

const confirmWin = () => {
  if (!pendingWin.value) return
  const { roundKey, matchIdx, slotIdx } = pendingWin.value
  setWinner(roundKey, matchIdx, slotIdx)
  pendingWin.value = null
}

const cancelWin = () => { pendingWin.value = null }

// ── Start-from-here confirmation ──────────────────────────────
// { top, pairList, matchIdx } — null when no pending confirm
const pendingStartAt = ref(null)

const requestStartAt = (top, pairList, matchIdx) => {
  pendingStartAt.value = { top, pairList, matchIdx }
}

const confirmStartAt = async () => {
  if (!pendingStartAt.value) return
  const { top, pairList, matchIdx } = pendingStartAt.value
  pendingStartAt.value = null
  await initiateBattlePairAt(top, pairList, matchIdx)
}

const cancelStartAt = () => { pendingStartAt.value = null }

// ── Genre switcher — per-genre status dot ─────────────────────
// Returns 'champion' | 'active' | 'idle'
const genreStatusDot = (genre) => {
  if (genreChampions.value[genre]) return 'champion'
  const phase = genre === selectedGenre.value
    ? battlePhase.value
    : (JSON.parse(localStorage.getItem(genreBattleStateKey(genre)) ?? '{}').phase ?? 'IDLE')
  return ['LOCKED', 'VOTING', 'REVEALED'].includes(phase) ? 'active' : 'idle'
}

const canSwitchGenre = computed(() =>
  battlePhase.value === 'IDLE' || battlePhase.value === 'DECIDED'
)

const genreSwitchBlockReason = computed(() => {
  if (battlePhase.value === 'LOCKED' || battlePhase.value === 'VOTING') return 'Finish this match first'
  if (battlePhase.value === 'REVEALED') return 'Click Next to advance, then switch genres'
  return ''
})
```

- [ ] **Step 2: Replace `requestGenreChange` to use hard-block logic**

Find the current `requestGenreChange` function (around line 1415):

```js
const requestGenreChange = (genre) => {
  if (battlePhase.value !== 'IDLE' && battlePhase.value !== 'DECIDED' && genre !== selectedGenre.value) {
    if (confirm(`A battle is in progress for "${selectedGenre.value}". Switching to "${genre}" will save the current state. Continue?`)) {
      selectedGenre.value = genre
    }
  } else {
    selectedGenre.value = genre
  }
}
```

Replace it with:

```js
const requestGenreChange = (genre) => {
  if (genre === selectedGenre.value) return
  if (!canSwitchGenre.value) return  // hard block — tooltip on button explains why
  selectedGenre.value = genre
}
```

- [ ] **Step 3: Run dev server and confirm no compile errors**

```bash
cd BES-frontend && npm run dev
```

Expected: Server starts on http://localhost:5173 with no console errors. Navigate to `/battle/control` and confirm the page loads.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: add setupLocked, pendingWin, pendingStartAt computed/state for flow enforcement"
```

---

### Task 3: Wrap Setup card in collapsible container + add locked banner

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (template, around line 1862)

- [ ] **Step 1: Replace the Setup card opening tag and add collapsible header**

Find line 1862:
```html
    <!-- Config bar + Bracket (merged) -->
    <div class="card p-5">
      <div class="flex flex-wrap items-center gap-3 mb-4">
```

Replace with:
```html
    <!-- Setup panel (collapsible, locks once battle starts) -->
    <div class="card overflow-hidden">
      <!-- Header — always visible, click to expand/collapse -->
      <div
        class="flex items-center justify-between px-5 py-3 cursor-pointer select-none"
        :class="setupLocked ? 'border-b border-surface-600/40' : (setupExpanded ? 'border-b border-surface-600/40' : '')"
        @click="setupExpanded = !setupExpanded"
      >
        <div class="flex items-center gap-3">
          <span class="type-label text-content-secondary" style="letter-spacing:0.18em">SETUP</span>
          <span
            v-if="setupLocked"
            class="inline-flex items-center gap-1.5 px-2.5 py-1 type-label"
            style="font-size:10px;letter-spacing:0.14em;background:rgba(245,158,11,0.08);border:1px solid rgba(245,158,11,0.25);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
          >
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400" style="box-shadow:0 0 6px rgba(245,158,11,0.7)"></span>
            <span class="text-amber-400">LOCKED · BATTLE IN PROGRESS</span>
          </span>
        </div>
        <i class="pi text-content-muted transition-transform duration-200" :class="setupExpanded ? 'pi-chevron-up' : 'pi-chevron-down'" style="font-size:11px"></i>
      </div>

      <!-- Collapsible content -->
      <div v-show="setupExpanded" class="p-5">

      <!-- Locked banner -->
      <div
        v-if="setupLocked"
        class="mb-4 px-4 py-3 flex items-center gap-3"
        style="border-left:3px solid rgba(245,158,11,0.6);background:rgba(245,158,11,0.06)"
      >
        <span class="type-label text-amber-400" style="font-size:11px;letter-spacing:0.16em">Setup locked — Reset Bracket to modify</span>
      </div>

      <div class="flex flex-wrap items-center gap-3 mb-4">
```

- [ ] **Step 2: Close the new collapsible `<div v-show>` before the end of the card**

Find line 2413:
```html
    </div> <!-- end merged card -->
```

Replace with:
```html
      </div> <!-- end collapsible content -->
    </div> <!-- end setup card -->
```

- [ ] **Step 3: Verify collapse works in browser**

Open http://localhost:5173/battle/control. Click the SETUP header — the content should collapse and expand. The locked banner should not yet appear (no battle started).

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: wrap setup panel in collapsible container with locked banner"
```

---

### Task 4: Hide seeding, bracket-size, judge, and guest edit controls when locked

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (template, various locations in the setup panel)

- [ ] **Step 1: Hide format toggle (bracket size chips) when locked**

Find (around line 1885):
```html
        <!-- Format toggle — hidden for smoke genres (format auto-detected from genre name) -->
        <template v-if="!isGenreSmoke">
```

Replace with:
```html
        <!-- Format toggle — hidden for smoke genres and when locked -->
        <template v-if="!isGenreSmoke && !setupLocked">
```

- [ ] **Step 2: Hide seeding tools row when locked**

Find (around line 2017):
```html
      <div class="flex flex-wrap items-center gap-2 mt-4 mb-5">
        <!-- Pickup crew sort toggle (mixed bracket only) -->
        <template v-if="isMixedBracket">
```

Replace the outer div with:
```html
      <div v-if="!setupLocked" class="flex flex-wrap items-center gap-2 mt-4 mb-5">
        <!-- Pickup crew sort toggle (mixed bracket only) -->
        <template v-if="isMixedBracket">
```

- [ ] **Step 3: Hide seeding pool section when locked**

Find (around line 2162):
```html
      <!-- ── Seeding Pool ──────────────────────────────── -->
      <div class="mb-5">
```

Replace with:
```html
      <!-- ── Seeding Pool ──────────────────────────────── -->
      <div v-if="!setupLocked" class="mb-5">
```

- [ ] **Step 4: Show judge weightage as plain text when locked, input when not**

Find in the judge management section (around line 1917):
```html
            <div class="flex items-center gap-1">
              <span class="type-label text-content-muted" style="font-size:9px;letter-spacing:0.12em">WT</span>
              <input
                type="number"
                :value="j.weightage ?? 1"
                min="1"
                class="w-10 bg-surface-900 border border-surface-600 text-content-primary text-center type-body"
                style="padding:2px 4px;font-size:12px;clip-path:polygon(3px 0%,100% 0%,calc(100% - 3px) 100%,0% 100%)"
                @change="e => submitUpdateJudgeWeightage(j.id, e.target.value)"
              />
            </div>
```

Replace with:
```html
            <div class="flex items-center gap-1">
              <span class="type-label text-content-muted" style="font-size:9px;letter-spacing:0.12em">WT</span>
              <span v-if="setupLocked" class="type-body text-content-muted" style="font-size:12px;min-width:2.5rem;text-align:center">{{ j.weightage ?? 1 }}</span>
              <input
                v-else
                type="number"
                :value="j.weightage ?? 1"
                min="1"
                class="w-10 bg-surface-900 border border-surface-600 text-content-primary text-center type-body"
                style="padding:2px 4px;font-size:12px;clip-path:polygon(3px 0%,100% 0%,calc(100% - 3px) 100%,0% 100%)"
                @change="e => submitUpdateJudgeWeightage(j.id, e.target.value)"
              />
            </div>
```

- [ ] **Step 5: Hide judge remove button when locked**

Find (around line 1928):
```html
            <button
              @click="submitRemoveBattleJudge(j.name)"
              class="flex items-center justify-center hover:text-red-400 transition-colors"
            >
              <i class="pi pi-times text-xs"></i>
            </button>
```

Replace with:
```html
            <button
              v-if="!setupLocked"
              @click="submitRemoveBattleJudge(j.name)"
              class="flex items-center justify-center hover:text-red-400 transition-colors"
            >
              <i class="pi pi-times text-xs"></i>
            </button>
```

- [ ] **Step 6: Hide judge add controls when locked**

Find (around line 1939):
```html
        <!-- Add control pushed to the right -->
        <div class="ml-auto flex items-center gap-2">
```

Replace with:
```html
        <!-- Add control pushed to the right — hidden when locked -->
        <div v-if="!setupLocked" class="ml-auto flex items-center gap-2">
```

- [ ] **Step 7: Hide guest remove button when locked**

Find (around line 2113):
```html
            <!-- remove button — visually separated strip -->
            <button
              @click="submitRemoveBattleGuest(g)"
              class="flex items-center justify-center px-2.5 flex-shrink-0 border-l border-surface-600/40 text-surface-400 hover:text-red-400 hover:bg-red-500/10 transition-colors"
              title="Remove guest"
            >
              <i class="pi pi-times" style="font-size:11px"></i>
            </button>
```

Replace with:
```html
            <!-- remove button — hidden when locked -->
            <button
              v-if="!setupLocked"
              @click="submitRemoveBattleGuest(g)"
              class="flex items-center justify-center px-2.5 flex-shrink-0 border-l border-surface-600/40 text-surface-400 hover:text-red-400 hover:bg-red-500/10 transition-colors"
              title="Remove guest"
            >
              <i class="pi pi-times" style="font-size:11px"></i>
            </button>
```

- [ ] **Step 8: Hide guest add form when locked**

Find (around line 2125):
```html
        <!-- Add guest form pushed to the right -->
        <div class="ml-auto flex flex-col gap-2 flex-shrink-0">
```

Replace with:
```html
        <!-- Add guest form — hidden when locked -->
        <div v-if="!setupLocked" class="ml-auto flex flex-col gap-2 flex-shrink-0">
```

- [ ] **Step 9: Verify in browser**

Start a battle (click Start Round on a filled bracket). Confirm:
- Bracket size chips disappear
- Seeding tools row disappears
- Seeding pool disappears
- Judge weightage becomes plain text, × and Add disappear
- Guest × and Add form disappear
- Judge names and guest names remain visible

- [ ] **Step 10: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: hide seeding, bracket-size, judge, and guest edit controls when setupLocked"
```

---

### Task 5: Lock bracket drag-drop, clear buttons, Start Round; add WIN confirmation

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (bracket template, lines 2196–2412)

- [ ] **Step 1: Lock standard bracket slot drag-drop when setupLocked**

In the standard bracket section, find the draggable name div inside **slot 0** (around line 2249):
```html
                  <div v-if="match[0]"
                    draggable="true"
                    @dragstart="(e) => onDragStart(`Top${size}`, mIdx, 0, e)"
                    @dragend="onDragEnd"
                    class="flex-1 min-w-0 select-none cursor-grab active:cursor-grabbing flex items-center gap-3"
```

Replace with:
```html
                  <div v-if="match[0]"
                    :draggable="!setupLocked"
                    @dragstart="!setupLocked && onDragStart(`Top${size}`, mIdx, 0, $event)"
                    @dragend="!setupLocked && onDragEnd()"
                    class="flex-1 min-w-0 select-none flex items-center gap-3"
                    :class="!setupLocked ? 'cursor-grab active:cursor-grabbing' : ''"
```

Do the same for **slot 1** draggable div (around line 2298):
```html
                  <div v-if="match[1]"
                    draggable="true"
                    @dragstart="(e) => onDragStart(`Top${size}`, mIdx, 1, e)"
                    @dragend="onDragEnd"
                    class="flex-1 min-w-0 select-none cursor-grab active:cursor-grabbing flex items-center gap-3"
```

Replace with:
```html
                  <div v-if="match[1]"
                    :draggable="!setupLocked"
                    @dragstart="!setupLocked && onDragStart(`Top${size}`, mIdx, 1, $event)"
                    @dragend="!setupLocked && onDragEnd()"
                    class="flex-1 min-w-0 select-none flex items-center gap-3"
                    :class="!setupLocked ? 'cursor-grab active:cursor-grabbing' : ''"
```

Also disable drop targets on slots when locked. In both slot 0 and slot 1 wrapper divs, find:
```html
                  @dragover.prevent="onDragOver(`Top${size}`, mIdx, 0)"
                  @dragleave="dragOverKey = null"
                  @drop.prevent="onDrop(`Top${size}`, mIdx, 0)"
```
Replace with:
```html
                  @dragover.prevent="!setupLocked && onDragOver(`Top${size}`, mIdx, 0)"
                  @dragleave="dragOverKey = null"
                  @drop.prevent="!setupLocked && onDrop(`Top${size}`, mIdx, 0)"
```
Do the same for slot 1's handlers (replace `0` with `1`).

- [ ] **Step 2: Hide standard bracket slot clear buttons when locked**

Find (around line 2270):
```html
                  <button v-if="match[0] && !isGuestSlot(match[0])" @click="clearSlot(`Top${size}`, mIdx, 0)"
```

Replace with:
```html
                  <button v-if="!setupLocked && match[0] && !isGuestSlot(match[0])" @click="clearSlot(`Top${size}`, mIdx, 0)"
```

Find (around line 2319):
```html
                  <button v-if="match[1] && !isGuestSlot(match[1])" @click="clearSlot(`Top${size}`, mIdx, 1)"
```

Replace with:
```html
                  <button v-if="!setupLocked && match[1] && !isGuestSlot(match[1])" @click="clearSlot(`Top${size}`, mIdx, 1)"
```

- [ ] **Step 3: Replace WIN button clicks with `requestWin` (both slots)**

Find the slot 0 WIN button (around line 2271):
```html
                  <button
                    :disabled="!match[0]"
                    @click="match[2] === match[0] && match[0] ? clearWinner(`Top${size}`, mIdx) : setWinner(`Top${size}`, mIdx, 0)"
```

Replace with:
```html
                  <button
                    :disabled="!match[0]"
                    @click="match[2] === match[0] && match[0] ? clearWinner(`Top${size}`, mIdx) : requestWin(`Top${size}`, mIdx, 0, match[0])"
```

Find the slot 1 WIN button (around line 2320):
```html
                  <button
                    :disabled="!match[1]"
                    @click="match[2] === match[1] && match[1] ? clearWinner(`Top${size}`, mIdx) : setWinner(`Top${size}`, mIdx, 1)"
```

Replace with:
```html
                  <button
                    :disabled="!match[1]"
                    @click="match[2] === match[1] && match[1] ? clearWinner(`Top${size}`, mIdx) : requestWin(`Top${size}`, mIdx, 1, match[1])"
```

- [ ] **Step 4: Replace "Start from here" direct call with `requestStartAt`**

Find (around line 2328):
```html
                  @click="initiateBattlePairAt(`Top${size}`, rounds[`Top${size}`], mIdx)"
```

Replace with:
```html
                  @click="requestStartAt(`Top${size}`, rounds[`Top${size}`], mIdx)"
```

- [ ] **Step 5: Hide "Start Round" button when setupLocked (use "Start from here" only)**

Find (around line 2337):
```html
            <button
              v-if="effectivePhase === 'IDLE'"
              :disabled="!isActiveRoundFilled"
              @click="initiateBattlePair(`Top${size}`, rounds[`Top${size}`])"
```

Replace with:
```html
            <button
              v-if="effectivePhase === 'IDLE' && !setupLocked"
              :disabled="!isActiveRoundFilled"
              @click="initiateBattlePair(`Top${size}`, rounds[`Top${size}`])"
```

- [ ] **Step 6: Lock smoke queue drag-drop and clear when locked**

Find the smoke queue item div (around line 2364):
```html
          <div
            v-for="(match, mIdx) in rounds"
            :key="mIdx"
            :draggable="!!match.name"
            @dragstart="(e) => onSmokeDragStart(mIdx, e)"
            @dragend="onDragEnd"
            @dragover.prevent="onSmokeDragOver(mIdx)"
            @dragleave="dragOverKey = null"
            @drop.prevent="onSmokeDrop(mIdx)"
```

Replace with:
```html
          <div
            v-for="(match, mIdx) in rounds"
            :key="mIdx"
            :draggable="!!match.name && !setupLocked"
            @dragstart="!setupLocked && onSmokeDragStart(mIdx, $event)"
            @dragend="onDragEnd"
            @dragover.prevent="!setupLocked && onSmokeDragOver(mIdx)"
            @dragleave="dragOverKey = null"
            @drop.prevent="!setupLocked && onSmokeDrop(mIdx)"
```

Hide smoke slot clear button when locked. Find (around line 2396):
```html
            <button
              v-if="match.name && !isGuestSlot(match.name)"
              @click.stop="clearSmokeSlot(mIdx)"
```

Replace with:
```html
            <button
              v-if="!setupLocked && match.name && !isGuestSlot(match.name)"
              @click.stop="clearSmokeSlot(mIdx)"
```

Hide smoke "Start Round" when locked and battle in progress:
Find (around line 2405):
```html
        <button
          @click="initiateBattlePair(0, 0)"
          class="w-full py-2 bg-accent para-chip type-label transition-all duration-200"
        >
```

Replace with:
```html
        <button
          v-if="!setupLocked || currentBattle.length === 0"
          @click="initiateBattlePair(0, 0)"
          class="w-full py-2 bg-accent para-chip type-label transition-all duration-200"
        >
```

- [ ] **Step 7: Add WIN confirmation modal and Start-from-here confirmation modal**

These go just before `</template>` (line 2855). Find the last closing `</div>` before `</template>` (line 2853) and add after it:

```html
    <!-- WIN confirmation modal -->
    <Transition name="fade">
      <div v-if="pendingWin" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
        <div class="card-hover p-6 max-w-sm w-full mx-4 relative">
          <div class="corner-bar-tl"></div>
          <div class="type-page-title text-lg mb-2">
            {{ pendingWin.replacing ? 'Replace Winner?' : 'Set Winner?' }}
          </div>
          <p class="type-body text-content-muted mb-1">
            <template v-if="pendingWin.replacing">
              Replace <span class="text-content-primary">{{ pendingWin.replacing }}</span> with <span class="text-content-primary">{{ pendingWin.name }}</span>?
              The next round slot will be updated.
            </template>
            <template v-else>
              Set <span class="text-content-primary">{{ pendingWin.name }}</span> as winner?
              They will be placed in the next round slot.
            </template>
          </p>
          <p v-if="pendingWin.replacing" class="type-label text-content-muted mb-6" style="font-size:10px;letter-spacing:0.12em">
            If {{ pendingWin.name }} has already played in a later round, correct those results manually.
          </p>
          <p v-else class="mb-6"></p>
          <div class="flex gap-3 justify-end">
            <button @click="cancelWin" class="para-chip-sm px-4 py-2 type-label transition-all">Cancel</button>
            <button @click="confirmWin" class="para-chip-sm px-4 py-2 type-label bg-emerald-500/20 text-emerald-400 border-emerald-500/40 hover:bg-emerald-500/30 transition-all">Confirm</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Start-from-here confirmation modal -->
    <Transition name="fade">
      <div v-if="pendingStartAt" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
        <div class="card-hover p-6 max-w-sm w-full mx-4 relative">
          <div class="corner-bar-tl"></div>
          <div class="type-page-title text-lg mb-2">Start from this match?</div>
          <p class="type-body text-content-muted mb-1">
            <span class="text-content-primary">{{ pendingStartAt?.pairList?.[pendingStartAt.matchIdx]?.[0] }}</span>
            vs
            <span class="text-content-primary">{{ pendingStartAt?.pairList?.[pendingStartAt.matchIdx]?.[1] }}</span>
          </p>
          <p v-if="pendingStartAt?.matchIdx > 0" class="type-label text-content-muted mb-6" style="font-size:10px;letter-spacing:0.12em">
            Matches before this one will be skipped.
          </p>
          <p v-else class="mb-6"></p>
          <div class="flex gap-3 justify-end">
            <button @click="cancelStartAt" class="para-chip-sm px-4 py-2 type-label transition-all">Cancel</button>
            <button @click="confirmStartAt" class="para-chip-sm px-4 py-2 type-label bg-accent transition-all">
              <i class="pi pi-play text-xs mr-1.5"></i>Start
            </button>
          </div>
        </div>
      </div>
    </Transition>
```

- [ ] **Step 8: Verify in browser**

With a bracket filled but no battle started:
- Drag a participant in the bracket — should still work (not locked yet)
- Click WIN — modal appears asking "Set [name] as winner?"
- Confirm — winner is set, ✓ appears
- Click WIN again on the same slot (already has winner shown as ✓) — shows "Replace Winner?" modal
- Start a battle (Start Round) — verify drag-drop stops working and clear buttons disappear

- [ ] **Step 9: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: lock bracket drag-drop and add WIN/start-from-here confirmation dialogs"
```

---

### Task 6: Move genre switcher to Live panel + add status dots + phase-block switching

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (template — remove from Setup, add to Live)

- [ ] **Step 1: Remove genre switcher from Setup card header**

In the Setup card content, find the genre toggle section (around line 1862):
```html
      <div class="flex flex-wrap items-center gap-3 mb-4">
        <!-- Event name -->
        <span class="font-heading font-bold text-base text-content-primary whitespace-nowrap">{{ selectedEvent }}</span>
        <span class="text-surface-600 select-none">|</span>

        <!-- Genre toggle -->
        <div class="flex flex-wrap gap-2">
          <button
            v-for="g in uniqueGenres"
            :key="g"
            @click="requestGenreChange(g)"
            class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150 inline-flex items-center gap-1.5"
            :class="selectedGenre === g
              ? 'text-accent border-[color:var(--accent-muted)]'
              : 'text-content-muted hover:text-content-primary'"
          >
            {{ g }}
            <i v-if="(g === selectedGenre && battlePhase === 'DECIDED') || genreChampions[g]" class="pi pi-star-fill text-[9px] text-amber-400" title="Champion locked — ready to reveal"></i>
          </button>
        </div>
        <!-- Format toggle — hidden for smoke genres and when locked -->
        <template v-if="!isGenreSmoke && !setupLocked">
          <span class="text-surface-600 select-none">|</span>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="s in sizes.filter(s => s !== 7)"
              :key="s"
              @click="requestSizeChange(s)"
              class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150"
              :class="topSize === s
                ? 'text-accent border-[color:var(--accent-muted)]'
                : 'text-content-muted hover:text-content-primary'"
            >Top {{ s }}</button>
          </div>
        </template>
      </div>
```

Replace with (keep event name + format toggle, remove genre toggle):
```html
      <div class="flex flex-wrap items-center gap-3 mb-4">
        <!-- Event name -->
        <span class="font-heading font-bold text-base text-content-primary whitespace-nowrap">{{ selectedEvent }}</span>
        <!-- Format toggle — hidden for smoke genres and when locked -->
        <template v-if="!isGenreSmoke && !setupLocked">
          <span class="text-surface-600 select-none">|</span>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="s in sizes.filter(s => s !== 7)"
              :key="s"
              @click="requestSizeChange(s)"
              class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150"
              :class="topSize === s
                ? 'text-accent border-[color:var(--accent-muted)]'
                : 'text-content-muted hover:text-content-primary'"
            >Top {{ s }}</button>
          </div>
        </template>
      </div>
```

- [ ] **Step 2: Add genre switcher with status dots at the top of the Live panel**

Find the Live panel section rule (around line 2480):
```html
      <div class="section-rule mb-4">
        <span class="section-rule-label">Live Match</span>
        <div class="section-rule-line"></div>
      </div>
```

Insert the genre switcher immediately before this section rule:
```html
      <!-- Genre switcher with per-genre status dots -->
      <div class="flex flex-wrap items-center gap-2 mb-4">
        <span class="type-label text-content-muted" style="font-size:10px;letter-spacing:0.18em">GENRE</span>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="g in uniqueGenres"
            :key="g"
            @click="requestGenreChange(g)"
            class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150 inline-flex items-center gap-1.5"
            :class="[
              selectedGenre === g
                ? 'text-accent border-[color:var(--accent-muted)]'
                : !canSwitchGenre && g !== selectedGenre
                  ? 'text-content-muted/40 cursor-not-allowed'
                  : 'text-content-muted hover:text-content-primary'
            ]"
            :title="g !== selectedGenre && !canSwitchGenre ? genreSwitchBlockReason : ''"
            :disabled="g !== selectedGenre && !canSwitchGenre"
          >
            <!-- Status dot -->
            <template v-if="genreStatusDot(g) === 'champion'">
              <i class="pi pi-star-fill text-[9px] text-amber-400"></i>
            </template>
            <template v-else-if="genreStatusDot(g) === 'active'">
              <span
                class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse"
                style="box-shadow:0 0 6px rgba(245,158,11,0.7)"
              ></span>
            </template>
            <template v-else>
              <span class="inline-block w-1.5 h-1.5 rounded-full bg-surface-400/40"></span>
            </template>
            {{ g }}
          </button>
        </div>
        <!-- Block reason inline hint when switch is blocked -->
        <span
          v-if="uniqueGenres.length > 1 && !canSwitchGenre"
          class="type-label text-amber-400/70"
          style="font-size:10px;letter-spacing:0.12em"
        >{{ genreSwitchBlockReason }}</span>
      </div>

      <div class="section-rule mb-4">
        <span class="section-rule-label">Live Match</span>
        <div class="section-rule-line"></div>
      </div>
```

- [ ] **Step 3: Verify genre switcher in browser**

- Multiple genres should appear as chips with status dots at the top of the Live panel
- Genre selector no longer appears in the Setup header
- Starting a battle sets the amber pulse dot on the active genre
- Attempting to click another genre during LOCKED/VOTING shows it as disabled with tooltip

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: move genre switcher to Live panel with status dots and phase-based blocking"
```

---

### Task 7: Demote Previous button; enforce single-primary-action button table

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (Live panel action buttons, around line 2700)

- [ ] **Step 1: Demote Previous button to small secondary with confirmation**

Find the Previous button in the REVEALED section (around line 2771):
```html
        <!-- REVEALED: previous + next -->
        <template v-if="battlePhase === 'REVEALED'">
          <button
            @click="prevPair"
            class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
          >
            <i class="pi pi-chevron-left text-xs"></i>
            Previous
          </button>
          <button
            @click="nextPair"
            class="bg-accent para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
          >
            Next
            <i class="pi pi-chevron-right text-xs"></i>
          </button>
        </template>
```

Replace with:
```html
        <!-- REVEALED: next (primary) + prev (small secondary, requires confirm) -->
        <template v-if="battlePhase === 'REVEALED'">
          <button
            @click="nextPair"
            class="bg-accent para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
          >
            Next
            <i class="pi pi-chevron-right text-xs"></i>
          </button>
          <button
            v-if="previousBattlePair"
            @click="confirm(`Go back to ${previousBattlePair[0]} vs ${previousBattlePair[1]}? The current match result will be cleared.`) && prevPair()"
            class="para-chip-sm px-3 py-1.5 type-label inline-flex items-center gap-1 text-content-muted hover:text-content-secondary transition-all"
            style="font-size:11px"
            title="Go back to previous match (rarely needed)"
          >
            <i class="pi pi-chevron-left text-[10px]"></i>
            Prev
          </button>
        </template>
```

- [ ] **Step 2: Verify action buttons match the spec table**

Check each phase button is shown correctly (refer to the spec table at `docs/superpowers/specs/2026-06-03-battle-control-flow-enforcement.md`). The existing logic already matches — confirm by running through each phase manually:

- LOCKED → only `Open Voting` shown ✓
- VOTING (non-final, not all voted) → only `Get Score` (disabled) ✓
- VOTING (non-final, all voted) → only `Get Score` (enabled) ✓
- VOTING (final, all voted, clear winner) → `Lock Champion` shown ✓
- REVEALED → `Next ►` (primary) + `‹ Prev` (small secondary) ✓
- DECIDED → `Reveal Champion` + `Unlock` ✓

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: demote Previous button to small secondary with confirmation"
```

---

### Task 8: Replace Reset Bracket modal with two-step inline confirm

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (Live panel always-visible actions + modal section)

- [ ] **Step 1: Replace Reset Bracket button with two-step inline in Live panel**

Find (around line 2808):
```html
      <!-- Always-visible actions -->
      <div class="flex flex-wrap gap-2 mt-2">
        <button
          @click="showResetConfirm = true"
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 text-red-400 border-red-500/30 transition-all"
        >
          <i class="pi pi-refresh text-xs"></i>
          Reset Bracket
        </button>
```

Replace with:
```html
      <!-- Always-visible actions -->
      <div class="flex flex-wrap gap-2 mt-2">
        <!-- Reset Bracket: two-step inline confirm -->
        <template v-if="resetConfirmStep === 0">
          <button
            @click="resetConfirmStep = 1"
            class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 text-red-400 border-red-500/30 hover:border-red-500/60 transition-all"
          >
            <i class="pi pi-refresh text-xs"></i>
            Reset Bracket
          </button>
        </template>
        <template v-else>
          <div
            class="flex flex-wrap items-center gap-3 px-4 py-3 w-full"
            style="border-left:3px solid rgba(239,68,68,0.6);background:rgba(239,68,68,0.08)"
          >
            <span class="type-label text-red-400 flex-1" style="font-size:11px;letter-spacing:0.12em">
              Clear all bracket data for {{ selectedGenre }}? Cannot be undone.
            </span>
            <button
              @click="confirmResetBracket(); resetConfirmStep = 0"
              class="para-chip-sm px-3 py-1.5 type-label bg-red-600/20 text-red-400 border-red-500/40 hover:bg-red-600/30 transition-all whitespace-nowrap"
            >Confirm Reset</button>
            <button
              @click="resetConfirmStep = 0"
              class="para-chip-sm px-3 py-1.5 type-label text-content-muted hover:text-content-primary transition-all"
            >Cancel</button>
          </div>
        </template>
```

- [ ] **Step 2: Remove the old Reset Bracket modal**

Find and delete the entire old modal block (around line 2415):
```html
    <!-- Reset bracket confirmation modal -->
    <Transition name="fade">
      <div v-if="showResetConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
        <div class="card-hover p-6 max-w-sm w-full mx-4 relative">
          <div class="corner-bar-tl"></div>
          <div class="type-page-title text-lg mb-2">Reset Bracket?</div>
          <p class="type-body text-content-muted mb-6">This will clear all bracket data and set the battle phase to IDLE. This cannot be undone.</p>
          <div class="flex gap-3 justify-end">
            <button
              @click="showResetConfirm = false"
              class="para-chip-sm px-4 py-2 type-label transition-all"
            >Cancel</button>
            <button
              @click="confirmResetBracket"
              class="para-chip-sm px-4 py-2 type-label bg-red-600/20 text-red-400 border-red-500/40 hover:bg-red-600/30 transition-all"
            >Reset</button>
          </div>
        </div>
      </div>
    </Transition>
```

Also remove the `showResetConfirm` ref from the script (around line 24):
```js
const showResetConfirm = ref(false)
```

- [ ] **Step 3: Verify Reset Bracket two-step in browser**

- Click Reset Bracket → inline confirmation expands with red warning
- Click Cancel → collapses back
- Click Confirm Reset → bracket clears, confirmation collapses

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: replace reset bracket modal with inline two-step confirm"
```

---

### Task 9: Move overlay settings and image upload to bottom of Live panel

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (template — cut from Setup, paste into Live)

- [ ] **Step 1: Cut overlay settings from Setup card**

Find and remove the entire overlay settings `<details>` block from inside the Setup card (around line 1953–2010):
```html
      <!-- Overlay Settings -->
      <details class="overlay-settings-panel">
        <summary class="overlay-settings-summary">Overlay Settings</summary>
        ...
      </details>
```

- [ ] **Step 2: Paste overlay settings into bottom of Live panel**

In the Live panel, find the "Always-visible actions" div (around line 2808, now after your Task 8 changes). Place the overlay settings block **after** the reset bracket section and **before** the uploaded images list:

```html
      <!-- Overlay settings — always accessible, runtime config -->
      <details class="overlay-settings-panel mt-2">
        <summary class="overlay-settings-summary">Overlay Settings</summary>
        <div class="overlay-settings-body">
          <div class="overlay-setting-row">
            <span class="overlay-setting-label">Left Color</span>
            <div class="overlay-color-group">
              <input
                type="color"
                v-model="overlayConfig.leftColor"
                @change="pushOverlayConfig"
                class="overlay-color-swatch"
                title="Left team color"
              />
              <input
                type="text"
                v-model="overlayConfig.leftColor"
                @change="pushOverlayConfig"
                maxlength="7"
                placeholder="#dc2626"
                class="overlay-hex-input"
              />
            </div>
          </div>
          <div class="overlay-setting-row">
            <span class="overlay-setting-label">Right Color</span>
            <div class="overlay-color-group">
              <input
                type="color"
                v-model="overlayConfig.rightColor"
                @change="pushOverlayConfig"
                class="overlay-color-swatch"
                title="Right team color"
              />
              <input
                type="text"
                v-model="overlayConfig.rightColor"
                @change="pushOverlayConfig"
                maxlength="7"
                placeholder="#2563eb"
                class="overlay-hex-input"
              />
            </div>
          </div>
          <div class="overlay-setting-row">
            <span class="overlay-setting-label">Show Images</span>
            <label class="overlay-toggle">
              <input
                type="checkbox"
                v-model="overlayConfig.showImages"
                @change="pushOverlayConfig"
              />
              <span class="overlay-toggle-track"></span>
            </label>
          </div>
        </div>
        <p v-if="overlayConfigError" class="overlay-config-error">{{ overlayConfigError }}</p>
      </details>
```

- [ ] **Step 3: Move image upload label to Live panel always-visible actions**

Find the image upload label in Always-visible actions (it's currently inside that same div):
```html
        <!-- File upload -->
        <label
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 cursor-pointer transition-all"
        >
          <i class="pi pi-upload text-xs"></i>
          Upload Images
          <input type="file" multiple @change="onFileChange" class="hidden" />
        </label>
```

This is already in the Live panel's always-visible actions (it was there before the restructure). Confirm it's still there after the Task 8 edits — no move needed if it's already in the right place. If it was accidentally cut, add it back inside the `<div class="flex flex-wrap gap-2 mt-2">` alongside the Reset Bracket button.

- [ ] **Step 4: Full end-to-end browser test**

Navigate to `/battle/control`. Verify:
1. Setup panel collapses when first match starts
2. Overlay Settings appear in the Live panel (not Setup)
3. Image upload still works from Live panel
4. Genre chips appear at top of Live panel with correct status dots
5. WIN confirmation fires correctly
6. Start-from-here confirmation fires correctly
7. Reset Bracket shows inline two-step
8. Prev button is small with confirmation
9. Judge/guest controls hidden when locked

- [ ] **Step 5: Final commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: move overlay settings and image upload to Live panel, complete flow enforcement"
```

---

### Task 10: Verify with docker build + open PR

**Files:**
- No code changes

- [ ] **Step 1: Run frontend tests**

```bash
cd BES-frontend && npm test
```

Expected: All existing tests pass. No new tests required (all changes are UI-structural with no extractable pure functions).

- [ ] **Step 2: Build frontend**

```bash
cd BES-frontend && npm run build
```

Expected: Build succeeds with no errors.

- [ ] **Step 3: Docker rebuild**

From the project root:
```bash
docker-compose up --build --no-cache
```

Expected: All containers healthy. Navigate to http://localhost/battle/control and run through the full battle flow:
- Fill bracket → Start Round → Open Voting → Get Score → Next → confirm all guards work
- Switch genre mid-LOCKED → confirm hard block
- Reset Bracket → confirm two-step inline confirm

- [ ] **Step 4: Open PR**

```bash
gh pr create \
  --title "feat: battle control flow enforcement and UI restructure (#50)" \
  --body "$(cat <<'EOF'
## Summary
- Splits BattleControl into collapsible Setup panel (auto-locks once battle starts) and always-visible Live panel
- Hard-blocks genre switching during LOCKED/VOTING/REVEALED phases (tooltip explains why)
- WIN button now requires confirmation (set or replace winner), with cascade warning for replacements
- Start-from-here requires confirmation; Start Round hidden once setup is locked
- All seeding, bracket-size, judge, and guest edit controls hidden (not just disabled) when locked
- Reset Bracket replaced with inline two-step confirm (no modal)
- Previous button demoted to small secondary with confirmation
- Genre switcher moved to Live panel with per-genre status dots (grey/amber-pulse/gold-star)
- Overlay settings and image upload moved to bottom of Live panel (always accessible)

## Test plan
- [ ] Fill bracket for 2+ genres, start Genre A Top16 — Setup panel auto-collapses, locked banner appears
- [ ] During LOCKED phase, click Genre B chip — confirm hard block with tooltip
- [ ] Click WIN button on slot with no winner — confirm "Set winner?" modal
- [ ] Click WIN button on slot that already has winner (✓) — confirm "Replace winner?" modal
- [ ] Click ▶ play icon on match row — confirm "Start from this match?" modal
- [ ] Click Reset Bracket — confirm inline two-step replaces button, Cancel collapses it
- [ ] Verify Previous button is small and requires confirm dialog

Closes #50

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

