<script setup>
import { getBattleJudges, getCurrentBattlePair, getImage, getOverlayConfig } from '@/utils/api';
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket';
import { computed, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue';
import { useDelay } from '@/utils/utils';
import { useRoute } from 'vue-router'
import Chart from './Chart.vue';

const route = useRoute()

// ── Overlay config (live from BattleControl + API) ─────────────────────────
const overlayConfig = ref({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' })

// ── Battle state ───────────────────────────────────────────────────────────
const imageLeft  = ref(null)
const imageRight = ref(null)

const clients = []
let unmounted = false
const subscribedTopics = new Set()
const rightName  = ref('')
const leftName   = ref('')
const leftScore  = ref(0)
const rightScore = ref(0)
const currentWinner = ref(-2)
const battleJudges  = ref([])

// Champion reveal overlay
const championReveal = ref(null)   // null | { genreName, championName }

// Judge panel visibility: true = off-screen (idle/battle), false = visible (score revealed)
const hideJudgeDecision = ref(true)

// ── Voting state ───────────────────────────────────────────────────────────
// showVotingIndicator: true when /topic/battle/phase emits VOTING
const showVotingIndicator = ref(false)
// votesVisible: false until score is revealed — hides vote state on judge cards
const votesVisible = ref(false)
// battlePhase: tracks current phase to guard against stale score WS events
const battlePhase = ref('IDLE')

// ── Animation state (standard mode only) ──────────────────────────────────
// judgeAnim: '' | 'slide-down' | 'slide-up' (also used for smoke mode panel)
const judgeAnim = ref('')
// leftWin / rightWin: winner-expand CSS class trigger
const leftWin  = ref(false)
const rightWin = ref(false)
// leftReset / rightReset: loser/winner exit animation trigger
const leftReset  = ref(false)
const rightReset = ref(false)
// vsAnim: '' | 'rush-in' | 'knock-left' | 'knock-right'
const vsAnim = ref('')
// stageShaking: brief shake burst on VS landing (~340ms into entrance)
const stageShaking = ref(false)
// winnerTagVisible: WINNER stamp appears after winner is determined
const winnerTagVisible = ref(false)
// glitching: glitch overlay during next-pair transition
const glitching = ref(false)
// animToken: incremented whenever a new pair starts (LOCKED phase or updateBattlePair).
// updateScore captures the token at start and aborts if it changes, preventing
// stale leftWin/rightWin assignments from a previous pair's score animation.
let animToken = 0

const isSmoke = computed(() => route.query.isSmoke === 'true')

const judgePanelClass = computed(() => {
  if (isSmoke.value) return 'smoke-judge-always-on'
  return judgeAnim.value
})

// ── Entrance animation ─────────────────────────────────────────────────────
const runEntrance = async () => {
  await useDelay().wait(50) // allow DOM to clear previous animation classes
  if (unmounted) return
  hideJudgeDecision.value = true
  vsAnim.value = 'rush-in'
  await useDelay().wait(340) // VS hits undershoot trough at ~340ms
  if (unmounted) return
  stageShaking.value = true
  await useDelay().wait(120)
  if (unmounted) return
  stageShaking.value = false
}

// ── Battle pair update ─────────────────────────────────────────────────────
const updateBattlePair = async (msg) => {
  if (!msg) return

  // SMOKE MODE: only wipe judge votes, keep panel on screen
  if (isSmoke.value) {
    if (battleJudges.value?.judges) {
      battleJudges.value = {
        ...battleJudges.value,
        judges: battleJudges.value.judges.map(j => ({ ...j, vote: -3 }))
      }
    }
    return
  }

  // Increment token — aborts any in-progress updateScore for the previous pair
  animToken++

  // STANDARD MODE: if the judge panel is visible, clean it up before the new pair
  if (!hideJudgeDecision.value) {
    glitching.value = true
    judgeAnim.value = 'slide-up'
    await useDelay().wait(100)
    if (unmounted) return

    // Only slide a panel out if a winner was actually declared.
    // If currentWinner is -2 the score animation was aborted mid-way — skip the
    // panel exit so it doesn't look like a winner reveal on the wrong pair.
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

  // Reset all state before new pair
  leftWin.value          = false
  rightWin.value         = false
  currentWinner.value    = -2
  vsAnim.value           = ''
  showVotingIndicator.value = false
  votesVisible.value     = false
  winnerTagVisible.value = false

  leftName.value   = msg.left
  rightName.value  = msg.right
  leftScore.value  = msg.leftScore  ?? 0
  rightScore.value = msg.rightScore ?? 0
  imageLeft.value  = await getImage(`${msg.left}.png`)
  imageRight.value = await getImage(`${msg.right}.png`)

  await runEntrance()
}

// ── Judge list update ──────────────────────────────────────────────────────
const updateBattleJudge = (msg) => {
  battleJudges.value = msg
  battleJudges.value.judges.forEach(j => {
    const topic = `/topic/battle/vote/${j.id}`
    if (!subscribedTopics.has(topic)) {
      subscribedTopics.add(topic)
      const c = createClient()
      clients.push(c)
      subscribeToChannel(c, topic, (m) => updateJudgeVote(m))
    }
  })
}

// ── Judge vote update (internal only — hidden until score reveal) ──────────
const updateJudgeVote = (msg) => {
  const updatedJudges = battleJudges.value.judges.map(j =>
    j.id === msg.judge ? { ...j, vote: msg.vote } : j
  )
  battleJudges.value = { ...battleJudges.value, judges: updatedJudges }
}

watch(battleJudges, (newVal) => {
  if (!newVal?.judges) return
  newVal.judges.forEach(j => {
    const topic = `/topic/battle/vote/${j.id}`
    if (!subscribedTopics.has(topic)) {
      subscribedTopics.add(topic)
      const c = createClient()
      clients.push(c)
      subscribeToChannel(c, topic, (m) => updateJudgeVote(m))
    }
  })
}, { deep: true })

// ── Score reveal ───────────────────────────────────────────────────────────
const updateScore = async (msg) => {
  // Guard: stale event if phase already reset
  if (battlePhase.value === 'LOCKED') return
  // Capture token — if a new pair starts (LOCKED handler or updateBattlePair) before
  // we finish, animToken increments and all subsequent checks abort this animation.
  const myToken = animToken
  const ok = () => !unmounted && animToken === myToken

  showVotingIndicator.value = false
  rightScore.value = msg.right
  leftScore.value  = msg.left

  // Phase 1: judge panel slams to vertical center of screen
  hideJudgeDecision.value = false
  judgeAnim.value = 'judge-slam-center'

  // Shake on slam landing (~350ms into animation)
  await useDelay().wait(350)
  if (!ok()) return
  stageShaking.value = true
  await useDelay().wait(80)
  if (!ok()) return
  stageShaking.value = false

  // Reveal votes as slam settles
  votesVisible.value = true

  // Audience reads the votes
  await useDelay().wait(1500)
  if (!ok()) return

  // Phase 2: panel scales down and retreats to top
  judgeAnim.value = 'judge-retreat-top'
  await useDelay().wait(550)
  if (!ok()) return
  judgeAnim.value = 'judge-at-top'

  // Brief pause before winner reveal
  await useDelay().wait(150)
  if (!ok()) return

  currentWinner.value = msg.message

  if (msg.message === 0) {
    // Left wins: VS rockets right, left panel expands
    leftWin.value          = true
    winnerTagVisible.value = true
    vsAnim.value           = 'knock-right'
    await useDelay().wait(100)
    if (!ok()) return
    rightReset.value = true
  } else if (msg.message === 1) {
    // Right wins: VS rockets left, right panel expands
    rightWin.value         = true
    winnerTagVisible.value = true
    vsAnim.value           = 'knock-left'
    await useDelay().wait(100)
    if (!ok()) return
    leftReset.value = true
  }
  // msg.message === -1: tie — panels stay
}

// ── Mount ──────────────────────────────────────────────────────────────────
onMounted(async () => {
  document.documentElement.classList.add('transparent-page')
  document.body.classList.add('transparent-page')

  // Fetch initial overlay config (survives OBS refresh)
  const config = await getOverlayConfig()
  if (config?.showImages !== undefined) overlayConfig.value = config

  // Live overlay config updates from BattleControl
  const cOverlay = createClient()
  clients.push(cOverlay)
  subscribeToChannel(cOverlay, '/topic/battle/overlay-config', (msg) => {
    if (msg?.showImages !== undefined) overlayConfig.value = msg
  })

  // Phase subscription: track phase for stale-event guard; VOTING shows indicator
  const cPhase = createClient()
  clients.push(cPhase)
  subscribeToChannel(cPhase, '/topic/battle/phase', (msg) => {
    if (!msg?.phase) return
    battlePhase.value = msg.phase
    showVotingIndicator.value = msg.phase === 'VOTING'
    if (msg.phase === 'LOCKED') {
      // Next was clicked — abort any in-progress score animation and reset overlay
      animToken++
      hideJudgeDecision.value = true
      judgeAnim.value         = ''
      votesVisible.value      = false
      winnerTagVisible.value  = false
      leftWin.value           = false
      rightWin.value          = false
      leftReset.value         = false
      rightReset.value        = false
    }
  })

  subscribeToChannel(cPhase, '/topic/battle/champion-reveal', (data) => {
    if (data.dismiss) {
      championReveal.value = null
    } else {
      championReveal.value = { genreName: data.genreName, championName: data.championName }
    }
  })

  if (!isSmoke.value) {
    battleJudges.value = await getBattleJudges()
    const res = await getCurrentBattlePair()
    if (res) await updateBattlePair(res)
    const cPair2   = createClient(); clients.push(cPair2)
    const cScore2  = createClient(); clients.push(cScore2)
    const cJudges2 = createClient(); clients.push(cJudges2)
    subscribeToChannel(cPair2,   '/topic/battle/battle-pair', (msg) => updateBattlePair(msg))
    subscribeToChannel(cScore2,  '/topic/battle/score',       (msg) => updateScore(msg))
    subscribeToChannel(cJudges2, '/topic/battle/judges',      (msg) => updateBattleJudge(msg))
  }
})

onBeforeUnmount(() => {
  unmounted = true
  clients.forEach(c => { if (c?.value) deactivateClient(c.value) })
})

onUnmounted(() => {
  document.documentElement.classList.remove('transparent-page')
  document.body.classList.remove('transparent-page')
  const appRoot = document.getElementById('app')
  if (appRoot) appRoot.style.background = ''
})
</script>

<template>
  <div
    class="overlay-root"
    :class="{ 'stage-shake': stageShaking }"
    :style="{ '--left-color': overlayConfig.leftColor, '--right-color': overlayConfig.rightColor }"
  >
    <!-- ── Champion Reveal Overlay ─────────────────── -->
    <Transition name="champ-reveal">
      <div v-if="championReveal" class="champ-overlay">
        <div class="champ-overlay-bg"></div>
        <div class="champ-overlay-content">
          <div class="champ-genre-tag">{{ championReveal.genreName }} · Final</div>
          <div class="champ-label">CHAMPION</div>
          <div class="champ-name-slam">{{ championReveal.championName }}</div>
          <div class="champ-gold-bar"></div>
        </div>
      </div>
    </Transition>

    <!-- Screen-reader live region -->
    <div class="sr-only" role="status" aria-live="assertive" aria-atomic="true">
      <template v-if="currentWinner === 0">{{ leftName }} wins!</template>
      <template v-else-if="currentWinner === 1">{{ rightName }} wins!</template>
      <template v-else-if="currentWinner === -1">Tie between {{ leftName }} and {{ rightName }}!</template>
    </div>

    <!-- Glitch transition overlay -->
    <div v-if="glitching" class="glitch-overlay" aria-hidden="true"></div>

    <!-- Structural decorators — standard mode only -->
    <template v-if="!isSmoke">
      <div class="center-divider" aria-hidden="true"></div>
      <div class="scanlines"      aria-hidden="true"></div>
      <div class="color-bleed color-bleed-left"  aria-hidden="true"></div>
      <div class="color-bleed color-bleed-right" aria-hidden="true"></div>
    </template>

    <!-- ══════════════════════════════════════════════════
         JUDGE PANEL — shared across both modes
    ═══════════════════════════════════════════════════ -->
    <div
      v-if="battleJudges?.judges?.length && !isSmoke"
      class="judge-panel"
      :class="judgePanelClass"
      role="region"
      aria-label="Judges"
      aria-live="polite"
      aria-atomic="false"
    >
      <div class="judge-border-glow" aria-hidden="true"></div>
      <div class="judge-inner">
        <div class="judges-header" aria-hidden="true">
          <span class="judges-line"></span>
          <span class="judges-label">JUDGES</span>
          <span class="judges-line"></span>
        </div>
        <div class="judge-cards-row" role="list">
          <div
            v-for="(j, index) in battleJudges.judges"
            :key="index"
            class="judge-card"
            role="listitem"
            :aria-label="`${j.name}: ${!votesVisible ? 'awaiting vote' : j.vote === 0 ? 'voted left' : j.vote === 1 ? 'voted right' : j.vote === -1 ? 'voted tie' : 'awaiting vote'}`"
            :class="{
              'voted-left':   votesVisible && j.vote === 0,
              'voted-right':  votesVisible && j.vote === 1,
              'voted-tie':    votesVisible && j.vote === -1,
              'card-unvoted': !votesVisible,
              'card-burst':    votesVisible,
            }"
            :style="votesVisible ? { animationDelay: `${index * 55}ms` } : {}"
          >
            <div class="judge-row">
              <span
                class="vote-arrow vote-arrow-left"
                :class="{ 'arrow-lit-left': votesVisible && j.vote === 0 }"
                aria-hidden="true"
              ></span>
              <span class="judge-name" :class="{ 'judge-name-tie': votesVisible && j.vote === -1 }">{{ j.name }}</span>
              <span
                class="vote-arrow vote-arrow-right"
                :class="{ 'arrow-lit-right': votesVisible && j.vote === 1 }"
                aria-hidden="true"
              ></span>
            </div>
            <div v-if="!(votesVisible && j.vote === -1)" class="vote-track" aria-hidden="true">
              <div
                class="vote-fill"
                :class="{
                  'fill-left':  votesVisible && j.vote === 0,
                  'fill-right': votesVisible && j.vote === 1,
                  'fill-blank': !votesVisible || j.vote === -3 || j.vote === null,
                }"
              ></div>
            </div>
            <div v-if="votesVisible && j.vote === -1" class="tie-badge" aria-hidden="true">TIE</div>
          </div>
        </div>
      </div>
    </div>

    <!-- ══════════════════════════════════════════════════
         STANDARD BATTLE  (isSmoke = false)
    ═══════════════════════════════════════════════════ -->
    <template v-if="!isSmoke">

      <!-- Left battler panel -->
      <div
        class="battler-panel left-panel"
        :class="{
          'slam-in-left':   hideJudgeDecision,
          'slide-left-out': leftReset,
          'panel-winner':   leftWin,
        }"
        role="region"
        :aria-label="`Left: ${leftName || 'TBD'}${leftScore > 0 ? ', score ' + leftScore : ''}${leftWin ? ' — winner' : ''}`"
      >
        <div class="panel-color-wash panel-color-wash-left" aria-hidden="true"></div>

        <!-- Picture mode: fixed-width group shifts to center on panel-winner expand -->
        <template v-if="overlayConfig.showImages">
          <div class="img-group">
            <img v-if="imageLeft" :src="imageLeft" :alt="leftName" class="battler-img" />
            <div v-else class="battler-placeholder" aria-hidden="true"></div>
            <div class="name-overlay">
              <span v-if="winnerTagVisible && leftWin" class="winner-label winner-label-left" aria-hidden="true">WINNER</span>
              <span class="name-text name-text-left">{{ leftName || '???' }}</span>
            </div>
          </div>
        </template>

        <!-- Name-only mode -->
        <template v-else>
          <div class="name-center-wrap">
            <span v-if="winnerTagVisible && leftWin" class="winner-label winner-label-left" aria-hidden="true">WINNER</span>
            <span class="name-giant name-giant-left">{{ leftName || '???' }}</span>
          </div>
        </template>

        <div class="corner-accent corner-accent-tl" aria-hidden="true"></div>
        <div class="bottom-edge bottom-edge-left"   aria-hidden="true"></div>
      </div>

      <!-- VS badge -->
      <div
        class="vs-badge"
        :class="[vsAnim, { 'vs-gone': currentWinner !== -2 && vsAnim !== 'knock-left' && vsAnim !== 'knock-right' }]"
        aria-hidden="true"
      >
        <span class="vs-text">VS</span>
      </div>

      <!-- Right battler panel -->
      <div
        class="battler-panel right-panel"
        :class="{
          'slam-in-right':   hideJudgeDecision,
          'slide-right-out': rightReset,
          'panel-winner':    rightWin,
        }"
        role="region"
        :aria-label="`Right: ${rightName || 'TBD'}${rightScore > 0 ? ', score ' + rightScore : ''}${rightWin ? ' — winner' : ''}`"
      >
        <div class="panel-color-wash panel-color-wash-right" aria-hidden="true"></div>

        <!-- Picture mode: fixed-width group shifts to center on panel-winner expand -->
        <template v-if="overlayConfig.showImages">
          <div class="img-group">
            <img v-if="imageRight" :src="imageRight" :alt="rightName" class="battler-img" />
            <div v-else class="battler-placeholder" aria-hidden="true"></div>
            <div class="name-overlay name-overlay-right">
              <span v-if="winnerTagVisible && rightWin" class="winner-label winner-label-right" aria-hidden="true">WINNER</span>
              <span class="name-text name-text-right">{{ rightName || '???' }}</span>
            </div>
          </div>
        </template>

        <!-- Name-only mode -->
        <template v-else>
          <div class="name-center-wrap">
            <span v-if="winnerTagVisible && rightWin" class="winner-label winner-label-right" aria-hidden="true">WINNER</span>
            <span class="name-giant name-giant-right">{{ rightName || '???' }}</span>
          </div>
        </template>

        <div class="corner-accent corner-accent-tr" aria-hidden="true"></div>
        <div class="bottom-edge bottom-edge-right"  aria-hidden="true"></div>
      </div>

      <!-- Voting indicator — visible during VOTING phase -->
      <transition name="fade-indicator">
        <div v-if="showVotingIndicator" class="voting-indicator" aria-label="Judges are voting">
          <span class="voting-dot" aria-hidden="true"></span>
          <span class="voting-label">JUDGES VOTING</span>
        </div>
      </transition>

    </template>

    <!-- ══════════════════════════════════════════════════
         SMOKE MODE  (isSmoke = true)
    ═══════════════════════════════════════════════════ -->
    <template v-else>
      <Chart />
    </template>

  </div>
</template>

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

<style scoped>
/* ── Screen-reader only ─────────────────────────────────────── */
.sr-only {
  position: absolute; width: 1px; height: 1px; padding: 0;
  margin: -1px; overflow: hidden; clip: rect(0,0,0,0);
  white-space: nowrap; border: 0;
}

/* ── Root ───────────────────────────────────────────────────── */
.overlay-root {
  position: fixed;
  top: 0; left: 0;
  width: 100vw; height: 100vh;
  overflow: hidden;
  font-family: 'Anton SC', sans-serif;
  /* Default CSS custom properties — overridden by :style binding */
  --left-color: #dc2626;
  --right-color: #2563eb;
}

/* ── Stage shake ────────────────────────────────────────────── */
.stage-shake {
  animation: stageShake 120ms cubic-bezier(0.2, 0, 0.3, 1) both;
}

/* ── Glitch overlay ─────────────────────────────────────────── */
.glitch-overlay {
  position: absolute; inset: 0;
  z-index: 100;
  pointer-events: none;
  background: repeating-linear-gradient(
    0deg,
    transparent, transparent 4px,
    rgba(255,255,255,0.04) 4px, rgba(255,255,255,0.04) 8px
  );
  animation: glitchFlicker 380ms steps(1) forwards;
}

/* ── Center divider ─────────────────────────────────────────── */
.center-divider {
  position: absolute;
  top: 0; bottom: 0;
  left: calc(50% - 1px);
  width: 2px;
  background: linear-gradient(
    to bottom,
    transparent 0%,
    rgba(255,255,255,0.12) 30%,
    rgba(255,255,255,0.22) 50%,
    rgba(255,255,255,0.12) 70%,
    transparent 100%
  );
  transform: skewX(-3deg);
  z-index: 5;
  pointer-events: none;
}

/* ── Scanlines ──────────────────────────────────────────────── */
.scanlines {
  position: absolute; inset: 0;
  z-index: 2;
  pointer-events: none;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0px, transparent 3px,
    rgba(0,0,0,0.035) 3px, rgba(0,0,0,0.035) 4px
  );
}

/* ── Global color bleeds ────────────────────────────────────── */
.color-bleed {
  position: absolute; inset: 0;
  pointer-events: none; z-index: 1;
}
.color-bleed-left {
  background: radial-gradient(
    ellipse 45% 55% at 0% 100%,
    color-mix(in srgb, var(--left-color) 16%, transparent),
    transparent 70%
  );
}
.color-bleed-right {
  background: radial-gradient(
    ellipse 45% 55% at 100% 100%,
    color-mix(in srgb, var(--right-color) 16%, transparent),
    transparent 70%
  );
}

/* ══════════════════════════════════════════════════
   JUDGE PANEL
══════════════════════════════════════════════════ */
.judge-panel {
  position: absolute;
  top: 0; left: 0; right: 0;
  z-index: 50;
  display: flex;
  justify-content: center;
  align-items: flex-start;
  pointer-events: none;
  transform: translateY(-100%);
}
.smoke-judge-always-on {
  transform: translateY(0) !important;
  animation: none !important;
}
/* no .judge-border-glow — removed the rotating glow card look */
.judge-border-glow { display: none; }
.judge-inner {
  position: relative;
  display: flex; flex-direction: column; gap: 8px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--left-color) 12%, rgba(6,8,18,0.94)),
    color-mix(in srgb, var(--right-color) 12%, rgba(6,8,18,0.94))
  );
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 24px 80px rgba(0,0,0,0.75), 0 0 0 1px rgba(255,255,255,0.04) inset;
  padding: 12px 24px 16px;
}
.judges-header {
  display: flex; align-items: center; gap: 8px;
}
.judges-label {
  font-family: 'Inter', sans-serif;
  font-size: 9px; font-weight: 700;
  letter-spacing: 0.32em;
  color: rgba(255,255,255,0.28);
  text-transform: uppercase;
  white-space: nowrap; flex-shrink: 0;
}
.judges-line {
  flex: 1; height: 1px;
  background: rgba(255,255,255,0.07);
}
.judge-cards-row {
  display: flex; gap: 8px;
}

/* Judge card — sharp parallelogram */
.judge-card {
  display: flex; flex-direction: column;
  align-items: center; gap: 7px;
  min-width: 130px;
  padding: 8px 12px;
  clip-path: polygon(5px 0%, 100% 0%, calc(100% - 5px) 100%, 0% 100%);
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.06);
  transition: background 0.25s ease, box-shadow 0.25s ease;
}

/* Unvoted: dim pulse */
.card-unvoted {
  background: rgba(255,255,255,0.06) !important;
  animation: cardPulse 2.2s ease-in-out infinite;
}

/* Burst in when votes are revealed */
.card-burst {
  animation: cardBurst 280ms cubic-bezier(0.2, 0, 0.3, 1) both;
}

.voted-left {
  background: color-mix(in srgb, var(--left-color) 12%, transparent);
  border-color: color-mix(in srgb, var(--left-color) 40%, transparent);
  box-shadow: 0 0 20px color-mix(in srgb, var(--left-color) 25%, transparent);
}
.voted-right {
  background: color-mix(in srgb, var(--right-color) 12%, transparent);
  border-color: color-mix(in srgb, var(--right-color) 40%, transparent);
  box-shadow: 0 0 20px color-mix(in srgb, var(--right-color) 25%, transparent);
}
.voted-tie {
  background: rgba(255,255,255,0.06);
  border-color: rgba(255,255,255,0.20);
  box-shadow: none;
}

/* Judge name + arrow row */
.judge-row { display: flex; align-items: center; gap: 9px; }
.judge-name {
  font-family: 'Anton SC', sans-serif;
  font-size: 22px;
  color: rgba(255,255,255,0.90);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  line-height: 1;
}

/* Direction arrows */
.vote-arrow {
  display: inline-block;
  width: 18px; height: 18px; flex-shrink: 0;
  background: rgba(255,255,255,0.15);
  opacity: 0.2;
  transition: opacity 0.2s ease, background 0.2s ease, filter 0.2s ease;
}
.vote-arrow-left  { clip-path: polygon(100% 0%, 0% 50%, 100% 100%); }
.vote-arrow-right { clip-path: polygon(0% 0%, 100% 50%, 0% 100%); }
.arrow-lit-left {
  opacity: 1;
  background: var(--left-color);
  filter: drop-shadow(0 0 8px var(--left-color));
}
.arrow-lit-right {
  opacity: 1;
  background: var(--right-color);
  filter: drop-shadow(0 0 8px var(--right-color));
}

/* Tie state */
.judge-name-tie { color: rgba(255,255,255,0.32) !important; }
.tie-badge {
  font-family: 'Anton SC', sans-serif;
  font-size: 13px; letter-spacing: 0.35em;
  color: rgba(255,255,255,0.55);
  text-align: center; width: 100%;
  border: 1px solid rgba(255,255,255,0.18);
  border-radius: 4px; padding: 2px 0;
  animation: cardBurst 280ms cubic-bezier(0.2, 0, 0.3, 1) both;
}

/* Vote track bar */
.vote-track {
  width: 100%; height: 10px;
  background: rgba(255,255,255,0.1);
  border-radius: 9999px;
  overflow: hidden;
}
.vote-fill {
  height: 100%;
  border-radius: 9999px;
  transition: background 0.25s ease, width 0.25s ease;
  width: 0%;
}
.fill-left  {
  width: 100%;
  background: linear-gradient(90deg, color-mix(in srgb, var(--left-color) 70%, black), var(--left-color));
  box-shadow: 0 0 14px color-mix(in srgb, var(--left-color) 90%, transparent);
}
.fill-right {
  width: 100%;
  background: linear-gradient(90deg, color-mix(in srgb, var(--right-color) 70%, black), var(--right-color));
  box-shadow: 0 0 14px color-mix(in srgb, var(--right-color) 90%, transparent);
}
.fill-blank { width: 0%; background: transparent; }

/* ══════════════════════════════════════════════════
   BATTLER PANELS
   Both panels use left-only positioning so winner
   centering (left:0, width:100%) works from either side.
══════════════════════════════════════════════════ */
.battler-panel {
  position: absolute;
  bottom: 0;
  height: 100vh;
  display: flex; flex-direction: column;
  align-items: center; justify-content: flex-end;
  /* CSS transition for winner expand */
  transition: left 420ms cubic-bezier(0.2, 0, 0.3, 1),
              width 420ms cubic-bezier(0.2, 0, 0.3, 1);
}
.left-panel  { left: 0;    width: 46%; }
.right-panel { left: 54%;  width: 46%; }

/* Winner: both panels expand to full width from left:0 */
.panel-winner { left: 0 !important; width: 100% !important; }

/* Panel color wash (gradient overlay) */
.panel-color-wash {
  position: absolute; inset: 0;
  pointer-events: none; z-index: 0;
}
.panel-color-wash-left {
  background: linear-gradient(
    to top,
    color-mix(in srgb, var(--left-color) 55%, black) 0%,
    color-mix(in srgb, var(--left-color) 15%, transparent) 40%,
    transparent 70%
  );
}
.panel-color-wash-right {
  background: linear-gradient(
    to top,
    color-mix(in srgb, var(--right-color) 55%, black) 0%,
    color-mix(in srgb, var(--right-color) 15%, transparent) 40%,
    transparent 70%
  );
}

/* Corner accent bars (3px vertical, fade down in team color) */
.corner-accent {
  position: absolute; top: 0;
  width: 3px; height: 28%;
  pointer-events: none; z-index: 3;
}
.corner-accent-tl { left: 0;  background: linear-gradient(to bottom, var(--left-color), transparent); }
.corner-accent-tr { right: 0; background: linear-gradient(to bottom, var(--right-color), transparent); }

/* Bottom edge lines (3px horizontal, fade inward) */
.bottom-edge {
  position: absolute; bottom: 0;
  height: 3px; width: 40%;
  pointer-events: none; z-index: 3;
}
.bottom-edge-left  { left: 0;  background: linear-gradient(to right, var(--left-color), transparent); }
.bottom-edge-right { right: 0; background: linear-gradient(to left, var(--right-color), transparent); }

/* ── Picture mode: img-group — fixed width so image doesn't stretch on panel expand ── */
.img-group {
  position: relative;
  width: 46vw;       /* matches panel's normal 46% of 100vw */
  flex-shrink: 0;
}

/* ── Picture mode ────────────────────────────────── */
.battler-img {
  position: relative; z-index: 10;
  width: 100%;
  aspect-ratio: 3/4;
  object-fit: cover;
  object-position: top;
  max-height: 85vh;
  display: block;
}
.battler-placeholder {
  position: relative; z-index: 10;
  width: 12vw; height: 30vh;
  margin-bottom: 8vh;
  border-radius: 8px;
  border: 1.5px dashed rgba(255,255,255,0.18);
  background: rgba(255,255,255,0.03);
  animation: placeholderBreath 3s ease-in-out infinite;
}
.name-overlay {
  position: absolute;
  bottom: 0; left: 0; right: 0;
  z-index: 20;
  padding: 10vh 3vw 5vh;
  background: none;
  display: flex; flex-direction: column; align-items: flex-start; gap: 6px;
}
.name-overlay-right { align-items: flex-end; }
.name-text {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(22px, 4.5vw, 72px);
  text-transform: uppercase;
  color: #fff;
  line-height: 1; letter-spacing: 0.07em;
}
.name-text-left {
  text-shadow: 3px 3px 0 var(--left-color),
               0 0 30px color-mix(in srgb, var(--left-color) 60%, transparent);
}
.name-text-right {
  text-shadow: -3px 3px 0 var(--right-color),
               0 0 30px color-mix(in srgb, var(--right-color) 60%, transparent);
}

/* ── Name-only mode ──────────────────────────────── */
.name-center-wrap {
  position: absolute;
  top: 50%; transform: translateY(-50%);
  z-index: 20;
  width: 90%;
  display: flex; flex-direction: column;
  align-items: center; gap: 14px;
  padding: 0 8px;
}
.name-giant {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(28px, 5.5vw, 90px);
  text-transform: uppercase;
  color: #fff;
  line-height: 1; letter-spacing: 0.06em;
  word-break: break-word; text-align: center;
}
.name-giant-left {
  text-shadow: 4px 4px 0 var(--left-color),
               0 0 50px color-mix(in srgb, var(--left-color) 50%, transparent);
}
.name-giant-right {
  text-shadow: -4px 4px 0 var(--right-color),
               0 0 50px color-mix(in srgb, var(--right-color) 50%, transparent);
}

/* Score badges */
.score-badge {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(12px, 1.6vw, 28px);
  line-height: 1;
  color: rgba(255,255,255,0.92);
  background: rgba(255,255,255,0.15);
  border: 1px solid rgba(255,255,255,0.25);
  border-radius: 6px;
  padding: 2px 8px;
  letter-spacing: 0.05em;
}
.score-badge-large {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(16px, 2.5vw, 42px);
  color: rgba(255,255,255,0.6);
  letter-spacing: 0.05em;
}

/* ── VS badge ────────────────────────────────────── */
.vs-badge {
  position: absolute;
  bottom: 7vh;
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
}
.vs-text {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(24px, 4.2vw, 72px);
  color: rgba(255,255,255,0.88);
  letter-spacing: 0.14em;
  text-shadow: 0 0 40px rgba(255,255,255,0.25);
  clip-path: polygon(10% 0%, 90% 0%, 100% 50%, 90% 100%, 10% 100%, 0% 50%);
  background: rgba(0,0,0,0.25);
  padding: 4px 22px 4px 18px;
  display: block;
}
.vs-gone { display: none; }

/* ── Voting indicator ────────────────────────────── */
.voting-indicator {
  position: absolute;
  bottom: 2vh; left: 50%;
  transform: translateX(-50%);
  z-index: 40;
  display: flex; align-items: center; gap: 8px;
}
.voting-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: #ef4444;
  animation: votingPulse 1.2s ease-in-out infinite;
}
.voting-label {
  font-family: 'Inter', sans-serif;
  font-size: 11px; font-weight: 700;
  letter-spacing: 0.2em;
  color: rgba(255,255,255,0.45);
  text-transform: uppercase;
}
/* Fade transition for voting indicator */
.fade-indicator-enter-active,
.fade-indicator-leave-active { transition: opacity 0.3s ease; }
.fade-indicator-enter-from,
.fade-indicator-leave-to     { opacity: 0; }

/* ── Winner label — Anton SC, same design language as name ── */
.winner-label {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(14px, 2.2vw, 38px);
  letter-spacing: 0.5em;
  text-transform: uppercase;
  color: #fff;
  line-height: 1;
  text-align: center;
  opacity: 0;
  animation: winnerStamp 380ms cubic-bezier(0.2, 0, 0.3, 1) both;
  animation-delay: 250ms;
}
.winner-label-left {
  text-shadow: 3px 3px 0 var(--left-color),
               0 0 40px color-mix(in srgb, var(--left-color) 65%, transparent);
}
.winner-label-right {
  text-shadow: -3px 3px 0 var(--right-color),
               0 0 40px color-mix(in srgb, var(--right-color) 65%, transparent);
}

/* ══════════════════════════════════════════════════
   KEYFRAMES
══════════════════════════════════════════════════ */

/* Stage shake on VS landing */
@keyframes stageShake {
  0%   { transform: translate(0,    0);  }
  15%  { transform: translate(-4px, 3px); }
  30%  { transform: translate(5px, -2px); }
  45%  { transform: translate(-3px, 4px); }
  60%  { transform: translate(4px, -3px); }
  75%  { transform: translate(-2px, 2px); }
  100% { transform: translate(0,    0);  }
}

/* Glitch flicker during next-pair transition */
@keyframes glitchFlicker {
  0%   { opacity: 1;   clip-path: inset(0% 0 0% 0); transform: skewX(0); }
  12%  { opacity: 0;   }
  25%  { opacity: 1;   clip-path: inset(20% 0 30% 0); transform: skewX(2deg); }
  37%  { opacity: 0.7; }
  50%  { opacity: 1;   clip-path: inset(55% 0 5% 0);  transform: skewX(-1deg) translateX(3px); }
  62%  { opacity: 0.4; }
  75%  { opacity: 1;   clip-path: inset(10% 0 78% 0); transform: skewX(3deg); }
  87%  { opacity: 0.8; }
  100% { opacity: 0;   }
}

/* Left panel slams in from left edge */
@keyframes leftSlamIn {
  0%   { transform: translateX(-72vw); }
  82%  { transform: translateX(3px); }
  100% { transform: translateX(0); }
}

/* Right panel slams in from right edge */
@keyframes rightSlamIn {
  0%   { transform: translateX(72vw); }
  82%  { transform: translateX(-3px); }
  100% { transform: translateX(0); }
}

/* VS rushes in from in-front-of-camera (scale 6→0.72→1.10→1) */
@keyframes vsRushIn {
  0%   { transform: translateX(-50%) scale(6);    opacity: 0.5; }
  55%  { transform: translateX(-50%) scale(0.72); opacity: 1;   }
  75%  { transform: translateX(-50%) scale(1.10); }
  100% { transform: translateX(-50%) scale(1);    }
}

/* VS rockets toward right (left wins — loser is right) */
@keyframes vsKnockRight {
  0%   { transform: translateX(-50%) scale(1);    opacity: 1; }
  18%  { transform: translateX(-50%) scale(1.25); }
  100% { transform: translateX(80vw) scale(0.3) rotate(55deg); opacity: 0; }
}

/* VS rockets toward left (right wins — loser is left) */
@keyframes vsKnockLeft {
  0%   { transform: translateX(-50%) scale(1);    opacity: 1; }
  18%  { transform: translateX(-50%) scale(1.25); }
  100% { transform: translateX(-130vw) scale(0.3) rotate(-55deg); opacity: 0; }
}

/* Loser hard-cuts off left edge */
@keyframes hardCutLeft {
  0%   { transform: translateX(0);      opacity: 1; }
  100% { transform: translateX(-110vw); opacity: 0; }
}

/* Loser hard-cuts off right edge */
@keyframes hardCutRight {
  0%   { transform: translateX(0);     opacity: 1; }
  100% { transform: translateX(110vw); opacity: 0; }
}

/* Judge panel drops from top edge */
@keyframes slideDown {
  from { transform: translateY(-100%); }
  to   { transform: translateY(0); }
}
@keyframes slideUp {
  from { transform: translateY(0); }
  to   { transform: translateY(-100%); }
}

/* Judge slams from above into vertical center of screen, rests at larger scale */
@keyframes judgeSlamCenter {
  0%   { transform: translateY(-160%) scale(3.0);  opacity: 0; filter: blur(14px); }
  52%  { transform: translateY(42vh)  scale(1.28); opacity: 1; filter: blur(0); }
  68%  { transform: translateY(42vh)  scale(1.48); }
  82%  { transform: translateY(42vh)  scale(1.33); }
  100% { transform: translateY(42vh)  scale(1.38); opacity: 1; }
}

/* Judge scales back down while retreating to top */
@keyframes judgeRetreatTop {
  0%   { transform: translateY(42vh) scale(1.38); }
  100% { transform: translateY(0)    scale(1); }
}

/* Card burst entrance on score reveal */
@keyframes cardBurst {
  0%   { transform: scale(1.4) skewX(-5deg); opacity: 0; }
  65%  { transform: scale(0.97) skewX(0);   opacity: 1; }
  100% { transform: scale(1) skewX(0);      opacity: 1; }
}

/* Unvoted card pulse */
@keyframes cardPulse {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.58; }
}

/* Voting dot pulse */
@keyframes votingPulse {
  0%, 100% { transform: scale(1);   opacity: 1; }
  50%       { transform: scale(1.6); opacity: 0.4; }
}

/* WINNER stamp */
@keyframes winnerStamp {
  0%   { transform: scale(1.5) translateY(-10px); opacity: 0; filter: blur(6px); }
  60%  { transform: scale(0.97) translateY(1px);  opacity: 1; filter: blur(0); }
  100% { transform: scale(1) translateY(0);        opacity: 1; filter: blur(0); }
}

/* Placeholder breathing */
@keyframes placeholderBreath {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.5; }
}

/* (borderRotate removed — judge card no longer uses rotating glow border) */

/* ── Champion Reveal Overlay ────────────────────────────── */
.champ-overlay {
  position: absolute; inset: 0; z-index: 50;
  display: flex; align-items: center; justify-content: center;
  background: #060818;
}
.champ-overlay-bg {
  position: absolute; inset: 0; pointer-events: none;
  background: radial-gradient(ellipse 60% 50% at 50% 55%, rgba(245,158,11,0.14) 0%, transparent 68%);
}
.champ-overlay-content {
  position: relative; z-index: 1;
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  text-align: center;
}
.champ-genre-tag {
  font-family: 'Anton SC', sans-serif; font-size: 9px;
  letter-spacing: 0.45em; text-transform: uppercase;
  color: rgba(255,255,255,0.3);
}
.champ-label {
  font-family: 'Anton SC', sans-serif; font-size: 11px;
  letter-spacing: 0.5em; text-transform: uppercase;
  color: rgba(245,158,11,0.85);
}
.champ-name-slam {
  font-family: 'Anton SC', sans-serif; font-size: 15vw;
  letter-spacing: 0.07em; text-transform: uppercase; line-height: 1;
  color: #fff;
  text-shadow: 0 0 40px rgba(245,158,11,0.65), 0 0 80px rgba(245,158,11,0.3);
}
.champ-gold-bar {
  width: 180px; height: 2px; margin-top: 6px;
  background: linear-gradient(90deg, transparent, rgba(245,158,11,0.8), transparent);
}

/* Transition */
.champ-reveal-enter-active { transition: opacity 0.3s ease; }
.champ-reveal-leave-active { transition: opacity 0.25s ease; }
.champ-reveal-enter-from,
.champ-reveal-leave-to    { opacity: 0; }

.champ-reveal-enter-active .champ-name-slam {
  animation: champNameSlam 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275) 0.35s both;
}
.champ-reveal-enter-active .champ-label {
  animation: champFadeUp 0.4s ease 0.2s both;
}
.champ-reveal-enter-active .champ-genre-tag {
  animation: champFadeUp 0.4s ease 0.08s both;
}
.champ-reveal-enter-active .champ-gold-bar {
  animation: champBarExpand 0.6s ease 0.65s both;
}

@keyframes champNameSlam {
  from { opacity: 0; transform: scale(0.72) translateY(18px); }
  to   { opacity: 1; transform: scale(1)    translateY(0); }
}
@keyframes champFadeUp {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}
@keyframes champBarExpand {
  from { width: 0; opacity: 0; }
  to   { width: 180px; opacity: 1; }
}

/* ── Animation utility classes ───────────────────── */
.slam-in-left  { animation: leftSlamIn  450ms cubic-bezier(0.2, 0, 0.3, 1) both; }
.slam-in-right { animation: rightSlamIn 450ms cubic-bezier(0.2, 0, 0.3, 1) both; }

.rush-in      { animation: vsRushIn    520ms cubic-bezier(0.12, 0, 0.2, 1) both; }
.knock-right  { animation: vsKnockRight 380ms cubic-bezier(0.55, 0, 1, 0.45) forwards; }
.knock-left   { animation: vsKnockLeft  380ms cubic-bezier(0.55, 0, 1, 0.45) forwards; }

.slide-left-out  { animation: hardCutLeft  320ms cubic-bezier(0.55, 0, 1, 0) forwards; }
.slide-right-out { animation: hardCutRight 320ms cubic-bezier(0.55, 0, 1, 0) forwards; }

.slide-down       { animation: slideDown       480ms cubic-bezier(0.34, 1.3, 0.64, 1) forwards; }
.slide-up         { animation: slideUp         300ms cubic-bezier(0.2,  0,   1,   0) forwards; }
.judge-slam-center { animation: judgeSlamCenter 680ms cubic-bezier(0.2,  0,   0.3, 1) both; }
.judge-retreat-top { animation: judgeRetreatTop 500ms cubic-bezier(0.34, 1.1, 0.64, 1) forwards; }
.judge-at-top      { transform: translateY(0); }
</style>

