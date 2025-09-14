<script setup>
import { ref } from 'vue';
import { capsFirstLetter } from '@/utils/utils';

const props = defineProps({
  cards: {
    type: Array,
    required: true,
  }
})

const colors = ref(['bg-red-100', 'bg-orange-100', 'bg-amber-100', 'bg-yellow-100', 'bg-lime-100', 'bg-green-100', 'bg-emerald-100', 'bg-teal-100', 'bg-cyan-100',
    'bg-sky-100', 'bg-blue-100', 'bg-indigo-100', 'bg-violet-100', 'bg-purple-100', 'bg-fuchsia-100', 'bg-pink-100', 'bg-rose-100'])

const emit = defineEmits(["update:cards"])

</script>

<template>
  <div class="mx-5">
    <!-- Horizontal scroll container with snap -->
    <div
      class="flex overflow-x-auto snap-x snap-mandatory scroll-smooth h-[100vh]"
    >
      <div
        v-for="(card, idx) in props.cards"
        :key="idx"
        class="flex-shrink-0 w-full snap-center px-4 flex items-center justify-center"
      >
        <div class="w-full h-[85%] rounded-2xl shadow-lg flex flex-col items-center justify-center p-8"
              :class="`${colors[idx % colors.length]}`">
          <h2 class="text-3xl font-bold text-gray-900">
            {{ card.participantName }}
          </h2>
          <p class="text-lg text-gray-700 mt-2">
            Category: {{ capsFirstLetter(card.genreName) }}
          </p>
          <p class="text-2xl text-gray-700 mt-2">
            Number: <span class="text-4xl font-bold">#{{ capsFirstLetter(card.auditionNumber) }}</span> 
          </p>


          <!-- Slider -->
          <div class="mt-6 w-3/4">
            <input
              type="range"
              min="0"
              max="10"
              step="0.1"
              class="w-full accent-orange-400 custom-slider"
              v-model="card.score"
            />
            <p class="mt-2 text-2xl text-gray-900">Score: {{ card.score }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Base */
.custom-slider {
  -webkit-appearance: none; /* Reset default styles */
  appearance: none;
  height: 3rem; /* Space for large thumb */
  width: 100%;
}

/* Chrome, Safari, Edge, Brave (WebKit/Blink) */
.custom-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #f6892a;
  cursor: pointer;
  border: none;
  margin-top: -26px; /* Align thumb vertically with thin track */
}

/* Track (Chrome/Brave desktop) */
.custom-slider::-webkit-slider-runnable-track {
  height: 8px;  /* control thickness of track */
  border-radius: 4px;
  background: #ddd;
}

</style>

