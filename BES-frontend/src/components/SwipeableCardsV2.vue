<script setup>
import { ref, nextTick, onMounted, computed } from 'vue';

const props = defineProps({
  cards:        { type: Array,  required: true },
  feedbackData: { type: Object, default: () => new Map() },
  criteria:     { type: Array,  default: () => [] },
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
      class="flex overflow-x-auto snap-x snap-mandatory scroll-smooth gap-2 px-2 pt-2 pb-4 items-center h-full"
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
          class="card-hover p-3 relative transition-[border-color,box-shadow] duration-200"
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
                      <div class="type-body text-content-primary leading-tight mt-1" style="font-size:1.9rem; overflow-wrap: break-word">{{ card.participantName }}</div>
                      <div v-if="card.memberNames?.length" class="type-label text-content-muted normal-case mt-1 leading-snug" style="font-size:16px;letter-spacing:0.04em">{{ card.memberNames.join(' · ') }}</div>
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
                    <span class="type-body text-content-primary truncate" style="font-size:1rem">{{ card.participantName }}</span>
                  </div>
                  <div v-if="card.memberNames?.length" class="type-label text-content-muted normal-case mt-0.5 truncate" style="font-size:13px;letter-spacing:0.04em">{{ card.memberNames.join(' · ') }}</div>
                </template>
              </div>

              <!-- Feedback + criteria (scrollable if needed) -->
              <div class="score-card-info-scroll">
                <!-- Feedback button -->
                <div class="flex justify-center mb-2">
                  <button
                    @click.stop="emit('open-feedback', card)"
                    class="flex items-center gap-1.5 px-2.5 py-1.5 para-chip-sm type-label transition-all duration-150"
                    :class="feedbackData?.get(card.auditionNumber)
                      ? 'text-green-400 border-green-500/35'
                      : 'text-content-muted hover:text-content-primary'"
                  >
                    <i class="pi pi-comment text-xs" />
                    {{ feedbackData?.get(card.auditionNumber) ? 'Edit' : 'Feedback' }}
                    <span v-if="feedbackData?.get(card.auditionNumber)" class="ml-1 w-1.5 h-1.5 rounded-full bg-green-400 inline-block"></span>
                  </button>
                </div>

                <!-- Feedback preview: compact on mobile, full on tablet+ -->
                <template v-if="feedbackData?.get(card.auditionNumber)">
                  <!-- Mobile: compact summary -->
                  <div
                    class="md:hidden mb-2 px-3 py-2 border border-green-500/25 bg-emerald-500/[0.06]"
                    style="clip-path: polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
                  >
                    <div class="flex items-center gap-2">
                      <i class="pi pi-tags text-xs text-emerald-400/70 flex-shrink-0"></i>
                      <span class="type-body text-content-primary" style="font-size: 13px">
                        {{ feedbackData.get(card.auditionNumber).tagLabels?.length || 0 }} tag{{ feedbackData.get(card.auditionNumber).tagLabels?.length !== 1 ? 's' : '' }}
                        <template v-if="feedbackData.get(card.auditionNumber).note"> · note added</template>
                      </span>
                    </div>
                  </div>
                  <!-- Tablet+: full tag chips + note -->
                  <div
                    class="hidden md:block mb-2 p-2 border border-green-500/25 bg-emerald-500/[0.06]"
                    style="clip-path: polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
                  >
                    <div v-if="feedbackData.get(card.auditionNumber).tagLabels?.length" class="flex flex-wrap gap-1.5 mb-1">
                      <span
                        v-for="tag in feedbackData.get(card.auditionNumber).tagLabels"
                        :key="tag.id"
                        class="inline-flex items-center gap-1 px-2 py-0.5 type-label bg-white/[0.12] text-content-primary border border-white/20"
                        style="clip-path: polygon(3px 0%,100% 0%,calc(100% - 3px) 100%,0% 100%)"
                      >
                        {{ tag.label }}
                        <button @click.stop="emit('remove-tag', { auditionNumber: card.auditionNumber, tagId: tag.id })" class="text-content-muted hover:text-content-primary transition-colors">
                          <i class="pi pi-times text-[9px]" />
                        </button>
                      </span>
                    </div>
                    <div v-if="feedbackData.get(card.auditionNumber).note" class="text-xs text-white/65">{{ feedbackData.get(card.auditionNumber).note }}</div>
                  </div>
                </template>

                <!-- Criteria scores summary — clickable, active row highlighted -->
                <template v-if="hasCriteria">
                  <div class="h-px mb-2 bg-white/5"></div>
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
                        <span class="type-body"
                          :class="getActiveCriterion(idx) === criterion.name ? 'text-content-primary' : 'text-content-muted'"
                        >{{ criterion.name }}</span>
                        <span v-if="criterion.weight != null" class="type-label text-accent/70">×{{ criterion.weight }}</span>
                      </div>
                      <span
                        class="font-source tabular-nums font-bold leading-none"
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
                <div class="flex gap-2 mb-3 overflow-x-auto pb-0.5" style="scrollbar-width: none;">
                  <button
                    v-for="criterion in criteria"
                    :key="'sel-'+criterion.id"
                    @click="setActiveCriterion(idx, criterion.name)"
                    class="flex-shrink-0 flex items-center gap-2 px-4 py-2.5 para-chip transition-all duration-150 active:scale-95"
                    :class="getActiveCriterion(idx) === criterion.name
                      ? 'bg-accent border-accent'
                      : criteriaScore(card, criterion.name) > 0
                        ? 'border-accent/40 text-accent/80'
                        : 'border-white/10 text-content-muted hover:text-content-primary'"
                    :style="getActiveCriterion(idx) === criterion.name
                      ? 'box-shadow: 0 0 14px var(--accent-muted)'
                      : ''"
                  >
                    <span class="type-label leading-none"
                      :class="getActiveCriterion(idx) === criterion.name ? 'text-surface-900' : ''"
                    >{{ criterion.name }}</span>
                    <span
                      v-if="criteriaScore(card, criterion.name) > 0"
                      class="font-source tabular-nums text-sm font-bold leading-none"
                      :class="getActiveCriterion(idx) === criterion.name ? 'text-surface-900' : 'text-accent'"
                    >{{ criteriaScore(card, criterion.name) }}</span>
                    <span v-else class="inline-block w-2 h-2 rounded-full bg-white/15 shrink-0"></span>
                  </button>
                </div>

                <template v-for="criterion in criteria" :key="criterion.id">
                  <div v-if="getActiveCriterion(idx) === criterion.name">
                    <button

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
                  
                  @click="setSingleScore(card, 10)"
                  class="w-full py-2 mb-2 font-bold text-sm border transition-all duration-150 active:scale-[0.98] disabled:opacity-20 disabled:cursor-not-allowed"
                  style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.35); color: rgb(245,158,11);"
                >10 — Full Score</button>
                <div class="grid grid-cols-2 gap-1.5">
                  <div class="rounded-xl p-1.5 bg-white/[0.03] border border-white/[0.05]">
                    <div class="text-[9px] font-bold text-white/20 uppercase tracking-widest mb-1.5 text-center">Whole</div>
                    <div class="grid grid-cols-3 gap-1">
                      <button
                        v-for="value in 9" :key="'w'+value"

                        @click="setSingleScore(card, Number(value))"
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
                        
                        @click="setSingleScore(card, updateDecimal(card.score, value))"
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

/* ── Landscape: info left, keypad right ─────────────────────────────── */
@media (orientation: landscape) {
  .score-card-body {
    display: grid;
    grid-template-columns: 1fr 1fr;
    column-gap: 16px;
    align-items: stretch;
  }
}
</style>
