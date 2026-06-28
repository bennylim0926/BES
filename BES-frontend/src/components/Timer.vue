<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from "vue";

const props = defineProps({
  baselineDuration: { type: Number, default: null },
})

const emit = defineEmits(['started', 'stopped', 'tick'])

const selectedTime = ref(0)
const timeLeft = ref(0)
const running = ref(false)
let timer = null
let startedAtMs = 0

const isRunning = computed(() => running.value)

// Adopt externally-supplied baseline (used on page-refresh restore from backend).
watch(() => props.baselineDuration, (val) => {
  if (!running.value && val != null && val > 0) {
    selectedTime.value = val
  }
}, { immediate: true })

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

// Reset = stop and park display at the picked baseline.
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
