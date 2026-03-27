<script setup>
import { ref, computed, nextTick, onMounted } from 'vue';

const props = defineProps({
  cards:        { type: Array,  required: true },
  feedbackData: { type: Object, default: () => new Map() }, // Map of auditionNumber → { tagIds, note, tagLabels }
  criteria:     { type: Array,  default: () => [] },        // [{id, name, weight}]
});

const emit = defineEmits(['open-feedback', 'remove-tag']);

const scrollRef = ref(null)
const currentIndex = ref(0)

const hasCriteria = computed(() => props.criteria.length > 0)

// Per-card active criterion tab: keyed by auditionNumber
const activeCriterion = ref({})
const getActiveCriterion = (auditionNumber) => activeCriterion.value[auditionNumber] ?? props.criteria[0]?.name ?? null
const setActiveCriterion = (auditionNumber, name) => { activeCriterion.value = { ...activeCriterion.value, [auditionNumber]: name } }

const totalWeight = computed(() => {
  if (!hasCriteria.value) return 1
  const hasWeights = props.criteria.some(c => c.weight != null)
  if (!hasWeights) return props.criteria.length
  return props.criteria.reduce((sum, c) => sum + (c.weight ?? 1), 0)
})

function criteriaScore(card, criterionName) {
  return card.criteriaScores?.[criterionName] ?? 0
}

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

// Group cards into pairs by audition number (1&2, 3&4, ...)
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

const updateCriteriaDecimal = (card, criterionName, num) => {
  const current = criteriaScore(card, criterionName)
  const newVal = current === 10 ? 10 : Math.floor(current) + num / 10
  setCriteriaScore(card, criterionName, newVal)
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
        <!-- Pair header -->
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
          <template v-for="(card, slotIdx) in pair" :key="slotIdx">
            <!-- Gap placeholder -->
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
              <!-- Header -->
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
                <div class="flex-shrink-0 ml-4 flex flex-col items-end gap-2">
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
                    <div class="text-xs font-bold text-content-muted uppercase tracking-widest mb-1 text-right">
                      {{ hasCriteria ? 'Avg' : 'Score' }}
                    </div>
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

              <!-- ── Multi-criteria mode (tabbed) ── -->
              <template v-if="hasCriteria">
                <!-- Tab bar -->
                <div class="flex gap-1 mb-4 overflow-x-auto" style="scrollbar-width: none;">
                  <button
                    v-for="criterion in criteria"
                    :key="criterion.id"
                    @click="setActiveCriterion(card.auditionNumber, criterion.name)"
                    class="flex-shrink-0 flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold border transition-all duration-150"
                    :class="getActiveCriterion(card.auditionNumber) === criterion.name
                      ? 'bg-primary-600 text-white border-primary-600'
                      : 'bg-surface-700/60 border-surface-600/50 text-surface-300 hover:border-primary-500/50 hover:text-primary-300'"
                  >
                    {{ criterion.name }}
                    <span
                      v-if="criteriaScore(card, criterion.name) > 0"
                      class="font-source tabular-nums"
                      :class="getActiveCriterion(card.auditionNumber) === criterion.name ? 'text-white/80' : 'text-primary-400'"
                    >{{ criteriaScore(card, criterion.name) }}</span>
                  </button>
                </div>

                <!-- Active criterion panel -->
                <template v-for="criterion in criteria" :key="criterion.id">
                  <div v-if="getActiveCriterion(card.auditionNumber) === criterion.name">
                    <!-- Criterion score + label -->
                    <div class="flex items-center justify-between mb-3">
                      <div class="flex items-center gap-2">
                        <span class="text-xs font-bold text-content-secondary uppercase tracking-widest">{{ criterion.name }}</span>
                        <span v-if="criterion.weight != null" class="text-xs text-primary-400/70">×{{ criterion.weight }}</span>
                      </div>
                      <span
                        class="font-source font-extrabold text-3xl tabular-nums leading-none"
                        :class="criteriaScore(card, criterion.name) === 0 ? 'text-surface-500' : 'text-primary-400'"
                      >
                        {{ criteriaScore(card, criterion.name) === 0 ? '—' : criteriaScore(card, criterion.name) }}
                      </span>
                    </div>

                    <button
                      :disabled="!isActive(pairIdx)"
                      @click="setCriteriaScore(card, criterion.name, 10)"
                      class="w-full py-2.5 mb-3 rounded-xl text-sm font-bold border-2 border-primary-600 text-primary-400
                             hover:bg-primary-600 hover:text-white active:bg-primary-700 active:text-white
                             disabled:opacity-30 disabled:cursor-not-allowed transition-all duration-200"
                    >
                      {{ 10 }} — Full Score
                    </button>

                    <div class="grid grid-cols-2 gap-2">
                      <div class="bg-surface-700/40 border border-surface-600/50 rounded-xl p-2.5">
                        <div class="text-xs font-bold text-content-secondary uppercase tracking-widest mb-2 text-center">Whole</div>
                        <div class="grid grid-cols-3 gap-2">
                          <button
                            v-for="value in 9"
                            :key="'w'+criterion.name+value"
                            :disabled="!isActive(pairIdx)"
                            @click="setCriteriaScore(card, criterion.name, Number(value))"
                            class="py-3 rounded-xl text-base font-bold border transition-all duration-150"
                            :class="Math.floor(criteriaScore(card, criterion.name)) === value && criteriaScore(card, criterion.name) === value
                              ? 'bg-primary-600 text-white border-primary-600 shadow-[0_0_8px_rgba(6,182,212,0.4)]'
                              : 'bg-surface-600/60 border-surface-500/50 text-content-primary hover:border-primary-500/60 hover:bg-surface-600 disabled:opacity-30'"
                          >{{ value }}</button>
                        </div>
                      </div>
                      <div class="bg-primary-500/8 border border-primary-500/25 rounded-xl p-2.5">
                        <div class="text-xs font-bold text-primary-400 uppercase tracking-widest mb-2 text-center">Decimal</div>
                        <div class="grid grid-cols-3 gap-2">
                          <button
                            v-for="value in 9"
                            :key="'d'+criterion.name+value"
                            :disabled="!isActive(pairIdx)"
                            @click="updateCriteriaDecimal(card, criterion.name, value)"
                            class="py-3 rounded-xl text-base font-semibold border transition-all duration-150"
                            :class="(criteriaScore(card, criterion.name) * 10 % 10).toFixed(0) == value
                              ? 'bg-primary-500/30 text-primary-300 border-primary-500/60 shadow-[0_0_8px_rgba(6,182,212,0.25)]'
                              : 'bg-primary-500/5 border-primary-500/15 text-primary-300/70 hover:border-primary-500/40 hover:bg-primary-500/15 disabled:opacity-30'"
                          >.{{ value }}</button>
                        </div>
                      </div>
                    </div>
                  </div>
                </template>
              </template>

              <!-- ── Single-score mode (legacy) ── -->
              <template v-else>
                <button
                  :disabled="!isActive(pairIdx)"
                  @click="card.score = 10"
                  class="w-full py-3.5 mb-4 rounded-xl text-sm font-bold border-2 border-primary-600 text-primary-400
                         hover:bg-primary-600 hover:text-white active:bg-primary-700 active:text-white
                         disabled:opacity-30 disabled:cursor-not-allowed transition-all duration-200"
                >
                  10 — Full Score
                </button>
                <div class="grid grid-cols-2 gap-3">
                  <div class="bg-surface-700/40 border border-surface-600/50 rounded-xl p-3">
                    <div class="text-xs font-bold text-content-secondary uppercase tracking-widest mb-2.5 text-center">Whole</div>
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
                      >{{ value }}</button>
                    </div>
                  </div>
                  <div class="bg-primary-500/8 border border-primary-500/25 rounded-xl p-3">
                    <div class="text-xs font-bold text-primary-400 uppercase tracking-widest mb-2.5 text-center">Decimal</div>
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
                      >.{{ value }}</button>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>
