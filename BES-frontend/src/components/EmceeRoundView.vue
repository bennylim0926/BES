<script setup>
import { ref, computed } from 'vue';

const props = defineProps({
  participants: { type: Array, required: true },
  mode: { type: String, default: 'SOLO' },
});

const currentRound = ref(1)

const rounds = computed(() => {
  const sorted = [...props.participants].sort((a, b) => a.auditionNumber - b.auditionNumber)
  if (!sorted.length) return []

  if (props.mode === 'PAIR') {
    const maxNum = sorted[sorted.length - 1].auditionNumber
    const result = []
    for (let i = 1; i <= maxNum; i += 2) {
      result.push([
        sorted.find(p => p.auditionNumber === i) ?? { _placeholder: true, auditionNumber: i },
        sorted.find(p => p.auditionNumber === i + 1) ?? { _placeholder: true, auditionNumber: i + 1 },
      ])
    }
    return result
  }

  return sorted.map(p => [p])
})

const totalRounds = computed(() => rounds.value.length)
const currentRoundSlots = computed(() => rounds.value[currentRound.value - 1] ?? [])

// Each upcoming entry carries its absolute round number as key so TransitionGroup can track it
const upcomingRounds = computed(() =>
  rounds.value.slice(currentRound.value).map((slots, i) => ({
    slots,
    roundNumber: currentRound.value + 1 + i,
  }))
)

// Swipe / nav state
const touchStartX = ref(0)
const dragOffset = ref(0)
const isDragging = ref(false)
const direction = ref('left') // 'left' = next, 'right' = prev

const goNext = () => {
  if (currentRound.value >= totalRounds.value) return
  direction.value = 'left'
  currentRound.value++
}
const goPrev = () => {
  if (currentRound.value <= 1) return
  direction.value = 'right'
  currentRound.value--
}

const onTouchStart = (e) => {
  touchStartX.value = e.touches[0].clientX
  isDragging.value = true
  dragOffset.value = 0
}
const onTouchMove = (e) => {
  if (!isDragging.value) return
  dragOffset.value = e.touches[0].clientX - touchStartX.value
}
const onTouchEnd = () => {
  if (!isDragging.value) return
  isDragging.value = false
  const threshold = 60
  if (dragOffset.value < -threshold) goNext()
  else if (dragOffset.value > threshold) goPrev()
  dragOffset.value = 0
}

const cardStyle = computed(() => {
  if (!isDragging.value) return {}
  const x = dragOffset.value
  return {
    transform: `translateX(${x}px) rotate(${x * 0.03}deg)`,
    opacity: Math.max(0.4, 1 - Math.abs(x) / 250),
    transition: 'none',
    cursor: 'grabbing',
  }
})

const swipeHint = computed(() => {
  if (!isDragging.value) return null
  if (dragOffset.value < -30) return 'left'
  if (dragOffset.value > 30) return 'right'
  return null
})
</script>

<template>
  <div class="w-full flex flex-col gap-4">

    <!-- Navigation header -->
    <div class="flex items-center justify-between">
      <button
        @click="goPrev"
        :disabled="currentRound === 1"
        class="flex items-center gap-1.5 px-4 py-2 rounded-xl border border-surface-600 bg-surface-800
               text-sm font-semibold text-content-secondary hover:border-surface-500 hover:bg-surface-700
               disabled:opacity-30 disabled:cursor-not-allowed transition-all"
      >
        <i class="pi pi-chevron-left text-xs"></i>
        Prev
      </button>

      <div class="text-center">
        <div class="text-xs font-semibold text-content-muted uppercase tracking-wider mb-0.5">Round</div>
        <div class="font-source font-bold text-2xl text-content-primary">
          {{ currentRound }}
          <span class="text-surface-500 text-lg font-normal">/ {{ totalRounds }}</span>
        </div>
      </div>

      <button
        @click="goNext"
        :disabled="currentRound === totalRounds"
        class="flex items-center gap-1.5 px-4 py-2 rounded-xl border border-surface-600 bg-surface-800
               text-sm font-semibold text-content-secondary hover:border-surface-500 hover:bg-surface-700
               disabled:opacity-30 disabled:cursor-not-allowed transition-all"
      >
        Next
        <i class="pi pi-chevron-right text-xs"></i>
      </button>
    </div>

    <!-- Current round (swipeable) -->
    <div class="relative overflow-hidden">
      <!-- Swipe hint overlays -->
      <div
        class="absolute inset-y-0 left-0 w-16 flex items-center justify-center z-10 pointer-events-none transition-opacity duration-150 rounded-l-2xl"
        :class="swipeHint === 'right' && currentRound > 1 ? 'opacity-100 bg-primary-500/10' : 'opacity-0'"
      >
        <i class="pi pi-chevron-left text-2xl text-primary-400"></i>
      </div>
      <div
        class="absolute inset-y-0 right-0 w-16 flex items-center justify-center z-10 pointer-events-none transition-opacity duration-150 rounded-r-2xl"
        :class="swipeHint === 'left' && currentRound < totalRounds ? 'opacity-100 bg-primary-500/10' : 'opacity-0'"
      >
        <i class="pi pi-chevron-right text-2xl text-primary-400"></i>
      </div>

      <Transition :name="direction === 'left' ? 'card-left' : 'card-right'" mode="out-in">
        <div
          :key="currentRound"
          :style="cardStyle"
          @touchstart.passive="onTouchStart"
          @touchmove.passive="onTouchMove"
          @touchend="onTouchEnd"
          class="rounded-2xl border border-primary-500/60 bg-surface-800
                 shadow-[0_0_0_1px_rgba(6,182,212,0.2),0_8px_32px_rgba(6,182,212,0.10)]
                 select-none touch-pan-y"
        >
          <div class="flex items-center justify-between px-4 pt-3 pb-2 border-b border-primary-500/20">
            <div class="flex items-center gap-2">
              <span class="text-xs font-bold uppercase tracking-widest text-primary-400">
                Round {{ currentRound }}
              </span>
              <span
                v-if="currentRound === totalRounds"
                class="text-xs px-2 py-0.5 rounded-full bg-surface-600/60 text-surface-300 border border-surface-500/40 font-semibold uppercase tracking-wider"
              >
                Last
              </span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-xs px-2.5 py-0.5 rounded-full bg-primary-500 text-white font-bold uppercase tracking-wider">
                Now
              </span>
              <span class="text-xs text-content-muted hidden sm:inline">swipe to navigate</span>
            </div>
          </div>

          <div class="p-4" :class="mode === 'PAIR' ? 'grid grid-cols-2 gap-3' : ''">
            <template v-for="(slot, sIdx) in currentRoundSlots" :key="sIdx">
              <div
                v-if="slot._placeholder"
                class="rounded-xl border-2 border-dashed border-amber-500/50 bg-amber-500/5 p-3 flex items-center gap-3 min-h-[64px]"
              >
                <div class="flex-shrink-0 w-10 h-10 rounded-xl bg-amber-500/15 border border-amber-500/30 flex items-center justify-center text-sm font-source font-bold text-amber-400">
                  {{ slot.auditionNumber }}
                </div>
                <div>
                  <div class="text-sm font-bold text-amber-400 uppercase tracking-wider">Not Registered</div>
                  <div class="text-xs text-amber-300/60 mt-0.5">Audition #{{ slot.auditionNumber }} has no participant</div>
                </div>
              </div>
              <div
                v-else
                class="rounded-xl p-3 flex items-center gap-3 min-h-[64px] bg-surface-700/50"
              >
                <div class="flex-shrink-0 w-10 h-10 rounded-xl flex items-center justify-center font-source font-bold bg-primary-500/20 border border-primary-500/40 text-primary-400 text-base">
                  {{ slot.auditionNumber }}
                </div>
                <div class="flex-1 min-w-0">
                  <div class="font-heading font-bold text-xl text-content-primary truncate leading-tight">
                    {{ slot.participantName }}
                  </div>
                  <div v-if="slot.judgeName" class="text-xs text-content-muted truncate mt-0.5">
                    {{ slot.judgeName }}
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </Transition>
    </div>

    <!-- Upcoming rounds — animated list -->
    <TransitionGroup
      :name="direction === 'left' ? 'list-up' : 'list-down'"
      tag="div"
      class="flex flex-col gap-3 relative"
    >
      <div
        v-for="({ slots, roundNumber }, uIdx) in upcomingRounds"
        :key="roundNumber"
        class="rounded-2xl border overflow-hidden transition-colors duration-300"
        :class="uIdx === 0
          ? 'border-primary-500/20 bg-surface-800/60'
          : 'border-surface-600/20 bg-surface-900/60 opacity-60'"
      >
        <div class="flex items-center justify-between px-4 pt-3 pb-2 border-b border-surface-600/20">
          <div class="flex items-center gap-2">
            <span class="text-xs font-bold uppercase tracking-widest text-content-muted">
              Round {{ roundNumber }}
            </span>
            <span
              v-if="roundNumber === totalRounds"
              class="text-xs px-2 py-0.5 rounded-full bg-surface-600/60 text-surface-300 border border-surface-500/40 font-semibold uppercase tracking-wider"
            >
              Last
            </span>
          </div>
          <span
            v-if="uIdx === 0"
            class="text-xs px-2.5 py-0.5 rounded-full bg-surface-600 text-primary-300 border border-primary-500/30 font-bold uppercase tracking-wider"
          >
            Up Next
          </span>
        </div>

        <div class="p-4" :class="mode === 'PAIR' ? 'grid grid-cols-2 gap-3' : ''">
          <template v-for="(slot, sIdx) in slots" :key="sIdx">
            <div
              v-if="slot._placeholder"
              class="rounded-xl border-2 border-dashed border-amber-500/50 bg-amber-500/5 p-3 flex items-center gap-3 min-h-[64px]"
            >
              <div class="flex-shrink-0 w-10 h-10 rounded-xl bg-amber-500/15 border border-amber-500/30 flex items-center justify-center text-sm font-source font-bold text-amber-400">
                {{ slot.auditionNumber }}
              </div>
              <div>
                <div class="text-sm font-bold text-amber-400 uppercase tracking-wider">Not Registered</div>
                <div class="text-xs text-amber-300/60 mt-0.5">Audition #{{ slot.auditionNumber }} has no participant</div>
              </div>
            </div>
            <div
              v-else
              class="rounded-xl p-3 flex items-center gap-3 min-h-[64px] bg-surface-800/30"
            >
              <div class="flex-shrink-0 w-10 h-10 rounded-xl flex items-center justify-center font-source font-bold bg-surface-700 border border-surface-600/50 text-surface-400 text-sm">
                {{ slot.auditionNumber }}
              </div>
              <div class="flex-1 min-w-0">
                <div class="font-heading font-bold text-base text-content-primary truncate leading-tight">
                  {{ slot.participantName }}
                </div>
                <div v-if="slot.judgeName" class="text-xs text-content-muted truncate mt-0.5">
                  {{ slot.judgeName }}
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </TransitionGroup>

  </div>
</template>

<style scoped>
/* ── Current card ─────────────────────────────────── */
.card-left-enter-active,
.card-left-leave-active,
.card-right-enter-active,
.card-right-leave-active {
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.28s ease;
}
.card-left-enter-from  { transform: translateX(60px);  opacity: 0; }
.card-left-enter-to    { transform: translateX(0);      opacity: 1; }
.card-left-leave-from  { transform: translateX(0);      opacity: 1; }
.card-left-leave-to    { transform: translateX(-60px);  opacity: 0; }

.card-right-enter-from { transform: translateX(-60px); opacity: 0; }
.card-right-enter-to   { transform: translateX(0);     opacity: 1; }
.card-right-leave-from { transform: translateX(0);     opacity: 1; }
.card-right-leave-to   { transform: translateX(60px);  opacity: 0; }

/* ── Upcoming list (vertical) ─────────────────────── */
/* Going next (swipe left): list slides up */
.list-up-move {
  transition: transform 0.32s cubic-bezier(0.4, 0, 0.2, 1);
}
.list-up-enter-active {
  transition: transform 0.32s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.28s ease;
}
.list-up-leave-active {
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.22s ease;
  position: absolute;
  width: 100%;
}
.list-up-enter-from { transform: translateY(32px); opacity: 0; }
.list-up-enter-to   { transform: translateY(0);    opacity: 1; }
.list-up-leave-from { transform: translateY(0);    opacity: 1; }
.list-up-leave-to   { transform: translateY(-20px); opacity: 0; }

/* Going prev (swipe right): list slides down */
.list-down-move {
  transition: transform 0.32s cubic-bezier(0.4, 0, 0.2, 1);
}
.list-down-enter-active {
  transition: transform 0.32s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.28s ease;
}
.list-down-leave-active {
  transition: transform 0.28s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.22s ease;
  position: absolute;
  width: 100%;
}
.list-down-enter-from { transform: translateY(-32px); opacity: 0; }
.list-down-enter-to   { transform: translateY(0);     opacity: 1; }
.list-down-leave-from { transform: translateY(0);     opacity: 1; }
.list-down-leave-to   { transform: translateY(20px);  opacity: 0; }
</style>
