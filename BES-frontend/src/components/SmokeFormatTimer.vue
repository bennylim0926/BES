<script setup>
/**
 * SmokeFormatTimer.vue
 *
 * Session-level countdown for 7-to-Smoke format.
 *   - Operator selects a duration (30m / 45m preset, or custom)
 *   - Timer auto-starts when the first round begins (autoStartIfIdle() called from LiveMatchPanel)
 *   - Stays EXPIRED — operator must manually reset
 *   - Expiry flags state so Get Score shows the winner picker instead of normal flow
 */
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'

const props = defineProps({
  stompClient:   { type: Object, default: null },
  recoveryState: { type: Object, default: null },
  eventName:     { type: String, default: '' }
})

const emit = defineEmits(['expired'])

// ── State ────────────────────────────────────────────────────────────
const timerState       = ref('IDLE')   // 'IDLE' | 'RUNNING' | 'EXPIRED'
const selectedMinutes  = ref(30)       // chosen duration — used when autoStartIfIdle fires
const showCustomInput  = ref(false)    // toggle custom input visibility
const customMinutes    = ref('')
const isCustomSelected = ref(false)    // true when a custom duration chip is active
const totalDuration    = ref(0)
const timeLeft         = ref(0)

let intervalId = null

// ── Computed ─────────────────────────────────────────────────────────
const isIdle    = computed(() => timerState.value === 'IDLE')
const isRunning = computed(() => timerState.value === 'RUNNING')
const isExpired = computed(() => timerState.value === 'EXPIRED')

const displayTime = computed(() => {
  const m = Math.floor(timeLeft.value / 60)
  const s = timeLeft.value % 60
  return `${m}:${String(s).padStart(2, '0')}`
})

const progressPct = computed(() => {
  if (totalDuration.value === 0) return 0
  return (timeLeft.value / totalDuration.value) * 100
})

const isWarning = computed(() => isRunning.value && timeLeft.value <= 300) // last 5 min

// ── Timer control ────────────────────────────────────────────────────
function startWithDuration(seconds) {
  clearInterval(intervalId)
  intervalId = null
  timerState.value = 'RUNNING'
  totalDuration.value = seconds
  timeLeft.value = seconds
  publishState()
  intervalId = setInterval(() => {
    timeLeft.value--
    publishState()
    if (timeLeft.value <= 0) expireTimer()
  }, 1000)
}

function expireTimer() {
  clearInterval(intervalId)
  intervalId = null
  timerState.value = 'EXPIRED'
  timeLeft.value = 0
  publishState()
  emit('expired')
}

function resetTimer() {
  clearInterval(intervalId)
  intervalId = null
  timerState.value = 'IDLE'
  totalDuration.value = 0
  timeLeft.value = 0
  selectedMinutes.value = 30
  showCustomInput.value = false
  customMinutes.value = ''
  isCustomSelected.value = false
  publishState()
}

function selectPreset(minutes) {
  selectedMinutes.value = minutes
  showCustomInput.value = false
  customMinutes.value = ''
  isCustomSelected.value = false
}

function toggleCustomInput() {
  showCustomInput.value = !showCustomInput.value
  if (!showCustomInput.value) customMinutes.value = ''
}

function confirmCustom() {
  const mins = parseInt(customMinutes.value, 10)
  if (!mins || mins <= 0) return
  selectedMinutes.value = mins
  showCustomInput.value = false
  customMinutes.value = ''
  isCustomSelected.value = true
}

function removeCustom() {
  isCustomSelected.value = false
  selectedMinutes.value = 30
  customMinutes.value = ''
  showCustomInput.value = false
}

/** Called from LiveMatchPanel when the first smoke round starts. Only fires if IDLE. */
function autoStartIfIdle() {
  if (!isIdle.value) return
  startWithDuration(selectedMinutes.value * 60)
}

defineExpose({ isExpired, autoStartIfIdle, resetTimer, selectedMinutes })

// ── Backend sync ─────────────────────────────────────────────────────
function publishState() {
  try {
    const url = props.eventName
      ? `/api/v1/battle/format-timer?event=${encodeURIComponent(props.eventName)}`
      : '/api/v1/battle/format-timer'
    fetch(url, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        running: isRunning.value,
        timeLeft: timeLeft.value,
        totalDuration: totalDuration.value,
        expired: isExpired.value
      })
    }).catch(() => { /* fire-and-forget */ })
  } catch (_) { /* ignore publish errors */ }
}

function recoverFromState(state) {
  if (!state) return
  if (timerState.value !== 'IDLE') return

  if (state.expired) {
    timerState.value = 'EXPIRED'
    timeLeft.value = 0
    totalDuration.value = state.totalDuration || 0
    return
  }
  if (!state.running || (state.timeLeft ?? 0) <= 0) return

  timerState.value = 'RUNNING'
  totalDuration.value = state.totalDuration || state.timeLeft
  timeLeft.value = state.timeLeft
  selectedMinutes.value = Math.round((state.totalDuration || state.timeLeft) / 60)

  clearInterval(intervalId)
  intervalId = setInterval(() => {
    timeLeft.value--
    publishState()
    if (timeLeft.value <= 0) expireTimer()
  }, 1000)
}

watch(() => props.recoveryState, (state) => { if (state) recoverFromState(state) }, { immediate: true })

let fmtSub = null
onMounted(() => {
  if (!props.stompClient) return
  const doSubscribe = () => {
    if (fmtSub) return
    const fmtTopic = props.eventName
      ? `/topic/battle/${props.eventName}/format-timer`
      : '/topic/battle/format-timer'
    fmtSub = props.stompClient.subscribe(fmtTopic, (raw) => {
      try {
        const msg = JSON.parse(raw.body)
        if (msg) recoverFromState(msg)
      } catch (_) { /* ignore malformed WS messages */ }
    })
  }
  if (props.stompClient.connected) {
    doSubscribe()
  } else {
    const prev = props.stompClient.onConnect
    // eslint-disable-next-line vue/no-mutating-props
    props.stompClient.onConnect = () => { if (prev) prev(); doSubscribe() }
  }
})

onBeforeUnmount(() => {
  if (fmtSub) { fmtSub.unsubscribe(); fmtSub = null }
  clearInterval(intervalId)
  intervalId = null
})

if (props.recoveryState) recoverFromState(props.recoveryState)
</script>

<template>
  <div class="select-none">
    <div class="section-rule w-full mb-3">
      <span class="section-rule-label">FORMAT TIMER</span>
      <span class="section-rule-line"></span>
    </div>

    <!-- IDLE / EXPIRED: duration selector — shown in both states so no manual reset needed -->
    <div v-if="isIdle || isExpired" class="flex flex-col gap-2">
      <!-- Expired hint -->
      <div v-if="isExpired" class="flex items-center gap-2 mb-1">
        <span class="expired-dot"></span>
        <span class="type-label text-content-muted" style="font-size:10px;letter-spacing:0.14em">BATTLE HAS ENDED · SELECT DURATION FOR NEXT SESSION</span>
      </div>

      <div class="flex items-center gap-2 flex-wrap">
        <!-- Preset chips -->
        <button
          v-for="m in [30, 45]"
          :key="m"
          class="para-chip-sm px-3 py-2 type-body transition-all duration-150 active:scale-95"
          :class="selectedMinutes === m && !showCustomInput && !isCustomSelected
            ? 'preset-selected text-accent'
            : 'text-content-muted hover:text-content-primary hover:border-white/20'"
          @click="selectPreset(m)"
        >
          {{ m }}m
        </button>

        <!-- Custom chip: shows selected duration with × remove when confirmed, otherwise shows CUSTOM toggle -->
        <div
          v-if="isCustomSelected"
          class="para-chip-sm px-3 py-2 type-body preset-selected text-accent flex items-center gap-2"
        >
          <span>{{ selectedMinutes }}m</span>
          <button
            class="leading-none text-accent/60 hover:text-accent transition-colors active:scale-95"
            style="font-size:15px;line-height:1"
            @click="removeCustom"
            title="Remove custom duration"
          >×</button>
        </div>
        <button
          v-else
          class="para-chip-sm px-3 py-2 type-body transition-all duration-150 active:scale-95"
          :class="showCustomInput
            ? 'preset-selected text-accent'
            : 'text-content-muted hover:text-content-primary hover:border-white/20'"
          @click="toggleCustomInput"
        >
          CUSTOM
        </button>
      </div>

      <!-- Custom input — shown when CUSTOM chip is toggled -->
      <div v-if="showCustomInput" class="flex items-center gap-2 mt-1">
        <input
          v-model="customMinutes"
          type="number"
          min="1"
          max="180"
          placeholder="min"
          class="custom-input"
          @keydown.enter="confirmCustom"
          autofocus
        />
        <button
          class="para-chip-sm px-3 py-2 type-body transition-all duration-150 active:scale-95"
          :class="customMinutes ? 'preset-selected text-accent' : 'text-content-muted opacity-40 pointer-events-none'"
          @click="confirmCustom"
        >
          SET
        </button>
      </div>

      <!-- Hint -->
      <span class="type-label text-content-muted" style="font-size:10px;letter-spacing:0.14em">
        {{ selectedMinutes }}M SELECTED · STARTS WITH FIRST ROUND
      </span>
    </div>

    <!-- RUNNING: countdown + progress + reset -->
    <div v-else-if="isRunning" class="flex flex-col gap-2">
      <div class="flex items-center justify-between">
        <span
          class="format-countdown transition-colors duration-300"
          :class="isWarning ? 'text-amber-400' : 'text-content-primary'"
        >
          {{ displayTime }}
        </span>
        <button
          class="para-chip-sm px-3 py-1 type-label text-content-muted hover:text-content-primary"
          @click="resetTimer"
        >
          RESET
        </button>
      </div>
      <div class="progress-track w-full" :class="isWarning ? 'progress-track-warning' : ''">
        <div
          class="progress-fill h-full transition-all duration-1000 ease-linear"
          :class="isWarning ? 'bg-amber-400' : 'bg-[color:var(--accent-color)]'"
          :style="{ width: progressPct + '%' }"
        ></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.expired-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ef4444;
  box-shadow: 0 0 6px #ef4444;
  flex-shrink: 0;
}

.preset-selected {
  border-color: color-mix(in srgb, var(--accent-color) 55%, transparent);
}

.format-countdown {
  font-family: 'Oswald', sans-serif;
  font-size: 22px;
  letter-spacing: 0.02em;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.progress-track {
  height: 3px;
  background: rgba(255, 255, 255, 0.06);
  overflow: hidden;
}

.progress-track-warning {
  background: rgba(251, 191, 36, 0.12);
}

.progress-fill {
  border-radius: 0;
}

.custom-input {
  width: 64px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.12);
  color: var(--content-primary, #fff);
  font-family: 'Oswald', sans-serif;
  font-size: 13px;
  letter-spacing: 0.05em;
  padding: 6px 8px;
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
  outline: none;
  text-align: center;
}

.custom-input:focus {
  border-color: rgba(255, 255, 255, 0.3);
}

.custom-input::-webkit-outer-spin-button,
.custom-input::-webkit-inner-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
</style>
