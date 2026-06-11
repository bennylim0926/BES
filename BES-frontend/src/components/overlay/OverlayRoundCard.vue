<script setup>
import { computed } from 'vue'

const props = defineProps({
  eventName:   { type: String, default: '' },
  genreName:   { type: String, default: '' },
  roundLabel:  { type: String, default: '' },
  isFinal:     { type: Boolean, default: false },
  strikeDelay: { type: Number, default: 2200 },
})

const headline = computed(() => props.genreName || props.eventName)
const showEventLabel = computed(() => !!props.eventName && !!props.genreName)
const headlineChars = computed(() => headline.value.toUpperCase().split(''))
</script>

<template>
  <div
    class="round-card"
    :class="{ 'rc-final': isFinal }"
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
</style>
