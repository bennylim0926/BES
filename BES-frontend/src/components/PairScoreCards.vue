<script setup>
import { ref, computed, nextTick, onMounted } from 'vue';

const props = defineProps({
  cards: { type: Array, required: true },
});

const scrollRef = ref(null)
const currentIndex = ref(0)

// Group cards into pairs by audition number (1&2, 3&4, ...)
// Gap slots carry { _placeholder: true, auditionNumber } so they can be labelled.
const pairs = computed(() => {
  const sorted = [...props.cards].sort((a, b) => a.auditionNumber - b.auditionNumber)
  if (!sorted.length) return []
  const maxNum = sorted[sorted.length - 1].auditionNumber
  const result = []
  for (let i = 1; i <= maxNum; i += 2) {
    result.push([
      sorted.find(p => p.auditionNumber === i) ?? { _placeholder: true, auditionNumber: i },
      sorted.find(p => p.auditionNumber === i + 1) ?? { _placeholder: true, auditionNumber: i + 1 },
    ])
  }
  return result
})

const observePairs = async () => {
  await nextTick()
  if (!scrollRef.value) return
  const slides = scrollRef.value.querySelectorAll('[data-pair]')
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
  slides.forEach((slide) => observer.observe(slide))
}

onMounted(observePairs)

const updateDecimal = (score, num) => {
  if (score === 10) return 10
  const wholeNum = Math.floor(score)
  return wholeNum + num / 10
}

const isActive = (pairIdx) => pairIdx === currentIndex.value
</script>

<template>
  <div class="w-full h-auto">
    <!-- Pair dots -->
    <div v-if="pairs.length > 1" class="flex justify-center gap-1 mb-3">
      <div
        v-for="(_, i) in pairs"
        :key="i"
        class="h-1 rounded-full transition-all duration-300"
        :class="i === currentIndex ? 'w-6 bg-primary-500' : 'w-1.5 bg-surface-600'"
      ></div>
    </div>

    <div
      ref="scrollRef"
      v-if="pairs.length"
      class="flex overflow-x-auto snap-x snap-mandatory scroll-smooth gap-4 px-2 pb-4"
      style="scrollbar-width: none;"
    >
      <div
        v-for="(pair, pairIdx) in pairs"
        :key="pairIdx"
        :data-index="pairIdx"
        data-pair
        class="flex-shrink-0 w-[96%] snap-center"
      >
        <!-- Pair header: round indicator + Last badge -->
        <div class="flex items-center justify-between mb-2 px-1">
          <span class="text-xs font-semibold text-content-muted uppercase tracking-wider">
            Round {{ pairIdx + 1 }} of {{ pairs.length }}
          </span>
          <span
            v-if="pairIdx === pairs.length - 1"
            class="text-xs px-2 py-0.5 rounded-full bg-surface-600/60 text-surface-300 border border-surface-500/40 font-semibold uppercase tracking-wider"
          >
            Last
          </span>
        </div>
        <div class="flex flex-col gap-4">
          <!-- Render each slot (card or null placeholder) -->
          <template v-for="(card, slotIdx) in pair" :key="slotIdx">
            <!-- Gap placeholder — audition number not registered -->
            <div
              v-if="card._placeholder"
              class="rounded-2xl border-2 border-dashed border-amber-500/50 bg-amber-500/5 p-6 flex items-center gap-4 min-h-[100px]"
            >
              <div class="w-12 h-12 rounded-xl bg-amber-500/15 border border-amber-500/30 flex items-center justify-center flex-shrink-0">
                <span class="text-lg font-source font-bold text-amber-400">{{ card.auditionNumber }}</span>
              </div>
              <div>
                <div class="text-sm font-bold text-amber-400 uppercase tracking-wider mb-0.5">Not Registered</div>
                <div class="text-xs text-amber-300/60">Audition #{{ card.auditionNumber }} has no participant</div>
              </div>
            </div>

            <!-- Scoring card -->
            <div
              v-else
              class="rounded-2xl border p-5 transition-all duration-200"
              :class="isActive(pairIdx)
                ? 'bg-surface-800 border-primary-500/40 shadow-[0_0_0_1px_rgba(6,182,212,0.2),0_8px_32px_rgba(6,182,212,0.12)]'
                : 'bg-surface-900 border-surface-600/30 opacity-50'"
            >
              <!-- Header: audition info + score -->
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
                <div class="flex-shrink-0 ml-4">
                  <div
                    class="text-6xl font-source font-extrabold tabular-nums leading-none"
                    :class="card.score === 0 ? 'text-surface-500' : 'text-primary-400'"
                  >
                    {{ card.score === 0 ? '—' : card.score }}
                  </div>
                </div>
              </div>

              <div class="h-px bg-surface-600/50 mb-4"></div>

              <!-- Scoring controls -->
              <!-- Full score button -->
              <button
                :disabled="!isActive(pairIdx)"
                @click="card.score = 10"
                class="w-full py-3.5 mb-4 rounded-xl text-sm font-bold border-2 border-primary-600 text-primary-400
                       hover:bg-primary-600 hover:text-white active:bg-primary-700 active:text-white
                       disabled:opacity-30 disabled:cursor-not-allowed transition-all duration-200"
              >
                10 — Full Score
              </button>

              <!-- Scoring grid -->
              <div class="grid grid-cols-2 gap-3">
                <!-- Whole numbers -->
                <div class="bg-surface-700/40 border border-surface-600/50 rounded-xl p-3">
                  <div class="text-xs font-bold text-content-secondary uppercase tracking-widest mb-2.5 text-center">
                    Whole
                  </div>
                  <div class="grid grid-cols-3 gap-2">
                    <button
                      v-for="value in 9"
                      :key="'w'+value"
                      :disabled="!isActive(pairIdx)"
                      @click="card.score = Number(value)"
                      class="py-3 rounded-xl text-base font-bold border transition-all duration-150"
                      :class="Math.floor(card.score) === value && card.score === value
                        ? 'bg-primary-600 text-white border-primary-600 shadow-[0_0_8px_rgba(6,182,212,0.4)]'
                        : 'bg-surface-600/60 border-surface-500/50 text-content-primary hover:border-primary-500/60 hover:bg-surface-600 disabled:opacity-30'"
                    >
                      {{ value }}
                    </button>
                  </div>
                </div>

                <!-- Decimals -->
                <div class="bg-primary-500/8 border border-primary-500/25 rounded-xl p-3">
                  <div class="text-xs font-bold text-primary-400 uppercase tracking-widest mb-2.5 text-center">
                    Decimal
                  </div>
                  <div class="grid grid-cols-3 gap-2">
                    <button
                      v-for="value in 9"
                      :key="'d'+value"
                      :disabled="!isActive(pairIdx)"
                      @click="card.score = updateDecimal(card.score, value)"
                      class="py-3 rounded-xl text-base font-semibold border transition-all duration-150"
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
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
