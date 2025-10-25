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

// const colors = [
//   'bg-red-100', 'bg-orange-100', 'bg-amber-100', 'bg-yellow-100', 'bg-lime-100',
//   'bg-green-100', 'bg-emerald-100', 'bg-teal-100', 'bg-cyan-100', 'bg-sky-100',
//   'bg-blue-100', 'bg-indigo-100', 'bg-violet-100', 'bg-purple-100', 'bg-fuchsia-100', 'bg-pink-100', 'bg-rose-100'
// ];
const colors = ['bg-orange-100', 'bg-sky-100'];
const darkColors = ['dark:bg-gray-800', 'dark:bg-slate-800'];
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
        class="flex-shrink-0 w-full snap-center px-4 flex items-center justify-center"
      >
        <div
          class="w-full min-h-[85%] rounded-2xl shadow-lg flex flex-col p-8 overflow-y-auto"
          :class="[colors[idx % colors.length], darkColors[idx % darkColors.length]]"
        >
        <div class="w-full mt-auto grid grid-cols-[2fr_2fr]">
            
            <div class="flex-1 flex flex-col items-center justify-end text-center mb-5">
            <div class="flex flex-wrap w-full gap-10 justify-center items-center mt-2">
              <p class="text-xl text-gray-700 dark:text-gray-300">
                Name:
                <span class="text-6xl font-bold text-gray-900 dark:text-gray-100">
                  {{ card.participantName }} (#{{ capsFirstLetter(card.auditionNumber) }})
                </span>
              </p>
              <!-- <p class="text-3lg text-gray-700 dark:text-gray-300">
                Number:
                <span class="text-4xl font-bold text-gray-900 dark:text-gray-100">
                  #{{ capsFirstLetter(card.auditionNumber) }}
                </span>
              </p> -->
            </div>
        </div>
        
        <div class="flex-1 flex flex-col items-center justify-end text-center mb-5">
            <p class="text-xl text-gray-700 dark:text-gray-300">
                Score:
                <span class="text-6xl font-bold text-gray-900 dark:text-gray-100">
                  {{ card.score }}
                </span>
              </p>
            </div>
        </div>
        <hr class="h-px mb-5 bg-gray-900 border-0 dark:bg-gray-500">
        <div class="flex w-full gap-3 text-5xl font-bold mb-2 justify-center items-center">
            <!-- <div
            class="flex-1 min-h-[100px] bg-orange-300 dark:bg-transparent text-gray-700 dark:text-gray-100 text-2xl rounded flex items-center justify-center border border-orange-400
                active:bg-orange-400 active:border active:text-gray-100"
            @click="card.score = 0"
            >
            Reset
            </div> -->
            <div
            class="min-h-[70px] w-[85%] mx-auto px-6 bg-orange-300 dark:bg-transparent text-gray-700 dark:text-gray-100 text-2xl rounded flex items-center justify-center border border-orange-400
                active:bg-orange-400 active:border active:text-gray-100"
            @click="card.score = 10"
            >
            10 (Full Score)
            </div>
        </div>
          <!-- Scoring Buttons -->
          <div class="w-full mt-auto grid grid-cols-[2fr_2fr]">
            <div class="flex justify-center items-center">
                <div class="grid grid-cols-3 w-auto gap-2 mb-4">
                <ReusableButton
                    v-for="value in 9"
                    :key="value"
                    :buttonName="value"
                    class="text-3xl bg-orange-300 dark:bg-transparent rounded"
                    @onClick="card.score = Number(value)"
                />
                </div>
            </div>
            <div class="flex justify-center items-center">
                <div class="grid grid-cols-3 w-auto gap-2 mb-4">
                <ReusableButton
                    v-for="value in 9"
                    :key="value"
                    :buttonName="'.'+value"
                    class="text-2xl bg-orange-300 dark:bg-transparent rounded"
                    @onClick="()=>{card.score = updateDecimal(card.score, value)}"
                />
                </div>
            </div>
            <!-- <div class="grid grid-cols-1 w-full gap-3 text-5xl font-bold">
              <div
                class="flex-1 min-h-[100px] bg-orange-300 dark:bg-transparent text-gray-700 dark:text-gray-100 text-2xl rounded flex items-center justify-center border border-orange-400
                  active:bg-orange-400 active:border active:text-gray-100"
                @click="minusByZeroOne(card)"
              >
                - 0.1
              </div>
              <div
                class="flex-1 min-h-[100px] bg-orange-300 dark:bg-transparent text-gray-700 dark:text-gray-100 text-2xl rounded flex items-center justify-center border border-orange-400
                  active:bg-orange-400 active:border active:text-gray-100"
                @click="addByZeroOne(card, 10)"
              >
                + 0.1
              </div>
            </div> -->
             
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
