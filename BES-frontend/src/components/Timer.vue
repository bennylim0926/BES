<script setup>
import { ref, computed, onBeforeUnmount } from "vue";

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
  <div class="flex flex-col items-center gap-1 select-none">
    <div class="text-center leading-none">
      <div
        class="type-stat transition-colors duration-300"
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
        class="h-full transition-all duration-1000"
        :class="isNearEnd ? 'bg-red-500' : countUp ? 'bg-green-400' : 'bg-white/50'"
        :style="{ width: progressPct + '%' }"
      ></div>
    </div>

    <!-- Controls -->
    <div class="flex items-center gap-1 flex-wrap justify-center">
      <button
        @click="toggleMode"
        class="para-chip-sm type-label transition-all duration-150 active:scale-95"
        :class="countUp
          ? 'text-accent border-[color:var(--accent-muted)]'
          : 'text-content-muted hover:text-content-primary'"
      >
        <i :class="countUp ? 'pi pi-arrow-up' : 'pi pi-arrow-down'" class="mr-0.5 text-[9px]"></i>
        {{ countUp ? 'Up' : 'Dn' }}
      </button>
      <div class="w-px h-5 bg-white/12"></div>
      <button
        v-for="t in [30, 45, 60, 90]"
        :key="t"
        @click="startTimer(t)"
        class="para-chip-sm type-label transition-all duration-150 active:scale-95"
        :class="selectedTime === t && isRunning
          ? 'text-accent border-[color:var(--accent-muted)]'
          : 'text-content-muted hover:text-content-primary'"
      >{{ t }}</button>
    </div>
  </div>
</template>
