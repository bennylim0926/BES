<script setup>
import ReusableDropdown from '@/components/ReusableDropdown.vue'
import { addBattleJudge, addBattleGuest, battleJudgeVote, getBattleGuests, getBattleJudges, getBattlePhase, getBattleState, getOverlayConfig, getParticipantScore, getPickupCrews, getRegisteredParticipantsByEvent, removeBattleGuest, removeBattleJudge, resetBattleVotes, revealChampion, dismissChampionReveal, setActiveGenre, setBattlePair, setBattlePhase, setBattleScore, setBracketState, setOverlayConfig, updateSmokeList, uploadImage } from '@/utils/api'
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
const finalTieBlocked = ref(false)
const revealActive = ref(false)
const overlayConfig = ref({ showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' })
const showRecoveryBanner = ref(false)
const recoveryState      = ref(null)

const saveStatus = ref('idle') // 'idle' | 'saving' | 'saved' | 'error'
let saveTimer = null
const markSaving = () => { saveStatus.value = 'saving'; clearTimeout(saveTimer) }
const markSaved  = () => {
  saveStatus.value = 'saved'
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => { saveStatus.value = 'idle' }, 2200)
}
const markSaveError = () => { saveStatus.value = 'error' }

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
  await Promise.all(battleJudges.value.judges.map(j => battleJudgeVote(j.id, -3)))
}

const currentBattlePair = computed(() => {
  if (currentBattle.value.length === 0) return
  if (isSmoke.value) return [currentBattle?.value[0], currentBattle?.value[1]]
  const left = currentBattle?.value[1][currentBattle?.value[0]][0]
  const right = currentBattle?.value[1][currentBattle?.value[0]][1]
  return [left, right]
})

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
    localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
    localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
    localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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

const onDrop = (tgtRound, tgtMatch, tgtSlot) => {
  // Pool → bracket drop
  if (poolDragName.value) {
    const name = poolDragName.value
    poolDragName.value = null
    dragOverKey.value = null
    const guestNames = new Set(guestsForCurrentGenre.value.map(g => g.guestName))
    if (guestNames.has(rounds.value[tgtRound][tgtMatch][tgtSlot])) return // never overwrite a pinned guest
    rounds.value[tgtRound][tgtMatch][tgtSlot] = name
    rounds.value[tgtRound][tgtMatch][2] = null
    localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
    broadcastBracket()
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

  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
  broadcastBracket()
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
    localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
  broadcastBracket()
}

const clearSmokeSlot = (idx) => {
  if (!rounds.value[idx]) return
  rounds.value[idx] = { ...rounds.value[idx], name: null }
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
  broadcastBracket()
}

const clearSlot = (roundKey, matchIdx, slotIdx) => {
  const match = rounds.value[roundKey][matchIdx]
  match[slotIdx] = null
  match[2] = null
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  if (!nextRoundSize) { localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value))); broadcastBracket(); return }
  const nextRoundKey = `Top${nextRoundSize}`
  const nextMatchIdx = Math.floor(matchIdx / 2)
  const nextSlotIdx = matchIdx % 2
  rounds.value[nextRoundKey][nextMatchIdx][nextSlotIdx] = winner
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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

// All judges have cast a vote (none still at -3 = "hasn't voted this round")
const allJudgesVoted = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  return judges.length > 0 && judges.every(j => j.vote !== -3)
})

// Tentative winner from live judge votes — mirrors backend scoring logic
const tentativeWinner = computed(() => {
  const judges = battleJudges.value?.judges ?? []
  if (judges.some(j => j.vote === -3)) return -2   // not all voted yet
  const leftVotes  = judges.filter(j => j.vote === 0).length
  const rightVotes = judges.filter(j => j.vote === 1).length
  if (leftVotes === rightVotes) return -1            // tie
  return leftVotes > rightVotes ? 0 : 1
})

// Show "Reveal Champion" in VOTING when all judges voted + clear winner (final only)
const showFinalReveal = computed(() =>
  battlePhase.value === 'VOTING' &&
  isFinalInProgress.value &&
  allJudgesVoted.value &&
  tentativeWinner.value !== -1
)

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
  const saved = localStorage.getItem(genreBattleStateKey(genre))
  if (!saved) {
    currentBattle.value = []
    currentTop.value = ''
    currentRound.value = 0
    currentWinner.value = -2
    return
  }
  const { battle, top, round, phase } = JSON.parse(saved)
  currentBattle.value = battle ?? []
  currentTop.value = top ?? ''
  currentRound.value = round ?? 0
  currentWinner.value = -2
  // Re-broadcast so the overlay updates to this genre's current pair without needing "Start Round"
  const pair = currentBattlePair.value
  if (pair?.[0] && pair?.[1]) {
    await setBattlePair(pair[0], pair[1], top === 'Top2', getMembersFor(pair[0]), getMembersFor(pair[1]))
    battlePhase.value = 'LOCKED'
    // Restore VOTING phase so "Reveal Champion" is available immediately if judges already voted
    if (phase === 'VOTING') {
      await setBattlePhase('VOTING')
      battlePhase.value = 'VOTING'
    }
  }
}

const jumpToRecoveredPair = async () => {
  if (!recoveryState.value) return
  markSaving()
  const { currentPair, currentRoundIndex, battlePhase: restoredPhase, bracket } = recoveryState.value

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
  const judges = (battleJudges.value?.judges ?? []).map(j => ({ id: j.id, vote: j.vote }))
  localStorage.setItem(genreJudgeKey(genre), JSON.stringify(judges))
}

// Called when genre switches: removes current backend judges, restores saved judges + votes for new genre
const syncJudgesForGenre = async (newGenre, prevGenre) => {
  if (judgeSyncing) return
  judgeSyncing = true
  try {
    // Refresh from backend first so we save the true current state (not stale cache)
    battleJudges.value = await getBattleJudges()
    if (prevGenre) saveGenreJudges(prevGenre)
    const toRemove = battleJudges.value?.judges ?? []
    // Remove sequentially — parallel DELETEs cause ConcurrentModificationException on the
    // backend's ArrayList, silently leaving judges behind and contaminating the next genre.
    for (const j of toRemove) {
      await removeBattleJudge(j.id)
    }
    const raw = JSON.parse(localStorage.getItem(genreJudgeKey(newGenre)) ?? '[]')
    if (raw.length > 0) {
      // Support both old format (array of ids) and new format (array of { id, vote })
      const entries = raw.map(s => (typeof s === 'object' ? s : { id: s, vote: -1 }))
      // Add sequentially for the same reason as removes above
      for (const { id } of entries) {
        await addBattleJudge(id)
      }
      // Restore non-default votes — addBattleJudge sets vote=-1, so only restore others
      await Promise.all(
        entries
          .filter(({ vote }) => vote !== undefined && vote !== -1)
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
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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

const revealChampionForGenre = async () => {
  if (showFinalReveal.value) {
    // Capture winner name before broadcasting (tentativeWinner computed from live votes)
    const winnerName = tentativeWinner.value === 0
      ? currentBattlePair.value?.[0]
      : currentBattlePair.value?.[1]
    // Directly broadcast the score — bypasses submitGetScore's early-return guard
    const res = await setBattleScore(true)
    if (res?.status === 409) {
      // Defensive: shouldn't happen since tentativeWinner !== -1
      finalTieBlocked.value = true
      return
    }
    const data = await res.json()
    currentWinner.value = Number(data.winner)
    if (data.winner === 0 || data.winner === 1) {
      setWinner(currentTop.value, currentRound.value, data.winner)
      await revealChampion(selectedGenre.value, winnerName)
      revealActive.value = true
    }
    return
  }
  if (!currentGenreChampion.value) return
  await revealChampion(selectedGenre.value, currentGenreChampion.value)
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
  localStorage.removeItem(`Top${topSize.value}${selectedGenre.value}Rounds`)
  rounds.value = initRounds()
  placeGuestsInBracket()
  broadcastBracket()
  await setBattlePhase('IDLE')
  battlePhase.value = 'IDLE'
  currentWinner.value = -2
  currentBattle.value = []
  currentTop.value = ''
  localStorage.removeItem('currentTop')
  saveGenreBattleState(selectedGenre.value)
}

watch(selectedEvent, async (newVal) => {
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
  }
}, { immediate: true })

watch(selectedGenre, async (newVal, oldVal) => {
  // Dismiss before resetting revealActive — the check must happen while it's still true
  if (oldVal && revealActive.value) await dismissChampionReveal()
  revealActive.value = false
  if (newVal) {
    // Auto-detect format from genre name — smoke genres always use topSize=7, others reset to 16
    const genreNeedsSmoke = newVal.toLowerCase().includes('7 to smoke') || newVal.toLowerCase().includes('7tosmoke')
    if (genreNeedsSmoke && Number(topSize.value) !== 7) {
      topSize.value = 7
      localStorage.setItem('topSize', '7')
    } else if (!genreNeedsSmoke && Number(topSize.value) === 7) {
      topSize.value = 16
      localStorage.setItem('topSize', '16')
    }
    localStorage.setItem("selectedGenre", newVal)
    const storedRounds = localStorage.getItem(`Top${topSize.value}${newVal}Rounds`)
    rounds.value = JSON.parse(storedRounds) || initRounds()
    pickupCrews.value = await getPickupCrews(selectedEvent.value, newVal)
    placeGuestsInBracket()
    if (oldVal) {
      saveGenreBattleState(oldVal)
      await setActiveGenre(selectedEvent.value, newVal)
    }
    broadcastBracket()
    if (oldVal) {
      await restoreAndBroadcastGenreBattle(newVal)
    }
    // Sync per-genre judges on genre switch.
    // mountJudgeSyncDone guards against firing before onMounted loads battleJudges from API.
    // oldVal check (truthy) skips the immediate fire and empty-string initialization cases.
    if (mountJudgeSyncDone && oldVal) await syncJudgesForGenre(newVal, oldVal)
  } else {
    pickupCrews.value = []
  }
}, { immediate: true })

watch(topSize, async (newVal) => {
  if (newVal) {
    localStorage.setItem("topSize", newVal)
    const storedRounds = localStorage.getItem(`Top${newVal}${selectedGenre.value}Rounds`)
    rounds.value = JSON.parse(storedRounds) || initRounds()
    currentBattle.value = []
    currentWinner.value = -2
    broadcastBracket()
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

onMounted(async () => {
  initialiseDropdown()
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
      battlePhase.value = JSON.parse(raw.body).phase
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
            <span class="recovery-detail">ROUND {{ recoveryState.currentRoundIndex + 1 }} — {{ recoveryState.currentPair?.left ?? '???' }} VS {{ recoveryState.currentPair?.right ?? '???' }}</span>
          </div>
        </div>
        <div class="recovery-actions">
          <button class="recovery-btn recovery-btn-dismiss" @click="showRecoveryBanner = false">
            DISMISS
          </button>
        </div>
      </div>
    </Transition>

    <!-- Config bar + Bracket (merged) -->
    <div class="card p-5">
      <div class="flex flex-wrap items-center gap-3 mb-4">
        <!-- Event name -->
        <span class="font-heading font-bold text-base text-content-primary whitespace-nowrap">{{ selectedEvent }}</span>
        <span class="text-surface-600 select-none">|</span>

        <!-- Genre toggle -->
        <div class="flex flex-wrap gap-2">
          <button
            v-for="g in uniqueGenres"
            :key="g"
            @click="selectedGenre = g"
            class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150"
            :class="selectedGenre === g
              ? 'text-accent border-[color:var(--accent-muted)]'
              : 'text-content-muted hover:text-content-primary'"
          >{{ g }}</button>
        </div>
        <!-- Format toggle — hidden for smoke genres (format auto-detected from genre name) -->
        <template v-if="!isGenreSmoke">
          <span class="text-surface-600 select-none">|</span>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="s in sizes.filter(s => s !== 7)"
              :key="s"
              @click="topSize = s"
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
            class="card-hover p-2 relative inline-flex items-center gap-1.5 px-3"
          >
            <div class="corner-bar-tl"></div>
            <span class="type-body text-content-primary">{{ j.name }}</span>
            <button
              @click="submitRemoveBattleJudge(j.name)"
              class="flex items-center justify-center hover:text-red-400 transition-colors"
            >
              <i class="pi pi-times text-xs"></i>
            </button>
          </div>
          <span v-if="!battleJudges?.judges?.length" class="type-label text-content-muted">None added</span>
        </div>

        <!-- Add control pushed to the right -->
        <div class="ml-auto flex items-center gap-2">
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

      <div class="flex flex-wrap items-center gap-2 mt-4 mb-5">
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
            <!-- remove button — visually separated strip -->
            <button
              @click="submitRemoveBattleGuest(g)"
              class="flex items-center justify-center px-2.5 flex-shrink-0 border-l border-surface-600/40 text-surface-400 hover:text-red-400 hover:bg-red-500/10 transition-colors"
              title="Remove guest"
            >
              <i class="pi pi-times" style="font-size:11px"></i>
            </button>
          </span>
          <span v-if="!guestsForCurrentGenre.length" class="type-label text-content-muted pt-1">None added</span>
        </div>

        <!-- Add guest form pushed to the right -->
        <div class="ml-auto flex flex-col gap-2 flex-shrink-0">
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
      <div class="mb-5">
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
            @click="activeRoundIdx = idx"
            class="para-chip-sm px-4 py-1.5 type-label transition-all duration-150"
            :class="activeRoundIdx === idx
              ? 'text-accent border-[color:var(--accent-muted)]'
              : 'text-content-muted hover:text-content-primary'"
          >Top {{ size }}</button>
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
                <!-- Start from this match -->
                <button
                  v-if="match[0] && match[1]"
                  @click="initiateBattlePairAt(`Top${size}`, rounds[`Top${size}`], mIdx)"
                  class="flex-shrink-0 flex items-center justify-center w-8 ml-1.5 self-stretch rounded text-accent border border-[color:var(--accent-muted)] bg-[color:var(--accent-subtle)] hover:bg-[color:var(--accent-muted)] transition-colors"
                  title="Start round from this match"
                ><i class="pi pi-play text-[10px]"></i></button>
              </div>
            </div>

            <button
              @click="initiateBattlePair(`Top${size}`, rounds[`Top${size}`])"
              class="w-full py-2 bg-accent para-chip type-label transition-all duration-200"
            >
              <i class="pi pi-play text-xs mr-1.5"></i>
              Start Round
            </button>
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
    </div> <!-- end merged card -->

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
            'semantic-chip-success': battlePhase === 'REVEALED',
            'semantic-chip-warning': battlePhase === 'LOCKED',
            'semantic-chip-warning animate-pulse': battlePhase === 'VOTING',
            'semantic-chip-warning opacity-50': battlePhase === 'IDLE',
          }"
        >
          <div
            class="w-2 h-2 rounded-full"
            :style="battlePhase === 'REVEALED' ? 'background:#34d399;box-shadow:0 0 8px rgba(52,211,153,0.8)' : battlePhase === 'LOCKED' ? 'background:#f59e0b;box-shadow:0 0 8px rgba(245,158,11,0.8)' : battlePhase === 'VOTING' ? 'background:#f59e0b;box-shadow:0 0 8px rgba(245,158,11,0.8)' : 'background:#6b7280;box-shadow:0 0 8px rgba(107,114,128,0.5)'"
          ></div>
          <span class="type-body" :class="battlePhase === 'REVEALED' ? 'text-emerald-400' : battlePhase === 'LOCKED' ? 'text-amber-400' : battlePhase === 'VOTING' ? 'text-amber-400' : 'text-gray-400'">{{ battlePhase }}</span>
        </div>
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

      <!-- Winner announcement -->
      <div
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

      <!-- Match pairs (standard) -->
      <div v-if="!isSmoke" class="grid grid-cols-3 gap-3 mb-4">
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
      <div v-else class="grid grid-cols-2 gap-3 mb-4">
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

      <!-- Action buttons -->
      <div class="flex flex-wrap gap-2">
        <!-- LOCKED: open voting -->
        <button
          v-if="battlePhase === 'LOCKED'"
          @click="openVoting"
          class="bg-accent para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
        >
          <i class="pi pi-lock-open text-xs"></i>
          Open Voting
        </button>

        <!-- VOTING: final + all judges voted + clear winner → one-click Reveal Champion -->
        <button
          v-if="showFinalReveal"
          @click="revealChampionForGenre"
          class="para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 border-accent transition-all"
        >
          <i class="pi pi-star text-xs"></i>
          Reveal Champion
        </button>

        <!-- VOTING: all other cases (non-final, not all voted, or tie) -->
        <button
          v-else-if="battlePhase === 'VOTING'"
          @click="submitGetScore"
          class="bg-accent para-chip-sm px-4 py-2 type-label inline-flex items-center gap-1.5 transition-all"
        >
          <i class="pi pi-bolt text-xs"></i>
          {{ (Number(currentWinner) === -1 && !isSmoke) ? 'Rematch' : 'Get Score' }}
        </button>

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

        <!-- Champion Reveal -->
        <button
          v-if="currentGenreChampion && !revealActive"
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
