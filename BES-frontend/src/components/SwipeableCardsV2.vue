<script setup>
import { ref, nextTick, onMounted, computed } from 'vue';

const props = defineProps({
  cards:        { type: Array,  required: true },
  feedbackData: { type: Object, default: () => new Map() },
  criteria:     { type: Array,  default: () => [] },
});

const emit = defineEmits(['update:cards', 'open-feedback', 'remove-tag', 'submit', 'reset', 'jump']);

const scrollRef    = ref(null)
const currentIndex = ref(0)

const activeCriterion    = ref({})
const getActiveCriterion = (idx)       => activeCriterion.value[idx] ?? props.criteria[0]?.name ?? null
const setActiveCriterion = (idx, name) => { activeCriterion.value = { ...activeCriterion.value, [idx]: name } }

const hasCriteria = computed(() => props.criteria.length > 0)
const totalWeight = computed(() => {
  if (!hasCriteria.value) return 1
  const hasWeights = props.criteria.some(c => c.weight != null)
  if (!hasWeights) return props.criteria.length
  return props.criteria.reduce((sum, c) => sum + (c.weight ?? 1), 0)
})

function criteriaScore(card, criterionName) { return card.criteriaScores?.[criterionName] ?? 0 }

function setCriteriaScore(card, criterionName, value) {
  if (!card.criteriaScores) card.criteriaScores = {}
  card.criteriaScores[criterionName] = value
  card.score = computeAggregate(card)
}

function computeAggregate(card) {
  if (!hasCriteria.value) return card.score
  const hasWeights = props.criteria.some(c => c.weight != null)
  let weighted = 0
  props.criteria.forEach(c => {
    const s = card.criteriaScores?.[c.name] ?? 0
    const w = hasWeights ? (c.weight ?? 1) : 1
    weighted += s * w
  })
  return Number((weighted / totalWeight.value).toFixed(2))
}

const observeCards = async () => {
  await nextTick()
  const cards = scrollRef.value.querySelectorAll('[data-card]')
  const observer = new IntersectionObserver(
    (entries) => { entries.forEach((e) => { if (e.isIntersecting) currentIndex.value = Number(e.target.getAttribute('data-index')) }) },
    { root: scrollRef.value, threshold: 0.6 }
  )
  cards.forEach((c) => observer.observe(c))
}

const updateDecimal = (score, num) => score === 10 ? 10 : Math.floor(score) + num / 10
const updateCriteriaDecimal = (card, criterionName, num) => {
  const newVal = criteriaScore(card, criterionName) === 10 ? 10 : Math.floor(criteriaScore(card, criterionName)) + num / 10
  setCriteriaScore(card, criterionName, newVal)
}

const aggregateDisplay = computed(() => {
  if (!hasCriteria.value) return null
  const card = props.cards[currentIndex.value]
  if (!card) return null
  return computeAggregate(card)
})

onMounted(observeCards)
</script>

<template>
  <div class="w-full relative h-full bg-surface-900 touch-manipulation" style="padding-bottom: 72px; overflow: hidden;">

    <!-- Card scroll area -->
    <div
      ref="scrollRef"
      v-if="props.cards && props.cards.length"
      class="flex overflow-x-auto snap-x snap-mandatory scroll-smooth gap-2 px-2 pt-2 pb-4"
      style="scrollbar-width: none;"
    >
      <div
        v-for="(card, idx) in props.cards"
        :key="idx"
        :data-index="idx"
        data-card
        class="flex-shrink-0 snap-center"
        style="width: 100%; scroll-snap-stop: always;"
      >
        <!-- Score card -->
        <div
          class="rounded-2xl border p-3 transition-all duration-200"
          :class="[idx === currentIndex ? 'bg-surface-800 border-amber-500/25' : 'bg-surface-900 border-white/5 opacity-40']"
          :style="idx === currentIndex
            ? { boxShadow: '0 0 0 1px rgba(245,158,11,0.12), 0 8px 32px rgba(0,0,0,0.7)' }
            : {}"
        >
          <div class="score-card-body">

            <!-- ── Info column (left in landscape) ── -->
            <div class="score-card-info">

              <!-- Participant header -->
              <div class="flex items-start justify-between mb-2">
                <div>
                  <span class="font-anton text-amber-400 leading-none block" style="font-size: 2.8rem;">#{{ card.auditionNumber }}</span>
                  <div class="font-heading font-bold text-xl text-white leading-tight mt-0.5">{{ card.participantName }}</div>
                </div>
                <!-- Score display -->
                <div class="text-right flex-shrink-0 ml-3">
                  <div class="text-[9px] font-bold text-white/25 uppercase tracking-widest mb-0.5">
                    {{ hasCriteria ? 'AVG' : 'SCORE' }}
                  </div>
                  <div
                    class="font-anton tabular-nums leading-none"
                    style="font-size: 3.5rem;"
                    :class="card.score === 0 ? 'text-white/20' : 'text-amber-400'"
                  >{{ card.score === 0 ? '—' : card.score }}</div>
                </div>
              </div>

              <!-- Feedback button -->
              <button
                @click.stop="emit('open-feedback', card)"
                class="mb-2 flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-bold border transition-all duration-150 active:scale-95"
                :class="feedbackData?.get(card.auditionNumber)
                  ? 'bg-green-500/15 border-green-500/35 text-green-400'
                  : 'bg-white/4 border-white/10 text-white/35 hover:text-white/60 hover:border-white/20'"
              >
                <i class="pi pi-comment text-xs" />
                {{ feedbackData?.get(card.auditionNumber) ? 'Edit Feedback' : 'Feedback' }}
                <span v-if="feedbackData?.get(card.auditionNumber)" class="ml-1 w-1.5 h-1.5 rounded-full bg-green-400 inline-block"></span>
              </button>

              <!-- Feedback tag preview -->
              <div
                v-if="feedbackData?.get(card.auditionNumber)"
                class="mb-2 p-2 rounded-xl border border-white/6 bg-white/[0.02]"
              >
                <div v-if="feedbackData.get(card.auditionNumber).tagLabels?.length" class="flex flex-wrap gap-1.5 mb-1">
                  <span
                    v-for="tag in feedbackData.get(card.auditionNumber).tagLabels"
                    :key="tag.id"
                    class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-500/10 text-amber-300 border border-amber-500/20"
                  >
                    {{ tag.label }}
                    <button @click.stop="emit('remove-tag', { auditionNumber: card.auditionNumber, tagId: tag.id })" class="text-amber-400/40 hover:text-red-400 transition-colors">
                      <i class="pi pi-times text-[9px]" />
                    </button>
                  </span>
                </div>
                <div v-if="feedbackData.get(card.auditionNumber).note" class="text-xs text-white/35 line-clamp-2">
                  {{ feedbackData.get(card.auditionNumber).note }}
                </div>
              </div>

              <!-- Criteria tabs (multi-criteria mode) -->
              <template v-if="hasCriteria">
                <div class="h-px mb-2 bg-white/5"></div>
                <div class="flex gap-0 mb-2 overflow-x-auto border-b border-white/[0.06]" style="scrollbar-width: none;">
                  <button
                    v-for="criterion in criteria"
                    :key="criterion.id"
                    @click="setActiveCriterion(idx, criterion.name)"
                    class="flex-shrink-0 flex items-center gap-1 px-3 py-1.5 text-xs font-bold border-b-2 transition-all duration-150 -mb-px"
                    :class="getActiveCriterion(idx) === criterion.name
                      ? 'border-amber-400 text-amber-400'
                      : 'border-transparent text-white/25 hover:text-white/50'"
                  >
                    {{ criterion.name }}
                    <span v-if="criteriaScore(card, criterion.name) > 0" class="font-source tabular-nums text-amber-300/60 text-[10px]">
                      {{ criteriaScore(card, criterion.name) }}
                    </span>
                  </button>
                </div>
                <template v-for="criterion in criteria" :key="criterion.id">
                  <div v-if="getActiveCriterion(idx) === criterion.name" class="flex items-center justify-between mb-2">
                    <span class="text-[9px] font-bold text-white/25 uppercase tracking-widest">{{ criterion.name }}<span v-if="criterion.weight != null" class="text-amber-400/40 ml-1">×{{ criterion.weight }}</span></span>
                    <span class="font-anton text-2xl tabular-nums" :class="criteriaScore(card, criterion.name) === 0 ? 'text-white/15' : 'text-amber-400'">
                      {{ criteriaScore(card, criterion.name) === 0 ? '—' : criteriaScore(card, criterion.name) }}
                    </span>
                  </div>
                </template>
              </template>

            </div><!-- /score-card-info -->

            <!-- ── Keypad column (right in landscape) ── -->
            <div class="score-card-keypad">

              <!-- ── Multi-criteria mode ── -->
              <template v-if="hasCriteria">
                <template v-for="criterion in criteria" :key="criterion.id">
                  <div v-if="getActiveCriterion(idx) === criterion.name">
                    <button
                      :disabled="idx !== currentIndex"
                      @click="setCriteriaScore(card, criterion.name, 10)"
                      class="w-full py-2 mb-2 font-bold text-sm border transition-all duration-150 active:scale-[0.98] disabled:opacity-20 disabled:cursor-not-allowed"
                      style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.35); color: rgb(245,158,11);"
                    >10 — Full Score</button>
                    <div class="grid grid-cols-2 gap-1.5">
                      <div class="rounded-xl p-1.5 bg-white/[0.03] border border-white/[0.05]">
                        <div class="text-[9px] font-bold text-white/20 uppercase tracking-widest mb-1.5 text-center">Whole</div>
                        <div class="grid grid-cols-3 gap-1">
                          <button
                            v-for="value in 9" :key="'w'+criterion.name+value"
                            :disabled="idx !== currentIndex"
                            @click="setCriteriaScore(card, criterion.name, Number(value))"
                            class="py-4 text-sm font-bold transition-all duration-100 active:scale-95 disabled:opacity-20"
                            :class="Math.floor(criteriaScore(card, criterion.name)) === value && criteriaScore(card, criterion.name) === value ? 'text-black' : 'text-white/55 hover:text-white'"
                            :style="Math.floor(criteriaScore(card, criterion.name)) === value && criteriaScore(card, criterion.name) === value
                              ? 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgb(245,158,11);'
                              : 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(255,255,255,0.05);'"
                          >{{ value }}</button>
                        </div>
                      </div>
                      <div class="rounded-xl p-1.5 bg-amber-500/[0.03] border border-amber-500/[0.10]">
                        <div class="text-[9px] font-bold text-amber-400/35 uppercase tracking-widest mb-1.5 text-center">Decimal</div>
                        <div class="grid grid-cols-3 gap-1">
                          <button
                            v-for="value in 9" :key="'d'+criterion.name+value"
                            :disabled="idx !== currentIndex"
                            @click="updateCriteriaDecimal(card, criterion.name, value)"
                            class="py-4 text-sm font-semibold transition-all duration-100 active:scale-95 disabled:opacity-20"
                            :class="(criteriaScore(card, criterion.name) * 10 % 10).toFixed(0) == value ? 'text-black' : 'text-amber-300/45 hover:text-amber-300'"
                            :style="(criteriaScore(card, criterion.name) * 10 % 10).toFixed(0) == value
                              ? 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgb(245,158,11);'
                              : 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.06);'"
                          >.{{ value }}</button>
                        </div>
                      </div>
                    </div>
                  </div>
                </template>
              </template>

              <!-- ── Single-score mode ── -->
              <template v-else>
                <button
                  :disabled="idx !== currentIndex"
                  @click="card.score = 10"
                  class="w-full py-2 mb-2 font-bold text-sm border transition-all duration-150 active:scale-[0.98] disabled:opacity-20 disabled:cursor-not-allowed"
                  style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.35); color: rgb(245,158,11);"
                >10 — Full Score</button>
                <div class="grid grid-cols-2 gap-1.5">
                  <div class="rounded-xl p-1.5 bg-white/[0.03] border border-white/[0.05]">
                    <div class="text-[9px] font-bold text-white/20 uppercase tracking-widest mb-1.5 text-center">Whole</div>
                    <div class="grid grid-cols-3 gap-1">
                      <button
                        v-for="value in 9" :key="'w'+value"
                        :disabled="idx !== currentIndex"
                        @click="card.score = Number(value)"
                        class="py-4 text-sm font-bold transition-all duration-100 active:scale-95 disabled:opacity-20"
                        :class="Math.floor(card.score) === value && card.score === value ? 'text-black' : 'text-white/55 hover:text-white'"
                        :style="Math.floor(card.score) === value && card.score === value
                          ? 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgb(245,158,11);'
                          : 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(255,255,255,0.05);'"
                      >{{ value }}</button>
                    </div>
                  </div>
                  <div class="rounded-xl p-1.5 bg-amber-500/[0.03] border border-amber-500/[0.10]">
                    <div class="text-[9px] font-bold text-amber-400/35 uppercase tracking-widest mb-1.5 text-center">Decimal</div>
                    <div class="grid grid-cols-3 gap-1">
                      <button
                        v-for="value in 9" :key="'d'+value"
                        :disabled="idx !== currentIndex"
                        @click="card.score = updateDecimal(card.score, value)"
                        class="py-4 text-sm font-semibold transition-all duration-100 active:scale-95 disabled:opacity-20"
                        :class="(card.score * 10 % 10).toFixed(0) == value ? 'text-black' : 'text-amber-300/45 hover:text-amber-300'"
                        :style="(card.score * 10 % 10).toFixed(0) == value
                          ? 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgb(245,158,11);'
                          : 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.06);'"
                      >.{{ value }}</button>
                    </div>
                  </div>
                </div>
              </template>

            </div><!-- /score-card-keypad -->

          </div><!-- /score-card-body -->
        </div>

        <!-- Swipe dots -->
        <div class="flex justify-center gap-1 mt-2">
          <div
            v-for="(_, i) in props.cards" :key="i"
            class="h-1 rounded-full transition-all duration-300"
            :class="i === currentIndex ? 'w-6 bg-amber-400' : 'w-1.5 bg-white/12'"
          ></div>
        </div>
      </div>
    </div>

    <!-- ── Sticky bottom action bar ── -->
    <div
      class="fixed bottom-0 left-0 right-0 z-30 flex items-center gap-2 px-4 py-3 border-t border-white/[0.07] bg-surface-900"
    >
      <button
        @click="emit('reset')"
        class="flex items-center gap-1.5 px-4 py-2.5 text-sm font-bold border transition-all duration-150 active:scale-95"
        style="clip-path: polygon(5px 0%, 100% 0%, calc(100% - 5px) 100%, 0% 100%); border-color: rgba(255,255,255,0.10); color: rgba(255,255,255,0.35); background: transparent;"
      ><i class="pi pi-undo text-xs"></i> Reset</button>

      <button
        @click="emit('jump')"
        class="flex items-center gap-1.5 px-4 py-2.5 text-sm font-bold border transition-all duration-150 active:scale-95"
        style="clip-path: polygon(5px 0%, 100% 0%, calc(100% - 5px) 100%, 0% 100%); border-color: rgba(255,255,255,0.10); color: rgba(255,255,255,0.35); background: transparent;"
      ><i class="pi pi-search text-xs"></i> Jump</button>

      <button
        @click="emit('submit')"
        class="flex-1 flex items-center justify-center gap-1.5 py-2.5 text-sm font-bold transition-all duration-150 active:scale-[0.98]"
        style="clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%); background: rgb(245,158,11); color: #000;"
      ><i class="pi pi-send text-xs"></i> Submit All</button>

      <div v-if="hasCriteria && aggregateDisplay !== null" class="text-xs font-bold text-amber-400/40 ml-1 shrink-0 font-source tabular-nums">
        AVG {{ aggregateDisplay }}
      </div>
    </div>

  </div>
</template>

<style scoped>
/* ── Landscape: info left, keypad right ─────────────────────────────── */
@media (orientation: landscape) {
  .score-card-body {
    display: grid;
    grid-template-columns: 1fr 1fr;
    column-gap: 16px;
    align-items: start;
  }
}
</style>
