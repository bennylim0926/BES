<script setup>
import { ref, nextTick, onMounted, computed } from 'vue';

const props = defineProps({
  cards:           { type: Array,   required: true },
  feedbackData:    { type: Object,  default: () => new Map() },
  criteria:        { type: Array,   default: () => [] },
  feedbackEnabled: { type: Boolean, default: true },
});

const emit = defineEmits(['update:cards', 'open-feedback', 'remove-tag', 'submit', 'reset', 'jump', 'score-change']);

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

const getCardIndexFromScroll = () => {
  const container = scrollRef.value
  if (!container) return 0
  // px-2 (8px padding each side) + gap-2 (8px gap)
  // card width = 100% of content = clientWidth - 16
  // snapPoint = cardWidth + gap = clientWidth - 16 + 8 = clientWidth - 8
  const snapPoint = container.clientWidth - 8
  const idx = Math.round(container.scrollLeft / snapPoint)
  return Math.max(0, Math.min(idx, props.cards.length - 1))
}

const observeCards = async () => {
  await nextTick()
  const container = scrollRef.value
  if (!container) return

  container.addEventListener('scroll', () => {
    currentIndex.value = getCardIndexFromScroll()
  }, { passive: true })

  const finalizeIndex = () => {
    // After snap animation settles, lock in the correct index
    setTimeout(() => {
      currentIndex.value = getCardIndexFromScroll()
    }, 100)
  }
  container.addEventListener('touchend', finalizeIndex)
  container.addEventListener('scrollend', () => {
    currentIndex.value = getCardIndexFromScroll()
  })
}

const updateDecimal = (score, num) => score === 10 ? 10 : Math.floor(score) + num / 10
const updateCriteriaDecimal = (card, criterionName, num) => {
  const newVal = criteriaScore(card, criterionName) === 10 ? 10 : Math.floor(criteriaScore(card, criterionName)) + num / 10
  setCriteriaScore(card, criterionName, newVal)
}

const _aggregateDisplay = computed(() => {
  if (!hasCriteria.value) return null
  const card = props.cards[currentIndex.value]
  if (!card) return null
  return computeAggregate(card)
})

onMounted(observeCards)

</script>

<template>
  <div class="w-full relative h-full bg-surface-900 touch-manipulation" style="overflow: hidden;">

    <!-- Card scroll area -->
    <div
      ref="scrollRef"
      v-if="props.cards && props.cards.length"
      class="cards-scroll-container flex overflow-x-auto snap-x snap-mandatory scroll-smooth gap-2 px-2 pt-1 pb-2 items-center h-full"
      style="scrollbar-width: none; overflow-y: hidden;"
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
          class="card-hover p-2 relative transition-[border-color,box-shadow] duration-200"
          :class="[idx === currentIndex ? (card.submitted ? 'border-emerald-500/50' : card.saving ? 'border-accent/40' : 'border-[color:var(--accent-muted)]') : 'border-white/5 opacity-40']"
          :style="idx === currentIndex
            ? { boxShadow: card.submitted ? '0 0 0 1px rgba(16,185,129,0.4), 0 8px 32px rgba(0,0,0,0.7)' : card.saving ? '0 0 0 1px var(--accent-muted), 0 0 24px var(--accent-subtle), 0 8px 32px rgba(0,0,0,0.7)' : '0 0 0 1px var(--accent-muted), 0 8px 32px rgba(0,0,0,0.7)' }
            : {}"
        >
          <div class="score-card-body">

            <!-- ── Info column (left in landscape) ── -->
            <div class="score-card-info">

              <!-- Participant header — compact horizontal for inactive (peeking) cards, centered for active -->
              <div :class="idx === currentIndex ? 'mb-2' : 'mb-2'">
                <!-- Active card: score inline with header -->
                <template v-if="idx === currentIndex">
                  <div class="flex items-start gap-3">
                    <!-- Left: participant info -->
                    <div class="flex-1 min-w-0">
                      <span class="type-stat text-accent leading-none block" style="font-size: 2rem;">#{{ card.auditionNumber }}</span>
                      <div class="type-name text-content-primary leading-tight mt-1" style="font-size:1.9rem; overflow-wrap: break-word">{{ card.participantName }}</div>
                      <div v-if="card.memberNames?.length" class="type-prose text-content-muted mt-1 leading-snug" style="font-size:15px;">{{ card.memberNames.join(' · ') }}</div>
                      <div v-if="card.saving" class="inline-flex items-center gap-1 mt-1 px-2 py-0.5 type-label text-xs text-accent/60 normal-case" style="background:rgba(255,255,255,0.05);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)">
                        <i class="pi pi-spin pi-spinner text-[10px]"></i> Saving…
                      </div>
                      <div v-else-if="card.submitted" class="inline-flex items-center gap-1 mt-1 px-2 py-0.5 type-label text-xs text-emerald-400 normal-case" style="background:rgba(16,185,129,0.12);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%);box-shadow:0 0 8px rgba(16,185,129,0.2)">
                        <i class="pi pi-check-circle text-[10px]"></i> Saved
                      </div>
                    </div>
                    <!-- Right: score -->
                    <div class="text-right flex-shrink-0">
                      <div class="type-label text-content-muted mb-0.5">{{ hasCriteria ? 'AVG' : 'SCORE' }}</div>
                      <div
                        class="type-stat tabular-nums leading-none"
                        style="font-size: 3.5rem;"
                        :class="card.submitted ? 'text-emerald-400' : card.score === 0 ? 'text-white/20' : 'text-accent'"
                      >{{ card.score === 0 ? '—' : card.score }}</div>
                    </div>
                  </div>
                </template>
                <!-- Inactive card: compact left-aligned so peek shows readable info -->
                <template v-else>
                  <div class="flex items-baseline gap-2">
                    <span class="type-stat text-accent leading-none flex-shrink-0" style="font-size:1.4rem">#{{ card.auditionNumber }}</span>
                    <span class="type-name text-content-primary truncate" style="font-size:1rem">{{ card.participantName }}</span>
                  </div>
                  <div v-if="card.memberNames?.length" class="type-prose text-content-muted mt-0.5 truncate" style="font-size:12px;">{{ card.memberNames.join(' · ') }}</div>
                </template>
              </div>

              <!-- Feedback + criteria (scrollable if needed) -->
              <div class="score-card-info-scroll">
                <!-- Criteria scores summary — clickable, active row highlighted -->
                <template v-if="hasCriteria">
                  <div class="h-px mb-1 bg-white/5"></div>
                  <div class="flex flex-col gap-1.5">
                    <button
                      v-for="criterion in criteria"
                      :key="criterion.id"
                      @click="setActiveCriterion(idx, criterion.name)"
                      class="flex items-center justify-between px-3 py-2 para-chip w-full text-left transition-all duration-150 active:scale-[0.99]"
                      :class="getActiveCriterion(idx) === criterion.name
                        ? 'border-accent'
                        : criteriaScore(card, criterion.name) > 0 ? 'border-accent/25' : 'border-white/[0.07]'"
                      :style="getActiveCriterion(idx) === criterion.name
                        ? 'background: rgba(255,255,255,0.08); border-left: 3px solid var(--accent-color); box-shadow: 0 0 10px var(--accent-subtle)'
                        : 'background: rgba(255,255,255,0.03)'"
                    >
                      <div class="flex items-center gap-1.5">
                        <span class="type-name"
                          :class="getActiveCriterion(idx) === criterion.name ? 'text-content-primary' : 'text-content-muted'"
                        >{{ criterion.name }}</span>
                        <span v-if="criterion.weight != null" class="type-label text-accent/70">×{{ criterion.weight }}</span>
                      </div>
                      <span
                        class="font-bold leading-none"
                        style="font-size: 1.6rem"
                        :class="criteriaScore(card, criterion.name) > 0 ? 'text-accent' : 'text-white/15'"
                      >{{ criteriaScore(card, criterion.name) > 0 ? criteriaScore(card, criterion.name) : '—' }}</span>
                    </button>
                  </div>
                </template>
              </div><!-- /score-card-info-scroll -->

            </div><!-- /score-card-info -->

            <!-- ── Keypad column (right in landscape) ── -->
            <div class="score-card-keypad">

              <!-- ── Multi-criteria mode ── -->
              <template v-if="hasCriteria">
                <!-- Criterion selector — lives in keypad column for easy reach -->
                <div class="criteria-selector-tabs flex gap-2 mb-2 overflow-x-auto pb-0.5" style="scrollbar-width: none;">
                  <button
                    v-for="criterion in criteria"
                    :key="'sel-'+criterion.id"
                    @click="setActiveCriterion(idx, criterion.name)"
                    class="flex-shrink-0 flex flex-col items-center justify-center gap-0.5 px-3 py-2.5 para-chip transition-all duration-150 active:scale-95 min-w-[64px]"
                    :class="getActiveCriterion(idx) === criterion.name
                      ? 'border-accent'
                      : 'border-white/[0.08]'"
                    :style="getActiveCriterion(idx) === criterion.name
                      ? 'background: var(--accent-color); border-color: var(--accent-color); box-shadow: 0 0 14px var(--accent-muted); color: #111111'
                      : 'background: rgba(255,255,255,0.06)'"
                  >
                    <span class="criteria-tab-label type-name-sm leading-none" style="color: inherit;"
                    >{{ criterion.name }}</span>
                    <span
                      class="criteria-tab-score font-bold leading-none mt-0.5"
                      style="font-size: 1.5rem; color: inherit;"
                      :class="getActiveCriterion(idx) === criterion.name
                        ? ''
                        : criteriaScore(card, criterion.name) > 0
                          ? 'text-white/55'
                          : 'text-white/15'"
                    >{{ criteriaScore(card, criterion.name) > 0 ? criteriaScore(card, criterion.name) : '—' }}</span>
                  </button>
                </div>

                <template v-for="criterion in criteria" :key="criterion.id">
                  <div v-if="getActiveCriterion(idx) === criterion.name">
                    <div class="action-btn-row flex gap-1.5 mb-2">
                      <button
                        @click="setCriteriaScore(card, criterion.name, 10)"
                        class="flex-1 py-3 font-bold text-sm border transition-all duration-150 active:scale-[0.98] disabled:opacity-20 disabled:cursor-not-allowed"
                        style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.35); color: rgb(245,158,11);"
                      >10 — FULL</button>
                      <button
                        v-if="props.feedbackEnabled"
                        @click.stop="emit('open-feedback', card)"
                        class="flex-1 flex items-center justify-center gap-1 py-3 para-chip-sm type-label transition-all duration-150"
                        :class="feedbackData?.get(card.auditionNumber) ? 'text-green-400 border-green-500/35' : 'text-content-muted hover:text-content-primary'"
                      >
                        <i class="pi text-xs" :class="feedbackData?.get(card.auditionNumber) ? 'pi-check-circle' : 'pi-comment'" />
                        {{ feedbackData?.get(card.auditionNumber) ? 'Edit' : 'Feedback' }}
                      </button>
                    </div>
                    <div class="grid grid-cols-2 gap-1.5">
                      <div class="rounded-xl p-1 bg-white/[0.03] border border-white/[0.05]">
                        <div class="text-[9px] font-bold text-white/20 uppercase tracking-widest mb-1 text-center">Whole</div>
                        <div class="grid grid-cols-3 gap-1">
                          <button
                            v-for="value in 9" :key="'w'+criterion.name+value"
                            @click="setCriteriaScore(card, criterion.name, Number(value))"
                            @touchstart.passive="pressedKey = 'w'+value"
                            @touchend="pressedKey = null" @touchcancel="pressedKey = null"
                            class="keypad-btn py-5 text-xl font-bold text-white/85 transition-all duration-100 active:scale-95 disabled:opacity-20"
                            :style="wholeBtnStyle('w'+value)"
                          >{{ value }}</button>
                        </div>
                      </div>
                      <div class="rounded-xl p-1 bg-amber-500/[0.03] border border-amber-500/[0.10]">
                        <div class="text-[9px] font-bold text-amber-400/35 uppercase tracking-widest mb-1 text-center">Decimal</div>
                        <div class="grid grid-cols-3 gap-1">
                          <button
                            v-for="value in 9" :key="'d'+criterion.name+value"
                            @click="updateCriteriaDecimal(card, criterion.name, value)"
                            @touchstart.passive="pressedKey = 'd'+value"
                            @touchend="pressedKey = null" @touchcancel="pressedKey = null"
                            class="keypad-btn py-5 text-xl font-semibold text-amber-300/80 transition-all duration-100 active:scale-95 disabled:opacity-20"
                            :style="decimalBtnStyle('d'+value)"
                          >.{{ value }}</button>
                        </div>
                      </div>
                    </div>
                  </div>
                </template>
              </template>

              <!-- ── Single-score mode ── -->
              <template v-else>
                <div class="action-btn-row flex gap-1.5 mb-2">
                  <button
                    @click="setSingleScore(card, 10)"
                    class="flex-1 py-3 font-bold text-sm border transition-all duration-150 active:scale-[0.98] disabled:opacity-20 disabled:cursor-not-allowed"
                    style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.35); color: rgb(245,158,11);"
                  >10 — FULL</button>
                  <button
                    v-if="props.feedbackEnabled"
                    @click.stop="emit('open-feedback', card)"
                    class="flex-1 flex items-center justify-center gap-1 py-3 para-chip-sm type-label transition-all duration-150"
                    :class="feedbackData?.get(card.auditionNumber) ? 'text-green-400 border-green-500/35' : 'text-content-muted hover:text-content-primary'"
                  >
                    <i class="pi text-xs" :class="feedbackData?.get(card.auditionNumber) ? 'pi-check-circle' : 'pi-comment'" />
                    {{ feedbackData?.get(card.auditionNumber) ? 'Edit' : 'Feedback' }}
                  </button>
                </div>
                <div class="grid grid-cols-2 gap-1.5">
                  <div class="rounded-xl p-1.5 bg-white/[0.03] border border-white/[0.05]">
                    <div class="text-[9px] font-bold text-white/20 uppercase tracking-widest mb-1.5 text-center">Whole</div>
                    <div class="grid grid-cols-3 gap-1">
                      <button
                        v-for="value in 9" :key="'w'+value"
                        @click="setSingleScore(card, Number(value))"
                        @touchstart.passive="pressedKey = 'w'+value"
                        @touchend="pressedKey = null" @touchcancel="pressedKey = null"
                        class="keypad-btn py-5 text-xl font-bold text-white/85 transition-all duration-100 active:scale-95 disabled:opacity-20"
                        :style="wholeBtnStyle('w'+value)"
                      >{{ value }}</button>
                    </div>
                  </div>
                  <div class="rounded-xl p-1.5 bg-amber-500/[0.03] border border-amber-500/[0.10]">
                    <div class="text-[9px] font-bold text-amber-400/35 uppercase tracking-widest mb-1.5 text-center">Decimal</div>
                    <div class="grid grid-cols-3 gap-1">
                      <button
                        v-for="value in 9" :key="'d'+value"
                        @click="setSingleScore(card, updateDecimal(card.score, value))"
                        @touchstart.passive="pressedKey = 'd'+value"
                        @touchend="pressedKey = null" @touchcancel="pressedKey = null"
                        class="keypad-btn py-5 text-xl font-semibold text-amber-300/80 transition-all duration-100 active:scale-95 disabled:opacity-20"
                        :style="decimalBtnStyle('d'+value)"
                      >.{{ value }}</button>
                    </div>
                  </div>
                </div>
              </template>

            </div><!-- /score-card-keypad -->

          </div><!-- /score-card-body -->
        </div>

        <!-- Swipe dots -->
        <div class="flex justify-center gap-1 mt-1">
          <div
            v-for="(_, i) in props.cards" :key="i"
            class="h-1 rounded-full transition-all duration-300"
            :class="i === currentIndex ? 'w-6 bg-amber-400' : 'w-1.5 bg-white/12'"
          ></div>
        </div>
      </div>
    </div>

  </div>
</template>

<style scoped>
/* ── Info column: content scrollable below header ───────────────── */
.score-card-info {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  min-height: 0;
}
.score-card-info-scroll {
  flex: 1 1 auto;
  overflow-y: auto;
  min-height: 0;
  scrollbar-width: thin;
  scrollbar-color: rgba(255,255,255,0.12) transparent;
  text-align: center;
}

/* ── Keypad button: amber flash on press, releases back ─────────────── */
.keypad-btn:active {
  background: rgba(245, 158, 11, 0.45) !important;
  color: #fff !important;
}

/* ── Portrait: hide duplicate criteria list ─────────────────────────── */
@media (orientation: portrait) {
  .score-card-info-scroll {
    display: none;
  }
}

/* ── Landscape: info left, keypad right ─────────────────────────────── */
@media (orientation: landscape) {
  .score-card-body {
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
  .score-card-body {
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
