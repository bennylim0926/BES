<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue';

const props = defineProps({
  cards:        { type: Array,  required: true },
  feedbackData: { type: Object, default: () => new Map() },
  criteria:     { type: Array,  default: () => [] },
});

const emit = defineEmits(['open-feedback', 'remove-tag', 'submit', 'reset', 'jump', 'score-change']);

const scrollRef    = ref(null)
const currentIndex = ref(0)

const hasCriteria = computed(() => props.criteria.length > 0)

const activeCriterion    = ref({})
const getActiveCriterion = (auditionNumber)       => activeCriterion.value[auditionNumber] ?? props.criteria[0]?.name ?? null
const setActiveCriterion = (auditionNumber, name) => { activeCriterion.value = { ...activeCriterion.value, [auditionNumber]: name } }

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
  emit('score-change', card)
}

function setSingleScore(card, value) {
  card.score = value
  emit('score-change', card)
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

const getPairIndexFromScroll = () => {
  const container = scrollRef.value
  if (!container) return 0
  const snapPoint = container.clientWidth + 8
  const biased = container.scrollLeft + snapPoint * 0.25
  const idx = Math.round(biased / snapPoint)
  return Math.max(0, Math.min(idx, pairs.value.length - 1))
}

const observePairs = async () => {
  await nextTick()
  const container = scrollRef.value
  if (!container) return

  container.addEventListener('scroll', () => {
    currentIndex.value = getPairIndexFromScroll()
  }, { passive: true })
  container.addEventListener('touchend', () => {
    currentIndex.value = getPairIndexFromScroll()
  })
  container.addEventListener('scrollend', () => {
    currentIndex.value = getPairIndexFromScroll()
  })
}

onMounted(observePairs)

const activeParticipantNum = ref(null)
const activePair = computed(() => pairs.value[currentIndex.value] ?? [])
const activeCard = computed(() =>
  activePair.value.find(c => !c._placeholder && c.auditionNumber === activeParticipantNum.value) ?? null
)

watch(currentIndex, () => {
  const first = activePair.value.find(c => !c._placeholder)
  activeParticipantNum.value = first?.auditionNumber ?? null
}, { immediate: true })

const aggregateDisplay = computed(() => {
  if (!hasCriteria.value || !activeCard.value) return null
  return computeAggregate(activeCard.value)
})

const updateDecimal = (score, num) => score === 10 ? 10 : Math.floor(score) + num / 10
const updateCriteriaDecimal = (card, criterionName, num) => {
  const newVal = criteriaScore(card, criterionName) === 10 ? 10 : Math.floor(criteriaScore(card, criterionName)) + num / 10
  setCriteriaScore(card, criterionName, newVal)
}

const isActivePair = (pairIdx) => pairIdx === currentIndex.value
</script>

<template>
  <div class="w-full relative h-full bg-surface-900 touch-manipulation" style="padding-bottom: 72px; overflow: hidden;">

    <!-- Pair dots -->
    <div v-if="pairs.length > 1" class="flex justify-center gap-1 py-2">
      <div
        v-for="(_, i) in pairs" :key="i"
        class="h-1 rounded-full transition-all duration-300"
        :class="i === currentIndex ? 'w-6 bg-amber-400' : 'w-1.5 bg-white/12'"
      ></div>
    </div>

    <!-- Pair slide scroll area -->
    <div
      ref="scrollRef"
      v-if="pairs.length"
      class="flex overflow-x-auto snap-x snap-mandatory scroll-smooth gap-2 px-2 pb-4 items-start h-full"
      style="scrollbar-width: none; overflow-y: hidden;"
    >
      <div
        v-for="(pair, pairIdx) in pairs"
        :key="pairIdx"
        :data-index="pairIdx"
        data-pair
        class="flex-shrink-0 snap-center"
        style="width: 100%; scroll-snap-stop: always;"
      >
        <div
          class="card-hover p-2 relative transition-all duration-200"
          :class="[isActivePair(pairIdx) ? 'border-[color:var(--accent-muted)]' : 'border-white/5 opacity-40']"
          :style="isActivePair(pairIdx)
            ? { boxShadow: '0 0 0 1px var(--accent-muted), 0 8px 32px rgba(0,0,0,0.7)' }
            : {}"
        >

          <!-- Round label -->
          <div class="flex items-center justify-between mb-3 px-1">
            <div class="corner-bar-tl"></div>
            <span class="type-label text-content-muted">
              Round {{ pairIdx + 1 }} of {{ pairs.length }}
            </span>
            <span v-if="pairIdx === pairs.length - 1" class="badge-neutral type-label">Last</span>
          </div>

          <!-- ── Participant selector: detail area + audition buttons ── -->
          <div class="flex gap-2 mb-3">
            <!-- Full detail container for active participant -->
            <div
              class="flex-1 min-w-0 flex items-center gap-2 px-3 py-2 para-chip-sm transition-all duration-200"
              :class="isActivePair(pairIdx) ? 'border-[color:var(--accent-muted)]' : 'opacity-40'"
            >
              <template v-if="activeCard && isActivePair(pairIdx)">
                <span class="type-stat flex-shrink-0 leading-none text-accent" style="font-size: 2rem">#{{ activeCard.auditionNumber }}</span>
                <div class="flex-1 min-w-0">
                  <div class="type-body text-content-primary leading-tight" style="font-size: 1.9rem; overflow-wrap: break-word">{{ activeCard.participantName }}</div>
                  <div v-if="activeCard.memberNames?.length" class="type-label text-content-muted normal-case truncate mt-0.5" style="font-size: 16px; letter-spacing: 0.04em">{{ activeCard.memberNames.join(' · ') }}</div>
                  <div v-if="activeCard.saving" class="inline-flex items-center gap-1 mt-1 px-2 py-0.5 type-label text-xs text-accent/60 normal-case" style="background:rgba(255,255,255,0.05);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)">
                    <i class="pi pi-spin pi-spinner text-[10px]"></i> Saving…
                  </div>
                  <div v-else-if="activeCard.submitted" class="inline-flex items-center gap-1 mt-1 px-2 py-0.5 type-label text-xs text-emerald-400 normal-case" style="background:rgba(16,185,129,0.12);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%);box-shadow:0 0 8px rgba(16,185,129,0.2)">
                    <i class="pi pi-check-circle text-[10px]"></i> Saved
                  </div>
                </div>
                <!-- Score inline -->
                <div class="text-right flex-shrink-0">
                  <div class="type-label text-content-muted mb-0.5">{{ hasCriteria ? 'AVG' : 'SCORE' }}</div>
                  <div
                    class="type-stat tabular-nums leading-none"
                    style="font-size: 2.8rem"
                    :class="activeCard.submitted ? 'text-emerald-400' : activeCard.score === 0 ? 'text-content-muted' : 'text-accent'"
                  >{{ activeCard.score === 0 ? '—' : activeCard.score }}</div>
                </div>
                <span v-if="feedbackData?.get(activeCard.auditionNumber)" class="w-2 h-2 rounded-full bg-green-400 flex-shrink-0 self-start mt-1"></span>
              </template>
              <template v-else>
                <span class="type-label text-content-muted italic">Select a participant</span>
              </template>
            </div>

            <!-- Audition number selector buttons (stacked vertically) -->
            <div class="flex flex-col gap-1 flex-shrink-0">
              <button
                v-for="(card, slotIdx) in pair"
                :key="slotIdx"
                @click="activeParticipantNum = card.auditionNumber"
                :disabled="card._placeholder"
                class="flex-shrink-0 flex flex-col items-center justify-center gap-0.5 px-3 py-1.5 para-chip-sm transition-all duration-150 active:scale-95 disabled:opacity-20 disabled:cursor-not-allowed min-w-0"
                :class="!card._placeholder && activeParticipantNum === card.auditionNumber && isActivePair(pairIdx)
                  ? ''
                  : 'text-content-muted hover:text-content-primary'"
                :style="!card._placeholder && activeParticipantNum === card.auditionNumber && isActivePair(pairIdx)
                  ? 'font-size: 1.6rem; background: var(--accent-color); border-color: var(--accent-color); box-shadow: 0 0 14px var(--accent-muted); color: #111111'
                  : 'font-size: 1.6rem'"
              >
                <span class="type-stat leading-none" style="font-size: 1.6rem; color: inherit">#{{ card.auditionNumber }}</span>
                <!-- Save state dot -->
                <span v-if="!card._placeholder && card.submitted" class="w-1.5 h-1.5 rounded-full bg-emerald-400 flex-shrink-0" style="box-shadow: 0 0 6px rgba(52,211,153,0.5)"></span>
                <i v-else-if="!card._placeholder && card.saving" class="pi pi-spin pi-spinner flex-shrink-0" style="font-size: 10px; color: var(--accent-color); opacity: 0.6"></i>
                <span v-else class="w-1.5 h-1.5 flex-shrink-0"></span>
              </button>
            </div>
          </div>

          <!-- ── Active participant's score + keypad ── -->
          <div v-if="activeCard && isActivePair(pairIdx)">

            <div class="pair-card-body">

              <!-- Info column -->
              <div class="pair-card-info">

                <!-- Scrollable: feedback + criteria -->
                <div class="pair-card-info-scroll">
                  <!-- Feedback button -->
                  <div class="flex justify-center mb-2">
                    <button
                      @click.stop="emit('open-feedback', activeCard)"
                      class="flex items-center gap-1.5 px-2.5 py-1.5 para-chip-sm type-label transition-all duration-150"
                      :class="feedbackData?.get(activeCard.auditionNumber)
                        ? 'text-green-400 border-green-500/35'
                        : 'text-content-muted hover:text-content-primary'"
                    >
                      <i class="pi pi-comment text-xs" />
                      {{ feedbackData?.get(activeCard.auditionNumber) ? 'Edit' : 'Feedback' }}
                      <span v-if="feedbackData?.get(activeCard.auditionNumber)" class="ml-1 w-1.5 h-1.5 rounded-full bg-green-400 inline-block"></span>
                    </button>
                  </div>

                  <!-- Feedback preview: compact on mobile, full on tablet+ -->
                  <template v-if="feedbackData?.get(activeCard.auditionNumber)">
                    <!-- Mobile: compact summary -->
                    <div
                      class="md:hidden mb-2 px-3 py-2 border border-green-500/25 bg-emerald-500/[0.06]"
                      style="clip-path: polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
                    >
                      <div class="flex items-center gap-2">
                        <i class="pi pi-tags text-xs text-emerald-400/70 flex-shrink-0"></i>
                        <span class="type-body text-content-primary" style="font-size: 13px">
                          {{ feedbackData.get(activeCard.auditionNumber).tagLabels?.length || 0 }} tag{{ feedbackData.get(activeCard.auditionNumber).tagLabels?.length !== 1 ? 's' : '' }}
                          <template v-if="feedbackData.get(activeCard.auditionNumber).note"> · note added</template>
                        </span>
                      </div>
                    </div>
                    <!-- Tablet+: full tag chips + note -->
                    <div
                      class="hidden md:block mb-2 p-2 border border-green-500/25 bg-emerald-500/[0.06]"
                      style="clip-path: polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
                    >
                      <div v-if="feedbackData.get(activeCard.auditionNumber).tagLabels?.length" class="flex flex-wrap gap-1.5 mb-1">
                        <span
                          v-for="tag in feedbackData.get(activeCard.auditionNumber).tagLabels"
                          :key="tag.id"
                          class="inline-flex items-center gap-1 px-2 py-0.5 type-label bg-white/[0.12] text-content-primary border border-white/20"
                          style="clip-path: polygon(3px 0%,100% 0%,calc(100% - 3px) 100%,0% 100%)"
                        >
                          {{ tag.label }}
                          <button @click.stop="emit('remove-tag', { auditionNumber: activeCard.auditionNumber, tagId: tag.id })" class="text-content-muted hover:text-content-primary transition-colors">
                            <i class="pi pi-times text-[9px]" />
                          </button>
                        </span>
                      </div>
                      <div v-if="feedbackData.get(activeCard.auditionNumber).note" class="text-xs text-white/65">{{ feedbackData.get(activeCard.auditionNumber).note }}</div>
                    </div>
                  </template>

                  <!-- Criteria scores summary — clickable, active row highlighted -->
                  <template v-if="hasCriteria">
                    <div class="h-px mb-2 bg-white/5"></div>
                    <div class="flex flex-col gap-1.5">
                      <button
                        v-for="criterion in criteria"
                        :key="criterion.id"
                        @click="setActiveCriterion(activeCard.auditionNumber, criterion.name)"
                        class="flex items-center justify-between px-3 py-2 para-chip w-full text-left transition-all duration-150 active:scale-[0.99]"
                        :class="getActiveCriterion(activeCard.auditionNumber) === criterion.name
                          ? 'border-accent'
                          : criteriaScore(activeCard, criterion.name) > 0 ? 'border-accent/25' : 'border-white/[0.07]'"
                        :style="getActiveCriterion(activeCard.auditionNumber) === criterion.name
                          ? 'background: rgba(255,255,255,0.08); border-left: 3px solid var(--accent-color); box-shadow: 0 0 10px var(--accent-subtle)'
                          : 'background: rgba(255,255,255,0.03)'"
                      >
                        <div class="flex items-center gap-1.5">
                          <span class="type-body"
                            :class="getActiveCriterion(activeCard.auditionNumber) === criterion.name ? 'text-content-primary' : 'text-content-muted'"
                          >{{ criterion.name }}</span>
                          <span v-if="criterion.weight != null" class="type-label text-accent/70">×{{ criterion.weight }}</span>
                        </div>
                        <span
                          class="font-source tabular-nums font-bold leading-none"
                          style="font-size: 1.6rem"
                          :class="criteriaScore(activeCard, criterion.name) > 0 ? 'text-accent' : 'text-white/15'"
                        >{{ criteriaScore(activeCard, criterion.name) > 0 ? criteriaScore(activeCard, criterion.name) : '—' }}</span>
                      </button>
                    </div>
                  </template>
                </div><!-- /pair-card-info-scroll -->
              </div><!-- /pair-card-info -->

              <!-- Keypad column -->
              <div class="pair-card-keypad">

                <!-- Multi-criteria mode -->
                <template v-if="hasCriteria">
                  <!-- Criterion selector chips — in keypad column for easy reach -->
                  <div class="flex gap-2 mb-3 overflow-x-auto pb-0.5" style="scrollbar-width: none;">
                    <button
                      v-for="criterion in criteria"
                      :key="'sel-'+criterion.id"
                      @click="setActiveCriterion(activeCard.auditionNumber, criterion.name)"
                      class="flex-shrink-0 flex items-center gap-2 px-4 py-2.5 para-chip transition-all duration-150 active:scale-95"
                      :class="getActiveCriterion(activeCard.auditionNumber) === criterion.name
                        ? 'bg-accent border-accent'
                        : criteriaScore(activeCard, criterion.name) > 0
                          ? 'border-accent/40 text-accent/80'
                          : 'border-white/10 text-content-muted hover:text-content-primary'"
                      :style="getActiveCriterion(activeCard.auditionNumber) === criterion.name
                        ? 'box-shadow: 0 0 14px var(--accent-muted)'
                        : ''"
                    >
                      <span class="type-label leading-none"
                        :class="getActiveCriterion(activeCard.auditionNumber) === criterion.name ? 'text-surface-900' : ''"
                      >{{ criterion.name }}</span>
                      <span
                        v-if="criteriaScore(activeCard, criterion.name) > 0"
                        class="font-source tabular-nums text-sm font-bold leading-none"
                        :class="getActiveCriterion(activeCard.auditionNumber) === criterion.name ? 'text-surface-900' : 'text-accent'"
                      >{{ criteriaScore(activeCard, criterion.name) }}</span>
                      <span v-else class="inline-block w-2 h-2 rounded-full bg-white/15 shrink-0"></span>
                    </button>
                  </div>

                  <template v-for="criterion in criteria" :key="criterion.id">
                    <div v-if="getActiveCriterion(activeCard.auditionNumber) === criterion.name">
                      <button
                        @click="setCriteriaScore(activeCard, criterion.name, 10)"
                        class="w-full py-2 mb-2 font-bold text-sm border transition-all duration-150 active:scale-[0.98]"
                        style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.35); color: rgb(245,158,11);"
                      >10 — Full Score</button>
                      <div class="grid grid-cols-2 gap-1.5">
                        <div class="rounded-xl p-1.5 bg-white/[0.03] border border-white/[0.05]">
                          <div class="text-[9px] font-bold text-white/20 uppercase tracking-widest mb-1.5 text-center">Whole</div>
                          <div class="grid grid-cols-3 gap-1">
                            <button
                              v-for="value in 9" :key="'w'+criterion.name+value"
                              @click="setCriteriaScore(activeCard, criterion.name, Number(value))"
                              class="py-4 text-sm font-bold transition-all duration-100 active:scale-95"
                              :class="Math.floor(criteriaScore(activeCard, criterion.name)) === value && criteriaScore(activeCard, criterion.name) === value ? 'text-black' : 'text-white/55 hover:text-white'"
                              :style="Math.floor(criteriaScore(activeCard, criterion.name)) === value && criteriaScore(activeCard, criterion.name) === value
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
                              @click="updateCriteriaDecimal(activeCard, criterion.name, value)"
                              class="py-4 text-sm font-semibold transition-all duration-100 active:scale-95"
                              :class="(criteriaScore(activeCard, criterion.name) * 10 % 10).toFixed(0) == value ? 'text-black' : 'text-amber-300/45 hover:text-amber-300'"
                              :style="(criteriaScore(activeCard, criterion.name) * 10 % 10).toFixed(0) == value
                                ? 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgb(245,158,11);'
                                : 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.06);'"
                            >.{{ value }}</button>
                          </div>
                        </div>
                      </div>
                    </div>
                  </template>
                </template>

                <!-- Single-score mode -->
                <template v-else>
                  <button
                    @click="setSingleScore(activeCard, 10)"
                    class="w-full py-2 mb-2 font-bold text-sm border transition-all duration-150 active:scale-[0.98]"
                    style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.35); color: rgb(245,158,11);"
                  >10 — Full Score</button>
                  <div class="grid grid-cols-2 gap-1.5">
                    <div class="rounded-xl p-1.5 bg-white/[0.03] border border-white/[0.05]">
                      <div class="text-[9px] font-bold text-white/20 uppercase tracking-widest mb-1.5 text-center">Whole</div>
                      <div class="grid grid-cols-3 gap-1">
                        <button
                          v-for="value in 9" :key="'w'+value"
                          @click="setSingleScore(activeCard, Number(value))"
                          class="py-4 text-sm font-bold transition-all duration-100 active:scale-95"
                          :class="Math.floor(activeCard.score) === value && activeCard.score === value ? 'text-black' : 'text-white/55 hover:text-white'"
                          :style="Math.floor(activeCard.score) === value && activeCard.score === value
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
                          @click="setSingleScore(activeCard, updateDecimal(activeCard.score, value))"
                          class="py-4 text-sm font-semibold transition-all duration-100 active:scale-95"
                          :class="(activeCard.score * 10 % 10).toFixed(0) == value ? 'text-black' : 'text-amber-300/45 hover:text-amber-300'"
                          :style="(activeCard.score * 10 % 10).toFixed(0) == value
                            ? 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgb(245,158,11);'
                            : 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.06);'"
                        >.{{ value }}</button>
                      </div>
                    </div>
                  </div>
                </template>

              </div><!-- /pair-card-keypad -->

            </div><!-- /pair-card-body -->
          </div>

          <!-- No active participant (all placeholders) -->
          <div v-else-if="isActivePair(pairIdx)" class="text-center py-8 text-amber-400/40 text-sm italic">
            No registered participants in this pair
          </div>

        </div>
      </div>
    </div>

    <!-- ── Sticky bottom action bar ── -->
    <div
      class="fixed bottom-0 left-0 right-0 z-30 flex items-center gap-2 px-4 py-3 border-t border-white/[0.07] bg-surface-900"
    >
      <button
        @click="emit('reset')"
        class="flex items-center gap-1.5 px-4 py-2.5 para-chip-sm type-label text-content-muted hover:text-content-primary transition-all duration-150 active:scale-95"
      ><i class="pi pi-undo text-xs"></i> Reset</button>

      <button
        @click="emit('jump')"
        class="flex items-center gap-1.5 px-4 py-2.5 para-chip-sm type-label text-content-muted hover:text-content-primary transition-all duration-150 active:scale-95"
      ><i class="pi pi-search text-xs"></i> Go To</button>

      <div v-if="hasCriteria && aggregateDisplay !== null" class="ml-auto text-xs font-bold text-amber-400/40 shrink-0 font-source tabular-nums">
        AVG {{ aggregateDisplay }}
      </div>
    </div>

  </div>
</template>

<style scoped>
/* ── Info column: content scrollable below header ───────────────── */
.pair-card-info {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  text-align: center;
  min-height: 0;
}
.pair-card-info-scroll {
  flex: 1 1 auto;
  overflow-y: auto;
  min-height: 0;
  scrollbar-width: thin;
  scrollbar-color: rgba(255,255,255,0.12) transparent;
}

/* ── Landscape: info left, keypad right ─────────────────────────────── */
@media (orientation: landscape) {
  .pair-card-body {
    display: grid;
    grid-template-columns: 1fr 1fr;
    column-gap: 16px;
    align-items: stretch;
  }
}
</style>
