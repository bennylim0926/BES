<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import Timer from './Timer.vue';
import { postAuditionDisplayState, getAuditionDisplayState } from '@/utils/api';
import { buildPairs, getPositionDisplay } from '@/utils/auditionPairs'

const props = defineProps({
  participants: { type: Array, required: true },
  mode:         { type: String, default: 'SOLO' },
  eventName:    { type: String, default: '' },
  categoryName: { type: String, default: '' },
  roundLabel:   { type: String, default: null },
  numberColor:  { type: String, default: null },
  pairSubMode:  { type: String, default: 'SHOWCASE' },
});

const timerRef = ref(null)
const timerVisible = ref(true)

// Sticky baseline duration the emcee picked for this (event, category).
// Survives round changes, timer reset, and timer expiry so both the
// emcee's Timer and the OBS audition display park on it instead of going
// blank. Re-hydrated from backend state on mount; reset to null when the
// component re-mounts on category switch.
const baselineDuration = ref(null)

function resetTimer() {
  // External "go away" — used when switching categories. Wipe both the
  // running timer and the sticky baseline so the next category starts clean.
  timerRef.value?.clearAll?.()
  baselineDuration.value = null
  timerRemaining.value = null
  timerVisible.value = false
}

defineExpose({ resetTimer })

const currentRound = ref(1)

const rounds = computed(() => {
  if (props.mode !== 'PAIR') {
    return [...props.participants]
      .sort((a, b) => a.auditionNumber - b.auditionNumber)
      .map(p => [p])
  }
  return buildPairs(props.participants, props.pairSubMode)
})

// Missing numbers between the current round and the next round.
// Guard against all-placeholder rounds: Math.max/min on an empty array
// returns -Infinity/Infinity, which would turn the for-loop below into
// an infinite loop (i = -Infinity, -Infinity + 1 === -Infinity in JS)
// and freeze the tab. Easy to trigger in PAIR mode when both slots of a
// round are unregistered (e.g. #51 + #52 both "Not Registered").
const gapAfterCurrent = computed(() => {
  if (props.pairSubMode === 'BATTLE') return []
  const current = currentRoundSlots.value
  if (!current.length) return []
  const nextRound = rounds.value[currentRound.value] // next round (0-indexed = currentRound)
  if (!nextRound || !nextRound.length) return []
  const currentReals = current.filter(s => !s._placeholder).map(s => s.auditionNumber)
  const nextReals = nextRound.filter(s => !s._placeholder).map(s => s.auditionNumber)
  if (!currentReals.length || !nextReals.length) return []
  const lastCurrent = Math.max(...currentReals)
  const firstNext = Math.min(...nextReals)
  if (firstNext - lastCurrent <= 1) return []
  const missing = []
  for (let i = lastCurrent + 1; i < firstNext; i++) missing.push(i)
  return missing
})

const totalRounds        = computed(() => rounds.value.length)
const currentRoundSlots  = computed(() => rounds.value[currentRound.value - 1] ?? [])

const lastTimerState = ref({})

// ── Timer display state (drives NOW card header progress bar) ─────────────────
const timerRemaining = ref(null)

const headerDisplayTime = computed(() => {
  if (lastTimerState.value.running && timerRemaining.value !== null) return String(timerRemaining.value)
  if (baselineDuration.value) return String(baselineDuration.value)
  return null
})

const headerProgressPct = computed(() => {
  if (lastTimerState.value.running && lastTimerState.value.duration) {
    return Math.max(0, ((timerRemaining.value ?? 0) / lastTimerState.value.duration) * 100)
  }
  return baselineDuration.value ? 100 : 0
})

const headerTimerNearEnd = computed(() =>
  lastTimerState.value.running && timerRemaining.value !== null && timerRemaining.value <= 10 && timerRemaining.value > 0
)

function buildStatePayload(timerState = {}) {
  const current = currentRoundSlots.value.map(slot => ({
    auditionNumber: slot.auditionNumber,
    participantName: slot._placeholder ? null : slot.participantName,
    memberNames: slot.memberNames ?? [],
    placeholder: !!slot._placeholder
  }))

  const nextRoundIdx = currentRound.value
  const nextSlotsRaw = rounds.value[nextRoundIdx] ?? []
  const next = nextSlotsRaw.map(slot => ({
    auditionNumber: slot.auditionNumber,
    participantName: slot._placeholder ? null : slot.participantName,
    memberNames: slot.memberNames ?? [],
    placeholder: !!slot._placeholder
  }))

  return {
    eventName: props.eventName,
    categoryName: props.categoryName,
    mode: props.mode,
    currentRound: currentRound.value,
    totalRounds: totalRounds.value,
    currentSlots: current,
    nextSlots: next,
    timerStartedAt: timerState.startedAt ?? null,
    timerDuration: timerState.duration ?? null,
    timerRunning: timerState.running ?? false,
    baselineDuration: baselineDuration.value,
    roundLabel: props.roundLabel ?? null,
    numberColor: props.numberColor ?? null,
    pairSubMode: props.pairSubMode,
  }
}

function publishState(timerState = {}) {
  if (!props.eventName) return
  postAuditionDisplayState(buildStatePayload(timerState))
}

function onTimerStarted(detail) {
  baselineDuration.value = detail.duration
  timerRemaining.value = detail.duration
  lastTimerState.value = { startedAt: detail.startedAt, duration: detail.duration, running: true }
  publishState(lastTimerState.value)
}

function onTimerStopped() {
  timerRemaining.value = null
  lastTimerState.value = { startedAt: null, duration: null, running: false }
  publishState(lastTimerState.value)
}

function onTimerTick(detail) {
  timerRemaining.value = detail.remaining
  lastTimerState.value = {
    startedAt: detail.running ? (lastTimerState.value.startedAt ?? null) : null,
    duration: detail.total,
    running: detail.running
  }
}

// Publish state to display whenever the round changes (include timer state if running)
watch(currentRound, () => {
  publishState(lastTimerState.value)
})

onMounted(async () => {
  if (!props.eventName) return
  const state = await getAuditionDisplayState(props.eventName, props.categoryName)
  // Restore baseline first so Timer can park on it even if no live timer is running.
  if (state && state.categoryName === props.categoryName && state.baselineDuration) {
    baselineDuration.value = state.baselineDuration
  }
  // Timer recovery: only resume if the saved state is for this same category
  if (state && !state.standby && state.categoryName === props.categoryName &&
      state.timerRunning && state.timerStartedAt && state.timerDuration) {
    const elapsed = Math.floor((Date.now() - state.timerStartedAt) / 1000)
    const remaining = Math.max(0, state.timerDuration - elapsed)
    if (remaining > 0) {
      await new Promise(r => setTimeout(r, 100))
      timerRef.value?.resumeTimer(remaining, state.timerDuration)
      timerRemaining.value = remaining
      lastTimerState.value = { startedAt: state.timerStartedAt, duration: state.timerDuration, running: true }
    }
  }
  // Always publish on mount so switching category updates the display immediately
  publishState(lastTimerState.value)
})

// Queue is scrollable — show ALL upcoming rounds, reversed so "Up Next"
// sits at the bottom near the NOW card. The emcee can swipe up to peek
// further ahead without changing currentRound (no broadcast side-effects).
const visibleRounds = computed(() =>
  rounds.value.slice(currentRound.value).map((slots, i) => ({
    slots,
    roundNumber: currentRound.value + 1 + i,
  })).reverse()
)

const touchStartX = ref(0)
const dragOffset  = ref(0)
const isDragging  = ref(false)
const direction   = ref('left')

const goNext = () => { if (currentRound.value < totalRounds.value)  { direction.value = 'left';  currentRound.value++; timerRef.value?.reset() } }
const goPrev = () => { if (currentRound.value > 1)                  { direction.value = 'right'; currentRound.value--; timerRef.value?.reset() } }

const onPointerDown = (e) => {
  e.currentTarget.setPointerCapture(e.pointerId)
  touchStartX.value = e.clientX
  isDragging.value  = true
  dragOffset.value  = 0
}

const onPointerMove = (e) => {
  if (!isDragging.value) return
  dragOffset.value = e.clientX - touchStartX.value
}

const onPointerUp = () => {
  if (!isDragging.value) return
  isDragging.value = false
  if      (dragOffset.value < -60) goNext()
  else if (dragOffset.value >  60) goPrev()
  dragOffset.value = 0
}

const cardStyle = computed(() => {
  if (!isDragging.value) return {}
  const x = dragOffset.value
  return { transform: `translate3d(${x}px,0,0) rotate(${x * 0.03}deg)`, opacity: Math.max(0.4, 1 - Math.abs(x) / 250), transition: 'none', cursor: 'grabbing', willChange: 'transform' }
})

const swipeHint = computed(() => {
  if (!isDragging.value) return null
  if (dragOffset.value < -30) return 'left'
  if (dragOffset.value > 30)  return 'right'
  return null
})
</script>

<template>
  <div class="emcee-root w-full flex flex-col h-full touch-manipulation" style="background: #111111; overflow-y: auto;">

    <!-- ── Queue ──────────────────────────────────────────────────────────────
         flex-col-reverse: stack from bottom (near NOW) upward.
         Scrollable: emcee can swipe up to peek further ahead without
         touching currentRound. Opacity fades only for the nearest few so
         the "Up Next" emphasis is preserved; deeper rounds stay readable.
    ──────────────────────────────────────────────────────────────────────── -->
    <div
      class="emcee-queue flex-1 px-3 pt-2 pb-1"
      style="display: flex; flex-direction: column-reverse; justify-content: flex-start; overflow-y: auto; scrollbar-width: none; overscroll-behavior: contain;"
    >
      <div class="flex flex-col gap-1.5">
        <div
          v-for="({ slots, roundNumber }, uIdx) in visibleRounds"
          :key="roundNumber"
          class="queue-item para-chip-sm overflow-hidden flex-shrink-0"
          :class="uIdx === visibleRounds.length - 1 ? 'border-white/15 bg-white/5' : 'border-white/5 bg-transparent'"
          style="opacity: 0.45"
        >
          <div class="flex items-center justify-between px-2 py-1">
            <span class="type-label text-content-muted">
              Round {{ roundNumber }}
            </span>
            <span
              v-if="uIdx === visibleRounds.length - 1"
              class="badge-neutral type-label"
            >Up Next</span>
          </div>
          <div class="px-2 pb-1.5">
            <template v-for="(slot, sIdx) in slots" :key="sIdx">
              <div v-if="slot._placeholder" class="text-xs text-amber-400/40 italic">
                #{{ slot.auditionNumber }} — Not Registered
              </div>
              <div v-else class="flex items-start gap-2">
                <span class="type-stat text-[18px] flex-shrink-0 text-content-muted">#{{ slot.auditionNumber }}</span>
                <div class="min-w-0 flex-1">
                  <span class="type-name block text-content-muted" style="font-size:18px">{{ slot.participantName }}</span>
                  <span v-if="slot.memberNames?.length" class="type-prose text-content-muted block" style="font-size:14px;">{{ slot.memberNames.join(' · ') }}</span>
                </div>
                <!-- Position badge right-aligned (PAIR mode only) -->
                <span
                  v-if="mode === 'PAIR'"
                  class="type-label px-1 py-0.5 flex-shrink-0 ml-auto self-center text-amber-300"
                  style="clip-path: polygon(3px 0%, 100% 0%, calc(100% - 3px) 100%, 0% 100%); font-size: 9px; border: 1px solid currentColor; opacity: 0.85; text-transform: none;"
                >{{ getPositionDisplay(sIdx, slots.length) }}</span>
                <span v-if="mode === 'PAIR' && sIdx === 0 && pairSubMode !== 'BATTLE'" class="text-white/20 text-xs">&amp;</span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- ── NOW card ──────────────────────────────────────────────────────── -->
    <div class="emcee-now px-3 pb-2">
      <div class="relative overflow-hidden">
        <div
          class="absolute inset-y-0 left-0 w-10 flex items-center justify-center z-10 pointer-events-none transition-opacity duration-150"
          :class="swipeHint === 'right' && currentRound > 1 ? 'opacity-100' : 'opacity-0'"
        >
          <i class="pi pi-chevron-left text-lg text-white/40"></i>
        </div>
        <div
          class="absolute inset-y-0 right-0 w-10 flex items-center justify-center z-10 pointer-events-none transition-opacity duration-150"
          :class="swipeHint === 'left' && currentRound < totalRounds ? 'opacity-100' : 'opacity-0'"
        >
          <i class="pi pi-chevron-right text-lg text-white/40"></i>
        </div>

        <Transition :name="direction === 'left' ? 'card-left' : 'card-right'" mode="out-in">
          <div
            :key="currentRound"
            :style="cardStyle"
            @pointerdown="onPointerDown"
            @pointermove="onPointerMove"
            @pointerup="onPointerUp"
            @pointercancel="onPointerUp"
            class="card-hover p-0 relative select-none"
            style="box-shadow: 0 0 0 1px rgba(255,255,255,0.06), 0 8px 32px rgba(0,0,0,0.6); touch-action: none;"
          >
            <div class="corner-bar-tl"></div>
            <div
              class="now-card-header"
              :class="{ 'now-card-header--near-end': headerTimerNearEnd, 'now-card-header--running': lastTimerState.running }"
              :style="{ '--progress': headerProgressPct + '%' }"
            >
              <div class="flex items-center gap-2 z-10 relative">
                <span class="glow-dot"></span>
                <span class="type-label text-content-muted">Now on Stage</span>
              </div>
              <span v-if="headerDisplayTime" class="now-card-timer z-10 relative" :class="{ 'now-card-timer--near-end': headerTimerNearEnd }">
                <i class="pi pi-clock mr-1" style="font-size:0.75em;opacity:0.7;"></i>{{ headerDisplayTime }}
              </span>
            </div>
            <div class="p-3">
              <template v-for="(slot, sIdx) in currentRoundSlots" :key="sIdx">
                <div v-if="slot._placeholder" class="text-amber-400/60 text-sm italic py-2">
                  #{{ slot.auditionNumber }} — Not Registered
                </div>
                <div v-else>
                  <div v-if="mode === 'PAIR' && sIdx > 0" class="text-white/20 text-sm my-1 pl-1">&amp;</div>
                  <div class="flex items-baseline gap-2">
                    <span class="type-stat" style="font-size: 2rem;">#{{ slot.auditionNumber }}</span>
                    <span class="type-name text-content-primary" style="font-size: clamp(1.5rem, 6vw, 2.8rem);">{{ slot.participantName }}</span>
                    <!-- Position badge (PAIR mode only) — right-aligned -->
                    <span
                      v-if="mode === 'PAIR'"
                      class="type-label px-1.5 py-0.5 flex-shrink-0 ml-auto text-amber-300"
                      style="clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%); font-size: 10px; border: 1px solid currentColor; opacity: 0.85; text-transform: none;"
                    >{{ getPositionDisplay(sIdx, currentRoundSlots.length) }}</span>
                  </div>
                  <div v-if="slot.memberNames?.length" class="type-prose text-content-muted mt-0.5 pl-1" style="font-size:14px;">{{ slot.memberNames.join(' · ') }}</div>
                  <div v-if="slot.judgeName" class="text-xs text-white/25 mt-0.5 pl-1">{{ slot.judgeName }}</div>
                </div>
              </template>
              <div
                v-if="gapAfterCurrent.length > 0"
                class="mt-2 flex items-center gap-2"
                style="border-left:3px solid rgba(245,158,11,0.5); padding-left:10px;"
              >
                <span class="type-prose-sm" style="color:rgba(245,158,11,0.6);">
                  Number{{ gapAfterCurrent.length > 1 ? 's' : '' }} not taken:
                  <span style="color:rgba(245,158,11,0.85);">{{ gapAfterCurrent.join(', ') }}</span>
                </span>
              </div>
            </div>
            <div class="flex items-stretch gap-1.5 px-4 pb-1.5">
              <button
                @pointerdown.stop
                @click="goPrev"
                :disabled="currentRound <= 1"
                class="nav-btn type-label"
              >‹ PREV</button>
              <button
                @pointerdown.stop
                @click="goNext"
                :disabled="currentRound >= totalRounds"
                class="nav-btn type-label"
              >NEXT ›</button>
            </div>
          </div>
        </Transition>
      </div>
    </div>

    <!-- ── Timer at bottom (thumb reach) ── -->
    <Transition name="timer-slide">
      <div v-if="timerVisible" class="emcee-timer px-3 pb-3 pt-2">
        <Timer ref="timerRef" :baseline-duration="baselineDuration" @started="onTimerStarted" @stopped="onTimerStopped" @tick="onTimerTick" />
      </div>
    </Transition>

  </div>
</template>

<style scoped>
/* ── Timer slide-up ──────────────────────────────────────────────────── */
.timer-slide-leave-active {
  transition: transform 0.25s ease-in, opacity 0.2s ease-in;
}
.timer-slide-leave-from {
  transform: translateY(0);
  opacity: 1;
}
.timer-slide-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}

/* ── Current card ─────────────────────────────────────────────────────── */
.card-left-enter-active, .card-left-leave-active,
.card-right-enter-active, .card-right-leave-active {
  transition: transform 0.18s cubic-bezier(0.2,0,0.2,1), opacity 0.16s ease;
}
.card-left-enter-active,
.card-right-enter-active { will-change: transform, opacity; }
.card-left-enter-from  { transform: translate3d(32px,0,0);  opacity: 0; }
.card-left-enter-to    { transform: translate3d(0,0,0);     opacity: 1; }
.card-left-leave-from  { transform: translate3d(0,0,0);     opacity: 1; }
.card-left-leave-to    { transform: translate3d(-32px,0,0); opacity: 0; }
.card-right-enter-from { transform: translate3d(-32px,0,0); opacity: 0; }
.card-right-enter-to   { transform: translate3d(0,0,0);     opacity: 1; }
.card-right-leave-from { transform: translate3d(0,0,0);     opacity: 1; }
.card-right-leave-to   { transform: translate3d(32px,0,0);  opacity: 0; }

/* ── Queue item transitions ────────────────────────────────────────── */
.queue-item {
  transition: opacity 0.3s ease;
}

/* Hide scrollbar on the scrollable queue (still scrolls via touch/wheel) */
.emcee-queue::-webkit-scrollbar { display: none; }

/* ── Landscape: timer left column, queue+card right column ──────────── */
@media (orientation: landscape) {
  .emcee-root {
    flex-direction: row !important;
    height: 100%;
    overflow: hidden;
  }
  .emcee-timer {
    width: 160px;
    flex: none;
    border-top: none !important;
    border-right-width: 1px;
    border-right-style: solid;
    border-color: rgba(255,255,255,0.05);
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
    padding-bottom: 12px;
    order: -1;
  }
  .emcee-queue {
    flex: 1;
    border-right: none;
  }
  .emcee-now {
    flex: none;
  }
}

/* ── NOW card header — doubles as a progress bar ────────────────────── */
.now-card-header {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  overflow: hidden;
}
/* fill layer — drains left to right as time runs out */
.now-card-header::before {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(255,255,255,0.07);
  width: var(--progress, 0%);
  transition: width 0.25s linear, background 0.3s ease;
  pointer-events: none;
}
.now-card-header--near-end::before {
  background: rgba(239, 68, 68, 0.18);
}

/* Timer number inside the header */
.now-card-timer {
  font-family: 'Oswald', sans-serif;
  font-size: clamp(1.4rem, 4vw, 2rem);
  line-height: 1;
  letter-spacing: 0.02em;
  font-variant-numeric: tabular-nums;
  color: rgba(255,255,255,0.85);
  transition: color 0.3s ease;
}
.now-card-timer--near-end {
  color: #ef4444;
}

/* ── Nav buttons ────────────────────────────────────────────────────── */
.nav-btn {
  flex: 1;
  min-height: 48px;
  clip-path: polygon(5px 0%, 100% 0%, calc(100% - 5px) 100%, 0% 100%);
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  color: var(--accent-color);
  cursor: pointer;
  transition: background 0.15s ease, opacity 0.15s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-btn:active:not(:disabled) {
  background: var(--accent-muted);
}
.nav-btn:disabled {
  opacity: 0.2;
  pointer-events: none;
}
</style>
