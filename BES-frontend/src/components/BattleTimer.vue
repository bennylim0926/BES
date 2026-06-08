<script setup>
/**
 * BattleTimer.vue
 *
 * A countdown timer for the live battle flow.
 * The Emcee selects a preset duration and starts the timer during LOCKED.
 * The timer runs independently — it does NOT auto-open voting.
 * Voting should be opened manually by the Emcee only after BOTH battlers
 * have finished their performance.
 * At 0 seconds, a burst animation plays and the timer resets.
 * Timer state is broadcast via STOMP every second for the overlay.
 *
 * State machine:
 *   IDLE    → (click START) → RUNNING
 *   RUNNING → (timeLeft=0)  → FINISHED (burst anim) → IDLE
 *   RUNNING → (click RESET) → IDLE
 */
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'

const props = defineProps({
  phase:         { type: String, default: 'IDLE' },
  stompClient:   { type: Object, default: null },
  presets:       { type: Array,  default: () => [30, 45, 60, 90] },
  recoveryState: { type: Object, default: null },  // { running, timeLeft, totalDuration }
  eventName:     { type: String, default: '' }
})

// ── Reactive state ──
const timerState        = ref('IDLE')  // 'IDLE' | 'RUNNING' | 'FINISHED'
const selectedDuration  = ref(30)      // currently highlighted preset (seconds)
const totalDuration     = ref(0)
const timeLeft          = ref(0)
const autoUnlocked      = ref(false)   // guard: true once timer has fired its 10s warning

let intervalId = null
let finishTimeoutId = null

// ── Computed ──
const isRunning    = computed(() => timerState.value === 'RUNNING')
const isIdle       = computed(() => timerState.value === 'IDLE')
const isFinished   = computed(() => timerState.value === 'FINISHED')
const isLockedPhase = computed(() => props.phase === 'LOCKED')
const isWarning    = computed(() => isRunning.value && timeLeft.value <= 10)
const isMuted      = computed(() => !isRunning.value && !isFinished.value && !isLockedPhase.value)

const displayTime = computed(() => {
  const mins = Math.floor(timeLeft.value / 60)
  const secs = timeLeft.value % 60
  return `${mins}:${String(secs).padStart(2, '0')}`
})

const progressPct = computed(() => {
  if (totalDuration.value === 0) return 0
  return (timeLeft.value / totalDuration.value) * 100
})

// ── Timer methods ──

function tapPreset(seconds) {
  // Tapping a preset always (re)starts the timer — no separate Start/Reset buttons.
  if (isRunning.value) {
    // Reset current timer first, then restart with new duration
    clearInterval(intervalId)
    intervalId = null
    clearTimeout(finishTimeoutId)
    finishTimeoutId = null
  }
  selectedDuration.value = seconds
  timerState.value = 'RUNNING'
  totalDuration.value = seconds
  timeLeft.value = seconds
  autoUnlocked.value = false
  publishState()
  clearInterval(intervalId)
  intervalId = setInterval(() => {
    timeLeft.value--
    publishState()
    if (timeLeft.value === 10 && !autoUnlocked.value) {
      autoUnlocked.value = true
    }
    if (timeLeft.value <= 0) {
      finishTimer()
    }
  }, 1000)
}

function finishTimer() {
  clearInterval(intervalId)
  intervalId = null
  timerState.value = 'FINISHED'
  timeLeft.value = 0
  publishState()                        // broadcast running=false, timeLeft=0 → overlay plays burst

  // After burst animation (~800ms), go back to IDLE
  clearTimeout(finishTimeoutId)
  finishTimeoutId = setTimeout(() => {
    timerState.value = 'IDLE'
    totalDuration.value = 0
    publishState()
  }, 800)
}

function resetTimer() {
  clearInterval(intervalId)
  intervalId = null
  clearTimeout(finishTimeoutId)
  finishTimeoutId = null
  timerState.value = 'IDLE'
  totalDuration.value = 0
  timeLeft.value = 0
  publishState()
}

defineExpose({ resetTimer })

function recoverFromState(state) {
  if (!state || !state.running) return
  if (isRunning.value) return  // already running, don't interrupt
  const left = state.timeLeft ?? 0
  if (left <= 0) return
  timerState.value = 'RUNNING'
  totalDuration.value = state.totalDuration || left
  timeLeft.value = left
  autoUnlocked.value = false

  publishState()

  clearInterval(intervalId)
  intervalId = setInterval(() => {
    timeLeft.value--
    publishState()
    if (timeLeft.value === 10 && !autoUnlocked.value) {
      autoUnlocked.value = true
    }
    if (timeLeft.value <= 0) {
      finishTimer()
    }
  }, 1000)
}

// Watch for timer state from backend snapshot (page-refresh recovery via REST/WS state)
watch(() => props.recoveryState, (state) => {
  if (state) recoverFromState(state)
}, { immediate: true })

// Direct subscription to /topic/battle/timer for page-refresh recovery.
// When the backend serves state via REST, it also rebroadcasts timer.
// The subscription may be set up after the broadcast — so we also
// recover from props.recoveryState when the STOMP connection is ready.
let timerSub = null
let wsRecoveryAttempted = false
onMounted(() => {
  if (!props.stompClient) return
  const doSubscribe = () => {
    if (timerSub) return
    const timerTopic = props.eventName
      ? `/topic/battle/${props.eventName}/timer`
      : '/topic/battle/timer'
    timerSub = props.stompClient.subscribe(timerTopic, (raw) => {
      try {
        const msg = JSON.parse(raw.body)
        if (msg && msg.running) { wsRecoveryAttempted = true; recoverFromState(msg) }
      } catch (_) { /* ignore malformed WS frames */ }
    })
    // If the prop was already set before WS connected, recover now
    if (!wsRecoveryAttempted && props.recoveryState) {
      wsRecoveryAttempted = true
      recoverFromState(props.recoveryState)
    }
  }
  if (props.stompClient.connected) {
    doSubscribe()
  } else {
    const prev = props.stompClient.onConnect
    // eslint-disable-next-line vue/no-mutating-props
    props.stompClient.onConnect = () => {
      if (prev) prev()
      doSubscribe()
    }
  }
})

onBeforeUnmount(() => {
  if (timerSub) { timerSub.unsubscribe(); timerSub = null }
})

// Also check on mount in case prop was already set before watcher registered
if (props.recoveryState) recoverFromState(props.recoveryState)

function publishState() {
  // Use REST API instead of STOMP — more reliable for writes.
  // Backend stores the payload and broadcasts to /topic/battle/timer for overlay + recovery.
  try {
    const url = props.eventName
      ? `/api/v1/battle/timer?event=${encodeURIComponent(props.eventName)}`
      : '/api/v1/battle/timer'
    fetch(url, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        running: isRunning.value,
        timeLeft: timeLeft.value,
        totalDuration: totalDuration.value
      })
    }).catch(() => {})
  } catch (_) {
    // Gracefully skip broadcast failures — never crash the UI
  }
}

// ── Cleanup ──
onBeforeUnmount(() => {
  clearInterval(intervalId)
  intervalId = null
  clearTimeout(finishTimeoutId)
  finishTimeoutId = null
  // Always broadcast final state so the overlay doesn't show a frozen timer
  if (timerState.value === 'RUNNING' || timerState.value === 'FINISHED') {
    timerState.value = 'IDLE'
    timeLeft.value = 0
    totalDuration.value = 0
    publishState()
  }
})
</script>

<template>
  <div
    class="relative select-none transition-opacity duration-300"
    :class="{ 'opacity-40 pointer-events-none': isMuted }"
  >
    <!-- ================================================================ -->
    <!-- IDLE + LOCKED: Preset selector (tap to start)                  -->
    <!-- ================================================================ -->
    <div v-if="isIdle && isLockedPhase" class="flex flex-col items-center gap-3 py-2">
      <div class="section-rule w-full">
        <span class="section-rule-label">BATTLE TIMER</span>
        <span class="section-rule-line"></span>
      </div>

      <div class="flex items-center gap-2">
        <button
          v-for="p in presets"
          :key="p"
          class="para-chip-sm px-3 py-2 type-body transition-all duration-150 active:scale-95"
          :class="selectedDuration === p
            ? 'preset-btn-active text-accent'
            : 'text-content-muted hover:text-content-primary hover:border-white/20'"
          @click="tapPreset(p)"
        >
          {{ p }}s
        </button>
      </div>
    </div>

    <!-- ================================================================ -->
    <!-- RUNNING: Progress bar + countdown + presets to restart          -->
    <!-- ================================================================ -->
    <div v-if="isRunning" class="flex flex-col items-center gap-3 py-2">
      <div class="section-rule w-full">
        <span class="section-rule-label">COUNTDOWN</span>
        <span class="section-rule-line"></span>
      </div>

      <div
        class="countdown-display text-center transition-colors duration-300"
        :class="isWarning ? 'text-red-500 animate-pulse' : 'text-content-primary'"
      >
        {{ displayTime }}
      </div>

      <div
        class="progress-track w-full"
        :class="isWarning ? 'progress-track-warning' : ''"
      >
        <div
          class="progress-fill h-full transition-all duration-1000 ease-linear"
          :class="isWarning
            ? 'bg-red-500 animate-pulse'
            : 'bg-[color:var(--accent-color)]'"
          :style="{ width: progressPct + '%' }"
        ></div>
      </div>

      <div class="flex items-center gap-2">
        <button
          v-for="p in presets"
          :key="p"
          class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150 active:scale-95"
          :class="totalDuration === p
            ? 'preset-btn-active text-accent'
            : 'text-content-muted hover:text-content-primary hover:border-white/20'"
          @click="tapPreset(p)"
        >
          {{ p }}s
        </button>
      </div>
    </div>

    <!-- ================================================================ -->
    <!-- FINISHED: Burst animation at 0s                                 -->
    <!-- ================================================================ -->
    <div v-if="isFinished" class="flex flex-col items-center justify-center py-4">
      <div class="countdown-burst text-center">
        <span class="burst-text">TIME</span>
      </div>
    </div>

    <!-- ================================================================ -->
    <!-- Muted IDLE (phase !== 'LOCKED' && phase !== 'VOTING')            -->
    <!-- ================================================================ -->
    <div v-if="isIdle && !isLockedPhase" class="flex items-center justify-center py-3">
      <span class="type-label text-content-muted text-xs">TIMER — LOCKED PHASE ONLY</span>
    </div>
  </div>
</template>

<style scoped>
/* ── Preset active state ──────────────────────────────────────── */
.preset-btn-active {
  border-color: color-mix(in srgb, var(--accent-color) 55%, transparent);
}
.preset-btn-active:hover {
  border-color: var(--accent-color) !important;
  background: rgba(255, 255, 255, 0.10);
}

/* ── Countdown display (28px Anton SC) ──────────────────────────── */
.countdown-display {
  font-family: 'Anton SC', sans-serif;
  font-size: 28px;
  letter-spacing: 0.02em;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

/* ── Progress bar track ────────────────────────────────────────── */
.progress-track {
  height: 4px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 0;
  overflow: hidden;
}
.progress-track-warning {
  background: rgba(239, 68, 68, 0.12);
}
.progress-fill {
  border-radius: 0;
}

/* ── Burst animation at 0s ──────────────────────────────────────── */
.countdown-burst {
  animation: timerBurst 0.8s cubic-bezier(0.25, 0.46, 0.45, 0.94) forwards;
}

.burst-text {
  font-family: 'Anton SC', sans-serif;
  font-size: 42px;
  letter-spacing: 0.06em;
  color: var(--accent-color, #fff);
  text-shadow:
    0 0 20px var(--accent-muted, rgba(255,255,255,0.3)),
    0 0 60px var(--accent-muted, rgba(255,255,255,0.15));
}

@keyframes timerBurst {
  0% {
    transform: scale(0.6);
    opacity: 0;
    filter: brightness(1);
  }
  15% {
    transform: scale(1.15);
    opacity: 1;
    filter: brightness(2.5);
  }
  30% {
    transform: scale(1.05);
    opacity: 1;
    filter: brightness(1.5);
  }
  100% {
    transform: scale(1.4);
    opacity: 0;
    filter: brightness(0.3);
  }
}
</style>
