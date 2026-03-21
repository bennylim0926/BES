<script setup>
import { getBattleJudges, getCurrentBattlePair, getImage } from '@/utils/api';
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket';
import { computed, onBeforeUnmount, onMounted, onUnmounted, ref, watch } from 'vue';
import { useDelay } from '@/utils/utils';
import { useRoute } from 'vue-router'
import Chart from './Chart.vue';

const route = useRoute()

const imageLeft = ref(null)
const imageRight = ref(null)

let client = createClient()
const subscribedTopics = new Set();
const rightName = ref("")
const leftName = ref("")

const leftScore = ref(0)
const rightScore = ref(0)
const currentWinner = ref(-2)

const battleJudges = ref([])

const hideJudgeDecision = ref(true)

// Separate animation state for judge panel:
// ''            = off-screen (no animation, CSS default)
// 'slide-down'  = animating in (Get Score pressed)
// 'slide-up'    = animating out (Next pair pressed)
const judgeAnim = ref('')

const leftWin = ref(false)
const rightWin = ref(false)

const leftReset = ref(false)
const rightReset = ref(false)

const isSmoke = computed(() => route.query.isSmoke === 'true')

// In smoke mode: panel is always on-screen, no animation class needed.
// In standard mode: use judgeAnim ('', 'slide-down', 'slide-up').
const judgePanelClass = computed(() => {
  if (isSmoke.value) return 'smoke-judge-always-on'
  return judgeAnim.value
})

const updateBattlePair = async (msg) => {
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

  // STANDARD MODE: animate out, then swap pair
  if (!hideJudgeDecision.value) {
    if (judgeAnim.value === 'slide-down') {
      judgeAnim.value = 'slide-up'
    }

    if (currentWinner.value === 0) {
      leftReset.value = true;
    } else if (currentWinner.value === 1) {
      rightReset.value = true;
    } else {
      leftReset.value = true;
      rightReset.value = true;
    }

    await useDelay().wait(1000);

    judgeAnim.value = ''
    hideJudgeDecision.value = true;
    await useDelay().wait(50);

    leftReset.value = false;
    rightReset.value = false;
  }

  leftWin.value = false;
  rightWin.value = false;
  currentWinner.value = -2;
  rightName.value = msg.right;
  rightScore.value = msg.rightScore;
  leftName.value = msg.left;
  leftScore.value = msg.leftScore;
  imageLeft.value = await getImage(`${leftName.value}.png`);
  imageRight.value = await getImage(`${rightName.value}.png`);
};

const updateBattleJudge = (msg) => {
  battleJudges.value = msg;

  battleJudges.value.judges.forEach(j => {
    const topic = `/topic/battle/vote/${j.id}`;
    if (!subscribedTopics.has(topic)) {
      subscribedTopics.add(topic);
      subscribeToChannel(createClient(), topic, (msg) => updateJudgeVote(msg));
    }
  });
};

const updateScore = async (msg) => {
  judgeAnim.value = 'slide-down'
  hideJudgeDecision.value = false
  rightScore.value = msg.right
  leftScore.value = msg.left
  await useDelay().wait(1000);
  currentWinner.value = msg.message
  if (msg.message === 0) {
    leftWin.value = true
    rightWin.value = false
  } else if (msg.message === 1) {
    leftWin.value = false
    rightWin.value = true
  } else if (msg.message === -1) {
    leftWin.value = false
    rightWin.value = false
  }
}

const updateJudgeVote = (msg) => {
  const updatedJudges = battleJudges.value.judges.map(j =>
    j.id === msg.judge ? { ...j, vote: msg.vote } : j
  );
  battleJudges.value = {
    ...battleJudges.value,
    judges: updatedJudges
  };
};

watch(battleJudges, (newVal) => {
  newVal.judges.forEach(j => {
    const topic = `/topic/battle/vote/${j.id}`;
    if (!subscribedTopics.has(topic)) {
      subscribedTopics.add(topic);
      subscribeToChannel(createClient(), topic, (msg) => updateJudgeVote(msg));
    }
  });
}, { deep: true })

onMounted(async () => {
  document.documentElement.classList.add("transparent-page");
  document.body.classList.add("transparent-page");
  if (isSmoke.value) {
    battleJudges.value = await getBattleJudges()
    subscribeToChannel(createClient(), "/topic/battle/judges", (msg) => updateBattleJudge(msg))
    subscribeToChannel(createClient(), "/topic/battle/battle-pair", (msg) => updateBattlePair(msg))
    subscribeToChannel(createClient(), "/topic/battle/score", (msg) => updateScore(msg))
  } else {
    battleJudges.value = await getBattleJudges()
    const res = await getCurrentBattlePair()
    updateBattlePair(res)
    subscribeToChannel(createClient(), "/topic/battle/battle-pair", (msg) => updateBattlePair(msg))
    subscribeToChannel(createClient(), "/topic/battle/score", (msg) => updateScore(msg))
    subscribeToChannel(createClient(), "/topic/battle/judges", (msg) => updateBattleJudge(msg))
  }
})

onBeforeUnmount(() => {
  deactivateClient(client.value)
})

onUnmounted(() => {
  document.documentElement.classList.remove("transparent-page");
  document.body.classList.remove("transparent-page");
  const appRoot = document.getElementById("app");
  if (appRoot) appRoot.style.background = "";
});
</script>


<template>
  <div class="overlay-root">

    <!-- ══════════════════════════════════════════════════════
         JUDGE PANEL — shared across both modes
         Standard: slides down on Get Score
         Smoke:    always visible, only votes reset on Next
    ═══════════════════════════════════════════════════════ -->
    <div
      v-if="battleJudges?.judges?.length"
      class="judge-panel"
      :class="judgePanelClass"
    >
      <!-- Prismatic border layer -->
      <div class="judge-border-glow"></div>

      <div class="judge-inner">
        <!-- Header label -->
        <div class="judges-header">
          <span class="judges-line"></span>
          <span class="judges-label">JUDGES</span>
          <span class="judges-line"></span>
        </div>

        <!-- Judge cards -->
        <div class="judge-cards-row">
          <div
            v-for="(j, index) in battleJudges.judges"
            :key="index"
            class="judge-card"
            :class="{
              'voted-red':  j.vote === 0,
              'voted-blue': j.vote === 1,
              'voted-tie':  j.vote === -1,
            }"
          >
            <!-- Arrow + Name + Arrow -->
            <div class="judge-row">
              <span class="vote-arrow vote-arrow-left"  :class="{ 'arrow-lit-red':  j.vote === 0 }"></span>
              <span class="judge-name">{{ j.name }}</span>
              <span class="vote-arrow vote-arrow-right" :class="{ 'arrow-lit-blue': j.vote === 1 }"></span>
            </div>
            <!-- Vote track bar -->
            <div class="vote-track">
              <div
                class="vote-fill"
                :class="{
                  'fill-red':   j.vote === 0,
                  'fill-blue':  j.vote === 1,
                  'fill-tie':   j.vote === -1,
                  'fill-blank': j.vote === -3 || j.vote === null,
                }"
              ></div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ═══════════════════════════════════════════════
         STANDARD BATTLE  (isSmoke = false)
    ════════════════════════════════════════════════ -->
    <template v-if="!isSmoke">

      <!-- ── Left Battler ── -->
      <div
        class="battler-panel left-panel"
        :class="{
          'slide-left':     rightWin,
          'fade-out':       rightWin,
          'slide-right':    leftWin,
          'left-slide-in':  hideJudgeDecision,
          'slide-left-out': leftReset,
          'panel-winner':   leftWin,
        }"
      >
        <div class="panel-gradient red-gradient"></div>
        <div class="panel-vignette"></div>

        <img v-if="imageLeft" :src="imageLeft" alt="" class="battler-img" />
        <div v-else class="battler-placeholder red-placeholder">
          <svg viewBox="0 0 100 200" fill="none" xmlns="http://www.w3.org/2000/svg" class="placeholder-svg">
            <circle cx="50" cy="30" r="22" fill="rgba(239,68,68,0.2)" stroke="rgba(239,68,68,0.35)" stroke-width="1.5"/>
            <path d="M18 90 Q50 70 82 90 L88 160 Q70 150 50 155 Q30 150 12 160 Z" fill="rgba(239,68,68,0.12)" stroke="rgba(239,68,68,0.28)" stroke-width="1.5"/>
            <line x1="18" y1="160" x2="10" y2="200" stroke="rgba(239,68,68,0.28)" stroke-width="5" stroke-linecap="round"/>
            <line x1="82" y1="160" x2="90" y2="200" stroke="rgba(239,68,68,0.28)" stroke-width="5" stroke-linecap="round"/>
          </svg>
        </div>

        <div class="name-bar red-bar" :class="{ 'bar-win-glow': leftWin }">
          <span class="name-text">{{ leftName || '???' }}</span>
          <span v-if="leftScore > 0" class="score-badge">{{ leftScore }}</span>
        </div>
      </div>

      <!-- ── VS badge ── -->
      <div class="vs-badge" :class="{ 'vs-hidden': currentWinner !== -2 }">
        <span class="vs-deco">◆</span>
        <span class="vs-text">VS</span>
        <span class="vs-deco">◆</span>
      </div>

      <!-- ── Right Battler ── -->
      <div
        class="battler-panel right-panel"
        :class="{
          'slide-left':      rightWin,
          'fade-out':        leftWin,
          'slide-right':     leftWin,
          'right-slide-in':  hideJudgeDecision,
          'slide-right-out': rightReset,
          'panel-winner':    rightWin,
        }"
      >
        <div class="panel-gradient blue-gradient"></div>
        <div class="panel-vignette"></div>

        <img v-if="imageRight" :src="imageRight" alt="" class="battler-img" />
        <div v-else class="battler-placeholder blue-placeholder">
          <svg viewBox="0 0 100 200" fill="none" xmlns="http://www.w3.org/2000/svg" class="placeholder-svg">
            <circle cx="50" cy="30" r="22" fill="rgba(59,130,246,0.2)" stroke="rgba(59,130,246,0.35)" stroke-width="1.5"/>
            <path d="M18 90 Q50 70 82 90 L88 160 Q70 150 50 155 Q30 150 12 160 Z" fill="rgba(59,130,246,0.12)" stroke="rgba(59,130,246,0.28)" stroke-width="1.5"/>
            <line x1="18" y1="160" x2="10" y2="200" stroke="rgba(59,130,246,0.28)" stroke-width="5" stroke-linecap="round"/>
            <line x1="82" y1="160" x2="90" y2="200" stroke="rgba(59,130,246,0.28)" stroke-width="5" stroke-linecap="round"/>
          </svg>
        </div>

        <div class="name-bar blue-bar" :class="{ 'bar-win-glow': rightWin }">
          <span class="name-text">{{ rightName || '???' }}</span>
          <span v-if="rightScore > 0" class="score-badge">{{ rightScore }}</span>
        </div>
      </div>

    </template>

    <!-- ═══════════════════════════════════════════════
         SMOKE MODE  (isSmoke = true)
    ════════════════════════════════════════════════ -->
    <template v-else>
      <Chart />
    </template>

  </div>
</template>


<style>
/* ── Transparent background (OBS) ─────────────────── */
html.transparent-page,
body.transparent-page,
html.transparent-page #app,
body.transparent-page #app {
  background: transparent !important;
  background-color: transparent !important;
}

/* ── Root container ───────────────────────────────── */
.overlay-root {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  font-family: 'Anton SC', sans-serif;
}

/* ══════════════════════════════════════════════════
   JUDGE PANEL
══════════════════════════════════════════════════ */
.judge-panel {
  position: absolute;
  top: 18px;
  left: 0;
  right: 0;
  z-index: 50;
  display: flex;
  justify-content: center;
  pointer-events: none;
  transform: translateY(-200px); /* off-screen default — no flash on load */
}

/* Smoke mode: always pinned, no animation */
.smoke-judge-always-on {
  transform: translateY(0) !important;
  animation: none !important;
}

/* Prismatic gradient border ring behind the inner card */
.judge-border-glow {
  position: absolute;
  inset: -1px;
  border-radius: 22px;
  background: linear-gradient(
    135deg,
    rgba(239,68,68,0.55) 0%,
    rgba(255,255,255,0.12) 35%,
    rgba(59,130,246,0.55) 65%,
    rgba(255,255,255,0.12) 100%
  );
  filter: blur(1px);
  animation: borderRotate 6s linear infinite;
  z-index: -1;
}

.judge-inner {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: rgba(6, 8, 18, 0.82);
  backdrop-filter: blur(28px);
  -webkit-backdrop-filter: blur(28px);
  border-radius: 20px;
  padding: 14px 24px 16px;
}

/* "JUDGES" header row */
.judges-header {
  display: flex;
  align-items: center;
  gap: 10px;
}
.judges-label {
  font-family: 'Inter', sans-serif;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.28em;
  color: rgba(255,255,255,0.35);
  text-transform: uppercase;
  white-space: nowrap;
  flex-shrink: 0;
}
.judges-line {
  flex: 1;
  height: 1px;
  background: rgba(255,255,255,0.1);
}

/* Cards row */
.judge-cards-row {
  display: flex;
  gap: 12px;
}

/* Judge card */
.judge-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  min-width: 148px;
  padding: 10px 14px;
  border-radius: 14px;
  border: 1.5px solid rgba(255,255,255,0.06);
  background: rgba(255,255,255,0.025);
  transition: border-color 0.35s ease, box-shadow 0.35s ease, background 0.35s ease;
}
.voted-red  {
  border-color: rgba(239,68,68,0.65);
  background: rgba(239,68,68,0.09);
  box-shadow: 0 0 28px rgba(239,68,68,0.35), inset 0 0 14px rgba(239,68,68,0.08);
}
.voted-blue {
  border-color: rgba(59,130,246,0.65);
  background: rgba(59,130,246,0.09);
  box-shadow: 0 0 28px rgba(59,130,246,0.35), inset 0 0 14px rgba(59,130,246,0.08);
}
.voted-tie  {
  border-color: rgba(251,191,36,0.65);
  background: rgba(251,191,36,0.07);
  box-shadow: 0 0 28px rgba(251,191,36,0.28), inset 0 0 14px rgba(251,191,36,0.06);
}

/* Arrow + name row */
.judge-row {
  display: flex;
  align-items: center;
  gap: 11px;
}

.judge-name {
  font-family: 'Anton SC', sans-serif;
  font-size: 28px;
  color: rgba(255,255,255,0.92);
  letter-spacing: 0.07em;
  text-transform: uppercase;
  line-height: 1;
}

/* Direction arrows — pure CSS clip-path, guaranteed equal size */
.vote-arrow {
  display: inline-block;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  background: rgba(255,255,255,0.15);
  opacity: 0.2;
  transition: opacity 0.3s ease, background 0.3s ease, filter 0.3s ease;
}
.vote-arrow-left  { clip-path: polygon(100% 0%, 0% 50%, 100% 100%); }
.vote-arrow-right { clip-path: polygon(0% 0%, 100% 50%, 0% 100%); }

.arrow-lit-red  {
  opacity: 1;
  background: #ef4444;
  filter: drop-shadow(0 0 8px rgba(239,68,68,0.95));
}
.arrow-lit-blue {
  opacity: 1;
  background: #3b82f6;
  filter: drop-shadow(0 0 8px rgba(59,130,246,0.95));
}

/* Vote track bar */
.vote-track {
  width: 100%;
  height: 10px;
  background: rgba(255,255,255,0.07);
  border-radius: 9999px;
  overflow: hidden;
}
.vote-fill {
  height: 100%;
  border-radius: 9999px;
  transition: background 0.45s ease, box-shadow 0.45s ease, width 0.45s ease;
  width: 0%;
}
.fill-red   { width: 100%; background: linear-gradient(90deg, #dc2626, #ef4444); box-shadow: 0 0 14px rgba(239,68,68,0.9); }
.fill-blue  { width: 100%; background: linear-gradient(90deg, #1d4ed8, #3b82f6); box-shadow: 0 0 14px rgba(59,130,246,0.9); }
.fill-tie   { width: 100%; background: linear-gradient(90deg, #b45309, #fbbf24); box-shadow: 0 0 14px rgba(251,191,36,0.9); }
.fill-blank { width: 0%;   background: transparent; }

/* ══════════════════════════════════════════════════
   BATTLER PANELS
══════════════════════════════════════════════════ */
.battler-panel {
  position: absolute;
  bottom: 0;
  width: 38vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
}

.left-panel  { left: 0; }
.right-panel { right: 0; }

/* Gradient wash */
.panel-gradient {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
.red-gradient  { background: linear-gradient(to top, rgba(153,27,27,0.88) 0%, rgba(153,27,27,0.22) 38%, transparent 68%); }
.blue-gradient { background: linear-gradient(to top, rgba(29,78,216,0.88) 0%, rgba(29,78,216,0.22) 38%, transparent 68%); }

/* Vignette removed — inset box-shadows created hard vertical lines at panel edges */
.panel-vignette { display: none; }

/* Winner glow on panel */
.panel-winner .red-gradient  { background: linear-gradient(to top, rgba(185,28,28,0.95) 0%, rgba(153,27,27,0.35) 45%, transparent 72%); }
.panel-winner .blue-gradient { background: linear-gradient(to top, rgba(37,99,235,0.95) 0%, rgba(29,78,216,0.35) 45%, transparent 72%); }

/* Battler image */
.battler-img {
  position: relative;
  z-index: 10;
  width: 100%;
  max-height: 85vh;
  object-fit: contain;
  object-position: bottom;
  margin-bottom: 8vh;
}

/* Placeholder silhouette */
.battler-placeholder {
  position: relative;
  z-index: 10;
  width: 12vw;
  height: 30vh;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 8vh;
  border-radius: 16px;
  border: 1.5px dashed;
  animation: placeholderBreath 3s ease-in-out infinite;
}
.red-placeholder  { border-color: rgba(239,68,68,0.28); background: rgba(239,68,68,0.04); }
.blue-placeholder { border-color: rgba(59,130,246,0.28); background: rgba(59,130,246,0.04); }
.placeholder-svg  { width: 55%; height: auto; opacity: 0.5; }

/* ── Name bar ─────────────────────────────────────── */
.name-bar {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 1.6vh 1.5vw;
}

.name-text {
  font-family: 'Anton SC', sans-serif;
  font-size: 3vw;
  letter-spacing: 0.07em;
  text-transform: uppercase;
  color: #ffffff;
  line-height: 1;
}

/* Score badge pill */
.score-badge {
  font-family: 'Anton SC', sans-serif;
  font-size: 1.8vw;
  line-height: 1;
  color: rgba(255,255,255,0.92);
  background: rgba(255,255,255,0.15);
  border: 1px solid rgba(255,255,255,0.25);
  border-radius: 6px;
  padding: 2px 8px;
  letter-spacing: 0.05em;
}

.red-bar {
  background: linear-gradient(90deg, rgba(185,28,28,0.0) 0%, rgba(220,38,38,0.95) 20%, rgba(220,38,38,0.95) 80%, rgba(185,28,28,0.0) 100%);
  border-top: 1.5px solid rgba(255,120,120,0.35);
}
.blue-bar {
  background: linear-gradient(90deg, rgba(29,78,216,0.0) 0%, rgba(37,99,235,0.95) 20%, rgba(37,99,235,0.95) 80%, rgba(29,78,216,0.0) 100%);
  border-top: 1.5px solid rgba(120,160,255,0.35);
}

/* Winner name bar: gold shimmer sweep */
.bar-win-glow {
  animation: winnerShimmer 1.8s ease-in-out forwards;
}

/* ── VS badge ─────────────────────────────────────── */
.vs-badge {
  position: absolute;
  bottom: 2.5vh;
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
  display: flex;
  align-items: center;
  gap: 10px;
  opacity: 1;
  transition: opacity 0.7s ease;
}
.vs-hidden {
  opacity: 0;
  pointer-events: none;
}

.vs-text {
  font-family: 'Anton SC', sans-serif;
  font-size: 4.2vw;
  color: rgba(255,255,255,0.88);
  letter-spacing: 0.14em;
  text-shadow: 0 0 40px rgba(255,255,255,0.3);
  animation: vsPulse 2.8s ease-in-out infinite;
  line-height: 1;
}

.vs-deco {
  font-size: 1.4vw;
  color: rgba(255,255,255,0.25);
  animation: vsPulse 2.8s ease-in-out infinite reverse;
}

/* ══════════════════════════════════════════════════
   KEYFRAME ANIMATIONS
══════════════════════════════════════════════════ */

/* Battler entrances / exits — spring easing */
@keyframes slideRight {
  from { transform: translateX(0); }
  to   { transform: translateX(55vw); }
}
@keyframes slideRightIn {
  from { transform: translateX(72vw); }
  to   { transform: translateX(0); }
}
@keyframes slideLeft {
  from { transform: translateX(0); }
  to   { transform: translateX(-55vw); }
}
@keyframes slideLeftIn {
  from { transform: translateX(-72vw); }
  to   { transform: translateX(0); }
}
@keyframes slideUp {
  from { transform: translateY(0); }
  to   { transform: translateY(-200px); }
}
@keyframes slideDown {
  from { transform: translateY(-200px); }
  to   { transform: translateY(0); }
}
@keyframes fadeOut {
  from { opacity: 1; }
  to   { opacity: 0; }
}
@keyframes slideLeftOut {
  from { transform: translateX(55vw);  opacity: 1; }
  to   { transform: translateX(-100vw); opacity: 0; }
}
@keyframes slideRightOut {
  from { transform: translateX(-55vw); opacity: 1; }
  to   { transform: translateX(100vw); opacity: 0; }
}

/* Judge panel border rotation */
@keyframes borderRotate {
  0%   { filter: blur(1px) hue-rotate(0deg); }
  100% { filter: blur(1px) hue-rotate(360deg); }
}

/* Placeholder breathing */
@keyframes placeholderBreath {
  0%, 100% { opacity: 1; }
  50%       { opacity: 0.55; }
}

/* VS pulse */
@keyframes vsPulse {
  0%, 100% { text-shadow: 0 0 40px rgba(255,255,255,0.3); }
  50%       { text-shadow: 0 0 70px rgba(255,255,255,0.55), 0 0 120px rgba(255,255,255,0.15); }
}

/* Winner shimmer sweep on name bar */
@keyframes winnerShimmer {
  0%   { background-position: -200% 0; filter: brightness(1); }
  30%  { filter: brightness(1.45); }
  60%  { background-position: 200% 0; filter: brightness(1.2); }
  100% { filter: brightness(1); }
}

/* ── Animation classes ────────────────────────────── */
.slide-right     { animation: slideRight    0.95s cubic-bezier(0.16,1,0.3,1) forwards; }
.slide-left      { animation: slideLeft     0.95s cubic-bezier(0.16,1,0.3,1) forwards; }
.slide-up        { animation: slideUp       0.9s  cubic-bezier(0.16,1,0.3,1) forwards; }
.slide-down      { animation: slideDown     0.9s  cubic-bezier(0.34,1.3,0.64,1) forwards; }
.fade-out        { animation: fadeOut       0.95s ease forwards; }
.left-slide-in   { animation: slideLeftIn   0.95s cubic-bezier(0.34,1.2,0.64,1) forwards; }
.right-slide-in  { animation: slideRightIn  0.95s cubic-bezier(0.34,1.2,0.64,1) forwards; }
.slide-left-out  { animation: slideLeftOut  0.9s  cubic-bezier(0.55,0,1,0.45) forwards; }
.slide-right-out { animation: slideRightOut 0.9s  cubic-bezier(0.55,0,1,0.45) forwards; }

/* Combined */
.slide-right.fade-out { animation: slideRight 0.95s cubic-bezier(0.16,1,0.3,1) forwards, fadeOut 0.95s ease forwards; }
.slide-left.fade-out  { animation: slideLeft  0.95s cubic-bezier(0.16,1,0.3,1) forwards, fadeOut 0.95s ease forwards; }
</style>
