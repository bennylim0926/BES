<script setup>
/**
 * LiveMatchPanel.vue
 *
 * Extracted from BattleControl.vue — the live match section shown to
 * Admin/Organiser (full control) and Emcee (isReadonly=true, write gated).
 *
 * Props are passed by the BattleControl orchestrator parent.
 * Every write action is emitted up for the parent to handle.
 */
import { computed, ref, watch } from 'vue'
import BattleTimer from '@/components/BattleTimer.vue'
import SmokeFormatTimer from '@/components/SmokeFormatTimer.vue'

const battleTimerRef      = ref(null)
const smokeFormatTimerRef = ref(null)

const props = defineProps({
  selectedEvent:             { type: String,  required: true },
  selectedGenre:             { type: String,  required: true },
  uniqueGenres:              { type: Array,   default: () => [] },
  battlePhase:               { type: String,  required: true },
  battleJudges:              { type: Array,   default: () => [] },
  currentBattle:             { type: Array,   default: () => [] },
  currentWinner:             { type: Number,  default: -2 },
  currentRound:              { type: Number,  default: 0 },
  currentTop:                { type: String,  default: '' },
  rounds:                    { type: [Object, Array], default: () => ({}) },
  topSize:                   { type: Number,  default: 16 },
  isSmoke:                   { type: Boolean, default: false },
  roundNames:                { type: Array,   default: () => [] },
  roundSizes:                { type: Array,   default: () => [] },
  saveStatus:                { type: String,  default: 'idle' },
  finalTieBlocked:           { type: Boolean, default: false },
  isReadonly:                { type: Boolean, default: false },
  isViewingNonActive:        { type: Boolean, default: false },
  liveGenreName:             { type: String,  default: '' },
  livePhase:                 { type: String,  default: 'IDLE' },
  genreChampions:            { type: Object,  default: () => ({}) },
  stompClient:               { type: Object,  default: null },
  overlayConfig:             { type: Object,  default: () => ({ leftColor: '#dc2626', rightColor: '#2563eb' }) },
  revealActive:              { type: Boolean, default: false },
  activeRoundIdx:            { type: Number,  default: 0 },
  recoveryTimer:             { type: Object,  default: null },
  recoveryFormatTimer:       { type: Object,  default: null },
  guestsForCurrentGenre:     { type: Array,   default: () => [] },
})

const emit = defineEmits([
  'request-genre-change',
  'open-voting',
  'get-score',
  'submit-revote',
  'lock-champion',
  'reveal-champion',
  'dismiss-reveal',
  'next-pair',
  'unlock-champion',
  'set-round',
  'start-round',
  'set-winner',
  'request-start-at',
  'request-start-all',
  'force-smoke-winner',
])

// ── Format timer winner picker ───────────────────────────────────
const showSmokeWinnerPicker  = ref(false)
const smokeWinnerCandidates  = ref([])   // [{ name, score }]
const autoSmokeWinner        = ref(null) // set when single clear top scorer

function handleGetScore() {
  battleTimerRef.value?.resetTimer()
  setTimeout(() => emit('get-score'), 400)
}

/**
 * Called by BattleControl after scoring completes with a fresh battler list from
 * the server. Only acts when the format timer is actually expired.
 */
function checkFormatWinnerIfExpired(battlers) {
  if (!smokeFormatTimerRef.value?.isExpired) return
  const filtered = (battlers ?? []).filter(b => b?.name)
  if (!filtered.length) return
  const maxScore = Math.max(...filtered.map(b => b.score ?? 0))
  const topBattlers = filtered.filter(b => (b.score ?? 0) === maxScore)
  if (topBattlers.length === 1) {
    emit('force-smoke-winner', topBattlers[0])
  } else {
    smokeWinnerCandidates.value = filtered
    autoSmokeWinner.value = null
    showSmokeWinnerPicker.value = true
  }
}

function isFormatTimerExpired() {
  return !!smokeFormatTimerRef.value?.isExpired
}

function confirmSmokeWinner(battler) {
  showSmokeWinnerPicker.value = false
  emit('force-smoke-winner', battler)
}

/** Called by BattleControl after the existing Start Round confirmation is accepted. */
function triggerFormatTimerStart() {
  smokeFormatTimerRef.value?.autoStartIfIdle()
}

// Stop the format timer when the smoke battle is decided (natural 7-smoke or format expiry)
watch(() => props.battlePhase, (phase) => {
  if (phase === 'DECIDED' && props.isSmoke) {
    smokeFormatTimerRef.value?.resetTimer()
  }
})

const formatTimerSelectedMinutes = computed(() => smokeFormatTimerRef.value?.selectedMinutes ?? 30)

defineExpose({ triggerFormatTimerStart, formatTimerSelectedMinutes, isFormatTimerExpired, checkFormatWinnerIfExpired })

// ── Genre status dot ─────────────────────────────────────────────
// Returns 'champion' | 'active' | 'idle'
function genreStatusDot(g) {
  if (props.genreChampions[g]) return 'champion'
  // In view-only mode selectedGenre is the viewed genre, not the live one.
  // Use liveGenreName + livePhase to correctly mark the live genre's dot.
  if (props.isViewingNonActive && props.liveGenreName) {
    if (g === props.liveGenreName && ['LOCKED', 'VOTING', 'REVEALED'].includes(props.livePhase)) return 'active'
    return 'idle'
  }
  const phase = g === props.selectedGenre ? props.battlePhase : 'IDLE'
  if (['LOCKED', 'VOTING', 'REVEALED'].includes(phase)) return 'active'
  return 'idle'
}

// ── Round tab status ──────────────────────────────────────────────
// Returns 'done' | 'active' | 'idle'
function roundTabStatus(roundKey) {
  if (!props.rounds || typeof props.rounds !== 'object' || Array.isArray(props.rounds)) return 'idle'
  const matchList = props.rounds[roundKey]
  if (!Array.isArray(matchList) || matchList.length === 0) return 'idle'
  if (props.currentTop === roundKey && props.battlePhase !== 'IDLE') return 'active'
  const filled = matchList.filter(m => m[0] || m[1])
  if (filled.length > 0 && filled.every(m => m[2])) return 'done'
  return 'idle'
}

// ── Judge vote display helpers ───────────────────────────────────
// Look up a judge's vote from currentBattle entries by judgeId.
function voteDisplay(judgeId) {
  const j = props.battleJudges.find(j => j.id === judgeId)
  if (!j || j.vote === undefined || j.vote === -3) return 'WAITING'
  if (j.vote === 0) return 'LEFT'
  if (j.vote === 1) return 'RIGHT'
  if (j.vote === -1) return 'TIE'
  return 'WAITING'
}

function _voteColor(judgeId) {
  const j = props.battleJudges.find(j => j.id === judgeId)
  if (j?.vote === 0) return props.overlayConfig.leftColor || '#dc2626'
  if (j?.vote === 1) return props.overlayConfig.rightColor || '#2563eb'
  return 'transparent'
}

// ── Battle pair computeds ────────────────────────────────────────
const currentBattlePair = computed(() => {
  if (props.currentBattle.length === 0) return null
  if (props.isSmoke) return [props.currentBattle[0], props.currentBattle[1]]
  const list = props.currentBattle[1]
  const idx = props.currentBattle[0]
  if (!Array.isArray(list) || list[idx] === undefined) return null
  return [list[idx][0], list[idx][1]]
})

const currentBattlePairNames = computed(() => {
  const pair = currentBattlePair.value
  if (!pair) return [null, null]
  const n = (p) => (p && typeof p === 'object') ? p.name : p
  return [n(pair[0]), n(pair[1])]
})

const currentBattleLeft = computed(() => currentBattlePairNames.value?.[0] ?? '')
const currentBattleRight = computed(() => currentBattlePairNames.value?.[1] ?? '')

const previousBattlePair = computed(() => {
  if (props.isSmoke || props.currentBattle.length === 0) return null
  const idx = props.currentBattle[0]
  if (idx <= 0) return null
  const list = props.currentBattle[1]
  if (!Array.isArray(list) || list[idx - 1] === undefined) return null
  return [list[idx - 1][0], list[idx - 1][1]]
})

const nextBattlePair = computed(() => {
  if (props.currentBattle.length === 0) return null
  if (props.isSmoke) {
    const rest = props.currentBattle[2]
    if (Array.isArray(rest) && rest.length > 0) {
      return rest.map(p => typeof p === 'object' ? p.name : p)
    }
    return null
  }
  const idx = props.currentBattle[0]
  const list = props.currentBattle[1]
  if (!Array.isArray(list) || idx >= list.length - 1) return null
  return [list[idx + 1][0], list[idx + 1][1]]
})

// ── Winner announcement display ──────────────────────────────────
const winnerAnnouncement = computed(() => {
  if (props.currentBattle.length === 0) return 'Select a match from the bracket to begin'
  if (props.currentWinner === -2) return 'Battle in progress'
  if (props.currentWinner === -3) return "Judges haven't voted yet"
  if (props.currentWinner === -1) return "It's a tie"
  if (props.currentWinner === 0 || props.currentWinner === 1) {
    const name = currentBattlePairNames.value?.[props.currentWinner]
    return name ? `${name} takes it` : (props.currentWinner === 0 ? 'LEFT takes it' : 'RIGHT takes it')
  }
  return ''
})

const winnerVariant = computed(() => {
  if (props.currentWinner === -2) return 'ongoing'
  if (props.currentWinner === -3) return 'wait'
  if (props.currentWinner === -1) return 'tie'
  return 'winner'
})

// ── Phase helpers ────────────────────────────────────────────────
const isFinalInProgress = computed(() =>
  !props.isSmoke && props.currentTop === 'Top2'
)

// ── Judge vote computeds ─────────────────────────────────────────
const allJudgesVoted = computed(() => {
  const v = props.currentBattle.filter(b => b?.judgeId !== undefined)
  if (v.length === 0) {
    // Fallback: check battleJudges directly if no judge entries in currentBattle
    const judges = Array.isArray(props.battleJudges) ? props.battleJudges : []
    return judges.length > 0 && judges.every(j => j.vote !== -3 && j.vote !== undefined)
  }
  return v.length > 0 && v.every(j => j.vote !== -3)
})

const tentativeWinner = computed(() => {
  const judges = props.currentBattle.filter(b => b?.judgeId !== undefined)
  if (judges.length === 0) {
    // Fallback: use battleJudges vote data
    const jl = Array.isArray(props.battleJudges) ? props.battleJudges : []
    if (jl.some(j => j.vote === -3 || j.vote === undefined)) return -2
    const leftW = jl.filter(j => j.vote === 0).reduce((s, j) => s + (j.weightage ?? 1), 0)
    const rightW = jl.filter(j => j.vote === 1).reduce((s, j) => s + (j.weightage ?? 1), 0)
    if (leftW === rightW) return -1
    return leftW > rightW ? 0 : 1
  }
  if (judges.some(j => j.vote === -3)) return -2
  const leftW = judges.filter(j => j.vote === 0).reduce((s, j) => s + (j.weightage ?? 1), 0)
  const rightW = judges.filter(j => j.vote === 1).reduce((s, j) => s + (j.weightage ?? 1), 0)
  if (leftW === rightW) return -1
  return leftW > rightW ? 0 : 1
})

const voteWeightDisplay = computed(() => {
  const judges = props.currentBattle.filter(b => b?.judgeId !== undefined)
  if (judges.length === 0) {
    const jl = Array.isArray(props.battleJudges) ? props.battleJudges : []
    return {
      left: jl.filter(j => j.vote === 0).reduce((s, j) => s + (j.weightage ?? 1), 0),
      right: jl.filter(j => j.vote === 1).reduce((s, j) => s + (j.weightage ?? 1), 0),
    }
  }
  return {
    left: judges.filter(j => j.vote === 0).reduce((s, j) => s + (j.weightage ?? 1), 0),
    right: judges.filter(j => j.vote === 1).reduce((s, j) => s + (j.weightage ?? 1), 0),
  }
})

const showFinalReveal = computed(() =>
  props.battlePhase === 'VOTING' &&
  isFinalInProgress.value &&
  allJudgesVoted.value &&
  tentativeWinner.value !== -1
)

const currentGenreChampion = computed(() => {
  if (props.isSmoke) return null
  const r = props.rounds
  if (r && typeof r === 'object' && !Array.isArray(r)) {
    return r.Top2?.[0]?.[2] ?? null
  }
  return null
})

const viewedRoundKey = computed(() => props.roundNames[props.activeRoundIdx] ?? '')
const viewedPairList = computed(() => {
  const key = viewedRoundKey.value
  if (!key || !props.rounds) return []
  return (props.rounds)[key] ?? []
})

</script>

<template>
  <div class="live-match-panel space-y-3">
    <!-- Role-aware guidance (Emcee) -->
    <div
      v-if="isReadonly"
      class="role-hint-banner"
    >
      <div class="w-2 h-2 rounded-full flex-shrink-0" style="background:var(--accent-color);box-shadow:0 0 8px var(--accent-muted)"></div>
      <span class="type-body flex-1">You control the battle flow. Start the timer when battlers are on stage.</span>
    </div>

    <!-- Genre switcher -->
    <div class="card px-4 sm:px-5 py-3 flex flex-wrap items-center gap-2 mb-3">
      <span class="type-label text-content-muted" style="font-size:10px;letter-spacing:0.18em">GENRE</span>
      <span
        v-if="isViewingNonActive"
        class="type-label px-2 py-0.5"
        style="font-size:9px;letter-spacing:0.22em;color:rgba(239,68,68,0.85);border:1px solid rgba(239,68,68,0.3);background:rgba(239,68,68,0.07);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
      >VIEW ONLY</span>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="g in uniqueGenres"
          :key="g"
          @click="$emit('request-genre-change', g)"
          class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1.5 type-label transition-all duration-150 inline-flex items-center gap-1.5"
          :class="[
            selectedGenre === g
              ? 'text-accent border-[color:var(--accent-muted)]'
              : 'text-content-muted hover:text-content-primary'
          ]"
        >
          <template v-if="genreStatusDot(g) === 'champion'">
            <i class="pi pi-star-fill text-[9px] text-amber-400"></i>
          </template>
          <template v-else-if="genreStatusDot(g) === 'active'">
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 animate-pulse" style="box-shadow:0 0 6px rgba(245,158,11,0.7)"></span>
          </template>
          <template v-else>
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-surface-400/40"></span>
          </template>
          {{ g }}
        </button>
      </div>
    </div>

    <!-- Live match card -->
    <div class="card p-5">
      <div class="section-rule mb-4">
        <span class="section-rule-label">Live Match</span>
        <div class="section-rule-line"></div>
      </div>

      <!-- Phase badge + Save status -->
      <div class="flex items-center gap-3 mb-4">
        <div
          class="inline-flex items-center gap-2 px-3 py-1.5"
          :class="{
            'semantic-chip-success': battlePhase === 'REVEALED',
            'semantic-chip-warning': battlePhase === 'LOCKED',
            'semantic-chip-warning animate-pulse': battlePhase === 'VOTING',
            'semantic-chip-warning opacity-50': battlePhase === 'IDLE',
          }"
        >
          <div
            class="w-2 h-2 rounded-full"
            :style="battlePhase === 'REVEALED'
              ? 'background:#34d399;box-shadow:0 0 8px rgba(52,211,153,0.8)'
              : battlePhase === 'LOCKED'
                ? 'background:#f59e0b;box-shadow:0 0 8px rgba(245,158,11,0.8)'
                : battlePhase === 'VOTING'
                  ? 'background:#f59e0b;box-shadow:0 0 8px rgba(245,158,11,0.8)'
                  : 'background:#6b7280;box-shadow:0 0 8px rgba(107,114,128,0.5)'"
          ></div>
          <span
            class="type-body"
            :class="battlePhase === 'REVEALED'
              ? 'text-emerald-400'
              : battlePhase === 'LOCKED'
                ? 'text-amber-400'
                : battlePhase === 'VOTING'
                  ? 'text-amber-400'
                  : 'text-gray-400'"
          >{{
            battlePhase === 'IDLE'     ? 'Standby'      :
            battlePhase === 'LOCKED'   ? 'Battling'     :
            battlePhase === 'VOTING'   ? 'Judging'      :
            battlePhase === 'REVEALED' ? 'Result Shown'  :
            battlePhase === 'DECIDED'  ? 'Champion Set'  : battlePhase
          }}</span>
        </div>

        <!-- Save state indicator -->
        <Transition name="recovery-fade" mode="out-in">
          <span
            v-if="saveStatus === 'saving'"
            key="saving"
            class="inline-flex items-center gap-1.5 px-2.5 py-1 type-label text-content-muted"
            style="font-size:10px;letter-spacing:0.16em;background:rgba(255,255,255,0.04);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
          >
            <i class="pi pi-spin pi-spinner" style="font-size:9px"></i> SAVING
          </span>
          <span
            v-else-if="saveStatus === 'saved'"
            key="saved"
            class="inline-flex items-center gap-1.5 px-2.5 py-1 type-label text-emerald-400"
            style="font-size:10px;letter-spacing:0.16em;background:rgba(52,211,153,0.08);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
          >
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400" style="box-shadow:0 0 6px rgba(52,211,153,0.7)"></span> SAVED
          </span>
          <span
            v-else-if="saveStatus === 'error'"
            key="error"
            class="inline-flex items-center gap-1.5 px-2.5 py-1 type-label text-amber-400"
            style="font-size:10px;letter-spacing:0.16em;background:rgba(245,158,11,0.08);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
          >
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400" style="box-shadow:0 0 6px rgba(245,158,11,0.7)"></span> SAVE FAILED
          </span>
        </Transition>
      </div>

      <!-- Final tie warning -->
      <div
        v-if="finalTieBlocked"
        class="semantic-chip-warning px-4 py-3 flex items-center justify-between gap-3 mb-4"
      >
        <div class="w-2 h-2 rounded-full" style="background:#f59e0b;box-shadow:0 0 8px rgba(245,158,11,0.8)"></div>
        <span class="type-body flex-1 text-amber-400"><i class="pi pi-exclamation-triangle mr-2"></i>TIE in Final — Revote required</span>
        <button
          v-if="!isReadonly && !isViewingNonActive"
          @click="$emit('submit-revote')"
          class="para-chip-sm px-3 py-1.5 type-label text-accent transition-all"
        >REVOTE</button>
      </div>

      <!-- Winner announcement / champion locked -->
      <div
        v-if="battlePhase === 'DECIDED'"
        class="px-4 py-3 mb-4"
        style="border-left:4px solid #34d399;background:rgba(52,211,153,0.08)"
      >
        <span class="type-label text-emerald-400" style="font-size:9px;letter-spacing:0.22em">⭐ CHAMPION LOCKED</span>
        <span class="type-body text-emerald-400 block mt-1" style="font-size:18px;font-weight:bold;text-shadow:0 0 12px rgba(52,211,153,0.4)">
          {{ genreChampions[selectedGenre] ?? currentGenreChampion ?? '—' }}
        </span>
        <span
          v-if="!revealActive"
          class="type-label text-content-muted block mt-1"
          style="font-size:9px;letter-spacing:0.22em"
        >
          {{ isReadonly ? 'CHAMPION LOCKED — ORGANISER WILL ANNOUNCE SHORTLY' : 'CHAMPION LOCKED — CLICK REVEAL CHAMPION BELOW' }}
        </span>
      </div>
      <div
        v-else
        class="px-4 py-3 mb-4"
        :class="{
          'semantic-chip-warning': winnerVariant === 'ongoing' || winnerVariant === 'wait',
          'semantic-chip-success': winnerVariant === 'winner',
          'border-l-3 border-gray-400 bg-gray-500/10': winnerVariant === 'tie',
        }"
      >
        <div
          class="w-2 h-2 rounded-full mb-1"
          :class="winnerVariant === 'winner' ? 'bg-emerald-400' : winnerVariant === 'tie' ? 'bg-gray-400' : 'bg-amber-400'"
          :style="winnerVariant === 'winner'
            ? 'box-shadow:0 0 8px rgba(52,211,153,0.8)'
            : winnerVariant === 'tie'
              ? ''
              : 'box-shadow:0 0 8px rgba(245,158,11,0.8)'"
        ></div>
        <span class="type-body text-content-primary">{{ winnerAnnouncement }}</span>
      </div>

      <!-- BattleTimer (LOCKED: timer counts down independently; Emcee opens voting manually) -->
      <div v-if="(!isReadonly || battlePhase === 'LOCKED' || battlePhase === 'VOTING') && !isViewingNonActive" class="mb-4">
        <BattleTimer
          ref="battleTimerRef"
          :phase="battlePhase"
          :stomp-client="stompClient"
          :recovery-state="recoveryTimer"
          :event-name="selectedEvent"
        />
      </div>

      <!-- 7-to-Smoke format timer (session-level countdown) -->
      <div v-if="isSmoke && !isViewingNonActive" class="mb-4">
        <SmokeFormatTimer
          ref="smokeFormatTimerRef"
          :stomp-client="stompClient"
          :recovery-state="recoveryFormatTimer"
          :event-name="selectedEvent"
        />
      </div>

      <!-- 7-to-Smoke queue display — shows all 8 slots, visible to both roles -->
      <div v-if="isSmoke && Array.isArray(rounds) && rounds.length > 0" class="card p-4 mb-4">
        <div class="section-rule mb-3">
          <span class="section-rule-label">QUEUE</span>
          <!-- Start Round — organiser: direct start; Emcee: same emit path -->
          <button
            v-if="battlePhase === 'IDLE' && rounds.some(r => r?.name) && !isViewingNonActive"
            @click="$emit('start-round')"
            class="ml-2 para-chip-sm px-2.5 py-1 type-label text-accent border-[color:var(--accent-muted)] hover:bg-[color:var(--accent-subtle)] transition-all inline-flex items-center gap-1"
            style="font-size:10px"
          >
            <i class="pi pi-play" style="font-size:8px"></i>
            START ROUND
          </button>
          <div class="section-rule-line"></div>
        </div>
        <div class="flex flex-col gap-1">
          <div
            v-for="(slot, idx) in rounds"
            :key="idx"
            class="compact-match-card flex items-center gap-2 py-1.5 px-2"
            :class="{
              'bg-[color:var(--accent-subtle)] border-l-[3px] border-l-[color:var(--accent-muted)]':
                battlePhase !== 'IDLE' && (idx === 0 || idx === 1)
            }"
          >
            <span class="type-label text-content-muted text-[10px] w-5 flex-shrink-0">{{ idx + 1 }}</span>
            <span
              class="type-body flex-1 truncate min-w-0"
              :class="slot.name ? 'text-content-primary' : 'text-content-muted/40 italic'"
            >{{ slot.name || '—' }}</span>
            <span
              v-if="slot.name && slot.score > 0"
              class="type-label text-accent flex-shrink-0"
              style="font-size:10px;letter-spacing:0.06em"
            >{{ slot.score }}</span>
            <span
              v-if="battlePhase !== 'IDLE' && idx < 2 && slot.name"
              class="type-label text-accent"
              style="font-size:8px;letter-spacing:0.14em"
            >ACTIVE</span>
          </div>
        </div>
      </div>

      <!-- Round tabs + bracket viewer — compact combined section -->
      <div v-if="!isSmoke && roundNames.length > 0" class="card p-4">
        <div class="section-rule mb-3">
          <span class="section-rule-label">BRACKET</span>
          <!-- Start All button — IDLE phase, current round has filled matches -->
          <button
            v-if="battlePhase === 'IDLE' && viewedPairList.some(m => m[0] && m[1]) && !isViewingNonActive"
            @click="$emit('request-start-all', viewedRoundKey, viewedPairList)"
            class="ml-2 para-chip-sm px-2.5 py-1 type-label text-accent border-[color:var(--accent-muted)] hover:bg-[color:var(--accent-subtle)] transition-all inline-flex items-center gap-1"
            style="font-size:10px"
          >
            <i class="pi pi-play" style="font-size:8px"></i>
            START {{ viewedRoundKey.replace('Top', 'TOP ') }}
          </button>
          <div class="section-rule-line"></div>
        </div>

        <!-- Round tabs as compact filter chips -->
        <div class="flex flex-wrap gap-1.5 mb-3">
          <button
            v-for="(name, idx) in roundNames"
            :key="idx"
            @click="$emit('set-round', idx)"
            class="para-chip-sm px-2.5 py-1 type-label transition-all duration-150 text-[10px] inline-flex items-center gap-1"
            :class="activeRoundIdx === idx
              ? 'text-accent border-[color:var(--accent-muted)]'
              : 'text-content-muted hover:text-content-primary'"
          >
            <i
              v-if="roundTabStatus(name) === 'done'"
              class="pi pi-check"
              style="font-size:8px;color:#34d399"
            ></i>
            <span
              v-else-if="roundTabStatus(name) === 'active'"
              class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 flex-shrink-0"
              style="box-shadow:0 0 6px rgba(245,158,11,0.7);animation:pulse 1s ease-in-out infinite"
            ></span>
            {{ name.replace('Top', 'TOP ') }}
          </button>
        </div>

        <!-- Bracket viewer — shows only selected round -->
        <div v-if="rounds && Object.keys(rounds).length > 0" class="compact-bracket">
          <div
            v-for="(match, mIdx) in viewedPairList"
            :key="mIdx"
            class="compact-match-card flex items-center gap-1.5 py-1.5 px-2"
            :class="{
              'bg-[color:var(--accent-subtle)] border-l-[3px] border-l-[color:var(--accent-muted)]':
                (match[0] && (match[0] === currentBattleLeft || match[0] === currentBattleRight)) ||
                (match[1] && (match[1] === currentBattleLeft || match[1] === currentBattleRight))
            }"
          >
            <span class="type-label text-content-muted text-[10px] w-5 flex-shrink-0">{{ mIdx + 1 }}</span>

            <!-- Left name -->
            <span class="type-name-sm flex-1 truncate min-w-0" :class="match[2] === match[0] && match[0] ? 'text-green-400' : match[0] ? 'text-content-primary' : 'text-content-muted/40'">
              {{ match[0] || '---' }}
              <span v-if="match[2] === match[0] && match[0]" class="type-label text-green-400 ml-1" style="font-size:8px">WIN</span>
            </span>

            <!-- Left WIN button (organiser only) -->
            <button
              v-if="!isReadonly && !isViewingNonActive && match[0]"
              @click="$emit('set-winner', viewedRoundKey, mIdx, 0)"
              class="flex-shrink-0 px-1.5 py-1 transition-colors"
              :class="match[2] === match[0] ? 'text-amber-400' : 'text-surface-600 hover:text-amber-400 hover:bg-amber-500/10'"
              title="Set as winner"
            ><i class="pi pi-crown" style="font-size:9px"></i></button>

            <span class="type-label text-content-muted text-[9px] mx-0.5 flex-shrink-0">VS</span>

            <!-- Right WIN button (organiser only) -->
            <button
              v-if="!isReadonly && !isViewingNonActive && match[1]"
              @click="$emit('set-winner', viewedRoundKey, mIdx, 1)"
              class="flex-shrink-0 px-1.5 py-1 transition-colors"
              :class="match[2] === match[1] ? 'text-amber-400' : 'text-surface-600 hover:text-amber-400 hover:bg-amber-500/10'"
              title="Set as winner"
            ><i class="pi pi-crown" style="font-size:9px"></i></button>

            <!-- Right name -->
            <span class="type-name-sm flex-1 truncate text-right min-w-0" :class="match[2] === match[1] && match[1] ? 'text-green-400' : match[1] ? 'text-content-primary' : 'text-content-muted/40'">
              {{ match[1] || '---' }}
              <span v-if="match[2] === match[1] && match[1]" class="type-label text-green-400 ml-1" style="font-size:8px">WIN</span>
            </span>

            <!-- Start from here (IDLE, both slots filled) -->
            <button
              v-if="battlePhase === 'IDLE' && match[0] && match[1] && !isViewingNonActive"
              @click="$emit('request-start-at', viewedRoundKey, viewedPairList, mIdx)"
              class="flex-shrink-0 ml-1 px-1.5 py-1 text-surface-600 hover:text-accent hover:bg-[color:var(--accent-subtle)] transition-colors"
              title="Start from this match"
            ><i class="pi pi-play" style="font-size:8px"></i></button>
          </div>
        </div>
        <div v-else class="type-label text-content-muted text-[10px] text-center py-3">
          No bracket data
        </div>
      </div>

      <!-- Judge vote grid -->
      <div v-if="battleJudges.length > 0" class="mb-4">
        <div class="section-rule mb-3">
          <span class="section-rule-label">JUDGES</span>
          <div class="section-rule-line"></div>
        </div>
        <div
          class="grid gap-2 mb-3"
          :style="{ gridTemplateColumns: `repeat(${battleJudges.length}, 1fr)` }"
        >
          <div
            v-for="judge in battleJudges"
            :key="judge.id"
            class="px-3 py-3 text-center"
            :style="{
              clipPath: 'polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)',
              border: voteDisplay(judge.id) === 'WAITING'
                ? '1px solid rgba(245,158,11,0.3)'
                : voteDisplay(judge.id) === 'LEFT'
                  ? `1px solid ${overlayConfig.leftColor}99`
                  : voteDisplay(judge.id) === 'RIGHT'
                    ? `1px solid ${overlayConfig.rightColor}99`
                    : '1px solid rgba(255,255,255,0.07)',
              background: voteDisplay(judge.id) === 'WAITING'
                ? 'rgba(245,158,11,0.06)'
                : voteDisplay(judge.id) === 'LEFT'
                  ? `${overlayConfig.leftColor}18`
                  : voteDisplay(judge.id) === 'RIGHT'
                    ? `${overlayConfig.rightColor}18`
                    : 'rgba(255,255,255,0.04)',
            }"
          >
            <div style="font-size:10px;letter-spacing:0.18em;color:rgba(255,255,255,0.55);margin-bottom:2px">{{ judge.name }}</div>
            <div style="font-size:11px;color:#93c5fd;letter-spacing:0.12em;margin-bottom:4px;font-weight:700">WT {{ judge.weightage ?? 1 }}</div>
            <div
              v-if="voteDisplay(judge.id) === 'WAITING'"
              class="type-body text-amber-400"
              style="font-size:13px"
            >⏳ WAITING</div>
            <div
              v-else-if="voteDisplay(judge.id) === 'LEFT'"
              class="type-body"
              :style="{ fontSize: '13px', color: overlayConfig.leftColor }"
            >{{ currentBattlePairNames?.[0] ?? 'LEFT' }}</div>
            <div
              v-else-if="voteDisplay(judge.id) === 'RIGHT'"
              class="type-body"
              :style="{ fontSize: '13px', color: overlayConfig.rightColor }"
            >{{ currentBattlePairNames?.[1] ?? 'RIGHT' }}</div>
          </div>
        </div>

        <!-- Winner preview banner (all judges voted) -->
        <div
          v-if="allJudgesVoted && tentativeWinner !== -1 && tentativeWinner !== -2"
          class="px-4 py-3"
          :style="{
            borderLeft: `4px solid ${tentativeWinner === 0 ? overlayConfig.leftColor : overlayConfig.rightColor}`,
            background: `${tentativeWinner === 0 ? overlayConfig.leftColor : overlayConfig.rightColor}18`,
          }"
        >
          <div
            class="type-label mb-2"
            style="font-size:9px;letter-spacing:0.18em"
            :style="{ color: tentativeWinner === 0 ? overlayConfig.leftColor : overlayConfig.rightColor }"
          >WINNER PREVIEW {{ isReadonly ? '' : '(ORGANISER ONLY)' }}</div>
          <div
            class="type-body"
            style="font-size:20px;letter-spacing:0.08em;font-weight:bold"
            :style="{ color: tentativeWinner === 0 ? overlayConfig.leftColor : overlayConfig.rightColor }"
          >
            {{ tentativeWinner === 0
              ? (currentBattlePairNames?.[0] ?? 'LEFT')
              : (currentBattlePairNames?.[1] ?? 'RIGHT') }}
          </div>
          <div class="type-label text-content-muted mt-1" style="font-size:13px;letter-spacing:0.06em">
            {{ voteWeightDisplay.left }} – {{ voteWeightDisplay.right }}
          </div>
        </div>
        <div
          v-else-if="allJudgesVoted && tentativeWinner === -1"
          class="px-4 py-3"
          style="border-left:3px solid #6b7280;background:rgba(107,114,128,0.08)"
        >
          <div class="type-label mb-2" style="font-size:9px;letter-spacing:0.18em;color:#9ca3af">WINNER PREVIEW</div>
          <div class="type-body" style="font-size:20px;letter-spacing:0.06em;font-weight:bold;color:#9ca3af">
            TIE — {{ voteWeightDisplay.left }} – {{ voteWeightDisplay.right }}
          </div>
          <div class="type-label text-content-muted mt-1" style="font-size:13px;letter-spacing:0.06em">Revote required</div>
        </div>
      </div>
      <div
        v-else-if="battlePhase !== 'IDLE' && !isReadonly"
        class="mb-4 px-3 py-2"
        style="clip-path:polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%);border:1px solid rgba(255,255,255,0.07);background:rgba(255,255,255,0.04)"
      >
        <span class="type-label text-content-muted">No judges assigned for this battle</span>
      </div>

      <!-- Match cards (standard) -->
      <div v-if="!isSmoke && currentBattle.length > 0" class="grid grid-cols-3 gap-3 mb-4">
        <div class="stat-card relative">
          <div class="corner-bar-tl"></div>
          <span class="type-label text-content-muted mb-1">Previous</span>
          <template v-if="previousBattlePair">
            <span class="type-body text-content-secondary block">{{ previousBattlePair[0] }}</span>
            <span class="type-label text-content-muted">vs</span>
            <span class="type-body text-content-secondary block">{{ previousBattlePair[1] }}</span>
          </template>
          <span v-else class="type-stat text-content-disabled opacity-30">—</span>
        </div>
        <div
          class="stat-card relative"
          style="box-shadow: 0 0 0 1px var(--accent-muted), 0 8px 40px var(--accent-subtle);"
        >
          <div class="corner-bar-tl"></div>
          <span class="type-label text-accent mb-1">Current</span>
          <template v-if="currentBattlePair">
            <span class="type-body text-content-primary block">{{ currentBattlePair[0] }}</span>
            <span class="type-label text-content-muted my-0.5 block">vs</span>
            <span class="type-body text-content-primary block">{{ currentBattlePair[1] }}</span>
          </template>
          <span v-else class="type-stat text-content-disabled opacity-30">—</span>
        </div>
        <div class="stat-card relative">
          <div class="corner-bar-tl"></div>
          <span class="type-label text-content-muted mb-1">Next</span>
          <template v-if="nextBattlePair">
            <span class="type-body text-content-secondary block">{{ nextBattlePair[0] }}</span>
            <span class="type-label text-content-muted">vs</span>
            <span class="type-body text-content-secondary block">{{ nextBattlePair[1] }}</span>
          </template>
          <span v-else class="type-stat text-content-disabled opacity-30">—</span>
        </div>
      </div>

      <!-- Match cards (smoke) -->
      <div v-if="isSmoke && currentBattle.length > 0" class="grid grid-cols-2 gap-3 mb-4">
        <div
          class="stat-card relative"
          style="box-shadow: 0 0 0 1px var(--accent-muted), 0 8px 40px var(--accent-subtle);"
        >
          <div class="corner-bar-tl"></div>
          <span class="type-label text-accent mb-1">Current Match</span>
          <template v-if="currentBattlePair">
            <span class="type-name text-content-primary block">{{ currentBattlePair[0]?.name ?? currentBattlePair[0] }} <span class="type-label text-content-muted">({{ currentBattlePair[0]?.score ?? '?' }})</span></span>
            <span class="type-label text-content-muted my-0.5 block">vs</span>
            <span class="type-name text-content-primary block">{{ currentBattlePair[1]?.name ?? currentBattlePair[1] }} <span class="type-label text-content-muted">({{ currentBattlePair[1]?.score ?? '?' }})</span></span>
          </template>
        </div>
        <div class="stat-card relative">
          <div class="corner-bar-tl"></div>
          <span class="type-label text-content-muted mb-1">Queue</span>
          <span v-if="nextBattlePair" class="type-name text-content-secondary block">{{ Array.isArray(nextBattlePair) ? nextBattlePair.join(', ') : nextBattlePair }}</span>
          <span v-else class="type-stat text-content-disabled opacity-30">—</span>
        </div>
      </div>

      <!-- Battle guests — read-only list for all roles -->
      <div v-if="guestsForCurrentGenre.length > 0" class="mb-4">
        <div class="section-rule mb-2">
          <span class="section-rule-label">BATTLE GUESTS</span>
          <div class="section-rule-line"></div>
        </div>
        <div class="flex flex-wrap gap-1.5">
          <span
            v-for="g in guestsForCurrentGenre"
            :key="g.id"
            class="inline-flex items-center gap-1.5 px-2.5 py-1"
            style="clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%);border:1px solid rgba(255,255,255,0.1);background:rgba(255,255,255,0.04)"
          >
            <i class="pi pi-star text-accent" style="font-size:0.55rem"></i>
            <span class="type-name-sm text-content-primary">{{ g.guestName }}</span>
            <span v-if="!isSmoke" class="type-label text-content-muted" style="font-size:10px">→ {{ g.entryRound }}</span>
          </span>
        </div>
      </div>

      <!-- Phase action buttons -->
      <div v-if="currentBattle.length > 0 && !isReadonly && !isViewingNonActive" class="flex flex-wrap gap-3 sm:gap-2">
        <!-- LOCKED: open voting -->
        <button
          v-if="battlePhase === 'LOCKED'"
          @click="$emit('open-voting')"
          class="bg-accent para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center"
        >
          <i class="pi pi-lock-open text-xs"></i>
          Start Judging
        </button>

        <!-- VOTING: non-final, not all voted, or tie → Get Score / Rematch -->
        <button
          v-if="battlePhase === 'VOTING' && !showFinalReveal"
          :disabled="!allJudgesVoted"
          @click="handleGetScore"
          :class="allJudgesVoted
            ? 'bg-accent para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center'
            : 'bg-surface-700/30 para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all cursor-not-allowed opacity-50 flex-1 sm:flex-none justify-center'"
          :title="allJudgesVoted ? '' : 'All judges must vote first'"
        >
          <i class="pi pi-bolt text-xs"></i>
          {{ (Number(currentWinner) === -1 && !isSmoke) ? 'Rematch' : 'Reveal Result' }}
        </button>

        <!-- VOTING + final + all judges voted → Lock Champion -->
        <button
          v-if="battlePhase === 'VOTING' && isFinalInProgress && allJudgesVoted"
          :disabled="!showFinalReveal"
          @click="$emit('lock-champion')"
          class="para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center"
          :class="showFinalReveal
            ? 'border-amber-400/50 text-amber-400 bg-amber-400/10 hover:bg-amber-400/20'
            : 'border-gray-500/30 text-content-muted bg-surface-700/30 cursor-not-allowed'"
          :title="showFinalReveal ? 'Lock the champion' : 'Cannot lock — result is a tie'"
        >
          <i class="pi pi-lock text-xs"></i>
          Confirm Champion
        </button>

        <!-- DECIDED: champion locked, ready to reveal or unlock for revote -->
        <!-- 7-to-Smoke auto-declares the champion — no manual reveal/unlock needed -->
        <template v-if="battlePhase === 'DECIDED' && !isSmoke">
          <button
            v-if="!revealActive"
            @click="$emit('reveal-champion')"
            class="para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 border-accent transition-all flex-1 sm:flex-none justify-center"
          >
            <i class="pi pi-star text-xs"></i>
            Reveal Champion
          </button>
          <button
            v-if="revealActive"
            @click="$emit('dismiss-reveal')"
            class="para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center"
          >
            <i class="pi pi-times text-xs"></i>
            Hide Overlay
          </button>
          <button
            @click="$emit('unlock-champion')"
            class="para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 text-content-muted hover:text-content-primary transition-all flex-1 sm:flex-none justify-center"
          >
            <i class="pi pi-unlock text-xs"></i>
            Reset Final
          </button>
        </template>

        <!-- REVEALED: advance to next match or end bracket -->
        <template v-if="battlePhase === 'REVEALED'">
          <button
            @click="$emit('next-pair')"
            class="bg-accent para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center"
          >
            {{ isFinalInProgress ? 'End Battle' : 'Next Match' }}
            <i class="pi pi-chevron-right text-xs"></i>
          </button>
        </template>

        <!-- Champion re-reveal (not in DECIDED, has a champion but not yet revealed). Smoke auto-reveals; no manual button. -->
        <button
          v-if="!isSmoke && battlePhase !== 'DECIDED' && (currentGenreChampion || genreChampions[selectedGenre]) && !revealActive"
          @click="$emit('reveal-champion')"
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 border-accent transition-all"
        >
          <i class="pi pi-star text-xs"></i>
          Reveal Champion
        </button>
        <button
          v-if="!isSmoke && revealActive && battlePhase !== 'DECIDED'"
          @click="$emit('dismiss-reveal')"
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
        >
          <i class="pi pi-times text-xs"></i>
          Hide Overlay
        </button>
      </div>

      <!-- Phase action buttons (Emcee readonly: controls for running the live battle) -->
      <div v-if="isReadonly && currentBattle.length > 0" class="flex flex-wrap gap-3 sm:gap-2">
        <!-- LOCKED: open voting (manual, in addition to auto-unlock at 10s) -->
        <button
          v-if="battlePhase === 'LOCKED'"
          @click="$emit('open-voting')"
          class="bg-accent para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center"
        >
          <i class="pi pi-lock-open text-xs"></i>
          Start Judging
        </button>

        <!-- VOTING: Get Score / Rematch -->
        <button
          v-if="battlePhase === 'VOTING' && !showFinalReveal"
          :disabled="!allJudgesVoted"
          @click="handleGetScore"
          :class="allJudgesVoted
            ? 'bg-accent para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center'
            : 'bg-surface-700/30 para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all cursor-not-allowed opacity-50 flex-1 sm:flex-none justify-center'"
          :title="allJudgesVoted ? '' : 'All judges must vote first'"
        >
          <i class="pi pi-bolt text-xs"></i>
          {{ (Number(currentWinner) === -1 && !isSmoke) ? 'Rematch' : 'Reveal Result' }}
        </button>

        <!-- VOTING + final + all judges voted → Lock Champion (regular battle only) -->
        <button
          v-if="battlePhase === 'VOTING' && isFinalInProgress && allJudgesVoted"
          :disabled="!showFinalReveal"
          @click="$emit('lock-champion')"
          class="para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center"
          :class="showFinalReveal
            ? 'border-amber-400/50 text-amber-400 bg-amber-400/10 hover:bg-amber-400/20'
            : 'border-gray-500/30 text-content-muted bg-surface-700/30 cursor-not-allowed'"
          :title="showFinalReveal ? 'Lock the champion' : 'Cannot lock — result is a tie'"
        >
          <i class="pi pi-lock text-xs"></i>
          Confirm Champion
        </button>

        <!-- DECIDED: reveal champion (regular battle only — smoke auto-reveals) -->
        <button
          v-if="!isSmoke && battlePhase === 'DECIDED' && !revealActive"
          @click="$emit('reveal-champion')"
          class="para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 border-accent transition-all flex-1 sm:flex-none justify-center"
        >
          <i class="pi pi-star text-xs"></i>
          Reveal Champion
        </button>
        <button
          v-if="!isSmoke && revealActive"
          @click="$emit('dismiss-reveal')"
          class="para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center"
        >
          <i class="pi pi-times text-xs"></i>
          Hide Overlay
        </button>
        <button
          v-if="!isSmoke && battlePhase === 'DECIDED'"
          @click="$emit('unlock-champion')"
          class="para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 text-content-muted hover:text-content-primary transition-all flex-1 sm:flex-none justify-center"
        >
          <i class="pi pi-unlock text-xs"></i>
          Unlock
        </button>

        <!-- REVEALED: advance to next match or end bracket -->
        <button
          v-if="battlePhase === 'REVEALED'"
          @click="$emit('next-pair')"
          class="bg-accent para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center"
        >
          {{ isFinalInProgress ? 'End Battle' : 'Next Match' }}
          <i class="pi pi-chevron-right text-xs"></i>
        </button>
      </div>

      <!-- Start Round: both roles use the BRACKET section above (per-match ▶ or START ALL) -->
      <!-- Smoke Start is in the QUEUE section above -->

      <!-- No active battle placeholder -->
      <div
        v-if="currentBattle.length === 0"
        class="w-full py-4 sm:py-3 text-center"
      >
        <span class="type-label text-content-muted">
          {{ isReadonly ? 'Waiting for organiser to start...' : 'Set up the bracket above, then press ▶ to start a match.' }}
        </span>
      </div>
    </div>
  </div>

  <!-- ── Smoke Format Winner Picker Modal ──────────────────────── -->
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="showSmokeWinnerPicker" class="smoke-picker-overlay" @click.self="showSmokeWinnerPicker = false">
        <div class="smoke-picker-modal">
          <div class="section-rule mb-4">
            <span class="section-rule-label">BATTLE HAS ENDED</span>
            <span class="section-rule-line"></span>
          </div>

          <p class="type-label text-content-muted mb-4" style="font-size:11px;letter-spacing:0.12em">
            {{ autoSmokeWinner ? 'CLEAR WINNER BY SCORE — CONFIRM TO DECLARE CHAMPION' : 'SCORES ARE TIED — SELECT THE FORMAT WINNER' }}
          </p>

          <div class="flex flex-col gap-2 mb-5">
            <button
              v-for="b in smokeWinnerCandidates"
              :key="b.name"
              class="smoke-picker-row"
              :class="{
                'smoke-picker-row-top': autoSmokeWinner?.name === b.name,
                'smoke-picker-row-tied': !autoSmokeWinner && smokeWinnerCandidates.filter(x => x.score === Math.max(...smokeWinnerCandidates.map(x => x.score ?? 0))).some(x => x.name === b.name)
              }"
              @click="confirmSmokeWinner(b)"
            >
              <span class="type-name flex-1 truncate">{{ b.name }}</span>
              <span class="smoke-score-badge">{{ b.score ?? 0 }} <span style="font-size:9px;opacity:0.6">SMK</span></span>
              <span v-if="autoSmokeWinner?.name === b.name" class="type-label text-accent ml-2" style="font-size:9px">HIGHEST</span>
              <i class="pi pi-chevron-right text-content-muted text-xs ml-1"></i>
            </button>
          </div>

          <button
            class="para-chip-sm px-4 py-2 type-label text-content-muted hover:text-content-primary w-full text-center"
            @click="showSmokeWinnerPicker = false"
          >
            CANCEL — CONTINUE BATTLE
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>

</template>

<style scoped>
.live-match-panel {
  width: 100%;
}

/* Role guidance banner */
.role-hint-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  border-left: 3px solid var(--accent-color);
  background: color-mix(in srgb, var(--accent-color) 7%, transparent);
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
}

/* Phase badge semantic overrides */
.semantic-chip-success {
  border-left: 3px solid #34d399;
  border-top: 1px solid rgba(52,211,153,0.2);
  border-right: 1px solid rgba(52,211,153,0.2);
  border-bottom: 1px solid rgba(52,211,153,0.2);
  background: rgba(52,211,153,0.08);
}

.semantic-chip-warning {
  border-left: 3px solid #f59e0b;
  border-top: 1px solid rgba(245,158,11,0.2);
  border-right: 1px solid rgba(245,158,11,0.2);
  border-bottom: 1px solid rgba(245,158,11,0.2);
  background: rgba(245,158,11,0.08);
}

/* Card styling matches global .card utility */
.card {
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
}

.card.p-5 {
  padding: 1.25rem;
}

/* Stat card replicating base.css .stat-card */
.stat-card {
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 1.25rem;
  text-align: center;
  gap: 0.25rem;
  position: relative;
}

/* Corner accent bar for stat cards */
.corner-bar-tl {
  position: absolute;
  top: 0;
  left: 0;
  width: 2px;
  height: 28%;
  background: linear-gradient(to bottom, var(--accent-color), transparent);
  pointer-events: none;
}

/* Section rule */
.section-rule {
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-rule-label {
  font-family: 'Oswald', sans-serif;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.28);
  white-space: nowrap;
}

.section-rule-line {
  flex: 1;
  height: 1px;
  background: rgba(255, 255, 255, 0.07);
}

/* Para-chip-sm replicating global */
.para-chip-sm {
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.07);
  transition: background 0.12s ease, border-color 0.12s ease, box-shadow 0.12s ease;
}

.para-chip-sm:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.10);
  border-color: rgba(255, 255, 255, 0.18);
}

.para-chip-sm:active:not(:disabled) {
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.28);
  box-shadow: inset 0 1px 4px rgba(0, 0, 0, 0.4);
}

.para-chip-sm:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* bg-accent hover state */
button.bg-accent:hover:not(:disabled) {
  filter: brightness(0.88);
}

button.bg-accent:active:not(:disabled) {
  filter: brightness(0.75);
  box-shadow: inset 0 1px 4px rgba(0, 0, 0, 0.35);
}

/* Transition for save status */
.recovery-fade-enter-active,
.recovery-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.recovery-fade-enter-from,
.recovery-fade-leave-to {
  opacity: 0;
  transform: translateY(-3px);
}

/* Text color utilities */
.text-accent { color: var(--accent-color); }
.text-content-muted { color: rgba(255, 255, 255, 0.35); }
.text-content-secondary { color: rgba(255, 255, 255, 0.55); }
.text-content-primary { color: rgba(255, 255, 255, 0.85); }
.text-content-disabled { color: rgba(255, 255, 255, 0.18); }
.text-emerald-400 { color: #34d399; }
.text-amber-400 { color: #f59e0b; }
.text-gray-400 { color: #9ca3af; }

/* Border accent */
.border-accent { border-color: var(--accent-color); }

button.border-accent:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.08);
}

/* Typography — using Anton SC via the scoped fallback */
.type-label {
  font-family: 'Oswald', sans-serif;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  line-height: 1;
}

.type-body {
  font-family: 'Oswald', sans-serif;
  font-size: 15px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  line-height: 1.1;
}

.type-stat {
  font-family: 'Oswald', sans-serif;
  font-size: 48px;
  letter-spacing: 0.02em;
  line-height: 1;
}

/* Animation for pulse */
.animate-pulse {
  animation: pulse 1.5s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* Pi icon default */
i.pi {
  font-style: normal;
}

/* ── Smoke Format Winner Picker ─────────────────────────────── */
.smoke-picker-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 16px;
}

.smoke-picker-modal {
  background: #1a1a1a;
  border: 1px solid rgba(255, 255, 255, 0.1);
  clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);
  padding: 24px;
  width: 100%;
  max-width: 420px;
}

.smoke-picker-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.07);
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  cursor: pointer;
  transition: background 0.15s;
  width: 100%;
  text-align: left;
  min-height: 52px;
}

.smoke-picker-row:hover {
  background: rgba(255, 255, 255, 0.07);
}

.smoke-picker-row-top {
  border-color: color-mix(in srgb, var(--accent-color) 40%, transparent);
  background: color-mix(in srgb, var(--accent-color) 6%, transparent);
}

.smoke-picker-row-tied {
  border-color: rgba(251, 191, 36, 0.4);
  background: rgba(251, 191, 36, 0.06);
}

.smoke-score-badge {
  font-family: 'Oswald', sans-serif;
  font-size: 16px;
  letter-spacing: 0.02em;
  color: var(--accent-color, #fff);
  flex-shrink: 0;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s;
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
</style>
