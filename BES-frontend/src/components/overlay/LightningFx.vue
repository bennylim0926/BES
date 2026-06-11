<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useOverlayParticles } from '@/utils/overlayParticles'
import { randomStrike, randomBranch, randomVert, VERT_BOLTS } from '@/utils/overlayBolts'

const props = defineProps({
  active:      { type: Boolean, default: true },
  voting:      { type: Boolean, default: false },
  strikeNonce: { type: Number, default: 0 },
  winnerNonce: { type: Number, default: 0 },
  winnerSide:  { type: Number, default: -1 },
})

const { particles, spawnAmbient } = useOverlayParticles()

onMounted(() => {
  spawnAmbient(14, () => ({
    x: Math.random() * 100,
    y: -5 + Math.random() * 10,
    size: 2 + Math.random() * 4,
    dur: 10 + Math.random() * 10,
    delay: -(Math.random() * 15),
    op: 0.10 + Math.random() * 0.22,
  }))
})

// ── Impact strike bolt ─────────────────────────────────────────────────────
const strikeBolt = ref(null)
const strikeBranch = ref(null)
let strikeTimer = null

watch(() => props.strikeNonce, (n) => {
  if (!n) return
  strikeBolt.value  = { d: randomStrike(), id: n }
  strikeBranch.value = { d: randomBranch(), id: n }
  clearTimeout(strikeTimer)
  strikeTimer = setTimeout(() => { strikeBolt.value = null; strikeBranch.value = null }, 480)
})

// ── Winner radiate bolts ───────────────────────────────────────────────────
const winnerBolts = ref(null)
let winnerTimer = null

watch(() => props.winnerNonce, (n) => {
  if (props.winnerSide !== 0 && props.winnerSide !== 1) return
  // Four vertical bolts rotated to radiate outward from winner center
  winnerBolts.value = {
    id: n,
    side: props.winnerSide,
    paths: [VERT_BOLTS[0], VERT_BOLTS[1], VERT_BOLTS[2], VERT_BOLTS[3]],
  }
  clearTimeout(winnerTimer)
  winnerTimer = setTimeout(() => { winnerBolts.value = null }, 1000)
})

// ── Voting crackle — two small vertical bolts near the VS ─────────────────
const crackleA = VERT_BOLTS[0]
const crackleB = VERT_BOLTS[2]

onBeforeUnmount(() => {
  clearTimeout(strikeTimer)
  clearTimeout(winnerTimer)
})
</script>

<template>
  <div class="lfx" aria-hidden="true">

    <!-- SVG filter defs for bolt glow bloom -->
    <svg width="0" height="0" style="position:absolute;overflow:visible">
      <defs>
        <filter id="lfx-glow" x="-50%" y="-50%" width="200%" height="200%">
          <feGaussianBlur in="SourceGraphic" stdDeviation="2.5" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
        <filter id="lfx-glow-strong" x="-80%" y="-80%" width="260%" height="260%">
          <feGaussianBlur in="SourceGraphic" stdDeviation="4" result="blur" />
          <feMerge><feMergeNode in="blur" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>
    </svg>

    <!-- ── Ambient atmosphere ───────────────────────────────────────────── -->
    <div class="lfx-ambient" :data-paused="!active">
      <!-- Soft vertical light shafts drifting slowly -->
      <div class="lfx-col lfx-col-a"></div>
      <div class="lfx-col lfx-col-b"></div>
      <div class="lfx-col lfx-col-c"></div>
      <!-- Cool dust bokeh particles falling slowly -->
      <div
        v-for="p in particles"
        :key="p.id"
        class="lfx-bokeh"
        :style="{
          left: p.x + '%',
          top: p.y + 'vh',
          width: p.size + 'px',
          height: p.size + 'px',
          opacity: p.op,
          animationDuration: p.dur + 's',
          animationDelay: p.delay + 's',
        }"
      ></div>
    </div>

    <!-- ── Voting ambient crackle — small arcs near the VS ─────────────── -->
    <svg v-if="voting" class="lfx-crackle" viewBox="0 0 100 100" preserveAspectRatio="none" overflow="visible">
      <!-- Outer glow layer -->
      <path :d="crackleA" pathLength="100" class="lfx-crackle-glow lfx-crackle-a" />
      <path :d="crackleB" pathLength="100" class="lfx-crackle-glow lfx-crackle-b" />
      <!-- Bright core layer -->
      <path :d="crackleA" pathLength="100" class="lfx-crackle-core lfx-crackle-a" />
      <path :d="crackleB" pathLength="100" class="lfx-crackle-core lfx-crackle-b" />
    </svg>

    <!-- ── Impact strike bolt + white flash ────────────────────────────── -->
    <template v-if="strikeBolt">
      <svg :key="strikeBolt.id" class="lfx-strike" viewBox="0 0 100 100" preserveAspectRatio="none" overflow="visible">
        <!-- Outer glow (thick, colored) -->
        <path :d="strikeBolt.d" pathLength="240" class="lfx-bolt-glow" stroke-width="7" />
        <!-- Mid layer (medium, near-white) -->
        <path :d="strikeBolt.d" pathLength="240" class="lfx-bolt-mid"  stroke-width="3" />
        <!-- Core (thin, pure white) -->
        <path :d="strikeBolt.d" pathLength="240" class="lfx-bolt-core" stroke-width="1" />
      </svg>
      <!-- Branch off main bolt -->
      <svg v-if="strikeBranch" :key="'b' + strikeBranch.id" class="lfx-branch" viewBox="0 0 100 100" preserveAspectRatio="none" overflow="visible">
        <path :d="strikeBranch.d" pathLength="120" class="lfx-bolt-glow" stroke-width="4" />
        <path :d="strikeBranch.d" pathLength="120" class="lfx-bolt-core" stroke-width="1" />
      </svg>
      <!-- Brightness flash overlay -->
      <div :key="'f' + strikeBolt.id" class="lfx-flash"></div>
    </template>

    <!-- ── Winner radiate bolts ─────────────────────────────────────────── -->
    <svg
      v-if="winnerBolts"
      :key="'w' + winnerBolts.id"
      class="lfx-winner"
      :class="winnerBolts.side === 0 ? 'lfx-winner-left' : 'lfx-winner-right'"
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      overflow="visible"
    >
      <g v-for="(d, i) in winnerBolts.paths" :key="i" :transform="`rotate(${[0, 90, 180, 270][i]} 50 50)`">
        <path :d="d" pathLength="200" class="lfx-bolt-glow" stroke-width="6"
              :style="{ animationDelay: `${i * 60}ms` }" />
        <path :d="d" pathLength="200" class="lfx-bolt-mid"  stroke-width="2.5"
              :style="{ animationDelay: `${i * 60}ms` }" />
        <path :d="d" pathLength="200" class="lfx-bolt-core" stroke-width="1"
              :style="{ animationDelay: `${i * 60}ms` }" />
      </g>
    </svg>

  </div>
</template>

<style scoped>
.lfx { position: absolute; inset: 0; pointer-events: none; z-index: 5; overflow: visible; }
.lfx-ambient { position: absolute; inset: 0; }
.lfx-ambient[data-paused="true"] * { animation-play-state: paused; }

/* ── Ambient light shafts ──────────────────────────────────────────────── */
.lfx-col {
  position: absolute; top: -10%; height: 120%; width: 120px;
  background: linear-gradient(to bottom,
    transparent,
    color-mix(in srgb, var(--overlay-accent, #00d4ff) 6%, transparent) 35%,
    color-mix(in srgb, var(--overlay-accent, #00d4ff) 10%, transparent) 55%,
    transparent 80%
  );
  animation: lfxColDrift 9s ease-in-out infinite alternate;
}
.lfx-col-a { left: 14%; }
.lfx-col-b { left: 48%; animation-delay: -3.5s; width: 160px; }
.lfx-col-c { left: 76%; animation-delay: -6s; }
@keyframes lfxColDrift {
  from { transform: translateX(-24px); opacity: 0.5; }
  to   { transform: translateX(24px);  opacity: 1; }
}

/* ── Bokeh dust particles ─────────────────────────────────────────────── */
.lfx-bokeh {
  position: absolute;
  border-radius: 50%;
  background: #dff6ff;
  filter: blur(0.5px);
  animation-name: lfxBokehFall;
  animation-timing-function: linear;
  animation-iteration-count: infinite;
}
@keyframes lfxBokehFall { to { transform: translateY(115vh); } }

/* ── Shared bolt draw-on animation ───────────────────────────────────── */
.lfx-bolt-glow,
.lfx-bolt-mid,
.lfx-bolt-core {
  fill: none;
  stroke-dasharray: 240;
  stroke-dashoffset: 240;
  stroke-linecap: round;
  stroke-linejoin: round;
  animation: lfxBoltDraw 120ms linear forwards, lfxBoltFade 280ms 130ms ease-out forwards;
}
/* Outer glow: thick, colored, semi-transparent */
.lfx-bolt-glow {
  stroke: var(--overlay-accent, #00d4ff);
  stroke-opacity: 0.55;
  filter: url(#lfx-glow-strong);
}
/* Mid: white, medium opacity */
.lfx-bolt-mid {
  stroke: #cff4ff;
  stroke-opacity: 0.75;
}
/* Core: pure white, solid */
.lfx-bolt-core {
  stroke: #ffffff;
  stroke-opacity: 1;
}
@keyframes lfxBoltDraw { to { stroke-dashoffset: 0; } }
@keyframes lfxBoltFade { to { opacity: 0; } }

/* ── Branch bolt (slightly delayed) ──────────────────────────────────── */
.lfx-branch {
  position: absolute; inset: 0; width: 100%; height: 100%;
  overflow: visible;
}
.lfx-branch .lfx-bolt-glow,
.lfx-branch .lfx-bolt-core {
  stroke-dasharray: 120;
  stroke-dashoffset: 120;
  animation: lfxBoltDraw 90ms 40ms linear forwards, lfxBoltFade 200ms 140ms ease-out forwards;
}

/* ── Flash overlay ────────────────────────────────────────────────────── */
.lfx-strike {
  position: absolute; inset: 0; width: 100%; height: 100%;
  overflow: visible;
}
.lfx-flash {
  position: absolute; inset: 0;
  background: radial-gradient(ellipse at center,
    rgba(180, 230, 255, 0.55) 0%,
    rgba(255, 255, 255, 0.22) 40%,
    transparent 70%
  );
  opacity: 0;
  animation: lfxFlash 220ms ease-out forwards;
}
@keyframes lfxFlash {
  0%  { opacity: 0; }
  15% { opacity: 1; }
  100%{ opacity: 0; }
}

/* ── Voting crackle — two small vertical arcs flanking the VS ─────────── */
.lfx-crackle {
  position: absolute;
  left: 42%; top: 28%;
  width: 16%; height: 44%;
  overflow: visible;
}
.lfx-crackle-glow {
  fill: none;
  stroke: var(--overlay-accent, #00d4ff);
  stroke-width: 4;
  stroke-opacity: 0;
  stroke-dasharray: 100;
  stroke-dashoffset: 0;
  filter: url(#lfx-glow);
}
.lfx-crackle-core {
  fill: none;
  stroke: #ffffff;
  stroke-width: 1;
  stroke-opacity: 0;
  stroke-dasharray: 100;
  stroke-dashoffset: 0;
}
/* Staggered opacity spikes to simulate crackle timing */
.lfx-crackle-a { animation: lfxCrackleA 1.9s linear infinite; }
.lfx-crackle-b { animation: lfxCrackleB 2.7s linear infinite; }
@keyframes lfxCrackleA {
  0%, 22%, 30%, 55%, 62%, 100% { stroke-opacity: 0; }
  24%, 28% { stroke-opacity: 1; }
  57%, 60% { stroke-opacity: 0.7; }
}
@keyframes lfxCrackleB {
  0%, 40%, 48%, 75%, 82%, 100% { stroke-opacity: 0; }
  42%, 46% { stroke-opacity: 0.9; }
  77%, 80% { stroke-opacity: 0.55; }
}

/* ── Winner radiate bolts ─────────────────────────────────────────────── */
.lfx-winner { position: absolute; top: 0; height: 100%; width: 50%; overflow: visible; }
.lfx-winner-left  { left: 0; }
.lfx-winner-right { left: 50%; }
.lfx-winner .lfx-bolt-glow,
.lfx-winner .lfx-bolt-mid,
.lfx-winner .lfx-bolt-core {
  stroke-dasharray: 200;
  stroke-dashoffset: 200;
  animation: lfxBoltDraw 180ms linear forwards, lfxBoltFade 480ms 220ms ease-out forwards;
}

/* ── Reduced motion ───────────────────────────────────────────────────── */
@media (prefers-reduced-motion: reduce) {
  .lfx { display: none !important; }
}
</style>
