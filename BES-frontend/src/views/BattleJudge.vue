<script setup>
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
  clearVote()
  judgeId.value   = null
  judgeName.value = ''
  localStorage.removeItem(LS_JUDGE_ID)
  localStorage.removeItem(LS_JUDGE_NAME)
  showJudgePicker.value = true
  if (voteClient) {
    deactivateClient(voteClient)
    const idx = wsClients.indexOf(voteClient)
    if (idx !== -1) wsClients.splice(idx, 1)
    voteClient = null
  }
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
  localStorage.setItem(key, JSON.stringify({ vote, leftName: leftName.value, rightName: rightName.value, timestamp: Date.now() }))
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
    const res = await battleJudgeVote(judgeId.value, side)
    if (!res?.ok) return   // POST failed — stay armed, let judge try again
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
  if (judgeId.value == null || voteClient) return
  voteClient = createClient()
  wsClients.push(voteClient)
  subscribeToChannel(voteClient, `/topic/battle/vote/${judgeId.value}`, (msg) => {
    if (msg.vote === 0 || msg.vote === 1 || msg.vote === -1) {
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
</script>

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
    <div class="names-bar">
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
          :aria-label="confirmedVote === 0 ? `${leftName || 'Left'} — vote locked` : active === 0 ? `${leftName || 'Left'} — tap again to confirm` : `Vote ${leftName || 'Left'}`"
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
          :aria-label="confirmedVote === 1 ? `${rightName || 'Right'} — vote locked` : active === 1 ? `${rightName || 'Right'} — tap again to confirm` : `Vote ${rightName || 'Right'}`"
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

<style scoped>
/* ── Root ─────────────────────────────────────────── */
.judge-root {
  display: flex;
  flex-direction: column;
  height: 100dvh;
  background: #060a14;
  overflow: hidden;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  font-family: 'Anton SC', sans-serif;
}

/* ── Header ───────────────────────────────────────── */
.judge-header {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 20px;
  background: rgba(255, 255, 255, 0.022);
  border-bottom: 1px solid rgba(255, 255, 255, 0.065);
  backdrop-filter: blur(16px);
  z-index: 20;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 10px;
}

.brand-pip {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
  animation: pipPulse 2.4s ease-in-out infinite;
}
.pip-red  { background: #ef4444; box-shadow: 0 0 10px rgba(239,68,68,0.8); }
.pip-blue { background: #3b82f6; box-shadow: 0 0 10px rgba(59,130,246,0.8); animation-delay: 1.2s; }

.brand-wordmark {
  font-family: 'Anton SC', sans-serif;
  font-size: 12px;
  letter-spacing: 0.25em;
  color: rgba(255, 255, 255, 0.3);
  text-transform: uppercase;
}

.selector-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.selector-eyebrow {
  font-family: 'Inter', sans-serif;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: rgba(255, 255, 255, 0.3);
  text-transform: uppercase;
  white-space: nowrap;
}

/* ── Phase blocker (IDLE — covers entire root) ────── */
.phase-blocker {
  position: absolute;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(6, 10, 20, 0.96);
  backdrop-filter: blur(6px);
}
.phase-blocker-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  text-align: center;
}
.phase-blocker-icon { font-size: 2.5rem; }
.phase-blocker-title {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(16px, 3vw, 24px);
  letter-spacing: 0.15em;
  color: rgba(255,255,255,0.5);
  text-transform: uppercase;
}

.idle-selector-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.idle-selector-eyebrow {
  font-family: 'Inter', sans-serif;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.2em;
  color: rgba(255, 255, 255, 0.3);
  text-transform: uppercase;
}

/* ── Header center (phase pill + pair names) ──────── */
.header-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}
.phase-pill {
  font-family: 'Inter', sans-serif;
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid transparent;
}
.phase-pill--idle    { background: rgba(71,85,105,0.3); color: rgba(148,163,184,0.6); border-color: rgba(71,85,105,0.4); }
.phase-pill--locked  { background: rgba(245,158,11,0.15); color: rgba(251,191,36,0.9); border-color: rgba(245,158,11,0.4); }
.phase-pill--voting  { background: rgba(6,182,212,0.15); color: rgba(6,182,212,0.95); border-color: rgba(6,182,212,0.4); animation: votingPulse 1.6s ease-in-out infinite; }
.phase-pill--revealed { background: rgba(16,185,129,0.15); color: rgba(52,211,153,0.95); border-color: rgba(16,185,129,0.4); }

.pair-label {
  font-family: 'Inter', sans-serif;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: rgba(255,255,255,0.7);
  text-overflow: ellipsis;
  overflow: hidden;
  white-space: nowrap;
  max-width: 240px;
}
.vs-sep {
  color: rgba(255,255,255,0.35);
  font-weight: 400;
  margin: 0 4px;
}

/* ── Panels container ─────────────────────────────── */
.panels-wrap {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
}

/* ── Phase overlays (inside panels-wrap) ──────────── */
.panels-overlay {
  position: absolute;
  inset: 0;
  z-index: 15;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}
.locked-overlay  { background: rgba(6, 10, 20, 0.72); backdrop-filter: blur(3px); }
.revealed-overlay { background: rgba(6, 10, 20, 0.82); backdrop-filter: blur(4px); }

.panels-overlay-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
  padding: 0 20px;
}
.overlay-lock-icon { font-size: 2rem; }
.overlay-title {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(14px, 2.5vw, 20px);
  letter-spacing: 0.18em;
  color: rgba(251,191,36,0.85);
}
.overlay-sub {
  font-family: 'Inter', sans-serif;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.1em;
  color: rgba(255,255,255,0.4);
}
.overlay-winner-name {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(24px, 5vw, 52px);
  letter-spacing: 0.1em;
  color: rgba(255,255,255,0.95);
  line-height: 1;
  text-transform: uppercase;
}
.overlay-winner-label {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(16px, 3.5vw, 36px);
  letter-spacing: 0.25em;
  color: rgba(52,211,153,0.9);
}

/* ── Phase transition ─────────────────────────────── */
.phase-fade-enter-active,
.phase-fade-leave-active { transition: opacity 0.35s ease; }
.phase-fade-enter-from,
.phase-fade-leave-to { opacity: 0; }

@keyframes votingPulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(6,182,212,0.4); }
  50% { box-shadow: 0 0 0 4px rgba(6,182,212,0); }
}

.vote-panel {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  outline: none;
  cursor: pointer;
  overflow: hidden;
  background: #060a14; /* solid dark base so transparent gradient parts don't show white */
  transition: flex 0.55s cubic-bezier(0.34, 1.56, 0.64, 1);
  -webkit-tap-highlight-color: transparent;
}
.tie-panel { flex: 0.62; }

/* Soft gradient divider between panels — fades naturally at top and bottom */
.vote-panel + .vote-panel::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10%;
  bottom: 10%;
  width: 1px;
  background: linear-gradient(to bottom, transparent, rgba(255,255,255,0.08) 30%, rgba(255,255,255,0.08) 70%, transparent);
  pointer-events: none;
  z-index: 20;
}

/* Expand active panel slightly */
.vote-panel.is-active { flex: 1.12; }
.tie-panel.is-active  { flex: 0.74; }

/* ── Panel background fill ────────────────────────── */
.panel-fill {
  position: absolute;
  inset: 0;
  transition: opacity 0.4s ease;
}

.left-fill {
  background:
    radial-gradient(ellipse 80% 70% at 50% 65%, rgba(220,38,38,0.55) 0%, rgba(127,17,17,0.35) 55%, transparent 80%),
    linear-gradient(180deg, #1a0505 0%, #2e0a0a 50%, #1a0505 100%);
}
.tie-fill {
  background:
    radial-gradient(ellipse 80% 70% at 50% 65%, rgba(71,85,105,0.6) 0%, rgba(30,41,59,0.4) 55%, transparent 80%),
    linear-gradient(180deg, #0a0e18 0%, #141e30 50%, #0a0e18 100%);
}
.right-fill {
  background:
    radial-gradient(ellipse 80% 70% at 50% 65%, rgba(37,99,235,0.55) 0%, rgba(17,40,140,0.35) 55%, transparent 80%),
    linear-gradient(180deg, #050916 0%, #0a1535 50%, #050916 100%);
}

/* Active backgrounds: brighter radial center */
.left-panel.is-active .left-fill {
  background:
    radial-gradient(ellipse 95% 85% at 50% 62%, rgba(239,68,68,0.72) 0%, rgba(185,28,28,0.5) 42%, transparent 72%),
    linear-gradient(180deg, #220808 0%, #420d0d 50%, #220808 100%);
}
.tie-panel.is-active .tie-fill {
  background:
    radial-gradient(ellipse 95% 85% at 50% 62%, rgba(100,116,139,0.7) 0%, rgba(51,65,85,0.5) 48%, transparent 72%),
    linear-gradient(180deg, #0f1624 0%, #1e2e48 50%, #0f1624 100%);
}
.right-panel.is-active .right-fill {
  background:
    radial-gradient(ellipse 95% 85% at 50% 62%, rgba(59,130,246,0.72) 0%, rgba(29,78,216,0.5) 42%, transparent 72%),
    linear-gradient(180deg, #060b22 0%, #0e1e50 50%, #060b22 100%);
}

/* Confirmed: brief bright flash */
.is-confirmed .left-fill  { animation: flashRed   0.45s ease-out; }
.is-confirmed .tie-fill   { animation: flashSlate 0.45s ease-out; }
.is-confirmed .right-fill { animation: flashBlue  0.45s ease-out; }

/* ── Edge accent line ─────────────────────────────── */
.edge-accent {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  opacity: 0;
  transition: opacity 0.4s ease;
  pointer-events: none;
}
.left-accent  { right: 0; background: linear-gradient(to bottom, transparent 0%, rgba(239,68,68,0.7) 30%, rgba(239,68,68,0.7) 70%, transparent 100%); }
.right-accent { left: 0;  background: linear-gradient(to bottom, transparent 0%, rgba(59,130,246,0.7) 30%, rgba(59,130,246,0.7) 70%, transparent 100%); }

.is-active .edge-accent { opacity: 1; }

/* ── Shimmer sweep ────────────────────────────────── */
.panel-shimmer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0;
  background: linear-gradient(
    110deg,
    transparent 25%,
    rgba(255,255,255,0.055) 50%,
    transparent 75%
  );
  background-size: 250% 100%;
  transition: opacity 0.3s ease;
}
.is-active .panel-shimmer {
  opacity: 1;
  animation: shimmer 2.2s linear infinite;
}

/* ── Panel inner content ──────────────────────────── */
.panel-inner {
  position: relative;
  z-index: 5;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: clamp(10px, 2.2vh, 26px);
}

/* Direction label */
.dir-label {
  margin: 0;
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(22px, 4vw, 52px);
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.45);
  line-height: 1;
  transition: color 0.3s ease, text-shadow 0.3s ease, letter-spacing 0.35s ease;
}
.is-active .dir-label {
  color: rgba(255,255,255,0.95);
  letter-spacing: 0.26em;
}
.left-panel.is-active .dir-label  { text-shadow: 0 0 40px rgba(239,68,68,0.65), 0 2px 0 rgba(0,0,0,0.5); }
.tie-panel.is-active .dir-label   { text-shadow: 0 0 40px rgba(148,163,184,0.55), 0 2px 0 rgba(0,0,0,0.5); }
.right-panel.is-active .dir-label { text-shadow: 0 0 40px rgba(59,130,246,0.65), 0 2px 0 rgba(0,0,0,0.5); }

/* Image frame */
.img-frame {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel-img {
  width: clamp(96px, 17vw, 200px);
  height: clamp(96px, 17vw, 200px);
  object-fit: contain;
  position: relative;
  z-index: 2;
  transition: transform 0.5s cubic-bezier(0.34, 1.56, 0.64, 1),
              filter 0.35s ease;
  filter: drop-shadow(0 16px 40px rgba(0,0,0,0.7));
}
.left-img  { transform: rotate(-45deg); }
.right-img { transform: rotate(45deg); }

.is-active .panel-img {
  filter: drop-shadow(0 0 28px rgba(255,255,255,0.28))
          drop-shadow(0 16px 40px rgba(0,0,0,0.6));
}
.left-panel.is-active .left-img  { transform: rotate(-45deg) scale(1.14); }
.right-panel.is-active .right-img { transform: rotate(45deg) scale(1.14); }
.tie-panel.is-active .panel-img   { transform: scale(1.12); }

/* Image halo glow */
.img-halo {
  position: absolute;
  width: clamp(80px, 14vw, 160px);
  height: clamp(80px, 14vw, 160px);
  border-radius: 50%;
  filter: blur(32px);
  opacity: 0;
  z-index: 1;
  transition: opacity 0.5s ease;
  pointer-events: none;
}
.red-halo     { background: rgba(239,68,68,0.7); }
.neutral-halo { background: rgba(100,116,139,0.6); }
.blue-halo    { background: rgba(59,130,246,0.7); }

.is-active .img-halo {
  opacity: 1;
  animation: haloBreath 1.9s ease-in-out infinite;
}

/* Feedback row */
.feedback-row {
  font-family: 'Inter', sans-serif;
  font-size: clamp(9px, 1.4vw, 13px);
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.88);
  text-align: center;
  padding: 0 10px;
  white-space: nowrap;
  opacity: 0;
  transform: translateY(7px);
  transition: opacity 0.25s ease, transform 0.25s ease;
}
.feedback-row.visible {
  opacity: 1;
  transform: translateY(0);
}
.check-mark {
  display: inline-block;
  font-size: 1.15em;
  margin-right: 2px;
  animation: checkPop 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

/* ── Pulse ring border ────────────────────────────── */
.pulse-ring {
  position: absolute;
  inset: 0;
  border: 2px solid transparent;
  pointer-events: none;
  z-index: 8;
  transition: border-color 0.3s ease;
}
.left-panel.is-active .left-ring    { border-color: rgba(239,68,68,0.55); animation: ringPulse 1.5s ease-in-out infinite; }
.tie-panel.is-active .neutral-ring  { border-color: rgba(148,163,184,0.45); animation: ringPulse 1.5s ease-in-out infinite; }
.right-panel.is-active .blue-ring   { border-color: rgba(59,130,246,0.55); animation: ringPulse 1.5s ease-in-out infinite; }

.left-panel.is-confirmed .left-ring    { border-color: rgba(239,68,68,0.9); animation: confirmBurst 0.55s ease-out forwards; }
.tie-panel.is-confirmed .neutral-ring  { border-color: rgba(148,163,184,0.9); animation: confirmBurst 0.55s ease-out forwards; }
.right-panel.is-confirmed .blue-ring   { border-color: rgba(59,130,246,0.9); animation: confirmBurst 0.55s ease-out forwards; }

/* ── Keyframes ────────────────────────────────────── */
@keyframes pipPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%       { opacity: 0.35; transform: scale(0.65); }
}

@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

@keyframes haloBreath {
  0%, 100% { transform: scale(1);    opacity: 0.85; }
  50%       { transform: scale(1.4); opacity: 0.38; }
}

@keyframes ringPulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.28; }
}

@keyframes confirmBurst {
  0%   { inset: 0;     opacity: 1; }
  60%  { inset: -6px;  opacity: 0.8; }
  100% { inset: -22px; opacity: 0; }
}

@keyframes checkPop {
  0%   { transform: scale(0); }
  70%  { transform: scale(1.3); }
  100% { transform: scale(1); }
}

@keyframes flashRed {
  0%   { filter: brightness(1); }
  25%  { filter: brightness(2.4) saturate(1.4); }
  100% { filter: brightness(1); }
}
@keyframes flashSlate {
  0%   { filter: brightness(1); }
  25%  { filter: brightness(2.2); }
  100% { filter: brightness(1); }
}
@keyframes flashBlue {
  0%   { filter: brightness(1); }
  25%  { filter: brightness(2.4) saturate(1.4); }
  100% { filter: brightness(1); }
}
</style>
