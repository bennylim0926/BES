<script setup>
import { computed, ref } from 'vue'
import Timer from './Timer.vue'
import { updateAuditionDisplay } from '@/utils/api'

const props = defineProps({
  judgeSlots:   { type: Object, required: true },
  currentRound: { type: Number, required: true },
  maxRound:     { type: Number, required: true },
  eventName:    { type: String, default: '' },
  categoryName: { type: String, default: '' },
})

const emit = defineEmits(['advance', 'retreat'])

const judgeNames = computed(() => Object.keys(props.judgeSlots).filter(k => k !== '__unassigned__'))

const slotAt = (judgeName, round) => {
  const pool = props.judgeSlots[judgeName] ?? []
  return pool.find(p => p.auditionNumber === round) ?? null
}

// All upcoming rounds reversed so UP NEXT sits at the bottom of the queue (nearest NOW)
const upcomingRounds = computed(() => {
  const rounds = []
  for (let r = props.currentRound + 1; r <= props.maxRound; r++) rounds.push(r)
  return rounds.reverse()
})

// ── Timer ──────────────────────────────────────────────────────────────────
const timerRef = ref(null)
const baselineDuration = ref(null)
const lastTimerState = ref({})

function buildJudgeSlotMap(round) {
  const slots = {}
  for (const j of judgeNames.value) {
    const s = slotAt(j, round)
    slots[j] = s
      ? { auditionNumber: s.auditionNumber, participantName: s.participantName, memberNames: s.memberNames ?? [] }
      : null
  }
  return slots
}

async function publishState(timerState = {}) {
  if (!props.eventName || !props.categoryName) return
  await updateAuditionDisplay(props.eventName, {
    eventName:         props.eventName,
    categoryName:      props.categoryName,
    judgeMode:         true,
    judgeCurrentRound: props.currentRound,
    judgeSlots:        buildJudgeSlotMap(props.currentRound),
    timerStartedAt:    timerState.startedAt ?? null,
    timerDuration:     timerState.duration ?? null,
    timerRunning:      timerState.running ?? false,
    baselineDuration:  baselineDuration.value,
  })
}

function onTimerStarted(detail) {
  baselineDuration.value = detail.duration
  lastTimerState.value = { startedAt: detail.startedAt, duration: detail.duration, running: true }
  publishState(lastTimerState.value)
}

function onTimerStopped() {
  lastTimerState.value = { startedAt: null, duration: null, running: false }
  publishState(lastTimerState.value)
}

function onTimerTick() { /* OBS reconstructs from timerStartedAt */ }

function onAdvance() { timerRef.value?.reset?.(); emit('advance') }
function onRetreat() { timerRef.value?.reset?.(); emit('retreat') }
</script>

<template>
  <div class="judge-root w-full flex flex-col h-full touch-manipulation select-none" style="background:#111111; overflow-y:auto;">

    <!-- Round counter + judge column headers (sticky) -->
    <div class="shrink-0 px-3 pt-3 pb-1">
      <div class="flex items-center justify-between mb-2">
        <span class="type-label text-content-muted">ROUND</span>
        <span class="text-accent font-oswald text-2xl font-medium tracking-wide">
          {{ currentRound }} / {{ maxRound }}
        </span>
      </div>
      <!-- Judge headers -->
      <div class="grid gap-2" :style="{ gridTemplateColumns: `repeat(${judgeNames.length}, 1fr)` }">
        <div v-for="j in judgeNames" :key="j" class="card p-1.5 text-center">
          <span class="section-rule-label" style="text-transform:uppercase;font-size:10px">{{ j }}</span>
        </div>
      </div>
    </div>

    <!-- Scrollable queue: upcoming rounds (reversed — UP NEXT closest to NOW) -->
    <div
      class="judge-queue flex-1 px-3 pt-1 pb-1"
      style="display:flex; flex-direction:column-reverse; justify-content:flex-start; overflow-y:auto; scrollbar-width:none; overscroll-behavior:contain;"
    >
      <div class="flex flex-col gap-1">
        <div
          v-for="(r, idx) in upcomingRounds"
          :key="r"
          class="para-chip-sm overflow-hidden flex-shrink-0"
          :class="idx === upcomingRounds.length - 1
            ? 'border-white/15 bg-white/5'
            : 'border-white/5 bg-transparent'"
          style="opacity:0.45"
        >
          <div class="flex items-center gap-2 px-2 pt-1 pb-0.5">
            <span class="type-label text-content-muted tabular-nums" style="min-width:3rem">
              {{ idx === upcomingRounds.length - 1 ? 'UP NEXT' : `RND ${r}` }}
            </span>
          </div>
          <!-- Per-judge mini slots for this round -->
          <div class="grid gap-1.5 px-2 pb-1.5" :style="{ gridTemplateColumns: `repeat(${judgeNames.length}, 1fr)` }">
            <div v-for="j in judgeNames" :key="j" class="flex flex-col min-w-0">
              <template v-if="slotAt(j, r)">
                <span class="type-label text-accent tabular-nums" style="font-size:11px">#{{ slotAt(j, r).auditionNumber }}</span>
                <span class="type-name text-content-muted truncate" style="font-size:12px">{{ slotAt(j, r).participantName }}</span>
              </template>
              <span v-else class="type-label text-content-muted/30" style="font-size:11px">—</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- NOW: current round grid -->
    <div class="shrink-0 px-3 pb-1">
      <div class="type-label text-content-muted mb-1.5" style="font-size:10px;letter-spacing:0.18em">NOW</div>
      <div class="grid gap-2" :style="{ gridTemplateColumns: `repeat(${judgeNames.length}, 1fr)` }">
        <div
          v-for="j in judgeNames"
          :key="j"
          class="card p-3 flex flex-col items-center justify-center gap-1"
          style="min-height:110px"
          :class="slotAt(j, currentRound) ? '' : 'opacity-30'"
        >
          <template v-if="slotAt(j, currentRound)">
            <span class="text-accent font-oswald text-3xl font-medium tabular-nums">
              #{{ slotAt(j, currentRound).auditionNumber }}
            </span>
            <span class="type-name text-center" style="font-size:13px">
              {{ slotAt(j, currentRound).participantName }}
            </span>
            <div v-if="slotAt(j, currentRound).memberNames?.length" class="flex flex-wrap gap-1 justify-center mt-0.5">
              <span
                v-for="m in slotAt(j, currentRound).memberNames"
                :key="m"
                class="para-chip-sm type-label px-1.5 py-0.5"
              >{{ m }}</span>
            </div>
          </template>
          <template v-else>
            <span class="text-content-muted font-oswald text-3xl">—</span>
            <span class="type-label text-content-muted text-xs">WAITING</span>
          </template>
        </div>
      </div>
    </div>

    <!-- Navigation -->
    <div class="shrink-0 flex gap-3 px-3 py-2">
      <button
        data-testid="prev-round"
        class="flex-1 card py-3 type-label transition-opacity"
        :disabled="currentRound <= 1"
        :class="currentRound <= 1 ? 'opacity-30' : ''"
        @click="onRetreat"
      >‹ PREV</button>
      <button
        data-testid="next-round"
        class="flex-1 card py-3 type-label transition-opacity text-accent"
        :disabled="currentRound >= maxRound"
        :class="currentRound >= maxRound ? 'opacity-30' : ''"
        @click="onAdvance"
      >NEXT ›</button>
    </div>

    <!-- Timer -->
    <div class="shrink-0 px-3 pb-3 pt-1">
      <Timer
        ref="timerRef"
        :baseline-duration="baselineDuration"
        @started="onTimerStarted"
        @stopped="onTimerStopped"
        @tick="onTimerTick"
      />
    </div>

  </div>
</template>

<style scoped>
.judge-root { overflow-y: auto; }
.judge-queue::-webkit-scrollbar { display: none; }
</style>
