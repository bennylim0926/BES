<script setup>
import { ref, computed, watch, nextTick, onMounted } from 'vue';
import { capsFirstLetter } from '@/utils/utils';
import ReusableButton from './ReusableButton.vue';

const props = defineProps({
  cards: {
    type: Array,
    required: true,
  },
});

const emit = defineEmits(['update:cards']);

const scrollRef = ref(null)
const currentIndex = ref(0)

const observeCards = async () => {
  await nextTick()

  const cards = scrollRef.value.querySelectorAll('[data-card]')

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const index = Number(entry.target.getAttribute('data-index'))
          currentIndex.value = index
        }
      })
    },
    {
      root: scrollRef.value,
      threshold: 0.6, // 60% of the card must be visible to count as active
    }
  )

  cards.forEach((card) => observer.observe(card))
}

const addByZeroOne = (source, max) => {
  if (source.score < max) {
    source.score = parseFloat((source.score + 0.1).toFixed(1));
  }
};
const minusByZeroOne = (source) => {
  if (source.score > 0) {
    source.score = parseFloat((source.score - 0.1).toFixed(1));
  }
};

const updateDecimal = (score, num) =>{
    if(score === 10){
        return 10
    }
    const wholeNum = Math.floor(score)
    return wholeNum + num/10
}
const colors = ['bg-orange-50'];

onMounted(observeCards)
</script>

<template>
  
  <div class="w-full h-auto">
    <!-- Horizontal Scroll -->
    <div
      ref="scrollRef"
      v-if="props.cards && props.cards.length"
      class="flex overflow-x-auto snap-x snap-mandatory scroll-smooth md:gap-4 md:px-4"
    >
      <div
        v-for="(card, idx) in props.cards"
        :key="idx"
        :data-index="idx"
        data-card
        class="flex-shrink-0 w-[90%] md:w-[75%] snap-center flex justify-center mb-10 mx-3"
      >
        <div
          class="w-[90vw] md:w-[70vw] rounded-2xl shadow-xl/30 flex flex-col p-4 md:p-8 lg:p-8 mx-0 md:mx-5 lg:mx-5"
          :class="[colors[idx % colors.length]]"
        >
        <div class="w-full grid grid-cols-1 md:grid-cols-[3fr_1fr]">
            <div class="flex flex-col  items-center justify-center text-center">
              <p class="text-sm md:text-lg text-black ">
                <span class="text-lg md:text-3xl lg:text-3xl font-semibold text-black">
                  {{ card.participantName }} (#{{ capsFirstLetter(card.auditionNumber) }})
                </span>
              </p>
        </div>
        
        <div class="flex flex-col items-center justify-center text-center">
            <p class="text-sm md:text-lg text-black">
                Score:
                <span class="text-lg md:text-4xl lg:text-4xl font-semibold text-black ">
                  {{ card.score }}
                </span>
              </p>
            </div>
        </div>
        <hr class="h-px mb-5 bg-gray-900 border-0 ">
        <div class="flex mb-5 justify-center">
            <button
            :disabled="idx !== currentIndex"
            class="min-h-[45px] w-[85%] px-6 bg-orange-400 text-white text-lg md:text-2xl lg:text-2xl rounded flex items-center justify-center
                active:bg-orange-300 active:border active:text-gray-100 shadow-md"
            @click="card.score = 10"
            >
            10(Full Score)
            </button>
        </div>
          <!-- Scoring Buttons -->
          <div class="w-full grid grid-cols-[4fr_5fr] md:grid-cols-[2fr_2fr] ">
            <div class="flex justify-center">
                <div class="grid grid-cols-3 gap-2 max-w-[300px]">
                <ReusableButton
                    v-for="value in 9"
                    :disabled="idx !== currentIndex"
                    :key="value"
                    :buttonName="value"
                    class="text-xl md:text-3xl rounded shadow-sm"
                    @onClick="card.score = Number(value)"
                />
                </div>
            </div>
            <div class="flex justify-center">
                <div class="grid grid-cols-3 gap-2 max-w-[300px]">
                <ReusableButton
                    :disabled="idx !== currentIndex"
                    v-for="value in 9"
                    :key="value"
                    :buttonName="'.'+value"
                    class="text-lg md:text-2xl rounded shadow-sm"
                    @onClick="()=>{card.score = updateDecimal(card.score, value)}"
                />
                </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.custom-slider {
  -webkit-appearance: none;
  appearance: none;
  height: 3rem;
  width: 100%;
}

.custom-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  appearance: none;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: #f6892a;
  cursor: pointer;
  border: none;
  margin-top: -26px;
}

.custom-slider::-webkit-slider-runnable-track {
  height: 8px;
  border-radius: 4px;
  background: #ddd;
}
</style>
