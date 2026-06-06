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
import { computed } from 'vue'
import BracketViewer from '@/components/BracketViewer.vue'
import BattleTimer from '@/components/BattleTimer.vue'

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
  canSwitchGenre:            { type: Boolean, default: true },
  genreSwitchBlockReason:    { type: String,  default: '' },
  genreChampions:            { type: Object,  default: () => ({}) },
  stompClient:               { type: Object,  default: null },
  overlayConfig:             { type: Object,  default: () => ({ leftColor: '#dc2626', rightColor: '#2563eb' }) },
  revealActive:              { type: Boolean, default: false },
  activeRoundIdx:            { type: Number,  default: 0 },
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
  'unlock',
])

// ── Genre status dot ─────────────────────────────────────────────
// Returns 'champion' | 'active' | 'idle'
function genreStatusDot(g) {
  if (props.genreChampions[g]) return 'champion'
  const phase = g === props.selectedGenre ? props.battlePhase : 'IDLE'
  if (['LOCKED', 'VOTING', 'REVEALED'].includes(phase)) return 'active'
  return 'idle'
}

// ── Judge vote display helpers ───────────────────────────────────
// Look up a judge's vote from currentBattle entries by judgeId.
function voteDisplay(judgeId) {
  const v = props.currentBattle.find(b => b?.judgeId === judgeId)
  if (!v || v.vote === undefined || v.vote === -3) return 'WAITING'
  if (v.vote === 0) return 'LEFT'
  if (v.vote === 1) return 'RIGHT'
  if (v.vote === -1) return 'TIE'
  return 'WAITING'
}

function voteColor(judgeId) {
  const v = props.currentBattle.find(b => b?.judgeId === judgeId)
  if (v?.vote === 0) return props.overlayConfig.leftColor || '#dc2626'
  if (v?.vote === 1) return props.overlayConfig.rightColor || '#2563eb'
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
  if (props.currentBattle.length === 0) return 'Choose a round to start'
  if (props.currentWinner === -2) return 'Battle is ongoing'
  if (props.currentWinner === -3) return 'Judges are not ready yet'
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
const isActiveBattleInThisRound = computed(() => {
  if (props.currentBattle.length === 0) return false
  if (props.isSmoke) return true
  return true
})

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

// ── Round tab status ─────────────────────────────────────────────
// Returns 'active' | 'done' | 'filled' | 'locked' | 'empty'
function roundTabStatus(idx) {
  if (props.isSmoke) return 'empty'
  const key = props.roundNames[idx]
  if (!key) return 'empty'
  const pairList = props.rounds?.[key]
  if (!Array.isArray(pairList)) return 'empty'

  const hasActive = props.currentBattle.length > 0 && props.currentTop === key
  if (hasActive) return 'active'

  const allHaveWinners = pairList.every(m => Array.isArray(m) && m[2])
  if (allHaveWinners && pairList.length > 0 && pairList.some(m => m[0] || m[1])) return 'done'

  if (idx > 0) {
    const prevKey = props.roundNames[idx - 1]
    const prevList = props.rounds?.[prevKey]
    if (Array.isArray(prevList) && prevList.length > 0 && !prevList.every(m => Array.isArray(m) && m[2])) {
      return 'locked'
    }
  }

  const allFilled = pairList.every(m => Array.isArray(m) && m[0] && m[1])
  if (allFilled) return 'filled'
  return 'empty'
}
</script>

<template>
  <div class="live-match-panel space-y-4">
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
      <div class="flex flex-wrap gap-2">
        <button
          v-for="g in uniqueGenres"
          :key="g"
          @click="$emit('request-genre-change', g)"
          class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1.5 type-label transition-all duration-150 inline-flex items-center gap-1.5"
          :class="[
            selectedGenre === g
              ? 'text-accent border-[color:var(--accent-muted)]'
              : !canSwitchGenre && g !== selectedGenre
                ? 'text-content-muted/40 cursor-not-allowed'
                : 'text-content-muted hover:text-content-primary'
          ]"
          :title="g !== selectedGenre && !canSwitchGenre ? genreSwitchBlockReason : ''"
          :disabled="g !== selectedGenre && !canSwitchGenre"
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
      <span
        v-if="uniqueGenres.length > 1 && !canSwitchGenre"
        class="type-label text-amber-400/70"
        style="font-size:10px;letter-spacing:0.12em"
      >{{ genreSwitchBlockReason }}</span>
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
          >{{ battlePhase }}</span>
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
          v-if="!isReadonly"
          @click="$emit('submit-revote')"
          class="para-chip-sm px-3 py-1.5 type-label text-accent transition-all"
        >START REVOTE</button>
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
          FINAL · {{ isReadonly ? 'WAITING FOR ORGANISER' : 'ORGANISER ONLY' }} — NOT REVEALED YET
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

      <!-- BattleTimer (LOCKED phase only) -->
      <div v-if="!isReadonly || battlePhase === 'LOCKED'" class="mb-4">
        <BattleTimer
          :phase="battlePhase"
          :stomp-client="stompClient"
          @unlock="$emit('unlock')"
        />
      </div>

      <!-- Round tabs (standard bracket only) -->
      <div v-if="!isSmoke && roundNames.length > 0" class="flex flex-wrap gap-2 sm:gap-1 mb-4">
        <button
          v-for="(name, idx) in roundNames"
          :key="idx"
          @click="$emit('set-round', idx)"
          class="para-chip-sm px-4 py-3 sm:py-1.5 type-label transition-all duration-150 inline-flex items-center gap-1.5 flex-1 sm:flex-none justify-center sm:justify-start"
          :class="{
            'text-accent border-[color:var(--accent-muted)]': activeRoundIdx === idx,
            'text-emerald-400/70 border-emerald-500/30': activeRoundIdx !== idx && roundTabStatus(idx) === 'done',
            'text-content-muted/40 cursor-not-allowed': roundTabStatus(idx) === 'locked',
            'text-content-muted hover:text-content-primary': activeRoundIdx !== idx && roundTabStatus(idx) !== 'done' && roundTabStatus(idx) !== 'locked',
            'border-amber-400/40 text-amber-400': roundTabStatus(idx) === 'active',
          }"
          :title="roundTabStatus(idx) === 'locked' ? 'Waiting for previous round to complete' : ''"
        >
          <i
            v-if="roundTabStatus(idx) === 'active'"
            class="pi pi-circle-fill text-[6px] text-amber-400"
            title="Active battle"
          ></i>
          <i
            v-else-if="roundTabStatus(idx) === 'done'"
            class="pi pi-check text-[9px] text-emerald-400/70"
            title="Round complete"
          ></i>
          <i
            v-else-if="roundTabStatus(idx) === 'locked'"
            class="pi pi-lock text-[8px] text-content-muted/40"
            title="Waiting for previous round"
          ></i>
          {{ name.replace('Top', 'TOP ') }}
        </button>
      </div>

      <!-- Bracket viewer (read-only) -->
      <div v-if="rounds && (typeof rounds === 'object' ? Object.keys(rounds).length : rounds.length) > 0" class="mb-4">
        <BracketViewer
          :rounds="rounds"
          :top-size="topSize"
          :current-round="currentRound"
          :is-smoke="isSmoke"
          :current-battle-left="currentBattlePairNames?.[0] ?? ''"
          :current-battle-right="currentBattlePairNames?.[1] ?? ''"
        />
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
        v-else-if="battlePhase !== 'IDLE'"
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
            <span class="type-body text-content-primary block">{{ currentBattlePair[0]?.name ?? currentBattlePair[0] }} ({{ currentBattlePair[0]?.score ?? '?' }})</span>
            <span class="type-label text-content-muted my-0.5 block">vs</span>
            <span class="type-body text-content-primary block">{{ currentBattlePair[1]?.name ?? currentBattlePair[1] }} ({{ currentBattlePair[1]?.score ?? '?' }})</span>
          </template>
        </div>
        <div class="stat-card relative">
          <div class="corner-bar-tl"></div>
          <span class="type-label text-content-muted mb-1">Queue</span>
          <span v-if="nextBattlePair" class="type-body text-content-secondary block">{{ Array.isArray(nextBattlePair) ? nextBattlePair.join(', ') : nextBattlePair }}</span>
          <span v-else class="type-stat text-content-disabled opacity-30">—</span>
        </div>
      </div>

      <!-- Phase action buttons -->
      <div v-if="currentBattle.length > 0 && !isReadonly" class="flex flex-wrap gap-3 sm:gap-2">
        <!-- LOCKED: open voting -->
        <button
          v-if="battlePhase === 'LOCKED'"
          @click="$emit('open-voting')"
          class="bg-accent para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center"
        >
          <i class="pi pi-lock-open text-xs"></i>
          Open Voting
        </button>

        <!-- VOTING: non-final, not all voted, or tie → Get Score / Rematch -->
        <button
          v-if="battlePhase === 'VOTING' && !showFinalReveal"
          :disabled="!allJudgesVoted"
          @click="$emit('get-score')"
          :class="allJudgesVoted
            ? 'bg-accent para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center'
            : 'bg-surface-700/30 para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all cursor-not-allowed opacity-50 flex-1 sm:flex-none justify-center'"
          :title="allJudgesVoted ? '' : 'Waiting for all judges to vote'"
        >
          <i class="pi pi-bolt text-xs"></i>
          {{ (Number(currentWinner) === -1 && !isSmoke) ? 'Rematch' : 'Get Score' }}
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
          Lock Champion
        </button>

        <!-- DECIDED: champion locked, ready to reveal or unlock for revote -->
        <template v-if="battlePhase === 'DECIDED'">
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
            Dismiss Reveal
          </button>
          <button
            @click="$emit('unlock-champion')"
            class="para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 text-content-muted hover:text-content-primary transition-all flex-1 sm:flex-none justify-center"
          >
            <i class="pi pi-unlock text-xs"></i>
            Unlock
          </button>
        </template>

        <!-- REVEALED: next only -->
        <template v-if="battlePhase === 'REVEALED'">
          <button
            @click="$emit('next-pair')"
            class="bg-accent para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center"
          >
            Next
            <i class="pi pi-chevron-right text-xs"></i>
          </button>
        </template>

        <!-- Champion re-reveal (not in DECIDED, has a champion but not yet revealed) -->
        <button
          v-if="battlePhase !== 'DECIDED' && (currentGenreChampion || genreChampions[selectedGenre]) && !revealActive"
          @click="$emit('reveal-champion')"
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 border-accent transition-all"
        >
          <i class="pi pi-star text-xs"></i>
          Reveal Champion
        </button>
        <button
          v-if="revealActive && battlePhase !== 'DECIDED'"
          @click="$emit('dismiss-reveal')"
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
        >
          <i class="pi pi-times text-xs"></i>
          Dismiss Reveal
        </button>
      </div>

      <!-- Phase action buttons (Emcee readonly: limited set) -->
      <div v-if="isReadonly && currentBattle.length > 0" class="flex flex-wrap gap-3 sm:gap-2">
        <!-- Emcee can trigger revel when champion is already decided -->
        <button
          v-if="battlePhase === 'DECIDED' && !revealActive"
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
          Dismiss Reveal
        </button>
        <button
          v-if="battlePhase === 'REVEALED'"
          @click="$emit('next-pair')"
          class="bg-accent para-chip-sm px-5 sm:px-4 py-3.5 sm:py-2 type-label inline-flex items-center gap-1.5 transition-all flex-1 sm:flex-none justify-center"
        >
          Next
          <i class="pi pi-chevron-right text-xs"></i>
        </button>
      </div>

      <!-- No active battle placeholder -->
      <div
        v-if="currentBattle.length === 0"
        class="w-full py-4 sm:py-3 text-center"
      >
        <span class="type-label text-content-muted">
          {{ isReadonly ? 'Waiting for organiser to start a battle...' : 'Set up bracket and start a round from the Setup panel above.' }}
        </span>
      </div>
    </div>
  </div>
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
  font-family: 'Anton SC', sans-serif;
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
  font-family: 'Anton SC', sans-serif;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  line-height: 1;
}

.type-body {
  font-family: 'Anton SC', sans-serif;
  font-size: 15px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  line-height: 1.1;
}

.type-stat {
  font-family: 'Anton SC', sans-serif;
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
</style>
