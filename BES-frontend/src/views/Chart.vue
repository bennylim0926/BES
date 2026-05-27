<!-- BES-frontend/src/views/Chart.vue -->
<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { getSmokeList, getOverlayConfig, getBattleJudges } from '@/utils/api'
import { createClient, subscribeToChannel, deactivateClient } from '@/utils/websocket'
import { useDelay } from '@/utils/utils'
import { barHeightPct, findScoreGainers } from '@/utils/smokeChartHelpers'

// ── Overlay config ──────────────────────────────────────────────────────────
const overlayConfig = ref({ leftColor: '#dc2626', rightColor: '#2563eb' })

// ── Chart data ──────────────────────────────────────────────────────────────
// Position 0 = active left, 1 = active right, 2+ = queue
const smokeParticipants = ref([])

// ── Judge data ──────────────────────────────────────────────────────────────
const battleJudges = ref(null)       // { judges: [{ id, name, vote }] }

// ── Result overlay ──────────────────────────────────────────────────────────
const showResult    = ref(false)
const resultState   = ref(null)  // { winner: 0|1|-1, leftName, rightName }

// ── Champion overlay ────────────────────────────────────────────────────────
const showChampion  = ref(false)
const champName     = ref('')
const champColor    = ref('#dc2626')

// ── Score pop animation ─────────────────────────────────────────────────────
const scorePopNames = ref(new Set())

// ── WebSocket clients ───────────────────────────────────────────────────────
const clients = []
const subscribedVoteTopics = new Set()
let unmounted = false

// ── Computed ────────────────────────────────────────────────────────────────
const activeLeft  = computed(() => smokeParticipants.value[0] ?? null)
const activeRight = computed(() => smokeParticipants.value[1] ?? null)

// ── Score pop: watch for score increases ────────────────────────────────────
watch(
  smokeParticipants,
  (next, prev) => {
    if (!prev?.length) return
    const gainers = findScoreGainers(prev, next)
    gainers.forEach(name => {
      scorePopNames.value = new Set([...scorePopNames.value, name])
      setTimeout(() => {
        scorePopNames.value = new Set([...scorePopNames.value].filter(n => n !== name))
      }, 600)
    })
  },
  { deep: true }
)

// ── WebSocket handlers ──────────────────────────────────────────────────────
const updateList = (msg) => {
  if (!msg?.battlers) return
  smokeParticipants.value = msg.battlers
  // Champion check runs here — after scores are updated in the list
  if (!showChampion.value) {
    const champ = msg.battlers.find(p => p.score >= 7)
    if (champ) {
      champName.value  = champ.name
      const idx = msg.battlers.indexOf(champ)
      champColor.value = idx === 0
        ? overlayConfig.value.leftColor
        : idx === 1
          ? overlayConfig.value.rightColor
          : overlayConfig.value.leftColor
      showChampion.value = true
    }
  }
}

const updateBattleJudge = (msg) => {
  if (!msg?.judges) return
  battleJudges.value = msg
  msg.judges.forEach(j => {
    const topic = `/topic/battle/vote/${j.id}`
    if (!subscribedVoteTopics.has(topic)) {
      subscribedVoteTopics.add(topic)
      const c = createClient(); clients.push(c)
      subscribeToChannel(c, topic, (m) => updateJudgeVote(m))
    }
  })
}

const updateJudgeVote = (msg) => {
  if (!battleJudges.value?.judges) return
  battleJudges.value = {
    ...battleJudges.value,
    judges: battleJudges.value.judges.map(j =>
      j.id === msg.judge ? { ...j, vote: msg.vote } : j
    )
  }
}

// Capture active names + judge state at reveal time so result overlay stays correct after queue shifts
const updateScore = async (msg) => {
  if (showChampion.value) return
  if (showResult.value) return  // already displaying a result — ignore duplicate events
  const leftName  = activeLeft.value?.name  ?? ''
  const rightName = activeRight.value?.name ?? ''
  const winner    = msg.message // 0 | 1 | -1
  // Fetch fresh judge votes from API at reveal time — avoids race where per-judge WS
  // subscriptions haven't connected yet and the local battleJudges still shows stale/cleared votes
  const freshJudges = await getBattleJudges()
  const judges = freshJudges?.judges ?? battleJudges.value?.judges ?? null

  resultState.value = { winner, leftName, rightName, judges }
  showResult.value = true

  await useDelay().wait(4000)
  if (unmounted) return
  showResult.value = false
}

// ── Mount ───────────────────────────────────────────────────────────────────
onMounted(async () => {
  document.documentElement.classList.add('transparent-page')
  document.body.classList.add('transparent-page')

  const config = await getOverlayConfig()
  if (config?.leftColor !== undefined) overlayConfig.value = config

  const smoke = await getSmokeList()
  if (smoke?.list) smokeParticipants.value = smoke.list

  battleJudges.value = await getBattleJudges()

  const cConfig = createClient(); clients.push(cConfig)
  subscribeToChannel(cConfig, '/topic/battle/overlay-config', (msg) => {
    if (msg?.leftColor !== undefined) overlayConfig.value = msg
  })

  const cSmoke = createClient(); clients.push(cSmoke)
  subscribeToChannel(cSmoke, '/topic/battle/smoke', updateList)

  const cJudges = createClient(); clients.push(cJudges)
  subscribeToChannel(cJudges, '/topic/battle/judges', updateBattleJudge)

  const cScore = createClient(); clients.push(cScore)
  subscribeToChannel(cScore, '/topic/battle/score', updateScore)

  const cPhase = createClient(); clients.push(cPhase)
  subscribeToChannel(cPhase, '/topic/battle/phase', (msg) => {
    // When phase resets to LOCKED (Next was clicked), dismiss result overlay immediately
    if (msg?.phase === 'LOCKED') showResult.value = false
  })
})

onBeforeUnmount(() => {
  unmounted = true
  clients.forEach(c => { if (c) deactivateClient(c) })
  document.documentElement.classList.remove('transparent-page')
  document.body.classList.remove('transparent-page')
})
</script>

<template>
  <div
    class="smoke-root"
    :style="{ '--left-color': overlayConfig.leftColor, '--right-color': overlayConfig.rightColor }"
  >
    <!-- Atmospheric background bleeds -->
    <div class="atmo-bleed" aria-hidden="true"></div>

    <!-- Header -->
    <div class="smoke-header" aria-hidden="true">
      <span class="smoke-title">7 TO SMOKE</span>
    </div>

    <!-- Bar chart -->
    <div class="smoke-chart">
      <TransitionGroup tag="div" name="col" class="smoke-cols">
        <template v-for="(item, idx) in smokeParticipants" :key="item.name">
          <!-- VS chip between position 0 and 1 -->
          <div v-if="idx === 1" class="vs-chip" aria-hidden="true">VS</div>

          <!-- Queue gap spacer between position 1 and 2 -->
          <div v-if="idx === 2" class="queue-gap" aria-hidden="true"></div>

          <!-- Fighter column -->
          <div
            class="smoke-col"
            :class="{
              'col-active-left':  idx === 0,
              'col-active-right': idx === 1,
            }"
          >
            <div class="bar-wrap">
              <div
                class="bar"
                :class="{ 'score-pop': scorePopNames.has(item.name) }"
                :style="{ height: barHeightPct(item.score) + '%' }"
                :aria-label="`${item.name}: ${item.score} points`"
              ></div>
            </div>
            <div class="dots-row" aria-hidden="true">
              <div
                v-for="d in 7"
                :key="d"
                class="dot"
                :class="{ filled: d <= item.score }"
              ></div>
            </div>
            <div class="col-name">{{ item.name }}</div>
          </div>
        </template>
      </TransitionGroup>

      <!-- Design B: ghost names behind chart -->
      <div
        v-if="activeLeft && activeRight"
        class="bg-names"
        aria-hidden="true"
      >
        <span class="bg-name bg-name-left">{{ activeLeft.name }}</span>
        <span class="bg-vs">VS</span>
        <span class="bg-name bg-name-right">{{ activeRight.name }}</span>
      </div>
    </div>

    <!-- Result overlay (win / tie) -->
    <Transition name="result-fade">
      <div
        v-if="showResult && resultState && !showChampion"
        class="result-overlay"
        role="status"
        aria-live="assertive"
      >
        <!-- Win header -->
        <template v-if="resultState.winner === 0">
          <div class="result-name result-left">{{ resultState.leftName }}</div>
          <div class="result-sub">TAKES THE POINT</div>
        </template>
        <template v-else-if="resultState.winner === 1">
          <div class="result-name result-right">{{ resultState.rightName }}</div>
          <div class="result-sub">TAKES THE POINT</div>
        </template>
        <template v-else>
          <div class="result-name result-tie">IT'S A TIE</div>
          <div class="result-sub">NO POINTS AWARDED</div>
        </template>

        <!-- Judge cards (snapshot at reveal time — not live battleJudges) -->
        <div v-if="resultState.judges" class="judge-inner">
          <div class="judges-header" aria-hidden="true">
            <span class="judges-line"></span>
            <span class="judges-label">JUDGES</span>
            <span class="judges-line"></span>
          </div>
          <div class="judge-cards-row" role="list">
            <div
              v-for="(j, index) in resultState.judges"
              :key="j.id"
              class="judge-card card-burst"
              :class="{
                'voted-left':  j.vote === 0,
                'voted-right': j.vote === 1,
                'voted-tie':   j.vote === -1,
              }"
              :style="{ animationDelay: `${index * 55}ms` }"
              role="listitem"
              :aria-label="`${j.name}: ${j.vote === 0 ? 'voted left' : j.vote === 1 ? 'voted right' : 'tie'}`"
            >
              <div v-if="j.vote !== -1" class="judge-row">
                <span class="vote-arrow vote-arrow-left" :class="{ 'arrow-lit-left': j.vote === 0 }" aria-hidden="true"></span>
                <span class="judge-name">{{ j.name }}</span>
                <span class="vote-arrow vote-arrow-right" :class="{ 'arrow-lit-right': j.vote === 1 }" aria-hidden="true"></span>
              </div>
              <div v-else class="judge-row">
                <span class="judge-name judge-name-tie">{{ j.name }}</span>
              </div>
              <div v-if="j.vote !== -1" class="vote-track" aria-hidden="true">
                <div class="vote-fill" :class="{ 'fill-left': j.vote === 0, 'fill-right': j.vote === 1, 'fill-blank': j.vote !== 0 && j.vote !== 1 }"></div>
              </div>
              <div v-else class="tie-badge" aria-hidden="true">TIE</div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Champion overlay -->
    <Transition name="champ-fade">
      <div
        v-if="showChampion"
        class="champion-overlay"
        :style="{ '--champ-color': champColor }"
        role="status"
        aria-live="assertive"
        :aria-label="`${champName} is the 7 to Smoke champion`"
      >
        <div class="champ-burst" aria-hidden="true"></div>
        <div class="champ-name">{{ champName }}</div>
        <div class="champ-sub">7 TO SMOKE CHAMPION</div>
      </div>
    </Transition>
  </div>
</template>

<style>
/* OBS transparent background — must be global */
html.transparent-page,
body.transparent-page,
html.transparent-page #app,
body.transparent-page #app {
  background: transparent !important;
  background-color: transparent !important;
}
</style>

<style scoped>
/* ── Root ─────────────────────────────────────────────── */
.smoke-root {
  position: fixed;
  inset: 0;
  font-family: 'Anton SC', sans-serif;
  background: #06080e;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  --left-color:  #dc2626;
  --right-color: #2563eb;
}

/* ── Atmospheric bleeds ───────────────────────────────── */
.atmo-bleed {
  position: absolute; inset: 0; z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(ellipse 60% 60% at 0% 100%,   color-mix(in srgb, var(--left-color)  16%, transparent) 0%, transparent 70%),
    radial-gradient(ellipse 60% 60% at 100% 100%, color-mix(in srgb, var(--right-color) 16%, transparent) 0%, transparent 70%);
}

/* ── Header ───────────────────────────────────────────── */
.smoke-header {
  position: relative; z-index: 2; flex-shrink: 0;
  display: flex; align-items: center; justify-content: space-between;
  padding: 8px 18px 6px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  background: rgba(0,0,0,0.35);
  backdrop-filter: blur(8px);
}
.smoke-title {
  font-size: clamp(10px, 1.4vw, 16px);
  letter-spacing: 0.22em;
  color: rgba(255,255,255,0.5);
}

/* ── Chart area ───────────────────────────────────────── */
.smoke-chart {
  position: relative; z-index: 2;
  flex: 1; min-height: 0;
  display: flex; flex-direction: column;
}

.smoke-cols {
  flex: 1; min-height: 0;
  display: flex;
  align-items: flex-end;
  padding: 10px 14px 0;
  gap: 5px;
  overflow: hidden;
}

/* ── Fighter column ───────────────────────────────────── */
.smoke-col {
  flex: 1;
  display: flex; flex-direction: column; align-items: center;
  height: 100%;
  /* Cap columns so full bars (7/7) only graze the bottom quarter of the ghost names */
  max-height: 68%;
}

/* ── Bar wrap + bar ───────────────────────────────────── */
.bar-wrap {
  flex: 1; width: 100%;
  display: flex; flex-direction: column; justify-content: flex-end;
  position: relative;
}
/* Dashed goal line at top */
.bar-wrap::before {
  content: '';
  position: absolute; top: 0; left: 0; right: 0;
  border-top: 1px dashed rgba(255,255,255,0.07);
}
.bar {
  width: 100%;
  border-radius: 5px 5px 2px 2px;
  min-height: 3px;
  position: relative; overflow: hidden;
  transition: height 0.6s cubic-bezier(0.22, 1, 0.36, 1);
  /* Default: waiting/gray */
  background: linear-gradient(0deg, #181818 0%, #2a2a2a 100%);
}
/* Glossy top highlight — only on active bars */
.bar::after {
  content: '';
  position: absolute; top: 0; left: 0; right: 0; height: 45%;
  background: linear-gradient(180deg, rgba(255,255,255,0.14) 0%, transparent 100%);
  border-radius: 5px 5px 0 0;
  pointer-events: none;
  display: none;
}

/* Active left — red */
.col-active-left .bar {
  background: linear-gradient(0deg,
    color-mix(in srgb, var(--left-color) 35%, #000) 0%,
    var(--left-color) 75%,
    color-mix(in srgb, var(--left-color) 50%, #fff) 100%
  );
  box-shadow:
    0 0 22px color-mix(in srgb, var(--left-color) 65%, transparent),
    0 0 60px color-mix(in srgb, var(--left-color) 25%, transparent),
    inset 0 0 0 1.5px rgba(255,255,255,0.2);
}
.col-active-left .bar::after { display: block; }

/* Active right — blue */
.col-active-right .bar {
  background: linear-gradient(0deg,
    color-mix(in srgb, var(--right-color) 35%, #000) 0%,
    var(--right-color) 75%,
    color-mix(in srgb, var(--right-color) 50%, #fff) 100%
  );
  box-shadow:
    0 0 22px color-mix(in srgb, var(--right-color) 65%, transparent),
    0 0 60px color-mix(in srgb, var(--right-color) 25%, transparent),
    inset 0 0 0 1.5px rgba(255,255,255,0.2);
}
.col-active-right .bar::after { display: block; }

/* ── Dots row ─────────────────────────────────────────── */
.dots-row {
  display: flex; gap: 2px; margin-top: 5px; justify-content: center;
  flex-shrink: 0; flex-wrap: wrap;
}
.dot {
  width: clamp(8px, 1.3vw, 16px); height: clamp(8px, 1.3vw, 16px);
  border-radius: 50%;
  border: 1.5px solid currentColor;
  opacity: 0.25;
  color: rgba(255,255,255,0.5);
}
.dot.filled { opacity: 1; background: currentColor; }
.col-active-left  .dot { color: var(--left-color); }
.col-active-right .dot { color: var(--right-color); }

/* ── Name label ───────────────────────────────────────── */
.col-name {
  font-size: clamp(11px, 1.6vw, 20px);
  letter-spacing: 0.1em;
  text-align: center;
  margin-top: 3px; margin-bottom: 5px;
  color: rgba(255,255,255,0.28);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  width: 100%; padding: 0 2px;
}
.col-active-left .col-name {
  color: color-mix(in srgb, var(--left-color) 50%, #fff);
  text-shadow: 0 0 12px color-mix(in srgb, var(--left-color) 80%, transparent);
}
.col-active-right .col-name {
  color: color-mix(in srgb, var(--right-color) 50%, #fff);
  text-shadow: 0 0 12px color-mix(in srgb, var(--right-color) 80%, transparent);
}

/* ── VS chip ──────────────────────────────────────────── */
.vs-chip {
  flex: 0 0 clamp(16px, 2.2vw, 26px);
  align-self: flex-end;
  margin-bottom: clamp(20px, 3vw, 30px);
  display: flex; align-items: center; justify-content: center;
  font-size: clamp(7px, 0.9vw, 10px);
  letter-spacing: 0.1em;
  color: rgba(255,255,255,0.5);
  border: 1px solid rgba(255,255,255,0.12);
  border-radius: 4px;
  background: rgba(255,255,255,0.04);
  padding: 3px 0; line-height: 1;
}

/* ── Queue gap ────────────────────────────────────────── */
.queue-gap { flex: 0 0 clamp(8px, 1.4vw, 16px); }

/* ── Design B: ghost names behind chart ───────────────── */
.bg-names {
  position: absolute; z-index: 1;
  top: 4%; left: 0; right: 0;
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 14px;
  pointer-events: none;
}
.bg-name {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(32px, 8vw, 110px);
  letter-spacing: 0.06em; line-height: 1;
  opacity: 0.1;
  white-space: nowrap;
}
.bg-name-left  { color: var(--left-color);  text-shadow: 0 0 40px var(--left-color); }
.bg-name-right { color: var(--right-color); text-shadow: 0 0 40px var(--right-color); }
.bg-vs {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(10px, 2vw, 24px); letter-spacing: 0.2em;
  color: rgba(255,255,255,0.08);
  flex-shrink: 0; padding: 0 8px;
}

/* ── FLIP column slide (TransitionGroup move) ─────────── */
.col-move {
  transition: transform 0.85s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

/* ══════════════════════════════════════════════════
   RESULT OVERLAY
══════════════════════════════════════════════════ */
.result-overlay {
  position: absolute; inset: 0; z-index: 50;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  gap: 6px;
  background: rgba(6, 8, 18, 0.72);
  backdrop-filter: blur(4px);
}

.result-name {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(28px, 5.5vw, 88px);
  letter-spacing: 0.07em;
  line-height: 1;
  text-transform: uppercase;
  animation: resultSlam 520ms cubic-bezier(0.2, 0, 0.3, 1) both;
}
.result-left {
  color: #fff;
  text-shadow: 4px 4px 0 var(--left-color),
               0 0 50px color-mix(in srgb, var(--left-color) 50%, transparent);
}
.result-right {
  color: #fff;
  text-shadow: -4px 4px 0 var(--right-color),
               0 0 50px color-mix(in srgb, var(--right-color) 50%, transparent);
}
.result-tie {
  color: #fff;
  text-shadow: 0 0 40px rgba(255,255,255,0.3);
}
.result-sub {
  font-family: 'Inter', sans-serif;
  font-weight: 700;
  font-size: clamp(9px, 1.1vw, 16px);
  letter-spacing: 0.32em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.35);
  margin-top: 2px; margin-bottom: 16px;
}

/* Judge inner container */
.judge-inner {
  position: relative;
  display: flex; flex-direction: column; gap: 8px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--left-color)  12%, rgba(6,8,18,0.94)),
    color-mix(in srgb, var(--right-color) 12%, rgba(6,8,18,0.94))
  );
  backdrop-filter: blur(24px);
  border-radius: 14px;
  border: 1px solid rgba(255,255,255,0.12);
  box-shadow: 0 24px 80px rgba(0,0,0,0.75), 0 0 0 1px rgba(255,255,255,0.04) inset;
  padding: 10px 20px 14px;
}
.judges-header { display: flex; align-items: center; gap: 8px; }
.judges-label {
  font-family: 'Inter', sans-serif;
  font-size: 9px; font-weight: 700;
  letter-spacing: 0.32em; color: rgba(255,255,255,0.28);
  text-transform: uppercase; white-space: nowrap; flex-shrink: 0;
}
.judges-line { flex: 1; height: 1px; background: rgba(255,255,255,0.07); }
.judge-cards-row { display: flex; gap: 8px; }

/* Judge card — parallelogram */
.judge-card {
  display: flex; flex-direction: column; align-items: center; gap: 7px;
  min-width: 130px; padding: 8px 12px;
  clip-path: polygon(5px 0%, 100% 0%, calc(100% - 5px) 100%, 0% 100%);
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.06);
}
.voted-left {
  background: color-mix(in srgb, var(--left-color)  12%, transparent);
  border-color: color-mix(in srgb, var(--left-color)  40%, transparent);
  box-shadow: 0 0 20px color-mix(in srgb, var(--left-color)  25%, transparent);
}
.voted-right {
  background: color-mix(in srgb, var(--right-color) 12%, transparent);
  border-color: color-mix(in srgb, var(--right-color) 40%, transparent);
  box-shadow: 0 0 20px color-mix(in srgb, var(--right-color) 25%, transparent);
}
.voted-tie {
  background: rgba(255,255,255,0.06);
  border-color: rgba(255,255,255,0.20);
}
.judge-row { display: flex; align-items: center; gap: 9px; }
.judge-name {
  font-family: 'Anton SC', sans-serif;
  font-size: 22px; color: rgba(255,255,255,0.90);
  letter-spacing: 0.06em; text-transform: uppercase; line-height: 1;
}
.judge-name-tie { color: rgba(255,255,255,0.32) !important; }
.vote-arrow {
  display: inline-block; width: 18px; height: 18px; flex-shrink: 0;
  background: rgba(255,255,255,0.15); opacity: 0.2;
  transition: opacity 0.2s ease, background 0.2s ease, filter 0.2s ease;
}
.vote-arrow-left  { clip-path: polygon(100% 0%, 0% 50%, 100% 100%); }
.vote-arrow-right { clip-path: polygon(0% 0%, 100% 50%, 0% 100%); }
.arrow-lit-left  { opacity: 1; background: var(--left-color);  filter: drop-shadow(0 0 8px var(--left-color)); }
.arrow-lit-right { opacity: 1; background: var(--right-color); filter: drop-shadow(0 0 8px var(--right-color)); }
.tie-badge {
  font-family: 'Anton SC', sans-serif;
  font-size: 13px; letter-spacing: 0.35em;
  color: rgba(255,255,255,0.55); text-align: center; width: 100%;
  border: 1px solid rgba(255,255,255,0.18); border-radius: 4px; padding: 2px 0;
}
.vote-track {
  width: 100%; height: 10px;
  background: rgba(255,255,255,0.1); border-radius: 9999px; overflow: hidden;
}
.vote-fill { height: 100%; border-radius: 9999px; width: 0%; }
.fill-left  {
  width: 100%;
  background: linear-gradient(90deg, color-mix(in srgb, var(--left-color)  70%, black), var(--left-color));
  box-shadow: 0 0 14px color-mix(in srgb, var(--left-color)  90%, transparent);
}
.fill-right {
  width: 100%;
  background: linear-gradient(90deg, color-mix(in srgb, var(--right-color) 70%, black), var(--right-color));
  box-shadow: 0 0 14px color-mix(in srgb, var(--right-color) 90%, transparent);
}
.fill-blank { background: transparent; }

/* Result overlay fade transition */
.result-fade-enter-active { transition: opacity 0.25s ease; }
.result-fade-leave-active  { transition: opacity 0.4s ease; }
.result-fade-enter-from, .result-fade-leave-to { opacity: 0; }

/* ══════════════════════════════════════════════════
   CHAMPION OVERLAY
══════════════════════════════════════════════════ */
.champion-overlay {
  position: absolute; inset: 0; z-index: 60;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 10px;
  background: rgba(4, 5, 12, 0.88);
  --champ-color: #dc2626;
}
.champ-burst {
  position: absolute; inset: 0; pointer-events: none;
  background: radial-gradient(
    ellipse 70% 60% at 50% 50%,
    color-mix(in srgb, var(--champ-color) 30%, transparent) 0%,
    transparent 70%
  );
  animation: champBurst 0.6s ease-out both;
}
.champ-name {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(36px, 7vw, 110px);
  letter-spacing: 0.08em; line-height: 1;
  text-transform: uppercase;
  color: #fff;
  text-shadow: 4px 4px 0 var(--champ-color),
               0 0 60px color-mix(in srgb, var(--champ-color) 60%, transparent);
  animation: champSlam 720ms cubic-bezier(0.2, 0, 0.3, 1) both;
}
.champ-sub {
  font-family: 'Inter', sans-serif;
  font-weight: 700;
  font-size: clamp(10px, 1.3vw, 18px);
  letter-spacing: 0.4em; text-transform: uppercase;
  color: rgba(255,255,255,0.4);
  animation: champSubFade 0.5s ease both;
  animation-delay: 0.45s;
  opacity: 0;
}

.champ-fade-enter-active { transition: opacity 0.2s ease; }
.champ-fade-leave-active  { transition: opacity 0.5s ease; }
.champ-fade-enter-from, .champ-fade-leave-to { opacity: 0; }

/* ══════════════════════════════════════════════════
   KEYFRAMES
══════════════════════════════════════════════════ */
@keyframes resultSlam {
  0%   { transform: scale(2.2) translateY(-16px); opacity: 0; filter: blur(10px); }
  55%  { transform: scale(0.97) translateY(0);    opacity: 1; filter: blur(0); }
  72%  { transform: scale(1.03); }
  85%  { transform: scale(0.99); }
  100% { transform: scale(1); }
}

@keyframes champSlam {
  0%   { transform: scale(2.5) translateY(-20px); opacity: 0; filter: blur(12px); }
  55%  { transform: scale(0.96) translateY(0);    opacity: 1; filter: blur(0); }
  72%  { transform: scale(1.04); }
  85%  { transform: scale(0.99); }
  100% { transform: scale(1); }
}

@keyframes champBurst {
  0%   { opacity: 0; transform: scale(0.5); }
  60%  { opacity: 1; transform: scale(1.05); }
  100% { opacity: 0.85; transform: scale(1); }
}

@keyframes champSubFade {
  from { opacity: 0; transform: translateY(8px); }
  to   { opacity: 1; transform: translateY(0); }
}

@keyframes scorePop {
  0%   { transform: scaleY(1) scaleX(1); filter: brightness(1); }
  18%  { transform: scaleY(1.07) scaleX(1.04); filter: brightness(2.5); }
  38%  { transform: scaleY(0.96) scaleX(1.02); filter: brightness(1.5); }
  58%  { transform: scaleY(1.02) scaleX(1.01); filter: brightness(1.1); }
  100% { transform: scaleY(1) scaleX(1); filter: brightness(1); }
}
.score-pop {
  animation: scorePop 600ms cubic-bezier(0.2, 0, 0.3, 1) both;
}

@keyframes cardBurst {
  0%   { transform: scale(1.4) skewX(-5deg); opacity: 0; }
  65%  { transform: scale(0.97) skewX(0);   opacity: 1; }
  100% { transform: scale(1) skewX(0);      opacity: 1; }
}
.card-burst {
  animation: cardBurst 280ms cubic-bezier(0.2, 0, 0.3, 1) both;
}
</style>
