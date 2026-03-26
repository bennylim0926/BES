<script setup>
import { ref, nextTick, onMounted } from 'vue';
import { capsFirstLetter } from '@/utils/utils';

const props = defineProps({
  cards:        { type: Array,  required: true },
  feedbackData: { type: Object, default: () => new Map() }, // Map of auditionNumber → { tagIds, note, tagLabels }
});

const emit = defineEmits(['update:cards', 'open-feedback', 'remove-tag']);

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
          class="rounded-2xl border p-5 md:p-8 transition-all duration-200"
          :class="idx === currentIndex
            ? 'bg-surface-800 border-primary-500/40 shadow-[0_0_0_1px_rgba(6,182,212,0.2),0_8px_32px_rgba(6,182,212,0.12)]'
            : 'bg-surface-900 border-surface-600/30 opacity-50'"
        >
          <!-- Participant info -->
          <div class="flex items-start justify-between mb-5">
            <div>
              <div class="inline-flex items-center px-2.5 py-1 rounded-lg bg-surface-700/60 border border-surface-600/50 mb-2">
                <span class="text-xs font-bold text-primary-400 uppercase tracking-widest">
                  Audition #{{ card.auditionNumber }}
                </span>
              </div>
              <h3 class="font-heading font-bold text-2xl text-content-primary leading-tight">
                {{ card.participantName }}
              </h3>
            </div>
            <!-- Score display + feedback button -->
            <div class="text-right flex-shrink-0 ml-4 flex flex-col items-end gap-2">
              <!-- Feedback button -->
              <button
                @click.stop="emit('open-feedback', card)"
                class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-medium border transition-all duration-150"
                :class="feedbackData?.get(card.auditionNumber)
                  ? 'bg-emerald-500/20 border-emerald-500/50 text-emerald-400'
                  : 'bg-surface-700/60 border-surface-600/50 text-surface-400 hover:text-primary-400 hover:border-primary-500/50'"
                title="Leave feedback"
              >
                <i class="pi pi-comment text-xs" />
                <span>{{ feedbackData?.get(card.auditionNumber) ? 'Edit Feedback' : 'Feedback' }}</span>
              </button>
              <!-- Score display -->
              <div>
                <div class="text-xs font-bold text-content-muted uppercase tracking-widest mb-1">Score</div>
                <div
                  class="text-6xl font-source font-extrabold tabular-nums leading-none"
                  :class="card.score === 0 ? 'text-surface-500' : 'text-primary-400'"
                >
                  {{ card.score === 0 ? '—' : card.score }}
                </div>
              </div>
            </div>
          </div>

          <!-- Feedback preview -->
          <div
            v-if="feedbackData?.get(card.auditionNumber)"
            class="mb-4 p-3 rounded-xl bg-surface-700/30 border border-surface-600/40"
          >
            <!-- Tags -->
            <div
              v-if="feedbackData.get(card.auditionNumber).tagLabels?.length"
              class="flex flex-wrap gap-1.5"
              :class="feedbackData.get(card.auditionNumber).note ? 'mb-2' : ''"
            >
              <span
                v-for="tag in feedbackData.get(card.auditionNumber).tagLabels"
                :key="tag.id"
                class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-primary-500/15 text-primary-300 border border-primary-500/30"
              >
                {{ tag.label }}
                <button
                  @click.stop="emit('remove-tag', { auditionNumber: card.auditionNumber, tagId: tag.id })"
                  class="ml-0.5 text-primary-400/50 hover:text-red-400 transition-colors"
                  title="Remove tag"
                >
                  <i class="pi pi-times text-[10px]" />
                </button>
              </span>
            </div>
            <!-- Note -->
            <div v-if="feedbackData.get(card.auditionNumber).note" class="flex items-start gap-2">
              <i class="pi pi-align-left text-xs text-surface-400 mt-0.5 flex-shrink-0" />
              <p class="text-xs text-surface-300 leading-relaxed flex-1 line-clamp-2">
                {{ feedbackData.get(card.auditionNumber).note }}
              </p>
              <button
                @click.stop="emit('open-feedback', card)"
                class="text-surface-400 hover:text-primary-400 transition-colors flex-shrink-0"
                title="Edit note"
              >
                <i class="pi pi-pencil text-xs" />
              </button>
            </div>
          </div>

          <div class="h-px bg-surface-600/50 mb-4"></div>

          <!-- Full score button -->
          <button
            :disabled="idx !== currentIndex"
            @click="card.score = 10"
            class="w-full py-3 mb-4 rounded-xl text-sm font-bold border-2 border-primary-600 text-primary-400
                   hover:bg-primary-600 hover:text-white active:bg-primary-700 active:text-white
                   disabled:opacity-30 disabled:cursor-not-allowed
                   transition-all duration-200 btn-glow"
          >
            10 — Full Score
          </button>

          <!-- Scoring grid -->
          <div class="grid grid-cols-2 gap-3">
            <!-- Whole numbers 1-9 -->
            <div class="bg-surface-700/40 border border-surface-600/50 rounded-xl p-3">
              <div class="text-xs font-bold text-content-secondary uppercase tracking-widest mb-2.5 text-center">
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
                    ? 'bg-primary-600 text-white border-primary-600 shadow-[0_0_8px_rgba(6,182,212,0.4)]'
                    : 'bg-surface-600/60 border-surface-500/50 text-content-primary hover:border-primary-500/60 hover:bg-surface-600 disabled:opacity-30'"
                >
                  {{ value }}
                </button>
              </div>
            </div>

            <!-- Decimals .1-.9 -->
            <div class="bg-primary-500/8 border border-primary-500/25 rounded-xl p-3">
              <div class="text-xs font-bold text-primary-400 uppercase tracking-widest mb-2.5 text-center">
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
                    ? 'bg-primary-500/30 text-primary-300 border-primary-500/60 shadow-[0_0_8px_rgba(6,182,212,0.25)]'
                    : 'bg-primary-500/5 border-primary-500/15 text-primary-300/70 hover:border-primary-500/40 hover:bg-primary-500/15 disabled:opacity-30'"
                >
                  .{{ value }}
                </button>
              </div>
            </div>
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
              : 'w-1.5 bg-surface-600'"
          ></div>
        </div>
      </div>
    </div>
  </div>
</template>
