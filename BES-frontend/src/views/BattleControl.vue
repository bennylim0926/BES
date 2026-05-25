<script setup>
import ReusableButton from '@/components/ReusableButton.vue'
import ReusableDropdown from '@/components/ReusableDropdown.vue'
import { addBattleJudge, battleJudgeVote, getAllJudges, getBattleJudges, getBattlePhase, getParticipantScore, getPickupCrews, removeBattleJudge, setBattlePair, setBattlePhase, setBattleScore, setBracketState, updateSmokeList, uploadImage } from '@/utils/api'
import { deleteImage } from '@/utils/adminApi'
import { computed, onMounted, ref, watch, toRaw } from 'vue'
import { useDropdowns } from '@/utils/dropdown'
import { useEventUtils } from '@/utils/eventUtils'
import { useBattleLogic } from '@/utils/battleLogic'
import { subscribeToChannel, createClient } from '@/utils/websocket'

const { selectedEvent, selectedGenre, iintialiseDropdown, selectedJudge } = useDropdowns()
const { allJudges, fetchAllJudges, participants } = useEventUtils()
const { rounds, topSize, roundSizes, isSmoke, standardBattleRound, sevenToSmokeRound } = useBattleLogic()

const battleJudges = ref([])
const currentBattle = ref([])
const currentWinner = ref(-2)
const currentRound = ref(0)
const currentTop = ref('')
const battlePhase = ref('IDLE')
const showResetConfirm = ref(false)
const activeRoundIdx = ref(0)

const pickupCrews = ref([])
const crewSortMode = ref('leader')  // 'leader' | 'avg'

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
})
const nextBattlePair = computed(() => {
  if (currentBattle.value.length === 0) return
  if (isSmoke.value) return currentBattle?.value[2]
  if (currentBattle.value.length !== 0 && currentBattle?.value[0] < currentBattle?.value[1].length - 1) {
    return [currentBattle?.value[1][currentBattle?.value[0] + 1][0], currentBattle?.value[1][currentBattle?.value[0] + 1][1]]
  }
})

const resetJudgeVote = async () => {
  battleJudges.value.judges.forEach(async j => { await battleJudgeVote(j.id, -3) })
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

const initiateBattlePair = async (top, pairList) => {
  // Reset winner FIRST so the winnerAnnouncement computed doesn't fire setWinner
  // with stale winner + new round/top values when reactive state updates below.
  currentWinner.value = -2
  await resetJudgeVote()
  if (isSmoke.value) {
    await setBattlePair(rounds.value[0].name, rounds.value[1].name)
    await updateSmokePair()  // must await so smoke list is posted before overlay reloads
    return
  }
  currentBattle.value = [0, pairList]
  const left = currentBattle?.value[1][currentBattle?.value[0]][0]
  const right = currentBattle?.value[1][currentBattle?.value[0]][1]
  await setBattlePair(left, right)
  currentRound.value = 0
  currentTop.value = top
}

const prevPair = async () => {
  if (currentBattle.value.length !== 0 && currentBattle.value[0] > 0) {
    currentBattle.value = [currentBattle.value[0] - 1, currentBattle.value[1]]
    const left = currentBattle?.value[1][currentBattle?.value[0]][0]
    const right = currentBattle?.value[1][currentBattle?.value[0]][1]
    await setBattlePair(left, right)
    currentWinner.value = -2
    currentRound.value -= 1
  }
}

const nextPair = async () => {
  if (currentBattle.value.length === 0) return
  await resetJudgeVote()
  if (isSmoke.value) {
    await update7toSmokeMatch(currentWinner.value)
    await setBattlePair(rounds.value[0].name, rounds.value[1].name)
  } else {
    if (currentBattle?.value[0] < currentBattle?.value[1].length - 1) {
      currentBattle.value = [currentBattle.value[0] + 1, currentBattle.value[1]]
      const left = currentBattle?.value[1][currentBattle?.value[0]][0]
      const right = currentBattle?.value[1][currentBattle?.value[0]][1]
      await setBattlePair(left, right)
      currentWinner.value = -2
      currentRound.value += 1
    } else {
      // Last battle of this round — end match and return to IDLE
      await setBattlePhase('IDLE')
      battlePhase.value = 'IDLE'
      currentWinner.value = -2
      currentBattle.value = []
    }
  }
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

// Pool used by bracket slot dropdowns, seeding picker, and all sort/fill functions.
// When pre-formed teams exist, exclude raw individual names from the pool — team names only.
const bracketPool = computed(() => {
  if (isMixedBracket.value) {
    return [
      ...preFormedTeams.value.map(t => t.name),
      ...sortedPickupCrews.value.map(c => c.crewName),
    ]
  }
  // Pure team genre (pre-formed teams, no pickup crews) — only team names
  if (preFormedTeams.value.length > 0) {
    return preFormedTeams.value.map(t => t.name)
  }
  // Pure solo genre — original behavior
  return topNParticipants.value
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

const seededNames = computed(() => new Set(seeds.value.filter(Boolean)))
const availableForSeeding = computed(() => bracketPool.value.filter(p => !seededNames.value.has(p)))
const allSeeded = computed(() => seeds.value.every(Boolean))

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
  if (isMixedBracket.value) {
    const half = Math.floor(bracketSize.value / 2)
    const preformed = preFormedTeams.value.map(t => t.name)
    const pickup = sortedPickupCrews.value.map(c => c.crewName)
    const orderedPre = rankAsc.value ? [...preformed].reverse() : preformed
    const orderedPick = rankAsc.value ? [...pickup].reverse() : pickup
    seeds.value = [...fillHalf(orderedPre, half), ...fillHalf(orderedPick, half)]
  } else {
    const pool = bracketPool.value.slice(0, bracketSize.value)
    const ordered = rankAsc.value ? [...pool].reverse() : pool
    seeds.value = [...ordered, ...Array(Math.max(0, bracketSize.value - ordered.length)).fill(null)]
  }
  activeSeedIdx.value = null
  applyToFirstRound()
}

const highVsLowFill = () => {
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
    const half = Math.floor(bracketSize.value / 2)
    seeds.value = [
      ...hvl(preFormedTeams.value.map(t => t.name), half),
      ...hvl(sortedPickupCrews.value.map(c => c.crewName), half),
    ]
  } else {
    seeds.value = hvl(bracketPool.value.slice(0, bracketSize.value), bracketSize.value)
  }
  activeSeedIdx.value = null
  applyToFirstRound()
}

const randomFill = () => {
  if (isMixedBracket.value) {
    const half = Math.floor(bracketSize.value / 2)
    const preformed = [...preFormedTeams.value.map(t => t.name)].sort(() => Math.random() - 0.5)
    const pickup = [...sortedPickupCrews.value.map(c => c.crewName)].sort(() => Math.random() - 0.5)
    seeds.value = [...fillHalf(preformed, half), ...fillHalf(pickup, half)]
  } else {
    const pool = [...bracketPool.value.slice(0, bracketSize.value)].sort(() => Math.random() - 0.5)
    seeds.value = [...pool, ...Array(Math.max(0, bracketSize.value - pool.length)).fill(null)]
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
const clickSeedSlot = (idx) => {
  if (seeds.value[idx]) {
    const s = [...seeds.value]; s[idx] = null; seeds.value = s
    activeSeedIdx.value = null
  } else {
    activeSeedIdx.value = activeSeedIdx.value === idx ? null : idx
  }
}
const assignSeed = (name) => {
  if (activeSeedIdx.value === null) return
  const s = [...seeds.value]; s[activeSeedIdx.value] = name; seeds.value = s
  const next = s.findIndex((v, i) => i > activeSeedIdx.value && !v)
  activeSeedIdx.value = next !== -1 ? next : null
  if (s.every(Boolean)) applyToFirstRound()
}
const applyToFirstRound = () => {
  if (isSmoke.value) {
    rounds.value = seeds.value.map((name, i) => ({
      name: name ?? rounds.value[i]?.name ?? null,
      score: rounds.value[i]?.score ?? 0,
    }))
    broadcastBracket()
    return
  }
  const key = `Top${bracketSize.value}`
  if (!rounds.value[key]) return
  for (let i = 0; i < bracketSize.value / 2; i++) {
    rounds.value[key][i][0] = seeds.value[i * 2] ?? null
    rounds.value[key][i][1] = seeds.value[i * 2 + 1] ?? null
  }
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
  broadcastBracket()
}

watch([bracketSize, selectedGenre], resetSeeds, { immediate: true })

// 7 to Smoke slot picker
const activeSmokeIdx = ref(null)
const smokeSeededNames = computed(() => new Set((Array.isArray(rounds.value) ? rounds.value : []).map(r => r.name).filter(Boolean)))
const availableForSmoke = computed(() => topNParticipants.value.filter(p => !smokeSeededNames.value.has(p)))
const clickSmokeSlot = (idx) => {
  if (Array.isArray(rounds.value) && rounds.value[idx]?.name) {
    rounds.value[idx].name = null
    activeSmokeIdx.value = null
  } else {
    activeSmokeIdx.value = activeSmokeIdx.value === idx ? null : idx
  }
}
const assignSmokeSlot = (name) => {
  if (activeSmokeIdx.value === null || !Array.isArray(rounds.value)) return
  rounds.value[activeSmokeIdx.value].name = name
  const next = rounds.value.findIndex((r, i) => i > activeSmokeIdx.value && !r.name)
  activeSmokeIdx.value = next !== -1 ? next : null
}

const sizes = ref([7, 8, 16, 32])

function initRounds() {
  rounds.value = {}
  if (isSmoke.value) return sevenToSmokeRound()
  return standardBattleRound()
}

const broadcastBracket = () => setBracketState(toRaw(rounds.value), topSize.value)

function updateMatch(roundKey, matchIdx, slotIdx, value) {
  rounds.value[roundKey][matchIdx][slotIdx] = value
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

const allJudgeOptions = computed(() => ["", ...Object.values(allJudges.value).map(j => j.judgeName)])

const submitAddBattleJudge = async (name) => {
  const j = allJudges.value.find(j => j.judgeName === name)
  const res = await addBattleJudge(j?.judgeId)
  if (res.status === 404) console.log("No judge in database")
  battleJudges.value = await getBattleJudges()
}

const submitRemoveBattleJudge = async (name) => {
  const j = allJudges.value.find(j => j.judgeName === name)
  await removeBattleJudge(j?.judgeId)
  battleJudges.value = await getBattleJudges()
}

const submitGetScore = async () => {
  battleJudges.value = await getBattleJudges()
  const hasMinusThree = battleJudges?.value.judges.some(j => j.vote === -3)
  if (hasMinusThree) { currentWinner.value = -3; return }
  if (isSmoke.value) {
    const res = await setBattleScore()
    const data = await res.json()
    currentWinner.value = Number(data.winner)
    return
  }
  if (currentBattle.value.length === 0) return
  if (currentWinner.value === -1) {
    currentWinner.value = -2
    await resetJudgeVote()
    // Re-broadcast same pair so the overlay hides the judge panel and resets battler animations
    const [rLeft, rRight] = currentBattlePair.value ?? []
    if (rLeft && rRight) await setBattlePair(rLeft, rRight)
    return
  }
  const left = currentBattle?.value[1][currentBattle?.value[0]][0]
  const right = currentBattle?.value[1][currentBattle?.value[0]][1]
  if (left === "" || right === "") return
  const res = await setBattleScore()
  const data = await res.json()
  currentWinner.value = Number(data.winner)
  if (data.winner === 1 || data.winner === 0) { setWinner(currentTop.value, currentRound.value, data.winner) }
}

const openVoting = async () => {
  await setBattlePhase('VOTING')
  battlePhase.value = 'VOTING'
}

const confirmResetBracket = async () => {
  showResetConfirm.value = false
  localStorage.removeItem(`Top${topSize.value}${selectedGenre.value}Rounds`)
  rounds.value = initRounds()
  broadcastBracket()
  await setBattlePhase('IDLE')
  battlePhase.value = 'IDLE'
  currentWinner.value = -2
  currentBattle.value = []
}

watch(selectedEvent, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedEvent", newVal)
    const res = await getParticipantScore(newVal)
    participants.value = res.sort((a, b) => b.score - a.score)
    pickupCrews.value = []
    await fetchAllJudges(newVal)
  }
}, { immediate: true })

watch(selectedGenre, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedGenre", newVal)
    const storedRounds = localStorage.getItem(`Top${topSize.value}${newVal}Rounds`)
    rounds.value = JSON.parse(storedRounds) || initRounds()
    pickupCrews.value = await getPickupCrews(selectedEvent.value, newVal)
    broadcastBracket()
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

onMounted(async () => {
  iintialiseDropdown()
  await fetchAllJudges(selectedEvent.value)
  battleJudges.value = await getBattleJudges()
  const phaseData = await getBattlePhase()
  battlePhase.value = phaseData?.phase ?? 'IDLE'
  subscribeToChannel(createClient(), '/topic/battle/phase', (msg) => {
    battlePhase.value = msg.phase
  })
})
</script>

<template>
  <div class="page-container space-y-6">

    <!-- Page header -->
    <div>
      <h1 class="page-title">Battle Control</h1>
      <p class="text-muted mt-1">Manage brackets, rounds, and live voting</p>
    </div>

    <!-- Quick access links -->
    <div class="flex flex-wrap gap-2">
      <a
        href="/battle/overlay"
        target="_blank"
        rel="noopener noreferrer"
        class="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border border-surface-600 bg-surface-800
               text-sm font-semibold text-content-secondary hover:border-primary-400 hover:text-primary-400
               transition-all duration-200"
      >
        <i class="pi pi-desktop text-xs"></i>
        Stream Overlay
        <i class="pi pi-external-link text-xs text-content-muted"></i>
      </a>
      <a
        href="/battle/overlay?isSmoke=true"
        target="_blank"
        rel="noopener noreferrer"
        class="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border border-surface-600 bg-surface-800
               text-sm font-semibold text-content-secondary hover:border-primary-400 hover:text-primary-400
               transition-all duration-200"
      >
        <i class="pi pi-desktop text-xs"></i>
        Smoke Overlay
        <i class="pi pi-external-link text-xs text-content-muted"></i>
      </a>
      <a
        href="/battle/judge"
        target="_blank"
        rel="noopener noreferrer"
        class="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border border-surface-600 bg-surface-800
               text-sm font-semibold text-content-secondary hover:border-primary-400 hover:text-primary-400
               transition-all duration-200"
      >
        <i class="pi pi-users text-xs"></i>
        Judge View
        <i class="pi pi-external-link text-xs text-content-muted"></i>
      </a>
      <a
        href="/battle/bracket"
        target="_blank"
        rel="noopener noreferrer"
        class="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl border border-surface-600 bg-surface-800
               text-sm font-semibold text-content-secondary hover:border-primary-400 hover:text-primary-400
               transition-all duration-200"
      >
        <i class="pi pi-sitemap text-xs"></i>
        Live Bracket
        <i class="pi pi-external-link text-xs text-content-muted"></i>
      </a>
    </div>

    <!-- Config bar + Bracket (merged) -->
    <div class="card p-5">
      <div class="flex flex-wrap items-center gap-3 mb-4">
        <!-- Event name -->
        <span class="font-heading font-bold text-base text-content-primary whitespace-nowrap">{{ selectedEvent }}</span>
        <span class="text-surface-600 select-none">|</span>

        <!-- Genre toggle -->
        <div class="flex rounded-xl overflow-hidden border border-surface-600">
          <button
            v-for="g in uniqueGenres"
            :key="g"
            @click="selectedGenre = g"
            class="px-3.5 py-1.5 text-sm font-semibold transition-all duration-150"
            :class="selectedGenre === g
              ? 'bg-primary-600 text-white'
              : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
          >{{ g }}</button>
        </div>
        <span class="text-surface-600 select-none">|</span>

        <!-- Format toggle -->
        <div class="flex rounded-xl overflow-hidden border border-surface-600">
          <button
            v-for="s in sizes"
            :key="s"
            @click="topSize = s"
            class="px-3.5 py-1.5 text-sm font-semibold transition-all duration-150"
            :class="topSize === s
              ? 'bg-primary-600 text-white'
              : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
          >{{ s === 7 ? '7 to Smoke' : `Top ${s}` }}</button>
        </div>
      </div>

      <!-- Judge management -->
      <div class="flex flex-wrap items-center gap-3 pt-4 border-t border-surface-600/30">
        <span class="text-xs font-semibold text-content-muted uppercase tracking-wide whitespace-nowrap">Judges</span>

        <!-- Active judge pills -->
        <div class="flex flex-wrap gap-2">
          <span
            v-for="(j, index) in battleJudges?.judges || []"
            :key="index"
            class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-sm font-semibold
                   text-content-secondary bg-surface-700 border border-surface-600"
          >
            {{ j.name }}
            <button
              @click="submitRemoveBattleJudge(j.name)"
              class="flex items-center justify-center hover:text-red-400 transition-colors"
            >
              <i class="pi pi-times text-xs"></i>
            </button>
          </span>
          <span v-if="!battleJudges?.judges?.length" class="text-xs text-content-muted italic">None added</span>
        </div>

        <!-- Add control pushed to the right -->
        <div class="ml-auto flex items-center gap-2">
          <div class="w-44">
            <ReusableDropdown v-model="selectedJudge" labelId="" :options="allJudgeOptions" />
          </div>
          <button
            @click="submitAddBattleJudge(selectedJudge)"
            class="flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-primary-600 text-white text-sm
                   font-semibold hover:bg-primary-700 transition-all duration-200 whitespace-nowrap"
          >
            <i class="pi pi-plus text-xs"></i>
            Add
          </button>
        </div>
      </div>

      <div class="h-px bg-surface-600/30 my-4"></div>

      <!-- ── Seeding quick-fill ───────────────────────────── -->
      <div class="flex flex-wrap items-center gap-3 mb-5">
        <span class="text-xs font-semibold text-content-muted uppercase tracking-wide">Seed by</span>

        <!-- Pickup crew sort toggle (mixed bracket only) -->
        <template v-if="isMixedBracket">
          <div class="flex rounded-lg border border-surface-600/60 overflow-hidden text-xs font-semibold">
            <button
              @click="crewSortMode = 'leader'"
              class="px-2.5 py-1.5 transition-all"
              :class="crewSortMode === 'leader' ? 'bg-primary-600 text-white' : 'bg-surface-800 text-content-muted hover:text-content-primary'"
              title="Sort pickup crews by their leader's individual audition score"
            >Leader</button>
            <button
              @click="crewSortMode = 'avg'"
              class="px-2.5 py-1.5 transition-all"
              :class="crewSortMode === 'avg' ? 'bg-primary-600 text-white' : 'bg-surface-800 text-content-muted hover:text-content-primary'"
              title="Sort pickup crews by average score of all members"
            >Avg</button>
          </div>
          <span class="text-surface-600 select-none">|</span>
        </template>

        <div class="flex rounded-xl border border-surface-600/60 overflow-hidden text-xs font-semibold">
          <button
            @click="autoFillSeeds"
            class="flex items-center gap-1 px-3 py-1.5 text-content-secondary border-r border-surface-600/60
                   hover:bg-surface-700 hover:text-content-primary transition-all"
            :title="rankAsc ? 'Lowest score first' : 'Highest score first'"
          >
            <i :class="rankAsc ? 'pi pi-sort-amount-up' : 'pi pi-sort-amount-down'" class="text-xs"></i>
            By Rank
            <button
              @click.stop="rankAsc = !rankAsc; autoFillSeeds()"
              class="ml-0.5 px-1 py-0.5 rounded font-mono text-primary-400 hover:bg-primary-500/20 transition-all"
              :title="rankAsc ? 'Switch to highest first' : 'Switch to lowest first'"
            >{{ rankAsc ? '↑' : '↓' }}</button>
          </button>
          <button
            @click="highVsLowFill"
            class="flex items-center gap-1 px-3 py-1.5 text-content-secondary border-r border-surface-600/60
                   hover:bg-surface-700 hover:text-content-primary transition-all"
            title="Pair highest with lowest (1st vs last, 2nd vs 2nd-last...)"
          >
            <i class="pi pi-arrows-v text-xs"></i>
            High ↔ Low
          </button>
          <button
            @click="randomFill"
            class="flex items-center gap-1 px-3 py-1.5 text-content-secondary transition-all"
            :class="isMixedBracket ? 'border-r border-surface-600/60 hover:bg-surface-700 hover:text-content-primary' : 'hover:bg-surface-700 hover:text-content-primary'"
            title="Random shuffle"
          >
            <i class="pi pi-refresh text-xs"></i>
            Random
          </button>
          <button
            v-if="isMixedBracket"
            @click="splitBracketFill"
            class="flex items-center gap-1 px-3 py-1.5 text-primary-400 hover:bg-primary-500/10 transition-all"
            title="Pre-formed teams on left half, pickup crews on right half"
          >
            <i class="pi pi-table text-xs"></i>
            Split
          </button>
        </div>
      </div>

      <!-- ── Standard bracket ──────────────────────────── -->
      <div v-if="Number(topSize) !== 7">
        <!-- Round tabs -->
        <div class="flex rounded-xl overflow-hidden border border-surface-600 self-start mb-4 w-fit">
          <button
            v-for="(size, idx) in roundSizes"
            :key="idx"
            @click="activeRoundIdx = idx"
            class="px-5 py-2 text-sm font-semibold transition-all duration-150"
            :class="activeRoundIdx === idx
              ? 'bg-primary-600 text-white'
              : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
          >Top {{ size }}</button>
        </div>

        <!-- Active round matches -->
        <template v-for="(size, idx) in roundSizes" :key="idx">
          <div v-if="activeRoundIdx === idx" class="bg-surface-900/60 rounded-2xl p-4 border border-surface-600/50">
            <!-- 2-col grid for 4+ matches, single col otherwise -->
            <div
              class="mb-3"
              :class="rounds[`Top${size}`]?.length >= 4 ? 'grid grid-cols-2 gap-2' : 'flex flex-col gap-2'"
            >
              <div
                v-for="(match, mIdx) in rounds[`Top${size}`]"
                :key="mIdx"
                class="rounded-lg border border-surface-600/40 bg-surface-800/50 overflow-hidden"
              >
                <!-- Player 0 -->
                <div
                  class="flex items-center gap-1.5 px-2 py-1.5 transition-colors"
                  :class="match[2] === match[0] && match[0] ? 'bg-emerald-500/10' : ''"
                >
                  <i
                    class="pi pi-crown text-xs flex-shrink-0 transition-colors"
                    :class="match[2] === match[0] && match[0] ? 'text-amber-400' : 'text-surface-600'"
                  ></i>
                  <select
                    class="flex-1 min-w-0 bg-transparent text-xs font-semibold outline-none cursor-pointer
                           text-content-primary disabled:text-content-muted"
                    :class="match[2] === match[0] && match[0] ? 'text-emerald-400' : ''"
                    v-model="rounds[`Top${size}`][mIdx][0]"
                    @change="updateMatch(`Top${size}`, mIdx, 0, rounds[`Top${size}`][mIdx][0])"
                  >
                    <option :value="null" disabled>Select…</option>
                    <option v-for="p in bracketPool" :key="p" :value="p">{{ p }}</option>
                  </select>
                  <button
                    :disabled="!match[0]"
                    @click="match[2] === match[0] && match[0] ? clearWinner(`Top${size}`, mIdx) : setWinner(`Top${size}`, mIdx, 0)"
                    class="flex-shrink-0 w-10 text-center rounded text-xs font-bold transition-all
                           disabled:opacity-20 disabled:cursor-not-allowed leading-5"
                    :class="match[2] === match[0] && match[0]
                      ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 hover:bg-red-500/20 hover:text-red-400 hover:border-red-500/40'
                      : 'bg-surface-700 text-surface-400 border border-surface-600/50 hover:border-surface-500'"
                    :title="match[2] === match[0] && match[0] ? 'Click to unselect winner' : 'Set as winner'"
                  >{{ match[2] === match[0] && match[0] ? '✓' : 'Win' }}</button>
                </div>

                <!-- VS divider -->
                <div class="flex items-center gap-1.5 px-2 border-y border-surface-600/30 bg-surface-900/30">
                  <div class="flex-1 h-px bg-surface-600/20"></div>
                  <span class="text-xs font-bold text-surface-600 py-0.5">VS</span>
                  <div class="flex-1 h-px bg-surface-600/20"></div>
                </div>

                <!-- Player 1 -->
                <div
                  class="flex items-center gap-1.5 px-2 py-1.5 transition-colors"
                  :class="match[2] === match[1] && match[1] ? 'bg-emerald-500/10' : ''"
                >
                  <i
                    class="pi pi-crown text-xs flex-shrink-0 transition-colors"
                    :class="match[2] === match[1] && match[1] ? 'text-amber-400' : 'text-surface-600'"
                  ></i>
                  <select
                    class="flex-1 min-w-0 bg-transparent text-xs font-semibold outline-none cursor-pointer text-content-primary"
                    :class="match[2] === match[1] && match[1] ? 'text-emerald-400' : ''"
                    v-model="rounds[`Top${size}`][mIdx][1]"
                    @change="updateMatch(`Top${size}`, mIdx, 1, rounds[`Top${size}`][mIdx][1])"
                  >
                    <option :value="null" disabled>Select…</option>
                    <option v-for="p in bracketPool" :key="p" :value="p">{{ p }}</option>
                  </select>
                  <button
                    :disabled="!match[1]"
                    @click="match[2] === match[1] && match[1] ? clearWinner(`Top${size}`, mIdx) : setWinner(`Top${size}`, mIdx, 1)"
                    class="flex-shrink-0 w-10 text-center rounded text-xs font-bold transition-all
                           disabled:opacity-20 disabled:cursor-not-allowed leading-5"
                    :class="match[2] === match[1] && match[1]
                      ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 hover:bg-red-500/20 hover:text-red-400 hover:border-red-500/40'
                      : 'bg-surface-700 text-surface-400 border border-surface-600/50 hover:border-surface-500'"
                    :title="match[2] === match[1] && match[1] ? 'Click to unselect winner' : 'Set as winner'"
                  >{{ match[2] === match[1] && match[1] ? '✓' : 'Win' }}</button>
                </div>
              </div>
            </div>

            <button
              @click="initiateBattlePair(`Top${size}`, rounds[`Top${size}`])"
              class="w-full py-2 rounded-xl bg-primary-600 text-white text-xs font-bold
                     hover:bg-primary-700 active:bg-primary-800 transition-all duration-200"
            >
              <i class="pi pi-play text-xs mr-1.5"></i>
              Start Round
            </button>
          </div>
        </template>
      </div>

      <!-- ── 7 to Smoke bracket ──────────────────────────── -->
      <div v-else class="bg-surface-900/60 rounded-2xl p-4 border border-surface-600/50">
        <div class="text-xs font-bold text-primary-400 uppercase tracking-widest mb-3">7 to Smoke — Queue</div>

        <!-- Queue: ordered chips, populated by fill buttons above -->
        <div v-if="Array.isArray(rounds) && rounds.some(r => r.name)" class="flex flex-wrap gap-2 mb-4">
          <div
            v-for="(match, mIdx) in rounds"
            :key="mIdx"
            class="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-surface-600/40 bg-surface-800/50 text-sm font-semibold text-content-secondary"
          >
            <span class="text-xs font-source text-primary-400 font-bold">{{ mIdx + 1 }}</span>
            <span>{{ match.name || '—' }}</span>
          </div>
        </div>
        <p v-else class="text-xs text-content-muted mb-4">Use the Seed by buttons above to fill the queue.</p>

        <button
          @click="initiateBattlePair(0, 0)"
          class="w-full py-2 rounded-xl bg-primary-600 text-white text-xs font-bold
                 hover:bg-primary-700 transition-all duration-200"
        >
          <i class="pi pi-play text-xs mr-1.5"></i>
          Start Round
        </button>
      </div>
    </div> <!-- end merged card -->

    <!-- Reset bracket confirmation modal -->
    <Transition name="fade">
      <div v-if="showResetConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
        <div class="bg-surface-800 rounded-2xl border border-surface-600 p-6 max-w-sm w-full mx-4 shadow-xl">
          <h3 class="font-heading font-bold text-content-primary text-lg mb-2">Reset Bracket?</h3>
          <p class="text-sm text-content-muted mb-6">This will clear all bracket data and set the battle phase to IDLE. This cannot be undone.</p>
          <div class="flex gap-3 justify-end">
            <button
              @click="showResetConfirm = false"
              class="px-4 py-2 rounded-xl border border-surface-600 bg-surface-700 text-sm font-semibold
                     text-content-secondary hover:bg-surface-600 transition-all"
            >Cancel</button>
            <button
              @click="confirmResetBracket"
              class="px-4 py-2 rounded-xl bg-red-600 text-white text-sm font-semibold hover:bg-red-700 transition-all"
            >Reset</button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Live match tracker -->
    <div class="card p-5">
      <div class="flex items-center gap-3 mb-4">
        <h2 class="font-heading font-bold text-content-secondary">Live Match</h2>
        <span
          class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-bold uppercase tracking-wide"
          :class="{
            'bg-surface-700 text-surface-400': battlePhase === 'IDLE',
            'bg-amber-500/20 text-amber-400 border border-amber-500/40': battlePhase === 'LOCKED',
            'bg-primary-500/20 text-primary-400 border border-primary-500/40 animate-pulse': battlePhase === 'VOTING',
            'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40': battlePhase === 'REVEALED',
          }"
        >{{ battlePhase }}</span>
      </div>

      <!-- Winner announcement -->
      <div
        class="px-4 py-3 rounded-xl text-center text-sm font-semibold mb-4"
        :class="{
          'bg-amber-950 text-amber-400 border border-amber-700/50': winnerVariant === 'ongoing',
          'bg-amber-950 text-amber-400 border border-amber-700/50': winnerVariant === 'wait',
          'bg-primary-100 text-primary-400 border border-primary-500/40': winnerVariant === 'winner',
          'bg-surface-700 text-content-secondary border border-surface-500': winnerVariant === 'tie',
        }"
      >
        {{ winnerAnnouncement }}
      </div>

      <!-- Match pairs (standard) -->
      <div v-if="!isSmoke" class="grid grid-cols-3 gap-3 mb-4">
        <div class="card p-4 text-sm">
          <div class="text-xs font-semibold text-content-muted uppercase tracking-wider mb-2">Previous</div>
          <template v-if="previousBattlePair">
            <span class="font-bold text-content-secondary">{{ previousBattlePair[0] }}</span>
            <span class="text-content-muted mx-2">vs</span>
            <span class="font-bold text-content-secondary">{{ previousBattlePair[1] }}</span>
          </template>
          <span v-else class="text-content-disabled">—</span>
        </div>
        <div class="card p-4 text-sm ring-2 ring-primary-200">
          <div class="text-xs font-semibold text-primary-500 uppercase tracking-wider mb-2">Current</div>
          <template v-if="currentBattlePair">
            <span class="font-bold text-content-primary">{{ currentBattlePair[0] }}</span>
            <span class="text-content-muted mx-2">vs</span>
            <span class="font-bold text-content-primary">{{ currentBattlePair[1] }}</span>
          </template>
          <span v-else class="text-content-disabled">—</span>
        </div>
        <div class="card p-4 text-sm">
          <div class="text-xs font-semibold text-content-muted uppercase tracking-wider mb-2">Next</div>
          <template v-if="nextBattlePair">
            <span class="font-bold text-content-secondary">{{ nextBattlePair[0] }}</span>
            <span class="text-content-muted mx-2">vs</span>
            <span class="font-bold text-content-secondary">{{ nextBattlePair[1] }}</span>
          </template>
          <span v-else class="text-content-disabled">—</span>
        </div>
      </div>

      <!-- Match pairs (smoke) -->
      <div v-else class="grid grid-cols-2 gap-3 mb-4">
        <div class="card p-4 text-sm ring-2 ring-primary-200">
          <div class="text-xs font-semibold text-primary-500 uppercase tracking-wider mb-2">Current Match</div>
          <template v-if="currentBattlePair">
            <span class="font-bold text-content-primary">{{ currentBattlePair[0].name }} ({{ currentBattlePair[0].score }})</span>
            <span class="text-content-muted mx-2">vs</span>
            <span class="font-bold text-content-primary">{{ currentBattlePair[1].name }} ({{ currentBattlePair[1].score }})</span>
          </template>
        </div>
        <div class="card p-4 text-sm">
          <div class="text-xs font-semibold text-content-muted uppercase tracking-wider mb-2">Queue</div>
          <span v-if="nextBattlePair" class="text-content-muted">{{ nextBattlePair.map(p => p.name).join(', ') }}</span>
          <span v-else class="text-content-disabled">—</span>
        </div>
      </div>

      <!-- Action buttons -->
      <div class="flex flex-wrap gap-2">
        <!-- LOCKED: open voting -->
        <button
          v-if="battlePhase === 'LOCKED'"
          @click="openVoting"
          class="flex items-center gap-1.5 px-5 py-2.5 rounded-xl bg-primary-600 text-white text-sm
                 font-semibold hover:bg-primary-700 transition-all shadow-sm"
        >
          <i class="pi pi-lock-open text-xs"></i>
          Open Voting
        </button>

        <!-- VOTING: get score / rematch -->
        <button
          v-if="battlePhase === 'VOTING'"
          @click="submitGetScore"
          class="flex items-center gap-1.5 px-5 py-2.5 rounded-xl bg-primary-600 text-white text-sm
                 font-semibold hover:bg-primary-700 transition-all shadow-sm"
        >
          <i class="pi pi-bolt text-xs"></i>
          {{ (Number(currentWinner) === -1 && !isSmoke) ? 'Rematch' : 'Get Score' }}
        </button>

        <!-- REVEALED: previous + next -->
        <template v-if="battlePhase === 'REVEALED'">
          <button
            @click="prevPair"
            class="flex items-center gap-1.5 px-4 py-2.5 rounded-xl border border-surface-600 bg-surface-800
                   text-sm font-semibold text-content-secondary hover:border-surface-500 transition-all"
          >
            <i class="pi pi-chevron-left text-xs"></i>
            Previous
          </button>
          <button
            @click="nextPair"
            class="flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-primary-600 text-white text-sm
                   font-semibold hover:bg-primary-700 transition-all shadow-sm"
          >
            Next
            <i class="pi pi-chevron-right text-xs"></i>
          </button>
        </template>

        <button
          @click="showResetConfirm = true"
          class="flex items-center gap-1.5 px-4 py-2.5 rounded-xl border border-red-200 bg-surface-800
                 text-sm font-semibold text-red-400 hover:bg-red-950 transition-all"
        >
          <i class="pi pi-refresh text-xs"></i>
          Reset Bracket
        </button>

        <!-- File upload -->
        <label
          class="flex items-center gap-1.5 px-4 py-2.5 rounded-xl border border-surface-600 bg-surface-800
                 text-sm font-semibold text-content-secondary hover:border-surface-500 cursor-pointer transition-all"
        >
          <i class="pi pi-upload text-xs"></i>
          Upload Images
          <input type="file" multiple @change="onFileChange" class="hidden" />
        </label>
      </div>

      <!-- Uploaded images list -->
      <div v-if="uploadedFiles.length > 0" class="mt-4 pt-4 border-t border-surface-600/30">
        <div class="flex items-center gap-2 mb-3">
          <i class="pi pi-images text-content-muted text-sm"></i>
          <span class="text-sm font-semibold text-content-secondary">Uploaded Images</span>
          <span class="badge-neutral inline-flex items-center justify-center w-5 h-5 rounded-full bg-surface-700
                       text-xs font-bold text-content-muted">{{ uploadedFiles.length }}</span>
        </div>
        <div class="flex flex-wrap gap-2">
          <div
            v-for="(name, idx) in uploadedFiles"
            :key="idx"
            class="flex items-center gap-2 px-3 py-2 rounded-xl border border-surface-600
                   bg-surface-900 text-sm text-content-secondary"
          >
            <span class="max-w-[160px] truncate">{{ name }}</span>
            <button
              @click="removeUploadedFile(idx)"
              class="flex-shrink-0 w-5 h-5 flex items-center justify-center rounded-full
                     hover:bg-surface-600 transition-colors"
            >
              <i class="pi pi-times text-xs text-content-muted"></i>
            </button>
          </div>
        </div>
      </div>
    </div>

  </div>
</template>
