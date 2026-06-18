<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue";

const emit = defineEmits(['started', 'stopped', 'tick'])

const selectedTime = ref(0)
const timeLeft = ref(0)
const countUp = ref(false)
let timer = null
// Anchor wall-clock time used to recompute timeLeft on every tick.
// Replacing "increment by 1 every interval" with "diff Date.now()" keeps
// the timer accurate even when the tab is backgrounded and setInterval is
// throttled by the browser (iOS Safari throttles to ~1/minute).
let startedAtMs = 0

const isRunning = computed(() => timer !== null)
const isFinished = computed(() => selectedTime.value > 0 && timeLeft.value >= selectedTime.value)
const isNearEnd = computed(() =>
  !countUp.value &&
  selectedTime.value > 0 &&
  timeLeft.value >= selectedTime.value - 5 &&
  !isFinished.value
)

const displayTime = computed(() => {
  if (selectedTime.value === 0) return '—'
  if (countUp.value) {
    // show elapsed
    return String(Math.min(timeLeft.value, selectedTime.value))
  } else {
    // show remaining
    const remaining = selectedTime.value - timeLeft.value
    if (remaining <= 0) return '0'
    return String(remaining)
  }
})

const progressPct = computed(() => {
  if (selectedTime.value === 0) return 0
  return Math.min((timeLeft.value / selectedTime.value) * 100, 100)
})

function startTimer(seconds) {
  // Clicking the active preset while running resets the timer
  if (selectedTime.value === seconds && timer) {
    reset()
    return
  }
  if (timer) clearInterval(timer)
  selectedTime.value = seconds
  timeLeft.value = 0
  startedAtMs = Date.now()
  timer = setInterval(() => {
    const elapsed = Math.floor((Date.now() - startedAtMs) / 1000)
    if (elapsed < seconds) {
      timeLeft.value = elapsed
      const remaining = selectedTime.value - timeLeft.value
      emit('tick', { remaining, total: selectedTime.value, running: true })
    } else {
      timeLeft.value = seconds
      clearInterval(timer)
      timer = null
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
  countUp.value = !countUp.value
}

function reset() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
  selectedTime.value = 0
  timeLeft.value = 0
  emit('stopped')
}

function stop() {
  if (timer) {
    clearInterval(timer)
    timer = null
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
    timeLeft.value = totalDuration
    emit('stopped')
    return
  }
  selectedTime.value = totalDuration
  const elapsed0 = totalDuration - remainingSeconds
  timeLeft.value = elapsed0
  startedAtMs = Date.now() - (elapsed0 * 1000)
  timer = setInterval(() => {
    const elapsed = Math.floor((Date.now() - startedAtMs) / 1000)
    if (elapsed < totalDuration) {
      timeLeft.value = elapsed
      const remaining = selectedTime.value - timeLeft.value
      emit('tick', { remaining, total: selectedTime.value, running: true })
    } else {
      timeLeft.value = totalDuration
      clearInterval(timer)
      timer = null
      emit('stopped')
      emit('tick', { remaining: 0, total: selectedTime.value, running: false })
    }
  }, 250)
  emit('started', { duration: totalDuration, startedAt: startedAtMs })
}

defineExpose({ reset, stop, resumeTimer })

// When the tab returns from background, force an immediate recompute so
// the display catches up to the real elapsed time without waiting for the
// next interval tick.
function onVisibilityChange() {
  if (document.visibilityState !== 'visible' || !timer || selectedTime.value === 0) return
  const elapsed = Math.floor((Date.now() - startedAtMs) / 1000)
  if (elapsed >= selectedTime.value) {
    timeLeft.value = selectedTime.value
    clearInterval(timer)
    timer = null
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
  <div class="flex flex-col items-center gap-1 select-none">
    <div class="text-center leading-none">
      <div
        class="type-stat transition-colors duration-300"
        style="font-size: clamp(4rem, 14vh, 8rem); line-height: 1;"
        :class="{
          'text-red-500 animate-pulse': isNearEnd,
          'text-content-muted':         isFinished && !isNearEnd,
          'text-green-400':             countUp && !isNearEnd && !isFinished,
          'text-content-primary':       !isNearEnd && !isFinished && !countUp,
        }"
      >{{ displayTime }}</div>
      <div class="type-label text-content-muted">
        {{ selectedTime > 0 ? `OF ${selectedTime}S` : 'SECONDS' }}
      </div>
    </div>

    <!-- Progress bar -->
    <div class="w-full max-w-xs h-px bg-white/8 overflow-hidden">
      <div
        class="h-full transition-[width] duration-200"
        :class="isNearEnd ? 'bg-red-500' : countUp ? 'bg-green-400' : 'bg-white/50'"
        :style="{ width: progressPct + '%' }"
      ></div>
    </div>

    <!-- Controls -->
    <div class="flex items-center gap-2 flex-wrap justify-center">
      <button
        @click="toggleMode"
        class="para-chip px-6 py-4 type-body transition-all duration-150 active:scale-95"
        style="font-size:18px"
        :class="countUp
          ? 'text-accent border-[color:var(--accent-muted)]'
          : 'text-content-muted hover:text-content-primary'"
      >
        <i :class="countUp ? 'pi pi-arrow-up' : 'pi pi-arrow-down'" class="mr-1"></i>
        {{ countUp ? 'Up' : 'Down' }}
      </button>
      <div class="w-px h-8 bg-white/12"></div>
      <button
        v-for="t in [30, 45, 60, 90]"
        :key="t"
        @click="startTimer(t)"
        class="para-chip px-6 py-4 type-body transition-all duration-150 active:scale-95"
        style="font-size:18px"
        :class="selectedTime === t && isRunning
          ? 'text-accent border-[color:var(--accent-muted)]'
          : 'text-content-muted hover:text-content-primary'"
      >{{ t }}s</button>
    </div>
  </div>
</template>
