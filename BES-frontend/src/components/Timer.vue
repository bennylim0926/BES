<script setup>
import { ref, computed, onBeforeUnmount } from "vue";
import ReusableButton from "./ReusableButton.vue";

const selectedTime = ref(0)
const timeLeft = ref(0);
let timer = null;

const isRunning = computed(() => timer !== null)
const isFinished = computed(() => selectedTime.value > 0 && timeLeft.value >= selectedTime.value)
const isNearEnd = computed(() => selectedTime.value > 0 && timeLeft.value >= selectedTime.value - 5 && !isFinished.value)

const displayTime = computed(() => {
  if (selectedTime.value === 0) return '—'
  const remaining = selectedTime.value - timeLeft.value
  if (remaining <= 0) return '0'
  return String(remaining)
})

const progressPct = computed(() => {
  if (selectedTime.value === 0) return 0
  return Math.min((timeLeft.value / selectedTime.value) * 100, 100)
})

function startTimer(seconds) {
  if (timer) clearInterval(timer);
  selectedTime.value = seconds
  timeLeft.value = 0;
  timer = setInterval(() => {
    if (timeLeft.value < seconds) {
      timeLeft.value++;
    } else {
      clearInterval(timer);
      timer = null;
    }
  }, 1000);
}

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="card p-4">
    <div class="flex items-center gap-4">
      <!-- Countdown display -->
      <div class="flex-shrink-0 text-center min-w-[80px]">
        <div
          class="text-4xl font-heading font-extrabold tabular-nums transition-all duration-500"
          :class="{
            'text-surface-900': !isNearEnd && !isFinished,
            'text-red-500 animate-pulse': isNearEnd,
            'text-primary-600': isFinished,
          }"
        >
          {{ isFinished ? "Done" : displayTime }}
        </div>
        <div class="text-xs text-surface-400 mt-0.5">
          {{ selectedTime > 0 ? `of ${selectedTime}s` : 'seconds' }}
        </div>
      </div>

      <!-- Progress bar -->
      <div class="flex-1">
        <div class="h-1.5 bg-surface-100 rounded-full overflow-hidden mb-3">
          <div
            class="h-full rounded-full transition-all duration-1000"
            :class="isNearEnd ? 'bg-red-400' : 'bg-primary-500'"
            :style="{ width: progressPct + '%' }"
          ></div>
        </div>

        <!-- Quick-start buttons -->
        <div class="flex gap-2 flex-wrap">
          <button
            v-for="t in [30, 45, 60, 90]"
            :key="t"
            @click="startTimer(t)"
            class="px-3 py-1.5 rounded-lg text-xs font-semibold border transition-all duration-150"
            :class="selectedTime === t && isRunning
              ? 'bg-primary-600 text-white border-primary-600 shadow-sm'
              : 'bg-white border-surface-200 text-surface-700 hover:border-primary-300 hover:bg-primary-50'"
          >
            {{ t }}s
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
