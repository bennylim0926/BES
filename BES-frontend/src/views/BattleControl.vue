<script setup>
import ReusableButton from '@/components/ReusableButton.vue'
import ReusableDropdown from '@/components/ReusableDropdown.vue'
import { addBattleJudge, battleJudgeVote, getAllJudges, getBattleJudges, getParticipantScore, removeBattleJudge, setBattlePair, setBattleScore, updateSmokeList, uploadImage } from '@/utils/api'
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
    }
  }
}

const topParticipants = computed(() => {
  return [...new Set(participants.value.filter(p => p.genreName === selectedGenre.value).map(p => p.participantName))]
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
const availableForSeeding = computed(() => topNParticipants.value.filter(p => !seededNames.value.has(p)))
const allSeeded = computed(() => seeds.value.every(Boolean))

const resetSeeds = () => {
  seeds.value = Array(bracketSize.value).fill(null)
  activeSeedIdx.value = null
}
const rankAsc = ref(false) // false = desc (highest first), true = asc (lowest first)
const autoFillSeeds = () => {
  const pool = topNParticipants.value
  const ordered = rankAsc.value ? [...pool].reverse() : pool
  seeds.value = [...ordered, ...Array(Math.max(0, bracketSize.value - ordered.length)).fill(null)]
  activeSeedIdx.value = null
}

const highVsLowFill = () => {
  const pool = topNParticipants.value
  const n = pool.length
  const result = Array(bracketSize.value).fill(null)
  for (let i = 0; i < Math.ceil(n / 2); i++) {
    result[i * 2] = pool[i]
    if (n - 1 - i !== i) result[i * 2 + 1] = pool[n - 1 - i]
  }
  seeds.value = result
  activeSeedIdx.value = null
}

const randomFill = () => {
  const pool = [...topNParticipants.value].sort(() => Math.random() - 0.5)
  seeds.value = [...pool, ...Array(Math.max(0, bracketSize.value - pool.length)).fill(null)]
  activeSeedIdx.value = null
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
}
const applyToFirstRound = () => {
  if (isSmoke.value) {
    rounds.value = seeds.value.map((name, i) => ({
      name: name ?? rounds.value[i]?.name ?? null,
      score: rounds.value[i]?.score ?? 0,
    }))
    return
  }
  const key = `Top${bracketSize.value}`
  if (!rounds.value[key]) return
  for (let i = 0; i < bracketSize.value / 2; i++) {
    rounds.value[key][i][0] = seeds.value[i * 2] ?? null
    rounds.value[key][i][1] = seeds.value[i * 2 + 1] ?? null
  }
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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

function updateMatch(roundKey, matchIdx, slotIdx, value) {
  rounds.value[roundKey][matchIdx][slotIdx] = value
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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
  if (!nextRoundSize) return
  const nextRoundKey = `Top${nextRoundSize}`
  const nextMatchIdx = Math.floor(matchIdx / 2)
  const nextSlotIdx = matchIdx % 2
  rounds.value[nextRoundKey][nextMatchIdx][nextSlotIdx] = winner
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
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

const clearLocalStorage = () => {
  localStorage.removeItem(`Top${topSize.value}${selectedGenre.value}Rounds`)
  rounds.value = initRounds()
}

watch(selectedEvent, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedEvent", newVal)
    const res = await getParticipantScore(newVal)
    participants.value = res.sort((a, b) => b.score - a.score)
  }
}, { immediate: true })

watch(selectedGenre, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedGenre", newVal)
    const storedRounds = localStorage.getItem(`Top${topSize.value}${newVal}Rounds`)
    rounds.value = JSON.parse(storedRounds) || initRounds()
  }
}, { immediate: true })

watch(topSize, async (newVal) => {
  if (newVal) {
    localStorage.setItem("topSize", newVal)
    const storedRounds = localStorage.getItem(`Top${newVal}${selectedGenre.value}Rounds`)
    rounds.value = JSON.parse(storedRounds) || initRounds()
    currentBattle.value = []
    currentWinner.value = -2
  }
}, { immediate: true })

onMounted(async () => {
  iintialiseDropdown()
  await fetchAllJudges()
  battleJudges.value = await getBattleJudges()
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
    </div>

    <!-- Config bar -->
    <div class="card p-5">
      <div class="grid grid-cols-2 sm:grid-cols-3 gap-4 mb-4">
        <div class="flex flex-col gap-1">
          <span class="text-xs font-semibold text-content-muted uppercase tracking-wide">Event</span>
          <span class="badge-neutral text-sm px-3 py-1.5 self-start">{{ selectedEvent }}</span>
        </div>
        <ReusableDropdown v-model="selectedGenre" labelId="Genre" :options="uniqueGenres" />
        <ReusableDropdown v-model="topSize" labelId="Format" :options="sizes" />
      </div>

      <!-- Judge management -->
      <div class="flex flex-wrap items-end gap-3 pt-4 border-t border-surface-600/30">
        <div class="w-48">
          <ReusableDropdown v-model="selectedJudge" labelId="Add Judge" :options="allJudgeOptions" />
        </div>
        <button
          @click="submitAddBattleJudge(selectedJudge)"
          class="flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-surface-800 text-white text-sm
                 font-semibold hover:bg-surface-900 transition-all duration-200"
        >
          <i class="pi pi-plus text-xs"></i>
          Add
        </button>

        <!-- Active judges -->
        <div class="flex flex-wrap gap-2">
          <span
            v-for="(j, index) in battleJudges?.judges || []"
            :key="index"
            class="badge-neutral inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-surface-700
                   text-sm font-medium text-content-secondary border border-surface-600"
          >
            {{ j.name }}
            <button
              @click="submitRemoveBattleJudge(j.name)"
              class="w-4 h-4 rounded-full flex items-center justify-center
                     hover:bg-surface-300 transition-colors"
            >
              <i class="pi pi-times text-xs text-content-muted"></i>
            </button>
          </span>
        </div>
      </div>
    </div>

    <!-- Bracket setup -->
    <div class="card p-5">
      <h2 class="font-heading font-bold text-content-secondary mb-5">Bracket</h2>

      <!-- ── Seeding panel ─────────────────────────────── -->
      <div class="mb-6">
        <div class="flex items-center justify-between mb-3">
          <div>
            <h3 class="text-sm font-bold text-content-primary">
              Participant Seeding
              <span class="ml-2 text-xs font-normal text-content-muted">
                {{ seededNames.size }} / {{ bracketSize }} selected
              </span>
            </h3>
            <p class="text-xs text-content-muted mt-0.5">
              Select {{ bracketSize }} participants, then apply to bracket
            </p>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <!-- Fill strategy buttons -->
            <div class="flex rounded-xl border border-surface-600/60 overflow-hidden bg-surface-800/60 text-xs font-semibold">
              <button
                @click="autoFillSeeds"
                class="flex items-center gap-1 px-2.5 py-1.5 text-content-secondary border-r border-surface-600/60
                       hover:bg-surface-700 hover:text-content-primary transition-all"
                :title="rankAsc ? 'Lowest score first — click to fill' : 'Highest score first — click to fill'"
              >
                <i :class="rankAsc ? 'pi pi-sort-amount-up' : 'pi pi-sort-amount-down'" class="text-xs"></i>
                By Rank
                <button
                  @click.stop="rankAsc = !rankAsc; autoFillSeeds()"
                  class="ml-0.5 px-1 py-0.5 rounded text-xs font-mono text-primary-400 hover:bg-primary-500/20 transition-all"
                  :title="rankAsc ? 'Switch to highest first' : 'Switch to lowest first'"
                >{{ rankAsc ? '↑' : '↓' }}</button>
              </button>
              <button
                @click="highVsLowFill"
                class="flex items-center gap-1 px-2.5 py-1.5 text-content-secondary border-r border-surface-600/60
                       hover:bg-surface-700 hover:text-content-primary transition-all"
                title="Pair highest with lowest (1st vs last, 2nd vs 2nd-last...)"
              >
                <i class="pi pi-arrows-v text-xs"></i>
                High ↔ Low
              </button>
              <button
                @click="randomFill"
                class="flex items-center gap-1 px-2.5 py-1.5 text-content-secondary
                       hover:bg-surface-700 hover:text-content-primary transition-all"
                title="Random shuffle"
              >
                <i class="pi pi-refresh text-xs"></i>
                Random
              </button>
            </div>

            <button
              @click="applyToFirstRound"
              :disabled="!allSeeded"
              class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold transition-all
                     disabled:opacity-30 disabled:cursor-not-allowed"
              :class="allSeeded
                ? 'bg-primary-600 text-white hover:bg-primary-700'
                : 'bg-surface-700 text-content-muted border border-surface-600'"
            >
              <i class="pi pi-check text-xs"></i>
              Apply to Bracket
            </button>
          </div>
        </div>

        <!-- Seed slots grid -->
        <div class="grid grid-cols-4 gap-2 mb-3">
          <button
            v-for="(name, i) in seeds"
            :key="i"
            @click="clickSeedSlot(i)"
            class="relative flex items-center gap-2 px-3 py-2.5 rounded-xl border text-left transition-all duration-150"
            :class="[
              activeSeedIdx === i
                ? 'border-primary-500/70 bg-primary-500/10 ring-1 ring-primary-500/30'
                : name
                  ? 'border-surface-600/60 bg-surface-700/60 hover:border-surface-500'
                  : 'border-dashed border-surface-600/40 bg-surface-800/30 hover:border-surface-500/60',
            ]"
          >
            <span
              class="flex-shrink-0 w-6 h-6 rounded-lg flex items-center justify-center text-xs font-source font-bold"
              :class="name ? 'bg-primary-500/20 text-primary-400' : 'bg-surface-700 text-surface-500'"
            >{{ i + 1 }}</span>
            <span
              class="text-sm font-semibold truncate flex-1"
              :class="name ? 'text-content-primary' : 'text-content-muted'"
            >{{ name || 'Empty' }}</span>
            <i v-if="name" class="pi pi-times text-xs text-surface-500 hover:text-red-400 transition-colors flex-shrink-0"></i>
          </button>
        </div>

        <!-- Available participants picker -->
        <Transition name="fade-down">
          <div v-if="activeSeedIdx !== null" class="rounded-xl border border-primary-500/30 bg-surface-800/80 p-3">
            <p class="text-xs font-semibold text-primary-400 mb-2 uppercase tracking-wider">
              Selecting for slot #{{ activeSeedIdx + 1 }} — click a participant
            </p>
            <div v-if="availableForSeeding.length" class="flex flex-wrap gap-1.5">
              <button
                v-for="p in availableForSeeding"
                :key="p"
                @click="assignSeed(p)"
                class="px-3 py-1.5 rounded-lg border border-surface-600/60 bg-surface-700/60 text-sm font-semibold
                       text-content-secondary hover:border-primary-500/60 hover:bg-primary-500/10 hover:text-primary-400
                       transition-all duration-150"
              >{{ p }}</button>
            </div>
            <p v-else class="text-xs text-content-muted">All participants have been assigned.</p>
          </div>
        </Transition>
      </div>

      <div class="h-px bg-surface-600/30 mb-5"></div>

      <!-- ── Standard bracket ──────────────────────────── -->
      <div v-if="Number(topSize) !== 7" class="flex flex-wrap gap-4">
        <div
          v-for="(size, idx) in roundSizes"
          :key="idx"
          class="bg-surface-900/60 rounded-2xl p-4 border border-surface-600/50 min-w-[240px] flex-1"
        >
          <div class="text-xs font-bold text-primary-400 uppercase tracking-widest mb-3">
            Top {{ size }}
          </div>

          <div
            v-for="(match, mIdx) in rounds[`Top${size}`]"
            :key="mIdx"
            class="mb-3 rounded-xl border border-surface-600/40 bg-surface-800/50 overflow-hidden"
          >
            <!-- Player 0 -->
            <div
              class="flex items-center gap-2 px-3 py-2.5 transition-colors"
              :class="match[2] === match[0] && match[0] ? 'bg-emerald-500/10' : ''"
            >
              <i
                class="pi pi-crown text-xs flex-shrink-0 transition-colors"
                :class="match[2] === match[0] && match[0] ? 'text-amber-400' : 'text-surface-600'"
              ></i>
              <select
                class="flex-1 bg-transparent text-sm font-semibold outline-none cursor-pointer
                       text-content-primary disabled:text-content-muted"
                :class="match[2] === match[0] && match[0] ? 'text-emerald-400' : ''"
                v-model="rounds[`Top${size}`][mIdx][0]"
                @change="updateMatch(`Top${size}`, mIdx, 0, rounds[`Top${size}`][mIdx][0])"
              >
                <option :value="null" disabled>Select participant…</option>
                <option v-for="p in topNParticipants" :key="p" :value="p">{{ p }}</option>
              </select>
              <button
                :disabled="!match[0]"
                @click="setWinner(`Top${size}`, mIdx, 0)"
                class="flex-shrink-0 px-2 py-0.5 rounded-lg text-xs font-bold transition-all
                       disabled:opacity-20 disabled:cursor-not-allowed"
                :class="match[2] === match[0] && match[0]
                  ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                  : 'bg-surface-700 text-surface-400 border border-surface-600/50 hover:border-surface-500'"
              >{{ match[2] === match[0] && match[0] ? 'Winner' : 'Win' }}</button>
            </div>

            <!-- VS divider -->
            <div class="flex items-center gap-2 px-3 py-1 border-y border-surface-600/30 bg-surface-900/30">
              <div class="flex-1 h-px bg-surface-600/20"></div>
              <span class="text-xs font-bold text-surface-500">VS</span>
              <div class="flex-1 h-px bg-surface-600/20"></div>
            </div>

            <!-- Player 1 -->
            <div
              class="flex items-center gap-2 px-3 py-2.5 transition-colors"
              :class="match[2] === match[1] && match[1] ? 'bg-emerald-500/10' : ''"
            >
              <i
                class="pi pi-crown text-xs flex-shrink-0 transition-colors"
                :class="match[2] === match[1] && match[1] ? 'text-amber-400' : 'text-surface-600'"
              ></i>
              <select
                class="flex-1 bg-transparent text-sm font-semibold outline-none cursor-pointer text-content-primary"
                :class="match[2] === match[1] && match[1] ? 'text-emerald-400' : ''"
                v-model="rounds[`Top${size}`][mIdx][1]"
                @change="updateMatch(`Top${size}`, mIdx, 1, rounds[`Top${size}`][mIdx][1])"
              >
                <option :value="null" disabled>Select participant…</option>
                <option v-for="p in topNParticipants" :key="p" :value="p">{{ p }}</option>
              </select>
              <button
                :disabled="!match[1]"
                @click="setWinner(`Top${size}`, mIdx, 1)"
                class="flex-shrink-0 px-2 py-0.5 rounded-lg text-xs font-bold transition-all
                       disabled:opacity-20 disabled:cursor-not-allowed"
                :class="match[2] === match[1] && match[1]
                  ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                  : 'bg-surface-700 text-surface-400 border border-surface-600/50 hover:border-surface-500'"
              >{{ match[2] === match[1] && match[1] ? 'Winner' : 'Win' }}</button>
            </div>
          </div>

          <button
            @click="initiateBattlePair(`Top${size}`, rounds[`Top${size}`])"
            class="w-full mt-1 py-2.5 rounded-xl bg-primary-600 text-white text-xs font-bold
                   hover:bg-primary-700 active:bg-primary-800 transition-all duration-200"
          >
            <i class="pi pi-play text-xs mr-1.5"></i>
            Start Round
          </button>
        </div>
      </div>

      <!-- ── 7 to Smoke bracket ──────────────────────────── -->
      <div v-else class="bg-surface-900/60 rounded-2xl p-4 border border-surface-600/50">
        <div class="text-xs font-bold text-primary-400 uppercase tracking-widest mb-4">7 to Smoke</div>

        <!-- Slot grid -->
        <div class="grid grid-cols-2 gap-2 mb-3">
          <button
            v-for="(match, mIdx) in (Array.isArray(rounds.value) ? rounds.value : [])"
            :key="mIdx"
            @click="clickSmokeSlot(mIdx)"
            class="relative flex items-center gap-2 px-3 py-2.5 rounded-xl border text-left transition-all duration-150"
            :class="[
              activeSmokeIdx === mIdx
                ? 'border-primary-500/70 bg-primary-500/10 ring-1 ring-primary-500/30'
                : match.name
                  ? 'border-surface-600/60 bg-surface-700/60 hover:border-surface-500'
                  : 'border-dashed border-surface-600/40 bg-surface-800/30 hover:border-surface-500/60',
            ]"
          >
            <span
              class="flex-shrink-0 w-6 h-6 rounded-lg flex items-center justify-center text-xs font-source font-bold"
              :class="match.name ? 'bg-primary-500/20 text-primary-400' : 'bg-surface-700 text-surface-500'"
            >{{ mIdx + 1 }}</span>
            <span
              class="text-sm font-semibold truncate flex-1"
              :class="match.name ? 'text-content-primary' : 'text-content-muted'"
            >{{ match.name || 'Empty' }}</span>
            <i v-if="match.name" class="pi pi-times text-xs text-surface-500 hover:text-red-400 transition-colors flex-shrink-0"></i>
          </button>
        </div>

        <!-- Participant picker -->
        <Transition name="fade-down">
          <div v-if="activeSmokeIdx !== null" class="rounded-xl border border-primary-500/30 bg-surface-800/80 p-3 mb-3">
            <p class="text-xs font-semibold text-primary-400 mb-2 uppercase tracking-wider">
              Selecting for slot #{{ activeSmokeIdx + 1 }} — click a participant
            </p>
            <div v-if="availableForSmoke.length" class="flex flex-wrap gap-1.5">
              <button
                v-for="p in availableForSmoke"
                :key="p"
                @click="assignSmokeSlot(p)"
                class="px-3 py-1.5 rounded-lg border border-surface-600/60 bg-surface-700/60 text-sm font-semibold
                       text-content-secondary hover:border-primary-500/60 hover:bg-primary-500/10 hover:text-primary-400
                       transition-all duration-150"
              >{{ p }}</button>
            </div>
            <p v-else class="text-xs text-content-muted">All participants have been assigned.</p>
          </div>
        </Transition>

        <button
          @click="initiateBattlePair(0, 0)"
          class="w-full py-2.5 rounded-xl bg-primary-600 text-white text-xs font-bold
                 hover:bg-primary-700 transition-all duration-200"
        >
          <i class="pi pi-play text-xs mr-1.5"></i>
          Start Round
        </button>
      </div>
    </div>

    <!-- Live match tracker -->
    <div class="card p-5">
      <h2 class="font-heading font-bold text-content-secondary mb-4">Live Match</h2>

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
        <template v-if="Number(currentWinner) !== -2 && Number(currentWinner) !== -3 && (Number(currentWinner) !== -1 || isSmoke)">
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
        <template v-else>
          <button
            @click="submitGetScore"
            class="flex items-center gap-1.5 px-5 py-2.5 rounded-xl bg-primary-600 text-white text-sm
                   font-semibold hover:bg-primary-700 transition-all shadow-sm"
          >
            <i class="pi pi-bolt text-xs"></i>
            {{ (Number(currentWinner) === -1 && !isSmoke) ? 'Rematch' : 'Get Score' }}
          </button>
        </template>

        <button
          @click="clearLocalStorage"
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
