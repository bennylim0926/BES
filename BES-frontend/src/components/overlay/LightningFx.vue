<script setup>
import { ref, watch, onMounted } from 'vue'
import { useOverlayParticles } from '@/utils/overlayParticles'
import { randomBolt, BOLT_PATHS } from '@/utils/overlayBolts'

const props = defineProps({
  active:      { type: Boolean, default: true },
  voting:      { type: Boolean, default: false },
  strikeNonce: { type: Number, default: 0 },
  winnerNonce: { type: Number, default: 0 },
  winnerSide:  { type: Number, default: -1 },
})

const { particles, spawnAmbient } = useOverlayParticles()

onMounted(() => {
  spawnAmbient(12, () => ({
    x: Math.random() * 100,
    size: 3 + Math.random() * 5,
    dur: 9 + Math.random() * 8,
    delay: -(Math.random() * 12),
    op: 0.12 + Math.random() * 0.25,
  }))
})

const strikeBolt = ref(null)
let strikeTimer = null
watch(() => props.strikeNonce, () => {
  strikeBolt.value = { d: randomBolt(), id: props.strikeNonce }
  clearTimeout(strikeTimer)
  strikeTimer = setTimeout(() => { strikeBolt.value = null }, 420)
})

const winnerBolts = ref(null)
let winnerTimer = null
watch(() => props.winnerNonce, () => {
  if (props.winnerSide !== 0 && props.winnerSide !== 1) return
  winnerBolts.value = {
    id: props.winnerNonce,
    side: props.winnerSide,
    paths: [BOLT_PATHS[2], BOLT_PATHS[3], BOLT_PATHS[2], BOLT_PATHS[3]],
  }
  clearTimeout(winnerTimer)
  winnerTimer = setTimeout(() => { winnerBolts.value = null }, 900)
})

const crackleA = BOLT_PATHS[0]
const crackleB = BOLT_PATHS[4]
</script>

<template>
  <div class="lfx" aria-hidden="true">
    <div class="lfx-ambient" :data-paused="!active">
      <div class="lfx-col lfx-col-a"></div>
      <div class="lfx-col lfx-col-b"></div>
      <div class="lfx-col lfx-col-c"></div>
      <div
        v-for="p in particles"
        :key="p.id"
        class="lfx-bokeh"
        :style="{
          left: p.x + '%',
          width: p.size + 'px',
          height: p.size + 'px',
          opacity: p.op,
          animationDuration: p.dur + 's',
          animationDelay: p.delay + 's',
        }"
      ></div>
    </div>

    <svg v-if="voting" class="lfx-crackle" viewBox="0 0 100 100" preserveAspectRatio="none">
      <path :d="crackleA" pathLength="240" class="lfx-crackle-bolt lfx-crackle-a" />
      <path :d="crackleB" pathLength="240" class="lfx-crackle-bolt lfx-crackle-b" />
    </svg>

    <template v-if="strikeBolt">
      <svg :key="strikeBolt.id" class="lfx-strike" viewBox="0 0 100 100" preserveAspectRatio="none">
        <path :d="strikeBolt.d" pathLength="240" class="lfx-strike-bolt" />
      </svg>
      <div :key="'f' + strikeBolt.id" class="lfx-flash"></div>
    </template>

    <svg
      v-if="winnerBolts"
      :key="'w' + winnerBolts.id"
      class="lfx-winner"
      :class="winnerBolts.side === 0 ? 'lfx-winner-left' : 'lfx-winner-right'"
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
    >
      <path
        v-for="(d, i) in winnerBolts.paths"
        :key="i"
        :d="d"
        pathLength="240"
        class="lfx-winner-bolt"
        :transform="`rotate(${[30, 100, 170, 240][i]} 50 50)`"
        :style="{ animationDelay: `${i * 70}ms` }"
      />
    </svg>
  </div>
</template>

<style scoped>
.lfx { position: absolute; inset: 0; pointer-events: none; z-index: 5; }
.lfx-ambient { position: absolute; inset: 0; }
.lfx-ambient[data-paused="true"] * { animation-play-state: paused; }

.lfx-col {
  position: absolute; top: -10%; height: 120%; width: 140px;
  background: linear-gradient(to bottom, transparent, var(--overlay-accent-subtle) 35%, transparent 80%);
  animation: lfxColDrift 8s ease-in-out infinite alternate;
}
.lfx-col-a { left: 12%; }
.lfx-col-b { left: 47%; animation-delay: -3s; }
.lfx-col-c { left: 78%; animation-delay: -5.5s; }
@keyframes lfxColDrift {
  from { transform: translateX(-30px); opacity: 0.5; }
  to   { transform: translateX(30px);  opacity: 1; }
}

.lfx-bokeh {
  position: absolute; top: -5vh; border-radius: 50%;
  background: #eafcff; filter: blur(1px);
  animation-name: lfxBokehFall; animation-timing-function: linear; animation-iteration-count: infinite;
}
@keyframes lfxBokehFall { to { transform: translateY(115vh); } }

.lfx-crackle { position: absolute; left: 38%; top: 30%; width: 24%; height: 40%; }
.lfx-crackle-bolt {
  fill: none; stroke: var(--overlay-accent); stroke-width: 1;
  filter: drop-shadow(0 0 4px var(--overlay-accent));
  opacity: 0;
}
.lfx-crackle-a { animation: lfxCrackleA 2.6s linear infinite; }
.lfx-crackle-b { animation: lfxCrackleB 3.4s linear infinite; }
@keyframes lfxCrackleA { 0%, 28%, 36%, 100% { opacity: 0; } 30%, 34% { opacity: 0.3; } 71%, 74% { opacity: 0.22; } 76% { opacity: 0; } }
@keyframes lfxCrackleB { 0%, 52%, 60%, 100% { opacity: 0; } 54%, 58% { opacity: 0.3; } 12%, 15% { opacity: 0.18; } 17% { opacity: 0; } }

.lfx-strike { position: absolute; inset: 0; width: 100%; height: 100%; }
.lfx-strike-bolt {
  fill: none; stroke: #eafcff; stroke-width: 1.6;
  filter: drop-shadow(0 0 8px var(--overlay-accent));
  stroke-dasharray: 240; stroke-dashoffset: 240;
  animation: lfxBoltDraw 140ms linear forwards, lfxBoltFade 240ms 150ms ease-out forwards;
}
.lfx-flash {
  position: absolute; inset: 0; background: #fff; opacity: 0;
  animation: lfxFlash 180ms ease-out forwards;
}
@keyframes lfxFlash { 0% { opacity: 0; } 25% { opacity: 0.16; } 100% { opacity: 0; } }
@keyframes lfxBoltDraw { to { stroke-dashoffset: 0; } }
@keyframes lfxBoltFade { to { opacity: 0; } }

.lfx-winner { position: absolute; top: 0; height: 100%; width: 50%; }
.lfx-winner-left  { left: 0; }
.lfx-winner-right { left: 50%; }
.lfx-winner-bolt {
  fill: none; stroke: #eafcff; stroke-width: 1.4;
  filter: drop-shadow(0 0 8px var(--overlay-accent));
  stroke-dasharray: 240; stroke-dashoffset: 240;
  animation: lfxBoltDraw 160ms linear forwards, lfxBoltFade 420ms 200ms ease-out forwards;
}

@media (prefers-reduced-motion: reduce) {
  .lfx { display: none !important; }
}
</style>
