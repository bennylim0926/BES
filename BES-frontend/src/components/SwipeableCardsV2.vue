<script setup>
import { ref, computed, watch, nextTick } from 'vue';
import { capsFirstLetter } from '@/utils/utils';
import ReusableButton from './ReusableButton.vue';

const props = defineProps({
  cards: {
    type: Array,
    required: true,
  },
});

const emit = defineEmits(['update:cards']);

const currentIndex = ref(0);

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
</script>

<template>
  <div class="mx-5">
    <!-- Horizontal Scroll -->
    <div
      v-if="props.cards && props.cards.length"
      class="flex overflow-x-auto snap-x snap-mandatory scroll-smooth h-auto"
    >
      <div
        v-for="(card, idx) in props.cards"
        :key="idx"
        data-card
        class="flex-shrink-0 w-full snap-center flex justify-center"
      >
        <div
          class="w-full min-h-[85%] rounded-2xl shadow-xl flex flex-col p-2 md:p-8 lg:p-8 overflow-y-auto m-0 md:m-5 lg:m-5 mb-5"
          :class="[colors[idx % colors.length]]"
        >
        <div class="w-full grid grid-cols-2">
            <div class="flex flex-col items-center justify-end text-center mb-5">
            <div class="flex flex-wrap w-full gap-10 justify-center items-center mt-2">
              <p class="text-lg text-gray-700 ">
                Name:
                <span class="text-xl md:text-4xl lg:text-4xl font-semibold text-black">
                  {{ card.participantName }}(#{{ capsFirstLetter(card.auditionNumber) }})
                </span>
              </p>
            </div>
        </div>
        
        <div class="flex flex-col items-center justify-end text-center mb-5">
            <p class="text-lg text-black">
                Score:
                <span class="text-xl md:text-4xl lg:text-4xl font-semibold text-black ">
                  {{ card.score }}
                </span>
              </p>
            </div>
        </div>
        <hr class="h-px mb-5 bg-gray-900 border-0 ">
        <div class="flex mb-5 justify-center">
            <div
            class="min-h-[65px] w-[85%] px-6 bg-orange-400 text-white text-lg md:text-2xl lg:text-2xl rounded flex items-center justify-center
                active:bg-orange-300 active:border active:text-gray-100 shadow-md"
            @click="card.score = 10"
            >
            10(Full Score)
            </div>
        </div>
          <!-- Scoring Buttons -->
          <div class="w-full grid grid-cols-2 gap-4 mt-auto">
            <div class="flex justify-center">
                <div class="grid grid-cols-3 gap-2 max-w-[300px]">
                <ReusableButton
                    v-for="value in 9"
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
