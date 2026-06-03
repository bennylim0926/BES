<script setup>
import ReusableDropdown from '@/components/ReusableDropdown.vue'
import { addBattleJudge, addBattleGuest, battleJudgeVote, clearBattlePair, getBattleChampions, getBattleGuests, getBattleJudges, getBattlePhase, getBattleState, getOverlayConfig, getParticipantScore, getPickupCrews, getRegisteredParticipantsByEvent, getSmokeList, removeBattleGuest, removeBattleJudge, resetBattleVotes, revealChampion, dismissChampionReveal, setActiveGenre, setBattlePair, setBattlePhase, setBattleScore, setBracketState, setOverlayConfig, updateJudgeWeightage, updateSmokeList, uploadImage } from '@/utils/api'
import { deleteImage } from '@/utils/adminApi'
import { computed, onMounted, onUnmounted, ref, watch, toRaw } from 'vue'
import { useDropdowns } from '@/utils/dropdown'
import { useEventUtils } from '@/utils/eventUtils'
import { useBattleLogic } from '@/utils/battleLogic'
import { createClient, deactivateClient } from '@/utils/websocket'

const { selectedEvent, selectedGenre, initialiseDropdown, selectedJudge } = useDropdowns()
const { allJudges, fetchAllJudges, participants } = useEventUtils()
const { rounds, topSize, roundSizes, isSmoke, standardBattleRound, sevenToSmokeRound } = useBattleLogic()

const battleJudges = ref([])
const memberLookup = ref({}) // participantName → all member names (including rep)
const getMembersFor = (name) => memberLookup.value[name] ?? []
const isGuestSlot = (name) => !!name && guestsForCurrentGenre.value.some(g => g.guestName === name)
const currentBattle = ref([])
const currentWinner = ref(-2)
const currentRound = ref(0)
const currentTop = ref('')
const battlePhase = ref('IDLE')
const showResetConfirm = ref(false)
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
// { top, pairList, matchIdx } — null when no pending confirm
const pendingStartAt = ref(null)

const requestStartAt = (top, pairList, matchIdx) => {
  pendingStartAt.value = { top, pairList, matchIdx }
}

const confirmStartAt = async () => {
  if (!pendingStartAt.value) return
  const { top, pairList, matchIdx } = pendingStartAt.value
  pendingStartAt.value = null
  await initiateBattlePairAt(top, pairList, matchIdx)
}

const cancelStartAt = () => { pendingStartAt.value = null }

// ── Genre switcher — per-genre status dot ─────────────────────
// Returns 'champion' | 'active' | 'idle'
const _genreStatusDotMap = computed(() => {
  const map = {}
  for (const genre of uniqueGenres.value) {
    if (genreChampions.value[genre]) { map[genre] = 'champion'; continue }
    const phase = genre === selectedGenre.value
      ? battlePhase.value
      : (JSON.parse(localStorage.getItem(genreBattleStateKey(genre)) ?? '{}').phase ?? 'IDLE')
    map[genre] = ['LOCKED', 'VOTING', 'REVEALED'].includes(phase) ? 'active' : 'idle'
  }
  return map
})

const genreStatusDot = (genre) => _genreStatusDotMap.value[genre] ?? 'idle'

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

const removeUploadedFile = async (index) => {
  const name = uploadedFiles.value[index]
  const res = await deleteImage(name)
  if (res.ok) uploadedFiles.value.splice(index, 1)
}

const winnerAnnouncement = computed(() => {
  if (currentBattle.value.length === 0) return "Choose a round to start"
  if (currentWinner.value === -2) return "Battle is ongoing — GET SCORE when judges are ready"
  if (currentWinner.value === -3) return "Judges are not ready yet"
  if (isSmoke.value) {
    if (currentWinner.value === -1) return "It's a tie"
    if (currentWinner.value === 0) return `${rounds.value[0].name} takes it`
    if (currentWinner.value === 1) return `${rounds.value[1].name} takes it`
  } else {
    if (currentWinner.value === -1) return "It's a tie"
    if (currentWinner.value === 0) { setWinner(currentTop.value, currentRound.value, 0); return `${currentBattlePair?.value[currentWinner.value]} takes it` }
    if (currentWinner.value === 1) return `${currentBattlePair?.value[currentWinner.value]} takes it`
  }
  return ""
})

const winnerVariant = computed(() => {
  if (currentWinner.value === -2) return 'ongoing'
  if (currentWinner.value === -3) return 'wait'
  if (currentWinner.value === -1) return 'tie'
  return 'winner'
})

const previousBattlePair = computed(() => {
  if (currentBattle.value.length !== 0 && currentBattle?.value[0] > 0) {
    return [currentBattle?.value[1][currentBattle?.value[0] - 1][0], currentBattle?.value[1][currentBattle?.value[0] - 1][1]]
  }
  return null
})
const nextBattlePair = computed(() => {
  if (currentBattle.value.length === 0) return null
  if (isSmoke.value) return currentBattle?.value[2]
  if (currentBattle.value.length !== 0 && currentBattle?.value[0] < currentBattle?.value[1].length - 1) {
    return [currentBattle?.value[1][currentBattle?.value[0] + 1][0], currentBattle?.value[1][currentBattle?.value[0] + 1][1]]
  }
  return null
})

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

// Smoke mode pair entries are objects {name, score}; standard mode entries are strings.
// This computed always returns [string|null, string|null] so templates are uniform.
const currentBattlePairNames = computed(() => {
  const pair = currentBattlePair.value
  if (!pair) return [null, null]
  const n = (p) => (p && typeof p === 'object') ? p.name : p
  return [n(pair[0]), n(pair[1])]
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
  markSaving()
  currentWinner.value = -2
  revealActive.value = false
  await resetJudgeVote()
  if (top === 'Top2' && Array.isArray(rounds.value['Top2']?.[0])) {
    rounds.value['Top2'][0][2] = null
    localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  localStorage.setItem('currentTop', top)
  broadcastBracket()  // syncs currentRoundIndex to backend so recovery finds the right pair
  saveGenreBattleState(selectedGenre.value)
  markSaved()
}

const initiateBattlePair = async (top, pairList) => {
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
    localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  localStorage.setItem('currentTop', top)
  saveGenreBattleState(selectedGenre.value)
  markSaved()
}

const prevPair = async () => {
  if (currentBattle.value.length !== 0 && currentBattle.value[0] > 0) {
    markSaving()
    currentBattle.value = [currentBattle.value[0] - 1, currentBattle.value[1]]
    const left = currentBattle?.value[1][currentBattle?.value[0]][0]
    const right = currentBattle?.value[1][currentBattle?.value[0]][1]
    await setBattlePair(left, right, currentTop.value === 'Top2', getMembersFor(left), getMembersFor(right))
    await setBattlePhase('LOCKED')
    battlePhase.value = 'LOCKED'
    currentWinner.value = -2
    currentRound.value -= 1
    saveGenreBattleState(selectedGenre.value)
    markSaved()
  }
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
      saveGenreBattleState(selectedGenre.value)
    } else {
      await clearBattlePair()
      await setBattlePhase('IDLE')
      battlePhase.value = 'IDLE'
      currentWinner.value = -2
      currentBattle.value = []
      currentTop.value = ''
      localStorage.removeItem('currentTop')
      saveGenreBattleState(selectedGenre.value)
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

// Load resolved tie-breaker list from Score.vue if one exists for this event+genre+bracketSize
const resolvedParticipants = ref(null)
const loadResolvedParticipants = () => {
  const key = `tbResolved_${selectedEvent.value}_${selectedGenre.value}_${bracketSize.value}`
  const saved = localStorage.getItem(key)
  resolvedParticipants.value = saved ? JSON.parse(saved) : null
}
watch([selectedEvent, selectedGenre, bracketSize], loadResolvedParticipants, { immediate: true })

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
    localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
    localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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

const onDragStart = (roundKey, matchIdx, slotIdx, event) => {
  dragSource.value = { roundKey, matchIdx, slotIdx }

  const name = rounds.value[roundKey][matchIdx][slotIdx] || ''
  const ghost = document.createElement('div')
  ghost.textContent = name
  Object.assign(ghost.style, {
    position: 'fixed',
    top: '-9999px',
    left: '-9999px',
    padding: '5px 14px',
    background: '#1a1a1a',
    border: '1.5px solid rgba(248,113,113,0.65)',
    borderRadius: '8px',
    fontSize: '12px',
    fontWeight: '600',
    color: '#f0f0f0',
    boxShadow: '0 10px 28px rgba(0,0,0,0.7), 0 0 0 1px rgba(229,57,53,0.15)',
    whiteSpace: 'nowrap',
    pointerEvents: 'none',
  })
  document.body.appendChild(ghost)
  event.dataTransfer.setDragImage(ghost, ghost.offsetWidth / 2, ghost.offsetHeight / 2)
  requestAnimationFrame(() => document.body.removeChild(ghost))
}

const onDragOver = (roundKey, matchIdx, slotIdx) => {
  if (!dragSource.value && !poolDragName.value) return
  dragOverKey.value = `${roundKey}-${matchIdx}-${slotIdx}`
}

const onDragEnd = () => {
  dragSource.value = null
  dragOverKey.value = null
}

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
    localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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

  localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
  broadcastBracket()
  if (tgtIsCurrent || srcIsCurrent) reBroadcastCurrentPairIfActive()
}

const onPoolDragStart = (name, event) => {
  poolDragName.value = name
  const ghost = document.createElement('div')
  ghost.textContent = name
  Object.assign(ghost.style, {
    position: 'fixed',
    top: '-9999px',
    left: '-9999px',
    padding: '5px 14px',
    background: '#1a1a1a',
    border: '1.5px solid rgba(255,255,255,0.25)',
    borderRadius: '8px',
    fontSize: '12px',
    fontWeight: '600',
    color: '#f0f0f0',
    boxShadow: '0 10px 28px rgba(0,0,0,0.7)',
    whiteSpace: 'nowrap',
    pointerEvents: 'none',
  })
  document.body.appendChild(ghost)
  event.dataTransfer.setDragImage(ghost, ghost.offsetWidth / 2, ghost.offsetHeight / 2)
  requestAnimationFrame(() => document.body.removeChild(ghost))
}

const onPoolDragEnd = () => {
  poolDragName.value = null
  dragOverKey.value = null
}

const onSmokeDragStart = (idx, event) => {
  const name = rounds.value[idx]?.name
  if (!name) return
  dragSource.value = { smokeIdx: idx }
  const ghost = document.createElement('div')
  ghost.textContent = name
  Object.assign(ghost.style, {
    position: 'fixed', top: '-9999px', left: '-9999px',
    padding: '5px 14px', background: '#1a1a1a',
    border: '1.5px solid rgba(248,113,113,0.65)', borderRadius: '8px',
    fontSize: '12px', fontWeight: '600', color: '#f0f0f0',
    boxShadow: '0 10px 28px rgba(0,0,0,0.7)', whiteSpace: 'nowrap', pointerEvents: 'none',
  })
  document.body.appendChild(ghost)
  event.dataTransfer.setDragImage(ghost, ghost.offsetWidth / 2, ghost.offsetHeight / 2)
  requestAnimationFrame(() => document.body.removeChild(ghost))
}

const onSmokeDragOver = (idx) => {
  if (!dragSource.value && !poolDragName.value) return
  dragOverKey.value = `smoke-${idx}`
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
    localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
  broadcastBracket()
}

const clearSmokeSlot = (idx) => {
  if (!rounds.value[idx]) return
  rounds.value[idx] = { ...rounds.value[idx], name: null }
  localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
  broadcastBracket()
}

const clearSlot = (roundKey, matchIdx, slotIdx) => {
  const match = rounds.value[roundKey][matchIdx]
  match[slotIdx] = null
  match[2] = null
  localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  if (!nextRoundSize) { localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value))); broadcastBracket(); return }
  const nextRoundKey = `Top${nextRoundSize}`
  const nextMatchIdx = Math.floor(matchIdx / 2)
  const nextSlotIdx = matchIdx % 2
  rounds.value[nextRoundKey][nextMatchIdx][nextSlotIdx] = winner
  localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
const roundTabStatus = (idx) => {
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

const voteWeightDisplay = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  return {
    left:  judges.filter(j => j.vote === 0).reduce((s, j) => s + (j.weightage ?? 1), 0),
    right: judges.filter(j => j.vote === 1).reduce((s, j) => s + (j.weightage ?? 1), 0),
  }
})

const allJudgeOptions = computed(() => ["", ...Object.values(allJudges.value).map(j => j.judgeName)])

// Per-genre battle state persistence — saves which round/match was in progress so
// switching genres and back re-broadcasts the correct pair to the overlay automatically.
const genreBattleStateKey = (genre) => `battleState_${selectedEvent.value}_${genre}`

const saveGenreBattleState = (genre) => {
  if (!genre) return
  if (currentBattle.value.length === 0) {
    localStorage.removeItem(genreBattleStateKey(genre))
    return
  }
  localStorage.setItem(genreBattleStateKey(genre), JSON.stringify({
    battle: toRaw(currentBattle.value),
    top: currentTop.value,
    round: currentRound.value,
    phase: battlePhase.value,
  }))
}

const restoreAndBroadcastGenreBattle = async (genre) => {
  // At this point, switchActiveGenreService has already loaded this genre's state from DB
  // and broadcast it to all connected clients (overlay, bracket, judge). We only need
  // to restore local BattleControl UI state — no backend calls needed here.
  const saved = localStorage.getItem(genreBattleStateKey(genre))
  if (saved) {
    const { battle, top, round, phase } = JSON.parse(saved)
    currentBattle.value = battle ?? []
    currentTop.value = top ?? ''
    currentRound.value = round ?? 0
    currentWinner.value = -2
    battlePhase.value = phase ?? 'IDLE'
    return
  }
  // No localStorage: reconstruct local state from backend (already loaded by switchActiveGenreService).
  // Do NOT clear or re-push to backend — it already has the correct state.
  const state = await getBattleState()
  if (!state?.currentPair?.left || state.battlePhase === 'IDLE') {
    currentBattle.value = []
    currentTop.value = ''
    currentRound.value = 0
    currentWinner.value = -2
    return
  }
  battlePhase.value = state.battlePhase ?? 'IDLE'
  currentWinner.value = -2
  if (!isSmoke.value && state.bracket?.rounds) {
    // Populate rounds.value from DB so the bracket UI is visible, then cache to localStorage
    // so future genre switches don't need to fall back to DB again.
    rounds.value = state.bracket.rounds
    localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${genre}Rounds`, JSON.stringify(state.bracket.rounds))
    const bRounds = state.bracket.rounds
    let topKey = state.currentPair.isFinal ? 'Top2' : null
    if (!topKey) {
      for (const key of Object.keys(bRounds)) {
        const matchList = bRounds[key]
        if (!Array.isArray(matchList)) continue
        if (matchList.some(m => m[0] === state.currentPair.left && m[1] === state.currentPair.right)) {
          topKey = key; break
        }
      }
    }
    if (topKey) {
      currentTop.value = topKey
      const pairList = bRounds[topKey] ?? []
      const nameIdx = pairList.findIndex(m => m[0] === state.currentPair.left && m[1] === state.currentPair.right)
      const resolvedIdx = nameIdx >= 0 ? nameIdx : (state.currentRoundIndex ?? 0)
      currentRound.value = resolvedIdx
      currentBattle.value = [resolvedIdx, pairList]
      saveGenreBattleState(genre)
    }
  } else if (isSmoke.value) {
    const smokeRes = await getSmokeList()
    if (smokeRes?.list && Array.isArray(smokeRes.list)) {
      rounds.value = smokeRes.list.map(b => ({ name: b.name, score: b.score }))
      localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${genre}Rounds`, JSON.stringify(toRaw(rounds.value)))
    }
    if (state?.champion) {
      genreChampions.value = { ...genreChampions.value, [genre]: state.champion }
    }
    saveGenreBattleState(genre)
  }
}

const jumpToRecoveredPair = async () => {
  if (!recoveryState.value) return
  markSaving()
  const { currentPair, currentRoundIndex, battlePhase: restoredPhase, bracket, champion: restoredChampion } = recoveryState.value

  // Restore topSize from DB bracket state — browser localStorage may be stale if
  // the operator switched sizes on another session or after clearing storage.
  if (bracket?.topSize !== undefined) {
    const restored = Number(bracket.topSize)
    if (!isNaN(restored) && restored !== Number(topSize.value)) {
      topSize.value = restored
      localStorage.setItem('topSize', String(restored))
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
    localStorage.setItem('currentTop', topKey)
    const pairList = bracket.rounds[topKey] ?? []
    // Find pair by name — currentRoundIndex can be stale if broadcastBracket wasn't called
    const nameIdx = pairList.findIndex(m => m[0] === currentPair.left && m[1] === currentPair.right)
    const resolvedIdx = nameIdx >= 0 ? nameIdx : (currentRoundIndex ?? 0)
    currentRound.value = resolvedIdx
    currentBattle.value = [resolvedIdx, pairList]
  }

  // REVEALED: backend already has the correct state loaded from DB on startup.
  // Calling setBattlePair would force LOCKED and broadcast it to all connected devices.
  if (restoredPhase === 'REVEALED') {
    battlePhase.value = 'REVEALED'
    const match = rounds.value[currentTop.value]?.[currentRound.value]
    if (match?.[2]) {
      currentWinner.value = match[2] === currentBattlePair.value?.[0] ? 0 : 1
    }
    saveGenreBattleState(selectedGenre.value)
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
  saveGenreBattleState(selectedGenre.value)
  markSaved()
}

// Per-genre judge persistence helpers — stores { id, vote } so votes survive genre switches
const genreJudgeKey = (genre) => `battleJudges_${selectedEvent.value}_${genre}`
let judgeSyncing = false  // prevents concurrent syncJudgesForGenre calls

const saveGenreJudges = (genre) => {
  const judges = (battleJudges.value?.judges ?? []).map(j => ({ id: j.id, vote: j.vote, weightage: j.weightage ?? 1 }))
  localStorage.setItem(genreJudgeKey(genre), JSON.stringify(judges))
}

// Called when genre switches: removes current backend judges, restores saved judges + votes for new genre
const syncJudgesForGenre = async (newGenre, prevGenre) => {
  if (judgeSyncing) return
  judgeSyncing = true
  try {
    // Save outgoing genre's judges from UI cache BEFORE fetching from backend.
    // setActiveGenre already switched the backend to newGenre, so getBattleJudges()
    // would return newGenre's judges — overwriting prevGenre's localStorage with the
    // wrong list and permanently losing the outgoing genre's judge assignment.
    if (prevGenre) saveGenreJudges(prevGenre)
    // Now fetch backend to find out which judges are currently loaded (newGenre's) so
    // we can remove them before installing prevGenre→newGenre's saved set.
    battleJudges.value = await getBattleJudges()
    const toRemove = battleJudges.value?.judges ?? []
    // Remove sequentially — parallel DELETEs cause ConcurrentModificationException on the
    // backend's ArrayList, silently leaving judges behind and contaminating the next genre.
    for (const j of toRemove) {
      await removeBattleJudge(j.id)
    }
    const raw = JSON.parse(localStorage.getItem(genreJudgeKey(newGenre)) ?? '[]')
    if (raw.length > 0) {
      // Support both old format (array of ids) and new format (array of { id, vote })
      const entries = raw.map(s => (typeof s === 'object' ? s : { id: s, vote: -3, weightage: 1 }))
      // Add sequentially for the same reason as removes above
      for (const { id, weightage } of entries) {
        await addBattleJudge(id, weightage ?? 1)
      }
      // Restore non-default votes — addBattleJudge sets vote=-3 ("not voted"), so skip those.
      // -1 is a real tie vote and must be restored; only -3 means "hasn't voted this round".
      await Promise.all(
        entries
          .filter(({ vote }) => vote !== undefined && vote !== -3)
          .map(({ id, vote }) => battleJudgeVote(id, vote))
      )
    }
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
  saveGenreJudges(selectedGenre.value)
}

const submitUpdateJudgeWeightage = async (id, value) => {
  const weightage = Math.max(1, parseInt(value) || 1)
  await updateJudgeWeightage(id, weightage)
  battleJudges.value = await getBattleJudges()
  saveGenreJudges(selectedGenre.value)
}

const submitRemoveBattleJudge = async (name) => {
  const j = allJudges.value.find(j => j.judgeName === name)
  await removeBattleJudge(j?.judgeId)
  battleJudges.value = await getBattleJudges()
  saveGenreJudges(selectedGenre.value)
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
  localStorage.setItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  localStorage.setItem(genreChampionLocalKey(selectedGenre.value), winner)
  await setBattlePhase('DECIDED')
  battlePhase.value = 'DECIDED'
  saveGenreBattleState(selectedGenre.value)
}

const unlockChampion = async () => {
  const { [selectedGenre.value]: _removed, ...rest } = genreChampions.value
  genreChampions.value = rest
  localStorage.removeItem(genreChampionLocalKey(selectedGenre.value))
  await setBattlePhase('VOTING')
  battlePhase.value = 'VOTING'
  saveGenreBattleState(selectedGenre.value)
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
    saveGenreBattleState(selectedGenre.value)
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
  saveGenreBattleState(selectedGenre.value)
  markSaved()
}

const confirmResetBracket = async () => {
  showResetConfirm.value = false
  localStorage.removeItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`)
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
  finalTieBlocked.value = false
  localStorage.removeItem('currentTop')
  localStorage.removeItem(sizeStateKey(topSize.value))
  saveGenreBattleState(selectedGenre.value)
  // Clear champion tracking — both backend (DB) and local (ref + localStorage)
  await dismissChampionReveal()
  revealActive.value = false
  const { [selectedGenre.value]: _removed, ...rest } = genreChampions.value
  genreChampions.value = rest
  localStorage.removeItem(genreChampionLocalKey(selectedGenre.value))
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
  localStorage.removeItem(`Top${topSize.value}${selectedEvent.value}${selectedGenre.value}Rounds`)
  localStorage.removeItem(sizeStateKey(topSize.value))
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

// Guard: prompt before switching round tab when battle is in progress
const requestRoundChange = (idx) => {
  if (battlePhase.value !== 'IDLE' && idx !== activeRoundIdx.value) {
    pendingRoundIdx.value = idx
    showRoundChangeConfirm.value = true
  } else {
    activeRoundIdx.value = idx
  }
}

const confirmRoundChange = () => {
  showRoundChangeConfirm.value = false
  activeRoundIdx.value = pendingRoundIdx.value
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
      saveGenreBattleState(selectedGenre.value)
      await setActiveGenre(newVal, selectedGenre.value)
      await restoreAndBroadcastGenreBattle(selectedGenre.value)
    }
  }
}, { immediate: true })

const genreTopSizeKey = (genre) => `battleTopSize_${selectedEvent.value}_${genre}`
const genreChampionLocalKey = (genre) => `battleChampion_${selectedEvent.value}_${genre}`

// Load pending champions from localStorage for all genres as they become known
watch(uniqueGenres, (genres) => {
  if (!selectedEvent.value || !genres?.length) return
  const locals = {}
  for (const g of genres) {
    const p = localStorage.getItem(genreChampionLocalKey(g))
    if (p) locals[g] = p
  }
  // Merge: backend confirmed data (already in genreChampions) takes precedence
  genreChampions.value = { ...locals, ...genreChampions.value }
}, { immediate: true })

// When all judges revote to a tie in the final, clear any previously locked
// champion so stale data doesn't linger. (Champion is only saved via Lock button.)
watch(showFinalReveal, (newVal) => {
  if (!selectedGenre.value) return
  if (!newVal && isFinalInProgress.value && allJudgesVoted.value && tentativeWinner.value === -1) {
    const { [selectedGenre.value]: _removed, ...rest } = genreChampions.value
    genreChampions.value = rest
    localStorage.removeItem(genreChampionLocalKey(selectedGenre.value))
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
    localStorage.removeItem(genreChampionLocalKey(selectedGenre.value))
  }
})

watch(selectedGenre, async (newVal, oldVal) => {
  // Dismiss before resetting revealActive — the check must happen while it's still true
  if (oldVal && revealActive.value) await dismissChampionReveal()
  revealActive.value = false
  if (newVal) {
    // Persist outgoing genre's topSize before switching
    if (oldVal) localStorage.setItem(genreTopSizeKey(oldVal), String(topSize.value))

    // Restore per-genre topSize — smoke auto-detection takes priority, otherwise
    // use the saved size for this genre.  When no saved size exists, default to 16
    // for a genre switch (don't inherit the outgoing genre's size), but keep the
    // global localStorage value on first/immediate load so a refresh is stable.
    const genreNeedsSmoke = newVal.toLowerCase().includes('7 to smoke') || newVal.toLowerCase().includes('7tosmoke')
    if (genreNeedsSmoke) {
      if (Number(topSize.value) !== 7) {
        topSize.value = 7
        localStorage.setItem('topSize', '7')
      }
    } else {
      const savedSize = localStorage.getItem(genreTopSizeKey(newVal))
      const restoredSize = savedSize ? Number(savedSize) : (oldVal ? 16 : Number(topSize.value))
      if (restoredSize !== Number(topSize.value)) {
        skipSizeChangeClear = true
        topSize.value = restoredSize
        localStorage.setItem('topSize', String(restoredSize))
      }
    }

    // Restore pending champion for incoming genre from localStorage
    if (newVal && !genreChampions.value[newVal]) {
      const pending = localStorage.getItem(genreChampionLocalKey(newVal))
      if (pending) genreChampions.value = { ...genreChampions.value, [newVal]: pending }
    }
    localStorage.setItem("selectedGenre", newVal)
    const storedRounds = localStorage.getItem(`Top${topSize.value}${selectedEvent.value}${newVal}Rounds`)
    rounds.value = JSON.parse(storedRounds) || initRounds()
    pickupCrews.value = await getPickupCrews(selectedEvent.value, newVal)
    placeGuestsInBracket()
    if (oldVal) {
      saveGenreBattleState(oldVal)
      await setActiveGenre(selectedEvent.value, newVal)
    }
    // Only broadcast local rounds on a genre SWITCH (oldVal truthy) when localStorage has data.
    // On initial load (oldVal falsy), setActiveGenre in onMounted hasn't completed yet so the
    // backend still points at the previous event — broadcasting here would corrupt that event's
    // DB row. onMounted handles state restoration after setActiveGenre confirms the correct event.
    if (storedRounds && oldVal) broadcastBracket()
    if (oldVal) {
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
        saveGenreBattleState(newVal)
      }
    }
  } else {
    pickupCrews.value = []
  }
}, { immediate: true })

const sizeStateKey = (size) => `battleSizeState_${selectedEvent.value}_${selectedGenre.value}_${size}`
const roundIdxKey = () => `battleRoundIdx_${selectedEvent.value}_${selectedGenre.value}_${topSize.value}`

// Persist the active round tab selection so it survives refresh and genre switch.
// Does NOT auto-broadcast to overlay — the overlay stays on the IDLE announcement
// until the operator explicitly clicks "Start Round".
watch(activeRoundIdx, (idx) => {
  if (selectedEvent.value && selectedGenre.value) {
    localStorage.setItem(roundIdxKey(), String(idx))
  }
})

watch(topSize, async (newVal, oldVal) => {
  if (!newVal) return
  // Restore previously-selected round tab for this size; default to 0 (first round).
  // Only when event+genre are known — during setup they're still null, defer to onMounted.
  if (selectedEvent.value && selectedGenre.value) {
    const savedIdx = localStorage.getItem(roundIdxKey())
    activeRoundIdx.value = savedIdx !== null ? Math.min(Number(savedIdx), roundSizes.value.length - 1) : 0
  }
  // Save outgoing size's battle state so it can be restored on return (no overlay broadcast)
  if (oldVal && currentBattle.value.length > 0) {
    localStorage.setItem(sizeStateKey(oldVal), JSON.stringify({
      battle: toRaw(currentBattle.value),
      top: currentTop.value,
      round: currentRound.value,
      phase: battlePhase.value,
    }))
  }
  localStorage.setItem("topSize", newVal)
  // Also save per-genre so it survives refresh (not just genre switch)
  if (selectedEvent.value && selectedGenre.value) {
    localStorage.setItem(genreTopSizeKey(selectedGenre.value), String(newVal))
  }
  const storedRounds = localStorage.getItem(`Top${newVal}${selectedEvent.value}${selectedGenre.value}Rounds`)
  rounds.value = JSON.parse(storedRounds) || initRounds()
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

  // Restore the incoming size's battle state (pair + phase). Do NOT push to overlay —
  // the overlay updates only when the operator clicks a round tab or "Start Round".
  const savedSizeState = localStorage.getItem(sizeStateKey(newVal))
  if (savedSizeState) {
    const { battle, top, round, phase: savedPhase } = JSON.parse(savedSizeState)
    currentBattle.value = battle ?? []
    currentTop.value = top ?? ''
    currentRound.value = round ?? 0
    // Restore phase locally — but don't broadcast. The overlay stays on whatever
    // it was last showing. When the operator clicks a round tab, the activeRoundIdx
    // watcher will find and broadcast the correct pair from the bracket data.
    battlePhase.value = savedPhase && savedPhase !== 'IDLE' ? savedPhase : 'IDLE'
    saveGenreBattleState(selectedGenre.value)
  } else {
    currentBattle.value = []
    currentTop.value = ''
    currentRound.value = 0
  }
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
  // Restore per-genre bracket size (survives refresh)
  if (selectedEvent.value && selectedGenre.value) {
    const savedSize = localStorage.getItem(genreTopSizeKey(selectedGenre.value))
    if (savedSize) {
      topSize.value = Number(savedSize)
      localStorage.setItem('topSize', savedSize)
    }
  }
  // Now that event+genre are set, restore the saved round tab with correct keys
  const savedRoundIdx = localStorage.getItem(roundIdxKey())
  if (savedRoundIdx !== null) {
    activeRoundIdx.value = Math.min(Number(savedRoundIdx), roundSizes.value.length - 1)
  }
  await fetchAllJudges(selectedEvent.value)
  await fetchBattleGuests()
  battleJudges.value = await getBattleJudges()
  // Backend DB is now authoritative for judges (persisted on every change).
  // On refresh, don't call syncJudgesForGenre — that removes all judges and re-adds
  // from localStorage, which broadcasts an empty list and makes BattleJudge ask "who are you?".
  // Just seed localStorage if it's empty so future genre switches have a value to restore.
  if (selectedGenre.value) {
    const savedRaw = localStorage.getItem(genreJudgeKey(selectedGenre.value))
    if (savedRaw === null) {
      saveGenreJudges(selectedGenre.value)
    }
  }
  mountJudgeSyncDone = true
  const savedConfig = await getOverlayConfig()
  if (savedConfig?.showImages !== undefined) overlayConfig.value = savedConfig
  if (selectedEvent.value) {
    const champions = await getBattleChampions(selectedEvent.value)
    // Merge: localStorage pending (already loaded by watch(uniqueGenres)) +
    // backend confirmed. Backend wins on conflict (official record).
    if (champions && typeof champions === 'object') {
      genreChampions.value = { ...genreChampions.value, ...champions }
    }
  }
  const phaseData = await getBattlePhase()
  battlePhase.value = phaseData?.phase ?? 'IDLE'
  // Restore currentTop from localStorage only if a battle is genuinely in progress
  // (avoids stale 'Top2' from a previous session bleeding into non-final rounds)
  if (battlePhase.value !== 'IDLE') {
    const storedTop = localStorage.getItem('currentTop')
    if (storedTop) currentTop.value = storedTop
  }
  // Auto-restore from backend state on refresh — always check regardless of local state
  const battleState = await getBattleState()
  if (battleState?.battlePhase && battleState.battlePhase !== 'IDLE' && battleState.currentPair?.left) {
    recoveryState.value = battleState
    await jumpToRecoveredPair()   // restore silently
    showRecoveryBanner.value = true  // then notify the operator
  }
  wsClient.value = createClient()
  // Single onConnect handler — multiple subscribeToChannel calls before connection
  // overwrite each other's onConnect, so we wire everything here directly.
  wsClient.value.onConnect = () => {
    wsClient.value.subscribe('/topic/battle/phase', (raw) => {
      const msg = JSON.parse(raw.body)
      // Ignore phase messages intended for a different genre — stale WS from a genre
      // switch can arrive after we've already switched back and set the correct phase.
      if (msg.genre && msg.genre !== selectedGenre.value) return
      battlePhase.value = msg.phase
      if (msg.phase === 'DECIDED' && msg.champion) {
        genreChampions.value = { ...genreChampions.value, [selectedGenre.value]: msg.champion }
      }
    })
    // NOTE: /topic/battle/judges is intentionally NOT subscribed here.
    // syncJudgesForGenre does multiple remove+add operations in sequence; each
    // triggers a WS broadcast with an intermediate state. These late-arriving
    // messages would race against the explicit getBattleJudges() call at the end
    // of syncJudgesForGenre and corrupt battleJudges.value.
    // We update battleJudges.value only via explicit getBattleJudges() calls.
    syncJudgeVoteSubscriptions()
  }
  wsClient.value.activate()
})

onUnmounted(() => {
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
    <div class="flex flex-wrap gap-2">
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

    <!-- Setup panel (collapsible, locks once battle starts) -->
    <div class="card overflow-hidden">
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
        <span class="text-surface-600 select-none">|</span>

        <!-- Genre toggle -->
        <div class="flex flex-wrap gap-2">
          <button
            v-for="g in uniqueGenres"
            :key="g"
            @click="requestGenreChange(g)"
            class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150 inline-flex items-center gap-1.5"
            :class="selectedGenre === g
              ? 'text-accent border-[color:var(--accent-muted)]'
              : 'text-content-muted hover:text-content-primary'"
          >
            {{ g }}
            <i v-if="(g === selectedGenre && battlePhase === 'DECIDED') || genreChampions[g]" class="pi pi-star-fill text-[9px] text-amber-400" title="Champion locked — ready to reveal"></i>
          </button>
        </div>
        <!-- Format toggle — hidden for smoke genres and when locked -->
        <template v-if="!isGenreSmoke && !setupLocked">
          <span class="text-surface-600 select-none">|</span>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="s in sizes.filter(s => s !== 7)"
              :key="s"
              @click="requestSizeChange(s)"
              class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150"
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

      <div class="flex flex-wrap items-center gap-3 mt-3">
        <!-- Active judge slots -->
        <div class="flex flex-wrap gap-3 flex-1 min-w-0">
          <div
            v-for="(j, index) in battleJudges?.judges || []"
            :key="index"
            class="card-hover p-2 relative inline-flex items-center gap-2 px-3"
          >
            <div class="corner-bar-tl"></div>
            <span class="type-body text-content-primary">{{ j.name }}</span>
            <div class="flex items-center gap-1">
              <span class="type-label text-content-muted" style="font-size:9px;letter-spacing:0.12em">WT</span>
              <span v-if="setupLocked" class="type-body text-content-muted" style="font-size:12px;min-width:2.5rem;text-align:center">{{ j.weightage ?? 1 }}</span>
              <input
                v-else
                type="number"
                :value="j.weightage ?? 1"
                min="1"
                class="w-10 bg-surface-900 border border-surface-600 text-content-primary text-center type-body"
                style="padding:2px 4px;font-size:12px;clip-path:polygon(3px 0%,100% 0%,calc(100% - 3px) 100%,0% 100%)"
                @change="e => submitUpdateJudgeWeightage(j.id, e.target.value)"
              />
            </div>
            <button
              v-if="!setupLocked"
              @click="submitRemoveBattleJudge(j.name)"
              class="flex items-center justify-center hover:text-red-400 transition-colors"
            >
              <i class="pi pi-times text-xs"></i>
            </button>
          </div>
          <span v-if="!battleJudges?.judges?.length" class="type-label text-content-muted">None added</span>
        </div>

        <!-- Add control pushed to the right — hidden when locked -->
        <div v-if="!setupLocked" class="ml-auto flex items-center gap-2">
          <div class="w-44">
            <ReusableDropdown v-model="selectedJudge" labelId="" :options="allJudgeOptions" />
          </div>
          <button
            @click="submitAddBattleJudge(selectedJudge)"
            class="bg-accent para-chip-sm px-3 py-1.5 type-label transition-all duration-200 whitespace-nowrap"
          >
            <i class="pi pi-plus text-xs"></i>
            Add
          </button>
        </div>
      </div>

      <!-- Overlay Settings -->
      <details class="overlay-settings-panel">
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

      <div class="section-rule">
        <span class="section-rule-label">Seeding</span>
        <div class="section-rule-line"></div>
      </div>

      <div v-if="!setupLocked" class="flex flex-wrap items-center gap-2 mt-4 mb-5">
        <!-- Pickup crew sort toggle (mixed bracket only) -->
        <template v-if="isMixedBracket">
          <div class="flex gap-1">
            <button
              @click="crewSortMode = 'leader'"
              class="para-chip-sm px-2.5 py-1.5 type-label transition-all"
              :class="crewSortMode === 'leader' ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
              title="Sort pickup crews by their leader's individual audition score"
            >Leader</button>
            <button
              @click="crewSortMode = 'avg'"
              class="para-chip-sm px-2.5 py-1.5 type-label transition-all"
              :class="crewSortMode === 'avg' ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
              title="Sort pickup crews by average score of all members"
            >Avg</button>
          </div>
          <span class="text-surface-600 select-none">|</span>
        </template>

        <div class="flex flex-wrap gap-1">
          <button
            @click="autoFillSeeds"
            class="para-chip-sm px-3 py-1.5 type-label inline-flex items-center gap-1 transition-all"
            :class="rankAsc ? 'text-content-muted hover:text-content-primary' : 'text-content-muted hover:text-content-primary'"
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
            class="para-chip-sm px-3 py-1.5 type-label inline-flex items-center gap-1 transition-all"
            :class="guestsForCurrentGenre.length > 0 ? 'opacity-30 cursor-not-allowed text-content-muted' : 'text-content-muted hover:text-content-primary'"
            :title="guestsForCurrentGenre.length > 0 ? 'Disabled: bracket has pinned guests' : 'Pair highest with lowest (1st vs last, 2nd vs 2nd-last...)'"
          >
            <i class="pi pi-arrows-v text-xs"></i>
            High ↔ Low
          </button>
          <button
            @click="randomFill"
            class="para-chip-sm px-3 py-1.5 type-label inline-flex items-center gap-1 text-content-muted hover:text-content-primary transition-all"
            title="Random shuffle"
          >
            <i class="pi pi-refresh text-xs"></i>
            Random
          </button>
          <button
            v-if="isMixedBracket"
            @click="splitBracketFill"
            class="para-chip-sm px-3 py-1.5 type-label inline-flex items-center gap-1 text-accent transition-all"
            title="Pre-formed teams on left half, pickup crews on right half"
          >
            <i class="pi pi-table text-xs"></i>
            Split
          </button>
        </div>
      </div>

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

      <!-- ── Seeding Pool ──────────────────────────────── -->
      <div v-if="!setupLocked" class="mb-5">
        <div class="section-rule">
          <span class="section-rule-label">Seeding Pool</span>
          <span v-if="guestsForCurrentGenre.length" class="type-label text-content-muted ml-2">
            · {{ guestsForCurrentGenre.length }} guest slot{{ guestsForCurrentGenre.length > 1 ? 's' : '' }} reserved · {{ bracketSize - guestsForCurrentGenre.length }} {{ isSmoke ? 'queue slots' : 'seed slots' }} shown
          </span>
          <div class="section-rule-line"></div>
        </div>

        <div class="flex flex-wrap gap-1.5 mt-3 min-h-[28px]">
          <span v-if="!poolParticipants.length" class="type-label text-content-muted">
            {{ isSmoke ? `All ${bracketSize - guestsForCurrentGenre.length} queue slots filled` : `All top ${bracketSize - guestsForCurrentGenre.length} participants placed in bracket` }}
          </span>
          <span
            v-for="p in poolParticipants" :key="p.name"
            draggable="true"
            @dragstart="(e) => onPoolDragStart(p.name, e)"
            @dragend="onPoolDragEnd"
            class="para-chip-sm px-2.5 py-1 type-label text-content-primary cursor-grab active:cursor-grabbing select-none inline-flex items-center gap-1.5"
            :class="poolDragName === p.name ? 'opacity-40' : ''"
            :title="p.name"
          >
            <span>{{ p.name }}</span>
            <span class="text-content-muted" style="font-size:10px;letter-spacing:0.05em;opacity:0.7">{{ p.score % 1 === 0 ? p.score : p.score.toFixed(1) }}</span>
          </span>
        </div>
      </div>

      <div class="section-rule">
        <span class="section-rule-label">Bracket</span>
        <div class="section-rule-line"></div>
      </div>

      <!-- ── Standard bracket ──────────────────────────── -->
      <div v-if="Number(topSize) !== 7" class="mt-3">
        <!-- Round tabs -->
        <div class="flex flex-wrap gap-1 mb-4">
          <button
            v-for="(size, idx) in roundSizes"
            :key="idx"
            @click="requestRoundChange(idx)"
            class="para-chip-sm px-4 py-1.5 type-label transition-all duration-150 inline-flex items-center gap-1.5"
            :class="{
              'text-accent border-[color:var(--accent-muted)]': activeRoundIdx === idx,
              'text-emerald-400/70 border-emerald-500/30': activeRoundIdx !== idx && roundTabStatus(idx) === 'done',
              'text-content-muted/40 cursor-not-allowed': roundTabStatus(idx) === 'locked',
              'text-content-muted hover:text-content-primary': activeRoundIdx !== idx && roundTabStatus(idx) !== 'done' && roundTabStatus(idx) !== 'locked',
              'border-amber-400/40 text-amber-400': roundTabStatus(idx) === 'active',
            }"
            :title="roundTabStatus(idx) === 'locked' ? 'Waiting for previous round to complete' : ''"
          >
            <i v-if="roundTabStatus(idx) === 'active'" class="pi pi-circle-fill text-[6px] text-amber-400" title="Active battle"></i>
            <i v-else-if="roundTabStatus(idx) === 'done'" class="pi pi-check text-[9px] text-emerald-400/70" title="Round complete"></i>
            <i v-else-if="roundTabStatus(idx) === 'locked'" class="pi pi-lock text-[8px] text-content-muted/40" title="Waiting for previous round"></i>
            Top {{ size }}
          </button>
        </div>

        <!-- Active round matches -->
        <template v-for="(size, idx) in roundSizes" :key="idx">
          <div v-if="activeRoundIdx === idx" class="card p-4">
            <div class="flex flex-col gap-2 mb-3">
              <!-- Match card: horizontal Left VS Right layout -->
              <div
                v-for="(match, mIdx) in rounds[`Top${size}`]"
                :key="mIdx"
                class="card-hover p-3 relative flex items-stretch min-h-[44px]"
                :style="isActivePair(match) && effectivePhase !== 'IDLE'
                  ? 'border-left: 3px solid var(--accent-color); background: var(--accent-subtle); box-shadow: 0 0 0 1px var(--accent-muted), 0 0 18px var(--accent-subtle);'
                  : ''"
              >
                <div class="corner-bar-tl"></div>
                <!-- Slot 0 — left -->
                <div
                  class="flex-1 min-w-0 flex items-center gap-1.5 px-2 py-1.5 transition-all duration-150"
                  :class="[
                    match[2] === match[0] && match[0] ? 'bg-emerald-500/10' : '',
                    dragSource?.roundKey === `Top${size}` && dragSource?.matchIdx === mIdx && dragSource?.slotIdx === 0
                      ? 'ring-2 ring-primary-400/80 bg-primary-400/12 shadow-inner'
                      : dragOverKey === `Top${size}-${mIdx}-0` ? 'bg-primary-500/15 ring-2 ring-inset ring-primary-500/70' : ''
                  ]"
                  @dragover.prevent="onDragOver(`Top${size}`, mIdx, 0)"
                  @dragleave="dragOverKey = null"
                  @drop.prevent="onDrop(`Top${size}`, mIdx, 0)"
                >
                  <i class="pi pi-crown text-xs flex-shrink-0 transition-colors" :class="match[2] === match[0] && match[0] ? 'text-amber-400' : 'text-surface-600'"></i>
                  <div v-if="match[0]"
                    draggable="true"
                    @dragstart="(e) => onDragStart(`Top${size}`, mIdx, 0, e)"
                    @dragend="onDragEnd"
                    class="flex-1 min-w-0 select-none cursor-grab active:cursor-grabbing flex items-center gap-3"
                    :class="match[2] === match[0] && match[0] ? 'text-emerald-400' : 'text-content-primary'"
                  >
                    <div class="flex items-center gap-1.5 flex-shrink-0">
                      <span class="type-body">{{ match[0] }}</span>
                      <span v-if="isGuestSlot(match[0])" class="flex-shrink-0 inline-flex items-center gap-0.5 px-1.5 py-px text-amber-400 bg-amber-500/20 border border-amber-500/50 rounded" style="font-size:9px;font-weight:700;letter-spacing:0.1em"><i class="pi pi-star" style="font-size:7px"></i>GUEST</span>
                    </div>
                    <div v-if="getMembersFor(match[0]).length" class="flex flex-wrap gap-1 flex-1 min-w-0">
                      <span
                        v-for="m in getMembersFor(match[0])" :key="m"
                        class="inline-block px-2 py-0.5 normal-case flex-shrink-0"
                        :class="match[2] === match[0] ? 'bg-emerald-500/15 text-emerald-400/80' : 'bg-surface-700/60 text-content-muted'"
                        style="font-size:10px;letter-spacing:0.04em;clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
                      >{{ m }}</span>
                    </div>
                  </div>
                  <span v-else class="flex-1 type-body text-surface-600/60 italic">Drop here</span>
                  <button v-if="match[0] && !isGuestSlot(match[0])" @click="clearSlot(`Top${size}`, mIdx, 0)" class="flex-shrink-0 px-1.5 py-1 text-surface-400 hover:text-red-400 hover:bg-red-500/10 rounded transition-colors" title="Clear slot"><i class="pi pi-times text-[10px]"></i></button>
                  <button
                    :disabled="!match[0]"
                    @click="match[2] === match[0] && match[0] ? clearWinner(`Top${size}`, mIdx) : setWinner(`Top${size}`, mIdx, 0)"
                    class="flex-shrink-0 w-9 text-center rounded text-[11px] font-bold transition-all disabled:opacity-20 disabled:cursor-not-allowed leading-5"
                    :class="match[2] === match[0] && match[0] ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 hover:bg-red-500/20 hover:text-red-400 hover:border-red-500/40' : 'bg-surface-700 text-surface-400 border border-surface-600/50 hover:border-surface-500'"
                  >{{ match[2] === match[0] && match[0] ? '✓' : 'Win' }}</button>
                </div>

                <!-- VS badge (vertical divider) -->
                <div class="flex items-center justify-center w-7 shrink-0 border-x border-surface-600/30 bg-surface-900/50">
                  <span class="text-[9px] font-black text-surface-600 tracking-widest rotate-0">VS</span>
                </div>

                <!-- Slot 1 — right -->
                <div
                  class="flex-1 min-w-0 flex items-center gap-1.5 px-2 py-1.5 transition-all duration-150"
                  :class="[
                    match[2] === match[1] && match[1] ? 'bg-emerald-500/10' : '',
                    dragSource?.roundKey === `Top${size}` && dragSource?.matchIdx === mIdx && dragSource?.slotIdx === 1
                      ? 'ring-2 ring-primary-400/80 bg-primary-400/12 shadow-inner'
                      : dragOverKey === `Top${size}-${mIdx}-1` ? 'bg-primary-500/15 ring-2 ring-inset ring-primary-500/70' : ''
                  ]"
                  @dragover.prevent="onDragOver(`Top${size}`, mIdx, 1)"
                  @dragleave="dragOverKey = null"
                  @drop.prevent="onDrop(`Top${size}`, mIdx, 1)"
                >
                  <i class="pi pi-crown text-xs flex-shrink-0 transition-colors" :class="match[2] === match[1] && match[1] ? 'text-amber-400' : 'text-surface-600'"></i>
                  <div v-if="match[1]"
                    draggable="true"
                    @dragstart="(e) => onDragStart(`Top${size}`, mIdx, 1, e)"
                    @dragend="onDragEnd"
                    class="flex-1 min-w-0 select-none cursor-grab active:cursor-grabbing flex items-center gap-3"
                    :class="match[2] === match[1] && match[1] ? 'text-emerald-400' : 'text-content-primary'"
                  >
                    <div class="flex items-center gap-1.5 flex-shrink-0">
                      <span class="type-body">{{ match[1] }}</span>
                      <span v-if="isGuestSlot(match[1])" class="flex-shrink-0 inline-flex items-center gap-0.5 px-1.5 py-px text-amber-400 bg-amber-500/20 border border-amber-500/50 rounded" style="font-size:9px;font-weight:700;letter-spacing:0.1em"><i class="pi pi-star" style="font-size:7px"></i>GUEST</span>
                    </div>
                    <div v-if="getMembersFor(match[1]).length" class="flex flex-wrap gap-1 flex-1 min-w-0">
                      <span
                        v-for="m in getMembersFor(match[1])" :key="m"
                        class="inline-block px-2 py-0.5 normal-case flex-shrink-0"
                        :class="match[2] === match[1] ? 'bg-emerald-500/15 text-emerald-400/80' : 'bg-surface-700/60 text-content-muted'"
                        style="font-size:10px;letter-spacing:0.04em;clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
                      >{{ m }}</span>
                    </div>
                  </div>
                  <span v-else class="flex-1 type-body text-surface-600/60 italic">Drop here</span>
                  <button v-if="match[1] && !isGuestSlot(match[1])" @click="clearSlot(`Top${size}`, mIdx, 1)" class="flex-shrink-0 px-1.5 py-1 text-surface-400 hover:text-red-400 hover:bg-red-500/10 rounded transition-colors" title="Clear slot"><i class="pi pi-times text-[10px]"></i></button>
                  <button
                    :disabled="!match[1]"
                    @click="match[2] === match[1] && match[1] ? clearWinner(`Top${size}`, mIdx) : setWinner(`Top${size}`, mIdx, 1)"
                    class="flex-shrink-0 w-9 text-center rounded text-[11px] font-bold transition-all disabled:opacity-20 disabled:cursor-not-allowed leading-5"
                    :class="match[2] === match[1] && match[1] ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 hover:bg-red-500/20 hover:text-red-400 hover:border-red-500/40' : 'bg-surface-700 text-surface-400 border border-surface-600/50 hover:border-surface-500'"
                  >{{ match[2] === match[1] && match[1] ? '✓' : 'Win' }}</button>
                </div>
                <!-- Start from this match — only when round is idle and all slots filled -->
                <button
                  v-if="match[0] && match[1] && isActiveRoundFilled && effectivePhase === 'IDLE'"
                  @click="initiateBattlePairAt(`Top${size}`, rounds[`Top${size}`], mIdx)"
                  class="flex-shrink-0 flex items-center justify-center w-8 ml-1.5 self-stretch rounded text-accent border border-[color:var(--accent-muted)] bg-[color:var(--accent-subtle)] hover:bg-[color:var(--accent-muted)] transition-colors"
                  title="Start round from this match"
                ><i class="pi pi-play text-[10px]"></i></button>
              </div>
            </div>

            <button
              v-if="effectivePhase === 'IDLE'"
              :disabled="!isActiveRoundFilled"
              @click="initiateBattlePair(`Top${size}`, rounds[`Top${size}`])"
              class="w-full py-2 para-chip type-label transition-all duration-200"
              :class="isActiveRoundFilled ? 'bg-accent' : 'bg-surface-700 text-content-muted cursor-not-allowed'"
              :title="isActiveRoundFilled ? '' : 'All slots must be filled and the previous round must be completed'"
            >
              <i class="pi pi-play text-xs mr-1.5"></i>
              Start Round
            </button>
            <div v-else class="w-full py-2 text-center type-label text-content-muted">
              Active battle in {{ currentTop }}
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
            :draggable="!!match.name"
            @dragstart="(e) => onSmokeDragStart(mIdx, e)"
            @dragend="onDragEnd"
            @dragover.prevent="onSmokeDragOver(mIdx)"
            @dragleave="dragOverKey = null"
            @drop.prevent="onSmokeDrop(mIdx)"
            class="card-hover relative flex items-stretch overflow-hidden transition-all duration-150"
            :class="dragOverKey === `smoke-${mIdx}` ? 'ring-2 ring-inset ring-primary-500/70 bg-primary-500/10' :
                    (dragSource?.smokeIdx === mIdx ? 'ring-2 ring-primary-400/80 bg-primary-400/12' : '')"
            style="padding:0"
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
              v-if="match.name && !isGuestSlot(match.name)"
              @click.stop="clearSmokeSlot(mIdx)"
              class="flex items-center justify-center px-2 flex-shrink-0 border-l border-surface-600/30 text-surface-400 hover:text-red-400 hover:bg-red-500/10 transition-colors"
              title="Clear slot"
            ><i class="pi pi-times" style="font-size:10px"></i></button>
          </div>
        </div>

        <button
          @click="initiateBattlePair(0, 0)"
          class="w-full py-2 bg-accent para-chip type-label transition-all duration-200"
        >
          <i class="pi pi-play text-xs mr-1.5"></i>
          Start Round
        </button>
      </div>
      </div> <!-- end collapsible content -->
    </div> <!-- end setup card -->

    <!-- Reset bracket confirmation modal -->
    <Transition name="fade">
      <div v-if="showResetConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
        <div class="card-hover p-6 max-w-sm w-full mx-4 relative">
          <div class="corner-bar-tl"></div>
          <div class="type-page-title text-lg mb-2">Reset Bracket?</div>
          <p class="type-body text-content-muted mb-6">This will clear all bracket data and set the battle phase to IDLE. This cannot be undone.</p>
          <div class="flex gap-3 justify-end">
            <button
              @click="showResetConfirm = false"
              class="para-chip-sm px-4 py-2 type-label transition-all"
            >Cancel</button>
            <button
              @click="confirmResetBracket"
              class="para-chip-sm px-4 py-2 type-label bg-red-600/20 text-red-400 border-red-500/40 hover:bg-red-600/30 transition-all"
            >Reset</button>
          </div>
        </div>
      </div>
    </Transition>

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

    <!-- Live match tracker -->
    <div class="card p-5">
      <div class="section-rule mb-4">
        <span class="section-rule-label">Live Match</span>
        <div class="section-rule-line"></div>
      </div>

      <div class="flex items-center gap-3 mb-4">
        <div
          class="inline-flex items-center gap-2 px-3 py-1.5"
          :class="{
            'semantic-chip-success': effectivePhase === 'REVEALED',
            'semantic-chip-warning': effectivePhase === 'LOCKED',
            'semantic-chip-warning animate-pulse': effectivePhase === 'VOTING',
            'semantic-chip-warning opacity-50': effectivePhase === 'IDLE',
          }"
        >
          <div
            class="w-2 h-2 rounded-full"
            :style="effectivePhase === 'REVEALED' ? 'background:#34d399;box-shadow:0 0 8px rgba(52,211,153,0.8)' : effectivePhase === 'LOCKED' ? 'background:#f59e0b;box-shadow:0 0 8px rgba(245,158,11,0.8)' : effectivePhase === 'VOTING' ? 'background:#f59e0b;box-shadow:0 0 8px rgba(245,158,11,0.8)' : 'background:#6b7280;box-shadow:0 0 8px rgba(107,114,128,0.5)'"
          ></div>
          <span class="type-body" :class="effectivePhase === 'REVEALED' ? 'text-emerald-400' : effectivePhase === 'LOCKED' ? 'text-amber-400' : effectivePhase === 'VOTING' ? 'text-amber-400' : 'text-gray-400'">{{ effectivePhase }}</span>
        </div>
        <span v-if="!isActiveBattleInThisRound && battlePhase !== 'IDLE'" class="type-label text-content-muted">
          (active battle in {{ currentTop }})
        </span>
        <!-- Save state indicator — next to phase badge so operator sees it -->
        <Transition name="recovery-fade" mode="out-in">
          <span v-if="saveStatus === 'saving'" key="saving" class="inline-flex items-center gap-1.5 px-2.5 py-1 type-label text-content-muted" style="font-size:10px;letter-spacing:0.16em;background:rgba(255,255,255,0.04);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)">
            <i class="pi pi-spin pi-spinner" style="font-size:9px"></i> SAVING
          </span>
          <span v-else-if="saveStatus === 'saved'" key="saved" class="inline-flex items-center gap-1.5 px-2.5 py-1 type-label text-emerald-400" style="font-size:10px;letter-spacing:0.16em;background:rgba(52,211,153,0.08);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)">
            <span class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400" style="box-shadow:0 0 6px rgba(52,211,153,0.7)"></span> SAVED
          </span>
          <span v-else-if="saveStatus === 'error'" key="error" class="inline-flex items-center gap-1.5 px-2.5 py-1 type-label text-amber-400" style="font-size:10px;letter-spacing:0.16em;background:rgba(245,158,11,0.08);clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)">
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
          @click="startRevote"
          class="para-chip-sm px-3 py-1.5 type-label text-accent transition-all"
        >START REVOTE</button>
      </div>

      <!-- WINNER ANNOUNCEMENT -->
      <!-- DECIDED: champion locked — show glowing champion name -->
      <div
        v-if="battlePhase === 'DECIDED'"
        class="px-4 py-3 mb-4"
        style="border-left:4px solid #34d399;background:rgba(52,211,153,0.08)"
      >
        <span class="type-label text-emerald-400" style="font-size:9px;letter-spacing:0.22em">⭐ CHAMPION LOCKED</span>
        <span class="type-body text-emerald-400 block mt-1" style="font-size:18px;font-weight:bold;text-shadow:0 0 12px rgba(52,211,153,0.4)">
          {{ genreChampions[selectedGenre] ?? currentGenreChampion ?? '—' }}
        </span>
        <span v-if="!revealActive" class="type-label text-content-muted block mt-1" style="font-size:9px;letter-spacing:0.22em">
          FINAL · ORGANISER ONLY — NOT REVEALED YET
        </span>
      </div>
      <!-- All other phases: ongoing/wait/winner/tie announcement -->
      <div
        v-else
        class="px-4 py-3 mb-4"
        :class="{
          'semantic-chip-warning': winnerVariant === 'ongoing',
          'semantic-chip-warning': winnerVariant === 'wait',
          'semantic-chip-success': winnerVariant === 'winner',
          'border-l-3 border-gray-400 bg-gray-500/10': winnerVariant === 'tie',
        }"
      >
        <div
          class="w-2 h-2 rounded-full mb-1"
          :class="winnerVariant === 'winner' ? 'bg-emerald-400' : winnerVariant === 'tie' ? 'bg-gray-400' : 'bg-amber-400'"
          :style="winnerVariant === 'winner' ? 'box-shadow:0 0 8px rgba(52,211,153,0.8)' : winnerVariant === 'tie' ? '' : 'box-shadow:0 0 8px rgba(245,158,11,0.8)'"
        ></div>
        <span class="type-body text-content-primary">{{ winnerAnnouncement }}</span>
      </div>

      <!-- Judge vote panel — live per-judge votes, visible in all phases -->
      <div v-if="battleJudges?.judges?.length" class="mb-4">
        <div class="section-rule mb-3">
          <span class="section-rule-label">JUDGES</span>
          <div class="section-rule-line"></div>
        </div>
        <div
          class="grid gap-2 mb-3"
          :style="{ gridTemplateColumns: `repeat(${battleJudges.judges.length}, 1fr)` }"
        >
          <div
            v-for="judge in battleJudges.judges"
            :key="judge.id"
            class="px-3 py-3 text-center"
            :style="{
              clipPath: 'polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)',
              border: judge.vote === -3
                ? '1px solid rgba(245,158,11,0.3)'
                : judge.vote === 0
                  ? `1px solid ${overlayConfig.leftColor}99`
                  : `1px solid ${overlayConfig.rightColor}99`,
              background: judge.vote === -3
                ? 'rgba(245,158,11,0.06)'
                : judge.vote === 0
                  ? `${overlayConfig.leftColor}18`
                  : `${overlayConfig.rightColor}18`,
            }"
          >
            <div style="font-size:10px;letter-spacing:0.18em;color:rgba(255,255,255,0.55);margin-bottom:2px">{{ judge.name }}</div>
            <div style="font-size:11px;color:#93c5fd;letter-spacing:0.12em;margin-bottom:4px;font-weight:700">WT {{ judge.weightage ?? 1 }}</div>
            <div
              v-if="judge.vote === -3"
              class="type-body text-amber-400"
              style="font-size:13px"
            >⏳ WAITING</div>
            <div
              v-else-if="judge.vote === 0"
              class="type-body"
              :style="{ fontSize: '13px', color: overlayConfig.leftColor }"
            >{{ currentBattlePairNames?.[0] ?? 'LEFT' }}</div>
            <div
              v-else-if="judge.vote === 1"
              class="type-body"
              :style="{ fontSize: '13px', color: overlayConfig.rightColor }"
            >{{ currentBattlePairNames?.[1] ?? 'RIGHT' }}</div>
          </div>
        </div>
        <!-- Winner preview banner when all judges have voted -->
        <div
          v-if="allJudgesVoted && tentativeWinner !== -1"
          class="px-4 py-3"
          :style="{
            borderLeft: `4px solid ${tentativeWinner === 0 ? overlayConfig.leftColor : overlayConfig.rightColor}`,
            background: `${tentativeWinner === 0 ? overlayConfig.leftColor : overlayConfig.rightColor}18`,
          }"
        >
          <div class="type-label mb-2" style="font-size:9px;letter-spacing:0.18em" :style="{ color: tentativeWinner === 0 ? overlayConfig.leftColor : overlayConfig.rightColor }">WINNER PREVIEW (ORGANISER ONLY)</div>
          <div class="type-body" style="font-size:20px;letter-spacing:0.08em;font-weight:bold" :style="{ color: tentativeWinner === 0 ? overlayConfig.leftColor : overlayConfig.rightColor }">
            {{ tentativeWinner === 0 ? (currentBattlePairNames?.[0] ?? 'LEFT') : (currentBattlePairNames?.[1] ?? 'RIGHT') }}
          </div>
          <div class="type-label text-content-muted mt-1" style="font-size:13px;letter-spacing:0.06em">{{ voteWeightDisplay.left }} – {{ voteWeightDisplay.right }}</div>
        </div>
        <div
          v-else-if="allJudgesVoted && tentativeWinner === -1"
          class="px-4 py-3"
          style="border-left:3px solid #6b7280;background:rgba(107,114,128,0.08)"
        >
          <div class="type-label mb-2" style="font-size:9px;letter-spacing:0.18em;color:#9ca3af">WINNER PREVIEW</div>
          <div class="type-body" style="font-size:20px;letter-spacing:0.06em;font-weight:bold;color:#9ca3af">TIE — {{ voteWeightDisplay.left }} – {{ voteWeightDisplay.right }}</div>
          <div class="type-label text-content-muted mt-1" style="font-size:13px;letter-spacing:0.06em">Rematch required</div>
        </div>
      </div>
      <div
        v-else-if="!battleJudges?.judges?.length"
        class="mb-4 px-3 py-2"
        style="clip-path:polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%);border:1px solid rgba(255,255,255,0.07);background:rgba(255,255,255,0.04)"
      >
        <span class="type-label text-content-muted">No judges assigned for this battle</span>
      </div>

      <!-- Match pairs (standard) — only shown when viewing the active round -->
      <div v-if="!isSmoke && isActiveBattleInThisRound" class="grid grid-cols-3 gap-3 mb-4">
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
        <div class="stat-card relative" style="box-shadow: 0 0 0 1px var(--accent-muted), 0 8px 40px var(--accent-subtle);">
          <div class="corner-bar-tl"></div>
          <span class="type-label text-accent mb-1">Current</span>
          <template v-if="currentBattlePair">
            <span class="type-body text-content-primary block">{{ currentBattlePair[0] }}</span>
            <span v-if="getMembersFor(currentBattlePair[0]).length" class="type-label text-content-muted normal-case block" style="font-size:11px;letter-spacing:0.04em">{{ getMembersFor(currentBattlePair[0]).join(' · ') }}</span>
            <span class="type-label text-content-muted my-0.5 block">vs</span>
            <span class="type-body text-content-primary block">{{ currentBattlePair[1] }}</span>
            <span v-if="getMembersFor(currentBattlePair[1]).length" class="type-label text-content-muted normal-case block" style="font-size:11px;letter-spacing:0.04em">{{ getMembersFor(currentBattlePair[1]).join(' · ') }}</span>
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

      <!-- Match pairs (smoke) -->
      <div v-if="isSmoke" class="grid grid-cols-2 gap-3 mb-4">
        <div class="stat-card relative" style="box-shadow: 0 0 0 1px var(--accent-muted), 0 8px 40px var(--accent-subtle);">
          <div class="corner-bar-tl"></div>
          <span class="type-label text-accent mb-1">Current Match</span>
          <template v-if="currentBattlePair">
            <span class="type-body text-content-primary block">{{ currentBattlePair[0].name }} ({{ currentBattlePair[0].score }})</span>
            <span class="type-label text-content-muted">vs</span>
            <span class="type-body text-content-primary block">{{ currentBattlePair[1].name }} ({{ currentBattlePair[1].score }})</span>
          </template>
        </div>
        <div class="stat-card relative">
          <div class="corner-bar-tl"></div>
          <span class="type-label text-content-muted mb-1">Queue</span>
          <span v-if="nextBattlePair" class="type-body text-content-secondary block">{{ nextBattlePair.map(p => p.name).join(', ') }}</span>
          <span v-else class="type-stat text-content-disabled opacity-30">—</span>
        </div>
      </div>

      <!-- Action buttons — only for the active round -->
      <div v-if="isActiveBattleInThisRound" class="flex flex-wrap gap-2">
        <!-- LOCKED: open voting -->
        <button
          v-if="battlePhase === 'LOCKED'"
          @click="openVoting"
          class="bg-accent para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
        >
          <i class="pi pi-lock-open text-xs"></i>
          Open Voting
        </button>

        <!-- VOTING: non-final, not all voted, or tie → Get Score / Rematch -->
        <button
          v-if="battlePhase === 'VOTING' && !showFinalReveal"
          :disabled="!allJudgesVoted"
          @click="submitGetScore"
          :class="allJudgesVoted
            ? 'bg-accent para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all'
            : 'bg-surface-700/30 para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all cursor-not-allowed opacity-50'"
          :title="allJudgesVoted ? '' : 'Waiting for all judges to vote'"
        >
          <i class="pi pi-bolt text-xs"></i>
          {{ (Number(currentWinner) === -1 && !isSmoke) ? 'Rematch' : 'Get Score' }}
        </button>

        <!-- VOTING + final + all judges voted → Lock Champion.
             Enabled when clear winner, disabled on tie (listens in real-time). -->
        <button
          v-if="battlePhase === 'VOTING' && isFinalInProgress && allJudgesVoted"
          :disabled="!showFinalReveal"
          @click="lockChampion"
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
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
            @click="revealChampionForGenre"
            class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 border-accent transition-all"
          >
            <i class="pi pi-star text-xs"></i>
            Reveal Champion
          </button>
          <button
            v-if="revealActive"
            @click="dismissReveal"
            class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
          >
            <i class="pi pi-times text-xs"></i>
            Dismiss Reveal
          </button>
          <button
            @click="unlockChampion"
            class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 text-content-muted hover:text-content-primary transition-all"
          >
            <i class="pi pi-unlock text-xs"></i>
            Unlock
          </button>
        </template>

        <!-- REVEALED: previous + next -->
        <template v-if="battlePhase === 'REVEALED'">
          <button
            @click="prevPair"
            class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
          >
            <i class="pi pi-chevron-left text-xs"></i>
            Previous
          </button>
          <button
            @click="nextPair"
            class="bg-accent para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
          >
            Next
            <i class="pi pi-chevron-right text-xs"></i>
          </button>
        </template>

        <!-- Champion Reveal (re-reveal) — winner already scored or tracked.
             Only shown when a champion was previously locked/revealed for this genre.
             Not shown during DECIDED (has its own button above). -->
        <button
          v-if="battlePhase !== 'DECIDED' && (currentGenreChampion || genreChampions[selectedGenre]) && !revealActive"
          @click="revealChampionForGenre"
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 border-accent transition-all"
        >
          <i class="pi pi-star text-xs"></i>
          Reveal Champion
        </button>
        <button
          v-if="revealActive && battlePhase !== 'DECIDED'"
          @click="dismissReveal"
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
        >
          <i class="pi pi-times text-xs"></i>
          Dismiss Reveal
        </button>
      </div>

      <!-- Always-visible actions -->
      <div class="flex flex-wrap gap-2 mt-2">
        <button
          @click="showResetConfirm = true"
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 text-red-400 border-red-500/30 transition-all"
        >
          <i class="pi pi-refresh text-xs"></i>
          Reset Bracket
        </button>

        <!-- File upload -->
        <label
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 cursor-pointer transition-all"
        >
          <i class="pi pi-upload text-xs"></i>
          Upload Images
          <input type="file" multiple @change="onFileChange" class="hidden" />
        </label>
      </div>

      <!-- Uploaded images list -->
      <div v-if="uploadedFiles.length > 0" class="mt-4 pt-4">
        <div class="section-rule mb-3">
          <span class="section-rule-label">Uploaded Images</span>
          <div class="section-rule-line"></div>
        </div>
        <div class="flex flex-wrap gap-2">
          <div
            v-for="(name, idx) in uploadedFiles"
            :key="idx"
            class="card-hover p-2 relative flex items-center gap-2 px-3"
          >
            <div class="corner-bar-tl"></div>
            <span class="type-body text-content-primary max-w-[160px] truncate">{{ name }}</span>
            <button
              @click="removeUploadedFile(idx)"
              class="flex-shrink-0 w-5 h-5 flex items-center justify-center hover:text-red-400 transition-colors"
            >
              <i class="pi pi-times text-xs"></i>
            </button>
          </div>
        </div>
      </div>
    </div>

    </div>
  </div>
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
