# BattleJudge Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild `BattleJudge.vue` with a bold, dark UI matching BattleOverlay, overlay-config-driven colors, localStorage-persisted judge identity and vote state, and a LEFT/RIGHT side-by-side + TIE-at-bottom layout.

**Architecture:** Single-file Vue SFC rebuild. No new components. All state in `<script setup>`. Colors driven by `--left-color` / `--right-color` CSS custom properties sourced from `getOverlayConfig()` + WebSocket. Judge identity and vote stored in `localStorage`, cross-checked against backend on mount.

**Tech Stack:** Vue 3 `<script setup>`, Vitest (manual verification only — no existing component tests in codebase), STOMP WebSocket via `@/utils/websocket`, `@/utils/api`

---

## Task 1: Create feature branch

**Files:**
- No file changes — git operations only

- [ ] **Step 1: Fetch and create branch**

```bash
cd /Users/bennylim/Documents/BES
git fetch origin
git checkout -b feature/battle-judge-redesign
git rebase origin/master
```

Expected: branch created cleanly off latest master.

- [ ] **Step 2: Force-push branch to remote**

```bash
git push -u origin feature/battle-judge-redesign --force-with-lease
```

Expected: remote branch created.

- [ ] **Step 3: Add rebase-on-switch alias to shell profile**

Add this to `~/.zshrc` so switching to any feature branch always pulls latest master first:

```bash
gswm() {
  git fetch origin
  git checkout "$1" 2>/dev/null || git checkout -b "$1"
  git rebase origin/master
}
```

Run to reload:
```bash
source ~/.zshrc
```

From now on, use `gswm feature/battle-judge-redesign` instead of `git checkout`.

---

## Task 2: Rebuild `<script setup>`

**Files:**
- Modify: `BES-frontend/src/views/BattleJudge.vue` — replace entire `<script setup>` block

- [ ] **Step 1: Replace the entire `<script setup>` block**

Replace everything between `<script setup>` and `</script>` with:

```javascript
import { battleJudgeVote, getBattleJudges, getBattlePhase, getCurrentBattlePair, getOverlayConfig } from '@/utils/api'
import { subscribeToChannel, createClient, deactivateClient } from '@/utils/websocket'
import { onMounted, onUnmounted, ref } from 'vue'

// ── Overlay config ──────────────────────────────────────────────────────────
const overlayConfig = ref({ leftColor: '#dc2626', rightColor: '#2563eb' })

// ── Battle state ────────────────────────────────────────────────────────────
const battlePhase    = ref('IDLE')
const leftName       = ref('')
const rightName      = ref('')
const revealedWinner = ref(-2)
const battleJudges   = ref({ judges: [] })

// ── Judge identity ──────────────────────────────────────────────────────────
const judgeId         = ref(null)   // number | null
const judgeName       = ref('')
const showJudgePicker = ref(false)

const LS_JUDGE_ID   = 'battleJudgeId'
const LS_JUDGE_NAME = 'battleJudgeName'

function resolveJudgeIdentity(judges) {
  const storedId = localStorage.getItem(LS_JUDGE_ID)
  if (!storedId) { showJudgePicker.value = true; return }
  const id = Number(storedId)
  const match = judges.find(j => j.id === id)
  if (!match) {
    localStorage.removeItem(LS_JUDGE_ID)
    localStorage.removeItem(LS_JUDGE_NAME)
    showJudgePicker.value = true
    return
  }
  judgeId.value   = id
  judgeName.value = match.name
  setupVoteSubscription()
}

function selectJudge(j) {
  judgeId.value   = j.id
  judgeName.value = j.name
  localStorage.setItem(LS_JUDGE_ID,   String(j.id))
  localStorage.setItem(LS_JUDGE_NAME, j.name)
  showJudgePicker.value = false
  setupVoteSubscription()
}

function clearJudge() {
  judgeId.value   = null
  judgeName.value = ''
  localStorage.removeItem(LS_JUDGE_ID)
  localStorage.removeItem(LS_JUDGE_NAME)
  showJudgePicker.value = true
}

// ── Vote state ──────────────────────────────────────────────────────────────
const active        = ref(null)   // armed: 0 | 1 | -1 | null
const confirmedVote = ref(null)   // confirmed: 0 | 1 | -1 | null

function voteStorageKey() {
  if (judgeId.value == null || !leftName.value || !rightName.value) return null
  return `battleVote_${judgeId.value}_${leftName.value}_${rightName.value}`
}

function saveVote(vote) {
  const key = voteStorageKey()
  if (!key) return
  localStorage.setItem(key, JSON.stringify({ vote, leftName: leftName.value, rightName: rightName.value }))
}

function clearVote() {
  const key = voteStorageKey()
  if (key) localStorage.removeItem(key)
  confirmedVote.value = null
  active.value        = null
}

function restoreVoteFromStorage() {
  const key = voteStorageKey()
  if (!key) return null
  const raw = localStorage.getItem(key)
  if (!raw) return null
  try { return JSON.parse(raw).vote } catch { return null }
}

async function handleClick(side) {
  if (battlePhase.value !== 'VOTING') return
  if (confirmedVote.value !== null) return   // already voted — locked
  if (active.value === side) {
    await battleJudgeVote(judgeId.value, side)
    confirmedVote.value = side
    active.value        = null
    saveVote(side)
    if (navigator.vibrate) navigator.vibrate(40)
  } else {
    active.value = side
  }
}

// ── WebSocket ───────────────────────────────────────────────────────────────
const wsClients  = []
let   voteClient = null

function setupVoteSubscription() {
  if (!judgeId.value || voteClient) return
  voteClient = createClient()
  wsClients.push(voteClient)
  subscribeToChannel(voteClient, `/topic/battle/vote/${judgeId.value}`, (msg) => {
    if (msg.vote !== -3) {
      confirmedVote.value = msg.vote
      active.value        = null
    }
  })
}

// ── Mount ───────────────────────────────────────────────────────────────────
onMounted(async () => {
  // 1. Overlay colors
  const config = await getOverlayConfig()
  if (config?.leftColor) overlayConfig.value = config

  // 2. Phase
  const phaseData = await getBattlePhase()
  battlePhase.value = phaseData?.phase ?? 'IDLE'

  // 3. Current pair (must come before vote restore so key is correct)
  const pairData = await getCurrentBattlePair()
  if (pairData) {
    leftName.value  = pairData.left  ?? ''
    rightName.value = pairData.right ?? ''
  }

  // 4. Judges + identity
  battleJudges.value = await getBattleJudges()
  resolveJudgeIdentity(battleJudges.value?.judges ?? [])

  // 5. Restore vote from localStorage (backend WS overrides if different)
  if (battlePhase.value === 'VOTING') {
    const stored = restoreVoteFromStorage()
    if (stored !== null) confirmedVote.value = stored
  }

  // 6. WS subscriptions
  const cOverlay = createClient(); wsClients.push(cOverlay)
  const cPhase   = createClient(); wsClients.push(cPhase)
  const cPair    = createClient(); wsClients.push(cPair)
  const cScore   = createClient(); wsClients.push(cScore)
  const cJudges  = createClient(); wsClients.push(cJudges)

  subscribeToChannel(cOverlay, '/topic/battle/overlay-config', (msg) => {
    if (msg?.leftColor) overlayConfig.value = msg
  })

  subscribeToChannel(cPhase, '/topic/battle/phase', (msg) => {
    if (!msg?.phase) return
    battlePhase.value = msg.phase
    if (msg.phase === 'LOCKED') {
      clearVote()
      revealedWinner.value = -2
    }
  })

  subscribeToChannel(cPair, '/topic/battle/battle-pair', (msg) => {
    leftName.value  = msg.left  ?? ''
    rightName.value = msg.right ?? ''
    clearVote()
  })

  subscribeToChannel(cScore, '/topic/battle/score', (msg) => {
    revealedWinner.value = msg.message
  })

  subscribeToChannel(cJudges, '/topic/battle/judges', (msg) => {
    battleJudges.value = msg
    if (judgeId.value != null) {
      const still = (msg?.judges ?? []).find(j => j.id === judgeId.value)
      if (!still) clearJudge()
    }
  })
})

onUnmounted(() => {
  wsClients.forEach(c => deactivateClient(c))
})
```

- [ ] **Step 2: Verify the dev server starts without errors**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm run dev 2>&1 | head -30
```

Expected: no compile errors. If there are import errors, check that `getOverlayConfig` exists in `@/utils/api` (it should — it was added in the previous overlay improvements feature).

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/BattleJudge.vue
git commit -m "feat(battle-judge): rebuild script setup — overlay config, judge identity, vote persistence"
```

---

## Task 3: Rebuild `<template>`

**Files:**
- Modify: `BES-frontend/src/views/BattleJudge.vue` — replace entire `<template>` block

- [ ] **Step 1: Replace the entire `<template>` block**

Replace everything between `<template>` and `</template>` with:

```html
<template>
  <div
    class="judge-root"
    :style="{ '--left-color': overlayConfig.leftColor, '--right-color': overlayConfig.rightColor }"
    role="main"
    aria-label="Battle Judge voting panel"
  >

    <!-- ── Header ──────────────────────────────────────────────── -->
    <header class="judge-header">
      <div class="header-brand">
        <span class="brand-pip pip-left" aria-hidden="true"></span>
        <span class="brand-wordmark">BATTLE JUDGE</span>
        <span class="brand-pip pip-right" aria-hidden="true"></span>
      </div>

      <div class="header-center">
        <span
          class="phase-pill"
          :class="`phase-pill--${battlePhase.toLowerCase()}`"
          aria-live="polite"
        >{{ battlePhase }}</span>
      </div>

      <div class="header-right">
        <div v-if="judgeName" class="judge-chip">
          <span class="judge-chip-label">AS</span>
          <span class="judge-chip-name">{{ judgeName }}</span>
          <button class="judge-chip-clear" @click="clearJudge" aria-label="Change judge">✕</button>
        </div>
      </div>
    </header>

    <!-- ── Names bar ───────────────────────────────────────────── -->
    <div v-if="leftName || rightName" class="names-bar" aria-hidden="true">
      <div class="name-cell name-cell-left">
        <span class="name-cell-text">{{ leftName || '???' }}</span>
      </div>
      <div class="name-cell name-cell-right">
        <span class="name-cell-text">{{ rightName || '???' }}</span>
      </div>
    </div>

    <!-- ── Panels wrap ─────────────────────────────────────────── -->
    <div class="panels-wrap" role="group" aria-label="Vote options">

      <!-- Color bleeds -->
      <div class="bleed bleed-left"  aria-hidden="true"></div>
      <div class="bleed bleed-right" aria-hidden="true"></div>

      <!-- Phase blocker (LOCKED / IDLE) -->
      <Transition name="phase-fade">
        <div
          v-if="battlePhase === 'LOCKED' || battlePhase === 'IDLE'"
          class="panels-blocker"
          aria-live="polite"
        >
          <span class="blocker-icon">{{ battlePhase === 'LOCKED' ? '🔒' : '⏳' }}</span>
          <span class="blocker-text">{{ battlePhase === 'LOCKED' ? 'VOTING NOT OPEN' : 'WAITING…' }}</span>
        </div>
      </Transition>

      <!-- REVEALED banner -->
      <Transition name="banner-slide">
        <div
          v-if="battlePhase === 'REVEALED'"
          class="revealed-banner"
          :class="revealedWinner === 0 ? 'banner-left' : revealedWinner === 1 ? 'banner-right' : 'banner-tie'"
          aria-live="assertive"
        >
          <template v-if="revealedWinner === 0">
            <span class="banner-winner-name">{{ leftName }}</span>
            <span class="banner-wins">WINS</span>
          </template>
          <template v-else-if="revealedWinner === 1">
            <span class="banner-winner-name">{{ rightName }}</span>
            <span class="banner-wins">WINS</span>
          </template>
          <template v-else-if="revealedWinner === -1">
            <span class="banner-wins">TIE</span>
          </template>
        </div>
      </Transition>

      <!-- LR row -->
      <div class="lr-row">

        <!-- LEFT -->
        <button
          class="vote-panel panel-left"
          :class="{
            'is-armed': active === 0 && confirmedVote === null,
            'is-voted': confirmedVote === 0,
            'is-dim':   confirmedVote !== null && confirmedVote !== 0,
          }"
          @click="handleClick(0)"
          :disabled="battlePhase !== 'VOTING'"
          :aria-label="confirmedVote === 0 ? 'Left — vote locked' : active === 0 ? 'Left — tap again to confirm' : 'Vote Left'"
          :aria-pressed="confirmedVote === 0"
        >
          <div class="panel-bg panel-bg-left" aria-hidden="true"></div>
          <div class="panel-inner">
            <span class="direction-label">LEFT</span>
            <div
              class="panel-feedback"
              :class="{ visible: active === 0 && confirmedVote === null }"
              aria-live="polite"
            >TAP AGAIN TO CONFIRM</div>
          </div>
          <template v-if="confirmedVote === 0">
            <div class="vote-locked-wash wash-left" aria-hidden="true"></div>
            <div class="vote-locked-text" aria-hidden="true">✓ VOTE LOCKED</div>
          </template>
        </button>

        <!-- RIGHT -->
        <button
          class="vote-panel panel-right"
          :class="{
            'is-armed': active === 1 && confirmedVote === null,
            'is-voted': confirmedVote === 1,
            'is-dim':   confirmedVote !== null && confirmedVote !== 1,
          }"
          @click="handleClick(1)"
          :disabled="battlePhase !== 'VOTING'"
          :aria-label="confirmedVote === 1 ? 'Right — vote locked' : active === 1 ? 'Right — tap again to confirm' : 'Vote Right'"
          :aria-pressed="confirmedVote === 1"
        >
          <div class="panel-bg panel-bg-right" aria-hidden="true"></div>
          <div class="panel-inner">
            <span class="direction-label">RIGHT</span>
            <div
              class="panel-feedback"
              :class="{ visible: active === 1 && confirmedVote === null }"
              aria-live="polite"
            >TAP AGAIN TO CONFIRM</div>
          </div>
          <template v-if="confirmedVote === 1">
            <div class="vote-locked-wash wash-right" aria-hidden="true"></div>
            <div class="vote-locked-text" aria-hidden="true">✓ VOTE LOCKED</div>
          </template>
        </button>

      </div><!-- /lr-row -->

      <!-- TIE row -->
      <div class="tie-row">
        <button
          class="vote-panel panel-tie"
          :class="{
            'is-armed': active === -1 && confirmedVote === null,
            'is-voted': confirmedVote === -1,
            'is-dim':   confirmedVote !== null && confirmedVote !== -1,
          }"
          @click="handleClick(-1)"
          :disabled="battlePhase !== 'VOTING'"
          :aria-label="confirmedVote === -1 ? 'Tie — vote locked' : active === -1 ? 'Tie — tap again to confirm' : 'Vote Tie'"
          :aria-pressed="confirmedVote === -1"
        >
          <div class="panel-bg panel-bg-tie" aria-hidden="true"></div>
          <div class="panel-inner">
            <span class="direction-label direction-tie">TIE</span>
            <div
              class="panel-feedback"
              :class="{ visible: active === -1 && confirmedVote === null }"
              aria-live="polite"
            >TAP AGAIN TO CONFIRM</div>
          </div>
          <template v-if="confirmedVote === -1">
            <div class="vote-locked-wash wash-tie" aria-hidden="true"></div>
            <div class="vote-locked-text" aria-hidden="true">✓ VOTE LOCKED</div>
          </template>
        </button>
      </div><!-- /tie-row -->

    </div><!-- /panels-wrap -->

    <!-- ── Judge picker bottom sheet ───────────────────────────── -->
    <Transition name="sheet-slide">
      <div
        v-if="showJudgePicker"
        class="sheet-backdrop"
        role="dialog"
        aria-label="Select your name"
      >
        <div class="bottom-sheet">
          <div class="sheet-handle" aria-hidden="true"></div>
          <p class="sheet-title">WHO ARE YOU?</p>
          <div class="sheet-grid">
            <button
              v-for="j in (battleJudges?.judges ?? [])"
              :key="j.id"
              class="sheet-name-chip"
              @click="selectJudge(j)"
            >{{ j.name }}</button>
          </div>
        </div>
      </div>
    </Transition>

  </div>
</template>
```

- [ ] **Step 2: Verify template renders without errors in the browser**

Open `http://localhost:5173/battle/judge`. Expected: page loads, shows header with "BATTLE JUDGE" wordmark and a phase pill. No console errors.

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/BattleJudge.vue
git commit -m "feat(battle-judge): rebuild template — new layout with LR row, TIE row, bottom sheet, revealed banner"
```

---

## Task 4: Rebuild `<style scoped>`

**Files:**
- Modify: `BES-frontend/src/views/BattleJudge.vue` — replace entire `<style scoped>` block

- [ ] **Step 1: Replace the entire `<style scoped>` block**

Replace everything between `<style scoped>` and `</style>` with:

```css
/* ── Root ───────────────────────────────────────────────────── */
.judge-root {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background: #060a14;
  overflow: hidden;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  font-family: 'Inter', sans-serif;
  --left-color:  #dc2626;
  --right-color: #2563eb;
}

/* ── Header ─────────────────────────────────────────────────── */
.judge-header {
  flex-shrink: 0;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background: rgba(255,255,255,0.022);
  border-bottom: 1px solid rgba(255,255,255,0.065);
  backdrop-filter: blur(16px);
  z-index: 20;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.brand-pip {
  width: 6px; height: 6px;
  border-radius: 50%;
  animation: pipPulse 2.4s ease-in-out infinite;
}
.pip-left  { background: var(--left-color);  box-shadow: 0 0 8px var(--left-color); }
.pip-right { background: var(--right-color); box-shadow: 0 0 8px var(--right-color); animation-delay: 1.2s; }

.brand-wordmark {
  font-family: 'Anton SC', sans-serif;
  font-size: 11px;
  letter-spacing: 0.25em;
  color: rgba(255,255,255,0.28);
  text-transform: uppercase;
}

.header-center { flex: 1; display: flex; justify-content: center; }
.header-right  { flex-shrink: 0; }

/* Phase pill */
.phase-pill {
  font-family: 'Inter', sans-serif;
  font-size: 9px; font-weight: 800;
  letter-spacing: 0.2em; text-transform: uppercase;
  padding: 3px 10px; border-radius: 999px;
  border: 1px solid transparent;
}
.phase-pill--idle     { background: rgba(71,85,105,0.3);   color: rgba(148,163,184,0.6);  border-color: rgba(71,85,105,0.4); }
.phase-pill--locked   { background: rgba(245,158,11,0.15); color: rgba(251,191,36,0.9);   border-color: rgba(245,158,11,0.4); }
.phase-pill--voting   { background: rgba(6,182,212,0.15);  color: rgba(6,182,212,0.95);   border-color: rgba(6,182,212,0.4); animation: votingPillPulse 1.6s ease-in-out infinite; }
.phase-pill--revealed { background: rgba(16,185,129,0.15); color: rgba(52,211,153,0.95);  border-color: rgba(16,185,129,0.4); }

/* Judge chip */
.judge-chip {
  display: flex; align-items: center; gap: 5px;
  padding: 4px 10px;
  background: color-mix(in srgb, var(--left-color) 12%, rgba(6,10,20,0.8));
  border: 1px solid color-mix(in srgb, var(--left-color) 40%, transparent);
  border-radius: 999px;
}
.judge-chip-label { font-size: 8px; font-weight: 700; letter-spacing: 0.18em; color: rgba(255,255,255,0.35); }
.judge-chip-name  { font-size: 10px; font-weight: 800; letter-spacing: 0.1em; color: rgba(255,255,255,0.9); text-transform: uppercase; }
.judge-chip-clear {
  background: none; border: none; cursor: pointer;
  font-size: 10px; color: rgba(255,255,255,0.3);
  padding: 0 2px; line-height: 1; transition: color 0.15s;
}
.judge-chip-clear:hover { color: rgba(255,255,255,0.8); }

/* ── Names bar ──────────────────────────────────────────────── */
.names-bar {
  flex-shrink: 0;
  height: 10%;
  display: flex;
  min-height: 36px;
}
.name-cell {
  flex: 1; display: flex;
  align-items: center; justify-content: center;
  padding: 0 12px; overflow: hidden;
}
.name-cell-left  { background: color-mix(in srgb, var(--left-color)  12%, transparent); border-right: 1px solid rgba(255,255,255,0.04); }
.name-cell-right { background: color-mix(in srgb, var(--right-color) 12%, transparent); }
.name-cell-text {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(13px, 2.8vw, 22px);
  letter-spacing: 0.1em; text-transform: uppercase;
  text-overflow: ellipsis; overflow: hidden; white-space: nowrap;
}
.name-cell-left  .name-cell-text { color: color-mix(in srgb, var(--left-color)  90%, white); }
.name-cell-right .name-cell-text { color: color-mix(in srgb, var(--right-color) 90%, white); }

/* ── Panels wrap ────────────────────────────────────────────── */
.panels-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

/* Color bleeds */
.bleed {
  position: absolute; bottom: 0;
  width: 50%; height: 35%;
  pointer-events: none; z-index: 1;
}
.bleed-left  { left: 0;  background: radial-gradient(ellipse at 0% 100%, color-mix(in srgb, var(--left-color)  14%, transparent), transparent 70%); }
.bleed-right { right: 0; background: radial-gradient(ellipse at 100% 100%, color-mix(in srgb, var(--right-color) 14%, transparent), transparent 70%); }

/* LR row */
.lr-row {
  flex: 1;
  display: flex;
  gap: 2px;
}

/* TIE row */
.tie-row {
  flex-shrink: 0;
  height: 20%;
  min-height: 64px;
  padding-top: 2px;
}

/* ── Vote panel — shared ────────────────────────────────────── */
.vote-panel {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none; outline: none; cursor: pointer;
  overflow: hidden; background: transparent;
  transition: opacity 0.3s ease, filter 0.3s ease;
  -webkit-tap-highlight-color: transparent;
}
.lr-row   .vote-panel { flex: 1; }
.tie-row  .vote-panel { width: 100%; height: 100%; }

/* Panel backgrounds */
.panel-bg {
  position: absolute; inset: 0;
  transition: opacity 0.3s ease;
}
.panel-bg-left {
  background:
    radial-gradient(ellipse 80% 70% at 50% 62%, color-mix(in srgb, var(--left-color) 45%, transparent) 0%, transparent 75%),
    linear-gradient(180deg, color-mix(in srgb, var(--left-color) 8%, #060a14) 0%, color-mix(in srgb, var(--left-color) 18%, #060a14) 100%);
}
.panel-bg-right {
  background:
    radial-gradient(ellipse 80% 70% at 50% 62%, color-mix(in srgb, var(--right-color) 45%, transparent) 0%, transparent 75%),
    linear-gradient(180deg, color-mix(in srgb, var(--right-color) 8%, #060a14) 0%, color-mix(in srgb, var(--right-color) 18%, #060a14) 100%);
}
.panel-bg-tie {
  background:
    radial-gradient(ellipse 80% 60% at 50% 50%, rgba(71,85,105,0.35) 0%, transparent 75%),
    linear-gradient(180deg, #0a0e18 0%, #111827 100%);
}

/* Armed — brighter background */
.is-armed .panel-bg-left  { background: radial-gradient(ellipse 95% 85% at 50% 62%, color-mix(in srgb, var(--left-color)  65%, transparent) 0%, transparent 72%), linear-gradient(180deg, color-mix(in srgb, var(--left-color)  18%, #060a14) 0%, color-mix(in srgb, var(--left-color)  35%, #060a14) 100%); }
.is-armed .panel-bg-right { background: radial-gradient(ellipse 95% 85% at 50% 62%, color-mix(in srgb, var(--right-color) 65%, transparent) 0%, transparent 72%), linear-gradient(180deg, color-mix(in srgb, var(--right-color) 18%, #060a14) 0%, color-mix(in srgb, var(--right-color) 35%, #060a14) 100%); }
.is-armed .panel-bg-tie   { background: radial-gradient(ellipse 95% 85% at 50% 50%, rgba(100,116,139,0.55) 0%, transparent 72%), linear-gradient(180deg, #0f1624 0%, #1e2e48 100%); }
.is-armed { animation: armedPulse 1.4s ease-in-out infinite; }

/* Voted — max glow */
.is-voted .panel-bg-left  { background: radial-gradient(ellipse 95% 85% at 50% 62%, color-mix(in srgb, var(--left-color)  75%, transparent) 0%, transparent 72%), linear-gradient(180deg, color-mix(in srgb, var(--left-color)  22%, #060a14) 0%, color-mix(in srgb, var(--left-color)  42%, #060a14) 100%); }
.is-voted .panel-bg-right { background: radial-gradient(ellipse 95% 85% at 50% 62%, color-mix(in srgb, var(--right-color) 75%, transparent) 0%, transparent 72%), linear-gradient(180deg, color-mix(in srgb, var(--right-color) 22%, #060a14) 0%, color-mix(in srgb, var(--right-color) 42%, #060a14) 100%); }
.is-voted .panel-bg-tie   { background: radial-gradient(ellipse 95% 85% at 50% 50%, rgba(100,116,139,0.65) 0%, transparent 72%), linear-gradient(180deg, #0f1624 0%, #253649 100%); }

/* Dim — not chosen after vote */
.is-dim { opacity: 0.2; filter: saturate(0.15); pointer-events: none; }

/* ── Panel inner ────────────────────────────────────────────── */
.panel-inner {
  position: relative; z-index: 5;
  display: flex; flex-direction: column;
  align-items: center;
  gap: clamp(6px, 1.5vh, 18px);
}

.direction-label {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(28px, 5vw, 64px);
  letter-spacing: 0.2em; text-transform: uppercase;
  color: rgba(255,255,255,0.4);
  line-height: 1;
  transition: color 0.3s ease, letter-spacing 0.3s ease, text-shadow 0.3s ease;
}
.direction-tie { font-size: clamp(18px, 3.5vw, 42px); }

.is-armed .direction-label {
  color: rgba(255,255,255,0.92);
  letter-spacing: 0.26em;
}
.panel-left.is-armed  .direction-label { text-shadow: 0 0 32px color-mix(in srgb, var(--left-color)  70%, transparent); }
.panel-right.is-armed .direction-label { text-shadow: 0 0 32px color-mix(in srgb, var(--right-color) 70%, transparent); }
.panel-tie.is-armed   .direction-label { text-shadow: 0 0 32px rgba(148,163,184,0.6); }

.is-voted .direction-label { color: rgba(255,255,255,0.8); }

.panel-feedback {
  font-family: 'Inter', sans-serif;
  font-size: clamp(8px, 1.2vw, 12px);
  font-weight: 700; letter-spacing: 0.18em; text-transform: uppercase;
  color: rgba(255,255,255,0.85);
  opacity: 0; transform: translateY(6px);
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.panel-feedback.visible { opacity: 1; transform: translateY(0); }

/* ── Vote locked wash ───────────────────────────────────────── */
.vote-locked-wash {
  position: absolute; bottom: 0; left: 0; right: 0;
  height: 42%; z-index: 6; pointer-events: none;
}
.wash-left  { background: linear-gradient(to top, color-mix(in srgb, var(--left-color)  80%, black) 0%, color-mix(in srgb, var(--left-color)  40%, transparent) 65%, transparent 100%); }
.wash-right { background: linear-gradient(to top, color-mix(in srgb, var(--right-color) 80%, black) 0%, color-mix(in srgb, var(--right-color) 40%, transparent) 65%, transparent 100%); }
.wash-tie   { background: linear-gradient(to top, rgba(71,85,105,0.85) 0%, rgba(71,85,105,0.35) 65%, transparent 100%); }

.vote-locked-text {
  position: absolute; bottom: 9%; left: 0; right: 0;
  text-align: center;
  font-family: 'Inter', sans-serif;
  font-size: clamp(10px, 1.6vw, 15px);
  font-weight: 900; letter-spacing: 0.24em; text-transform: uppercase;
  color: white; z-index: 7; pointer-events: none;
}

/* ── Phase blocker ──────────────────────────────────────────── */
.panels-blocker {
  position: absolute; inset: 0; z-index: 15;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 12px;
  background: rgba(6,10,20,0.78);
  backdrop-filter: blur(4px);
  pointer-events: none;
}
.blocker-icon { font-size: 2.2rem; }
.blocker-text {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(14px, 2.5vw, 22px);
  letter-spacing: 0.22em; text-transform: uppercase;
  color: rgba(251,191,36,0.85);
}

/* ── REVEALED banner ────────────────────────────────────────── */
.revealed-banner {
  position: absolute; bottom: 0; left: 0; right: 0; z-index: 20;
  height: 52%;
  display: flex; flex-direction: column;
  align-items: center; justify-content: flex-end;
  padding-bottom: 8%;
  pointer-events: none;
}
.banner-left  { background: linear-gradient(to top, color-mix(in srgb, var(--left-color)  85%, black) 0%, color-mix(in srgb, var(--left-color)  30%, transparent) 72%, transparent 100%); }
.banner-right { background: linear-gradient(to top, color-mix(in srgb, var(--right-color) 85%, black) 0%, color-mix(in srgb, var(--right-color) 30%, transparent) 72%, transparent 100%); }
.banner-tie   { background: linear-gradient(to top, rgba(71,85,105,0.85) 0%, rgba(71,85,105,0.3) 72%, transparent 100%); }
.banner-winner-name {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(28px, 5.5vw, 58px);
  letter-spacing: 0.1em; text-transform: uppercase;
  color: white; line-height: 1;
}
.banner-wins {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(14px, 2.5vw, 30px);
  letter-spacing: 0.35em; text-transform: uppercase;
  color: rgba(52,211,153,0.9);
}

/* ── Bottom sheet ───────────────────────────────────────────── */
.sheet-backdrop {
  position: fixed; inset: 0; z-index: 50;
  background: rgba(6,10,20,0.65);
  backdrop-filter: blur(4px);
  display: flex; align-items: flex-end;
}
.bottom-sheet {
  width: 100%;
  background: #0d1220;
  border-top: 1px solid rgba(255,255,255,0.1);
  border-radius: 16px 16px 0 0;
  padding: 8px 16px 32px;
}
.sheet-handle {
  width: 32px; height: 4px;
  background: rgba(255,255,255,0.15);
  border-radius: 999px;
  margin: 0 auto 14px;
}
.sheet-title {
  font-family: 'Inter', sans-serif;
  font-size: 11px; font-weight: 800;
  letter-spacing: 0.25em; text-transform: uppercase;
  color: rgba(255,255,255,0.3);
  text-align: center; margin-bottom: 14px;
}
.sheet-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.sheet-name-chip {
  padding: 14px 8px;
  border-radius: 10px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  display: flex; align-items: center; justify-content: center;
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(15px, 3vw, 22px);
  letter-spacing: 0.12em; text-transform: uppercase;
  color: rgba(255,255,255,0.75);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, color 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.sheet-name-chip:active,
.sheet-name-chip:hover {
  background: color-mix(in srgb, var(--left-color) 16%, rgba(255,255,255,0.05));
  border-color: color-mix(in srgb, var(--left-color) 45%, transparent);
  color: rgba(255,255,255,0.95);
}

/* ── Transitions ────────────────────────────────────────────── */
.phase-fade-enter-active,
.phase-fade-leave-active { transition: opacity 0.3s ease; }
.phase-fade-enter-from,
.phase-fade-leave-to { opacity: 0; }

.sheet-slide-enter-active { transition: transform 0.32s cubic-bezier(0.34, 1.2, 0.64, 1); }
.sheet-slide-leave-active { transition: transform 0.22s cubic-bezier(0.4, 0, 1, 1); }
.sheet-slide-enter-from,
.sheet-slide-leave-to { transform: translateY(100%); }

.banner-slide-enter-active { transition: transform 0.45s cubic-bezier(0.2, 0, 0.3, 1), opacity 0.3s ease; }
.banner-slide-leave-active { transition: opacity 0.2s ease; }
.banner-slide-enter-from { transform: translateY(100%); opacity: 0; }
.banner-slide-leave-to { opacity: 0; }

/* ── Keyframes ──────────────────────────────────────────────── */
@keyframes pipPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%       { opacity: 0.3; transform: scale(0.6); }
}
@keyframes votingPillPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(6,182,212,0.4); }
  50%       { box-shadow: 0 0 0 4px rgba(6,182,212,0); }
}
@keyframes armedPulse {
  0%, 100% { box-shadow: inset 0 0 0 1.5px rgba(255,255,255,0); }
  50%       { box-shadow: inset 0 0 0 1.5px rgba(255,255,255,0.18); }
}

/* ── Tablet: center at max 480px ────────────────────────────── */
@media (min-width: 600px) {
  .judge-root {
    max-width: 480px;
    margin: 0 auto;
    border-left:  1px solid rgba(255,255,255,0.05);
    border-right: 1px solid rgba(255,255,255,0.05);
  }
}
```

- [ ] **Step 2: Check the page in a mobile viewport**

In Chrome DevTools, toggle device toolbar and set to iPhone 14 Pro (393×852). Verify:
- Header shows brand + phase pill + empty judge chip area
- LEFT and RIGHT fill the screen side-by-side
- TIE is clearly a full-width button at the bottom (~20% height)
- No scroll

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/views/BattleJudge.vue
git commit -m "feat(battle-judge): full CSS rebuild — dark overlay aesthetic, overlay-config colors, voted wash"
```

---

## Task 5: End-to-end verification & push

**Files:**
- No code changes — verification and git only

- [ ] **Step 1: Run the frontend dev server if not already running**

```bash
cd /Users/bennylim/Documents/BES/BES-frontend && npm run dev
```

- [ ] **Step 2: Verify judge identity flow**

1. Open `http://localhost:5173/battle/judge` in a fresh private window
2. Bottom sheet should slide up with judge name chips
3. Tap a name → sheet dismisses, judge chip appears in header ("AS [NAME]")
4. Hard refresh (`Cmd+Shift+R`) → sheet should NOT appear, judge chip should be restored
5. Tap ✕ on judge chip → sheet re-appears

- [ ] **Step 3: Verify vote persistence flow**

With the backend running and battle phase set to VOTING:
1. Tap LEFT once → "TAP AGAIN TO CONFIRM" appears
2. Tap LEFT again → gradient wash floods bottom, "✓ VOTE LOCKED" appears, RIGHT and TIE dim
3. Hard refresh → voted state restores (LEFT still glowing, others dimmed)

- [ ] **Step 4: Verify phase states**

Check each phase visually:
- **LOCKED**: panels visible but "🔒 VOTING NOT OPEN" overlay covers them
- **VOTING**: panels fully active, no overlay
- **REVEALED**: winner banner slides up from bottom
- **IDLE**: "⏳ WAITING…" overlay

- [ ] **Step 5: Verify colors follow overlay config**

In BattleControl, open Overlay Settings and change Left Color to `#7c3aed` (purple). BattleJudge should update the LEFT panel gradient within a second (via WS).

- [ ] **Step 6: Docker build**

```bash
cd /Users/bennylim/Documents/BES && docker-compose up --build --no-cache -d
```

Wait for frontend container to be healthy:
```bash
docker-compose ps
```

Open `https://<DOMAIN>/battle/judge` and repeat steps 2–5 against the production build.

- [ ] **Step 7: Force-push to remote**

```bash
git push origin feature/battle-judge-redesign --force-with-lease
```

---

## Self-Review Notes

**Spec coverage check:**
- ✅ Layout: LR side-by-side + TIE at bottom (Task 3/4)
- ✅ Visual language matching BattleOverlay (Task 4)
- ✅ Overlay config colors on mount + WS (Task 2)
- ✅ Judge identity: bottom sheet, localStorage, header chip, ✕ to change (Task 2/3/4)
- ✅ First load: sheet shown; on refresh: skip sheet (Task 2)
- ✅ Cross-check: judge removed from list → re-show sheet (Task 2)
- ✅ Tap-to-confirm: arm then confirm, different panel re-arms (Task 2)
- ✅ Haptic on confirm (Task 2)
- ✅ Vote persistence: write to localStorage + restore on mount (Task 2)
- ✅ Backend source of truth: WS vote subscription overrides localStorage (Task 2)
- ✅ Clear vote on LOCKED phase (Task 2)
- ✅ Phase states: IDLE/LOCKED/VOTING/REVEALED (Task 3/4)
- ✅ REVEALED banner with winner name + wins/tie (Task 3/4)
- ✅ Color bleeds in corners (Task 4)
- ✅ Responsive: clamp() font sizes, tablet max-width 480px (Task 4)
- ✅ WS subscription for vote set up only after judge identity resolved (Task 2)
- ✅ All 6 WS clients created separately (Task 2)
