# UX Audit & Live-Match Workflow Optimisation — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure BattleControl into separate LiveMatchPanel/Setup components, add Emcee battle backend access, build broadcast timer with auto-unlock at 10s, and apply UX guidance (empty states, error recovery, next-step nudges, role clarity) across all 5 roles.

**Architecture:** Three workstreams executed A → B+C. Stream A splits the 3195-line BattleControl.vue into LiveMatchPanel.vue (shared, read-only for Emcee), BracketViewer.vue (new read-only bracket), BracketEditor.vue (interactive bracket in Setup), and a thin BattleControl.vue orchestrator. Backend method-level @PreAuthorize changes add EMCEE to operator-tier endpoints. Stream B adds BattleTimer.vue with STOMP-based broadcast timer and overlay Netflix-zoom animation. Stream C applies reusable UX patterns (empty states, error banners, next-step nudges, role hints) across all views.

**Tech Stack:** Vue 3 (Composition API, `<script setup>`), Spring Boot (STOMP/WebSocket, Spring Security @PreAuthorize), Vitest (frontend), JUnit 5 + MockMvc (backend)

---

## File Structure

| File | Action | Responsibility |
|------|--------|----------------|
| `BES/src/main/java/com/example/BES/controllers/BattleController.java` | Modify | Add EMCEE to 4 operator-tier methods, add @MessageMapping for timer |
| `BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java` | Create | Verify Emcee can/cannot access correct endpoints |
| `BES-frontend/src/views/BattleControl.vue` | Modify | Thin orchestrator, hide Setup from Emcee |
| `BES-frontend/src/components/LiveMatchPanel.vue` | Create | Extracted live-match UI: genre tabs, round tabs, BracketViewer, BattleTimer, judge votes, match cards, phase actions |
| `BES-frontend/src/components/BracketViewer.vue` | Create | Read-only bracket display, both roles |
| `BES-frontend/src/components/BracketEditor.vue` | Create | Interactive bracket with drag-drop, Admin/Organiser only |
| `BES-frontend/src/components/BattleTimer.vue` | Create | Countdown timer, manual start, auto-unlock at 10s, STOMP broadcast |
| `BES-frontend/src/views/BattleOverlay.vue` | Modify | Timer bar display + Netflix zoom animation |
| `BES-frontend/src/utils/api.js` | Modify | Add `publishTimerState()` STOMP function |
| `BES-frontend/src/utils/__tests__/battleTimer.test.js` | Create | Timer state machine, auto-unlock, manual override |
| `BES-frontend/src/utils/__tests__/liveMatchPanel.test.js` | Create | Component rendering, role-based visibility |

UX guidance pass (Stream C) touches most views — see Tasks 12-16 for specific files.

---

### Task 1: Add EMCEE to BattleController operator-tier methods

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/BattleController.java`

**Background:** The controller currently has method-level `@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")` on mutation endpoints. Four methods need EMCEE added for the operator tier. GET endpoints are already public (no annotation) and fine as-is. Config-tier endpoints (battle-pair, bracket, judges, overlay-config, champion-reveal, battle-mode, smoke, upload, delete, resolved-participants) stay ADMIN/ORGANISER only.

- [ ] **Step 1: Add EMCEE to POST /score**

Line 132, change:
```java
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
```
to:
```java
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
```

- [ ] **Step 2: Add EMCEE to POST /revote**

Line 162, same change:
```java
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
```

- [ ] **Step 3: Add EMCEE to POST /phase**

Line 328, same change:
```java
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
```

- [ ] **Step 4: Add EMCEE to POST /active-genre**

Line 361, same change:
```java
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
```

- [ ] **Step 5: Verify no other endpoints need changes**

Config-tier endpoints that STAY as `hasAnyRole('ADMIN', 'ORGANISER')`:
- POST /battle-mode (line 79)
- POST /battle-pair (line 112)
- DELETE /battle-pair (line 123)
- POST /champion-reveal (line 169)
- DELETE /judge (line 188)
- POST /judge (line 196)
- POST /judge/weightage (line 215)
- POST /upload (line 249)
- DELETE /image (line 289)
- POST /smoke (line 314)
- POST /bracket (line 342)
- POST /overlay-config (line 354)
- POST /resolved-participants (line 382)

- [ ] **Step 6: Build backend to verify compilation**

Run: `cd BES && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/BattleController.java
git commit -m "feat: add EMCEE role to battle operator-tier endpoints (#61)

Add EMCEE to @PreAuthorize on POST /score, /revote, /phase, /active-genre.
Config-tier endpoints (bracket, judges, overlay, etc.) remain ADMIN/ORGANISER only."
```

---

### Task 2: Create BracketViewer.vue (read-only bracket)

**Files:**
- Create: `BES-frontend/src/components/BracketViewer.vue`

**Background:** A read-only bracket display used by both Admin/Organiser and Emcee inside LiveMatchPanel. Shows match cards per round without drag handles or edit controls. Highlights current match, winners, and dimmed upcoming slots.

- [ ] **Step 1: Create the component with props interface**

```vue
<!-- BES-frontend/src/components/BracketViewer.vue -->
<script setup>
import { computed } from 'vue'

const props = defineProps({
  rounds: { type: Object, required: true },
  topSize: { type: Number, required: true },
  currentRound: { type: Number, default: 0 },
  isSmoke: { type: Boolean, default: false },
  currentBattleLeft: { type: String, default: '' },
  currentBattleRight: { type: String, default: '' }
})

const roundNames = computed(() => {
  if (props.isSmoke) return []
  const sizes = []
  let s = props.topSize
  while (s >= 2) {
    if (s < props.topSize) sizes.push(`Top${s}`)
    s = Math.floor(s / 2)
  }
  return sizes
})

const slotClass = (name, round) => {
  if (!name) return 'bracket-slot-empty'
  const isCurrent = name === props.currentBattleLeft || name === props.currentBattleRight
  if (isCurrent) return 'bracket-slot-active'
  // Check if this slot has a winner
  const match = props.rounds[round]
  if (match && Array.isArray(match)) {
    for (const m of match) {
      if (m[2] === name) return 'bracket-slot-winner'
      if ((m[0] === name || m[1] === name) && m[2] && m[2] !== name) return 'bracket-slot-loser'
    }
  }
  return 'bracket-slot-filled'
}
</script>

<template>
  <div class="bracket-viewer">
    <div v-if="isSmoke" class="smoke-viewer">
      <div
        v-for="(battler, idx) in (rounds.Top7 || rounds['7-to-Smoke'] || [])"
        :key="idx"
        class="smoke-slot"
        :class="{ 'smoke-active': idx <= 1 }"
      >
        <span class="smoke-pos">{{ idx + 1 }}</span>
        <span class="smoke-name">{{ battler?.name || '---' }}</span>
        <span class="smoke-score">{{ battler?.score || 0 }}</span>
      </div>
    </div>

    <div v-else class="round-list">
      <div
        v-for="(round, rIdx) in roundNames"
        :key="round"
        class="round-column"
        :class="{ 'round-current': rIdx === currentRound }"
      >
        <div class="round-label type-label">{{ round }}</div>
        <div
          v-for="(match, mIdx) in (rounds[round] || [])"
          :key="`${round}-${mIdx}`"
          class="match-card"
        >
          <div class="match-slot" :class="slotClass(match[0], round)">
            <span class="slot-name">{{ match[0] || '---' }}</span>
          </div>
          <div class="match-slot" :class="slotClass(match[1], round)">
            <span class="slot-name">{{ match[1] || '---' }}</span>
          </div>
          <div v-if="match[2]" class="match-winner-badge type-label">WIN</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.bracket-viewer {
  width: 100%;
}
.round-list {
  display: flex;
  gap: 1rem;
  overflow-x: auto;
}
.round-column {
  flex: 1;
  min-width: 120px;
}
.round-label {
  text-align: center;
  letter-spacing: 0.18em;
  color: var(--content-muted, #888);
  margin-bottom: 0.5rem;
}
.match-card {
  position: relative;
  margin-bottom: 0.5rem;
  padding: 0.375rem;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.07);
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
}
.match-slot {
  padding: 0.25rem 0.5rem;
  font-size: 11px;
  letter-spacing: 0.05em;
  font-family: 'Anton SC', sans-serif;
  text-transform: uppercase;
}
.slot-name {
  color: var(--content-secondary, #999);
}
.bracket-slot-empty .slot-name {
  color: rgba(255,255,255,0.2);
}
.bracket-slot-active {
  background: rgba(245,158,11,0.08);
  border-left: 3px solid rgba(245,158,11,0.6);
}
.bracket-slot-active .slot-name {
  color: rgba(245,158,11,0.9);
}
.bracket-slot-winner .slot-name {
  color: rgba(34,197,94,0.85);
}
.bracket-slot-loser .slot-name {
  color: rgba(255,255,255,0.3);
  text-decoration: line-through;
}
.match-winner-badge {
  position: absolute;
  top: 50%;
  right: -8px;
  transform: translateY(-50%);
  font-size: 8px;
  color: rgba(34,197,94,0.8);
}
.smoke-viewer {
  display: flex;
  flex-direction: column;
  gap: 0.375rem;
}
.smoke-slot {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0.75rem;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.07);
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
}
.smoke-active {
  border-color: rgba(245,158,11,0.3);
  background: rgba(245,158,11,0.05);
}
.smoke-pos {
  font-size: 10px;
  color: var(--content-muted, #666);
  min-width: 20px;
}
.smoke-name {
  flex: 1;
  font-family: 'Anton SC', sans-serif;
  font-size: 12px;
  text-transform: uppercase;
  color: var(--content-primary, #fff);
}
.smoke-score {
  font-size: 18px;
  font-family: 'Anton SC', sans-serif;
  color: var(--accent-color, #fff);
}
</style>
```

- [ ] **Step 2: Verify component compiles**

Run: `cd BES-frontend && npx vue-tsc --noEmit --skipLibCheck src/components/BracketViewer.vue 2>&1 || true`
Expected: No critical errors (warnings acceptable)

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/components/BracketViewer.vue
git commit -m "feat: add BracketViewer read-only bracket component (#61)"
```

---

### Task 3: Create BracketEditor.vue (interactive bracket, extracted from BattleControl)

**Files:**
- Create: `BES-frontend/src/components/BracketEditor.vue`
- Modify: `BES-frontend/src/views/BattleControl.vue` (extract bracket editing template+logic)

**Background:** Extract the interactive bracket editing section from BattleControl.vue (the match cards with drag-drop, win/clear buttons, "Start from this match") into its own component. This is used only by Admin/Organiser inside the Setup panel. Refer to the existing BattleControl.vue template around the bracket match cards section for the exact UI being extracted.

- [ ] **Step 1: Identify the bracket editing section in BattleControl.vue**

The bracket editing section in BattleControl.vue is inside the Setup panel. It includes:
- Round tabs (Top16/Top8/Top4/Top2) in the Setup section
- Match cards with drag-drop slot pickers (uses `parseDropKey` from pointerDnd)
- Win/clear buttons per slot
- "Start from this match" button
- "Start Round" button
- 7-to-Smoke slot queue with drag-reorder

For now, create the component shell and emit interface. The full extraction of template code from BattleControl will be done in Task 5 when BattleControl is refactored.

- [ ] **Step 1: Create BracketEditor.vue with props, emits, and template shell**

```vue
<!-- BES-frontend/src/components/BracketEditor.vue -->
<script setup>
import { computed } from 'vue'
import { parseDropKey } from '@/utils/pointerDnd'

const props = defineProps({
  rounds: { type: Object, required: true },
  topSize: { type: Number, required: true },
  currentRound: { type: Number, default: 0 },
  isSmoke: { type: Boolean, default: false },
  unplacedParticipants: { type: Array, default: () => [] },
  setupLocked: { type: Boolean, default: false },
  memberLookup: { type: Object, default: () => ({}) },
  guestsForCurrentGenre: { type: Array, default: () => [] }
})

const emit = defineEmits([
  'update-rounds',
  'start-battle-at',
  'clear-slot',
  'set-winner',
  'clear-winner',
  'start-round',
  'reorder-smoke'
])

const roundNames = computed(() => {
  if (props.isSmoke) return []
  const sizes = []
  let s = props.topSize
  while (s >= 2) {
    if (s < props.topSize) sizes.push(`Top${s}`)
    s = Math.floor(s / 2)
  }
  return sizes
})

// Drag-drop handlers
function onDragOver(e) { e.preventDefault() }

function onDrop(e, round, matchIdx, slotIdx) {
  e.preventDefault()
  const key = e.dataTransfer?.getData('text/plain')
  if (!key) return
  const parsed = parseDropKey(key)
  if (!parsed) return
  emit('update-rounds', { round, matchIdx, slotIdx, name: parsed.name, isGuest: parsed.isGuest })
}

function onDragStart(e, name, isGuest) {
  e.dataTransfer.setData('text/plain', JSON.stringify({ name, isGuest: !!isGuest }))
}
</script>

<template>
  <div class="bracket-editor">
    <!-- Round tabs -->
    <div v-if="!isSmoke" class="flex flex-wrap gap-2 mb-3">
      <button
        v-for="(round, rIdx) in roundNames"
        :key="round"
        class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150"
        :class="rIdx === currentRound
          ? 'text-accent border-[color:var(--accent-muted)]'
          : 'text-content-muted hover:text-content-primary'"
      >
        {{ round }}
      </button>
    </div>

    <!-- Standard bracket editing — extracted from BattleControl.vue -->
    <!-- Full drag-drop slot editing template will be migrated inline in Task 5 -->
    <div class="text-content-muted type-label" style="font-size:10px; letter-spacing:0.14em">
      BRACKET EDITOR — Interactive slots will be migrated from BattleControl.vue in Task 5
    </div>

    <!-- 7-to-Smoke editing -->
    <div v-if="isSmoke" class="smoke-editor">
      <div
        v-for="(battler, idx) in (rounds.Top7 || rounds['7-to-Smoke'] || [])"
        :key="idx"
        class="smoke-slot flex items-center gap-2 p-2 mb-1"
        style="background:rgba(255,255,255,0.02);border:1px solid rgba(255,255,255,0.06);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
        @dragover="onDragOver"
        @drop="(e) => onDrop(e, 'smoke', idx, 0)"
      >
        <span class="type-label text-content-muted" style="min-width:20px">{{ idx + 1 }}</span>
        <span class="type-body flex-1">{{ battler?.name || '---' }}</span>
        <button
          v-if="battler?.name && !setupLocked"
          class="text-content-muted hover:text-red-400 text-xs"
          @click="emit('clear-slot', { round: 'smoke', idx })"
        >✕</button>
      </div>
    </div>
  </div>
</template>
```

Note: The full interactive bracket template (match cards with drag-drop slots, win/clear buttons, "Start from this match" button, "Start Round" button, guest badges) will be migrated from BattleControl.vue during Task 5.

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/components/BracketEditor.vue
git commit -m "feat: add BracketEditor component shell (#61)"
```

---

### Task 4: Create BattleTimer.vue (countdown, STOMP broadcast, auto-unlock)

**Files:**
- Create: `BES-frontend/src/components/BattleTimer.vue`

- [ ] **Step 1: Create the full component**

```vue
<!-- BES-frontend/src/components/BattleTimer.vue -->
<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { Client } from '@stomp/stompjs'

const props = defineProps({
  phase: { type: String, default: 'IDLE' },        // current battle phase
  stompClient: { type: Client, default: null },    // shared STOMP client
  presets: { type: Array, default: () => [30, 45, 60, 90] }
})

const emit = defineEmits(['unlock'])  // fired when timer hits 10s → trigger phase change

// Timer state
const TIMER_IDLE = 'IDLE'
const TIMER_RUNNING = 'RUNNING'
const timerState = ref(TIMER_IDLE)
const totalDuration = ref(60)
const timeLeft = ref(60)
let intervalId = null

// Computed
const isIdle = computed(() => timerState.value === TIMER_IDLE)
const isRunning = computed(() => timerState.value === TIMER_RUNNING)
const progressPercent = computed(() =>
  totalDuration.value > 0 ? (timeLeft.value / totalDuration.value) * 100 : 0
)
const displayTime = computed(() => {
  const m = Math.floor(timeLeft.value / 60)
  const s = timeLeft.value % 60
  return `${m}:${String(s).padStart(2, '0')}`
})
const isWarning = computed(() => isRunning.value && timeLeft.value <= 10)
const autoUnlocked = ref(false)  // prevent double-unlock

// Broadcast timer state via STOMP
function broadcastTimerState() {
  if (!props.stompClient || !props.stompClient.connected) return
  props.stompClient.publish({
    destination: '/app/battle/timer',
    body: JSON.stringify({
      running: timerState.value === TIMER_RUNNING,
      timeLeft: timeLeft.value,
      totalDuration: totalDuration.value
    })
  })
}

// Start timer
function startTimer(duration) {
  if (!isIdle.value) return
  totalDuration.value = duration || totalDuration.value
  timeLeft.value = totalDuration.value
  timerState.value = TIMER_RUNNING
  autoUnlocked.value = false
  broadcastTimerState()

  intervalId = setInterval(() => {
    timeLeft.value--
    broadcastTimerState()

    // Auto-unlock at 10s remaining
    if (timeLeft.value <= 10 && !autoUnlocked.value) {
      autoUnlocked.value = true
      emit('unlock')
    }

    // Timer naturally expires at 0
    if (timeLeft.value <= 0) {
      stopTimer()
    }
  }, 1000)
}

// Reset timer
function resetTimer() {
  stopTimer()
  timerState.value = TIMER_IDLE
  timeLeft.value = 0
  totalDuration.value = 60
  autoUnlocked.value = false
  broadcastTimerState()
}

// Stop timer (internal)
function stopTimer() {
  if (intervalId) {
    clearInterval(intervalId)
    intervalId = null
  }
  timerState.value = TIMER_IDLE
  broadcastTimerState()
}

// Watch phase changes — reset timer when leaving LOCKED
watch(() => props.phase, (newPhase) => {
  if (newPhase !== 'LOCKED' && isRunning.value) {
    resetTimer()
  }
})

onBeforeUnmount(() => {
  if (intervalId) clearInterval(intervalId)
})
</script>

<template>
  <div class="battle-timer" :class="{ 'timer-warning': isWarning, 'timer-hidden': phase !== 'LOCKED' }">
    <!-- Preset buttons (when idle) -->
    <div v-if="isIdle" class="timer-presets">
      <button
        v-for="p in presets"
        :key="p"
        @click="startTimer(p)"
        class="timer-preset-btn para-chip-sm px-3 py-2 type-label"
      >
        {{ p }}s
      </button>
      <button
        @click="startTimer(60)"
        class="timer-preset-btn timer-preset-start para-chip-sm px-3 py-2 type-label"
      >
        START {{ totalDuration }}s
      </button>
    </div>

    <!-- Running display -->
    <div v-if="isRunning" class="timer-running">
      <div class="timer-progress-bar">
        <div
          class="timer-progress-fill"
          :class="{ 'fill-warning': isWarning }"
          :style="{ width: progressPercent + '%' }"
        ></div>
      </div>
      <div class="timer-display" :class="{ 'text-warning': isWarning }">
        {{ displayTime }}
      </div>
      <button @click="resetTimer" class="timer-reset-btn type-label">
        RESET
      </button>
    </div>
  </div>
</template>

<style scoped>
.battle-timer {
  margin: 0.75rem 0;
}
.timer-hidden {
  opacity: 0.4;
  pointer-events: none;
}
.timer-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  align-items: center;
}
.timer-preset-btn {
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.07);
  color: var(--content-muted, #888);
  cursor: pointer;
  transition: all 150ms;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  font-size: 11px;
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
}
.timer-preset-btn:hover {
  border-color: var(--accent-muted, rgba(255,255,255,0.25));
  color: var(--content-primary, #fff);
}
.timer-preset-start {
  color: var(--accent-color, #fff);
  border-color: var(--accent-muted, rgba(255,255,255,0.25));
}
.timer-running {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}
.timer-progress-bar {
  flex: 1;
  height: 4px;
  background: rgba(255,255,255,0.06);
  border-radius: 2px;
  overflow: hidden;
}
.timer-progress-fill {
  height: 100%;
  background: var(--accent-color, #fff);
  transition: width 1s linear;
  border-radius: 2px;
}
.fill-warning {
  background: #ef4444;
  animation: pulse-bar 0.5s ease-in-out infinite alternate;
}
.timer-display {
  font-family: 'Anton SC', sans-serif;
  font-size: 28px;
  letter-spacing: 0.04em;
  color: var(--content-primary, #fff);
  min-width: 60px;
  text-align: center;
}
.text-warning {
  color: #ef4444;
  animation: pulse-text 0.5s ease-in-out infinite alternate;
}
.timer-reset-btn {
  background: none;
  border: 1px solid rgba(255,255,255,0.1);
  color: var(--content-muted, #888);
  padding: 0.25rem 0.75rem;
  cursor: pointer;
  font-size: 10px;
  letter-spacing: 0.14em;
  clip-path: polygon(3px 0%, 100% 0%, calc(100% - 3px) 100%, 0% 100%);
}
.timer-reset-btn:hover {
  border-color: rgba(239,68,68,0.4);
  color: #ef4444;
}
@keyframes pulse-bar {
  from { opacity: 0.6; }
  to { opacity: 1; }
}
@keyframes pulse-text {
  from { opacity: 0.7; transform: scale(1); }
  to { opacity: 1; transform: scale(1.05); }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/components/BattleTimer.vue
git commit -m "feat: add BattleTimer component with auto-unlock at 10s (#61)"
```

---

### Task 5: Create LiveMatchPanel.vue (extracted from BattleControl)

**Files:**
- Create: `BES-frontend/src/components/LiveMatchPanel.vue`

**Background:** Extract the live match section from BattleControl.vue into this new component. It contains: genre switcher, round tabs, BracketViewer, BattleTimer, phase badge, save status, judge vote grid, match cards, and phase action buttons. Props include `isReadonly` for Emcee.

- [ ] **Step 1: Create LiveMatchPanel.vue**

```vue
<!-- BES-frontend/src/components/LiveMatchPanel.vue -->
<script setup>
import { computed } from 'vue'
import BracketViewer from '@/components/BracketViewer.vue'
import BattleTimer from '@/components/BattleTimer.vue'

const props = defineProps({
  // Data
  selectedEvent: { type: String, required: true },
  selectedGenre: { type: String, required: true },
  uniqueGenres: { type: Array, default: () => [] },
  battlePhase: { type: String, required: true },
  battleJudges: { type: Array, default: () => [] },
  currentBattle: { type: Array, default: () => [] },
  currentWinner: { type: Number, default: -2 },
  currentRound: { type: Number, default: 0 },
  currentTop: { type: String, default: '' },
  rounds: { type: Object, default: () => ({}) },
  topSize: { type: Number, default: 16 },
  isSmoke: { type: Boolean, default: false },
  roundNames: { type: Array, default: () => [] },

  // State
  saveStatus: { type: String, default: 'idle' },
  finalTieBlocked: { type: Boolean, default: false },
  isReadonly: { type: Boolean, default: false },
  canSwitchGenre: { type: Boolean, default: true },
  genreSwitchBlockReason: { type: String, default: '' },
  genreChampions: { type: Object, default: () => ({}) },

  // STOMP
  stompClient: { type: Object, default: null },

  // Overlay config (for vote colors)
  overlayConfig: { type: Object, default: () => ({ leftColor: '#dc2626', rightColor: '#2563eb' }) }
})

const emit = defineEmits([
  'request-genre-change',
  'open-voting',
  'get-score',
  'submit-revote',
  'lock-champion',
  'reveal-champion',
  'dismiss-reveal',
  'next-pair',
  'unlock-champion',
  'set-round',
  'unlock'  // timer auto-unlock
])

function genreStatusDot(g) {
  if (props.genreChampions[g]) return 'champion'
  if (g === props.selectedGenre) return 'active'
  return 'idle'
}

function voteDisplay(judgeId) {
  const v = props.currentBattle.find(b => b?.judgeId === judgeId)
  if (!v) return 'WAITING'
  if (v.vote === 0) return 'LEFT'
  if (v.vote === 1) return 'RIGHT'
  if (v.vote === -1) return 'TIE'
  return 'WAITING'
}

function voteColor(judgeId) {
  const v = props.currentBattle.find(b => b?.judgeId === judgeId)
  if (v?.vote === 0) return props.overlayConfig.leftColor
  if (v?.vote === 1) return props.overlayConfig.rightColor
  return 'transparent'
}
</script>

<template>
  <div class="live-match-panel space-y-4">
    <!-- Role-aware guidance -->
    <div
      v-if="isReadonly"
      class="px-4 py-2.5 type-label flex items-center gap-2"
      style="font-size:10px;letter-spacing:0.12em;border-left:3px solid var(--accent-muted);background:var(--accent-subtle)"
    >
      <span class="inline-block w-2 h-2 rounded-full" style="background:var(--accent-color);box-shadow:0 0 6px var(--accent-muted)"></span>
      You control the battle flow. Start the timer when battlers are on stage.
    </div>

    <!-- Genre switcher -->
    <div class="card px-4 py-3 flex flex-wrap items-center gap-2">
      <span class="type-label text-content-muted" style="font-size:10px;letter-spacing:0.18em">GENRE</span>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="g in uniqueGenres"
          :key="g"
          @click="emit('request-genre-change', g)"
          class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150 inline-flex items-center gap-1.5"
          :class="[
            selectedGenre === g
              ? 'text-accent border-[color:var(--accent-muted)]'
              : !canSwitchGenre && g !== selectedGenre
                ? 'text-content-muted/40 cursor-not-allowed'
                : 'text-content-muted hover:text-content-primary'
          ]"
          :disabled="g !== selectedGenre && !canSwitchGenre"
        >
          <template v-if="genreStatusDot(g) === 'champion'">
            <i class="pi pi-star-fill text-[9px] text-amber-400"></i>
          </template>
          <template v-else-if="genreStatusDot(g) === 'active'">
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" style="box-shadow:0 0 6px rgba(245,158,11,0.7)"></span>
          </template>
          <template v-else>
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-surface-400/40"></span>
          </template>
          {{ g }}
        </button>
      </div>
    </div>

    <!-- Phase badge + Save status -->
    <div class="flex flex-wrap items-center gap-3">
      <div
        class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-2"
        :class="{
          'text-content-muted': battlePhase === 'IDLE',
          'text-amber-400 border-amber-400/30': battlePhase === 'LOCKED',
          'text-green-400 border-green-400/30': battlePhase === 'VOTING',
          'text-accent border-[color:var(--accent-muted)]': battlePhase === 'REVEALED' || battlePhase === 'DECIDED'
        }"
      >
        <span
          class="inline-block w-2 h-2 rounded-full"
          :class="{
            'bg-surface-400': battlePhase === 'IDLE',
            'bg-amber-400 animate-pulse': battlePhase === 'LOCKED' || battlePhase === 'VOTING',
            'bg-green-400': battlePhase === 'REVEALED',
            'bg-accent': battlePhase === 'DECIDED'
          }"
        ></span>
        {{ battlePhase }}
      </div>
      <span
        v-if="saveStatus === 'saving'"
        class="type-label text-content-muted" style="font-size:10px"
      >SAVING...</span>
      <span
        v-if="saveStatus === 'saved'"
        class="type-label text-green-400" style="font-size:10px"
      >SAVED</span>
    </div>

    <!-- Timer (LOCKED phase only) -->
    <BattleTimer
      v-if="!isReadonly || battlePhase === 'LOCKED'"
      :phase="battlePhase"
      :stompClient="stompClient"
      @unlock="emit('unlock')"
    />

    <!-- Round tabs -->
    <div v-if="!isSmoke" class="flex flex-wrap gap-2">
      <button
        v-for="(round, rIdx) in roundNames"
        :key="round"
        @click="emit('set-round', rIdx)"
        class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150"
        :class="rIdx === currentRound
          ? 'text-accent border-[color:var(--accent-muted)]'
          : 'text-content-muted hover:text-content-primary'"
      >
        {{ round }}
      </button>
    </div>

    <!-- Bracket viewer (read-only, both roles) -->
    <BracketViewer
      :rounds="rounds"
      :topSize="topSize"
      :currentRound="currentRound"
      :isSmoke="isSmoke"
      :currentBattleLeft="currentBattle[0]?.left || ''"
      :currentBattleRight="currentBattle[0]?.right || ''"
    />

    <!-- Judge vote grid -->
    <div v-if="battlePhase !== 'IDLE'" class="card p-4">
      <div class="section-rule mb-3">
        <span class="section-rule-label">JUDGE VOTES</span>
        <div class="section-rule-line"></div>
      </div>
      <div class="flex flex-wrap gap-3">
        <div
          v-for="j in battleJudges"
          :key="j.id"
          class="para-chip-sm px-4 py-2.5 flex items-center gap-2"
          style="min-width:120px"
        >
          <span class="type-body text-content-primary">{{ j.name }}</span>
          <span class="type-label text-content-muted" style="font-size:9px">×{{ j.weightage || 1 }}</span>
          <span
            class="type-label ml-auto"
            :style="{ color: voteColor(j.id), fontSize: '11px', letterSpacing: '0.1em' }"
          >{{ voteDisplay(j.id) }}</span>
        </div>
      </div>
    </div>

    <!-- Previous / Current / Next match cards -->
    <div class="flex flex-wrap gap-3">
      <!-- Previous match -->
      <div class="card p-3 flex-1 min-w-[200px]">
        <div class="type-label text-content-muted mb-2" style="font-size:9px;letter-spacing:0.16em">PREVIOUS</div>
        <div class="type-body text-content-muted">{{ currentBattle[1]?.left || '---' }} VS {{ currentBattle[1]?.right || '---' }}</div>
      </div>
      <!-- Current match -->
      <div class="card p-3 flex-1 min-w-[200px]" style="border-color:var(--accent-muted)">
        <div class="type-label mb-2" style="font-size:9px;letter-spacing:0.16em;color:var(--accent-color)">CURRENT</div>
        <div class="type-body text-content-primary">{{ currentBattle[0]?.left || '---' }} VS {{ currentBattle[0]?.right || '---' }}</div>
      </div>
      <!-- Next match (standard only) -->
      <div v-if="!isSmoke" class="card p-3 flex-1 min-w-[200px]">
        <div class="type-label text-content-muted mb-2" style="font-size:9px;letter-spacing:0.16em">NEXT</div>
        <div class="type-body text-content-muted">{{ currentBattle[2]?.left || '---' }} VS {{ currentBattle[2]?.right || '---' }}</div>
      </div>
    </div>

    <!-- Smite queue (7-to-Smoke only) -->
    <div v-if="isSmoke && rounds.Top7" class="card p-4">
      <div class="section-rule mb-3">
        <span class="section-rule-label">QUEUE</span>
        <div class="section-rule-line"></div>
      </div>
      <div v-for="(b, idx) in rounds.Top7.slice(2)" :key="idx" class="type-body text-content-muted py-1">
        {{ idx + 3 }}. {{ b?.name || '---' }} ({{ b?.score || 0 }})
      </div>
    </div>

    <!-- Phase action buttons -->
    <div class="flex flex-wrap gap-2">
      <!-- Open Voting (LOCKED phase) -->
      <button
        v-if="battlePhase === 'LOCKED'"
        @click="emit('open-voting')"
        class="para-chip-sm px-6 py-3 type-label text-accent border-[color:var(--accent-muted)] hover:bg-[color:var(--accent-subtle)] transition-all duration-150"
      >
        OPEN VOTING NOW
      </button>

      <!-- Get Score (VOTING phase, non-final) -->
      <button
        v-if="battlePhase === 'VOTING' && !currentBattle[0]?.isFinal"
        @click="emit('get-score')"
        class="para-chip-sm px-6 py-3 type-label text-content-primary hover:text-accent transition-all duration-150"
      >
        GET SCORE
      </button>

      <!-- Revote (VOTING phase, final, tied) -->
      <button
        v-if="battlePhase === 'VOTING' && finalTieBlocked"
        @click="emit('submit-revote')"
        class="para-chip-sm px-6 py-3 type-label text-red-400 border-red-400/30 hover:bg-red-400/10 transition-all duration-150"
      >
        REVOTE (TIE)
      </button>

      <!-- Lock Champion (VOTING phase, final, all voted) -->
      <button
        v-if="battlePhase === 'VOTING' && currentBattle[0]?.isFinal"
        @click="emit('lock-champion')"
        class="para-chip-sm px-6 py-3 type-label text-amber-400 border-amber-400/30 hover:bg-amber-400/10 transition-all duration-150"
      >
        LOCK CHAMPION
      </button>

      <!-- Reveal Champion (DECIDED phase) -->
      <button
        v-if="battlePhase === 'DECIDED' && !revealActive"
        @click="emit('reveal-champion')"
        class="para-chip-sm px-6 py-3 type-label text-amber-400 border-amber-400/30 hover:bg-amber-400/10 transition-all duration-150"
      >
        REVEAL CHAMPION
      </button>

      <!-- Dismiss Reveal (DECIDED phase, reveal active) -->
      <button
        v-if="battlePhase === 'DECIDED' && revealActive"
        @click="emit('dismiss-reveal')"
        class="para-chip-sm px-6 py-3 type-label text-content-muted hover:text-content-primary transition-all duration-150"
      >
        DISMISS REVEAL
      </button>

      <!-- Unlock Champion (DECIDED phase) -->
      <button
        v-if="battlePhase === 'DECIDED'"
        @click="emit('unlock-champion')"
        class="para-chip-sm px-6 py-3 type-label text-content-muted hover:text-content-primary transition-all duration-150"
      >
        UNLOCK
      </button>

      <!-- Next (REVEALED phase) -->
      <button
        v-if="battlePhase === 'REVEALED'"
        @click="emit('next-pair')"
        class="para-chip-sm px-6 py-3 type-label text-accent border-[color:var(--accent-muted)] hover:bg-[color:var(--accent-subtle)] transition-all duration-150"
      >
        NEXT PAIR
      </button>
    </div>
  </div>
</template>

<style scoped>
.live-match-panel {
  /* container only — all styling is on child components */
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/components/LiveMatchPanel.vue
git commit -m "feat: add LiveMatchPanel extracted from BattleControl (#61)"
```

---

### Task 6: Refactor BattleControl.vue to orchestrator

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

**Background:** Refactor BattleControl.vue into a thin orchestrator (~200 lines of logic) that:
1. Imports and renders LiveMatchPanel (for both roles) and Setup panel (Admin/Organiser only)
2. Keeps all reactive state, API calls, and WebSocket logic
3. Passes data as props to LiveMatchPanel
4. Handles events emitted from LiveMatchPanel
5. Hides Setup panel entirely when user is Emcee

The setup panel (seeding, judge mgmt, BracketEditor, format toggle, guests, overlay settings, reset bracket, upload images) stays in BattleControl.vue but is gated behind `v-if="isAdminOrOrganiser"`.

- [ ] **Step 1: Read the current template to identify Setup vs Live sections**

Determine the exact line ranges in the template for:
- Setup panel content (currently lines 1910-2532)
- Live Match section (currently lines 2577-2921)

- [ ] **Step 2: Add imports and isAdminOrOrganiser computed**

At the top of `<script setup>` in BattleControl.vue, add:
```js
import LiveMatchPanel from '@/components/LiveMatchPanel.vue'
import { useAuthStore } from '@/utils/auth'

const authStore = useAuthStore()
const isAdminOrOrganiser = computed(() => {
  const role = authStore.user?.['role']?.[0]?.authority
  return role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'
})
```

- [ ] **Step 3: Add roundNames computed (moved from Setup to be shared)**

```js
const roundNames = computed(() => {
  if (isSmoke.value) return []
  const sizes = []
  let s = topSize.value
  while (s >= 2) {
    if (s < topSize.value) sizes.push(`Top${s}`)
    s = Math.floor(s / 2)
  }
  return sizes
})
```

- [ ] **Step 4: Add timer unlock handler**

```js
const handleTimerUnlock = async () => {
  if (battlePhase.value !== 'LOCKED') return
  await setBattlePhase('VOTING')
}
```

- [ ] **Step 5: Replace the template's Live Match section with LiveMatchPanel**

Replace the entire Live Match section of the template (from `<!-- Live Match -->` through the phase action buttons) with:
```html
<LiveMatchPanel
  :selectedEvent="selectedEvent"
  :selectedGenre="selectedGenre"
  :uniqueGenres="uniqueGenres"
  :battlePhase="battlePhase"
  :battleJudges="battleJudges"
  :currentBattle="currentBattle"
  :currentWinner="currentWinner"
  :currentRound="currentRound"
  :currentTop="currentTop"
  :rounds="rounds"
  :topSize="topSize"
  :isSmoke="isSmoke"
  :roundNames="roundNames"
  :saveStatus="saveStatus"
  :finalTieBlocked="finalTieBlocked"
  :isReadonly="!isAdminOrOrganiser"
  :canSwitchGenre="canSwitchGenre"
  :genreSwitchBlockReason="genreSwitchBlockReason"
  :genreChampions="genreChampions"
  :stompClient="wsClient"
  :overlayConfig="overlayConfig"
  :revealActive="revealActive"
  @request-genre-change="requestGenreChange"
  @open-voting="openVoting"
  @get-score="submitGetScore"
  @submit-revote="submitRevote"
  @lock-champion="lockChampion"
  @reveal-champion="() => revealChampionForGenre(selectedGenre)"
  @dismiss-reveal="dismissChampionRevealForGenre"
  @next-pair="nextPair"
  @unlock-champion="unlockChampion"
  @set-round="(idx) => { currentRound = idx }"
  @unlock="handleTimerUnlock"
/>
```

- [ ] **Step 6: Wrap Setup panel in v-if**

```html
<div v-if="isAdminOrOrganiser">
  <!-- existing Setup panel content stays here unchanged -->
</div>
```

- [ ] **Step 7: Move genre switcher and round tabs out of Setup**

The genre switcher and round tabs are now in LiveMatchPanel. Remove them from the Setup panel template in BattleControl.vue. The Setup panel should contain only:
- Format toggle
- Judge management
- Seeding controls
- Battle Guests
- BracketEditor (drag-drop slots)
- 7-to-Smoke queue editor
- Reset Bracket + Upload Images
- Overlay Settings

- [ ] **Step 8: Wire the wsClient ref to LiveMatchPanel**

Ensure `wsClient` is available as a ref and passed to LiveMatchPanel as `:stompClient="wsClient"`.

- [ ] **Step 9: Verify the page compiles**

Run: `cd BES-frontend && npx vue-tsc --noEmit --skipLibCheck 2>&1 | head -30`
Expected: No new TypeScript errors from BattleControl.vue or LiveMatchPanel.vue

- [ ] **Step 10: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "refactor: split BattleControl into orchestrator + LiveMatchPanel (#61)

Extract live match UI into LiveMatchPanel component.
Setup panel now hidden from Emcee via isAdminOrOrganiser gate.
Genre switcher, round tabs, bracket viewer, timer, judge votes,
match cards, and phase actions are in LiveMatchPanel."
```

---

### Task 7: Backend — Create STOMP timer message handler

**Files:**
- Create: `BES/src/main/java/com/example/BES/controllers/BattleTimerController.java`

**Background:** The frontend BattleTimer publishes STOMP messages to `/app/battle/timer`. The backend needs a `@MessageMapping` handler that broadcasts to `/topic/battle/timer` so the overlay receives real-time timer state.

- [ ] **Step 1: Create the STOMP controller**

```java
// BES/src/main/java/com/example/BES/controllers/BattleTimerController.java
package com.example.BES.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class BattleTimerController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/battle/timer")
    public void handleTimerState(Map<String, Object> payload) {
        // Broadcast timer state to all overlay subscribers
        messagingTemplate.convertAndSend("/topic/battle/timer", payload);
    }
}
```

- [ ] **Step 2: Build backend to verify compilation**

Run: `cd BES && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/BattleTimerController.java
git commit -m "feat: add STOMP timer broadcast handler (#61)"
```

---

### Task 8: Overlay — Timer bar display + Netflix zoom animation

**Files:**
- Modify: `BES-frontend/src/views/BattleOverlay.vue`

**Background:** Add a timer bar at the top of the overlay (60% width, centered) that:
1. Animates in from top when timer starts (300ms slide + fade)
2. Shows countdown number and progress bar
3. At timeLeft=0, plays Netflix zoom transition (scale 1→20, 400ms, cubic-bezier)
4. Subscribes to `/topic/battle/timer` WebSocket

**Constraint:** Do NOT change BattleOverlay.vue's existing bespoke CSS/structure — add the timer as an additional layer on top.

- [ ] **Step 1: Add timer state ref and WebSocket subscription**

In BattleOverlay.vue's `<script setup>`, add:
```js
import { ref, watch, computed } from 'vue'

// Timer state
const timerState = ref({ running: false, timeLeft: 0, totalDuration: 0 })
const showTimer = ref(false)
const timerFinished = ref(false)

// Subscribe to timer topic (add to existing WebSocket subscriptions)
subscribeToChannel(wsClient, '/topic/battle/timer', (msg) => {
  const data = JSON.parse(msg.body)
  timerState.value = data

  if (data.running && !showTimer.value) {
    showTimer.value = true
    timerFinished.value = false
  }
  if (!data.running && data.timeLeft === 0 && showTimer.value) {
    // Timer finished — trigger Netflix zoom
    timerFinished.value = true
    setTimeout(() => {
      showTimer.value = false
      timerFinished.value = false
    }, 600) // after zoom animation completes
  }
})
```

- [ ] **Step 2: Add timer bar HTML to the template**

Add at the very top of the overlay template (above existing battle content):
```html
<!-- Timer bar overlay -->
<Transition name="timer-enter">
  <div v-if="showTimer" class="timer-overlay" :class="{ 'timer-zoom': timerFinished }">
    <div class="timer-bar-container">
      <div class="timer-progress-track">
        <div
          class="timer-progress-fill"
          :style="{ width: timerState.totalDuration > 0 ? (timerState.timeLeft / timerState.totalDuration) * 100 + '%' : '0%' }"
          :class="{ 'timer-fill-warning': timerState.timeLeft <= 10 }"
        ></div>
      </div>
      <div class="timer-countdown" :class="{ 'timer-text-warning': timerState.timeLeft <= 10 }">
        {{ Math.floor(timerState.timeLeft / 60) }}:{{ String(timerState.timeLeft % 60).padStart(2, '0') }}
      </div>
    </div>
  </div>
</Transition>
```

- [ ] **Step 3: Add CSS for timer bar and animations**

Add to BattleOverlay.vue's `<style scoped>`:
```css
/* Timer overlay — positioned above all battle content */
.timer-overlay {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  z-index: 100;
  width: 60%;
  padding: 16px 0 0 0;
  pointer-events: none;
}

.timer-bar-container {
  background: rgba(0, 0, 0, 0.75);
  border: 1px solid rgba(255, 255, 255, 0.1);
  padding: 8px 20px;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
}

.timer-progress-track {
  height: 3px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 2px;
  margin-bottom: 6px;
  overflow: hidden;
}

.timer-progress-fill {
  height: 100%;
  background: var(--accent-color, #fff);
  transition: width 1s linear;
  border-radius: 2px;
}

.timer-fill-warning {
  background: #ef4444;
  animation: timer-pulse-bar 0.5s ease-in-out infinite alternate;
}

.timer-countdown {
  font-family: 'Anton SC', sans-serif;
  font-size: 24px;
  letter-spacing: 0.06em;
  color: var(--accent-color, #fff);
  text-align: center;
  text-shadow: 0 0 12px var(--accent-muted, rgba(255,255,255,0.25));
}

.timer-text-warning {
  color: #ef4444;
  text-shadow: 0 0 12px rgba(239, 68, 68, 0.5);
  animation: timer-pulse-text 0.5s ease-in-out infinite alternate;
}

/* Enter animation: slide down from top + fade in */
.timer-enter-enter-active {
  animation: timer-slide-in 300ms cubic-bezier(0.22, 0.61, 0.36, 1);
}
.timer-enter-leave-active {
  animation: timer-slide-out 200ms ease-in;
}

@keyframes timer-slide-in {
  from { opacity: 0; transform: translateX(-50%) translateY(-100%); }
  to { opacity: 1; transform: translateX(-50%) translateY(0); }
}
@keyframes timer-slide-out {
  from { opacity: 1; transform: translateX(-50%) translateY(0); }
  to { opacity: 0; transform: translateX(-50%) translateY(-100%); }
}

/* Netflix zoom: scale up and rush past camera */
.timer-zoom {
  animation: netflix-zoom 400ms cubic-bezier(0.55, 0, 0.45, 1) forwards;
  pointer-events: none;
}

@keyframes netflix-zoom {
  0% { transform: translateX(-50%) scale(1); opacity: 1; }
  60% { opacity: 1; }
  100% { transform: translateX(-50%) scale(20); opacity: 0; }
}

@keyframes timer-pulse-bar {
  from { opacity: 0.6; }
  to { opacity: 1; }
}
@keyframes timer-pulse-text {
  from { opacity: 0.7; transform: scale(1); }
  to { opacity: 1; transform: scale(1.03); }
}
```

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BattleOverlay.vue
git commit -m "feat: add broadcast timer bar + Netflix zoom to overlay (#61)"
```

---

### Task 9: Frontend — Integrate timer into LiveMatchPanel and wire unlock

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue` (handleTimerUnlock refinement)

- [ ] **Step 1: Refine the handleTimerUnlock function**

In BattleControl.vue's `<script setup>`, update `handleTimerUnlock`:
```js
const handleTimerUnlock = async () => {
  if (battlePhase.value !== 'LOCKED') return
  try {
    await setBattlePhase('VOTING')
  } catch (err) {
    console.error('Auto-unlock failed:', err)
    // Emcee can still manually open voting via the button
  }
}
```

- [ ] **Step 2: Add STOMP publish for timer state in BattleControl.vue**

The BattleTimer component uses `props.stompClient` to publish to `/app/battle/timer`. Verify that `wsClient` is the active STOMP client and is being passed. In BattleControl.vue, ensure:
```js
// wsClient is already created via createClient() in BattleControl.vue
// It's passed as :stompClient="wsClient" to LiveMatchPanel
// BattleTimer receives it from LiveMatchPanel
```

- [ ] **Step 3: Verify the unlock flow end-to-end order**

The flow is:
1. Emcee presses timer preset button in BattleTimer
2. BattleTimer counts down, broadcasts each second via STOMP
3. At 10s remaining, BattleTimer emits `unlock` event
4. LiveMatchPanel re-emits to BattleControl
5. BattleControl calls `handleTimerUnlock()` → `setBattlePhase('VOTING')`
6. Backend transitions phase, broadcasts `/topic/battle/phase`
7. Overlay plays Netflix zoom, shows voting indicator
8. Judges can now vote

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: wire timer auto-unlock into battle phase flow (#61)"
```

---

### Task 10: Create backend integration test for Emcee auth

**Files:**
- Create: `BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java`

- [ ] **Step 1: Create the test class**

```java
// BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java
package com.example.BES.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BattleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // === Operator-tier: Emcee SHOULD have access ===

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetBattleState() throws Exception {
        mockMvc.perform(get("/api/v1/battle/state"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetBattlePhase() throws Exception {
        mockMvc.perform(get("/api/v1/battle/phase"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetBattleJudges() throws Exception {
        mockMvc.perform(get("/api/v1/battle/judges"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetBracketState() throws Exception {
        mockMvc.perform(get("/api/v1/battle/bracket"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetOverlayConfig() throws Exception {
        mockMvc.perform(get("/api/v1/battle/overlay-config"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetActiveGenre() throws Exception {
        mockMvc.perform(get("/api/v1/battle/active-genre"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetChampions() throws Exception {
        mockMvc.perform(get("/api/v1/battle/champions")
                .param("event", "test-event"))
            .andExpect(status().isOk());
    }

    // === Config-tier: Emcee should NOT have access ===

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_cannotSetBattlePair() throws Exception {
        mockMvc.perform(post("/api/v1/battle/battle-pair")
                .contentType("application/json")
                .content("{\"leftBattler\":\"A\",\"rightBattler\":\"B\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_cannotSetBracket() throws Exception {
        mockMvc.perform(post("/api/v1/battle/bracket")
                .contentType("application/json")
                .content("{\"topSize\":16,\"rounds\":{}}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_cannotSetOverlayConfig() throws Exception {
        mockMvc.perform(post("/api/v1/battle/overlay-config")
                .contentType("application/json")
                .content("{\"leftColor\":\"#ff0000\",\"rightColor\":\"#0000ff\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_cannotAddJudge() throws Exception {
        mockMvc.perform(post("/api/v1/battle/judge")
                .contentType("application/json")
                .content("{\"name\":\"Judge A\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_cannotSetSmokeList() throws Exception {
        mockMvc.perform(post("/api/v1/battle/smoke")
                .contentType("application/json")
                .content("{\"battlers\":[]}"))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 2: Run the tests**

Run: `cd BES && mvn test -Dtest=BattleControllerIntegrationTest`
Expected: All Emcee GET tests pass (200), all Emcee POST config tests pass (403 Forbidden)

- [ ] **Step 3: Commit**

```bash
git add BES/src/test/java/com/example/BES/controllers/BattleControllerIntegrationTest.java
git commit -m "test: add Emcee auth integration tests for battle endpoints (#61)"
```

---

### Task 11: Frontend tests — BattleTimer state machine

**Files:**
- Create: `BES-frontend/src/utils/__tests__/battleTimer.test.js`

- [ ] **Step 1: Create timer unit tests**

```js
// BES-frontend/src/utils/__tests__/battleTimer.test.js
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import BattleTimer from '@/components/BattleTimer.vue'

// Mock STOMP client
const mockStompClient = {
  connected: true,
  publish: vi.fn()
}

describe('BattleTimer', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.restoreAllTimers()
  })

  it('renders preset buttons when idle', () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    expect(wrapper.text()).toContain('30s')
    expect(wrapper.text()).toContain('45s')
    expect(wrapper.text()).toContain('60s')
    expect(wrapper.text()).toContain('90s')
    expect(wrapper.text()).toContain('START')
  })

  it('starts countdown when preset is clicked', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.find('.timer-preset-btn').trigger('click')
    // Timer should now show countdown display
    expect(wrapper.find('.timer-display').exists()).toBe(true)
    expect(wrapper.find('.timer-display').text()).toMatch(/\d:\d{2}/)
  })

  it('broadcasts timer state every second', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.find('.timer-preset-btn').trigger('click')

    vi.advanceTimersByTime(3000)
    // Should have published ~4 times (initial + 3 seconds)
    expect(mockStompClient.publish).toHaveBeenCalled()
    const calls = mockStompClient.publish.mock.calls
    expect(calls.length).toBeGreaterThanOrEqual(3)
  })

  it('emits unlock event at 10s remaining', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    // Start with 20s
    await wrapper.find('.timer-preset-btn').trigger('click') // first preset is 30s

    // Fast-forward to 10s remaining (skip 20 seconds)
    vi.advanceTimersByTime(20000)

    expect(wrapper.emitted('unlock')).toBeTruthy()
    expect(wrapper.emitted('unlock').length).toBe(1)
  })

  it('resets timer when reset button is clicked', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.find('.timer-preset-btn').trigger('click')
    vi.advanceTimersByTime(5000)

    await wrapper.find('.timer-reset-btn').trigger('click')
    // Should be back to idle, showing presets
    expect(wrapper.find('.timer-presets').exists()).toBe(true)
    expect(wrapper.find('.timer-display').exists()).toBe(false)
  })

  it('resets timer when phase leaves LOCKED', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.find('.timer-preset-btn').trigger('click')
    vi.advanceTimersByTime(5000)

    await wrapper.setProps({ phase: 'VOTING' })
    // Should reset
    expect(wrapper.find('.timer-presets').exists()).toBe(true)
  })

  it('shows warning state when timeLeft <= 10', async () => {
    const wrapper = mount(BattleTimer, {
      props: { phase: 'LOCKED', stompClient: mockStompClient }
    })
    await wrapper.find('.timer-preset-btn').trigger('click') // 30s
    vi.advanceTimersByTime(21000) // 9s remaining

    expect(wrapper.find('.timer-warning').exists()).toBe(true)
    expect(wrapper.find('.text-warning').exists()).toBe(true)
  })
})
```

- [ ] **Step 2: Run the timer tests**

Run: `cd BES-frontend && npx vitest run src/utils/__tests__/battleTimer.test.js`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/__tests__/battleTimer.test.js
git commit -m "test: add BattleTimer state machine tests (#61)"
```

---

### Task 12: Frontend tests — LiveMatchPanel rendering

**Files:**
- Create: `BES-frontend/src/utils/__tests__/liveMatchPanel.test.js`

- [ ] **Step 1: Create LiveMatchPanel tests**

```js
// BES-frontend/src/utils/__tests__/liveMatchPanel.test.js
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LiveMatchPanel from '@/components/LiveMatchPanel.vue'

const defaultProps = {
  selectedEvent: 'Test Event',
  selectedGenre: 'Hip Hop',
  uniqueGenres: ['Hip Hop', 'Popping'],
  battlePhase: 'IDLE',
  battleJudges: [],
  currentBattle: [],
  currentWinner: -2,
  currentRound: 0,
  currentTop: 'Top16',
  rounds: {},
  topSize: 16,
  isSmoke: false,
  roundNames: ['Top8', 'Top4', 'Top2'],
  saveStatus: 'idle',
  finalTieBlocked: false,
  isReadonly: false,
  canSwitchGenre: true,
  genreSwitchBlockReason: '',
  genreChampions: {}
}

describe('LiveMatchPanel', () => {
  it('renders genre switcher', () => {
    const wrapper = mount(LiveMatchPanel, { props: defaultProps })
    expect(wrapper.text()).toContain('Hip Hop')
    expect(wrapper.text()).toContain('Popping')
  })

  it('renders phase badge', () => {
    const wrapper = mount(LiveMatchPanel, { props: defaultProps })
    expect(wrapper.text()).toContain('IDLE')
  })

  it('shows role guidance when readonly (Emcee)', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: { ...defaultProps, isReadonly: true }
    })
    expect(wrapper.text()).toContain('control the battle flow')
  })

  it('hides role guidance when not readonly (Admin/Organiser)', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: { ...defaultProps, isReadonly: false }
    })
    expect(wrapper.text()).not.toContain('control the battle flow')
  })

  it('renders open voting button in LOCKED phase', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: { ...defaultProps, battlePhase: 'LOCKED' }
    })
    expect(wrapper.text()).toContain('OPEN VOTING')
  })

  it('renders get score button in VOTING phase', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: { ...defaultProps, battlePhase: 'VOTING' }
    })
    expect(wrapper.text()).toContain('GET SCORE')
  })

  it('renders next pair button in REVEALED phase', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: { ...defaultProps, battlePhase: 'REVEALED' }
    })
    expect(wrapper.text()).toContain('NEXT PAIR')
  })

  it('renders round tabs', () => {
    const wrapper = mount(LiveMatchPanel, { props: defaultProps })
    expect(wrapper.text()).toContain('Top8')
    expect(wrapper.text()).toContain('Top4')
    expect(wrapper.text()).toContain('Top2')
  })

  it('renders judge vote grid when not IDLE', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: {
        ...defaultProps,
        battlePhase: 'VOTING',
        battleJudges: [{ id: 1, name: 'Judge A', weightage: 1 }]
      }
    })
    expect(wrapper.text()).toContain('JUDGE VOTES')
    expect(wrapper.text()).toContain('Judge A')
  })

  it('renders prev/current/next match cards', () => {
    const wrapper = mount(LiveMatchPanel, {
      props: {
        ...defaultProps,
        currentBattle: [
          { left: 'Alice', right: 'Bob' },
          { left: 'PrevL', right: 'PrevR' },
          { left: 'NextL', right: 'NextR' }
        ]
      }
    })
    expect(wrapper.text()).toContain('PREVIOUS')
    expect(wrapper.text()).toContain('CURRENT')
    expect(wrapper.text()).toContain('NEXT')
  })
})
```

- [ ] **Step 2: Run the LiveMatchPanel tests**

Run: `cd BES-frontend && npx vitest run src/utils/__tests__/liveMatchPanel.test.js`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/__tests__/liveMatchPanel.test.js
git commit -m "test: add LiveMatchPanel rendering tests (#61)"
```

---

### Task 13: UX Guidance — Empty states across views

**Files:**
- Modify: `BES-frontend/src/views/EventDetails.vue` (participants list empty state)
- Modify: `BES-frontend/src/views/UpdateEventDetails.vue` (participant table empty state)
- Modify: `BES-frontend/src/views/Events.vue` (no events empty state)
- Modify: `BES-frontend/src/views/Score.vue` (no scores empty state)
- Modify: `BES-frontend/src/views/AuditionList.vue` (no participants empty state)
- Modify: `BES-frontend/src/views/CrewFormation.vue` (no participants empty state)

**Background:** Every list/table that can be empty gets a branded empty state with a clear call-to-action. Follow the pattern from the design spec: section icon/illustration, title describing what's missing, description of how to fix it, and action button(s).

For each view, identify the `v-if="items.length === 0"` or equivalent empty check, and replace any blank/terse empty state with the branded pattern.

- [ ] **Step 1: Audit each view for current empty state handling**

For each file, find where data lists are rendered and identify what's shown when the list is empty.

- [ ] **Step 2: Add empty state for EventDetails participant list**

In `BES-frontend/src/views/EventDetails.vue`, find the participant list section. When no participants exist, show:
```html
<div v-if="participants.length === 0" class="empty-state card p-8 text-center">
  <div class="type-page-title text-content-muted mb-3">NO PARTICIPANTS YET</div>
  <p class="type-body text-content-muted mb-4">Import from Google Sheets or add a walk-in to get started.</p>
  <div class="flex justify-center gap-3">
    <button @click="openSheetImport" class="para-chip-sm px-5 py-3 type-label text-accent border-[color:var(--accent-muted)]">
      IMPORT SHEET
    </button>
    <button @click="openWalkinForm" class="para-chip-sm px-5 py-3 type-label text-content-primary">
      ADD WALK-IN
    </button>
  </div>
</div>
```

- [ ] **Step 3: Add empty state for Events list**

In `BES-frontend/src/views/Events.vue`, when no events exist:
```html
<div v-if="events.length === 0" class="empty-state card p-8 text-center">
  <div class="type-page-title text-content-muted mb-3">NO EVENTS YET</div>
  <p class="type-body text-content-muted mb-4">Create your first event to get started.</p>
  <button @click="showCreateModal = true" class="para-chip-sm px-5 py-3 type-label text-accent border-[color:var(--accent-muted)]">
    CREATE EVENT
  </button>
</div>
```

- [ ] **Step 4: Add empty states for remaining views**

Apply the same pattern to:
- `UpdateEventDetails.vue` — "No participants in this event"
- `Score.vue` — "No scores submitted yet" (different message for emcee vs organiser)
- `AuditionList.vue` — "No participants in this genre" when list is empty
- `CrewFormation.vue` — "No individual participants available for crew formation"

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/
git commit -m "feat: add branded empty states across all views (#61)

Apply empty state pattern (section title + description + CTA button)
to EventDetails, Events, UpdateEventDetails, Score, AuditionList,
and CrewFormation views."
```

---

### Task 14: UX Guidance — Error recovery banners

**Files:**
- Modify: `BES-frontend/src/views/AuditionList.vue` (score submit error)
- Modify: `BES-frontend/src/views/BattleJudge.vue` (voting phase errors)
- Modify: `BES-frontend/src/views/EventDetails.vue` (sheet import errors, walk-in errors)
- Modify: `BES-frontend/src/views/UpdateEventDetails.vue` (email send errors)

**Background:** Add semantic error recovery banners (3px left border + glowing dot pattern) with actionable retry buttons. Each error must answer: what happened, what to do, and provide a retry button.

- [ ] **Step 1: Add error recovery to AuditionList score submission**

In `BES-frontend/src/views/AuditionList.vue`, wrap the score submission in try/catch and add an error ref:
```js
const scoreError = ref(null)

async function submitScore(participantId, score) {
  scoreError.value = null
  try {
    await submitScoreApi(participantId, score)
  } catch (err) {
    scoreError.value = {
      message: 'Score not saved. The server returned an error.',
      participantId,
      score
    }
  }
}
```

Template:
```html
<div
  v-if="scoreError"
  class="error-banner px-4 py-3 flex items-center gap-3"
  style="border-left:3px solid #ef4444;background:rgba(239,68,68,0.06)"
>
  <span class="inline-block w-2 h-2 rounded-full bg-red-500" style="box-shadow:0 0 8px rgba(239,68,68,0.6)"></span>
  <span class="type-body text-red-400 flex-1">{{ scoreError.message }}</span>
  <button @click="retryScoreSubmit(scoreError)" class="para-chip-sm px-3 py-1.5 type-label text-red-400 border-red-400/30">
    RETRY
  </button>
</div>
```

- [ ] **Step 2: Add error recovery to BattleJudge voting**

In `BES-frontend/src/views/BattleJudge.vue`, add error handling for vote submission failures and phase-not-VOTING clarity:
```js
const voteError = ref(null)

async function submitVote(side) {
  voteError.value = null
  try {
    await battleJudgeVote(judgeId.value, side)
  } catch (err) {
    voteError.value = 'Vote not recorded. Tap to retry.'
  }
}
```

Template (phase-not-VOTING message):
```html
<div v-if="battlePhase === 'LOCKED'" class="text-content-muted type-label text-center py-8">
  WAITING FOR OPERATOR TO OPEN VOTING
</div>
<div v-if="battlePhase === 'IDLE'" class="text-content-muted type-label text-center py-8">
  WAITING FOR BATTLE TO START
</div>
```

- [ ] **Step 3: Add error recovery to EventDetails sheet import**

Catch Google Sheets import errors and show the sheet name + row details if available:
```js
const importError = ref(null)

async function importSheet(sheetName) {
  importError.value = null
  try {
    await importParticipantsFromSheet(sheetName)
  } catch (err) {
    importError.value = `Import failed for "${sheetName}". Check the sheet format and try again.`
  }
}
```

- [ ] **Step 4: Add error recovery to UpdateEventDetails email send**

Add per-participant error indicators and batch retry:
```js
const emailErrors = ref({}) // participantId → error message

async function sendEmails(participantIds) {
  for (const id of participantIds) {
    try {
      await sendEmail(id)
      emailErrors.value[id] = null
    } catch (err) {
      emailErrors.value[id] = 'Send failed. Tap to retry.'
    }
  }
}
```

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/
git commit -m "feat: add error recovery banners to audition, battle, event views (#61)"
```

---

### Task 15: UX Guidance — Next-step nudges and role-aware guidance

**Files:**
- Modify: `BES-frontend/src/views/EventDetails.vue` (post-creation nudges)
- Modify: `BES-frontend/src/views/UpdateEventDetails.vue` (post-assignment nudges)
- Modify: `BES-frontend/src/views/Score.vue` (post-release nudge)
- Modify: `BES-frontend/src/views/BattleControl.vue` (post-seed nudge)
- Modify: `BES-frontend/src/views/JudgeSessionView.vue` (role guidance)
- Modify: `BES-frontend/src/views/EmceeSessionView.vue` (role guidance)
- Modify: `BES-frontend/src/views/HelperSessionView.vue` (role guidance)
- Modify: `BES-frontend/src/views/AuditionList.vue` (judge/emcee role guidance)

- [ ] **Step 1: Add next-step nudges to EventDetails**

Add a reactive nudge banner that appears after key actions:
```js
const nudge = ref(null) // { message, link, linkText }

// After importing participants:
nudge.value = { message: 'Participants imported.', link: '/event/update-event-details', linkText: 'Next: Assign judges →' }

// After assigning all judges:
nudge.value = { message: 'All judges assigned.', link: '/event/audition-list', linkText: 'Event ready. Go to Audition List →' }
```

Template:
```html
<div
  v-if="nudge"
  class="nudge-banner px-4 py-3 flex items-center gap-3"
  style="border-left:3px solid var(--accent-muted);background:var(--accent-subtle)"
>
  <span class="type-body flex-1" style="color:var(--accent-color)">{{ nudge.message }}</span>
  <router-link :to="nudge.link" class="para-chip-sm px-3 py-1.5 type-label text-accent border-[color:var(--accent-muted)]">
    {{ nudge.linkText }}
  </router-link>
</div>
```

- [ ] **Step 2: Add next-step nudge to Score (after results release)**

```js
nudge.value = { message: 'Results are now live.', link: '/results', linkText: 'View results portal →' }
```

- [ ] **Step 3: Add next-step nudge to BattleControl (after seeding)**

```js
nudge.value = { message: 'Bracket seeded.', link: null, linkText: 'Ready. Switch to Live Match to begin.' }
```

- [ ] **Step 4: Add role-aware guidance to session hub views**

In `JudgeSessionView.vue`:
```html
<div class="role-guidance px-4 py-3 type-label" style="font-size:10px;letter-spacing:0.12em;border-left:3px solid var(--accent-muted);background:var(--accent-subtle)">
  You are logged in as a Judge. Swipe through audition cards to score participants. Your scores are submitted per participant.
</div>
```

In `EmceeSessionView.vue`:
```html
<div class="role-guidance px-4 py-3 type-label" style="font-size:10px;letter-spacing:0.12em;border-left:3px solid var(--accent-muted);background:var(--accent-subtle)">
  You are logged in as Emcee. Control the audition flow from Audition List, view scores, and manage the live battle.
</div>
```

In `HelperSessionView.vue`:
```html
<div class="role-guidance px-4 py-3 type-label" style="font-size:10px;letter-spacing:0.12em;border-left:3px solid var(--accent-muted);background:var(--accent-subtle)">
  You are logged in as a Helper. Use the Event Day tab to register walk-ins and manage check-ins.
</div>
```

- [ ] **Step 5: Add role-aware guidance to AuditionList**

At top of the view, show role-specific hint:
```html
<div v-if="dynamicRole === 'Judge'" class="px-4 py-2.5 type-label flex items-center gap-2" style="font-size:10px;letter-spacing:0.12em;border-left:3px solid var(--accent-muted);background:var(--accent-subtle)">
  Swipe through cards. Tap score to submit.
</div>
<div v-if="dynamicRole === 'Emcee'" class="px-4 py-2.5 type-label flex items-center gap-2" style="font-size:10px;letter-spacing:0.12em;border-left:3px solid var(--accent-muted);background:var(--accent-subtle)">
  Track participant progress. Use the timer for each performance.
</div>
```

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/
git commit -m "feat: add next-step nudges and role-aware guidance (#61)"
```

---

### Task 16: End-to-end smoke test walkthrough

**Files:**
- Create: `docs/test-scripts/ux-audit-smoke-test.md`

- [ ] **Step 1: Create smoke test checklist**

```markdown
# UX Audit Smoke Test — Issue #61

Walk through every role end-to-end. Check: discoverability, empty states, error recovery, next-step nudges, role clarity.

## Emcee
- [ ] Open /auth/token?t=<token>, confirm landing on EmceeSession
- [ ] Navigate to AuditionList — role guidance visible?
- [ ] Use Emcee timer — clear controls?
- [ ] Navigate to Score — scoreboard readable?
- [ ] Navigate to BattleControl — Setup panel hidden?
- [ ] LiveMatchPanel visible with genre switcher, round tabs, bracket viewer?
- [ ] Start timer during LOCKED phase — countdown works?
- [ ] Timer auto-unlocks at 10s → phase becomes VOTING?
- [ ] Manual "Open Voting Now" button works alongside timer?
- [ ] Phase actions (Get Score, Next Pair, etc.) work?
- [ ] WebSocket disconnect — "Reconnecting…" indicator shown?

## Judge
- [ ] Open /auth/token?t=<token>, confirm landing on JudgeSession
- [ ] Navigate to AuditionList — can score participants?
- [ ] Score submit — confirmation visible?
- [ ] Score submit fails — error banner with retry?
- [ ] Navigate to BattleJudge — judge identity clear?
- [ ] Phase transitions — clear when to vote vs wait?
- [ ] Two-tap voting — confirmation step self-explanatory?
- [ ] Removed from battle — "Not Assigned" overlay shown?

## Helper
- [ ] Open /auth/token?t=<token>, confirm landing on HelperSession
- [ ] Navigate to EventDetails — Event Day tab active by default?
- [ ] Add walk-in — success confirmation?
- [ ] Add walk-in fails (duplicate) — error banner?
- [ ] Check-in flow — participant list scannable?

## Organiser
- [ ] Login, land on MainMenu — active event indicator clear?
- [ ] Create event — flow discoverable?
- [ ] Import participants — sheet import errors actionable?
- [ ] Assign judges — flow clear?
- [ ] Release results — button prominent?
- [ ] BattleControl — Setup panel fully functional?
- [ ] Seed bracket — methods explained?
- [ ] Reset bracket — two-step confirm works?

## Admin
- [ ] All Organiser checks pass
- [ ] AdminPage — genre CRUD, judge management, feedback tags, accent color picker?
```

- [ ] **Step 2: Commit**

```bash
git add docs/test-scripts/ux-audit-smoke-test.md
git commit -m "docs: add UX audit smoke test checklist (#61)"
```

---

### Task 17: Final integration verification

**Files:**
- Modify: None (verification only)

- [ ] **Step 1: Run all frontend tests**

```bash
cd BES-frontend && npx vitest run
```
Expected: All existing + new tests pass

- [ ] **Step 2: Run all backend tests**

```bash
cd BES && mvn test
```
Expected: All tests pass including new BattleControllerIntegrationTest

- [ ] **Step 3: Verify Docker build succeeds**

```bash
docker-compose up --build --no-cache
```
Expected: All services start without errors. Backend compiles. Frontend builds.

- [ ] **Step 4: Quick manual verification in browser**

- Login as Admin → BattleControl → Setup panel visible, LiveMatchPanel visible
- Login as Emcee (token) → BattleControl → Setup hidden, LiveMatchPanel visible with timer
- Open overlay → Timer bar appears when timer starts, Netflix zoom at 0

- [ ] **Step 5: Create the 7-to-Smoke format-level timer issue**

```bash
gh issue create --title "7-to-Smoke format-level timer with highest-score-wins fallback" --body "Per discussion in #61: 7-to-Smoke needs an overall time limit (e.g., 30/45 min). When the format timer expires with no battler reaching 7 points, the highest-scoring battler wins. Tie-breaker handling needed for equal scores. This is separate from the per-round timer implemented in #61."
```

- [ ] **Step 6: Commit any final changes and push**

```bash
git add -A
git commit -m "chore: final integration verification for UX audit (#61)"
git push origin feat/issue-61-ux-audit
```
