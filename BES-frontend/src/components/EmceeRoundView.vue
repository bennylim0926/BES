<script setup>
import { ref, computed } from 'vue';
import Timer from './Timer.vue';

const props = defineProps({
  participants: { type: Array, required: true },
  mode:         { type: String, default: 'SOLO' },
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

const totalRounds        = computed(() => rounds.value.length)
const currentRoundSlots  = computed(() => rounds.value[currentRound.value - 1] ?? [])
const MAX_VISIBLE_UPCOMING = 4

const allUpcomingRounds = computed(() =>
  rounds.value.slice(currentRound.value).map((slots, i) => ({
    slots,
    roundNumber: currentRound.value + 1 + i,
  }))
)

const visibleRounds    = computed(() => allUpcomingRounds.value.slice(0, MAX_VISIBLE_UPCOMING).reverse())
const hiddenRoundsCount = computed(() => Math.max(0, allUpcomingRounds.value.length - MAX_VISIBLE_UPCOMING))

const touchStartX = ref(0)
const dragOffset  = ref(0)
const isDragging  = ref(false)
const direction   = ref('left')

const goNext = () => { if (currentRound.value < totalRounds.value)  { direction.value = 'left';  currentRound.value++ } }
const goPrev = () => { if (currentRound.value > 1)                  { direction.value = 'right'; currentRound.value-- } }

const onTouchStart = (e) => { touchStartX.value = e.touches[0].clientX; isDragging.value = true; dragOffset.value = 0 }
const onTouchMove  = (e) => { if (!isDragging.value) return; dragOffset.value = e.touches[0].clientX - touchStartX.value }
const onTouchEnd   = () => {
  if (!isDragging.value) return
  isDragging.value = false
  if      (dragOffset.value < -60) goNext()
  else if (dragOffset.value >  60) goPrev()
  dragOffset.value = 0
}

const cardStyle = computed(() => {
  if (!isDragging.value) return {}
  const x = dragOffset.value
  return { transform: `translate3d(${x}px,0,0) rotate(${x * 0.03}deg)`, opacity: Math.max(0.4, 1 - Math.abs(x) / 250), transition: 'none', cursor: 'grabbing', willChange: 'transform' }
})

const swipeHint = computed(() => {
  if (!isDragging.value) return null
  if (dragOffset.value < -30) return 'left'
  if (dragOffset.value > 30)  return 'right'
  return null
})
</script>

<template>
  <div class="emcee-root w-full flex flex-col h-full touch-manipulation" style="background: #060818; overflow: hidden;">

    <!-- ── Queue ──────────────────────────────────────────────────────────────
         flex-col-reverse: stack from bottom (near NOW) upward.
         DOM order: queue first, "+N" second. Reverse means:
         queue → bottom, "+N" → above queue (top). Overflow clips top only.
    ──────────────────────────────────────────────────────────────────────── -->
    <div
      class="emcee-queue flex-1 px-3 pt-2 pb-1"
      style="display: flex; flex-direction: column-reverse; justify-content: flex-start; overflow: hidden;"
    >
      <div class="flex flex-col gap-1.5">
        <div
          v-for="({ slots, roundNumber }, uIdx) in visibleRounds"
          :key="roundNumber"
          class="queue-item rounded-xl border overflow-hidden flex-shrink-0"
          :class="uIdx === visibleRounds.length - 1 ? 'border-white/15 bg-white/5' : 'border-white/5 bg-transparent'"
          :style="{ opacity: uIdx === visibleRounds.length - 1 ? '1' : uIdx === visibleRounds.length - 2 ? '0.5' : uIdx === visibleRounds.length - 3 ? '0.3' : '0.15' }"
        >
          <div class="flex items-center justify-between px-2 py-1">
            <span class="text-[10px] font-bold uppercase tracking-widest text-white/30">
              Round {{ roundNumber }}
            </span>
            <span
              v-if="uIdx === visibleRounds.length - 1"
              class="text-[10px] px-2 py-0.5 rounded-full bg-white/10 text-white/50 border border-white/10 font-bold uppercase tracking-wider"
            >Up Next</span>
          </div>
          <div class="px-2 pb-1.5">
            <template v-for="(slot, sIdx) in slots" :key="sIdx">
              <div v-if="slot._placeholder" class="text-xs text-amber-400/40 italic">
                #{{ slot.auditionNumber }} — Not Registered
              </div>
              <div v-else class="flex items-center gap-2 flex-wrap">
                <span class="font-anton text-xl" :class="uIdx === visibleRounds.length - 1 ? 'text-white/60' : 'text-white/25'">#{{ slot.auditionNumber }}</span>
                <span class="font-heading font-bold text-sm" :class="uIdx === visibleRounds.length - 1 ? 'text-white/70' : 'text-white/30'" style="text-transform: uppercase; letter-spacing: 0.05em;">{{ slot.participantName }}</span>
                <span v-if="mode === 'PAIR' && sIdx === 0" class="text-white/20 text-xs">&amp;</span>
              </div>
            </template>
          </div>
        </div>
      </div>
      <div
        v-if="hiddenRoundsCount > 0"
        class="flex-shrink-0 text-center pb-1"
      >
        <span class="text-[10px] text-white/15 font-bold uppercase tracking-widest">+{{ hiddenRoundsCount }} more rounds</span>
      </div>
    </div>

    <!-- ── NOW card ──────────────────────────────────────────────────────── -->
    <div class="emcee-now px-3 pb-2">
      <div class="relative overflow-hidden">
        <div
          class="absolute inset-y-0 left-0 w-10 flex items-center justify-center z-10 pointer-events-none transition-opacity duration-150"
          :class="swipeHint === 'right' && currentRound > 1 ? 'opacity-100' : 'opacity-0'"
        >
          <i class="pi pi-chevron-left text-lg text-white/40"></i>
        </div>
        <div
          class="absolute inset-y-0 right-0 w-10 flex items-center justify-center z-10 pointer-events-none transition-opacity duration-150"
          :class="swipeHint === 'left' && currentRound < totalRounds ? 'opacity-100' : 'opacity-0'"
        >
          <i class="pi pi-chevron-right text-lg text-white/40"></i>
        </div>

        <Transition :name="direction === 'left' ? 'card-left' : 'card-right'" mode="out-in">
          <div
            :key="currentRound"
            :style="cardStyle"
            @touchstart.passive="onTouchStart"
            @touchmove.passive="onTouchMove"
            @touchend="onTouchEnd"
            class="rounded-2xl border border-white/20 bg-white/5 select-none touch-pan-y"
            style="box-shadow: 0 0 0 1px rgba(255,255,255,0.06), 0 8px 32px rgba(0,0,0,0.6);"
          >
            <div class="flex items-center justify-between px-3 pt-2 pb-1.5 border-b border-white/8">
              <div class="flex items-center gap-2">
                <span class="w-2 h-2 rounded-full bg-white animate-pulse inline-block"></span>
                <span class="text-[10px] font-bold uppercase tracking-widest text-white/50">Now on Stage</span>
              </div>
              <span class="text-[10px] text-white/25 font-bold uppercase tracking-widest">
                Rd {{ currentRound }} / {{ totalRounds }}
              </span>
            </div>
            <div class="p-3">
              <template v-for="(slot, sIdx) in currentRoundSlots" :key="sIdx">
                <div v-if="slot._placeholder" class="text-amber-400/60 text-sm italic py-2">
                  #{{ slot.auditionNumber }} — Not Registered
                </div>
                <div v-else>
                  <div v-if="mode === 'PAIR' && sIdx > 0" class="text-white/20 text-sm my-1 pl-1">&amp;</div>
                  <div class="flex items-baseline gap-2">
                    <span class="font-anton text-white/40" style="font-size: 2rem;">#{{ slot.auditionNumber }}</span>
                    <span class="font-anton text-white leading-tight" style="font-size: clamp(1.5rem, 6vw, 2.8rem); text-transform: uppercase; letter-spacing: 0.05em;">{{ slot.participantName }}</span>
                  </div>
                  <div v-if="slot.judgeName" class="text-xs text-white/25 mt-0.5 pl-1">{{ slot.judgeName }}</div>
                </div>
              </template>
            </div>
            <div class="flex items-center justify-between px-4 pb-1.5">
              <span class="text-[8px] text-white/12 tracking-wider">← Prev</span>
              <span class="text-[8px] text-white/12">swipe</span>
              <span class="text-[8px] text-white/12 tracking-wider">Next →</span>
            </div>
          </div>
        </Transition>
      </div>
    </div>

    <!-- ── Timer at bottom (thumb reach) ── -->
    <div class="emcee-timer px-3 pb-3 pt-2 border-t border-white/5">
      <Timer />
    </div>

  </div>
</template>

<style scoped>
/* ── Current card ─────────────────────────────────────────────────────── */
.card-left-enter-active, .card-left-leave-active,
.card-right-enter-active, .card-right-leave-active {
  transition: transform 0.18s cubic-bezier(0.2,0,0.2,1), opacity 0.16s ease;
}
.card-left-enter-active,
.card-right-enter-active { will-change: transform, opacity; }
.card-left-enter-from  { transform: translate3d(32px,0,0);  opacity: 0; }
.card-left-enter-to    { transform: translate3d(0,0,0);     opacity: 1; }
.card-left-leave-from  { transform: translate3d(0,0,0);     opacity: 1; }
.card-left-leave-to    { transform: translate3d(-32px,0,0); opacity: 0; }
.card-right-enter-from { transform: translate3d(-32px,0,0); opacity: 0; }
.card-right-enter-to   { transform: translate3d(0,0,0);     opacity: 1; }
.card-right-leave-from { transform: translate3d(0,0,0);     opacity: 1; }
.card-right-leave-to   { transform: translate3d(32px,0,0);  opacity: 0; }

/* ── Queue item transitions ────────────────────────────────────────── */
.queue-item {
  transition: opacity 0.3s ease;
}

/* ── Landscape: timer left column, queue+card right column ──────────── */
@media (orientation: landscape) {
  .emcee-root {
    flex-direction: row !important;
    height: 100%;
    overflow: hidden;
  }
  .emcee-timer {
    width: 160px;
    flex: none;
    border-top: none !important;
    border-right-width: 1px;
    border-right-style: solid;
    border-color: rgba(255,255,255,0.05);
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
    padding-bottom: 12px;
    order: -1;
  }
  .emcee-queue {
    flex: 1;
    border-right: none;
  }
  .emcee-now {
    flex: none;
  }
}
</style>
