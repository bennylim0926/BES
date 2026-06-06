<script setup>
import { battleJudgeVote, getBattleJudges, getBattlePhase, getBattleState, getCurrentBattlePair, getOverlayConfig } from '@/utils/api'
import { subscribeToChannel, createClient, deactivateClient } from '@/utils/websocket'
import { onMounted, onUnmounted, ref } from 'vue'
import { useAuthStore } from '@/utils/auth'
import { useRouter } from 'vue-router'

const router = useRouter()

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
const notAssigned     = ref(false)

const lastJudgeState = ref('')  // JSON diff guard

const hydrateJudgeFromState = (state) => {
  if (!state) return
  const snapshot = JSON.stringify(state)
  if (snapshot === lastJudgeState.value) return
  lastJudgeState.value = snapshot

  // Phase — only update if changed
  if (state.battlePhase && state.battlePhase !== battlePhase.value) {
    battlePhase.value = state.battlePhase
    if (state.battlePhase === 'LOCKED') {
      clearVote()
      revealedWinner.value = -2
    }
  }

  // Pair — only update if changed (prevents unnecessary clearVote)
  if (state.currentPair?.left) {
    if (leftName.value !== state.currentPair.left || rightName.value !== state.currentPair.right) {
      leftName.value = state.currentPair.left || ''
      rightName.value = state.currentPair.right || ''
      clearVote()
    }
  }

  // Restore revealedWinner on refresh during REVEALED (e.g. hard reload mid-reveal)
  if (state.battlePhase === 'REVEALED' && state.bracket?.rounds && state.currentPair?.left) {
    for (const matchList of Object.values(state.bracket.rounds)) {
      if (!Array.isArray(matchList)) continue
      const match = matchList.find(m =>
        Array.isArray(m) && m[0] === state.currentPair.left && m[1] === state.currentPair.right && m[2]
      )
      if (match) {
        revealedWinner.value = match[2] === state.currentPair.left ? 0 : 1
        break
      }
    }
  }

  // Judges — check if current judge was re-added after block
  if (state.judges?.length) {
    battleJudges.value = { judges: state.judges }
  }
}

function loadJudgeIdentity(judges) {
  const authStore = useAuthStore()
  const sid = authStore.judgeId
  if (!sid) { notAssigned.value = true; return }
  const match = judges.find(j => j.id === Number(sid))
  if (!match) { notAssigned.value = true; return }
  judgeId.value   = match.id
  judgeName.value = match.name
  setupVoteSubscription()
}

function clearJudge() {
  clearVote()
  judgeId.value   = null
  judgeName.value = ''
  notAssigned.value = true
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
  // Tapping the already-voted panel — no-op
  if (confirmedVote.value === side) return
  // Tapping a different panel while voted — revoke and arm the new panel
  if (confirmedVote.value !== null) {
    clearVote()
    active.value = side
    return
  }
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
let   voteClient      = null
let   clearJudgeTimer = null

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
  loadJudgeIdentity(battleJudges.value?.judges ?? [])

  // 5. Restore vote from backend judges list if available, fall back to localStorage
  if (battlePhase.value === 'VOTING' && judgeId.value != null) {
    const myJudge = (battleJudges.value?.judges ?? []).find(j => j.id === judgeId.value)
    if (myJudge && (myJudge.vote === 0 || myJudge.vote === 1 || myJudge.vote === -1)) {
      confirmedVote.value = myJudge.vote
    } else {
      const stored = restoreVoteFromStorage()
      if (stored !== null) confirmedVote.value = stored
    }
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
    const prev = battlePhase.value
    battlePhase.value = msg.phase
    if (msg.phase === 'LOCKED' && prev !== 'LOCKED') {
      clearVote()
      revealedWinner.value = -2
    }
  })

  subscribeToChannel(cPair, '/topic/battle/battle-pair', (msg) => {
    const newLeft  = msg.left  ?? ''
    const newRight = msg.right ?? ''
    if (newLeft !== leftName.value || newRight !== rightName.value) {
      leftName.value  = newLeft
      rightName.value = newRight
      clearVote()
    }
  })

  subscribeToChannel(cScore, '/topic/battle/score', (msg) => {
    revealedWinner.value = msg.message
  })

  subscribeToChannel(cJudges, '/topic/battle/judges', (msg) => {
    battleJudges.value = msg
    const authStore = useAuthStore()
    const judges = msg?.judges ?? []

    // If currently blocked (notAssigned), check if judge re-appeared
    if (notAssigned.value && authStore.judgeName) {
      const match = judges.find(j => j.name === authStore.judgeName)
      if (match) {
        judgeId.value = match.id
        judgeName.value = match.name
        notAssigned.value = false
        setupVoteSubscription()
        return
      }
    }

    if (judgeId.value != null) {
      const still = judges.find(j => j.id === judgeId.value)
      if (still) {
        clearTimeout(clearJudgeTimer)
        clearJudgeTimer = null
        notAssigned.value = false
      } else {
        // Judge not in list — check by name first (organiser may have re-created
        // the judge with a new ID during genre sync). If not found, clear immediately.
        const byName = authStore.judgeName
          ? judges.find(j => j.name === authStore.judgeName)
          : null
        if (byName) {
          judgeId.value = byName.id
          notAssigned.value = false
        } else {
          clearJudge()
        }
      }
    }
  })

  // Full-state snapshot for initial hydration and reconnect recovery
  const cState = createClient(); wsClients.push(cState)
  subscribeToChannel(cState, '/topic/battle/state', (msg) => {
    hydrateJudgeFromState(msg)
  })

  // Initial hydration + reconnect recovery
  const initialState = await getBattleState()
  if (initialState) hydrateJudgeFromState(initialState)
})

onUnmounted(() => {
  clearTimeout(clearJudgeTimer)
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

      <div class="header-right" style="display:flex;align-items:center;gap:8px;">
        <button
          v-if="judgeId"
          @click="router.push('/judge/session')"
          class="audition-nav-btn"
          title="Go to Session Hub"
        >HUB</button>
        <div v-if="judgeName" class="judge-chip">
          <span class="judge-chip-label">AS</span>
          <span class="judge-chip-name">{{ judgeName }}</span>
        </div>
      </div>
    </header>

    <!-- ── Panels wrap ─────────────────────────────────────────── -->
    <div class="panels-wrap" role="group" aria-label="Vote options">

      <!-- Color bleeds -->
      <div class="bleed bleed-left"  aria-hidden="true"></div>
      <div class="bleed bleed-right" aria-hidden="true"></div>

      <!-- Phase blocker (LOCKED / IDLE) -->
      <Transition name="phase-fade">
        <div
          v-if="battlePhase === 'LOCKED' || battlePhase === 'IDLE' || battlePhase === 'DECIDED'"
          class="panels-blocker"
          aria-live="polite"
        >
          <span class="blocker-icon">{{ battlePhase === 'DECIDED' ? '⭐' : battlePhase === 'LOCKED' ? '🔒' : '⏳' }}</span>
          <span class="blocker-text">{{ battlePhase === 'DECIDED' ? 'CHAMPION DECIDED' : battlePhase === 'LOCKED' ? 'WAITING FOR OPERATOR TO OPEN VOTING' : 'WAITING…' }}</span>
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
            <span class="direction-label">{{ leftName || 'LEFT' }}</span>
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
            <span class="direction-label">{{ rightName || 'RIGHT' }}</span>
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

      <!-- Not assigned overlay -->
      <Transition name="phase-fade">
        <div
          v-if="notAssigned"
          class="panels-blocker"
          aria-live="polite"
        >
          <span class="blocker-icon">🚫</span>
          <span class="blocker-text">NOT ASSIGNED TO THIS BATTLE</span>
        </div>
      </Transition>

    </div><!-- /panels-wrap -->

  </div>
</template>

<style scoped>
/* ── Root ───────────────────────────────────────────────────── */
.judge-root {
  position: fixed;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: #060a14;
  overflow: hidden;
  user-select: none;
  -webkit-tap-highlight-color: transparent;
  font-family: 'Inter', sans-serif;
  touch-action: manipulation;
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
  padding: 10px 8px;
  margin: -10px -8px;
  min-width: 44px; min-height: 44px;
  display: flex; align-items: center; justify-content: center;
  line-height: 1; transition: color 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.judge-chip-clear:hover { color: rgba(255,255,255,0.8); }

.pick-judge-btn {
  font-family: 'Inter', sans-serif;
  font-size: 9px; font-weight: 800;
  letter-spacing: 0.2em; text-transform: uppercase;
  padding: 8px 16px; border-radius: 999px;
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.2);
  color: rgba(255,255,255,0.7);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.pick-judge-btn:active { background: rgba(255,255,255,0.15); color: white; }

.audition-nav-btn {
  font-family: 'Inter', sans-serif;
  font-size: 9px; font-weight: 800;
  letter-spacing: 0.2em; text-transform: uppercase;
  padding: 5px 12px; border-radius: 999px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.5);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.audition-nav-btn:active { background: rgba(255,255,255,0.12); color: rgba(255,255,255,0.9); }

/* ── (names bar removed — names shown on panels) ───────────── */

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
.lr-row  .vote-panel { flex: 1; }
.tie-row .vote-panel { width: 100%; height: 100%; }

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

/* Dim — not chosen after vote (keep tappable for vote revocation) */
.is-dim { opacity: 0.2; filter: saturate(0.15); }

/* ── Panel inner ────────────────────────────────────────────── */
.panel-inner {
  position: relative; z-index: 5;
  display: flex; flex-direction: column;
  align-items: center;
  gap: clamp(6px, 1.5vh, 18px);
}

.direction-label {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(18px, 5.5vw, 44px);
  letter-spacing: 0.08em; text-transform: uppercase;
  color: rgba(255,255,255,0.4);
  line-height: 1.1;
  text-align: center;
  overflow-wrap: break-word;
  word-break: break-word;
  max-width: 90%;
  transition: color 0.3s ease, text-shadow 0.3s ease;
}
.direction-tie { font-size: clamp(16px, 4vw, 32px); }

.is-armed .direction-label {
  color: rgba(255,255,255,0.92);
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
}
.blocker-icon { font-size: 2.2rem; }
.blocker-text {
  font-family: 'Inter', sans-serif;
  font-size: clamp(14px, 2.5vw, 22px);
  font-weight: 800;
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

/* Tablet portrait — wider layout */
@media (min-width: 600px) {
  .judge-root {
    max-width: 640px;
    margin: 0 auto;
    border-left:  1px solid rgba(255,255,255,0.05);
    border-right: 1px solid rgba(255,255,255,0.05);
  }
}

/* Tablet landscape — shorter header, taller tie row for thumb reach */
@media (min-width: 600px) and (orientation: landscape) {
  .judge-header {
    height: 44px;
  }
  .tie-row {
    height: 26%;
    min-height: 72px;
  }
}
</style>
