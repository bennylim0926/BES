<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from "vue";

const props = defineProps({
  // Externally-supplied baseline (e.g. restored from backend state on mount).
  // When set and the timer is idle, the display shows this value instead of '—'.
  baselineDuration: { type: Number, default: null },
})

const emit = defineEmits(['started', 'stopped', 'tick'])

const selectedTime = ref(0)
const timeLeft = ref(0)
const countUp = ref(false)
const running = ref(false)
let timer = null
// Anchor wall-clock time used to recompute timeLeft on every tick.
// Replacing "increment by 1 every interval" with "diff Date.now()" keeps
// the timer accurate even when the tab is backgrounded and setInterval is
// throttled by the browser (iOS Safari throttles to ~1/minute).
let startedAtMs = 0

const isRunning = computed(() => running.value)
const isFinished = computed(() => running.value && selectedTime.value > 0 && timeLeft.value >= selectedTime.value)
const isNearEnd = computed(() =>
  running.value &&
  !countUp.value &&
  selectedTime.value > 0 &&
  timeLeft.value >= selectedTime.value - 5 &&
  !isFinished.value
)

const displayTime = computed(() => {
  // Idle (not running): show the sticky baseline so the audience-facing
  // display and the emcee timer both park on the last picked duration
  // instead of going blank.
  if (!running.value) {
    if (selectedTime.value > 0) return String(selectedTime.value)
    return '—'
  }
  if (countUp.value) {
    return String(Math.min(timeLeft.value, selectedTime.value))
  }
  const remaining = selectedTime.value - timeLeft.value
  if (remaining <= 0) return '0'
  return String(remaining)
})

// Adopt externally-supplied baseline (used on page-refresh restore from backend).
watch(() => props.baselineDuration, (val) => {
  if (!running.value && val != null && val > 0) {
    selectedTime.value = val
  }
}, { immediate: true })

const progressPct = computed(() => {
  if (selectedTime.value === 0) return 0
  return Math.min((timeLeft.value / selectedTime.value) * 100, 100)
})

function startTimer(seconds) {
  if (timer) clearInterval(timer)
  selectedTime.value = seconds
  timeLeft.value = 0
  startedAtMs = Date.now()
  running.value = true
  timer = setInterval(() => {
    const elapsed = Math.floor((Date.now() - startedAtMs) / 1000)
    if (elapsed < seconds) {
      timeLeft.value = elapsed
      const remaining = selectedTime.value - timeLeft.value
      emit('tick', { remaining, total: selectedTime.value, running: true })
    } else {
      // Natural expiry: snap timeLeft back to 0 so the display shows the
      // picked baseline (selectedTime), not "0".
      timeLeft.value = 0
      clearInterval(timer)
      timer = null
      running.value = false
      emit('stopped')
      emit('tick', { remaining: 0, total: selectedTime.value, running: false })
    }
  }, 250)
  emit('started', { duration: seconds, startedAt: startedAtMs })
}

function toggleMode() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  selectedTime.value = 0
  timeLeft.value = 0
  running.value = false
  countUp.value = !countUp.value
  emit('stopped')
}

// Reset = stop running countdown AND snap the display back to the picked
// baseline. Does NOT clear selectedTime — that's the whole point: the
// audience and the emcee both still see "45" (or whatever was picked)
// instead of a blank.
function reset() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  timeLeft.value = 0
  running.value = false
  emit('stopped')
}

// Hard clear — wipes baseline entirely. Used when switching categories.
function clearAll() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  selectedTime.value = 0
  timeLeft.value = 0
  running.value = false
  emit('stopped')
}

function stop() {
  if (timer) {
    clearInterval(timer)
    timer = null
    running.value = false
    emit('stopped')
  }
}

/**
 * Resume a countdown timer from a specific remaining time.
 * Called by EmceeRoundView on mount when recovering timer state after refresh.
 * @param {number} remainingSeconds - seconds left on the clock
 * @param {number} totalDuration - original total duration in seconds
 */
function resumeTimer(remainingSeconds, totalDuration) {
  if (timer) clearInterval(timer)
  if (remainingSeconds <= 0) {
    selectedTime.value = totalDuration
    timeLeft.value = 0
    running.value = false
    emit('stopped')
    return
  }
  selectedTime.value = totalDuration
  const elapsed0 = totalDuration - remainingSeconds
  timeLeft.value = elapsed0
  startedAtMs = Date.now() - (elapsed0 * 1000)
  running.value = true
  timer = setInterval(() => {
    const elapsed = Math.floor((Date.now() - startedAtMs) / 1000)
    if (elapsed < totalDuration) {
      timeLeft.value = elapsed
      const remaining = selectedTime.value - timeLeft.value
      emit('tick', { remaining, total: selectedTime.value, running: true })
    } else {
      timeLeft.value = 0
      clearInterval(timer)
      timer = null
      running.value = false
      emit('stopped')
      emit('tick', { remaining: 0, total: selectedTime.value, running: false })
    }
  }, 250)
  emit('started', { duration: totalDuration, startedAt: startedAtMs })
}

defineExpose({ reset, stop, clearAll, resumeTimer })

// When the tab returns from background, force an immediate recompute so
// the display catches up to the real elapsed time without waiting for the
// next interval tick.
function onVisibilityChange() {
  if (document.visibilityState !== 'visible' || !timer || selectedTime.value === 0) return
  const elapsed = Math.floor((Date.now() - startedAtMs) / 1000)
  if (elapsed >= selectedTime.value) {
    timeLeft.value = 0
    clearInterval(timer)
    timer = null
    running.value = false
    emit('stopped')
    emit('tick', { remaining: 0, total: selectedTime.value, running: false })
  } else {
    timeLeft.value = elapsed
    emit('tick', { remaining: selectedTime.value - elapsed, total: selectedTime.value, running: true })
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <!-- Controls only — timer number and progress bar live in the EmceeRoundView NOW card header -->
  <div class="flex items-center gap-2 justify-center select-none flex-wrap">
    <button
      @click="toggleMode"
      class="para-chip px-5 py-3 type-body transition-all duration-150 active:scale-95"
      style="font-size:16px"
      :class="countUp
        ? 'text-accent border-[color:var(--accent-muted)]'
        : 'text-content-muted hover:text-content-primary'"
    >
      <i :class="countUp ? 'pi pi-arrow-up' : 'pi pi-arrow-down'" class="mr-1"></i>
      {{ countUp ? 'Up' : 'Down' }}
    </button>
    <div class="w-px h-6 bg-white/12"></div>
    <button
      v-for="t in [45, 60, 90]"
      :key="t"
      @click="startTimer(t)"
      class="para-chip px-5 py-3 type-body transition-all duration-150 active:scale-95"
      style="font-size:16px"
      :class="selectedTime === t && isRunning
        ? 'text-accent border-[color:var(--accent-muted)]'
        : selectedTime === t
          ? 'text-content-secondary border-white/15'
          : 'text-content-muted hover:text-content-primary'"
    >{{ t }}s</button>
    <button
      @click="reset"
      class="para-chip px-5 py-3 type-body transition-all duration-150 active:scale-95 text-content-muted hover:text-content-primary"
      style="font-size:15px"
      title="Stop timer"
      :disabled="selectedTime === 0"
      :class="{ 'opacity-40 pointer-events-none': selectedTime === 0 }"
    >
      <i class="pi pi-stop-circle mr-1"></i>STOP
    </button>
  </div>
</template>
