<script setup>
import { ref, nextTick, onMounted } from 'vue';
import { capsFirstLetter } from '@/utils/utils';

const props = defineProps({
  cards: { type: Array, required: true },
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
          currentIndex.value = Number(entry.target.getAttribute('data-index'))
        }
      })
    },
    { root: scrollRef.value, threshold: 0.6 }
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

const updateDecimal = (score, num) => {
  if (score === 10) return 10
  const wholeNum = Math.floor(score)
  return wholeNum + num / 10
}

onMounted(observeCards)
</script>

<template>
  <div class="w-full h-auto">
    <div
      ref="scrollRef"
      v-if="props.cards && props.cards.length"
      class="flex overflow-x-auto snap-x snap-mandatory scroll-smooth gap-4 px-4 pb-4"
      style="scrollbar-width: none;"
    >
      <div
        v-for="(card, idx) in props.cards"
        :key="idx"
        :data-index="idx"
        data-card
        class="flex-shrink-0 w-[88%] md:w-[70%] snap-center"
      >
        <!-- Score card -->
        <div
          class="rounded-2xl border shadow-sm p-5 md:p-8 transition-all duration-200"
          :class="idx === currentIndex
            ? 'bg-white border-primary-200 shadow-md'
            : 'bg-surface-50 border-surface-200 opacity-70'"
        >
          <!-- Participant info -->
          <div class="flex items-center justify-between mb-4">
            <div>
              <div class="text-xs font-semibold text-surface-400 uppercase tracking-wider mb-1">
                Audition #{{ card.auditionNumber }}
              </div>
              <h3 class="font-heading font-bold text-xl text-surface-900">
                {{ card.participantName }}
              </h3>
            </div>
            <!-- Score display -->
            <div class="text-right">
              <div class="text-xs font-semibold text-surface-400 uppercase tracking-wider mb-1">Score</div>
              <div
                class="text-4xl font-heading font-extrabold tabular-nums"
                :class="card.score === 0 ? 'text-surface-300' : 'text-primary-600'"
              >
                {{ card.score === 0 ? '—' : card.score }}
              </div>
            </div>
          </div>

          <div class="h-px bg-surface-100 mb-4"></div>

          <!-- Full score button -->
          <button
            :disabled="idx !== currentIndex"
            @click="card.score = 10"
            class="w-full py-3 mb-4 rounded-xl text-sm font-bold border-2 border-primary-600 text-primary-600
                   hover:bg-primary-600 hover:text-white active:bg-primary-700 active:text-white
                   disabled:opacity-30 disabled:cursor-not-allowed
                   transition-all duration-200"
          >
            10 — Full Score
          </button>

          <!-- Scoring grid -->
          <div class="grid grid-cols-2 gap-3">
            <!-- Whole numbers 1-9 -->
            <div>
              <div class="text-xs font-semibold text-surface-400 uppercase tracking-wider mb-2 text-center">
                Whole
              </div>
              <div class="grid grid-cols-3 gap-1.5">
                <button
                  v-for="value in 9"
                  :key="'w'+value"
                  :disabled="idx !== currentIndex"
                  @click="card.score = Number(value)"
                  class="py-2.5 rounded-xl text-sm font-bold border transition-all duration-150"
                  :class="Math.floor(card.score) === value && card.score === value
                    ? 'bg-primary-600 text-white border-primary-600'
                    : 'bg-white border-surface-200 text-surface-700 hover:border-primary-300 hover:bg-primary-50 disabled:opacity-30'"
                >
                  {{ value }}
                </button>
              </div>
            </div>

            <!-- Decimals .1-.9 -->
            <div>
              <div class="text-xs font-semibold text-surface-400 uppercase tracking-wider mb-2 text-center">
                Decimal
              </div>
              <div class="grid grid-cols-3 gap-1.5">
                <button
                  v-for="value in 9"
                  :key="'d'+value"
                  :disabled="idx !== currentIndex"
                  @click="card.score = updateDecimal(card.score, value)"
                  class="py-2.5 rounded-xl text-sm font-semibold border transition-all duration-150"
                  :class="(card.score * 10 % 10).toFixed(0) == value
                    ? 'bg-surface-800 text-white border-surface-800'
                    : 'bg-white border-surface-200 text-surface-600 hover:border-surface-300 hover:bg-surface-50 disabled:opacity-30'"
                >
                  .{{ value }}
                </button>
              </div>
            </div>
          </div>

          <!-- Fine-tune: ±0.1 -->
          <div class="flex items-center justify-center gap-3 mt-4">
            <button
              :disabled="idx !== currentIndex"
              @click="minusByZeroOne(card)"
              class="w-10 h-10 rounded-full border border-surface-200 flex items-center justify-center
                     text-surface-600 hover:border-surface-400 hover:bg-surface-50
                     disabled:opacity-30 disabled:cursor-not-allowed transition-all"
            >
              <i class="pi pi-minus text-xs"></i>
            </button>
            <span class="text-xs text-surface-400 font-medium">Fine tune ±0.1</span>
            <button
              :disabled="idx !== currentIndex"
              @click="addByZeroOne(card, 10)"
              class="w-10 h-10 rounded-full border border-surface-200 flex items-center justify-center
                     text-surface-600 hover:border-surface-400 hover:bg-surface-50
                     disabled:opacity-30 disabled:cursor-not-allowed transition-all"
            >
              <i class="pi pi-plus text-xs"></i>
            </button>
          </div>
        </div>

        <!-- Card position indicator -->
        <div class="flex justify-center gap-1 mt-3">
          <div
            v-for="(_, i) in props.cards"
            :key="i"
            class="h-1 rounded-full transition-all duration-300"
            :class="i === currentIndex
              ? 'w-6 bg-primary-500'
              : 'w-1.5 bg-surface-300'"
          ></div>
        </div>
      </div>
    </div>
  </div>
</template>
