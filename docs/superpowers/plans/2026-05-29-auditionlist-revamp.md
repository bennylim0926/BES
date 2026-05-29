# AuditionList UI/UX Revamp Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the audition scoring and emcee views with bold Anton SC typography, gold (#f59e0b) judge accent, white emcee accent, column-reverse queue reading order, selector-tab pair scoring, and a sticky bottom action bar for judges — without touching any business logic.

**Architecture:** All backend calls, WebSocket subscriptions, scoring, and feedback logic stay unchanged. Only `<template>` and `<style>` sections are rewritten. New `submit`, `reset`, `jump` emits flow from scoring components up to `AuditionList`. `EmceeRoundView` absorbs `Timer` as a child, removing the need for AuditionList to render it. AuditionList gains a `hasActiveSession` computed that collapses the filter panel to a slim context bar during scoring.

**Tech Stack:** Vue 3 Composition API (`<script setup>`), Tailwind CSS v4, Anton SC (`font-anton` — already loaded in the project), CSS `clip-path: polygon(...)` for parallelogram buttons, `@media (orientation: landscape)` in scoped styles for split-pane layouts.

---

### Task 1: Timer.vue — Anton SC number, white emcee accent

**Files:**
- Modify: `BES-frontend/src/components/Timer.vue`

- [ ] **Step 1: Replace the `<template>` block (keep `<script setup>` unchanged)**

The `<script setup>` block (lines 1–63) stays identical. Replace everything from `<template>` to end of file:

```vue
<template>
  <div class="flex flex-col items-center gap-2 select-none">
    <!-- Anton SC number -->
    <div class="text-center leading-none">
      <div
        class="font-anton tabular-nums transition-colors duration-300"
        style="font-size: clamp(4rem, 15vw, 6rem);"
        :class="{
          'text-red-500 animate-pulse': isNearEnd,
          'text-white/40':              isFinished && !isNearEnd,
          'text-green-400':             countUp && !isNearEnd && !isFinished,
          'text-white':                 !isNearEnd && !isFinished && !countUp,
        }"
      >{{ displayTime }}</div>
      <div class="text-xs text-white/30 uppercase tracking-widest mt-1">
        {{ selectedTime > 0 ? `OF ${selectedTime}S` : 'SECONDS' }}
      </div>
    </div>

    <!-- Progress bar -->
    <div class="w-full max-w-xs h-0.5 bg-white/10 rounded-full overflow-hidden">
      <div
        class="h-full rounded-full transition-all duration-1000"
        :class="isNearEnd ? 'bg-red-500' : countUp ? 'bg-green-400' : 'bg-white/50'"
        :style="{ width: progressPct + '%' }"
      ></div>
    </div>

    <!-- Controls -->
    <div class="flex items-center gap-1.5 flex-wrap justify-center">
      <button
        @click="toggleMode"
        class="px-2.5 py-1 rounded-lg text-[11px] font-bold border transition-all duration-150 active:scale-95"
        :class="countUp
          ? 'bg-white/15 border-white/30 text-white'
          : 'bg-transparent border-white/15 text-white/40 hover:border-white/30 hover:text-white/60'"
      >
        <i :class="countUp ? 'pi pi-arrow-up' : 'pi pi-arrow-down'" class="mr-0.5 text-[9px]"></i>
        {{ countUp ? 'Up' : 'Dn' }}
      </button>
      <div class="w-px h-3.5 bg-white/15"></div>
      <button
        v-for="t in [30, 45, 60, 90]"
        :key="t"
        @click="startTimer(t)"
        class="px-2.5 py-1 rounded-lg text-[11px] font-bold border transition-all duration-150 active:scale-95"
        :class="selectedTime === t && isRunning
          ? 'bg-white/20 border-white/40 text-white'
          : 'bg-transparent border-white/15 text-white/40 hover:border-white/30 hover:text-white/60'"
      >{{ t }}</button>
    </div>
  </div>
</template>
```

- [ ] **Step 2: Verify in browser**

```bash
cd BES-frontend && npm run dev
```

Navigate to `http://localhost:5173/event/audition-list`, select Emcee + any genre. Confirm:
- Large Anton SC number renders (not smaller Outfit/Inter font)
- Clicking 30/45/60/90 starts countdown
- Last 5 seconds pulses red
- Count-up toggle (Dn/Up button) works
- "Done" no longer shown — displays `0` when finished

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/components/Timer.vue
git commit -m "feat(timer): Anton SC display, white emcee accent, compact controls"
```

---

### Task 2: EmceeRoundView.vue — Column-reverse queue, Timer at bottom, swipe-only, white accent

**Files:**
- Modify: `BES-frontend/src/components/EmceeRoundView.vue`

- [ ] **Step 1: Replace the entire file**

```vue
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
const upcomingRounds     = computed(() =>
  rounds.value.slice(currentRound.value).map((slots, i) => ({
    slots,
    roundNumber: currentRound.value + 1 + i,
  }))
)

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
  return { transform: `translateX(${x}px) rotate(${x * 0.03}deg)`, opacity: Math.max(0.4, 1 - Math.abs(x) / 250), transition: 'none', cursor: 'grabbing' }
})

const swipeHint = computed(() => {
  if (!isDragging.value) return null
  if (dragOffset.value < -30) return 'left'
  if (dragOffset.value > 30)  return 'right'
  return null
})
</script>

<template>
  <div class="emcee-root w-full flex flex-col" style="background: #060818; min-height: calc(100dvh - 64px);">

    <!-- ── Queue ──────────────────────────────────────────────────────────────
         flex-direction: column-reverse means DOM[0] (Up Next = currentRound+1)
         renders at the BOTTOM of this container, immediately above the NOW card.
         Further-out rounds stack upward with decreasing opacity.
    ──────────────────────────────────────────────────────────────────────── -->
    <div
      class="emcee-queue flex-1 overflow-y-auto px-4 pt-4 pb-2"
      style="display: flex; flex-direction: column-reverse; justify-content: flex-start; scrollbar-width: none;"
    >
      <TransitionGroup
        :name="direction === 'left' ? 'list-up' : 'list-down'"
        tag="div"
        class="flex flex-col-reverse gap-2"
      >
        <div
          v-for="({ slots, roundNumber }, uIdx) in upcomingRounds"
          :key="roundNumber"
          class="rounded-xl border overflow-hidden transition-all duration-300"
          :class="uIdx === 0 ? 'border-white/15 bg-white/5' : 'border-white/5 bg-transparent'"
          :style="{ opacity: uIdx === 0 ? '1' : uIdx === 1 ? '0.5' : uIdx === 2 ? '0.3' : '0.15' }"
        >
          <div class="flex items-center justify-between px-3 py-1.5">
            <span class="text-[10px] font-bold uppercase tracking-widest text-white/30">
              Round {{ roundNumber }}
            </span>
            <span
              v-if="uIdx === 0"
              class="text-[10px] px-2 py-0.5 rounded-full bg-white/10 text-white/50 border border-white/10 font-bold uppercase tracking-wider"
            >Up Next</span>
          </div>
          <div class="px-3 pb-2">
            <template v-for="(slot, sIdx) in slots" :key="sIdx">
              <div v-if="slot._placeholder" class="text-xs text-amber-400/40 italic">
                #{{ slot.auditionNumber }} — Not Registered
              </div>
              <div v-else class="flex items-center gap-2 flex-wrap">
                <span class="font-anton text-xl" :class="uIdx === 0 ? 'text-white/60' : 'text-white/25'">#{{ slot.auditionNumber }}</span>
                <span class="font-heading font-bold text-sm" :class="uIdx === 0 ? 'text-white/70' : 'text-white/30'" style="text-transform: uppercase; letter-spacing: 0.05em;">{{ slot.participantName }}</span>
                <span v-if="mode === 'PAIR' && sIdx === 0" class="text-white/20 text-xs">&amp;</span>
              </div>
            </template>
          </div>
        </div>
      </TransitionGroup>
    </div>

    <!-- ── NOW card ──────────────────────────────────────────────────────── -->
    <div class="emcee-now px-4 pb-3">
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
            class="rounded-2xl border border-white/20 select-none touch-pan-y"
            style="background: rgba(255,255,255,0.05); box-shadow: 0 0 0 1px rgba(255,255,255,0.06), 0 8px 32px rgba(0,0,0,0.6);"
          >
            <div class="flex items-center justify-between px-4 pt-3 pb-2 border-b border-white/8">
              <div class="flex items-center gap-2">
                <span class="w-2 h-2 rounded-full bg-white animate-pulse inline-block"></span>
                <span class="text-[10px] font-bold uppercase tracking-widest text-white/50">Now on Stage</span>
              </div>
              <span class="text-[10px] text-white/25 font-bold uppercase tracking-widest">
                Rd {{ currentRound }} / {{ totalRounds }}
              </span>
            </div>
            <div class="p-4">
              <template v-for="(slot, sIdx) in currentRoundSlots" :key="sIdx">
                <div v-if="slot._placeholder" class="text-amber-400/60 text-sm italic py-2">
                  #{{ slot.auditionNumber }} — Not Registered
                </div>
                <div v-else>
                  <div v-if="mode === 'PAIR' && sIdx > 0" class="text-white/20 text-sm my-1 pl-1">&amp;</div>
                  <div class="flex items-baseline gap-2">
                    <span class="font-anton text-white/40" style="font-size: 2rem;">#{{ slot.auditionNumber }}</span>
                    <span class="font-anton text-white leading-tight" style="font-size: clamp(1.5rem, 6vw, 2.8rem); text-transform: uppercase;">{{ slot.participantName }}</span>
                  </div>
                  <div v-if="slot.judgeName" class="text-xs text-white/25 mt-0.5 pl-1">{{ slot.judgeName }}</div>
                </div>
              </template>
            </div>
            <div class="text-center pb-2">
              <span class="text-[9px] text-white/15 tracking-widest">← swipe →</span>
            </div>
          </div>
        </Transition>
      </div>
    </div>

    <!-- ── Timer at bottom (thumb reach) ── -->
    <div class="emcee-timer px-4 pb-6 pt-3 border-t" style="border-color: rgba(255,255,255,0.05);">
      <Timer />
    </div>

  </div>
</template>

<style scoped>
/* ── Current card ─────────────────────────────────────────────────────── */
.card-left-enter-active, .card-left-leave-active,
.card-right-enter-active, .card-right-leave-active {
  transition: transform 0.28s cubic-bezier(0.4,0,0.2,1), opacity 0.28s ease;
}
.card-left-enter-from  { transform: translateX(60px);  opacity: 0; }
.card-left-enter-to    { transform: translateX(0);     opacity: 1; }
.card-left-leave-from  { transform: translateX(0);     opacity: 1; }
.card-left-leave-to    { transform: translateX(-60px); opacity: 0; }
.card-right-enter-from { transform: translateX(-60px); opacity: 0; }
.card-right-enter-to   { transform: translateX(0);     opacity: 1; }
.card-right-leave-from { transform: translateX(0);     opacity: 1; }
.card-right-leave-to   { transform: translateX(60px);  opacity: 0; }

/* ── Queue list transitions ─────────────────────────────────────────── */
.list-up-move,   .list-down-move   { transition: transform 0.32s cubic-bezier(0.4,0,0.2,1); }
.list-up-enter-active,   .list-down-enter-active   { transition: transform 0.32s cubic-bezier(0.4,0,0.2,1), opacity 0.28s ease; }
.list-up-leave-active,   .list-down-leave-active   { transition: transform 0.28s cubic-bezier(0.4,0,0.2,1), opacity 0.22s ease; position: absolute; width: 100%; }
.list-up-enter-from   { transform: translateY(32px);  opacity: 0; }
.list-up-enter-to     { transform: translateY(0);     opacity: 1; }
.list-up-leave-from   { transform: translateY(0);     opacity: 1; }
.list-up-leave-to     { transform: translateY(-20px); opacity: 0; }
.list-down-enter-from { transform: translateY(-32px); opacity: 0; }
.list-down-enter-to   { transform: translateY(0);     opacity: 1; }
.list-down-leave-from { transform: translateY(0);     opacity: 1; }
.list-down-leave-to   { transform: translateY(20px);  opacity: 0; }

/* ── Landscape: timer left column, queue+card right column ──────────── */
@media (orientation: landscape) {
  .emcee-root {
    flex-direction: row !important;
    height: calc(100dvh - 64px);
    overflow: hidden;
  }
  .emcee-timer {
    width: 160px;
    flex: none;
    border-top: none !important;
    border-right: 1px solid rgba(255,255,255,0.05);
    display: flex;
    flex-direction: column;
    justify-content: flex-end;
    padding-bottom: 24px;
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
```

- [ ] **Step 2: Verify in browser (portrait)**

Navigate to `/event/audition-list` as Emcee with a genre that has multiple participants. Confirm:
- No Prev/Next buttons
- Queue rounds stack above the NOW card; "Up Next" is the bottom-most queue item (immediately above NOW)
- Farther rounds fade out toward top
- Swiping the NOW card left/right advances/reverses rounds; queue updates accordingly
- Timer is at the bottom of the page

- [ ] **Step 3: Verify landscape**

Rotate device to landscape (or use browser DevTools → rotate). Confirm:
- Timer column on the left
- Queue + NOW card on the right
- Reading order still bottom-to-top within the right column

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/components/EmceeRoundView.vue
git commit -m "feat(emcee-round-view): column-reverse queue, Timer at bottom, swipe-only, landscape layout"
```

---

### Task 3: AuditionList.vue — Context bar, remove Timer, wire new emits

**Files:**
- Modify: `BES-frontend/src/views/AuditionList.vue`

- [ ] **Step 1: Remove the Timer import**

On line 10, remove:
```js
import Timer from '@/components/Timer.vue';
```

- [ ] **Step 2: Add `hasActiveSession` computed**

After line 322 (`const showFilters = ref(true)`), add:

```js
const hasActiveSession = computed(() => !!selectedGenre.value && !!selectedRole.value)
```

Also change the initial value of `showFilters` to `false` so the panel starts collapsed when returning to the page with a genre+role already saved in localStorage:

```js
const showFilters = ref(false)
```

- [ ] **Step 3: Replace the page header block (the `<!-- Page header + action toolbar -->` section)**

Find (approximately lines 409–460):
```html
    <!-- Page header + action toolbar -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-6">
      <div>
        <h1 class="page-title">Audition List</h1>
        <p class="text-muted mt-1">
          {{ selectedRole === 'Judge' ? 'Score participants for your genre' : 'Track audition progress' }}
        </p>
      </div>

      <!-- Action buttons -->
      <div class="flex items-center gap-2 flex-wrap">
        <!-- Toggle filters -->
        <button
          @click="showFilters = !showFilters"
          class="flex items-center gap-2 px-3.5 py-2 rounded-xl border text-sm font-semibold transition-all duration-200"
          :class="showFilters
            ? 'bg-surface-600 text-content-primary border-surface-500'
            : 'bg-surface-800 text-content-secondary border-surface-600 hover:border-surface-500'"
        >
          <i class="pi text-xs" :class="showFilters ? 'pi-filter-slash' : 'pi-filter'"></i>
          Filters
        </button>

        <!-- Judge-only actions -->
        <template v-if="selectedRole === 'Judge'">
          <button
            @click="showMiniMenu = !showMiniMenu"
            class="flex items-center gap-2 px-3.5 py-2 rounded-xl border border-surface-600 bg-surface-800
                   text-sm font-semibold text-content-secondary hover:border-surface-500 transition-all duration-200"
          >
            <i class="pi pi-search text-xs"></i>
            Jump
          </button>
          <button
            @click="confirmSubmit('Submit Scores', 'Are you sure you want to submit all scores now?')"
            class="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-primary-600 text-white text-sm
                   font-semibold hover:bg-primary-700 shadow-sm transition-all duration-200 btn-glow"
          >
            <i class="pi pi-send text-xs"></i>
            Submit
          </button>
          <button
            @click="confirmReset('Reset Scores', 'Are you sure you want to reset all scores? This cannot be undone.')"
            class="flex items-center gap-2 px-3.5 py-2 rounded-xl border border-red-800/50 bg-transparent
                   text-sm font-semibold text-red-400 hover:bg-red-950 transition-all duration-200"
          >
            <i class="pi pi-undo text-xs"></i>
            Reset
          </button>
        </template>
      </div>
    </div>
```

Replace with:
```html
    <!-- Context bar (active session: genre + role both selected) -->
    <div
      v-if="hasActiveSession"
      class="flex items-center justify-between px-4 py-2.5 mb-4 rounded-xl border border-white/8"
      style="background: #060818;"
    >
      <div class="flex items-center gap-2 text-sm font-bold text-white/60 flex-wrap">
        <span class="font-heading text-white/80">{{ selectedEvent }}</span>
        <span class="text-white/15">·</span>
        <span>{{ selectedGenre }}</span>
        <span class="text-white/15">·</span>
        <span class="uppercase text-xs tracking-widest">{{ judgingMode }}</span>
        <span class="text-white/15">·</span>
        <span>{{ selectedRole }}</span>
      </div>
      <button
        @click="showFilters = !showFilters"
        class="p-1.5 rounded-lg border transition-all active:scale-95"
        :class="showFilters ? 'border-white/30 text-white/70 bg-white/10' : 'border-white/10 text-white/30 hover:border-white/25 hover:text-white/60'"
      >
        <i class="pi pi-sliders-h text-xs"></i>
      </button>
    </div>

    <!-- Page header (no active session yet) -->
    <div v-else class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-6">
      <div>
        <h1 class="page-title">Audition List</h1>
        <p class="text-muted mt-1">
          {{ selectedRole === 'Judge' ? 'Score participants for your genre' : 'Track audition progress' }}
        </p>
      </div>
      <button
        @click="showFilters = !showFilters"
        class="flex items-center gap-2 px-3.5 py-2 rounded-xl border text-sm font-semibold transition-all duration-200 self-start"
        :class="showFilters
          ? 'bg-surface-600 text-content-primary border-surface-500'
          : 'bg-surface-800 text-content-secondary border-surface-600 hover:border-surface-500'"
      >
        <i class="pi text-xs" :class="showFilters ? 'pi-filter-slash' : 'pi-filter'"></i>
        Filters
      </button>
    </div>
```

- [ ] **Step 4: Update the filter panel `v-if`**

Find:
```html
      <div v-if="showFilters" class="card p-5 mb-6">
```

Replace with:
```html
      <div v-if="showFilters || !hasActiveSession" class="card p-5 mb-6">
```

- [ ] **Step 5: Remove `<Timer />` from the emcee template block**

Find:
```html
    <template v-if="selectedRole === 'Emcee' && filteredParticipantsForEmceeView.length > 0">
      <div class="sticky top-[72px] z-20 mb-4">
        <Timer />
      </div>
      <EmceeRoundView
        :participants="filteredParticipantsForEmceeView"
        :mode="judgingMode"
      />
    </template>
```

Replace with:
```html
    <template v-if="selectedRole === 'Emcee' && filteredParticipantsForEmceeView.length > 0">
      <EmceeRoundView
        :participants="filteredParticipantsForEmceeView"
        :mode="judgingMode"
      />
    </template>
```

- [ ] **Step 6: Wire `@submit`, `@reset`, `@jump` on both scoring components**

Find the `<PairScoreCards>` usage and replace with:
```html
      <PairScoreCards
        v-if="judgingMode === 'PAIR'"
        :cards="filteredParticipantsForJudge"
        :feedbackData="feedbackGiven"
        :criteria="criteria"
        @open-feedback="openFeedbackPopout"
        @remove-tag="removeTag"
        @submit="confirmSubmit('Submit Scores', 'Are you sure you want to submit all scores now?')"
        @reset="confirmReset('Reset Scores', 'Are you sure you want to reset all scores? This cannot be undone.')"
        @jump="showMiniMenu = true"
      />
      <SwipeableCardsV2
        v-else
        :cards="filteredParticipantsForJudge"
        :feedbackData="feedbackGiven"
        :criteria="criteria"
        @open-feedback="openFeedbackPopout"
        @remove-tag="removeTag"
        @submit="confirmSubmit('Submit Scores', 'Are you sure you want to submit all scores now?')"
        @reset="confirmReset('Reset Scores', 'Are you sure you want to reset all scores? This cannot be undone.')"
        @jump="showMiniMenu = true"
      />
```

- [ ] **Step 7: Verify in browser**

1. Load `/event/audition-list` with no genre selected — full page header + filter panel visible
2. Select a role and genre — context bar appears (`event · genre · mode · role`), filter collapses
3. Tap the sliders icon — filter panel toggles open/closed
4. Switch to Emcee → Timer now renders at the bottom of the EmceeRoundView, NOT at the top of the page

- [ ] **Step 8: Commit**

```bash
git add BES-frontend/src/views/AuditionList.vue
git commit -m "feat(audition-list): context bar, remove standalone Timer, wire submit/reset/jump"
```

---

### Task 4: SwipeableCardsV2.vue — Bold gold redesign, parallelogram buttons, sticky bottom bar, landscape

**Files:**
- Modify: `BES-frontend/src/components/SwipeableCardsV2.vue`

- [ ] **Step 1: Replace the entire file**

```vue
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
  <div class="w-full relative" style="background: #060818; padding-bottom: 72px;">

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
        style="width: 97%;"
      >
        <!-- Score card -->
        <div
          class="rounded-2xl border p-3 transition-all duration-200"
          :class="idx === currentIndex ? 'border-amber-500/25' : 'border-white/5 opacity-40'"
          :style="idx === currentIndex
            ? 'background: #0d1225; box-shadow: 0 0 0 1px rgba(245,158,11,0.12), 0 8px 32px rgba(0,0,0,0.7);'
            : 'background: #060818;'"
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
                class="mb-2 p-2 rounded-xl border border-white/6"
                style="background: rgba(255,255,255,0.02);"
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
                <div class="h-px mb-2" style="background: rgba(255,255,255,0.05);"></div>
                <div class="flex gap-0 mb-2 overflow-x-auto border-b" style="scrollbar-width: none; border-color: rgba(255,255,255,0.06);">
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
                      <div class="rounded-xl p-1.5" style="background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.05);">
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
                      <div class="rounded-xl p-1.5" style="background: rgba(245,158,11,0.03); border: 1px solid rgba(245,158,11,0.10);">
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
                  <div class="rounded-xl p-1.5" style="background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.05);">
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
                  <div class="rounded-xl p-1.5" style="background: rgba(245,158,11,0.03); border: 1px solid rgba(245,158,11,0.10);">
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
      class="fixed bottom-0 left-0 right-0 z-30 flex items-center gap-2 px-4 py-3 border-t"
      style="background: #060818; border-color: rgba(255,255,255,0.07);"
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
```

- [ ] **Step 2: Verify in browser as Judge (portrait)**

Load `/event/audition-list` as Judge role with a genre selected and participants with audition numbers. Confirm:
- Dark `#0d1225` card background with subtle gold border glow
- Anton SC gold `#f59e0b` number (`#04`) and score display
- Parallelogram-clipped digit buttons (slightly skewed shape)
- Active digit button fills gold; inactive buttons are subtle dark
- Tapping a digit sets the score correctly
- Feedback button shows green dot when feedback is given
- Swipe dots at bottom use gold for active position
- Sticky bottom bar: Reset / Jump / Submit All visible; Submit is gold parallelogram

- [ ] **Step 3: Verify landscape orientation**

Rotate device/DevTools to landscape. Confirm:
- Participant info + feedback are in the left half of the card
- Keypad fills the right half
- No scrolling needed to see the keypad

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/components/SwipeableCardsV2.vue
git commit -m "feat(swipeable-cards): Anton SC gold redesign, parallelogram buttons, landscape split-pane, sticky bottom bar"
```

---

### Task 5: PairScoreCards.vue — Selector tab pattern, gold accent, landscape, sticky bottom bar

**Files:**
- Modify: `BES-frontend/src/components/PairScoreCards.vue`

- [ ] **Step 1: Replace the entire file**

```vue
<script setup>
import { ref, computed, nextTick, onMounted, watch } from 'vue';

const props = defineProps({
  cards:        { type: Array,  required: true },
  feedbackData: { type: Object, default: () => new Map() },
  criteria:     { type: Array,  default: () => [] },
});

const emit = defineEmits(['open-feedback', 'remove-tag', 'submit', 'reset', 'jump']);

const scrollRef    = ref(null)
const currentIndex = ref(0)

const hasCriteria = computed(() => props.criteria.length > 0)

// Active criterion tab per participant: keyed by auditionNumber
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
    (entries) => { entries.forEach((e) => { if (e.isIntersecting) currentIndex.value = Number(e.target.getAttribute('data-index')) }) },
    { root: scrollRef.value, threshold: 0.6 }
  )
  slides.forEach((s) => observer.observe(s))
}

onMounted(observePairs)

// Active participant tab within the current pair
const activeParticipantNum = ref(null)

const activePair = computed(() => pairs.value[currentIndex.value] ?? [])
const activeCard = computed(() =>
  activePair.value.find(c => !c._placeholder && c.auditionNumber === activeParticipantNum.value) ?? null
)

// When pair changes, reset tab to first non-placeholder participant
watch(currentIndex, () => {
  const first = activePair.value.find(c => !c._placeholder)
  activeParticipantNum.value = first?.auditionNumber ?? null
}, { immediate: true })

const updateDecimal = (score, num) => score === 10 ? 10 : Math.floor(score) + num / 10
const updateCriteriaDecimal = (card, criterionName, num) => {
  const newVal = criteriaScore(card, criterionName) === 10 ? 10 : Math.floor(criteriaScore(card, criterionName)) + num / 10
  setCriteriaScore(card, criterionName, newVal)
}

const aggregateDisplay = computed(() => {
  if (!hasCriteria.value || !activeCard.value) return null
  return computeAggregate(activeCard.value)
})

const isActivePair = (pairIdx) => pairIdx === currentIndex.value
</script>

<template>
  <div class="w-full relative" style="background: #060818; padding-bottom: 72px;">

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
      class="flex overflow-x-auto snap-x snap-mandatory scroll-smooth gap-2 px-2 pb-4"
      style="scrollbar-width: none;"
    >
      <div
        v-for="(pair, pairIdx) in pairs"
        :key="pairIdx"
        :data-index="pairIdx"
        data-pair
        class="flex-shrink-0 snap-center"
        style="width: 97%;"
      >
        <div
          class="rounded-2xl border p-3 transition-all duration-200"
          :class="isActivePair(pairIdx) ? 'border-amber-500/25' : 'border-white/5 opacity-40'"
          :style="isActivePair(pairIdx)
            ? 'background: #0d1225; box-shadow: 0 0 0 1px rgba(245,158,11,0.12), 0 8px 32px rgba(0,0,0,0.7);'
            : 'background: #060818;'"
        >

          <!-- Round label -->
          <div class="flex items-center justify-between mb-3 px-1">
            <span class="text-[9px] font-bold text-white/25 uppercase tracking-widest">
              Round {{ pairIdx + 1 }} of {{ pairs.length }}
            </span>
            <span v-if="pairIdx === pairs.length - 1" class="text-[9px] px-2 py-0.5 rounded-full bg-white/8 text-white/35 border border-white/10 font-bold uppercase tracking-wider">Last</span>
          </div>

          <!-- ── Participant selector tabs ── -->
          <div class="flex gap-2 mb-3">
            <template v-for="(card, slotIdx) in pair" :key="slotIdx">
              <!-- Placeholder tab -->
              <div
                v-if="card._placeholder"
                class="flex-1 flex items-center justify-center px-3 py-2 rounded-xl border border-dashed border-amber-500/20 bg-amber-500/4"
              >
                <span class="text-xs text-amber-400/40 italic">#{{ card.auditionNumber }} Empty</span>
              </div>
              <!-- Real participant tab -->
              <button
                v-else
                @click="if (isActivePair(pairIdx)) activeParticipantNum = card.auditionNumber"
                :disabled="!isActivePair(pairIdx)"
                class="flex-1 flex items-center gap-2 px-3 py-2 rounded-xl border transition-all duration-150 active:scale-98 disabled:cursor-not-allowed text-left"
                :class="activeParticipantNum === card.auditionNumber && isActivePair(pairIdx)
                  ? 'border-amber-500/40 bg-amber-500/12'
                  : 'border-white/8 bg-white/3 opacity-60'"
              >
                <span
                  class="font-anton text-xl leading-none flex-shrink-0"
                  :class="activeParticipantNum === card.auditionNumber && isActivePair(pairIdx) ? 'text-amber-400' : 'text-white/30'"
                >#{{ card.auditionNumber }}</span>
                <div class="flex-1 min-w-0">
                  <div
                    class="font-heading font-bold text-sm leading-tight truncate"
                    :class="activeParticipantNum === card.auditionNumber && isActivePair(pairIdx) ? 'text-white' : 'text-white/35'"
                  >{{ card.participantName }}</div>
                  <div
                    v-if="card.score > 0"
                    class="text-[10px] font-source tabular-nums mt-0.5"
                    :class="activeParticipantNum === card.auditionNumber && isActivePair(pairIdx) ? 'text-amber-400/60' : 'text-white/20'"
                  >{{ card.score }}</div>
                </div>
                <!-- Green dot: feedback given for this participant -->
                <span v-if="feedbackData?.get(card.auditionNumber)" class="w-2 h-2 rounded-full bg-green-400 flex-shrink-0"></span>
              </button>
            </template>
          </div>

          <!-- ── Active participant's score + keypad ── -->
          <div v-if="activeCard && isActivePair(pairIdx)">

            <div class="pair-card-body">

              <!-- Info column -->
              <div class="pair-card-info">
                <!-- Score display -->
                <div class="flex items-center justify-between mb-2">
                  <div>
                    <div class="text-[9px] font-bold text-white/25 uppercase tracking-widest mb-0.5">
                      {{ hasCriteria ? 'AVG' : 'SCORE' }}
                    </div>
                    <div
                      class="font-anton tabular-nums leading-none"
                      style="font-size: 3rem;"
                      :class="activeCard.score === 0 ? 'text-white/15' : 'text-amber-400'"
                    >{{ activeCard.score === 0 ? '—' : activeCard.score }}</div>
                  </div>
                  <!-- Feedback button -->
                  <button
                    @click.stop="emit('open-feedback', activeCard)"
                    class="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-bold border transition-all duration-150 active:scale-95"
                    :class="feedbackData?.get(activeCard.auditionNumber)
                      ? 'bg-green-500/15 border-green-500/35 text-green-400'
                      : 'bg-white/4 border-white/10 text-white/35 hover:text-white/60 hover:border-white/20'"
                  >
                    <i class="pi pi-comment text-xs" />
                    {{ feedbackData?.get(activeCard.auditionNumber) ? 'Edit' : 'Feedback' }}
                    <span v-if="feedbackData?.get(activeCard.auditionNumber)" class="ml-1 w-1.5 h-1.5 rounded-full bg-green-400 inline-block"></span>
                  </button>
                </div>

                <!-- Feedback preview -->
                <div
                  v-if="feedbackData?.get(activeCard.auditionNumber)"
                  class="mb-2 p-2 rounded-xl border border-white/6"
                  style="background: rgba(255,255,255,0.02);"
                >
                  <div v-if="feedbackData.get(activeCard.auditionNumber).tagLabels?.length" class="flex flex-wrap gap-1.5 mb-1">
                    <span
                      v-for="tag in feedbackData.get(activeCard.auditionNumber).tagLabels"
                      :key="tag.id"
                      class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium bg-amber-500/10 text-amber-300 border border-amber-500/20"
                    >
                      {{ tag.label }}
                      <button @click.stop="emit('remove-tag', { auditionNumber: activeCard.auditionNumber, tagId: tag.id })" class="text-amber-400/40 hover:text-red-400 transition-colors">
                        <i class="pi pi-times text-[9px]" />
                      </button>
                    </span>
                  </div>
                  <div v-if="feedbackData.get(activeCard.auditionNumber).note" class="text-xs text-white/35 line-clamp-2">
                    {{ feedbackData.get(activeCard.auditionNumber).note }}
                  </div>
                </div>

                <!-- Criteria tabs -->
                <template v-if="hasCriteria">
                  <div class="flex gap-0 mb-2 overflow-x-auto border-b" style="scrollbar-width: none; border-color: rgba(255,255,255,0.06);">
                    <button
                      v-for="criterion in criteria"
                      :key="criterion.id"
                      @click="setActiveCriterion(activeCard.auditionNumber, criterion.name)"
                      class="flex-shrink-0 flex items-center gap-1 px-3 py-1.5 text-xs font-bold border-b-2 transition-all duration-150 -mb-px"
                      :class="getActiveCriterion(activeCard.auditionNumber) === criterion.name
                        ? 'border-amber-400 text-amber-400'
                        : 'border-transparent text-white/25 hover:text-white/50'"
                    >
                      {{ criterion.name }}
                      <span v-if="criteriaScore(activeCard, criterion.name) > 0" class="font-source tabular-nums text-amber-300/60 text-[10px]">
                        {{ criteriaScore(activeCard, criterion.name) }}
                      </span>
                    </button>
                  </div>
                  <template v-for="criterion in criteria" :key="criterion.id">
                    <div v-if="getActiveCriterion(activeCard.auditionNumber) === criterion.name" class="flex items-center justify-between mb-2">
                      <span class="text-[9px] font-bold text-white/25 uppercase tracking-widest">{{ criterion.name }}<span v-if="criterion.weight != null" class="text-amber-400/40 ml-1">×{{ criterion.weight }}</span></span>
                      <span class="font-anton text-2xl tabular-nums" :class="criteriaScore(activeCard, criterion.name) === 0 ? 'text-white/15' : 'text-amber-400'">
                        {{ criteriaScore(activeCard, criterion.name) === 0 ? '—' : criteriaScore(activeCard, criterion.name) }}
                      </span>
                    </div>
                  </template>
                </template>
              </div><!-- /pair-card-info -->

              <!-- Keypad column -->
              <div class="pair-card-keypad">

                <!-- Multi-criteria mode -->
                <template v-if="hasCriteria">
                  <template v-for="criterion in criteria" :key="criterion.id">
                    <div v-if="getActiveCriterion(activeCard.auditionNumber) === criterion.name">
                      <button
                        @click="setCriteriaScore(activeCard, criterion.name, 10)"
                        class="w-full py-2 mb-2 font-bold text-sm border transition-all duration-150 active:scale-[0.98]"
                        style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.35); color: rgb(245,158,11);"
                      >10 — Full Score</button>
                      <div class="grid grid-cols-2 gap-1.5">
                        <div class="rounded-xl p-1.5" style="background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.05);">
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
                        <div class="rounded-xl p-1.5" style="background: rgba(245,158,11,0.03); border: 1px solid rgba(245,158,11,0.10);">
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
                    @click="activeCard.score = 10"
                    class="w-full py-2 mb-2 font-bold text-sm border transition-all duration-150 active:scale-[0.98]"
                    style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(245,158,11,0.12); border-color: rgba(245,158,11,0.35); color: rgb(245,158,11);"
                  >10 — Full Score</button>
                  <div class="grid grid-cols-2 gap-1.5">
                    <div class="rounded-xl p-1.5" style="background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.05);">
                      <div class="text-[9px] font-bold text-white/20 uppercase tracking-widest mb-1.5 text-center">Whole</div>
                      <div class="grid grid-cols-3 gap-1">
                        <button
                          v-for="value in 9" :key="'w'+value"
                          @click="activeCard.score = Number(value)"
                          class="py-4 text-sm font-bold transition-all duration-100 active:scale-95"
                          :class="Math.floor(activeCard.score) === value && activeCard.score === value ? 'text-black' : 'text-white/55 hover:text-white'"
                          :style="Math.floor(activeCard.score) === value && activeCard.score === value
                            ? 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgb(245,158,11);'
                            : 'clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%); background: rgba(255,255,255,0.05);'"
                        >{{ value }}</button>
                      </div>
                    </div>
                    <div class="rounded-xl p-1.5" style="background: rgba(245,158,11,0.03); border: 1px solid rgba(245,158,11,0.10);">
                      <div class="text-[9px] font-bold text-amber-400/35 uppercase tracking-widest mb-1.5 text-center">Decimal</div>
                      <div class="grid grid-cols-3 gap-1">
                        <button
                          v-for="value in 9" :key="'d'+value"
                          @click="activeCard.score = updateDecimal(activeCard.score, value)"
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

          <!-- No active participant (both placeholders) -->
          <div v-else-if="isActivePair(pairIdx)" class="text-center py-8 text-amber-400/40 text-sm italic">
            No registered participants in this pair
          </div>

        </div>
      </div>
    </div>

    <!-- ── Sticky bottom action bar ── -->
    <div
      class="fixed bottom-0 left-0 right-0 z-30 flex items-center gap-2 px-4 py-3 border-t"
      style="background: #060818; border-color: rgba(255,255,255,0.07);"
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
  .pair-card-body {
    display: grid;
    grid-template-columns: 1fr 1fr;
    column-gap: 16px;
    align-items: start;
  }
}
</style>
```

- [ ] **Step 2: Verify pair mode in browser**

Set judging mode to PAIR (Admin role → filter panel → PAIR toggle, or update via the existing toggle in AuditionList). Then load the Judge view. Confirm:
- Two participant tabs at the top of the slide (not stacked cards)
- Active tab shows gold border; inactive tab is muted
- Green dot visible on a tab after giving feedback to that participant
- Tapping the inactive tab switches scoring focus — score display and keypad update to show that participant's state
- Swiping left/right changes pairs (pair 1&2 → pair 3&4)
- Landscape: info+feedback in left half, keypad in right half — no scrolling needed

- [ ] **Step 3: Verify score persistence across tab switches**

Score participant #1 with 7.5. Tap tab for participant #2. Score #2 with 6.0. Tap back to #1 — confirm score still shows 7.5 (mutations are on the card objects directly in the reactive `participants` array).

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/components/PairScoreCards.vue
git commit -m "feat(pair-score-cards): selector tab pattern, gold accent, feedback dot, landscape split-pane"
```

---

## Self-Review

**Spec coverage check:**

| Spec section | Task covering it |
|---|---|
| 2. Design language: `#060818`, `#0d1225`, gold `#f59e0b`, Anton SC, parallelogram buttons | Tasks 4, 5 |
| 3. SOLO judge — portrait layout, score display, feedback button, bottom bar | Task 4 |
| 3.2 Multi-criteria tabs — gold underline, AVG in bottom bar | Task 4 |
| 3.4 Feedback button — green dot when given | Tasks 4, 5 |
| 3.5 Sticky bottom bar — Reset / Jump / Submit | Tasks 4, 5 |
| 4. PAIR mode — selector tab, green dot on tab, no scroll | Task 5 |
| 4.2 PAIR landscape — tabs in left panel, keypad right | Task 5 |
| 5. Emcee — column-reverse queue, Up Next nearest to NOW | Task 2 |
| 5.2 Swipe-only navigation, no Prev/Next buttons | Task 2 |
| 5.3 Judge name only if assigned | Task 2 (only renders `v-if="slot.judgeName"`) |
| 5.4 PAIR mode `&` separator in emcee | Task 2 |
| 5.5 Landscape: timer left column, queue+NOW right column | Task 2 |
| 5.6 Timer states: white / red pulse / green count-up | Task 1 |
| 6. Filter panel → slim context bar on `hasActiveSession` | Task 3 |

All spec sections are covered. No gaps found.

**Placeholder scan:** No TBD, TODO, or incomplete sections.

**Type consistency:** 
- `emit('submit' | 'reset' | 'jump')` defined in both Tasks 4 and 5 match the handler bindings in Task 3 (`@submit`, `@reset`, `@jump`). ✓
- `activeCard` in PairScoreCards (Task 5) is used consistently for scoring actions (`activeCard.score`, `setCriteriaScore(activeCard, ...)`) ✓
- `aggregateDisplay` computed is defined in both Tasks 4 and 5, using the same `computeAggregate` function ✓
