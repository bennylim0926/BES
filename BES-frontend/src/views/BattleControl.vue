<script setup>
import ReusableButton from '@/components/ReusableButton.vue'
import ReusableDropdown from '@/components/ReusableDropdown.vue'
import { addBattleJudge, fetchAllEvents, getAllJudges, getBattleJudges, getParticipantScore, removeBattleJudge, setBattlePair, setBattleScore, uploadImage } from '@/utils/api'
import { computed, onMounted, ref, watch, toRaw } from 'vue'

const selectedEvent = ref(localStorage.getItem("selectedEvent") || "")
const selectedGenre = ref(localStorage.getItem("selectedGenre") || "All")
const filteredJudge = ref("")

const allJudges = ref([])
const allEvents = ref([])

const participants = ref([])

const battleJudges = ref([])

const currentBattle = ref([])
const currentWinner = ref(-2)

const currentRound = ref(0)
const currentTop = ref('')

const onFileChange = async(e)=>{
    const files = Array.from(e.target.files)
    for(const file of files){
        console.log(file)
        await uploadImage(file)
    }
}

const winnerAnnouncement = computed(()=>{
    if(currentWinner.value === -1){
        return "Its a tie"
    }else if(currentWinner.value === 0){
        setWinner(currentTop.value, currentRound.value, 0)
        return `${currentBattlePair?.value[currentWinner.value]} takes it`
    }
    else if(currentWinner.value === 1){
        setWinner(currentTop.value, currentRound.value, 1)
        return `${currentBattlePair?.value[currentWinner.value]} takes it`
    }
})

const previousBattlePair = computed(()=>{
    if(currentBattle.value.length !== 0 && currentBattle?.value[0]>0){
        const left = currentBattle?.value[1][currentBattle?.value[0]-1][0]
        const right = currentBattle?.value[1][currentBattle?.value[0]-1][1]
        return [left, right]
    }
})
const nextBattlePair = computed(()=>{
    // return currentBattle.value
    if(currentBattle.value.length !== 0 && currentBattle?.value[0] < currentBattle?.value[1].length-1){
        const left = currentBattle?.value[1][currentBattle?.value[0]+ 1][0]
        const right= currentBattle?.value[1][currentBattle?.value[0]+ 1][1]
        return [left, right]
    }
})

const currentBattlePair = computed(()=>{
    if(currentBattle.value.length !== 0){
        const left = currentBattle?.value[1][currentBattle?.value[0]][0]
        const right = currentBattle?.value[1][currentBattle?.value[0]][1]
        return [left, right]
    }
})

const initiateBattlePair = async(top, pairList) =>{
    currentBattle.value = [0, pairList]
    const left = currentBattle?.value[1][currentBattle?.value[0]][0]
    const right = currentBattle?.value[1][currentBattle?.value[0]][1]
    await setBattlePair(left, right)
    currentRound.value = 0
    currentTop.value = top
}

const prevPair = async () =>{
    if(currentBattle.value.length !== 0 && currentBattle.value[0] > 0){
        currentBattle.value = [currentBattle.value[0]-1, currentBattle.value[1]]
        const left = currentBattle?.value[1][currentBattle?.value[0]][0]
        const right = currentBattle?.value[1][currentBattle?.value[0]][1]
        await setBattlePair(left, right)
        currentWinner.value = -2;
        currentRound.value -= 1
    }
}

const nextPair = async () =>{
    if(currentBattle.value.length !== 0 && currentBattle?.value[0] < currentBattle?.value[1].length-1){
        currentBattle.value = [currentBattle.value[0]+1, currentBattle.value[1]]
        const left = currentBattle?.value[1][currentBattle?.value[0]][0]
        const right = currentBattle?.value[1][currentBattle?.value[0]][1]
        await setBattlePair(left, right)
        currentWinner.value = -2;
        currentRound.value += 1
    }
}

const topParticipants = computed(()=>{
    return [...new Set(
  participants.value
    .filter(p => p.genreName === selectedGenre.value)
    .map(p => p.participantName)
)];
})

const sizes  = ref([8, 16, 32])
// Start from Top 16
const topSize = ref(localStorage.getItem("topSize" || 16));

// Automatically generate rounds [16, 8, 4, 2]
const roundSizes = computed(() => {
  const arr = [];
  let cur = Number(topSize.value);
  while (cur >= 2) {
    arr.push(cur);
    cur /= 2;
  }
  return arr;
});


// Initialize rounds (each matchup = [player1, player2, winner])
const rounds = ref({});

function initRounds() {
  rounds.value = {};
  roundSizes.value.forEach(size => {
    const matches = [];
    for (let i = 0; i < size / 2; i++) {
      matches.push([null, null, null]); // [p1, p2, winner]
    }
    rounds.value[`Top${size}`] = matches;
  });
  return rounds.value
}

// Handle dropdown selection
function updateMatch(roundKey, matchIdx, slotIdx, value) {
  rounds.value[roundKey][matchIdx][slotIdx] = value;
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
}

// Handle winner checkbox selection
function setWinner(roundKey, matchIdx, slotIdx) {
  const match = rounds.value[roundKey][matchIdx];
  const winner = match[slotIdx];
  match[2] = winner; // set winner locally

  // Find next round key (e.g., Top16 → Top8)
  const roundIndex = roundSizes.value.indexOf(parseInt(roundKey.replace("Top", "")));
  const nextRoundSize = roundSizes.value[roundIndex + 1];
  if (!nextRoundSize) return; // no next round (Top 2)

  const nextRoundKey = `Top${nextRoundSize}`;
  const nextMatchIdx = Math.floor(matchIdx / 2); // each 2 matches -> 1 next match
  const nextSlotIdx = matchIdx % 2; // determines left/right in next round

  // Fill winner into next round
  rounds.value[nextRoundKey][nextMatchIdx][nextSlotIdx] = winner;
  localStorage.setItem(`Top${topSize.value}${selectedGenre.value}Rounds`, JSON.stringify(toRaw(rounds.value)))
}

// Clear future rounds if participant changes or new selection made
watch(rounds, (newVal) => {
  // Optional improvement: implement back-propagation cleanup
}, { deep: true });

const fetchEventsAndInit = async()=>{
    allEvents.value = await fetchAllEvents()
    const savedEvent = localStorage.getItem("selectedEvent")
    selectedEvent.value = savedEvent || (allEvents.value[0]?.folderName || "")
}

const uniqueGenres = computed(() => {
    const genres = participants.value.map(p => p.genreName);
    return [...new Set(genres)].sort();
})

const allJudgeOptions = computed(()=> {
    return ["", ...Object.values(allJudges.value).map(j => j.judgeName)];
})

const submitAddBattleJudge = async(name)=>{
    const j = allJudges.value.find(j => j.judgeName === name)
    const res = await addBattleJudge(j?.judgeId)
    if(res.status === 404) console.log("No judge in database")
    battleJudges.value = await getBattleJudges()
}

const submitRemoveBattleJudge = async(name) =>{
    const j = allJudges.value.find(j => j.judgeName === name)
    await removeBattleJudge(j?.judgeId)
    battleJudges.value = await getBattleJudges()
}

const submitGetScore = async()=>{
    if(currentBattle.value.length === 0) return 
    const left = currentBattle?.value[1][currentBattle?.value[0]][0]
    const right = currentBattle?.value[1][currentBattle?.value[0]][1]
    if(left === "" || right === "") return
    const res = await setBattleScore()
    const data = await res.json()
    currentWinner.value = Number(data.winner)
}

const clearLocalStorage = ()=>{
    localStorage.removeItem(`Top${topSize.value}${selectedGenre.value}Rounds`)
    rounds.value= initRounds()
}

watch(selectedEvent, async(newVal) =>{
    if(newVal){
        localStorage.setItem("selectedEvent", newVal);
        const res = await getParticipantScore(newVal)
        participants.value = res.sort((a,b) => b.score - a.score)
    }
}, {immediate: true})

watch(selectedGenre, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedGenre", newVal);
    const storedRounds = localStorage.getItem(`Top${topSize.value}${newVal}Rounds`)
    rounds.value = JSON.parse(storedRounds) || initRounds()
    
  }
},{immediate: true});

watch(topSize, async (newVal) => {
  if (newVal) {
    localStorage.setItem("topSize", newVal);
    const storedRounds = localStorage.getItem(`Top${newVal}${selectedGenre.value}Rounds`)
    rounds.value = JSON.parse(storedRounds) || initRounds()
    
  }
},{immediate: true});

onMounted(async ()=>{
    await fetchEventsAndInit()
    const res = await getAllJudges()
    allJudges.value = res
    battleJudges.value = await getBattleJudges()
})

</script>

<template>
    <div class="flex justify-center items-center">
        <form class="grid grid-cols-3 gap-2">
            <ReusableDropdown v-model="selectedEvent" labelId="Event" :options="allEvents.map(e => e.folderName)" />
            <ReusableDropdown v-model="selectedGenre" labelId="Genre" :options="uniqueGenres" />
            <ReusableDropdown v-model="topSize" labelId="Format" :options="sizes" />
        </form>
    </div>
    <div class="flex justify-center items-end mt-2">
      <div class="grid grid-cols-2 gap-2 items-end ">
        <ReusableDropdown  v-model="filteredJudge" labelId="Judge" :options="allJudgeOptions" />
        <div>
            <ReusableButton class="mx-2"
            @onClick="()=>{submitAddBattleJudge(filteredJudge)}" buttonName="Add Judge"></ReusableButton>
        </div>
      </div>
    </div>
    <div class="flex items-center justify-center mt-2">
      <span class="mx-2" v-for="(j, index) in battleJudges?.judges || []"
        :key="index">
        <span id="badge-dismiss-default" class="inline-flex items-center px-2 py-1 me-2 text-sm font-medium text-gray-800 bg-orange-100 rounded-sm dark:bg-gray-800 dark:text-gray-200">
        {{j.name}}
            <button @click="()=>submitRemoveBattleJudge(j.name)" type="button" class="inline-flex items-center p-1 ms-2 text-sm text-gray-100 bg-transparent rounded-xs hover:bg-orange-400 hover:text-gray-100 dark:hover:bg-orange-400 dark:hover:text-gray-100" data-dismiss-target="#badge-dismiss-default" aria-label="Remove">
                <svg class="w-2 h-2" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 14 14">
                    <path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="m1 1 6 6m0 0 6 6M7 7l6-6M7 7l-6 6"/>
                </svg>
                <span class="sr-only">Remove badge</span>
            </button>
        </span>
      </span>
    </div>
    <div class="flex flex-wrap justify-center gap-8 p-6">
    <!-- Each round -->
    <div
      v-for="(size, idx) in roundSizes"
      :key="idx"
      class="flex flex-col justify-between bg-orange-100 dark:bg-gray-800 p-4 rounded-lg shadow-md"
    >
      <div class="font-bold mb-4 text-lg text-gray-800 dark:text-gray-100">
        Top {{ size }}
      </div>

      <!-- Each Matchup -->
      <div
        v-for="(match, mIdx) in rounds[`Top${size}`]"
        :key="mIdx"
        class="flex items-center gap-2 mb-2"
      >
        <!-- Left Participant -->
        <div class="flex items-center gap-1">
          <select
            class="border rounded px-2 py-1 text-sm text-gray-700 dark:text-gray-100"
            v-model="rounds[`Top${size}`][mIdx][0]"
            @change="updateMatch(`Top${size}`, mIdx, 0, rounds[`Top${size}`][mIdx][0])"
          >
            <option :value="null" disabled>Select</option>
            <option v-for="p in topParticipants" :key="p" :value="p">{{ p }}</option>
          </select>
          <input
            type="checkbox"
            :checked="match[2] === match[0]"
            :disabled="!match[0]"
            @change="setWinner(`Top${size}`, mIdx, 0)"
          />
        </div>

        <span class="font-bold text-gray-700 dark:text-gray-100">vs</span>

        <!-- Right Participant -->
        <div class="flex items-center gap-1">
          <select
            class="border rounded px-2 py-1 text-sm text-gray-700 dark:text-gray-100"
            v-model="rounds[`Top${size}`][mIdx][1]"
            @change="updateMatch(`Top${size}`, mIdx, 1, rounds[`Top${size}`][mIdx][1])"
          >
            <option :value="null" disabled>Select</option>
            <option v-for="p in topParticipants" :key="p" :value="p">{{ p }}</option>
          </select>
          <input
            type="checkbox"
            :checked="match[2] === match[1]"
            :disabled="!match[1]"
            @change="setWinner(`Top${size}`, mIdx, 1)"
          />
        </div>
      </div>
      <ReusableButton @onClick="()=>{initiateBattlePair(`Top${size}`, rounds[`Top${size}`])}" buttonName="Start Round"></ReusableButton>
    </div>
  </div>

  <!-- {{ currentBattle }}
  <br></br> -->
    <div class="flex justify-center items-center">
        <div class="text-3xl text-gray-100">
            {{ winnerAnnouncement }}
        </div>
    </div>
  <div class="flex justify-center items-center">
    <div class="grid grid-cols-3 gap-2">
        <div v-if="previousBattlePair" class="text-gray-300 p-5 bg-gray-800 text-lg">
            Previous Pair: <span class="font-bold text-2xl text-gray-100"> {{ previousBattlePair?.[0] }} </span> vs <span class="font-bold text-2xl text-gray-100">{{ previousBattlePair?.[1] }}</span>
        </div> <div v-else class="bg-gray-800"></div>
        <div v-if="currentBattlePair" class="text-gray-300 p-5 bg-gray-800 text-lg">
            Current Pair: <span class="font-bold text-2xl text-gray-100">{{ currentBattlePair?.[0] }}</span> vs <span class="font-bold text-2xl text-gray-100">{{ currentBattlePair?.[1] }}</span>
        </div> <div v-else class="bg-gray-800"></div>
        <div v-if="nextBattlePair" class="text-gray-300 p-5 bg-gray-800 text-lg">
            Next Pair: <span class="font-bold text-2xl text-gray-100">{{ nextBattlePair?.[0] }}</span> vs <span class="font-bold text-2xl text-gray-100">{{ nextBattlePair?.[1] }}</span>
        </div> <div v-else class="bg-gray-800"></div>
    </div>
  </div>
  <div class="flex justify-center items-center">
    <div class="grid grid-cols-2 gap-2 mt-2">
        <ReusableButton @onClick="prevPair" buttonName="Previous Pair"></ReusableButton>
        <ReusableButton @onClick="nextPair" buttonName="Next Pair"></ReusableButton>
    </div>
  </div>
  <div class="flex justify-center items-center mt-2">
    <ReusableButton @onClick="submitGetScore" buttonName="Get Score"></ReusableButton>
  </div>

  <div class="flex justify-center items-center mt-2 mb-6">
    <div class="grid grid-cols-2">
        <ReusableButton @onClick="clearLocalStorage" buttonName="Reset Bracket"></ReusableButton>
        <input class="border border-orange-400 p-2 rounded" type="file" multiple @change="onFileChange"/>
    </div>
  </div>
</template>