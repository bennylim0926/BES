<script setup>
/**
 * BattleTimer.vue
 *
 * A countdown timer for the live battle flow during the LOCKED phase.
 * The Emcee selects a preset duration and starts the timer manually.
 * At 10 seconds remaining, it auto-emits an `unlock` event to trigger
 * the phase transition to VOTING. Timer state is broadcast via STOMP
 * every second for display on the overlay.
 *
 * State machine:
 *   IDLE   → (click START) → RUNNING
 *   RUNNING → (timeLeft=0)  → IDLE  (auto-stop)
 *   RUNNING → (click RESET) → IDLE
 *   RUNNING → (phase leaves LOCKED) → IDLE (auto-reset via watcher)
 */
import { ref, computed, watch, onBeforeUnmount } from 'vue'

const props = defineProps({
  phase:       { type: String, default: 'IDLE' },
  stompClient: { type: Object, default: null },
  presets:     { type: Array,  default: () => [30, 45, 60, 90] }
})

const emit = defineEmits(['unlock'])

// ── Reactive state ──
const timerState        = ref('IDLE')  // 'IDLE' | 'RUNNING'
const selectedDuration  = ref(30)      // currently highlighted preset (seconds)
const totalDuration     = ref(0)
const timeLeft          = ref(0)
const autoUnlocked      = ref(false)   // guard: prevent double-unlock emit

let intervalId = null

// ── Computed ──
const isRunning    = computed(() => timerState.value === 'RUNNING')
const isIdle       = computed(() => timerState.value === 'IDLE')
const isLockedPhase = computed(() => props.phase === 'LOCKED')
const isWarning    = computed(() => isRunning.value && timeLeft.value <= 10)
const isMuted      = computed(() => !isRunning.value && !isLockedPhase.value)

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

function selectPreset(seconds) {
  if (isRunning.value) return
  selectedDuration.value = seconds
}

function startTimer() {
  if (isRunning.value) return        // guard: only start from IDLE
  timerState.value = 'RUNNING'
  totalDuration.value = selectedDuration.value
  timeLeft.value = selectedDuration.value
  autoUnlocked.value = false

  publishState()                      // broadcast initial state

  clearInterval(intervalId)
  intervalId = setInterval(() => {
    timeLeft.value--

    publishState()                    // broadcast every tick

    // At exactly 10 seconds remaining: emit unlock (once)
    if (timeLeft.value === 10 && !autoUnlocked.value) {
      autoUnlocked.value = true
      emit('unlock')
    }

    // Auto-stop when countdown hits zero
    if (timeLeft.value <= 0) {
      resetTimer()
    }
  }, 1000)
}

function resetTimer() {
  clearInterval(intervalId)
  intervalId = null
  timerState.value = 'IDLE'
  totalDuration.value = 0
  timeLeft.value = 0
  publishState()
}

function publishState() {
  if (!props.stompClient || !props.stompClient.connected) return
  try {
    props.stompClient.publish({
      destination: '/app/battle/timer',
      body: JSON.stringify({
        running: isRunning.value,
        timeLeft: timeLeft.value,
        totalDuration: totalDuration.value
      })
    })
  } catch (_) {
    // Gracefully skip broadcast failures — never crash the UI
  }
}

// ── Phase watcher: auto-reset when phase leaves LOCKED while running ──
watch(() => props.phase, (newPhase, oldPhase) => {
  if (isRunning.value && oldPhase === 'LOCKED' && newPhase !== 'LOCKED') {
    resetTimer()
  }
})

// ── Cleanup ──
onBeforeUnmount(() => {
  if (intervalId) {
    clearInterval(intervalId)
    intervalId = null
  }
})
</script>

<template>
  <div
    class="relative select-none transition-opacity duration-300"
    :class="{ 'opacity-40 pointer-events-none': isMuted }"
  >
    <!-- ================================================================ -->
    <!-- IDLE + LOCKED: Preset selector + START button                   -->
    <!-- ================================================================ -->
    <div v-if="isIdle && isLockedPhase" class="flex flex-col items-center gap-3 py-2">
      <!-- Section rule label -->
      <div class="section-rule w-full">
        <span class="section-rule-label">BATTLE TIMER</span>
        <span class="section-rule-line"></span>
      </div>

      <!-- Preset buttons row -->
      <div class="flex items-center gap-2">
        <button
          v-for="p in presets"
          :key="p"
          class="para-chip-sm px-3 py-2 type-body transition-all duration-150 active:scale-95"
          :class="selectedDuration === p
            ? 'preset-btn-active text-accent'
            : 'text-content-muted hover:text-content-primary hover:border-white/20'"
          @click="selectPreset(p)"
        >
          {{ p }}s
        </button>
      </div>

      <!-- START button -->
      <button
        class="para-chip-sm start-btn px-5 py-2.5 type-body transition-all duration-150 active:scale-95"
        @click="startTimer"
      >
        START {{ selectedDuration }}s
      </button>
    </div>

    <!-- ================================================================ -->
    <!-- RUNNING: Progress bar + countdown + RESET                       -->
    <!-- ================================================================ -->
    <div v-if="isRunning" class="flex flex-col items-center gap-3 py-2">
      <!-- Section rule label -->
      <div class="section-rule w-full">
        <span class="section-rule-label">COUNTDOWN</span>
        <span class="section-rule-line"></span>
      </div>

      <!-- Countdown number (28px Anton SC) -->
      <div
        class="countdown-display text-center transition-colors duration-300"
        :class="isWarning ? 'text-red-500 animate-pulse' : 'text-content-primary'"
      >
        {{ displayTime }}
      </div>

      <!-- Progress bar track -->
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

      <!-- RESET button -->
      <button
        class="reset-btn para-chip-sm px-3 py-1.5 type-label transition-all duration-150 active:scale-95"
        @click="resetTimer"
      >
        RESET
      </button>
    </div>

    <!-- ================================================================ -->
    <!-- Muted IDLE (phase !== 'LOCKED') — minimal label                  -->
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

/* ── START button ──────────────────────────────────────────────── */
.start-btn {
  border-color: var(--accent-color) !important;
  color: var(--accent-color);
}
.start-btn:hover {
  border-color: var(--accent-color) !important;
  background: rgba(255, 255, 255, 0.18);
  filter: brightness(0.88);
}
.start-btn:active {
  filter: brightness(0.75);
  box-shadow: inset 0 1px 4px rgba(0, 0, 0, 0.35);
}

/* ── Countdown display (28px Anton SC) ──────────────────────────── */
.countdown-display {
  font-family: 'Anton SC', sans-serif;
  font-size: 28px;
  letter-spacing: 0.02em;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

/* ── RESET button ──────────────────────────────────────────────── */
.reset-btn {
  color: rgba(255, 255, 255, 0.35);
  border-color: rgba(255, 255, 255, 0.07);
}
.reset-btn:hover {
  color: #ef4444 !important;
  border-color: rgba(239, 68, 68, 0.3) !important;
  background: rgba(239, 68, 68, 0.08);
}

/* ── Progress bar track (4px height) ──────────────────────────── */
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
</style>
