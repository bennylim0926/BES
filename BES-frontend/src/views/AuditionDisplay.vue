<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { getAuditionDisplayState } from '@/utils/api'
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket'

const route = useRoute()
const eventName = ref(route.query.event || '')
const state = ref(null)
const client = ref(null)

// ── Local timer ticker (reconstructed from backend timerStartedAt + timerDuration) ──
const displayTimeLeft = ref(0)
let timerInterval = null

function startLocalTimer(startedAt, duration) {
  stopLocalTimer()
  const tick = () => {
    const elapsed = Math.floor((Date.now() - startedAt) / 1000)
    displayTimeLeft.value = Math.max(0, duration - elapsed)
    if (displayTimeLeft.value <= 0) stopLocalTimer()
  }
  tick()
  timerInterval = setInterval(tick, 250)
}

function stopLocalTimer() {
  if (timerInterval) { clearInterval(timerInterval); timerInterval = null }
}

// ── Computed ──────────────────────────────────────────────────────────────────
const isStandby  = computed(() => !state.value || state.value.standby)
const mode       = computed(() => state.value?.mode ?? 'SOLO')
const genreName  = computed(() => state.value?.genreName ?? '')
const eventLabel = computed(() => state.value?.eventName ?? eventName.value ?? '')
const roundLabel = computed(() => {
  if (!state.value || !state.value.totalRounds) return ''
  return `ROUND ${state.value.currentRound} / ${state.value.totalRounds}`
})
const currentSlots = computed(() => state.value?.currentSlots ?? [])
const nextSlots    = computed(() => state.value?.nextSlots ?? [])

const isNearEnd  = computed(() => displayTimeLeft.value <= 10 && displayTimeLeft.value > 0 && state.value?.timerRunning)
const isFinished = computed(() => displayTimeLeft.value <= 0 && state.value?.timerDuration > 0)
const timerLabel = computed(() => {
  if (!state.value?.timerDuration) return ''
  return String(displayTimeLeft.value)
})

// ── Lifecycle ─────────────────────────────────────────────────────────────────
function applyState(newState) {
  state.value = newState
  if (newState.timerRunning && newState.timerStartedAt && newState.timerDuration) {
    startLocalTimer(newState.timerStartedAt, newState.timerDuration)
  } else {
    stopLocalTimer()
    displayTimeLeft.value = newState.timerDuration ?? 0
  }
}

onMounted(async () => {
  if (!eventName.value) return

  const initial = await getAuditionDisplayState(eventName.value)
  if (initial) applyState(initial)

  client.value = createClient()
  subscribeToChannel(client.value, `/topic/audition/${eventName.value}/display`, (msg) => {
    applyState(msg)
  })
})

onUnmounted(() => {
  stopLocalTimer()
  if (client.value) deactivateClient(client.value)
})
</script>

<template>
  <div class="display-root">
    <!-- Scanlines overlay -->
    <div class="scanlines"></div>

    <!-- Color bleed -->
    <div class="color-bleed"></div>

    <!-- STANDBY: no state published yet -->
    <div v-if="isStandby" class="standby-container">
      <div class="corner-bar-tl"></div>
      <div class="corner-bar-bl"></div>
      <span class="type-label text-content-muted" style="font-size:14px;letter-spacing:0.22em">{{ eventLabel }}</span>
      <span class="type-stat text-accent" style="font-size: clamp(48px,8vw,80px);margin-top:12px">STANDBY</span>
      <span class="type-label text-content-muted" style="margin-top:8px">AWAITING AUDITION START</span>
    </div>

    <!-- ACTIVE display -->
    <div v-else class="active-container">

      <!-- Main area: event/genre header, round counter, number+name+timer -->
      <div class="main-area">
        <!-- Event name + genre stacked above everything -->
        <div class="event-header">
          <span class="event-header-name">{{ eventLabel }}</span>
          <span class="event-header-genre">{{ genreName }}</span>
        </div>

        <!-- Round counter -->
        <div class="section-rule mb-3">
          <span class="section-rule-label type-label text-content-muted">{{ roundLabel }}</span>
        </div>

        <!-- Current slot(s) + Timer side by side -->
        <div class="slot-timer-row">
          <!-- Left: number + name -->
          <div class="current-slots">
            <template v-for="(slot, sIdx) in currentSlots" :key="sIdx">
              <div v-if="slot.placeholder" class="slot-placeholder type-stat" style="font-size:clamp(50px,8vw,80px);color:rgba(245,158,11,0.3)">
                #{{ slot.auditionNumber }} — TBD
              </div>
              <div v-else class="slot-entry">
                <div class="type-stat audition-number">
                  #{{ slot.auditionNumber }}
                </div>
                <div class="type-body participant-name">
                  {{ slot.participantName }}
                </div>
                <div v-if="slot.memberNames?.length" class="type-label member-names">
                  {{ slot.memberNames.join(' · ') }}
                </div>
              </div>
              <!-- PAIR separator -->
              <div v-if="mode === 'PAIR' && sIdx === 0 && currentSlots.length > 1" class="pair-sep">
                <span class="type-stat">&amp;</span>
              </div>
            </template>
          </div>

          <!-- Right: Timer -->
          <div v-if="timerLabel" class="timer-display" :class="{ 'timer-near-end': isNearEnd, 'timer-finished': isFinished }">
            <div class="type-stat timer-number">{{ timerLabel }}</div>
          </div>
        </div>
      </div>

      <!-- UP NEXT (secondary area) -->
      <div v-if="nextSlots.length > 0" class="up-next-area">
        <div class="section-rule mb-2">
          <span class="section-rule-label type-label text-content-muted">UP NEXT</span>
        </div>
        <div class="next-slots">
          <template v-for="(slot, sIdx) in nextSlots" :key="sIdx">
            <div v-if="slot.placeholder" class="type-label" style="opacity:0.3;font-size:16px">
              #{{ slot.auditionNumber }} — TBD
            </div>
            <div v-else class="next-slot-entry">
              <span class="type-stat" style="font-size:24px;opacity:0.5">#{{ slot.auditionNumber }}</span>
              <span class="type-body" style="font-size:20px;opacity:0.4;margin-left:8px">{{ slot.participantName }}</span>
              <span v-if="slot.memberNames?.length" class="type-label" style="opacity:0.3;font-size:12px;margin-left:8px">{{ slot.memberNames.join(' · ') }}</span>
            </div>
            <span v-if="mode === 'PAIR' && sIdx === 0 && nextSlots.length > 1" style="opacity:0.2;margin:0 8px">&amp;</span>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ── Root layout ──────────────────────────────────────────────────────────── */
.display-root {
  position: fixed;
  inset: 0;
  background: #060818;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  font-family: 'Anton SC', sans-serif;
  text-transform: uppercase;
}

/* ── Scanlines ────────────────────────────────────────────────────────────── */
.scanlines {
  pointer-events: none;
  position: fixed;
  inset: 0;
  background: repeating-linear-gradient(
    0deg,
    transparent,
    transparent 2px,
    rgba(0,0,0,0.03) 2px,
    rgba(0,0,0,0.03) 4px
  );
  z-index: 10;
}

/* ── Color bleed ──────────────────────────────────────────────────────────── */
.color-bleed {
  pointer-events: none;
  position: fixed;
  inset: 0;
  background:
    radial-gradient(ellipse at 0% 100%, var(--accent-subtle, rgba(255,255,255,0.015)) 0%, transparent 60%),
    radial-gradient(ellipse at 100% 100%, var(--accent-subtle, rgba(255,255,255,0.015)) 0%, transparent 60%);
  z-index: 0;
}

/* ── Standby ──────────────────────────────────────────────────────────────── */
.standby-container {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 64px;
  clip-path: polygon(10px 0%, 100% 0%, calc(100% - 10px) 100%, 0% 100%);
  border: 1px solid rgba(255,255,255,0.06);
}

/* ── Active layout ────────────────────────────────────────────────────────── */
.active-container {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  padding: 40px;
}

.main-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

/* Event name + genre stacked, above round counter */
.event-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.event-header-name {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(14px, 1.6vw, 22px);
  letter-spacing: 0.28em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.35);
}
.event-header-genre {
  font-family: 'Anton SC', sans-serif;
  font-size: clamp(22px, 3vw, 42px);
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.7);
}

/* ── Current slots + timer side by side ──────────────────────────────────── */
.slot-timer-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: clamp(60px, 10vw, 160px);
}

.current-slots {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0;
}

.slot-entry {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.audition-number {
  font-size: clamp(80px, 14vw, 120px);
  line-height: 1;
  letter-spacing: 0.02em;
  text-shadow: 2px 2px 0 var(--accent-muted, rgba(255,255,255,0.15));
  color: var(--accent-color, #ffffff);
}

.participant-name {
  font-size: clamp(40px, 7vw, 80px);
  letter-spacing: 0.05em;
  color: #ffffff;
  margin-top: 4px;
}

.member-names {
  font-size: 16px;
  letter-spacing: 0.04em;
  color: rgba(255,255,255,0.35);
  text-transform: none;
  margin-top: 4px;
}

.pair-sep {
  margin: 8px 0;
  color: rgba(255,255,255,0.25);
  font-size: 28px;
}

.slot-placeholder {
  opacity: 0.4;
}

/* ── Timer ────────────────────────────────────────────────────────────────── */
.timer-display {
  flex-shrink: 0;
}

.timer-number {
  font-size: clamp(100px, 18vw, 220px);
  line-height: 1;
  letter-spacing: 0.02em;
  color: #ffffff;
  transition: color 0.3s ease;
}

.timer-near-end .timer-number {
  color: #ef4444;
  animation: pulse 0.5s ease-in-out infinite alternate;
}

.timer-finished .timer-number {
  color: rgba(255,255,255,0.2);
}

@keyframes pulse {
  from { opacity: 1; transform: scale(1); }
  to   { opacity: 0.7; transform: scale(1.03); }
}

/* ── UP NEXT ─────────────────────────────────────────────────────────────── */
.up-next-area {
  position: absolute;
  bottom: 60px;
  left: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.next-slots {
  display: flex;
  align-items: center;
}

.next-slot-entry {
  display: flex;
  align-items: baseline;
}

/* ── Section rule (defined locally since this is a standalone page) ───────── */
.section-rule {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  max-width: 500px;
}
.section-rule::after {
  content: '';
  flex: 1;
  height: 1px;
  background: rgba(255,255,255,0.08);
}
.section-rule-label {
  flex-shrink: 0;
}

/* ── Corner bars ─────────────────────────────────────────────────────────── */
.corner-bar-tl, .corner-bar-bl {
  position: absolute;
  background: var(--accent-color, #ffffff);
  opacity: 0.4;
}
.corner-bar-tl {
  top: 0; left: 0;
  width: 2px; height: 20px;
}
.corner-bar-bl {
  bottom: 0; left: 0;
  width: 2px; height: 20px;
}
</style>
