<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue';

const props = defineProps({
  cards:           { type: Array,   required: true },
  feedbackData:    { type: Object,  default: () => new Map() },
  criteria:        { type: Array,   default: () => [] },
  feedbackEnabled: { type: Boolean, default: true },
});

const emit = defineEmits(['open-feedback', 'remove-tag', 'submit', 'jump', 'score-change']);

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

const haptic = () => navigator.vibrate?.(8)

const pressedKey = ref(null)
const wholeBtnStyle = (key) => ({
  clipPath: 'polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%)',
  background: pressedKey.value === key ? 'rgb(245,158,11)' : 'rgba(255,255,255,0.05)',
  color: pressedKey.value === key ? '#fff' : undefined,
})
const decimalBtnStyle = (key) => ({
  clipPath: 'polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%)',
  background: pressedKey.value === key ? 'rgb(245,158,11)' : 'rgba(245,158,11,0.06)',
  color: pressedKey.value === key ? '#fff' : undefined,
})

function criteriaScore(card, criterionName) { return card.criteriaScores?.[criterionName] ?? 0 }

function setCriteriaScore(card, criterionName, value) {
  haptic()
  if (!card.criteriaScores) card.criteriaScores = {}
  card.criteriaScores[criterionName] = value
  card.score = computeAggregate(card)
  emit('score-change', card)
}

function setSingleScore(card, value) {
  haptic()
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

// Snap stride matches SwipeableCardsV2 / MiniScoreMenu: cards are
// `width:100%` inside a `px-2 gap-2` flex container, so each pair takes
// `clientWidth - 16` and the per-pair stride is `(clientWidth - 16) + 8`
// = `clientWidth - 8`. Using `clientWidth + 8` underestimates the stride;
// at higher indices the computed idx lags the actually-snapped pair by
// 1, so the v-if-gated card body silently disappears (visible as a
// blank "Select a participant" card around pair ~20+ on mobile).
const getPairIndexFromScroll = () => {
  const container = scrollRef.value
  if (!container) return 0
  const snapPoint = container.clientWidth - 8
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

// "Pending" desired participant — set by the Find Participant menu so
// the upcoming currentIndex change (from the smooth-scroll settling)
// honours the specific number the user tapped, instead of defaulting
// to the first non-placeholder (i.e. always the odd one in a pair).
const pendingActiveNum = ref(null)

watch(currentIndex, () => {
  if (pendingActiveNum.value != null) {
    const inPair = activePair.value.some(
      c => !c._placeholder && c.auditionNumber === pendingActiveNum.value
    )
    if (inPair) {
      activeParticipantNum.value = pendingActiveNum.value
      pendingActiveNum.value = null
      return
    }
    pendingActiveNum.value = null
  }
  const first = activePair.value.find(c => !c._placeholder)
  activeParticipantNum.value = first?.auditionNumber ?? null
}, { immediate: true })

// Called by the parent when the Find Participant menu picks a specific
// audition number. Sets both the active value (so the UI updates if
// we're already on the right pair) and a pending value (so any imminent
// currentIndex change from the smooth scroll preserves the choice).
function selectParticipant(auditionNumber) {
  if (auditionNumber == null) return
  pendingActiveNum.value = auditionNumber
  activeParticipantNum.value = auditionNumber
}

defineExpose({ selectParticipant })

const _aggregateDisplay = computed(() => {
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
  <div class="w-full relative h-full bg-surface-900 touch-manipulation" style="overflow: hidden;">

    <!-- Pair dots -->
    <div v-if="pairs.length > 1" class="flex justify-center gap-1 py-1">
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
      class="cards-scroll-container flex overflow-x-auto snap-x snap-mandatory scroll-smooth gap-2 px-2 pb-4 items-start h-full"
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
          <div class="flex items-center justify-between mb-2 px-1">
            <div class="corner-bar-tl"></div>
            <span class="type-label text-content-muted">
              Round {{ pairIdx + 1 }} of {{ pairs.length }}
            </span>
            <span v-if="pairIdx === pairs.length - 1" class="badge-neutral type-label">Last</span>
          </div>

          <!-- ── Participant selector: detail area + audition buttons ── -->
          <div class="flex gap-2 mb-2">
            <!-- Full detail container for active participant -->
            <div
              class="flex-1 min-w-0 flex items-center gap-2 px-2 py-1.5 para-chip-sm transition-all duration-200"
              :class="isActivePair(pairIdx) ? 'border-[color:var(--accent-muted)]' : 'opacity-40'"
            >
              <template v-if="activeCard && isActivePair(pairIdx)">
                <span class="type-stat flex-shrink-0 leading-none text-accent" style="font-size: 2rem">#{{ activeCard.auditionNumber }}</span>
                <div class="flex-1 min-w-0">
                  <div class="type-name text-content-primary leading-tight" style="font-size: 1.9rem; overflow-wrap: break-word">{{ activeCard.participantName }}</div>
                  <div v-if="activeCard.memberNames?.length" class="type-prose text-content-muted truncate mt-0.5" style="font-size: 15px;">{{ activeCard.memberNames.join(' · ') }}</div>
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
                class="flex-shrink-0 flex flex-col items-center justify-center px-3 py-2 para-chip-sm transition-all duration-150 active:scale-95 disabled:opacity-20 disabled:cursor-not-allowed min-w-0"
                :class="!card._placeholder && activeParticipantNum === card.auditionNumber && isActivePair(pairIdx)
                  ? ''
                  : !card._placeholder && card.submitted
                    ? 'text-emerald-300 border-emerald-500/50'
                    : !card._placeholder && card.saving
                      ? 'text-amber-300 border-amber-400/50'
                      : 'text-white/70 border-white/30'"
                :style="!card._placeholder && activeParticipantNum === card.auditionNumber && isActivePair(pairIdx)
                  ? 'font-size: 1.6rem; background: var(--accent-color); border-color: var(--accent-color); box-shadow: 0 0 14px var(--accent-muted); color: #111111'
                  : !card._placeholder && card.submitted
                    ? 'font-size: 1.6rem; background: rgba(52,211,153,0.12); box-shadow: 0 0 8px rgba(52,211,153,0.2)'
                    : !card._placeholder && card.saving
                      ? 'font-size: 1.6rem; background: rgba(245,158,11,0.08)'
                      : 'font-size: 1.6rem; background: rgba(255,255,255,0.06)'"
              >
                <span class="type-stat leading-none" style="font-size: 1.6rem; color: inherit">#{{ card.auditionNumber }}</span>
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
                          <span class="type-name"
                            :class="getActiveCriterion(activeCard.auditionNumber) === criterion.name ? 'text-content-primary' : 'text-content-muted'"
                          >{{ criterion.name }}</span>
                          <span v-if="criterion.weight != null" class="type-label text-accent/70">×{{ criterion.weight }}</span>
                        </div>
                        <span
                          class="font-bold leading-none"
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
                  <div class="criteria-selector-tabs flex gap-2 mb-2 overflow-x-auto pb-0.5" style="scrollbar-width: none;">
                    <button
                      v-for="criterion in criteria"
                      :key="'sel-'+criterion.id"
                      @click="setActiveCriterion(activeCard.auditionNumber, criterion.name)"
                      class="flex-shrink-0 flex flex-col items-center justify-center gap-0.5 px-3 py-2.5 para-chip transition-all duration-150 active:scale-95 min-w-[64px]"
                      :class="getActiveCriterion(activeCard.auditionNumber) === criterion.name
                        ? 'border-accent'
                        : 'border-white/[0.08]'"
                      :style="getActiveCriterion(activeCard.auditionNumber) === criterion.name
                        ? 'background: var(--accent-color); border-color: var(--accent-color); box-shadow: 0 0 14px var(--accent-muted); color: #111111'
                        : 'background: rgba(255,255,255,0.06)'"
                    >
                      <span class="criteria-tab-label type-name-sm leading-none" style="color: inherit;"
                      >{{ criterion.name }}</span>
                      <span
                        class="criteria-tab-score font-bold leading-none mt-0.5"
                        style="font-size: 1.5rem; color: inherit;"
                        :class="getActiveCriterion(activeCard.auditionNumber) === criterion.name
                          ? ''
                          : criteriaScore(activeCard, criterion.name) > 0
                            ? 'text-white/55'
                            : 'text-white/15'"
                      >{{ criteriaScore(activeCard, criterion.name) > 0 ? criteriaScore(activeCard, criterion.name) : '—' }}</span>
                    </button>
                  </div>

                  <template v-for="criterion in criteria" :key="criterion.id">
                    <div v-if="getActiveCriterion(activeCard.auditionNumber) === criterion.name">
                      <div class="action-btn-row flex gap-1.5 mb-2">
                        <button
                          @click="setCriteriaScore(activeCard, criterion.name, 10)"
                          class="flex-1 py-3 font-bold text-sm border transition-all duration-150 active:scale-[0.98]"
                          style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.35); color: rgb(245,158,11);"
                        >10 — FULL</button>
                        <button
                          v-if="props.feedbackEnabled"
                          @click.stop="emit('open-feedback', activeCard)"
                          class="flex-1 flex items-center justify-center gap-1 py-3 para-chip-sm type-label transition-all duration-150"
                          :class="feedbackData?.get(activeCard.auditionNumber) ? 'text-green-400 border-green-500/35' : 'text-content-muted hover:text-content-primary'"
                        >
                          <i class="pi text-xs" :class="feedbackData?.get(activeCard.auditionNumber) ? 'pi-check-circle' : 'pi-comment'" />
                          {{ feedbackData?.get(activeCard.auditionNumber) ? 'Edit' : 'Feedback' }}
                        </button>
                      </div>
                      <div class="grid grid-cols-2 gap-1.5">
                        <div class="rounded-xl p-1 bg-white/[0.03] border border-white/[0.05]">
                          <div class="text-[9px] font-bold text-white/20 uppercase tracking-widest mb-1 text-center">Whole</div>
                          <div class="grid grid-cols-3 gap-1">
                            <button
                              v-for="value in 9" :key="'w'+criterion.name+value"
                              @click="setCriteriaScore(activeCard, criterion.name, Number(value))"
                              @touchstart.passive="pressedKey = 'w'+value"
                              @touchend="pressedKey = null" @touchcancel="pressedKey = null"
                              class="keypad-btn py-5 text-xl font-bold text-white/85 transition-all duration-100 active:scale-95"
                              :style="wholeBtnStyle('w'+value)"
                            >{{ value }}</button>
                          </div>
                        </div>
                        <div class="rounded-xl p-1 bg-amber-500/[0.03] border border-amber-500/[0.10]">
                          <div class="text-[9px] font-bold text-amber-400/35 uppercase tracking-widest mb-1 text-center">Decimal</div>
                          <div class="grid grid-cols-3 gap-1">
                            <button
                              v-for="value in 9" :key="'d'+criterion.name+value"
                              @click="updateCriteriaDecimal(activeCard, criterion.name, value)"
                              @touchstart.passive="pressedKey = 'd'+value"
                              @touchend="pressedKey = null" @touchcancel="pressedKey = null"
                              class="keypad-btn py-5 text-xl font-semibold text-amber-300/80 transition-all duration-100 active:scale-95"
                              :style="decimalBtnStyle('d'+value)"
                            >.{{ value }}</button>
                          </div>
                        </div>
                      </div>
                    </div>
                  </template>
                </template>

                <!-- Single-score mode -->
                <template v-else>
                  <div class="action-btn-row flex gap-1.5 mb-2">
                    <button
                      @click="setSingleScore(activeCard, 10)"
                      class="flex-1 py-3 font-bold text-sm border transition-all duration-150 active:scale-[0.98]"
                      style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.35); color: rgb(245,158,11);"
                    >10 — FULL</button>
                    <button
                      v-if="props.feedbackEnabled"
                      @click.stop="emit('open-feedback', activeCard)"
                      class="flex-1 flex items-center justify-center gap-1 py-3 para-chip-sm type-label transition-all duration-150"
                      :class="feedbackData?.get(activeCard.auditionNumber) ? 'text-green-400 border-green-500/35' : 'text-content-muted hover:text-content-primary'"
                    >
                      <i class="pi text-xs" :class="feedbackData?.get(activeCard.auditionNumber) ? 'pi-check-circle' : 'pi-comment'" />
                      {{ feedbackData?.get(activeCard.auditionNumber) ? 'Edit' : 'Feedback' }}
                    </button>
                  </div>
                  <div class="grid grid-cols-2 gap-1.5">
                    <div class="rounded-xl p-1.5 bg-white/[0.03] border border-white/[0.05]">
                      <div class="text-[9px] font-bold text-white/20 uppercase tracking-widest mb-1.5 text-center">Whole</div>
                      <div class="grid grid-cols-3 gap-1">
                        <button
                          v-for="value in 9" :key="'w'+value"
                          @click="setSingleScore(activeCard, Number(value))"
                          @touchstart.passive="pressedKey = 'w'+value"
                          @touchend="pressedKey = null" @touchcancel="pressedKey = null"
                          class="keypad-btn py-5 text-xl font-bold text-white/85 transition-all duration-100 active:scale-95"
                          :style="wholeBtnStyle('w'+value)"
                        >{{ value }}</button>
                      </div>
                    </div>
                    <div class="rounded-xl p-1.5 bg-amber-500/[0.03] border border-amber-500/[0.10]">
                      <div class="text-[9px] font-bold text-amber-400/35 uppercase tracking-widest mb-1.5 text-center">Decimal</div>
                      <div class="grid grid-cols-3 gap-1">
                        <button
                          v-for="value in 9" :key="'d'+value"
                          @click="setSingleScore(activeCard, updateDecimal(activeCard.score, value))"
                          @touchstart.passive="pressedKey = 'd'+value"
                          @touchend="pressedKey = null" @touchcancel="pressedKey = null"
                          class="keypad-btn py-5 text-xl font-semibold text-amber-300/80 transition-all duration-100 active:scale-95"
                          :style="decimalBtnStyle('d'+value)"
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

/* ── Keypad button: amber flash on press, releases back ─────────────── */
.keypad-btn:active {
  background: rgba(245, 158, 11, 0.45) !important;
  color: #fff !important;
}

/* ── Portrait: hide duplicate criteria list ─────────────────────────── */
@media (orientation: portrait) {
  .pair-card-info-scroll {
    display: none;
  }
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

/* ── Tablet landscape: bigger keypad, bottom-aligned, bigger tab text ── */
@media (orientation: landscape) and (min-width: 768px) {
  .cards-scroll-container {
    align-items: flex-end;
  }
  .pair-card-body {
    grid-template-columns: 2fr 3fr;
  }
  .criteria-selector-tabs {
    display: none;
  }
  .keypad-btn {
    padding-top: 1.75rem;
    padding-bottom: 1.75rem;
  }
  .action-btn-row > button {
    padding-top: 0.875rem;
    padding-bottom: 0.875rem;
    font-size: 1rem;
  }
  .criteria-tab-label {
    font-size: 12px;
    letter-spacing: 0.16em;
  }
  .criteria-tab-score {
    font-size: 2rem;
  }
}
</style>
