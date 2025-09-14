<template>
    <div class="flex flex-col items-center space-y-4 h-50 w-auto bg-orange-50 dark:bg-gray-900 p-10 rounded-lg">
      <!-- Countdown -->
      <div v-if="timeLeft > 0">
        <div class="text-2xl ">
            Timer:
        <div
          class="text-5xl font-bold flex items-center justify-center"
          :class="{ 'animate-last10': timeLeft <= 10 }"
          :key="timeLeft" 
        >
          {{ timeLeft }}
        </div>
    </div>
      </div>
  
      <!-- When done -->
      <div v-else class="text-3xl font-bold text-orange-500">
        Time's Up
      </div>
  
      <!-- Buttons -->
      <div class="flex space-x-4">
        <ReusableButton
        v-for="t in [30, 45, 60, 90]"
        @onClick="startTimer(t)"
        :buttonName="t"></ReusableButton>
      </div>
    </div>
  </template>
  
  <script setup>
  import { ref, onBeforeUnmount } from "vue";
import ReusableButton from "./ReusableButton.vue";
  
  const timeLeft = ref(0);
  let timer = null;
  
  function startTimer(seconds) {
    if (timer) clearInterval(timer);
  
    timeLeft.value = seconds;
  
    timer = setInterval(() => {
      if (timeLeft.value > 0) {
        timeLeft.value--;
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
  
  <style>
  /* pop + fade ONLY for last 10s */
  @keyframes popFade {
  0% {
    transform: scale(1);
    opacity: 1;
  }
  80% {
    transform: scale(1.8);
    opacity: 0.2;
  }
  100% {
    transform: scale(2);
    opacity: 0;
  }
}

.animate-last10 {
  animation: popFade 1s cubic-bezier(0.4, 0, 0.2, 1) forwards;
}
  </style>
  