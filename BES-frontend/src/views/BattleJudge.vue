<script setup>
import leftHand from '@/assets/lefthand.png'
import rightHand from '@/assets/righthand.png'
import tie from '@/assets/no.png'
import ReusableDropdown from '@/components/ReusableDropdown.vue'
import { battleJudgeVote, getBattleJudges } from '@/utils/api'
import { computed, onMounted, ref } from 'vue'

const active = ref(null)
const confirmed = ref(null)
const battleJudges = ref([])
const selectedJudge = ref("")
const battleJudgesName = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  return judges.map(j => j.name)
})

async function handleClick(side) {
  if (active.value === side) {
    const judges = battleJudges.value?.judges ?? []
    const id = judges.filter(j => j.name === selectedJudge.value).map(j => j.id)
    await battleJudgeVote(id, side)
    active.value = null
    confirmed.value = side
    setTimeout(() => { confirmed.value = null }, 1800)
  } else {
    active.value = side
  }
}

onMounted(async () => {
  battleJudges.value = await getBattleJudges()
})
</script>

<template>
  <div class="judge-root">

    <!-- ── Header bar ─────────────────────────────── -->
    <header class="judge-header">
      <div class="header-brand">
        <span class="brand-pip pip-red"></span>
        <span class="brand-wordmark">BATTLE JUDGE</span>
        <span class="brand-pip pip-blue"></span>
      </div>
      <div class="selector-wrap">
        <span class="selector-eyebrow">VOTING AS</span>
        <div class="w-52">
          <ReusableDropdown
            v-model="selectedJudge"
            labelId=""
            :options="battleJudgesName"
            placeholder="Select judge…"
          />
        </div>
      </div>
    </header>

    <!-- ── Voting panels ──────────────────────────── -->
    <div class="panels-wrap">

      <!-- ── Left ── -->
      <button
        class="vote-panel left-panel"
        :class="{ 'is-active': active === 0, 'is-confirmed': confirmed === 0 }"
        @click="handleClick(0)"
      >
        <div class="panel-fill left-fill"></div>
        <div class="edge-accent left-accent"></div>
        <div class="panel-shimmer"></div>

        <div class="panel-inner">
          <p class="dir-label">LEFT</p>
          <div class="img-frame">
            <div class="img-halo red-halo"></div>
            <img :src="leftHand" class="panel-img left-img" alt="Left" />
          </div>
          <div class="feedback-row" :class="{ visible: active === 0 || confirmed === 0 }">
            <template v-if="confirmed === 0">
              <span class="check-mark">✓</span> VOTE LOCKED
            </template>
            <template v-else-if="active === 0">TAP AGAIN TO CONFIRM</template>
          </div>
        </div>

        <div class="pulse-ring left-ring"></div>
      </button>

      <!-- ── Tie ── -->
      <button
        class="vote-panel tie-panel"
        :class="{ 'is-active': active === -1, 'is-confirmed': confirmed === -1 }"
        @click="handleClick(-1)"
      >
        <div class="panel-fill tie-fill"></div>
        <div class="panel-shimmer"></div>

        <div class="panel-inner">
          <p class="dir-label">TIE</p>
          <div class="img-frame">
            <div class="img-halo neutral-halo"></div>
            <img :src="tie" class="panel-img" alt="Tie" />
          </div>
          <div class="feedback-row" :class="{ visible: active === -1 || confirmed === -1 }">
            <template v-if="confirmed === -1">
              <span class="check-mark">✓</span> VOTE LOCKED
            </template>
            <template v-else-if="active === -1">TAP AGAIN TO CONFIRM</template>
          </div>
        </div>

        <div class="pulse-ring neutral-ring"></div>
      </button>

      <!-- ── Right ── -->
      <button
        class="vote-panel right-panel"
        :class="{ 'is-active': active === 1, 'is-confirmed': confirmed === 1 }"
        @click="handleClick(1)"
      >
        <div class="panel-fill right-fill"></div>
        <div class="edge-accent right-accent"></div>
        <div class="panel-shimmer"></div>

        <div class="panel-inner">
          <p class="dir-label">RIGHT</p>
          <div class="img-frame">
            <div class="img-halo blue-halo"></div>
            <img :src="rightHand" class="panel-img right-img" alt="Right" />
          </div>
          <div class="feedback-row" :class="{ visible: active === 1 || confirmed === 1 }">
            <template v-if="confirmed === 1">
              <span class="check-mark">✓</span> VOTE LOCKED
            </template>
            <template v-else-if="active === 1">TAP AGAIN TO CONFIRM</template>
          </div>
        </div>

        <div class="pulse-ring blue-ring"></div>
      </button>

    </div>
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

/* ── Panels container ─────────────────────────────── */
.panels-wrap {
  flex: 1;
  display: flex;
  overflow: hidden;
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
