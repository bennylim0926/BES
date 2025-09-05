<script setup>
import { capsFirstLetter } from '@/utils/utils';

const props = defineProps({
  cards: {
    type: Array,
    required: true,
    // Example: [{ participantName: "Alice", genreName: "Hip-Hop" }, ...]
  }
})

const emit = defineEmits(["update:cards"])
const updateScore = (idx, val) => {
  const updated = [...props.cards]
  updated[idx] = { ...updated[idx], score: Number(val) }
  emit("update:cards", updated) // ✅ send back to parent
}
</script>

<template>
  <div class="w-full max-w-4xl mx-auto">
    <!-- Horizontal scroll container with snap -->
    <div
      class="flex overflow-x-auto snap-x snap-mandatory scroll-smooth h-[70vh]"
    >
      <div
        v-for="(card, idx) in props.cards"
        :key="idx"
        class="flex-shrink-0 w-full snap-center px-4 flex items-center justify-center"
      >
        <div class="w-full h-full bg-gray-800 rounded-2xl shadow-xl flex flex-col items-center justify-center p-8">
          <h2 class="text-3xl font-bold text-white">
            {{ card.participantName }}
          </h2>
          <p class="text-lg text-gray-300 mt-2">
            Category: {{ capsFirstLetter(card.genreName) }}
          </p>
          <p class="text-lg text-gray-300 mt-2">
            Number: {{ capsFirstLetter(card.auditionNumber) }}
          </p>


          <!-- Slider -->
          <div class="mt-6 w-3/4">
            <input
              type="range"
              min="0"
              max="10"
              step="0.1"
              class="w-full accent-blue-400"
              v-model="card.score"
            />
            <p class="mt-2">Score: {{ card.score }}</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
