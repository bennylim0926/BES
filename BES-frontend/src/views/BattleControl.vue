<script setup>
import { addBattleJudge, addBattleGuest, battleJudgeVote, clearBattlePair, getBattleChampions, getBattleGuests, getBattleJudges, getBattlePhase, getBattleState, getOverlayConfig, getParticipantScore, getPickupCrews, getRegisteredParticipantsByEvent, removeBattleGuest, removeBattleJudge, resetBattleVotes, revealChampion, dismissChampionReveal, setActiveGenre, setBattlePair, setBattlePhase, setBattleScore, setBracketState, setOverlayConfig, updateJudgeWeightage, updateSmokeList, uploadImage } from '@/utils/api'
import { computed, onMounted, onUnmounted, ref, watch, toRaw } from 'vue'
import { useDropdowns } from '@/utils/dropdown'
import { useEventUtils } from '@/utils/eventUtils'
import { useBattleLogic } from '@/utils/battleLogic'
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket'
import { parseDropKey } from '@/utils/pointerDnd'
import LiveMatchPanel from '@/components/LiveMatchPanel.vue'
import { useAuthStore } from '@/utils/auth'

const { selectedEvent, selectedGenre, initialiseDropdown } = useDropdowns()
const { allJudges, fetchAllJudges, participants } = useEventUtils()
const { rounds, topSize, roundSizes, isSmoke, standardBattleRound, sevenToSmokeRound } = useBattleLogic()

const authStore = useAuthStore()
const isAdminOrOrganiser = computed(() => {
  const role = authStore.user?.['role']?.[0]?.authority
  return role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'
})

const battleJudges = ref([])
const memberLookup = ref({}) // participantName → all member names (including rep)
const getMembersFor = (name) => memberLookup.value[name] ?? []
const isGuestSlot = (name) => !!name && guestsForCurrentGenre.value.some(g => g.guestName === name)
const currentBattle = ref([])
const currentWinner = ref(-2)
const currentRound = ref(0)
const currentTop = ref('')
const battlePhase = ref('IDLE')
const showSizeChangeConfirm = ref(false)  // prompt before switching bracket size
const showRoundChangeConfirm = ref(false) // prompt before switching round during battle
const pendingSize = ref(null)            // the size user wants to switch to
const pendingRoundIdx = ref(null)        // the round index user wants to switch to
const finalTieBlocked = ref(false)
const revealActive = ref(false)
let skipSizeChangeClear = false          // guard: suppress clear when topSize changes programmatically
const overlayConfig = ref({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' })
const showRecoveryBanner = ref(false)
const recoveryState      = ref(null)
const genreChampions     = ref({})
const lastAppliedState = ref('')  // JSON string of last applied /topic/battle/state snapshot

// Single entry point for full-state hydration from /topic/battle/state or REST API.
// Diffs against lastAppliedState to skip no-op updates and prevent animation disruption.
const hydrateFromState = (state) => {
  if (!state) return
  const snapshot = JSON.stringify(state)
  if (snapshot === lastAppliedState.value) return
  lastAppliedState.value = snapshot

  if (state.bracket) {
    if (state.bracket.topSize !== undefined) {
      const size = Number(state.bracket.topSize)
      if (!isNaN(size) && size !== Number(topSize.value)) {
        skipSizeChangeClear = true
        topSize.value = size
      }
    }
    if (state.bracket.rounds) {
      rounds.value = state.bracket.rounds
    }
  }
  if (state.currentRoundIndex !== undefined) {
    currentRound.value = state.currentRoundIndex
  }
  if (state.currentPair) {
    const pair = state.currentPair
    if (pair.left || pair.right) {
      const bracketRounds = state.bracket?.rounds ?? rounds.value
      let topKey = pair.isFinal ? 'Top2' : null
      if (!topKey && bracketRounds) {
        for (const key of Object.keys(bracketRounds)) {
          const matchList = bracketRounds[key]
          if (!Array.isArray(matchList)) continue
          if (matchList.some(m => Array.isArray(m) && m[0] === pair.left && m[1] === pair.right)) {
            topKey = key; break
          }
        }
      }
      if (topKey) {
        currentTop.value = topKey
        const pairList = bracketRounds[topKey] ?? []
        const pairIdx = pairList.findIndex(
          m => Array.isArray(m) && m[0] === pair.left && m[1] === pair.right
        )
        if (pairIdx >= 0) currentRound.value = pairIdx
        currentBattle.value = [pairIdx >= 0 ? pairIdx : 0, pairList]
      }
    } else {
      currentBattle.value = []
      currentTop.value = ''
    }
  }
  if (state.battlePhase) {
    battlePhase.value = state.battlePhase
  }
  if (state.champion) {
    genreChampions.value = { ...genreChampions.value, [selectedGenre.value]: state.champion }
  }
  if (state.judges?.length) {
    battleJudges.value = { judges: state.judges }
  }
  if (state.resolvedParticipants && state.resolvedParticipants !== '') {
    try {
      resolvedParticipants.value = JSON.parse(state.resolvedParticipants)
    } catch (_) { resolvedParticipants.value = null }
  }
}

const saveStatus = ref('idle') // 'idle' | 'saving' | 'saved' | 'error'
let saveTimer = null
const markSaving = () => { saveStatus.value = 'saving'; clearTimeout(saveTimer) }
const markSaved  = () => {
  saveStatus.value = 'saved'
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => { saveStatus.value = 'idle' }, 2200)
}
const _markSaveError = () => { saveStatus.value = 'error' }

// ── Setup panel state ──────────────────────────────────────────
const setupExpanded = ref(true)

const setupLocked = computed(() =>
  battlePhase.value !== 'IDLE' ||
  (currentBattle.value?.length ?? 0) > 0 ||
  (!isSmoke.value && Object.values(rounds.value).some(
    list => Array.isArray(list) && list.some(m => Array.isArray(m) && m[2])
  )) ||
  (isSmoke.value && Array.isArray(rounds.value) && rounds.value.some(r => (r?.score ?? 0) > 0))
)

const hasJudges = computed(() => (battleJudges.value?.judges ?? []).length > 0)

// Auto-collapse setup panel the first time a battle starts
watch(setupLocked, (locked) => {
  if (locked) setupExpanded.value = false
}, { once: true })

// ── Reset bracket inline two-step ─────────────────────────────
const resetConfirmStep = ref(0) // 0 = idle, 1 = awaiting confirm

// ── WIN button confirmation ────────────────────────────────────
// { roundKey, matchIdx, slotIdx, name, replacing } — null when no pending confirm
const pendingWin = ref(null)

const requestWin = (roundKey, matchIdx, slotIdx, name) => {
  const currentWinnerName = rounds.value[roundKey]?.[matchIdx]?.[2] ?? null
  pendingWin.value = { roundKey, matchIdx, slotIdx, name, replacing: currentWinnerName }
}

const confirmWin = () => {
  if (!pendingWin.value) return
  const { roundKey, matchIdx, slotIdx } = pendingWin.value
  setWinner(roundKey, matchIdx, slotIdx)
  pendingWin.value = null
}

const cancelWin = () => { pendingWin.value = null }

// ── Start-from-here confirmation ──────────────────────────────
// { top, pairList, matchIdx, startAll } — null when no pending confirm
const pendingStartAt = ref(null)

const roundLabel = (top) => {
  const labels = { Top16: 'Top 16', Top8: 'Quarterfinals', Top4: 'Semifinals', Top2: 'Finals' }
  return labels[top] || top || ''
}

const requestStartAt = (top, pairList, matchIdx) => {
  pendingStartAt.value = { top, pairList, matchIdx, startAll: false }
}

const requestStartAll = (top, pairList) => {
  pendingStartAt.value = { top, pairList, matchIdx: 0, startAll: true }
}

const confirmStartAt = async () => {
  if (!pendingStartAt.value) return
  const { top, pairList, matchIdx, startAll } = pendingStartAt.value
  pendingStartAt.value = null
  if (startAll) {
    await initiateBattlePair(top, pairList)
  } else {
    await initiateBattlePairAt(top, pairList, matchIdx)
  }
}

const cancelStartAt = () => { pendingStartAt.value = null }

const handleEmceeStartRound = () => {
  // Use the first round name and its pair list
  const roundName = roundNames.value[0] || `Top${topSize.value}`
  const pairList = rounds.value[roundName]
  if (!pairList || pairList.length === 0) return
  // Fire the same flow as the desktop "Start from this match" button
  pendingStartAt.value = { top: roundName, pairList, matchIdx: 0, startAll: true }
  confirmStartAt()
}

// ── Genre switcher — per-genre status dot ─────────────────────
// Returns 'champion' | 'active' | 'idle'
const _genreStatusDotMap = computed(() => {
  const map = {}
  for (const genre of uniqueGenres.value) {
    if (genreChampions.value[genre]) { map[genre] = 'champion'; continue }
    // Only the active genre has a known phase; other genres show as idle
    const phase = genre === selectedGenre.value ? battlePhase.value : 'IDLE'
    map[genre] = ['LOCKED', 'VOTING', 'REVEALED'].includes(phase) ? 'active' : 'idle'
  }
  return map
})

const canSwitchGenre = computed(() =>
  battlePhase.value === 'IDLE' || battlePhase.value === 'DECIDED'
)

const genreSwitchBlockReason = computed(() => {
  if (battlePhase.value === 'LOCKED' || battlePhase.value === 'VOTING') return 'Finish this match first'
  if (battlePhase.value === 'REVEALED') return 'Click Next to advance, then switch genres'
  return ''
})

const HEX_RE = /^#[0-9A-Fa-f]{6}$/
const overlayConfigError = ref('')
const pushOverlayConfig = async () => {
  if (!HEX_RE.test(overlayConfig.value.leftColor) || !HEX_RE.test(overlayConfig.value.rightColor)) {
    overlayConfigError.value = 'Colors must be a valid hex (e.g. #dc2626)'
    return
  }
  overlayConfigError.value = ''
  await setOverlayConfig(overlayConfig.value)
}

const activeRoundIdx = ref(0)

// LiveMatchPanel tab index — separate from currentRound (match index).
// currentRound is overloaded: it's the match index within currentTop, but
// LiveMatchPanel uses it as a round tab index. When nextPair increments
// currentRound from 0 to 1, the tab would incorrectly switch from Top16 to Top8.
const viewedRoundIdx = ref(0)
watch(currentTop, (newTop) => {
  const idx = roundNames.value.indexOf(newTop)
  if (idx >= 0) viewedRoundIdx.value = idx
})

const roundNames = computed(() => {
  if (isSmoke.value) return []
  const sizes = []
  let s = topSize.value
  while (s >= 2) {
    sizes.push(`Top${s}`)
    s = Math.floor(s / 2)
  }
  return sizes
})

const pickupCrews = ref([])
const crewSortMode = ref('leader')  // 'leader' | 'avg'

const battleGuests = ref([])
const newGuestName = ref('')
const newGuestEntryRound = ref('')
const newGuestMembers = ref('') // comma-separated member names
const addingGuest = ref(false)

const dragSource = ref(null)  // { roundKey, matchIdx, slotIdx }
const dragOverKey = ref(null) // `${roundKey}-${matchIdx}-${slotIdx}`
const poolDragName = ref(null) // name being dragged from the seeding pool

const uploadedFiles = ref([])

const onFileChange = async (e) => {
  const files = Array.from(e.target.files)
  for (const file of files) {
    await uploadImage(file)
    if (!uploadedFiles.value.includes(file.name)) {
      uploadedFiles.value.push(file.name)
    }
  }
  e.target.value = ''
}

const resetJudgeVote = async () => {
  // Reset locally first so the panel shows WAITING immediately, before WS echo arrives
  ;(battleJudges.value?.judges ?? []).forEach(j => { j.vote = -3 })
  await Promise.all(battleJudges.value.judges.map(j => battleJudgeVote(j.id, -3)))
}

const currentBattlePair = computed(() => {
  if (currentBattle.value.length === 0) return
  if (isSmoke.value) return [currentBattle?.value[0], currentBattle?.value[1]]
  const left = currentBattle?.value[1][currentBattle?.value[0]][0]
  const right = currentBattle?.value[1][currentBattle?.value[0]][1]
  return [left, right]
})

const isActivePair = (match) => {
  if (!currentBattlePair.value || !match[0] || !match[1]) return false
  const [a, b] = currentBattlePair.value
  return (match[0] === a && match[1] === b) || (match[0] === b && match[1] === a)
}

const updateSmokePair = async () => {
  currentBattle.value = [rounds.value[0], rounds.value[1], rounds.value.slice(2)]
  await updateSmokeList(rounds.value)
}

const initiateBattlePairAt = async (top, pairList, startIdx) => {
  if (!hasJudges.value) {
    alert('Cannot start round: no judges assigned. Add at least one judge first.')
    return
  }
  markSaving()
  currentWinner.value = -2
  revealActive.value = false
  await resetJudgeVote()
  if (top === 'Top2' && Array.isArray(rounds.value['Top2']?.[0])) {
    rounds.value['Top2'][0][2] = null
    broadcastBracket()
  }
  currentBattle.value = [startIdx, pairList]
  const left = pairList[startIdx][0]
  const right = pairList[startIdx][1]
  await setBattlePair(left, right, top === 'Top2', getMembersFor(left), getMembersFor(right))
  await setBattlePhase('LOCKED')
  battlePhase.value = 'LOCKED'
  currentRound.value = startIdx
  currentTop.value = top
  broadcastBracket()  // syncs currentRoundIndex to backend so recovery finds the right pair

  markSaved()
}

const initiateBattlePair = async (top, pairList) => {
  if (!hasJudges.value) {
    alert('Cannot start round: no judges assigned. Add at least one judge first.')
    return
  }
  markSaving()
  currentWinner.value = -2
  revealActive.value = false
  await resetJudgeVote()
  if (isSmoke.value) {
    await setBattlePair(rounds.value[0].name, rounds.value[1].name, false, getMembersFor(rounds.value[0].name), getMembersFor(rounds.value[1].name))
    await updateSmokePair()  // must await so smoke list is posted before overlay reloads
    await setBattlePhase('LOCKED')
    battlePhase.value = 'LOCKED'
    return
  }
  // Clear the Top2 winner slot so currentGenreChampion goes null during a re-run,
  // preventing a stale "Reveal Champion" button from appearing alongside showFinalReveal.
  if (top === 'Top2' && Array.isArray(rounds.value['Top2']?.[0])) {
    rounds.value['Top2'][0][2] = null
    broadcastBracket()
  }
  currentBattle.value = [0, pairList]
  const left = currentBattle?.value[1][currentBattle?.value[0]][0]
  const right = currentBattle?.value[1][currentBattle?.value[0]][1]
  await setBattlePair(left, right, top === 'Top2', getMembersFor(left), getMembersFor(right))
  await setBattlePhase('LOCKED')
  battlePhase.value = 'LOCKED'
  currentRound.value = 0
  currentTop.value = top

  markSaved()
}


const nextPair = async () => {
  if (currentBattle.value.length === 0) return
  markSaving()
  await resetJudgeVote()
  if (isSmoke.value) {
    await update7toSmokeMatch(currentWinner.value)
    await setBattlePair(rounds.value[0].name, rounds.value[1].name, false, getMembersFor(rounds.value[0].name), getMembersFor(rounds.value[1].name))
    await setBattlePhase('LOCKED')
    battlePhase.value = 'LOCKED'
    currentWinner.value = -2
  } else {
    if (currentBattle?.value[0] < currentBattle?.value[1].length - 1) {
      currentBattle.value = [currentBattle.value[0] + 1, currentBattle.value[1]]
      const left = currentBattle?.value[1][currentBattle?.value[0]][0]
      const right = currentBattle?.value[1][currentBattle?.value[0]][1]
      await setBattlePair(left, right, currentTop.value === 'Top2', getMembersFor(left), getMembersFor(right))
      await setBattlePhase('LOCKED')
      battlePhase.value = 'LOCKED'
      currentWinner.value = -2
      currentRound.value += 1
    
    } else {
      await clearBattlePair()
      await setBattlePhase('IDLE')
      battlePhase.value = 'IDLE'
      currentWinner.value = -2
      currentBattle.value = []
      currentTop.value = ''
    
    }
  }
  markSaved()
}

const topParticipants = computed(() => {
  return [...new Set(participants.value.filter(p => p.genreName === selectedGenre.value).map(p => p.participantName))]
})

// Pre-formed teams: registered with a format (e.g. "2v2"), deduped by name, sorted by avg score
const preFormedTeams = computed(() => {
  const seen = new Map()
  for (const p of participants.value) {
    if (p.genreName !== selectedGenre.value) continue
    if (!p.format || p.format === '') continue
    if (!seen.has(p.participantName)) {
      seen.set(p.participantName, { name: p.participantName, totalScore: 0, count: 0 })
    }
    const entry = seen.get(p.participantName)
    entry.totalScore += p.score ?? 0
    entry.count++
  }
  return [...seen.values()]
    .map(e => ({ name: e.name, avgScore: e.count > 0 ? e.totalScore / e.count : 0 }))
    .sort((a, b) => b.avgScore - a.avgScore)
})

// Pickup crews sorted by leader score or team avg score
const sortedPickupCrews = computed(() =>
  [...pickupCrews.value].sort((a, b) => {
    const sa = crewSortMode.value === 'leader' ? (a.leaderScore ?? 0) : (a.avgScore ?? 0)
    const sb = crewSortMode.value === 'leader' ? (b.leaderScore ?? 0) : (b.avgScore ?? 0)
    return sb - sa
  })
)

// True only when BOTH pre-formed teams AND pickup crews exist — Split bracket is only relevant then
const isMixedBracket = computed(() => preFormedTeams.value.length > 0 && pickupCrews.value.length > 0)

// True only when the genre has participants that BATTLE AS TEAMS (have member names in lookup).
// Individuals with a non-empty format field (e.g. smoke-style with crew affiliation) are excluded.
const isTeamGenre = computed(() => {
  if (isSmoke.value) return false
  return preFormedTeams.value.some(t => (memberLookup.value[t.name]?.length ?? 0) > 0)
})

// True when the selected genre's name indicates a 7-to-smoke format
const isGenreSmoke = computed(() => {
  const g = selectedGenre.value?.toLowerCase() ?? ''
  return g.includes('7 to smoke') || g.includes('7tosmoke')
})

const guestsForCurrentGenre = computed(() =>
  battleGuests.value.filter(g => g.genreName === selectedGenre.value)
)

const entryRoundOptions = computed(() =>
  roundSizes.value.map(s => `Top${s}`)
)

// Pool used by bracket slot dropdowns, seeding picker, and all sort/fill functions.
// When pre-formed teams exist, exclude raw individual names from the pool — team names only.
const bracketPool = computed(() => {
  let pool
  if (isMixedBracket.value) {
    pool = [
      ...preFormedTeams.value.map(t => t.name),
      ...sortedPickupCrews.value.map(c => c.crewName),
    ]
  } else if (preFormedTeams.value.length > 0) {
    pool = preFormedTeams.value.map(t => t.name)
  } else {
    pool = topNParticipants.value
  }
  // Include battle guests not already in pool so they appear in bracket slot dropdowns
  const guestNames = guestsForCurrentGenre.value.map(g => g.guestName).filter(n => !pool.includes(n))
  return [...pool, ...guestNames]
})

const participantsInFirstRound = computed(() => {
  if (isSmoke.value) {
    // Smoke rounds are a flat array of { name, score }
    return new Set(Array.isArray(rounds.value) ? rounds.value.filter(r => r?.name).map(r => r.name) : [])
  }
  const key = `Top${bracketSize.value}`
  if (!rounds.value[key]) return new Set()
  const placed = new Set()
  for (const match of rounds.value[key]) {
    if (match[0]) placed.add(match[0])
    if (match[1]) placed.add(match[1])
  }
  return placed
})

const poolParticipants = computed(() => {
  const guestSet = new Set(guestsForCurrentGenre.value.map(g => g.guestName))
  const slots = bracketSize.value - guestSet.size
  if (slots <= 0) return []
  const eligible = bracketPool.value.filter(n => !guestSet.has(n)).slice(0, slots)
  const placed = participantsInFirstRound.value
  return eligible
    .filter(n => !placed.has(n))
    .map(name => {
      const p = participants.value.find(x => x.participantName === name)
      if (p) return { name, score: p.score ?? 0 }
      const t = preFormedTeams.value.find(x => x.name === name)
      if (t) return { name, score: t.avgScore ?? 0 }
      const c = sortedPickupCrews.value.find(x => x.crewName === name)
      return { name, score: c ? (crewSortMode.value === 'leader' ? c.leaderScore : c.avgScore) ?? 0 : 0 }
    })
    .sort((a, b) => b.score - a.score)
})

// Bracket seeding
const bracketSize = computed(() => Number(topSize.value) === 7 ? 8 : Number(topSize.value))

// Resolved tie-breaker participants loaded from backend via getBattleState() / hydrateFromState.
const resolvedParticipants = ref(null)

const topNParticipants = computed(() => {
  if (resolvedParticipants.value) return resolvedParticipants.value.slice(0, bracketSize.value)
  return topParticipants.value.slice(0, bracketSize.value)
})
const seeds = ref([])
const activeSeedIdx = ref(null)


const resetSeeds = () => {
  seeds.value = Array(bracketSize.value).fill(null)
  activeSeedIdx.value = null
}
const rankAsc = ref(false) // false = desc (highest first), true = asc (lowest first)
// Helper: fill a fixed-length array with items from a pool, padding with nulls
const fillHalf = (pool, size) => [
  ...pool.slice(0, size),
  ...Array(Math.max(0, size - pool.length)).fill(null),
]

const autoFillSeeds = () => {
  const guestSet = new Set(guestsForCurrentGenre.value.map(g => g.guestName))
  const freeSlots = bracketSize.value - guestSet.size  // only fill slots not taken by guests
  if (isMixedBracket.value) {
    const half = Math.floor(freeSlots / 2)
    const preformed = preFormedTeams.value.map(t => t.name).filter(n => !guestSet.has(n))
    const pickup = sortedPickupCrews.value.map(c => c.crewName).filter(n => !guestSet.has(n))
    const orderedPre = rankAsc.value ? [...preformed].reverse() : preformed
    const orderedPick = rankAsc.value ? [...pickup].reverse() : pickup
    seeds.value = [...fillHalf(orderedPre, half), ...fillHalf(orderedPick, half)]
  } else {
    const pool = bracketPool.value.filter(n => !guestSet.has(n)).slice(0, freeSlots)
    const ordered = rankAsc.value ? [...pool].reverse() : pool
    seeds.value = [...ordered, ...Array(Math.max(0, freeSlots - ordered.length)).fill(null)]
  }
  activeSeedIdx.value = null
  applyToFirstRound()
}

const highVsLowFill = () => {
  const guestSet = new Set(guestsForCurrentGenre.value.map(g => g.guestName))
  const freeSlots = bracketSize.value - guestSet.size
  const hvl = (pool, size) => {
    const n = pool.length
    const result = Array(size).fill(null)
    for (let i = 0; i < Math.ceil(n / 2); i++) {
      result[i * 2] = pool[i]
      if (n - 1 - i !== i) result[i * 2 + 1] = pool[n - 1 - i]
    }
    return result
  }
  if (isMixedBracket.value) {
    const half = Math.floor(freeSlots / 2)
    seeds.value = [
      ...hvl(preFormedTeams.value.map(t => t.name).filter(n => !guestSet.has(n)), half),
      ...hvl(sortedPickupCrews.value.map(c => c.crewName).filter(n => !guestSet.has(n)), half),
    ]
  } else {
    seeds.value = hvl(bracketPool.value.filter(n => !guestSet.has(n)).slice(0, freeSlots), freeSlots)
  }
  activeSeedIdx.value = null
  applyToFirstRound()
}

const randomFill = () => {
  const guestSet = new Set(guestsForCurrentGenre.value.map(g => g.guestName))
  const freeSlots = bracketSize.value - guestSet.size
  if (isMixedBracket.value) {
    const half = Math.floor(freeSlots / 2)
    const preformed = [...preFormedTeams.value.map(t => t.name).filter(n => !guestSet.has(n))].sort(() => Math.random() - 0.5)
    const pickup = [...sortedPickupCrews.value.map(c => c.crewName).filter(n => !guestSet.has(n))].sort(() => Math.random() - 0.5)
    seeds.value = [...fillHalf(preformed, half), ...fillHalf(pickup, half)]
  } else {
    const pool = [...bracketPool.value.filter(n => !guestSet.has(n)).slice(0, freeSlots)].sort(() => Math.random() - 0.5)
    seeds.value = [...pool, ...Array(Math.max(0, freeSlots - pool.length)).fill(null)]
  }
  activeSeedIdx.value = null
  applyToFirstRound()
}

// Split: pre-formed teams fill the first half of the bracket, pickup crews fill the second half.
// This guarantees pre-formed teams only face each other and pickup crews only face each other
// until the final — where one side winner meets the other.
const splitBracketFill = () => {
  const half = Math.floor(bracketSize.value / 2)
  const preformed = preFormedTeams.value.slice(0, half).map(t => t.name)
  const pickup = sortedPickupCrews.value.slice(0, half).map(c => c.crewName)
  const newSeeds = Array(bracketSize.value).fill(null)
  preformed.forEach((name, i) => { newSeeds[i] = name })
  pickup.forEach((name, i) => { newSeeds[half + i] = name })
  seeds.value = newSeeds
  activeSeedIdx.value = null
  applyToFirstRound()
}
const placeGuestsInBracket = () => {
  const guests = guestsForCurrentGenre.value
  if (guests.length === 0) return
  const guestNames = new Set(guests.map(g => g.guestName))

  // ── Smoke: flat array ─────────────────────────────
  if (isSmoke.value) {
    if (!Array.isArray(rounds.value) || rounds.value.length === 0) {
      rounds.value = sevenToSmokeRound()
    }
    for (const guest of guests) {
      const alreadyPlaced = rounds.value.some(r => r?.name === guest.guestName)
      if (alreadyPlaced) continue
      const emptySlot = rounds.value.find(r => !r?.name)
      if (emptySlot) { emptySlot.name = guest.guestName; continue }
      // Displace lowest-scored non-guest
      let lowestScore = Infinity; let lowestSlot = null
      for (const slot of rounds.value) {
        if (!slot?.name || guestNames.has(slot.name)) continue
        const score = participants.value.find(p => p.participantName === slot.name)?.score ?? 0
        if (score < lowestScore) { lowestScore = score; lowestSlot = slot }
      }
      if (lowestSlot) lowestSlot.name = guest.guestName
    }
    broadcastBracket()
    return
  }

  // ── Standard bracket ──────────────────────────────
  for (const guest of guests) {
    const roundKey = guest.entryRound
    if (!rounds.value[roundKey]) continue
    const alreadyPlaced = rounds.value[roundKey].some(m => m[0] === guest.guestName || m[1] === guest.guestName)
    if (alreadyPlaced) continue
    // Try to find an empty slot first
    let placed = false
    for (const match of rounds.value[roundKey]) {
      if (match[0] === null) { match[0] = guest.guestName; placed = true; break }
      if (match[1] === null) { match[1] = guest.guestName; placed = true; break }
    }
    if (placed) continue
    // Bracket is full — displace the lowest-scored non-guest participant in this round
    let lowestScore = Infinity
    let lowestMatch = null
    let lowestSlot = null
    for (const match of rounds.value[roundKey]) {
      for (let s = 0; s < 2; s++) {
        const name = match[s]
        if (!name || guestNames.has(name)) continue
        const score = participants.value.find(p => p.participantName === name)?.score ?? 0
        if (score < lowestScore) {
          lowestScore = score
          lowestMatch = match
          lowestSlot = s
        }
      }
    }
    if (lowestMatch !== null) lowestMatch[lowestSlot] = guest.guestName
  }
  broadcastBracket()
}

const applyToFirstRound = () => {
  if (isSmoke.value) {
    const guestNames = new Set(guestsForCurrentGenre.value.map(g => g.guestName))
    const filteredSeeds = seeds.value.filter(n => n === null || !guestNames.has(n))
    let si = 0
    rounds.value = rounds.value.map((r, _i) => {
      if (r?.name && guestNames.has(r.name)) return r // pinned guest
      return { name: filteredSeeds[si++] ?? null, score: r?.score ?? 0 }
    })
    broadcastBracket()
    return
  }
  const key = `Top${bracketSize.value}`
  if (!rounds.value[key]) return
  const guestNames = new Set(guestsForCurrentGenre.value.map(g => g.guestName))
  // Collect non-guest slots in order; fill them from seeds (guests stay pinned)
  const freeSlots = []
  for (let i = 0; i < bracketSize.value / 2; i++) {
    if (!guestNames.has(rounds.value[key][i][0])) freeSlots.push([i, 0])
    if (!guestNames.has(rounds.value[key][i][1])) freeSlots.push([i, 1])
  }
  const filteredSeeds = seeds.value.filter(n => n === null || !guestNames.has(n))
  freeSlots.forEach(([mi, si], idx) => {
    rounds.value[key][mi][si] = filteredSeeds[idx] ?? null
  })
  broadcastBracket()
}

watch([bracketSize, selectedGenre], resetSeeds, { immediate: true })

// 7 to Smoke slot picker

const sizes = ref([7, 8, 16, 32])

function initRounds() {
  rounds.value = {}
  if (isSmoke.value) return sevenToSmokeRound()
  return standardBattleRound()
}

const broadcastBracket = () => setBracketState(toRaw(rounds.value), topSize.value, currentRound.value)

// Re-broadcast the current battle pair to the overlay if a bracket drag/drop changed
// its slots. Called after onDrop / onSmokeDrop so currentBattlePair already reflects
// the updated rounds.
const reBroadcastCurrentPairIfActive = () => {
  if (isSmoke.value || currentBattle.value.length === 0) return
  const pair = currentBattlePair.value
  if (pair?.[0] && pair?.[1]) {
    setBattlePair(pair[0], pair[1], currentTop.value === 'Top2',
      getMembersFor(pair[0]), getMembersFor(pair[1]))
  }
}

const onDrop = (tgtRound, tgtMatch, tgtSlot) => {
  // Capture whether the current pair is affected BEFORE clearing drag state
  const curRound = currentTop.value
  const curMatch = currentRound.value
  const tgtIsCurrent = tgtRound === curRound && tgtMatch === curMatch
  const srcIsCurrent = dragSource.value
    ? dragSource.value.roundKey === curRound && dragSource.value.matchIdx === curMatch
    : false

  // Pool → bracket drop
  if (poolDragName.value) {
    const name = poolDragName.value
    poolDragName.value = null
    dragOverKey.value = null
    const guestNames = new Set(guestsForCurrentGenre.value.map(g => g.guestName))
    if (guestNames.has(rounds.value[tgtRound][tgtMatch][tgtSlot])) return // never overwrite a pinned guest
    rounds.value[tgtRound][tgtMatch][tgtSlot] = name
    rounds.value[tgtRound][tgtMatch][2] = null
    broadcastBracket()
    if (tgtIsCurrent) reBroadcastCurrentPairIfActive()
    return
  }
  if (!dragSource.value) return
  const { roundKey: srcRound, matchIdx: srcMatch, slotIdx: srcSlot } = dragSource.value
  dragSource.value = null
  dragOverKey.value = null
  if (srcRound === tgtRound && srcMatch === tgtMatch && srcSlot === tgtSlot) return

  const srcVal = rounds.value[srcRound][srcMatch][srcSlot]
  const tgtVal = rounds.value[tgtRound][tgtMatch][tgtSlot]
  rounds.value[srcRound][srcMatch][srcSlot] = tgtVal
  rounds.value[tgtRound][tgtMatch][tgtSlot] = srcVal
  // Clear winner state for affected matches to avoid stale highlights
  rounds.value[srcRound][srcMatch][2] = null
  if (srcRound !== tgtRound || srcMatch !== tgtMatch) rounds.value[tgtRound][tgtMatch][2] = null

  broadcastBracket()
  if (tgtIsCurrent || srcIsCurrent) reBroadcastCurrentPairIfActive()
}

const onSmokeDrop = (tgtIdx) => {
  const guestNames = new Set(guestsForCurrentGenre.value.map(g => g.guestName))
  const tgtName = rounds.value[tgtIdx]?.name
  // Pool → smoke slot
  if (poolDragName.value) {
    const name = poolDragName.value
    poolDragName.value = null
    dragOverKey.value = null
    if (guestNames.has(tgtName)) return // never overwrite a pinned guest
    if (rounds.value[tgtIdx]) rounds.value[tgtIdx] = { ...rounds.value[tgtIdx], name }
    broadcastBracket()
    return
  }
  // Smoke slot → smoke slot swap
  if (dragSource.value?.smokeIdx === undefined) return
  const srcIdx = dragSource.value.smokeIdx
  dragSource.value = null
  dragOverKey.value = null
  if (srcIdx === tgtIdx) return
  if (guestNames.has(tgtName)) return // don't overwrite pinned guest
  const srcName = rounds.value[srcIdx]?.name
  if (rounds.value[srcIdx]) rounds.value[srcIdx] = { ...rounds.value[srcIdx], name: tgtName ?? null }
  if (rounds.value[tgtIdx]) rounds.value[tgtIdx] = { ...rounds.value[tgtIdx], name: srcName ?? null }
  broadcastBracket()
}

// ── Pointer-events DnD (replaces HTML5 drag API — works on touch + desktop) ──

let _ghostEl = null
let _ptrMoveHandler = null
let _ptrUpHandler = null

const _removePtrListeners = () => {
  if (_ptrMoveHandler) { document.removeEventListener('pointermove', _ptrMoveHandler); _ptrMoveHandler = null }
  if (_ptrUpHandler) {
    document.removeEventListener('pointerup',     _ptrUpHandler)
    document.removeEventListener('pointercancel', _ptrUpHandler)
    _ptrUpHandler = null
  }
}

const _cleanupDrag = () => {
  if (_ghostEl) { document.body.removeChild(_ghostEl); _ghostEl = null }
  _removePtrListeners()
  dragSource.value  = null
  poolDragName.value = null
  dragOverKey.value  = null
}

const onPointerDragStart = (type, payload, e) => {
  if (setupLocked.value) return
  e.preventDefault()

  // Set existing drag-state refs (drives existing highlight CSS — no template class changes)
  if (type === 'pool') {
    poolDragName.value = payload          // payload = name string
    dragSource.value   = null
  } else if (type === 'bracket') {
    dragSource.value   = payload          // payload = { roundKey, matchIdx, slotIdx }
    poolDragName.value = null
  } else if (type === 'smoke') {
    dragSource.value   = { smokeIdx: payload }   // payload = mIdx (number)
    poolDragName.value = null
  }

  // Resolve display name for ghost label
  let ghostName = ''
  if (type === 'pool') {
    ghostName = payload
  } else if (type === 'bracket') {
    ghostName = rounds.value[payload.roundKey]?.[payload.matchIdx]?.[payload.slotIdx] ?? ''
  } else if (type === 'smoke') {
    ghostName = rounds.value[payload]?.name ?? ''
  }

  // Create ghost element — position above finger on touch, below-right of cursor on mouse
  const _ghostOffX = e.pointerType === 'touch' ? -20 : 12
  const _ghostOffY = e.pointerType === 'touch' ? -50 : 12
  _ghostEl = document.createElement('div')
  _ghostEl.textContent = ghostName
  Object.assign(_ghostEl.style, {
    position:      'fixed',
    left:          `${e.clientX + _ghostOffX}px`,
    top:           `${e.clientY + _ghostOffY}px`,
    padding:       '5px 14px',
    background:    '#1a1a1a',
    border:        `1.5px solid ${type === 'pool' ? 'rgba(255,255,255,0.25)' : 'rgba(248,113,113,0.65)'}`,
    borderRadius:  '8px',
    fontSize:      '12px',
    fontWeight:    '600',
    color:         '#f0f0f0',
    boxShadow:     '0 10px 28px rgba(0,0,0,0.7)',
    whiteSpace:    'nowrap',
    pointerEvents: 'none',
    zIndex:        '9999',
  })
  document.body.appendChild(_ghostEl)

  _ptrMoveHandler = (ev) => {
    const ox = ev.pointerType === 'touch' ? -20 : 12
    const oy = ev.pointerType === 'touch' ? -50 : 12
    _ghostEl.style.left = `${ev.clientX + ox}px`
    _ghostEl.style.top  = `${ev.clientY + oy}px`

    // Find drop target under pointer (hide ghost first so it doesn't intercept)
    _ghostEl.style.display = 'none'
    const el = document.elementFromPoint(ev.clientX, ev.clientY)
    _ghostEl.style.display = ''
    const dropEl = el?.closest('[data-drop-key]')
    if (dropEl) {
      const parsed = parseDropKey(dropEl.dataset.dropKey)
      if (parsed?.type === 'bracket') {
        dragOverKey.value = `${parsed.roundKey}-${parsed.matchIdx}-${parsed.slotIdx}`
      } else if (parsed?.type === 'smoke') {
        dragOverKey.value = `smoke-${parsed.idx}`
      } else {
        dragOverKey.value = null
      }
    } else {
      dragOverKey.value = null
    }
  }

  _ptrUpHandler = (ev) => {
    _removePtrListeners()

    // Hide ghost before elementFromPoint so it doesn't intercept the hit test
    if (_ghostEl) _ghostEl.style.display = 'none'
    const el = document.elementFromPoint(ev.clientX, ev.clientY)
    if (_ghostEl) { document.body.removeChild(_ghostEl); _ghostEl = null }

    const dropKeyEl = el?.closest('[data-drop-key]')
    const parsed = dropKeyEl ? parseDropKey(dropKeyEl.dataset.dropKey) : null

    if (parsed && !setupLocked.value) {
      if (parsed.type === 'bracket') {
        onDrop(parsed.roundKey, parsed.matchIdx, parsed.slotIdx)
      } else if (parsed.type === 'smoke') {
        onSmokeDrop(parsed.idx)
      }
    } else {
      // No valid drop — clear state manually (onDrop/onSmokeDrop would have done this)
      dragSource.value   = null
      poolDragName.value = null
      dragOverKey.value  = null
    }
  }

  document.addEventListener('pointermove',  _ptrMoveHandler)
  document.addEventListener('pointerup',    _ptrUpHandler)
  document.addEventListener('pointercancel', _ptrUpHandler)
}

const clearSmokeSlot = (idx) => {
  if (!rounds.value[idx]) return
  rounds.value[idx] = { ...rounds.value[idx], name: null }
  broadcastBracket()
}

const clearSlot = (roundKey, matchIdx, slotIdx) => {
  const match = rounds.value[roundKey][matchIdx]
  match[slotIdx] = null
  match[2] = null
  broadcastBracket()
}

async function update7toSmokeMatch(winner) {
  if (!Array.isArray(rounds.value)) return
  if (winner === -1) {
    // Tie: both battlers go to the back of the queue
    const [first, second, ...rest] = rounds.value
    rounds.value = [...rest, first, second]
  } else if (winner === 0) {
    const arr = [...rounds.value]
    const second = arr.splice(1, 1)[0]
    arr[0] = { ...arr[0], score: arr[0].score + 1 }
    rounds.value = [...arr, second]
  } else if (winner === 1) {
    const arr = [...rounds.value]
    const first = arr.shift()
    arr[0] = { ...arr[0], score: arr[0].score + 1 }
    rounds.value = [...arr, first]
  }
  await updateSmokePair()
  currentWinner.value = -2
}

function setWinner(roundKey, matchIdx, slotIdx) {
  const match = rounds.value[roundKey][matchIdx]
  const winner = match[slotIdx]
  match[2] = winner
  const roundIndex = roundSizes.value.indexOf(parseInt(roundKey.replace("Top", "")))
  const nextRoundSize = roundSizes.value[roundIndex + 1]
  if (!nextRoundSize) { broadcastBracket(); return }
  const nextRoundKey = `Top${nextRoundSize}`
  const nextMatchIdx = Math.floor(matchIdx / 2)
  const nextSlotIdx = matchIdx % 2
  rounds.value[nextRoundKey][nextMatchIdx][nextSlotIdx] = winner
  broadcastBracket()
}

function clearWinner(roundKey, matchIdx) {
  const match = rounds.value[roundKey][matchIdx]
  const roundIndex = roundSizes.value.indexOf(parseInt(roundKey.replace("Top", "")))
  const nextRoundSize = roundSizes.value[roundIndex + 1]
  if (nextRoundSize) {
    const nextMatchIdx = Math.floor(matchIdx / 2)
    const nextSlotIdx = matchIdx % 2
    rounds.value[`Top${nextRoundSize}`][nextMatchIdx][nextSlotIdx] = null
  }
  match[2] = null
  broadcastBracket()
}


const uniqueGenres = computed(() => {
  const genres = participants.value.map(p => p.genreName)
  return [...new Set(genres)].sort()
})

const currentGenreChampion = computed(() => {
  if (isSmoke.value) return null
  return rounds.value['Top2']?.[0]?.[2] ?? null
})

const isFinalInProgress = computed(() => !isSmoke.value && currentTop.value === 'Top2')

// True when the current bracket has any placed participants (used for size-change guard)
const bracketHasData = computed(() => {
  if (isSmoke.value) return Array.isArray(rounds.value) && rounds.value.some(r => r?.name)
  return Object.values(rounds.value).some(pairList =>
    Array.isArray(pairList) && pairList.some(m => Array.isArray(m) && (m[0] || m[1]))
  )
})

// Round tab status: 'active' (has current battle), 'done' (all winners set),
// 'filled' (all slots filled + previous round complete), 'locked' (waiting for
// previous round to finish), 'empty' (slots not yet filled)
const _roundTabStatus = (idx) => {
  if (isSmoke.value) return 'empty'
  const size = roundSizes.value[idx]
  if (!size) return 'empty'
  const pairList = rounds.value[`Top${size}`]
  if (!Array.isArray(pairList)) return 'empty'
  // Active battle in this round?
  const hasActive = currentBattle.value.length > 0 && currentTop.value === `Top${size}`
  if (hasActive) return 'active'
  // All winners set? (round completed)
  const allHaveWinners = pairList.every(m => Array.isArray(m) && m[2])
  if (allHaveWinners && pairList.length > 0 && pairList.some(m => m[0] || m[1])) return 'done'
  // Previous round incomplete? This round is locked
  if (idx > 0) {
    const prevSize = roundSizes.value[idx - 1]
    const prevList = rounds.value[`Top${prevSize}`]
    if (Array.isArray(prevList) && prevList.length > 0 && !prevList.every(m => Array.isArray(m) && m[2])) {
      return 'locked'
    }
  }
  // All slots filled? Ready to start
  const allFilled = pairList.every(m => Array.isArray(m) && m[0] && m[1])
  if (allFilled) return 'filled'
  return 'empty'
}

// True when every match in the active round tab has both slots filled
// AND the previous round (if any) is fully complete (all winners set).
const isActiveRoundFilled = computed(() => {
  if (isSmoke.value) return true
  const idx = activeRoundIdx.value
  const size = roundSizes.value[idx]
  if (!size) return false
  // Current round: all slots filled
  const pairList = rounds.value[`Top${size}`]
  if (!Array.isArray(pairList)) return false
  if (!pairList.every(m => Array.isArray(m) && m[0] && m[1])) return false
  // Previous round (if any): all winners set
  if (idx > 0) {
    const prevSize = roundSizes.value[idx - 1]
    const prevList = rounds.value[`Top${prevSize}`]
    if (Array.isArray(prevList) && !prevList.every(m => Array.isArray(m) && m[2])) return false
  }
  return true
})

// All judges have cast a vote (none still at -3 = "hasn't voted this round")
const allJudgesVoted = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  return judges.length > 0 && judges.every(j => j.vote !== -3)
})

// Tentative winner from live judge votes — mirrors backend scoring logic
const tentativeWinner = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  if (judges.some(j => j.vote === -3)) return -2
  const leftWeight  = judges.filter(j => j.vote === 0).reduce((s, j) => s + (j.weightage ?? 1), 0)
  const rightWeight = judges.filter(j => j.vote === 1).reduce((s, j) => s + (j.weightage ?? 1), 0)
  if (leftWeight === rightWeight) return -1
  return leftWeight > rightWeight ? 0 : 1
})

// Show "Reveal Champion" in VOTING when all judges voted + clear winner (final only)
const showFinalReveal = computed(() =>
  battlePhase.value === 'VOTING' &&
  isFinalInProgress.value &&
  allJudgesVoted.value &&
  tentativeWinner.value !== -1
)

const restoreAndBroadcastGenreBattle = async (_genre) => {
  // Reset local state, then fetch the real state from the backend.
  // switchActiveGenreService already broadcast /topic/battle/state — if the WS
  // message arrived before we reset refs, the diff guard would block re-hydration.
  // Clear lastAppliedState so hydrateFromState always applies on genre switch.
  lastAppliedState.value = ''

  currentBattle.value = []
  currentTop.value = ''
  currentRound.value = 0
  currentWinner.value = -2
  battlePhase.value = 'IDLE'

  // Fetch state from REST as immediate source; WS will keep it in sync thereafter
  const state = await getBattleState()
  if (state) hydrateFromState(state)

  // Also re-fetch judges
  const judges = await getBattleJudges()
  if (judges) battleJudges.value = judges
}

const jumpToRecoveredPair = async () => {
  if (!recoveryState.value) return
  markSaving()
  const { currentPair, currentRoundIndex, battlePhase: restoredPhase, bracket, champion: restoredChampion } = recoveryState.value

  // Restore topSize from DB bracket state
  if (bracket?.topSize !== undefined) {
    const restored = Number(bracket.topSize)
    if (!isNaN(restored) && restored !== Number(topSize.value)) {
      topSize.value = restored
    }
  }

  // Restore champion if the DB recorded one for this genre
  if (restoredChampion && selectedGenre.value) {
    genreChampions.value = { ...genreChampions.value, [selectedGenre.value]: restoredChampion }
  }

  // Restore local bracket and pair position first (no backend calls yet)
  if (!isSmoke.value && bracket?.rounds) {
    rounds.value = bracket.rounds
    // Find which round key contains this match
    let topKey = 'Top2'
    if (!currentPair.isFinal) {
      for (const key of Object.keys(bracket.rounds)) {
        const matchList = bracket.rounds[key]
        if (!Array.isArray(matchList)) continue
        if (matchList.some(m => m[0] === currentPair.left && m[1] === currentPair.right)) {
          topKey = key
          break
        }
      }
    }
    currentTop.value = topKey
    const pairList = bracket.rounds[topKey] ?? []
    // Find pair by name — currentRoundIndex can be stale if broadcastBracket wasn't called
    const nameIdx = pairList.findIndex(m => m[0] === currentPair.left && m[1] === currentPair.right)
    const resolvedIdx = nameIdx >= 0 ? nameIdx : (currentRoundIndex ?? 0)
    currentRound.value = resolvedIdx
    currentBattle.value = [resolvedIdx, pairList]
    // Restore the round tab to match the recovered currentTop
    const recoveredSize = parseInt(topKey.replace('Top', ''))
    const recoveredIdx = roundSizes.value.indexOf(recoveredSize)
    if (recoveredIdx >= 0) { activeRoundIdx.value = recoveredIdx; viewedRoundIdx.value = recoveredIdx }
  }

  // REVEALED: backend already has the correct state loaded from DB on startup.
  // Calling setBattlePair would force LOCKED and broadcast it to all connected devices.
  if (restoredPhase === 'REVEALED') {
    battlePhase.value = 'REVEALED'
    const match = rounds.value[currentTop.value]?.[currentRound.value]
    if (match?.[2]) {
      currentWinner.value = match[2] === currentBattlePair.value?.[0] ? 0 : 1
    }
    markSaved()
    return
  }

  // Non-REVEALED: re-broadcast pair and phase to overlay and judges
  if (!isSmoke.value && bracket?.rounds) {
    await setBattlePair(
      currentPair.left, currentPair.right, currentPair.isFinal,
      currentPair.leftMembers?.length ? currentPair.leftMembers : getMembersFor(currentPair.left),
      currentPair.rightMembers?.length ? currentPair.rightMembers : getMembersFor(currentPair.right)
    )
  } else {
    // Smoke or no bracket data — just re-broadcast the pair
    await setBattlePair(currentPair.left, currentPair.right, false,
      getMembersFor(currentPair.left), getMembersFor(currentPair.right))
  }

  const phase = restoredPhase && restoredPhase !== 'IDLE' ? restoredPhase : 'LOCKED'
  await setBattlePhase(phase)
  battlePhase.value = phase
  markSaved()
}

let judgeSyncing = false  // prevents concurrent syncJudgesForGenre calls

// Called when genre switches: loads the new genre's judges from backend.
// Judges are persisted per-genre in the DB via battle_genre_state; setActiveGenre
// already loaded the correct set on the backend. No localStorage needed.
const syncJudgesForGenre = async (_newGenre, _prevGenre) => {
  if (judgeSyncing) return
  judgeSyncing = true
  try {
    battleJudges.value = await getBattleJudges()
    syncJudgeVoteSubscriptions()
  } finally {
    judgeSyncing = false
  }
}

const submitAddBattleJudge = async (name) => {
  const j = allJudges.value.find(j => j.judgeName === name)
  const res = await addBattleJudge(j?.judgeId)
  if (res.status === 404) console.log("No judge in database")
  battleJudges.value = await getBattleJudges()

}

const submitUpdateJudgeWeightage = async (id, value) => {
  const weightage = Math.max(1, parseInt(value) || 1)
  await updateJudgeWeightage(id, weightage)
  battleJudges.value = await getBattleJudges()

}

const submitRemoveBattleJudge = async (name) => {
  const j = allJudges.value.find(j => j.judgeName === name)
  await removeBattleJudge(j?.judgeId)
  battleJudges.value = await getBattleJudges()

}

const sortedJudgesForToggle = computed(() => {
  const activeMap = new Map((battleJudges.value?.judges ?? []).map(j => [j.name, j]))
  const all = Object.values(allJudges.value).map(j => {
    const active = activeMap.get(j.judgeName)
    return { name: j.judgeName, active: !!active, id: active?.id ?? null, weightage: active?.weightage ?? 1 }
  })
  return all.sort((a, b) => Number(b.active) - Number(a.active))
})

const toggleBattleJudge = (judgeName) => {
  const active = (battleJudges.value?.judges ?? []).some(j => j.name === judgeName)
  if (active) submitRemoveBattleJudge(judgeName)
  else submitAddBattleJudge(judgeName)
}

const fetchBattleGuests = async () => {
  if (!selectedEvent.value) return
  const res = await getBattleGuests(selectedEvent.value)
  if (res?.ok) battleGuests.value = await res.json()
}

const submitAddBattleGuest = async () => {
  if (!newGuestName.value.trim() || (!isSmoke.value && !newGuestEntryRound.value) || !selectedGenre.value) return
  addingGuest.value = true
  const memberNames = newGuestMembers.value.split(',').map(s => s.trim()).filter(Boolean)
  const entryRound = isSmoke.value ? `Top${topSize.value}` : newGuestEntryRound.value
  const res = await addBattleGuest(selectedEvent.value, selectedGenre.value, newGuestName.value.trim(), entryRound, memberNames)
  if (res?.ok) {
    const guest = await res.json()
    battleGuests.value.push(guest)
    // Add guest members to memberLookup so getMembersFor works immediately
    if (guest.memberNames?.length) {
      memberLookup.value = { ...memberLookup.value, [guest.guestName]: guest.memberNames }
    }
    newGuestName.value = ''
    newGuestEntryRound.value = ''
    newGuestMembers.value = ''
    placeGuestsInBracket()
  }
  addingGuest.value = false
}

const submitRemoveBattleGuest = async (guest) => {
  await removeBattleGuest(guest.id)
  battleGuests.value = battleGuests.value.filter(g => g.id !== guest.id)
  if (isSmoke.value && Array.isArray(rounds.value)) {
    for (const slot of rounds.value) {
      if (slot?.name === guest.guestName) slot.name = null
    }
  } else {
    for (const roundKey of Object.keys(rounds.value)) {
      if (!Array.isArray(rounds.value[roundKey])) continue
      for (const match of rounds.value[roundKey]) {
        if (!Array.isArray(match)) continue
        if (match[0] === guest.guestName) match[0] = null
        if (match[1] === guest.guestName) match[1] = null
      }
    }
  }
  broadcastBracket()
}

const submitGetScore = async () => {
  battleJudges.value = await getBattleJudges()
  const hasMinusThree = battleJudges?.value.judges.some(j => j.vote === -3)
  if (hasMinusThree) { currentWinner.value = -3; return }
  if (isSmoke.value) {
    const res = await setBattleScore()
    const data = await res.json()
    currentWinner.value = Number(data.winner)
    await setBattlePhase('REVEALED')
    battlePhase.value = 'REVEALED'
    return
  }
  if (currentBattle.value.length === 0) return
  // Final match: if all judges voted with a clear winner, stop here.
  // showFinalReveal is now true → button switches to "Reveal Champion" for the organiser to click.
  if (isFinalInProgress.value && allJudgesVoted.value && tentativeWinner.value !== -1) return
  if (currentWinner.value === -1) {
    currentWinner.value = -2
    await resetJudgeVote()
    // Re-broadcast same pair so the overlay hides the judge panel and resets battler animations
    const [rLeft, rRight] = currentBattlePair.value ?? []
    if (rLeft && rRight) await setBattlePair(rLeft, rRight, isFinalInProgress.value, getMembersFor(rLeft), getMembersFor(rRight))
    return
  }
  const left = currentBattle?.value[1][currentBattle?.value[0]][0]
  const right = currentBattle?.value[1][currentBattle?.value[0]][1]
  if (left === "" || right === "") return
  const isFinal = !isSmoke.value && currentTop.value === 'Top2'
  const res = await setBattleScore(isFinal)
  if (res?.status === 409) {
    finalTieBlocked.value = true
    // Re-broadcast same pair → overlay/bracket transitions to LOCKED (rematch state)
    const [rLeft, rRight] = currentBattlePair.value ?? []
    if (rLeft && rRight) await setBattlePair(rLeft, rRight, isFinalInProgress.value, getMembersFor(rLeft), getMembersFor(rRight))
    return
  }
  const data = await res.json()
  finalTieBlocked.value = false
  currentWinner.value = Number(data.winner)
  if (data.winner === 1 || data.winner === 0) { setWinner(currentTop.value, currentRound.value, data.winner) }
}

const startRevote = async () => {
  await resetBattleVotes()
  await resetJudgeVote()   // reset to -3 so allJudgesVoted tracks the fresh round
  finalTieBlocked.value = false
  currentWinner.value = -2
}

const lockChampion = async () => {
  const winner = tentativeWinner.value === 0
    ? currentBattlePair.value?.[0]
    : currentBattlePair.value?.[1]
  if (!winner) return
  genreChampions.value = { ...genreChampions.value, [selectedGenre.value]: winner }
  await setBattlePhase('DECIDED', winner)
  battlePhase.value = 'DECIDED'

}

const unlockChampion = async () => {
  const { [selectedGenre.value]: _removed, ...rest } = genreChampions.value
  genreChampions.value = rest
  await setBattlePhase('VOTING')
  battlePhase.value = 'VOTING'

}

const revealChampionForGenre = async () => {
  // Case 1: DECIDED phase — champion locked, score not yet submitted to bracket.
  // Do NOT call setBattleScore here — judge votes may be stale after a page refresh.
  // We already know who won (genreChampions), so determine the side directly from the pair.
  if (battlePhase.value === 'DECIDED' && !currentGenreChampion.value) {
    const championName = genreChampions.value[selectedGenre.value]
    if (!championName) return
    const pair = currentBattlePair.value
    if (!pair) return
    const side = championName === pair[0] ? 0 : (championName === pair[1] ? 1 : -1)
    if (side === -1) return
    setWinner(currentTop.value, currentRound.value, side)
    currentWinner.value = side
    await revealChampion(selectedGenre.value, championName)
    // Stay in DECIDED so the genre remains re-revealable forever
    await setBattlePhase('DECIDED')
    battlePhase.value = 'DECIDED'
  
    revealActive.value = true
    return
  }
  // Case 2: score already in bracket (winner in rounds data) OR tracked in genreChampions
  // (re-reveal). Also stays DECIDED.
  const champion = currentGenreChampion.value ?? genreChampions.value[selectedGenre.value]
  if (!champion) return
  // Update live match winner display so the WIN button reflects the correct side
  if (currentBattlePair.value) {
    const side = champion === currentBattlePair.value[0] ? 0 : (champion === currentBattlePair.value[1] ? 1 : -1)
    if (side !== -1) currentWinner.value = side
  }
  await revealChampion(selectedGenre.value, champion)
  revealActive.value = true
}

const dismissReveal = async () => {
  await dismissChampionReveal()
  revealActive.value = false
}

const openVoting = async () => {
  markSaving()
  await setBattlePhase('VOTING')
  battlePhase.value = 'VOTING'

  markSaved()
}


const confirmResetBracket = async () => {
  rounds.value = initRounds()
  placeGuestsInBracket()
  broadcastBracket()
  await clearBattlePair()
  await setBattlePhase('IDLE')
  battlePhase.value = 'IDLE'
  currentWinner.value = -2
  currentBattle.value = []
  currentTop.value = ''
  currentRound.value = 0
  activeRoundIdx.value = 0
  viewedRoundIdx.value = 0
  finalTieBlocked.value = false

  // Clear champion tracking — both backend (DB) and local ref
  await dismissChampionReveal()
  revealActive.value = false
  const { [selectedGenre.value]: _removed, ...rest } = genreChampions.value
  genreChampions.value = rest
}

const requestSizeChange = (newSize) => {
  if (bracketHasData.value && Number(topSize.value) !== newSize) {
    pendingSize.value = newSize
    showSizeChangeConfirm.value = true
  } else {
    topSize.value = newSize
  }
}

const confirmSizeChange = async () => {
  showSizeChangeConfirm.value = false
  // Reset bracket data for the current genre before switching size
  topSize.value = pendingSize.value
  pendingSize.value = null
}

const cancelSizeChange = () => {
  showSizeChangeConfirm.value = false
  pendingSize.value = null
}

// True when the active battle is happening in the currently-viewed round tab.
// Smoke mode has only one "round" (the queue) — always true when battle is active.
const isActiveBattleInThisRound = computed(() => {
  if (currentBattle.value.length === 0) return false
  if (isSmoke.value) return true
  const size = roundSizes.value[activeRoundIdx.value]
  return size != null && currentTop.value === `Top${size}`
})

// Effective phase for the currently-viewed round: IDLE if the active battle
// is in a different round, otherwise the global phase.
const effectivePhase = computed(() =>
  isActiveBattleInThisRound.value ? battlePhase.value : 'IDLE'
)

const confirmRoundChange = () => {
  showRoundChangeConfirm.value = false
  activeRoundIdx.value = pendingRoundIdx.value
  viewedRoundIdx.value = pendingRoundIdx.value
  pendingRoundIdx.value = null
}

const cancelRoundChange = () => {
  showRoundChangeConfirm.value = false
  pendingRoundIdx.value = null
}

// Guard: block genre switch when battle is in progress
const requestGenreChange = (genre) => {
  if (genre === selectedGenre.value) return
  if (!canSwitchGenre.value) return  // hard block — tooltip on button explains why
  selectedGenre.value = genre
}

watch(selectedEvent, async (newVal, oldVal) => {
  if (newVal) {
    localStorage.setItem("selectedEvent", newVal)
    const [scoreRes, participantRes] = await Promise.all([
      getParticipantScore(newVal),
      getRegisteredParticipantsByEvent(newVal)
    ])
    participants.value = (scoreRes ?? []).sort((a, b) => b.score - a.score)
    const lookup = {}
    for (const p of (participantRes ?? [])) {
      if (p.memberNames && p.memberNames.length > 0) {
        lookup[p.participantName] = p.memberNames
      }
    }
    // Also load guest members (requires fetchBattleGuests first)
    const guestRes = await getBattleGuests(newVal)
    const guests = guestRes?.ok ? await guestRes.json() : []
    battleGuests.value = guests
    for (const g of guests) {
      if (g.memberNames?.length) lookup[g.guestName] = g.memberNames
    }
    memberLookup.value = lookup
    pickupCrews.value = []
    await fetchAllJudges(newVal)
    // When the event changes but the selected genre name stays the same (shared genre name
    // across events), watch(selectedGenre) never fires so setActiveGenre is never called.
    // Re-register with the backend here so state is loaded from the correct event's row.
    if (oldVal && selectedGenre.value) {
    
      await setActiveGenre(newVal, selectedGenre.value)
      await restoreAndBroadcastGenreBattle(selectedGenre.value)
    }
  }
}, { immediate: true })

watch(uniqueGenres, (genres) => {
  if (!selectedEvent.value || !genres?.length) return
  // Champions are loaded from backend via getBattleChampions() in onMounted
}, { immediate: true })

// When all judges revote to a tie in the final, clear any previously locked
// champion so stale data doesn't linger. (Champion is only saved via Lock button.)
watch(showFinalReveal, (newVal) => {
  if (!selectedGenre.value) return
  if (!newVal && isFinalInProgress.value && allJudgesVoted.value && tentativeWinner.value === -1) {
    const { [selectedGenre.value]: _removed, ...rest } = genreChampions.value
    genreChampions.value = rest
  }
})

// When showFinalReveal is already false (e.g. after a Start Round reset) and judges
// subsequently vote to a tie in the final, the watch above won't fire (no transition).
// This watcher catches the steady-state tie condition and clears the champion.
watch([allJudgesVoted, tentativeWinner], ([voted, winner]) => {
  if (!selectedGenre.value) return
  if (voted && winner === -1 && isFinalInProgress.value && genreChampions.value[selectedGenre.value]) {
    const { [selectedGenre.value]: _removed, ...rest } = genreChampions.value
    genreChampions.value = rest
  }
})

watch(selectedGenre, async (newVal, oldVal) => {
  // Dismiss before resetting revealActive — the check must happen while it's still true
  if (oldVal && revealActive.value) await dismissChampionReveal()
  revealActive.value = false
  if (newVal) {
    // Restore per-genre topSize — smoke auto-detection takes priority, otherwise
    // fetch from DB-backed state. Default to 16 on genre switch.
    const genreNeedsSmoke = newVal.toLowerCase().includes('7 to smoke') || newVal.toLowerCase().includes('7tosmoke')
    if (genreNeedsSmoke) {
      if (Number(topSize.value) !== 7) {
        topSize.value = 7
      }
    } else {
      // Restore per-genre topSize from DB-backed state. Default to 16 on genre switch.
      if (oldVal) {
        const state = await getBattleState()
        if (state?.bracket?.topSize !== undefined) {
          const dbSize = Number(state.bracket.topSize)
          if (!isNaN(dbSize) && dbSize !== Number(topSize.value)) {
            skipSizeChangeClear = true
            topSize.value = dbSize
          }
        } else {
          topSize.value = 16
        }
      }
    }
    localStorage.setItem("selectedGenre", newVal)
    rounds.value = initRounds()
    pickupCrews.value = await getPickupCrews(selectedEvent.value, newVal)
    placeGuestsInBracket()
    if (oldVal) {
      // Switch backend to new genre FIRST — persistActiveState() saves outgoing genre's state,
      // loadGenreStateIntoMemory() loads incoming genre's state, broadcastStateSnapshot() pushes
      // the full snapshot to all clients.
      await setActiveGenre(selectedEvent.value, newVal)
      // Restore local BattleControl UI from the backend state just loaded.
      // Do NOT call broadcastBracket() here — the DB already has the correct bracket data
      // from the last mutation, and pushing initRounds() would overwrite it.
      await restoreAndBroadcastGenreBattle(newVal)
    }
    // Sync per-genre judges on genre switch.
    // mountJudgeSyncDone guards against firing before onMounted loads battleJudges from API.
    // oldVal check (truthy) skips the immediate fire and empty-string initialization cases.
    if (mountJudgeSyncDone && oldVal) await syncJudgesForGenre(newVal, oldVal)
    // Re-read authoritative phase from backend after restoration. The WS LOCKED
    // message from setBattlePair can race past the local phase assignment inside
    // restoreAndBroadcastGenreBattle. Brief delay lets WS messages flush first.
    if (oldVal) {
      await new Promise(r => setTimeout(r, 150))
      const confirmed = await getBattlePhase()
      if (confirmed?.phase) battlePhase.value = confirmed.phase
      // Defensive: if this genre has a champion locked, force DECIDED regardless
      // of what the backend returned (WS messages can corrupt the phase mid-switch).
      if (genreChampions.value[newVal] && battlePhase.value !== 'DECIDED') {
        await setBattlePhase('DECIDED')
        battlePhase.value = 'DECIDED'
      }
    }
  } else {
    pickupCrews.value = []
  }
}, { immediate: true })


watch(topSize, async (newVal, oldVal) => {
  if (!newVal) return
  // Restore previously-selected round tab for this size; default to 0 (first round).
  // Only when event+genre are known — during setup they're still null, defer to onMounted.
  if (selectedEvent.value && selectedGenre.value) {
    activeRoundIdx.value = 0
    viewedRoundIdx.value = 0
  }
  rounds.value = initRounds()
  currentWinner.value = -2
  // Only clear battle on user-initiated size change (not programmatic from
  // genre switch or initial load). The onMounted recovery handles init.
  if (oldVal && !skipSizeChangeClear) {
    await clearBattlePair()
    await setBattlePhase('IDLE')
    battlePhase.value = 'IDLE'
    currentBattle.value = []
    currentTop.value = ''
  }
  skipSizeChangeClear = false
  // Guard against broadcasting before event/genre are known (initial load with null values
  // would send empty initRounds() to DB, corrupting the stored bracket).
  if (selectedEvent.value && selectedGenre.value) broadcastBracket()

  // State is restored from /topic/battle/state via hydrateFromState
  currentBattle.value = []
  currentTop.value = ''
  currentRound.value = 0
}, { immediate: true })

const wsClient = ref(null)
// Prevents watch-triggered syncJudgesForGenre from running before onMounted
// has loaded battleJudges from the API (timing race on initialiseDropdown)
let mountJudgeSyncDone = false

// Per-judge vote subscriptions for real-time allJudgesVoted tracking
// (individual votes go to /topic/battle/vote/{id}, not /topic/battle/judges)
const judgeVoteSubscriptions = {}

const syncJudgeVoteSubscriptions = () => {
  if (!wsClient.value?.connected) return
  const judges = battleJudges.value?.judges ?? []
  const liveIds = new Set(judges.map(j => String(j.id)))
  // Unsubscribe removed judges
  for (const [key, sub] of Object.entries(judgeVoteSubscriptions)) {
    if (!liveIds.has(key)) {
      sub.unsubscribe()
      delete judgeVoteSubscriptions[key]
    }
  }
  // Subscribe new judges
  for (const judge of judges) {
    const key = String(judge.id)
    if (!judgeVoteSubscriptions[key]) {
      judgeVoteSubscriptions[key] = wsClient.value.subscribe(
        `/topic/battle/vote/${judge.id}`,
        (raw) => {
          const msg = JSON.parse(raw.body)
          const j = battleJudges.value?.judges?.find(j => j.id === msg.judge)
          if (j) j.vote = msg.vote
        }
      )
    }
  }
}

// Re-sync vote subscriptions whenever judge list changes (add/remove)
watch(() => battleJudges.value?.judges?.length, syncJudgeVoteSubscriptions)

// Broadcast smoke list to overlay in real-time as operator assigns participants —
// fires on any smoke rounds mutation (drag/drop, add, clear) without waiting for Start Round.
let smokeListSyncTimer = null
watch(rounds, () => {
  if (!isSmoke.value || !Array.isArray(rounds.value) || rounds.value.length === 0) return
  clearTimeout(smokeListSyncTimer)
  smokeListSyncTimer = setTimeout(() => updateSmokeList(rounds.value), 250)
}, { deep: true })

onMounted(async () => {
  initialiseDropdown()
  // Tell the backend which event+genre is active on every load.
  // watch(selectedGenre) only calls setActiveGenre when the genre CHANGES (oldVal truthy),
  // so on initial load or after an event switch via EventSelector (which remounts this page),
  // the backend still points at the previous event. Calling it here ensures the correct
  // (eventName, genreName) DB row is loaded before any getBattleState/getBattlePhase reads.
  if (selectedEvent.value && selectedGenre.value) {
    await setActiveGenre(selectedEvent.value, selectedGenre.value)
  }
  // Round tab starts at 0 on mount
  activeRoundIdx.value = 0
  viewedRoundIdx.value = 0
  await fetchAllJudges(selectedEvent.value)
  await fetchBattleGuests()
  battleJudges.value = await getBattleJudges()
  mountJudgeSyncDone = true
  const savedConfig = await getOverlayConfig()
  if (savedConfig?.showImages !== undefined) overlayConfig.value = savedConfig
  if (selectedEvent.value) {
    const champions = await getBattleChampions(selectedEvent.value)
    // Merge: backend confirmed data takes precedence over initial ref value
    // backend confirmed. Backend wins on conflict (official record).
    if (champions && typeof champions === 'object') {
      genreChampions.value = { ...genreChampions.value, ...champions }
    }
  }
  const phaseData = await getBattlePhase()
  battlePhase.value = phaseData?.phase ?? 'IDLE'
  // Auto-restore from backend state on refresh — always check regardless of local state
  const battleState = await getBattleState()
  if (battleState?.battlePhase && battleState.battlePhase !== 'IDLE' && battleState.currentPair?.left) {
    recoveryState.value = battleState
    await jumpToRecoveredPair()   // restore silently
    showRecoveryBanner.value = true  // then notify the operator
  }
  wsClient.value = createClient()
  wsClient.value.onConnect = () => {
    // Subscribe to full state snapshots — used for initial hydration, genre switch, and reconnect recovery.
    // Diff logic prevents re-rendering already-current state.
    subscribeToChannel(wsClient.value, '/topic/battle/state', (msg) => {
      // Guard: ignore state broadcasts for a different genre (stale WS from genre switch)
      if (msg.genreName && msg.genreName !== selectedGenre.value) return
      hydrateFromState(msg)
      syncJudgeVoteSubscriptions()
    })

    // Phase subscription — keep for real-time phase transitions
    wsClient.value.subscribe('/topic/battle/phase', (raw) => {
      const msg = JSON.parse(raw.body)
      if (msg.genre && msg.genre !== selectedGenre.value) return
      battlePhase.value = msg.phase
      if (msg.phase === 'DECIDED' && msg.champion) {
        genreChampions.value = { ...genreChampions.value, [selectedGenre.value]: msg.champion }
      }
    })

    // Sync judge list from WS broadcasts — safe now that syncJudgesForGenre is a
    // single atomic getBattleJudges() call (no more remove+add loop causing
    // intermediate states). judgeSyncing guard prevents WS from racing with
    // the explicit fetch during genre switch.
    wsClient.value.subscribe('/topic/battle/judges', (raw) => {
      if (judgeSyncing) return
      const msg = JSON.parse(raw.body)
      if (msg?.judges) {
        battleJudges.value = { judges: msg.judges }
        syncJudgeVoteSubscriptions()
      }
    })
    syncJudgeVoteSubscriptions()
  }
  wsClient.value.activate()
  // After WS activation, hydrate from the same battleState already fetched above.
  // The WS /topic/battle/state subscription was registered in onConnect BEFORE activate().
  // This reuses the existing REST response to cover the gap before the first WS message arrives.
  if (selectedEvent.value && selectedGenre.value) {
    if (battleState) hydrateFromState(battleState)
  }
})

onUnmounted(() => {
  _cleanupDrag()
  for (const sub of Object.values(judgeVoteSubscriptions)) sub.unsubscribe?.()
  deactivateClient(wsClient.value)
})
</script>

<template>
  <div class="page-container relative">
    <div class="color-bleed"></div>
    <div class="relative z-10 space-y-6">

    <!-- Page header -->
    <div>
      <div class="type-page-title">Battle Control</div>
      <p class="type-label text-content-muted mt-1">Manage brackets, rounds, and live voting</p>
    </div>

    <!-- Quick access links -->
    <div v-if="isAdminOrOrganiser" class="flex flex-wrap gap-2">
      <a
        href="/battle/overlay"
        target="_blank"
        rel="noopener noreferrer"
        class="para-chip-sm inline-flex items-center gap-2 px-4 py-2.5 type-body text-content-secondary hover:text-accent hover:border-[color:var(--accent-muted)] transition-all duration-200"
      >
        <i class="pi pi-desktop text-xs"></i>
        Stream Overlay
        <i class="pi pi-external-link text-xs text-content-muted"></i>
      </a>
      <a
        href="/battle/overlay?isSmoke=true"
        target="_blank"
        rel="noopener noreferrer"
        class="para-chip-sm inline-flex items-center gap-2 px-4 py-2.5 type-body text-content-secondary hover:text-accent hover:border-[color:var(--accent-muted)] transition-all duration-200"
      >
        <i class="pi pi-desktop text-xs"></i>
        Smoke Overlay
        <i class="pi pi-external-link text-xs text-content-muted"></i>
      </a>
      <a
        href="/battle/judge"
        target="_blank"
        rel="noopener noreferrer"
        class="para-chip-sm inline-flex items-center gap-2 px-4 py-2.5 type-body text-content-secondary hover:text-accent hover:border-[color:var(--accent-muted)] transition-all duration-200"
      >
        <i class="pi pi-users text-xs"></i>
        Judge View
        <i class="pi pi-external-link text-xs text-content-muted"></i>
      </a>
      <a
        href="/battle/bracket"
        target="_blank"
        rel="noopener noreferrer"
        class="para-chip-sm inline-flex items-center gap-2 px-4 py-2.5 type-body text-content-secondary hover:text-accent hover:border-[color:var(--accent-muted)] transition-all duration-200"
      >
        <i class="pi pi-sitemap text-xs"></i>
        Live Bracket
        <i class="pi pi-external-link text-xs text-content-muted"></i>
      </a>
    </div>

    <!-- Recovery banner -->
    <Transition name="recovery-fade">
      <div
        v-if="showRecoveryBanner && recoveryState"
        class="recovery-banner"
        role="alert"
        aria-live="polite"
      >
        <div class="recovery-body">
          <span class="recovery-dot"></span>
          <div class="recovery-text">
            <span class="recovery-title">RESTORED</span>
            <span class="recovery-detail">{{ recoveryState.currentPair?.left ?? '???' }} VS {{ recoveryState.currentPair?.right ?? '???' }}</span>
          </div>
        </div>
        <div class="recovery-actions">
          <button class="recovery-btn recovery-btn-dismiss" @click="showRecoveryBanner = false">
            DISMISS
          </button>
        </div>
      </div>
    </Transition>

    <!-- NOTE: Genre switcher is rendered inside LiveMatchPanel below -->

    <!-- Setup panel — visible only to Admin/Organiser -->
    <div v-if="isAdminOrOrganiser" class="card overflow-hidden">
      <!-- Header — always visible, click to expand/collapse -->
      <div
        class="flex items-center justify-between px-5 py-3 cursor-pointer select-none"
        :class="setupLocked ? 'border-b border-surface-600/40' : (setupExpanded ? 'border-b border-surface-600/40' : '')"
        @click="setupExpanded = !setupExpanded"
      >
        <div class="flex items-center gap-3">
          <span class="type-label text-content-secondary" style="letter-spacing:0.18em">SETUP</span>
          <span
            v-if="setupLocked"
            class="inline-flex items-center gap-1.5 px-2.5 py-1 type-label"
            style="font-size:10px;letter-spacing:0.14em;background:rgba(245,158,11,0.08);border:1px solid rgba(245,158,11,0.25);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
          >
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400" style="box-shadow:0 0 6px rgba(245,158,11,0.7)"></span>
            <span class="text-amber-400">LOCKED · BATTLE IN PROGRESS</span>
          </span>
        </div>
        <i class="pi text-content-muted transition-transform duration-200" :class="setupExpanded ? 'pi-chevron-up' : 'pi-chevron-down'" style="font-size:11px"></i>
      </div>

      <!-- Collapsible content -->
      <div v-show="setupExpanded" class="p-5">

      <!-- Locked banner -->
      <div
        v-if="setupLocked"
        class="mb-4 px-4 py-3 flex items-center gap-3"
        style="border-left:3px solid rgba(245,158,11,0.6);background:rgba(245,158,11,0.06)"
      >
        <span class="type-label text-amber-400" style="font-size:11px;letter-spacing:0.16em">Setup locked — Reset Bracket to modify</span>
      </div>

      <div class="flex flex-wrap items-center gap-3 mb-4">
        <!-- Event name -->
        <span class="font-heading font-bold text-base text-content-primary whitespace-nowrap">{{ selectedEvent }}</span>
        <!-- Format toggle — hidden for smoke genres and when locked -->
        <template v-if="!isGenreSmoke && !setupLocked">
          <span class="text-surface-600 select-none">|</span>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="s in sizes.filter(s => s !== 7)"
              :key="s"
              @click="requestSizeChange(s)"
              class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1.5 type-label transition-all duration-150"
              :class="topSize === s
                ? 'text-accent border-[color:var(--accent-muted)]'
                : 'text-content-muted hover:text-content-primary'"
            >Top {{ s }}</button>
          </div>
        </template>
      </div>

      <!-- Judge management -->
      <div class="section-rule mt-4">
        <span class="section-rule-label">Judges</span>
        <div class="section-rule-line"></div>
      </div>

      <!-- Toggle buttons when unlocked; read-only cards when locked -->
      <div class="mt-3">
        <template v-if="!setupLocked">
          <div class="flex flex-wrap gap-2">
            <div
              v-for="j in sortedJudgesForToggle"
              :key="j.name"
              @click="toggleBattleJudge(j.name)"
              class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1.5 type-label inline-flex items-center gap-1.5 transition-all duration-150 cursor-pointer select-none"
              :class="j.active
                ? 'text-accent border-[color:var(--accent-muted)] bg-[color:var(--accent-subtle)]'
                : 'text-content-muted/40 hover:text-content-muted border-surface-600/30'"
            >
              <i v-if="j.active" class="pi pi-check text-[9px]"></i>
              {{ j.name }}
              <template v-if="j.active">
                <span class="type-label text-content-muted ml-1" style="font-size:9px;letter-spacing:0.12em;opacity:0.7">WT</span>
                <input
                  type="number"
                  :value="j.weightage"
                  min="1"
                  @click.stop
                  @change="e => submitUpdateJudgeWeightage(j.id, e.target.value)"
                  class="w-8 bg-surface-900 border border-surface-600/60 text-accent text-center type-body"
                  style="padding:1px 2px;font-size:11px;clip-path:polygon(3px 0%,100% 0%,calc(100% - 3px) 100%,0% 100%)"
                />
              </template>
            </div>
            <span v-if="!sortedJudgesForToggle.length" class="type-label text-content-muted">No judges available</span>
          </div>
        </template>
        <template v-else>
          <div class="flex flex-wrap gap-3">
            <div
              v-for="(j, index) in battleJudges?.judges || []"
              :key="index"
              class="card-hover p-2 relative inline-flex items-center gap-2 px-3"
            >
              <div class="corner-bar-tl"></div>
              <span class="type-body text-content-primary">{{ j.name }}</span>
              <div class="flex items-center gap-1">
                <span class="type-label text-content-muted" style="font-size:9px;letter-spacing:0.12em">WT</span>
                <span class="type-body text-content-muted" style="font-size:12px;min-width:2.5rem;text-align:center">{{ j.weightage ?? 1 }}</span>
              </div>
            </div>
            <span v-if="!battleJudges?.judges?.length" class="type-label text-content-muted">None added</span>
          </div>
        </template>
      </div>

      <template v-if="!setupLocked">
      <div class="section-rule">
        <span class="section-rule-label">Seeding</span>
        <div class="section-rule-line"></div>
      </div>

      <!-- Seeding controls: stacked on mobile, inline on sm+ -->
      <div class="flex flex-col sm:flex-row sm:flex-wrap sm:items-center gap-3 sm:gap-2 mt-4 mb-5">
        <!-- Pickup crew sort toggle (mixed bracket only) -->
        <template v-if="isMixedBracket">
          <div class="flex gap-2 sm:gap-1">
            <button
              @click="crewSortMode = 'leader'"
              class="para-chip-sm px-3 sm:px-2.5 py-3 sm:py-1.5 type-label transition-all flex-1 sm:flex-none"
              :class="crewSortMode === 'leader' ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
              title="Sort pickup crews by their leader's individual audition score"
            >Leader</button>
            <button
              @click="crewSortMode = 'avg'"
              class="para-chip-sm px-3 sm:px-2.5 py-3 sm:py-1.5 type-label transition-all flex-1 sm:flex-none"
              :class="crewSortMode === 'avg' ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
              title="Sort pickup crews by average score of all members"
            >Avg</button>
          </div>
          <span class="text-surface-600 select-none hidden sm:inline">|</span>
        </template>

        <div class="flex flex-wrap gap-2 sm:gap-1">
          <button
            @click="autoFillSeeds"
            class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1.5 type-label inline-flex items-center gap-1 transition-all text-content-muted hover:text-content-primary"
            :title="rankAsc ? 'Lowest score first' : 'Highest score first'"
          >
            <i :class="rankAsc ? 'pi pi-sort-amount-up' : 'pi pi-sort-amount-down'" class="text-xs"></i>
            By Rank
            <span
              @click.stop="rankAsc = !rankAsc; autoFillSeeds()"
              class="ml-0.5 px-1 py-0.5 type-label text-accent cursor-pointer"
              :title="rankAsc ? 'Switch to highest first' : 'Switch to lowest first'"
            >{{ rankAsc ? '↑' : '↓' }}</span>
          </button>
          <button
            @click="highVsLowFill"
            :disabled="guestsForCurrentGenre.length > 0"
            class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1.5 type-label inline-flex items-center gap-1 transition-all"
            :class="guestsForCurrentGenre.length > 0 ? 'opacity-30 cursor-not-allowed text-content-muted' : 'text-content-muted hover:text-content-primary'"
            :title="guestsForCurrentGenre.length > 0 ? 'Disabled: bracket has pinned guests' : 'Pair highest with lowest (1st vs last, 2nd vs 2nd-last...)'"
          >
            <i class="pi pi-arrows-v text-xs"></i>
            High ↔ Low
          </button>
          <button
            @click="randomFill"
            class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1.5 type-label inline-flex items-center gap-1 text-content-muted hover:text-content-primary transition-all"
            title="Random shuffle"
          >
            <i class="pi pi-refresh text-xs"></i>
            Random
          </button>
          <button
            v-if="isMixedBracket"
            @click="splitBracketFill"
            class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1.5 type-label inline-flex items-center gap-1 text-accent transition-all"
            title="Pre-formed teams on left half, pickup crews on right half"
          >
            <i class="pi pi-table text-xs"></i>
            Split
          </button>
        </div>
      </div>

      <template v-if="!setupLocked || guestsForCurrentGenre.length > 0">
      <div class="section-rule">
        <span class="section-rule-label">Battle Guests</span>
        <div class="section-rule-line"></div>
      </div>

      <div class="flex flex-wrap items-start gap-3 mt-3 mb-5">
        <!-- Guest slots -->
        <div class="flex flex-wrap gap-2 flex-1 min-w-0">
          <span
            v-for="g in guestsForCurrentGenre"
            :key="g.id"
            class="card-hover relative inline-flex items-stretch gap-0 pr-0 overflow-hidden"
            style="padding: 0"
          >
            <div class="corner-bar-tl"></div>
            <!-- info block -->
            <div class="flex items-start gap-1.5 px-3 py-2">
              <i class="pi pi-star text-accent flex-shrink-0 mt-0.5" style="font-size:0.6rem"></i>
              <div class="min-w-0">
                <div class="flex items-center gap-1.5">
                  <span class="type-body text-content-primary">{{ g.guestName }}</span>
                  <span v-if="!isSmoke" class="type-label text-content-muted">→ {{ g.entryRound }}</span>
                </div>
                <div
                  v-if="g.memberNames?.length"
                  class="type-label text-content-muted normal-case mt-0.5"
                  style="font-size:11px;letter-spacing:0.04em;opacity:0.7"
                >{{ g.memberNames.join(' · ') }}</div>
              </div>
            </div>
            <!-- remove button — hidden when locked -->
            <button
              v-if="!setupLocked"
              @click="submitRemoveBattleGuest(g)"
              class="flex items-center justify-center px-2.5 flex-shrink-0 border-l border-surface-600/40 text-surface-400 hover:text-red-400 hover:bg-red-500/10 transition-colors"
              title="Remove guest"
            >
              <i class="pi pi-times" style="font-size:11px"></i>
            </button>
          </span>
          <span v-if="!guestsForCurrentGenre.length" class="type-label text-content-muted pt-1">None added</span>
        </div>

        <!-- Add guest form — hidden when locked -->
        <div v-if="!setupLocked" class="ml-auto flex flex-col gap-2 flex-shrink-0">
          <div class="flex items-center gap-2">
            <input
              v-model="newGuestName"
              type="text"
              :placeholder="isTeamGenre && !isSmoke ? 'Team name' : 'Guest name'"
              class="input-base w-64"
              @keyup.enter="submitAddBattleGuest"
            />
            <select
              v-if="!isSmoke"
              v-model="newGuestEntryRound"
              class="input-base w-24"
            >
              <option value="" disabled>Round</option>
              <option v-for="r in entryRoundOptions" :key="r" :value="r">{{ r }}</option>
            </select>
            <button
              @click="submitAddBattleGuest"
              :disabled="addingGuest || !newGuestName.trim() || (!isSmoke && !newGuestEntryRound)"
              class="bg-accent para-chip-sm px-3 py-1.5 type-label transition-all disabled:opacity-40 disabled:cursor-not-allowed whitespace-nowrap"
            >
              <i class="pi pi-plus text-xs"></i>
              Add
            </button>
          </div>
          <input
            v-if="isTeamGenre && !isSmoke"
            v-model="newGuestMembers"
            type="text"
            placeholder="Member names (e.g. Alice, Bob)"
            class="input-base w-full"
            @keyup.enter="submitAddBattleGuest"
          />
        </div>
      </div>
      </template><!-- end v-if for Battle Guests -->

      <!-- ── Seeding Pool ──────────────────────────────── -->
      <div v-if="!setupLocked" class="mb-5">
        <div class="section-rule">
          <span class="section-rule-label">Seeding Pool</span>
          <span v-if="guestsForCurrentGenre.length" class="type-label text-content-muted ml-2">
            · {{ guestsForCurrentGenre.length }} guest slot{{ guestsForCurrentGenre.length > 1 ? 's' : '' }} reserved · {{ bracketSize - guestsForCurrentGenre.length }} {{ isSmoke ? 'queue slots' : 'seed slots' }} shown
          </span>
          <div class="section-rule-line"></div>
        </div>

        <!-- Mobile: 2-col grid for easy tap; Tablet+: flex wrap -->
        <div class="grid grid-cols-2 sm:flex sm:flex-wrap gap-2 sm:gap-1.5 mt-3 min-h-[28px]">
          <span v-if="!poolParticipants.length" class="col-span-2 type-label text-content-muted">
            {{ isSmoke ? `All ${bracketSize - guestsForCurrentGenre.length} queue slots filled` : `All top ${bracketSize - guestsForCurrentGenre.length} participants placed in bracket` }}
          </span>
          <span
            v-for="p in poolParticipants" :key="p.name"
            :data-drag-name="p.name"
            @pointerdown="(e) => onPointerDragStart('pool', p.name, e)"
            class="para-chip-sm px-3 sm:px-2.5 py-3 sm:py-1 type-label text-content-primary cursor-grab active:cursor-grabbing select-none inline-flex items-center justify-between gap-1.5"
            :class="poolDragName === p.name ? 'opacity-40' : ''"
            :title="p.name"
            style="touch-action: none; user-select: none; min-height: 44px;"
          >
            <span class="truncate">{{ p.name }}</span>
            <span class="text-content-muted flex-shrink-0" style="font-size:11px;letter-spacing:0.05em;opacity:0.7">{{ p.score % 1 === 0 ? p.score : p.score.toFixed(1) }}</span>
          </span>
        </div>
      </div>
      </template><!-- end v-if="!setupLocked" for Seeding block -->

      <div class="section-rule">
        <span class="section-rule-label">Bracket</span>
        <div class="section-rule-line"></div>
      </div>

      <!-- ── Standard bracket ──────────────────────────── -->
      <div v-if="Number(topSize) !== 7" class="mt-3">
        <!-- Active round matches -->
        <template v-for="(size, idx) in roundSizes" :key="idx">
          <div v-if="activeRoundIdx === idx" class="card p-4">
            <div class="flex flex-col gap-2 mb-3">
              <!-- Match card: horizontal Left VS Right layout -->
              <div
                v-for="(match, mIdx) in rounds[`Top${size}`]"
                :key="mIdx"
                class="card-hover p-3 relative flex flex-col sm:flex-row items-stretch"
                :style="isActivePair(match) && effectivePhase !== 'IDLE'
                  ? 'border-left: 3px solid var(--accent-color); background: var(--accent-subtle); box-shadow: 0 0 0 1px var(--accent-muted), 0 0 18px var(--accent-subtle);'
                  : ''"
              >
                <div class="corner-bar-tl"></div>
                <!-- Slot 0 — full-width on mobile, left half on sm+ -->
                <div
                  class="flex-1 min-w-0 flex items-center gap-1.5 px-2 py-2 transition-all duration-150"
                  :data-drop-key="`bracket-Top${size}-${mIdx}-0`"
                  :class="[
                    match[2] === match[0] && match[0] ? 'bg-emerald-500/10' : '',
                    dragSource?.roundKey === `Top${size}` && dragSource?.matchIdx === mIdx && dragSource?.slotIdx === 0
                      ? 'ring-2 ring-primary-400/80 bg-primary-400/12 shadow-inner'
                      : dragOverKey === `Top${size}-${mIdx}-0` ? 'bg-primary-500/15 ring-2 ring-inset ring-primary-500/70' : ''
                  ]"
                >
                  <i class="pi pi-crown text-xs flex-shrink-0 transition-colors" :class="match[2] === match[0] && match[0] ? 'text-amber-400' : 'text-surface-600'"></i>
                  <!-- Name + members: stacked on mobile, inline on sm+ -->
                  <div v-if="match[0]"
                    @pointerdown="(e) => onPointerDragStart('bracket', { roundKey: `Top${size}`, matchIdx: mIdx, slotIdx: 0 }, e)"
                    class="flex-1 min-w-0 select-none flex flex-col sm:flex-row sm:items-center sm:flex-wrap gap-0.5 sm:gap-x-1.5"
                    :class="[!setupLocked ? 'cursor-grab active:cursor-grabbing' : '', match[2] === match[0] && match[0] ? 'text-emerald-400' : 'text-content-primary']"
                    style="touch-action: none;"
                  >
                    <div class="flex items-center gap-1 min-w-0">
                      <span class="type-body break-words">{{ match[0] }}</span>
                      <span v-if="isGuestSlot(match[0])" class="flex-shrink-0 inline-flex items-center gap-0.5 px-1.5 py-px text-amber-400 bg-amber-500/20 border border-amber-500/50 rounded" style="font-size:9px;font-weight:700;letter-spacing:0.1em"><i class="pi pi-star" style="font-size:7px"></i>GUEST</span>
                    </div>
                    <div v-if="getMembersFor(match[0]).length" class="flex flex-wrap gap-1">
                      <span
                        v-for="m in getMembersFor(match[0])" :key="m"
                        class="inline-block px-2 py-0.5 normal-case flex-shrink-0"
                        :class="match[2] === match[0] ? 'bg-emerald-500/15 text-emerald-400/80' : 'bg-surface-700/60 text-content-muted'"
                        style="font-size:10px;letter-spacing:0.04em;clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
                      >{{ m }}</span>
                    </div>
                  </div>
                  <span v-else class="flex-1 type-body text-surface-600/60 italic">Drop here</span>
                  <button v-if="!setupLocked && match[0] && !isGuestSlot(match[0])" @click="clearSlot(`Top${size}`, mIdx, 0)" class="flex-shrink-0 px-1.5 py-1 text-surface-400 hover:text-red-400 hover:bg-red-500/10 rounded transition-colors" title="Clear slot"><i class="pi pi-times text-[10px]"></i></button>
                  <button
                    :disabled="!match[0]"
                    @click="match[2] === match[0] && match[0] ? clearWinner(`Top${size}`, mIdx) : requestWin(`Top${size}`, mIdx, 0, match[0])"
                    class="flex-shrink-0 w-10 sm:w-11 text-center rounded text-[10px] sm:text-[11px] font-bold transition-all disabled:opacity-20 disabled:cursor-not-allowed leading-5"
                    :class="match[2] === match[0] && match[0] ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 hover:bg-red-500/20 hover:text-red-400 hover:border-red-500/40' : 'bg-surface-700 text-surface-400 border border-surface-600/50 hover:border-surface-500'"
                  >{{ match[2] === match[0] && match[0] ? '✓' : 'Win' }}</button>
                </div>

                <!-- VS: horizontal line on mobile, vertical divider on sm+ -->
                <div class="sm:hidden flex items-center gap-2 px-2 py-0.5">
                  <div class="flex-1 h-px bg-surface-600/30"></div>
                  <span class="text-[9px] font-black text-surface-600 tracking-widest">VS</span>
                  <div class="flex-1 h-px bg-surface-600/30"></div>
                </div>
                <div class="hidden sm:flex items-center justify-center w-7 shrink-0 border-x border-surface-600/30 bg-surface-900/50">
                  <span class="text-[9px] font-black text-surface-600 tracking-widest">VS</span>
                </div>

                <!-- Slot 1 — full-width on mobile, right half on sm+ -->
                <div
                  class="flex-1 min-w-0 flex items-center gap-1.5 px-2 py-2 transition-all duration-150"
                  :data-drop-key="`bracket-Top${size}-${mIdx}-1`"
                  :class="[
                    match[2] === match[1] && match[1] ? 'bg-emerald-500/10' : '',
                    dragSource?.roundKey === `Top${size}` && dragSource?.matchIdx === mIdx && dragSource?.slotIdx === 1
                      ? 'ring-2 ring-primary-400/80 bg-primary-400/12 shadow-inner'
                      : dragOverKey === `Top${size}-${mIdx}-1` ? 'bg-primary-500/15 ring-2 ring-inset ring-primary-500/70' : ''
                  ]"
                >
                  <i class="pi pi-crown text-xs flex-shrink-0 transition-colors" :class="match[2] === match[1] && match[1] ? 'text-amber-400' : 'text-surface-600'"></i>
                  <!-- Name + members: stacked on mobile, inline on sm+ -->
                  <div v-if="match[1]"
                    @pointerdown="(e) => onPointerDragStart('bracket', { roundKey: `Top${size}`, matchIdx: mIdx, slotIdx: 1 }, e)"
                    class="flex-1 min-w-0 select-none flex flex-col sm:flex-row sm:items-center sm:flex-wrap gap-0.5 sm:gap-x-1.5"
                    :class="[!setupLocked ? 'cursor-grab active:cursor-grabbing' : '', match[2] === match[1] && match[1] ? 'text-emerald-400' : 'text-content-primary']"
                    style="touch-action: none;"
                  >
                    <div class="flex items-center gap-1 min-w-0">
                      <span class="type-body break-words">{{ match[1] }}</span>
                      <span v-if="isGuestSlot(match[1])" class="flex-shrink-0 inline-flex items-center gap-0.5 px-1.5 py-px text-amber-400 bg-amber-500/20 border border-amber-500/50 rounded" style="font-size:9px;font-weight:700;letter-spacing:0.1em"><i class="pi pi-star" style="font-size:7px"></i>GUEST</span>
                    </div>
                    <div v-if="getMembersFor(match[1]).length" class="flex flex-wrap gap-1">
                      <span
                        v-for="m in getMembersFor(match[1])" :key="m"
                        class="inline-block px-2 py-0.5 normal-case flex-shrink-0"
                        :class="match[2] === match[1] ? 'bg-emerald-500/15 text-emerald-400/80' : 'bg-surface-700/60 text-content-muted'"
                        style="font-size:10px;letter-spacing:0.04em;clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
                      >{{ m }}</span>
                    </div>
                  </div>
                  <span v-else class="flex-1 type-body text-surface-600/60 italic">Drop here</span>
                  <button v-if="!setupLocked && match[1] && !isGuestSlot(match[1])" @click="clearSlot(`Top${size}`, mIdx, 1)" class="flex-shrink-0 px-1.5 py-1 text-surface-400 hover:text-red-400 hover:bg-red-500/10 rounded transition-colors" title="Clear slot"><i class="pi pi-times text-[10px]"></i></button>
                  <button
                    :disabled="!match[1]"
                    @click="match[2] === match[1] && match[1] ? clearWinner(`Top${size}`, mIdx) : requestWin(`Top${size}`, mIdx, 1, match[1])"
                    class="flex-shrink-0 w-10 sm:w-11 text-center rounded text-[10px] sm:text-[11px] font-bold transition-all disabled:opacity-20 disabled:cursor-not-allowed leading-5"
                    :class="match[2] === match[1] && match[1] ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 hover:bg-red-500/20 hover:text-red-400 hover:border-red-500/40' : 'bg-surface-700 text-surface-400 border border-surface-600/50 hover:border-surface-500'"
                  >{{ match[2] === match[1] && match[1] ? '✓' : 'Win' }}</button>
                </div>
                <!-- Start from this match — desktop only, mobile has the global Start Round button -->
                <button
                  v-if="match[0] && match[1] && !match[2] && isActiveRoundFilled && effectivePhase === 'IDLE'"
                  @click="requestStartAt(`Top${size}`, rounds[`Top${size}`], mIdx)"
                  class="hidden sm:flex flex-shrink-0 items-center justify-center w-10 ml-1.5 self-stretch rounded text-accent border border-[color:var(--accent-muted)] bg-[color:var(--accent-subtle)] hover:bg-[color:var(--accent-muted)] transition-colors"
                  title="Start round from this match"
                ><i class="pi pi-play text-[10px]"></i></button>
              </div>
            </div>

            <button
              v-if="effectivePhase === 'IDLE'"
              :disabled="!isActiveRoundFilled"
              @click="requestStartAll(`Top${size}`, rounds[`Top${size}`])"
              class="w-full py-4 sm:py-2 para-chip type-label transition-all duration-200"
              :class="isActiveRoundFilled ? 'bg-accent' : 'bg-surface-700 text-content-muted cursor-not-allowed'"
              :title="isActiveRoundFilled ? '' : 'All slots must be filled and the previous round must be completed'"
            >
              <i class="pi pi-play text-xs mr-1.5"></i>
              Start Round
            </button>
            <div v-else class="w-full py-4 sm:py-2 text-center type-label text-content-muted">
              <template v-if="currentTop === `Top${size}`">Active battle in Top{{ size }}</template>
              <template v-else-if="rounds[`Top${size}`]?.every(m => Array.isArray(m) && m[2])">Round complete</template>
            </div>
          </div>
        </template>
      </div>

      <!-- ── 7 to Smoke bracket ──────────────────────────── -->
      <div v-else class="mt-3">
        <div class="section-rule mb-4">
          <span class="section-rule-label">7 to Smoke — Queue</span>
          <div class="section-rule-line"></div>
        </div>

        <!-- Queue: ordered chips, drag-to-reorder + pool drop — always shows all 8 slots -->
        <div v-if="Array.isArray(rounds)" class="flex flex-wrap gap-2 mb-4">
          <div
            v-for="(match, mIdx) in rounds"
            :key="mIdx"
            :data-drop-key="`smoke-${mIdx}`"
            @pointerdown="(e) => !!match.name && onPointerDragStart('smoke', mIdx, e)"
            class="card-hover relative flex items-stretch overflow-hidden transition-all duration-150"
            :class="dragOverKey === `smoke-${mIdx}` ? 'ring-2 ring-inset ring-primary-500/70 bg-primary-500/10' :
                    (dragSource?.smokeIdx === mIdx ? 'ring-2 ring-primary-400/80 bg-primary-400/12' : '')"
            style="padding:0; touch-action: none;"
          >
            <div class="corner-bar-tl"></div>
            <!-- position number -->
            <div class="flex items-center px-2 border-r border-surface-600/30 bg-surface-900/40">
              <span class="type-label text-accent select-none">{{ mIdx + 1 }}</span>
            </div>
            <!-- name + guest badge -->
            <div
              class="flex items-center gap-1.5 px-3 py-2 min-w-0 flex-1 select-none"
              :class="match.name ? 'cursor-grab active:cursor-grabbing' : ''"
            >
              <span class="type-body truncate" :class="match.name ? 'text-content-primary' : 'text-surface-600/50 italic'">{{ match.name || '—' }}</span>
              <span
                v-if="match.name && isGuestSlot(match.name)"
                class="flex-shrink-0 inline-flex items-center gap-0.5 px-1.5 py-px text-amber-400 bg-amber-500/20 border border-amber-500/50 rounded"
                style="font-size:9px;font-weight:700;letter-spacing:0.1em"
              ><i class="pi pi-star" style="font-size:7px"></i>GUEST</span>
            </div>
            <!-- clear button — only for filled non-guest slots -->
            <button
              v-if="!setupLocked && match.name && !isGuestSlot(match.name)"
              @click.stop="clearSmokeSlot(mIdx)"
              class="flex items-center justify-center px-2 flex-shrink-0 border-l border-surface-600/30 text-surface-400 hover:text-red-400 hover:bg-red-500/10 transition-colors"
              title="Clear slot"
            ><i class="pi pi-times" style="font-size:10px"></i></button>
          </div>
        </div>

        <button
          v-if="!setupLocked || currentBattle.length === 0"
          @click="initiateBattlePair(0, 0)"
          class="w-full py-2 bg-accent para-chip type-label transition-all duration-200"
        >
          <i class="pi pi-play text-xs mr-1.5"></i>
          Start Round
        </button>
      </div>

      <!-- ── Setup actions ──────────────────────────────── -->
      <div class="section-rule mt-5 mb-4">
        <span class="section-rule-label">Actions</span>
        <div class="section-rule-line"></div>
      </div>
      <div class="flex flex-wrap gap-2 mb-3">
        <!-- Reset Bracket: two-step inline confirm -->
        <template v-if="resetConfirmStep === 0">
          <button
            @click="resetConfirmStep = 1"
            class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 text-red-400 border-red-500/30 hover:border-red-500/60 transition-all"
          >
            <i class="pi pi-refresh text-xs"></i>
            Reset Bracket
          </button>
        </template>
        <template v-else>
          <div
            class="flex flex-wrap items-center gap-3 px-4 py-3 w-full"
            style="border-left:3px solid rgba(239,68,68,0.6);background:rgba(239,68,68,0.08)"
          >
            <span class="type-label text-red-400 flex-1" style="font-size:11px;letter-spacing:0.12em">
              Clear all bracket data for {{ selectedGenre }}? Cannot be undone.
            </span>
            <button
              @click="confirmResetBracket(); resetConfirmStep = 0"
              class="para-chip-sm px-3 py-1.5 type-label bg-red-600/20 text-red-400 border-red-500/40 hover:bg-red-600/30 transition-all whitespace-nowrap"
            >Confirm Reset</button>
            <button
              @click="resetConfirmStep = 0"
              class="para-chip-sm px-3 py-1.5 type-label text-content-muted hover:text-content-primary transition-all"
            >Cancel</button>
          </div>
        </template>

        <!-- File upload -->
        <label
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 cursor-pointer transition-all"
        >
          <i class="pi pi-upload text-xs"></i>
          Upload Images
          <input type="file" multiple @change="onFileChange" class="hidden" />
        </label>
      </div>

      <!-- Overlay Settings -->
      <details class="overlay-settings-panel mt-2">
        <summary class="overlay-settings-summary">Overlay Settings</summary>
        <div class="overlay-settings-body">
          <div class="overlay-setting-row">
            <span class="overlay-setting-label">Left Color</span>
            <div class="overlay-color-group">
              <input
                type="color"
                v-model="overlayConfig.leftColor"
                @change="pushOverlayConfig"
                class="overlay-color-swatch"
                title="Left team color"
              />
              <input
                type="text"
                v-model="overlayConfig.leftColor"
                @change="pushOverlayConfig"
                maxlength="7"
                placeholder="#dc2626"
                class="overlay-hex-input"
              />
            </div>
          </div>
          <div class="overlay-setting-row">
            <span class="overlay-setting-label">Right Color</span>
            <div class="overlay-color-group">
              <input
                type="color"
                v-model="overlayConfig.rightColor"
                @change="pushOverlayConfig"
                class="overlay-color-swatch"
                title="Right team color"
              />
              <input
                type="text"
                v-model="overlayConfig.rightColor"
                @change="pushOverlayConfig"
                maxlength="7"
                placeholder="#2563eb"
                class="overlay-hex-input"
              />
            </div>
          </div>
          <div class="overlay-setting-row">
            <span class="overlay-setting-label">Show Images</span>
            <label class="overlay-toggle">
              <input
                type="checkbox"
                v-model="overlayConfig.showImages"
                @change="pushOverlayConfig"
              />
              <span class="overlay-toggle-track"></span>
            </label>
          </div>
        </div>
        <p v-if="overlayConfigError" class="overlay-config-error">{{ overlayConfigError }}</p>
      </details>

      </div> <!-- end collapsible content -->
    </div> <!-- end setup card -->

    <!-- Bracket size change confirmation modal -->
    <Transition name="fade">
      <div v-if="showSizeChangeConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
        <div class="card-hover p-6 max-w-sm w-full mx-4 relative">
          <div class="corner-bar-tl"></div>
          <div class="type-page-title text-lg mb-2">Change Bracket Size?</div>
          <p class="type-body text-content-muted mb-6">The current bracket has participants placed. Changing the size will reset all bracket data for this genre. Continue?</p>
          <div class="flex gap-3 justify-end">
            <button
              @click="cancelSizeChange"
              class="para-chip-sm px-4 py-2 type-label transition-all"
            >Cancel</button>
            <button
              @click="confirmSizeChange"
              class="para-chip-sm px-4 py-2 type-label bg-amber-600/20 text-amber-400 border-amber-500/40 hover:bg-amber-600/30 transition-all"
            >Change Size</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Round change confirmation modal -->
    <Transition name="fade">
      <div v-if="showRoundChangeConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
        <div class="card-hover p-6 max-w-sm w-full mx-4 relative">
          <div class="corner-bar-tl"></div>
          <div class="type-page-title text-lg mb-2">Switch Round?</div>
          <p class="type-body text-content-muted mb-6">A battle is in progress. Switching rounds will not affect the active battle — you can continue viewing other rounds safely.</p>
          <div class="flex gap-3 justify-end">
            <button
              @click="cancelRoundChange"
              class="para-chip-sm px-4 py-2 type-label transition-all"
            >Cancel</button>
            <button
              @click="confirmRoundChange"
              class="para-chip-sm px-4 py-2 type-label text-accent border-[color:var(--accent-muted)] transition-all"
            >Switch Round</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Live Match panel — rendered for all roles, acts as orchestrator -->
    <LiveMatchPanel
      :selectedEvent="selectedEvent"
      :selectedGenre="selectedGenre"
      :uniqueGenres="uniqueGenres"
      :battlePhase="battlePhase"
      :battleJudges="battleJudges?.judges ?? []"
      :currentBattle="currentBattle"
      :currentWinner="currentWinner"
      :currentRound="currentRound"
      :currentTop="currentTop"
      :rounds="rounds"
      :topSize="topSize"
      :isSmoke="isSmoke"
      :roundNames="roundNames"
      :roundSizes="roundSizes"
      :saveStatus="saveStatus"
      :finalTieBlocked="finalTieBlocked"
      :isReadonly="!isAdminOrOrganiser"
      :canSwitchGenre="canSwitchGenre"
      :genreSwitchBlockReason="genreSwitchBlockReason"
      :genreChampions="genreChampions"
      :stompClient="wsClient"
      :overlayConfig="overlayConfig"
      :revealActive="revealActive"
      :activeRoundIdx="viewedRoundIdx"
      @request-genre-change="requestGenreChange"
      @open-voting="openVoting"
      @get-score="submitGetScore"
      @submit-revote="startRevote"
      @lock-champion="lockChampion"
      @reveal-champion="revealChampionForGenre"
      @dismiss-reveal="dismissReveal"
      @next-pair="nextPair"
      @unlock-champion="unlockChampion"
      @set-round="(idx) => { viewedRoundIdx = idx }"
      @start-round="handleEmceeStartRound"
    />

    </div>
  </div>

    <!-- WIN confirmation modal -->
    <Transition name="fade">
      <div v-if="pendingWin" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
        <div class="card-hover p-6 max-w-sm w-full mx-4 relative">
          <div class="corner-bar-tl"></div>
          <div class="type-page-title text-lg mb-2">
            {{ pendingWin.replacing ? 'Replace Winner?' : 'Set Winner?' }}
          </div>
          <p class="type-body text-content-muted mb-1">
            <template v-if="pendingWin.replacing">
              Replace <span class="text-content-primary">{{ pendingWin.replacing }}</span> with <span class="text-content-primary">{{ pendingWin.name }}</span>?
              The next round slot will be updated.
            </template>
            <template v-else>
              Set <span class="text-content-primary">{{ pendingWin.name }}</span> as winner?
              They will be placed in the next round slot.
            </template>
          </p>
          <p v-if="pendingWin.replacing" class="type-label text-content-muted mb-6" style="font-size:10px;letter-spacing:0.12em">
            If {{ pendingWin.name }} has already played in a later round, correct those results manually.
          </p>
          <p v-else class="mb-6"></p>
          <div class="flex gap-3 justify-end">
            <button @click="cancelWin" class="para-chip-sm px-4 py-2 type-label transition-all">Cancel</button>
            <button @click="confirmWin" class="para-chip-sm px-4 py-2 type-label bg-emerald-500/20 text-emerald-400 border-emerald-500/40 hover:bg-emerald-500/30 transition-all">Confirm</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Start-from-here confirmation modal -->
    <Transition name="fade">
      <div v-if="pendingStartAt" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
        <div class="card-hover p-6 max-w-md w-full mx-4 relative max-h-[90vh] overflow-y-auto">
          <div class="corner-bar-tl"></div>
          <div class="type-page-title text-lg mb-4">Confirm Battle Round</div>

          <!-- Genre / Division -->
          <div class="section-rule mb-3">
            <span class="section-rule-label">Division</span>
            <div class="section-rule-line"></div>
          </div>
          <p class="type-body text-content-primary mb-3">{{ selectedGenre }}</p>

          <!-- Round -->
          <div class="section-rule mb-3">
            <span class="section-rule-label">Round</span>
            <div class="section-rule-line"></div>
          </div>
          <p class="type-body text-content-primary mb-3">
            {{ roundLabel(pendingStartAt?.top) }}
            <span class="type-label text-content-muted ml-2">({{ pendingStartAt?.pairList?.length || 0 }} match{{ pendingStartAt?.pairList?.length !== 1 ? 'es' : '' }})</span>
          </p>

          <!-- Judges -->
          <div class="section-rule mb-3">
            <span class="section-rule-label">Judges</span>
            <div class="section-rule-line"></div>
          </div>
          <div v-if="(battleJudges?.judges ?? []).length" class="flex flex-wrap gap-1.5 mb-3">
            <span v-for="j in battleJudges.judges" :key="j.id"
              class="badge-neutral type-label px-2 py-0.5 text-xs">{{ j.name }}</span>
          </div>
          <p v-else class="type-label text-red-400 mb-3">⚠ No judges assigned — add judges before starting</p>

          <!-- Starting match -->
          <div v-if="!pendingStartAt?.startAll" class="section-rule mb-3">
            <span class="section-rule-label">Starting Match</span>
            <div class="section-rule-line"></div>
          </div>
          <template v-if="!pendingStartAt?.startAll">
            <p class="type-body mb-1">
              <span class="text-content-primary">{{ pendingStartAt?.pairList?.[pendingStartAt.matchIdx]?.[0] }}</span>
              <span class="text-content-muted mx-2">vs</span>
              <span class="text-content-primary">{{ pendingStartAt?.pairList?.[pendingStartAt.matchIdx]?.[1] }}</span>
            </p>
            <p v-if="pendingStartAt?.matchIdx > 0" class="type-label text-amber-400/80 mb-4" style="font-size:10px;letter-spacing:0.12em">
              {{ pendingStartAt.matchIdx }} match{{ pendingStartAt.matchIdx !== 1 ? 'es' : '' }} before this one will be skipped.
            </p>
            <p v-else class="mb-4"></p>
          </template>
          <p v-else class="type-body text-content-primary mb-4">All matches in this round will be started from the beginning.</p>

          <div class="flex gap-3 justify-end">
            <button @click="cancelStartAt" class="para-chip-sm px-4 py-2 type-label transition-all">Cancel</button>
            <button @click="confirmStartAt" class="para-chip-sm px-4 py-2 type-label bg-accent transition-all" :disabled="!(battleJudges?.judges ?? []).length">
              <i class="pi pi-play text-xs mr-1.5"></i>Start Round
            </button>
          </div>
        </div>
      </div>
    </Transition>
</template>

<style scoped>
.overlay-settings-panel {
  background: #1a1a1a;
  border: 1px solid #2c2c2c;
  border-radius: 12px;
  margin-top: 12px;
  overflow: hidden;
}
.overlay-settings-summary {
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 600;
  color: #f0f0f0;
  cursor: pointer;
  user-select: none;
  list-style: none;
}
.overlay-settings-summary::-webkit-details-marker { display: none; }
.overlay-settings-body {
  padding: 4px 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.overlay-setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.overlay-setting-label {
  font-size: 13px;
  color: rgba(240,240,240,0.65);
}
.overlay-color-group {
  display: flex;
  align-items: center;
  gap: 8px;
}
.overlay-color-swatch {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  padding: 0;
  background: none;
}
.overlay-hex-input {
  width: 80px;
  background: #111;
  border: 1px solid #2c2c2c;
  border-radius: 6px;
  color: #f0f0f0;
  font-size: 12px;
  font-family: 'JetBrains Mono', monospace;
  padding: 4px 8px;
}
.overlay-toggle { display: flex; align-items: center; cursor: pointer; }
.overlay-toggle input { display: none; }
.overlay-toggle-track {
  width: 36px;
  height: 20px;
  background: #2c2c2c;
  border-radius: 10px;
  position: relative;
  transition: background 0.2s;
}
.overlay-toggle input:checked + .overlay-toggle-track { background: #e53935; }
.overlay-toggle-track::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 14px;
  height: 14px;
  background: #fff;
  border-radius: 50%;
  transition: transform 0.2s;
}
.overlay-toggle input:checked + .overlay-toggle-track::after { transform: translateX(16px); }
.overlay-config-error {
  font-size: 11px;
  color: #f87171;
  margin: 4px 0 0;
  padding: 0 2px;
}

/* ── Recovery banner ────────────────────────────────── */
.recovery-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 18px;
  border-left: 3px solid rgba(245,158,11,0.85);
  background: rgba(245,158,11,0.08);
  font-family: 'Anton SC', sans-serif;
}
.recovery-body {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.recovery-dot {
  flex-shrink: 0;
  width: 8px; height: 8px;
  border-radius: 50%;
  background: rgba(245,158,11,0.85);
  box-shadow: 0 0 8px rgba(245,158,11,0.6), 0 0 16px rgba(245,158,11,0.3);
  animation: recoveryPulse 1.8s ease-in-out infinite;
}
.recovery-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.recovery-title {
  font-size: 11px;
  letter-spacing: 0.18em;
  color: rgba(245,158,11,0.85);
  text-transform: uppercase;
}
.recovery-detail {
  font-size: 13px;
  letter-spacing: 0.08em;
  color: rgba(255,255,255,0.75);
  text-transform: uppercase;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.recovery-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.recovery-btn {
  font-family: 'Anton SC', sans-serif;
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  padding: 6px 14px;
  border: none;
  cursor: pointer;
  clip-path: polygon(5px 0%, 100% 0%, calc(100% - 5px) 100%, 0% 100%);
  transition: background 0.2s, color 0.2s;
}
.recovery-btn-jump {
  background: rgba(245,158,11,0.85);
  color: #060a14;
}
.recovery-btn-jump:hover {
  background: rgba(245,158,11,1);
}
.recovery-btn-dismiss {
  background: rgba(255,255,255,0.08);
  color: rgba(255,255,255,0.55);
}
.recovery-btn-dismiss:hover {
  background: rgba(255,255,255,0.14);
  color: rgba(255,255,255,0.75);
}

@keyframes recoveryPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50%       { opacity: 0.4; transform: scale(0.7); }
}

.recovery-fade-enter-active,
.recovery-fade-leave-active { transition: opacity 0.3s ease; }
.recovery-fade-enter-from,
.recovery-fade-leave-to { opacity: 0; }
</style>
