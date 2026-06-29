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

// For a given judge and round number, find the participant at that audition slot.
// judgeSlots[judge] is sorted by auditionNumber — slot at index (round-1).
const slotAt = (judgeName, round) => {
  const pool = props.judgeSlots[judgeName] ?? []
  return pool.find(p => p.auditionNumber === round) ?? null
}

// ── Timer ─────────────────────────────────────────────────────────────────────
const timerRef = ref(null)
const baselineDuration = ref(null)
const lastTimerState = ref({})

function buildJudgeSlotMap(round) {
  const slots = {}
  for (const j of judgeNames.value) {
    const slot = slotAt(j, round)
    slots[j] = slot
      ? { auditionNumber: slot.auditionNumber, participantName: slot.participantName, memberNames: slot.memberNames ?? [] }
      : null
  }
  return slots
}

async function publishState(timerState = {}) {
  if (!props.eventName || !props.categoryName) return
  await updateAuditionDisplay(props.eventName, {
    eventName:        props.eventName,
    categoryName:     props.categoryName,
    judgeMode:        true,
    judgeCurrentRound: props.currentRound,
    judgeSlots:       buildJudgeSlotMap(props.currentRound),
    timerStartedAt:   timerState.startedAt ?? null,
    timerDuration:    timerState.duration ?? null,
    timerRunning:     timerState.running ?? false,
    baselineDuration: baselineDuration.value,
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

function onTimerTick() {
  // OBS display reconstructs from timerStartedAt — no per-tick broadcast needed
}

function onAdvance() {
  timerRef.value?.reset?.()
  emit('advance')
}

function onRetreat() {
  timerRef.value?.reset?.()
  emit('retreat')
}
</script>

<template>
  <div class="flex flex-col h-full select-none" style="background:#111111;">

    <!-- Round counter -->
    <div class="flex items-center justify-between px-4 pt-3 pb-2">
      <span class="type-label text-content-muted">ROUND</span>
      <span class="text-accent font-oswald text-2xl font-medium tracking-wide">
        {{ currentRound }} / {{ maxRound }}
      </span>
    </div>

    <!-- Judge column headers -->
    <div
      class="grid px-3 gap-2 mb-2"
      :style="{ gridTemplateColumns: `repeat(${judgeNames.length}, 1fr)` }"
    >
      <div v-for="j in judgeNames" :key="j" class="card p-2 text-center">
        <span class="section-rule-label" style="text-transform:uppercase">{{ j }}</span>
      </div>
    </div>

    <!-- Participant slots for current round -->
    <div
      class="grid px-3 gap-2 flex-1"
      :style="{ gridTemplateColumns: `repeat(${judgeNames.length}, 1fr)` }"
    >
      <div
        v-for="j in judgeNames"
        :key="j"
        class="card p-3 flex flex-col items-center justify-center gap-1 min-h-[120px]"
        :class="slotAt(j, currentRound) ? '' : 'opacity-30'"
      >
        <template v-if="slotAt(j, currentRound)">
          <span class="text-accent font-oswald text-3xl font-medium tabular-nums">
            #{{ slotAt(j, currentRound).auditionNumber }}
          </span>
          <span class="type-name text-center text-sm">
            {{ slotAt(j, currentRound).participantName }}
          </span>
          <div v-if="slotAt(j, currentRound).memberNames?.length" class="flex flex-wrap gap-1 justify-center mt-1">
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

    <!-- Navigation -->
    <div class="flex gap-3 px-4 py-3">
      <button
        data-testid="prev-round"
        class="flex-1 card py-3 type-label transition-opacity"
        :disabled="currentRound <= 1"
        :class="currentRound <= 1 ? 'opacity-30' : ''"
        @click="onRetreat"
      >
        ‹ PREV
      </button>
      <button
        data-testid="next-round"
        class="flex-1 card py-3 type-label transition-opacity text-accent"
        :disabled="currentRound >= maxRound"
        :class="currentRound >= maxRound ? 'opacity-30' : ''"
        @click="onAdvance"
      >
        NEXT ›
      </button>
    </div>

    <!-- Timer (same position as EmceeRoundView) -->
    <div class="px-3 pb-3 pt-1">
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
