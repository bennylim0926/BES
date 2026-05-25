<script setup>
import { ref, computed, onBeforeUnmount } from "vue";
import ReusableButton from "./ReusableButton.vue";

const selectedTime = ref(0)
const timeLeft = ref(0)
const countUp = ref(false)
let timer = null

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
  if (timer) clearInterval(timer)
  selectedTime.value = seconds
  timeLeft.value = 0
  timer = setInterval(() => {
    if (timeLeft.value < seconds) {
      timeLeft.value++
    } else {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
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

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div
    class="card p-4 backdrop-blur-md"
    style="box-shadow: 0 4px 24px rgba(0,0,0,0.35), 0 1px 4px rgba(0,0,0,0.2);"
    :class="isRunning && isNearEnd ? 'animate-glow-pulse' : ''"
  >
    <div class="flex items-center gap-4">
      <!-- Time display -->
      <div class="flex-shrink-0 text-center min-w-[80px]">
        <div
          class="text-5xl font-heading font-extrabold tabular-nums transition-all duration-500"
          :class="{
            'text-content-primary': !isNearEnd && !isFinished,
            'text-red-400 animate-pulse': isNearEnd,
            'text-primary-400': isFinished,
          }"
        >
          {{ isFinished ? "Done" : displayTime }}
        </div>
        <div class="text-xs text-content-muted mt-0.5">
          {{ selectedTime > 0 ? `of ${selectedTime}s` : 'seconds' }}
        </div>
      </div>

      <!-- Progress bar + controls -->
      <div class="flex-1">
        <div class="h-1.5 bg-surface-600 rounded-full overflow-hidden mb-3">
          <div
            class="h-full rounded-full transition-all duration-1000"
            :class="isNearEnd ? 'bg-red-400' : 'bg-primary-500'"
            :style="{ width: progressPct + '%' }"
          ></div>
        </div>

        <div class="flex items-center gap-2 flex-wrap">
          <!-- Mode toggle -->
          <button
            @click="toggleMode"
            class="px-3 py-1.5 rounded-lg text-xs font-semibold border transition-all duration-150 flex items-center gap-1"
            :class="countUp
              ? 'bg-surface-600 border-primary-500/50 text-primary-300'
              : 'bg-surface-700 border-surface-600 text-content-secondary hover:border-primary-500/50'"
          >
            <i :class="countUp ? 'pi pi-arrow-up' : 'pi pi-arrow-down'" class="text-[10px]"></i>
            {{ countUp ? 'Count Up' : 'Count Down' }}
          </button>

          <div class="w-px h-4 bg-surface-600"></div>

          <!-- Duration presets -->
          <button
            v-for="t in [30, 45, 60, 90]"
            :key="t"
            @click="startTimer(t)"
            class="px-3 py-1.5 rounded-lg text-xs font-semibold border transition-all duration-150"
            :class="selectedTime === t && isRunning
              ? 'bg-primary-600 text-white border-primary-600 shadow-[0_0_12px_rgba(6,182,212,0.3)]'
              : 'bg-surface-700 border-surface-600 text-content-secondary hover:border-primary-500/50 hover:bg-surface-600'"
          >
            {{ t }}s
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
