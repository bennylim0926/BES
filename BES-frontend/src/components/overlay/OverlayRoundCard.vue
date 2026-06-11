<script setup>
import { computed } from 'vue'
import { VERT_BOLTS } from '@/utils/overlayBolts'

const props = defineProps({
  eventName:   { type: String, default: '' },
  genreName:   { type: String, default: '' },
  roundLabel:  { type: String, default: '' },
  isFinal:     { type: Boolean, default: false },
  strikeDelay: { type: Number, default: 2200 },
  themeKey:    { type: String, default: 'impact' },
})

const headline = computed(() => props.genreName || props.eventName)
const showEventLabel = computed(() => !!props.eventName && !!props.genreName)
const headlineChars = computed(() => headline.value.toUpperCase().split(''))

// Four vertical bolts rotated to converge from corners toward center
const cornerBolts = [VERT_BOLTS[0], VERT_BOLTS[1], VERT_BOLTS[2], VERT_BOLTS[3]]
</script>

<template>
  <div
    class="round-card"
    :class="[`rc-${themeKey}`, { 'rc-final': isFinal }]"
    :style="{ '--rc-strike': strikeDelay + 'ms' }"
    aria-hidden="true"
  >
    <div class="rc-scrim"></div>
    <div v-if="showEventLabel" class="rc-event">{{ eventName }}</div>
    <div class="rc-headline">
      <span
        v-for="(ch, i) in headlineChars"
        :key="i"
        class="rc-ch"
        :style="{ animationDelay: `calc(400ms + ${i * 45}ms)` }"
      >{{ ch === ' ' ? ' ' : ch }}</span>
    </div>
    <div v-if="roundLabel" class="rc-round">{{ roundLabel }}</div>
    <!-- Strike-out beam — bridges title → battlers (spec t=2200) -->
    <div v-if="themeKey === 'lightning'" class="rc-beam"></div>
    <!-- Finals: bolts converge from corners + sustained crackle -->
    <svg
      v-if="themeKey === 'lightning' && isFinal"
      class="rc-corner-bolts"
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      overflow="visible"
    >
      <!-- Each corner bolt = 3 layered strokes (glow / mid / core) -->
      <g v-for="(d, i) in cornerBolts" :key="i"
         :transform="`rotate(${[45, 135, 225, 315][i]} 50 50)`"
         :style="{ '--bd': `${600 + i * 90}ms` }">
        <path :d="d" pathLength="200" class="rc-cbolt-glow" stroke-width="6" />
        <path :d="d" pathLength="200" class="rc-cbolt-mid"  stroke-width="2.5" />
        <path :d="d" pathLength="200" class="rc-cbolt-core" stroke-width="1" />
      </g>
    </svg>
  </div>
</template>

<style scoped>
.round-card {
  position: absolute; inset: 0; z-index: 70;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 14px;
  pointer-events: none;
  animation: rcExit 200ms ease-in var(--rc-strike) forwards;
}
.rc-scrim {
  position: absolute; inset: 0;
  background: radial-gradient(ellipse at center, rgba(0,0,0,0.45), transparent 75%);
}
.rc-event {
  position: relative;
  font-family: 'Anton SC', sans-serif;
  font-size: 14px; letter-spacing: 0.22em; text-transform: uppercase;
  color: rgba(255,255,255,0.85);
  text-shadow: 0 2px 8px rgba(0,0,0,0.7);
  opacity: 0;
  animation: rcFadeIn 350ms ease-out 150ms forwards;
}
.rc-headline {
  position: relative;
  display: flex; flex-wrap: wrap; justify-content: center;
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(48px, 7vw, 92px);
  letter-spacing: 0.06em; text-transform: uppercase; line-height: 1;
  color: #fff;
  text-shadow: 0 4px 18px rgba(0,0,0,0.8);
}
.rc-ch { opacity: 0; animation: rcChIn 320ms cubic-bezier(0.16, 1, 0.3, 1) forwards; }
.rc-round {
  position: relative;
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(20px, 2.6vw, 32px);
  letter-spacing: 0.18em; text-transform: uppercase;
  color: rgba(255,255,255,0.92);
  text-shadow: 0 2px 10px rgba(0,0,0,0.7);
  opacity: 0;
  animation: rcFadeIn 350ms ease-out 700ms forwards;
}
@keyframes rcFadeIn { to { opacity: 1; } }
@keyframes rcChIn {
  from { opacity: 0; transform: translateY(18px); filter: blur(6px); }
  to   { opacity: 1; transform: translateY(0);    filter: blur(0); }
}
@keyframes rcExit { to { opacity: 0; } }

/* ══ LIGHTNING treatment ══════════════════════════════════════ */
.rc-lightning .rc-headline { text-shadow: none; }
.rc-lightning .rc-ch {
  background: linear-gradient(180deg, #ffffff 0%, #dff6ff 38%, #8fd6ee 50%, #eafbff 62%, #9adcf5 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  filter: drop-shadow(0 0 10px var(--overlay-accent-muted));
  animation-name: rcChZap;
}
@keyframes rcChZap {
  0%   { opacity: 0; transform: translateY(14px) scale(1.18); filter: blur(8px) brightness(3); }
  45%  { opacity: 1; filter: blur(1px) brightness(2.2) drop-shadow(0 0 14px var(--overlay-accent)); }
  100% { opacity: 1; transform: translateY(0) scale(1); filter: blur(0) brightness(1) drop-shadow(0 0 10px var(--overlay-accent-muted)); }
}
.rc-lightning .rc-event { color: var(--overlay-accent); text-shadow: 0 0 12px var(--overlay-accent-muted); }
.rc-lightning .rc-round {
  color: rgba(255,255,255,0.95);
  text-shadow: 0 0 14px var(--overlay-accent-muted), 0 2px 10px rgba(0,0,0,0.7);
}
.rc-beam {
  position: absolute; left: 50%; top: 0; width: 4px; height: 100%;
  transform: translateX(-50%) scaleY(0); transform-origin: top;
  background: linear-gradient(to bottom, #ffffff, var(--overlay-accent) 60%, transparent);
  filter: drop-shadow(0 0 12px var(--overlay-accent));
  animation: rcBeamDrop 160ms cubic-bezier(0.7, 0, 1, 1) var(--rc-strike) forwards;
}
@keyframes rcBeamDrop { to { transform: translateX(-50%) scaleY(1); } }
.rc-corner-bolts { position: absolute; inset: 0; width: 100%; height: 100%; overflow: visible; }
/* Layered corner bolt strokes: glow / mid / core */
.rc-cbolt-glow, .rc-cbolt-mid, .rc-cbolt-core {
  fill: none;
  stroke-dasharray: 200; stroke-dashoffset: 200;
  stroke-linecap: round; stroke-linejoin: round;
  animation:
    rcBoltDraw 200ms linear var(--bd) forwards,
    rcBoltCrackle 700ms calc(var(--bd) + 260ms) steps(2, jump-none) 3;
}
.rc-cbolt-glow {
  stroke: var(--overlay-accent, #00d4ff);
  stroke-opacity: 0.5;
  filter: drop-shadow(0 0 6px var(--overlay-accent, #00d4ff));
}
.rc-cbolt-mid  { stroke: #cff4ff; stroke-opacity: 0.75; }
.rc-cbolt-core { stroke: #ffffff; stroke-opacity: 1; }
@keyframes rcBoltDraw    { to { stroke-dashoffset: 0; } }
@keyframes rcBoltCrackle { 0%, 100% { opacity: 0.85; } 50% { opacity: 0.25; } }

@media (prefers-reduced-motion: reduce) {
  .rc-beam, .rc-corner-bolts { display: none; }
  .rc-ch, .rc-event, .rc-round { animation-duration: 1ms; animation-delay: 0ms; }
}
</style>
