<script setup>
import DynamicTable from '@/components/DynamicTable.vue';
import ReusableButton from '@/components/ReusableButton.vue';
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import { fetchAllEvents, getAllJudges, getRegisteredParticipantsByEvent, submitParticipantScore, whoami } from '@/utils/api';
import { createClient, subscribeToChannel } from '@/utils/websocket';
import { ref, computed, onMounted, watch, toRaw } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import Timer from '@/components/Timer.vue';
import { checkAuthStatus } from '@/utils/auth';
import SwipeableCardsV2 from '@/components/SwipeableCardsV2.vue';
import MiniScoreMenu from '@/components/MiniScoreMenu.vue';

const roles = ref(["Emcee", "Judge"])
const selectedEvent = ref(localStorage.getItem("selectedEvent") || "")
const selectedRole = ref(localStorage.getItem("selectedRole") || "")
const selectedGenre = ref(localStorage.getItem("selectedGenre") || "")
const filteredJudge = ref("")
const currentJudge = ref(localStorage.getItem("currentJudge") || "")
const allJudges = ref([])
const allEvents = ref([])
const participants = ref([])

const modalTitle = ref("")
const modalMessage = ref("")
const showModal = ref(false)
const showMiniMenu = ref(false)

const dynamicRole = async ()=>{
  const res = await whoami()
  if(res.username === "emcee"){
    roles.value = ["Emcee"]
    selectedRole.value = "Emcee"
  }else if (res.username === "judge"){
    roles.value = ["Judge"]
    selectedRole.value = "Judge"
  }else if (res.username === "admin"){
    roles.value = ["Emcee", "Judge"]
    selectedRole.value = localStorage.getItem("selectedRole") || ""
  }
}

const hasJudge = computed(()=>{
  return participants.value.some(item => item.judgeName !== null)
})

const openModal = (title, message) => {
    modalTitle.value = title
    modalMessage.value = message
    showModal.value = true
}

const filteredParticipantsForJudge = computed({
  get() {
    return participants.value
      .filter(p =>
        p.genreName === selectedGenre.value &&
        p.judgeName === (filteredJudge.value === "" ? null : filteredJudge.value) &&
        p.auditionNumber !== null
      )
      .sort((a, b) => a.auditionNumber - b.auditionNumber)
  },
  set(updatedList) {
    // sync changes back into participants
    updatedList.forEach(updated => {
      const idx = participants.value.findIndex(p => p.auditionNumber === updated.auditionNumber)
      if (idx !== -1) {
        participants.value[idx] = { ...participants.value[idx], ...updated }
      }
    })
  }
})

watch(filteredParticipantsForJudge, (newVal) => {
    localStorage.setItem("currentScore", JSON.stringify(toRaw(newVal)))
}, { deep: true });

const filteredParticipantsForEmcee = computed({
    get(){
        // if (selectedGenre.value === "All" && filteredJudge.value === "") return transformForTable(participants.value);
        // if (selectedGenre.value === "All") return transformForTable(participants.value.filter(p =>  p.judgeName === filteredJudge.value));
        if (filteredJudge.value === "") return transformForTable(participants.value.filter(p => p.genreName === selectedGenre.value && p.auditionNumber != null));
        return transformForTable(participants.value.filter(p => p.genreName === selectedGenre.value && p.judgeName === filteredJudge.value && p.auditionNumber !== null));
    },
    set(updatedSubset){
        const byId = new Map(updatedSubset.map(r => [r.rowId, r]));
        participants.value = participants.value.map(org => {
            const updated = byId.get(org.rowId)
            return updated ? {...org, ...updated}: org
        })
    }
});

watch(selectedEvent, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedEvent", newVal);
    participants.value = []
    const res = await getRegisteredParticipantsByEvent(newVal)
    participants.value = res.map((r,i)=>({
        ...r,
        rowId: r.rowId ?? i,
        score: 0
    }))
    const stored = localStorage.getItem("currentScore")
    if (stored) {
      const cached = JSON.parse(stored)
      participants.value = participants.value.map(p => {
        const found = cached.find(c => c.rowId === p.rowId)
        return found ? { ...p, ...found } : p
      })
    }
  }
},{ immediate: true });

watch(selectedGenre, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedGenre", newVal);
  }
},{immediate: true});

watch(selectedRole, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedRole", newVal);
  }
},{immediate: true});

watch(currentJudge, async (newVal) => {
  if (newVal) {
    localStorage.setItem("currentJudge", newVal);
    
  }
},{immediate: true});

const uniqueGenres = computed(() => {
    const genres = participants.value.map(p => p.genreName);
    return [...new Set(genres)].sort();
})

function transformForTable(data) {
  const judges = [...new Set(data.map(d => d.judgeName).filter(j => j !== null))];
  
  // If all judgeName are null → just return audition + participant
  if (judges.length === 0) {
    return {
      columns: [
        { key: 'auditionNumber', label: 'Number', type: 'text', readonly: true },
        { key: 'participantName', label: 'Name', type: 'text', readonly: true },
        { key: 'marked', label: 'Done', type: 'boolean'}
      ],
      rows: data
        .sort((a, b) => a.auditionNumber - b.auditionNumber)
        .map(d => ({
          auditionNumber: d.auditionNumber,
          participantName: d.participantName,
          marked: false
        }))
    };
  }

  // Otherwise → pivot by auditionNumber
  const auditions = {};
  data.forEach(d => {
    if (!auditions[d.auditionNumber]) {
      auditions[d.auditionNumber] = { auditionNumber: d.auditionNumber };
    }
    auditions[d.auditionNumber][d.judgeName] = d.participantName;
  });
  // Clean rows → drop if all judges undefined, else replace missing with ""
  const rows = Object.values(auditions)
    .map(row => {
      const allEmpty = judges.every(j => !row[j]);
      if (allEmpty) return null;
      judges.forEach(j => {
        if (!row[j]) row[j] = ""; // replace missing with ""
      });
      return row;
    })
    .filter(Boolean) // remove dropped rows
    .sort((a, b) => a.auditionNumber - b.auditionNumber);
  return {
    columns: [
      { key: 'auditionNumber', label: 'Audition', type: 'text', readonly: true },
      ...judges.map(j => ({ key: j, label: j, type: 'text', readonly: true }))
    ],
    rows
  };
}

const submitScore = async(eventName,genreName, judgeName, participants) =>{
  if(judgeName === ""){
    openModal("Failed", "Must assign a judge to this audition")
    return 
  }
  const p = participants.map( obj=>({
    ...obj,
    score: parseFloat(obj.score)
  }))
  const res = await submitParticipantScore(eventName, genreName, judgeName, p)
  if(res.ok){
    openModal("Success", "Score updated!")
    localStorage.removeItem("currentScore");
  }
}

const showFilters = ref(true)

const fetchEventsAndInit = async()=>{
    allEvents.value = await fetchAllEvents()
    const savedEvent = localStorage.getItem("selectedEvent")
    selectedEvent.value = savedEvent || (allEvents.value[0]?.folderName || "")
}

onMounted(async () => {
    const ok = await checkAuthStatus(["admin","emcee", "judge"])
    if(!ok) return
    await dynamicRole()
    await fetchEventsAndInit()
    
    const res = await getAllJudges()
    allJudges.value =["", ...Object.values(res).map(item => item.judgeName)];
    subscribeToChannel(createClient(), "/topic/audition/",
     (msg) => {
        // console.log(msg.name, msg.genre, msg.judge)
        const idx = participants.value
                            .findIndex(
                                p => p.participantName === msg.name 
                            && p.genreName === msg.genre
                            && (p.judgeName === null ? 1 : p.judgeName === msg.judge))
        if (idx !== -1){
            // console.log({...participants.value[idx], ...{auditionNumber: msg.auditionNumber}})
            participants.value[idx] = {...participants.value[idx], ...{auditionNumber: msg.auditionNumber} }
        }      
     })
})
</script>

<!-- Choose whether you are emcee or judge -->
<!-- If emcee, should see all participants in one event, one genre 
        the header either like | no. | judge 1 | judge 2| or no. | name |
    If judge, need to extra select to who you are so that the score given can be tracked
-->

<template>
    <div class="m-10">
    <div class="flex justify-end items-center mb-3">
      <ReusableButton @onClick="showFilters = !showFilters" :buttonName="showFilters ? 'Hide filter' : 'Show filter'"></ReusableButton>
      <ReusableButton v-if="selectedRole === 'Judge'" class="ml-2" @onClick="showMiniMenu = !showMiniMenu" buttonName="Score Menu"></ReusableButton>
    </div>

    <!-- Collapsible content -->
    <transition name="fade">
      <form v-if="showFilters" class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
        <ReusableDropdown v-model="selectedRole" labelId="Role" :options="roles" />
        <ReusableDropdown v-model="selectedEvent" labelId="Event" :options="allEvents.map(e => e.folderName)" />
        <ReusableDropdown v-model="selectedGenre" labelId="Genre" :options="uniqueGenres" />
        <ReusableDropdown v-if="hasJudge" v-model="filteredJudge" labelId="Judge" :options="allJudges" />
      </form>
    </transition>

    <!-- Optional extra dropdown (only for judges) -->
    <div class="flex justify-center mt-3" v-if="showFilters && selectedRole === 'Judge'">
      <div class="w-50">
        <ReusableDropdown v-model="currentJudge" labelId="Current Judge by:" :options="allJudges" />
      </div>
    </div>
  </div>
<div class="m-8" v-if="selectedRole==='Emcee' && filteredParticipantsForEmcee.rows.length > 0">
    <Timer class="sticky top-0 m-5 z-50 shadow-lg"></Timer>
    <DynamicTable 
        v-model:tableValue="filteredParticipantsForEmcee.rows"
        :tableConfig="filteredParticipantsForEmcee.columns"></DynamicTable>
</div>
<div v-else-if="selectedRole==='Judge' && filteredParticipantsForEmcee.rows.length>0">
    <!-- <SwipeableCards v-model:cards="filteredParticipantsForJudge"></SwipeableCards> -->
     <MiniScoreMenu 
     :cards="filteredParticipantsForJudge"
     :show="showMiniMenu"
     :title="'MOVE TO'"
     @close="showMiniMenu = !showMiniMenu"></MiniScoreMenu>
     <SwipeableCardsV2 
     :cards="filteredParticipantsForJudge"></SwipeableCardsV2>
    <div class="flex justify-center my-3">
        <ReusableButton @onClick="submitScore(selectedEvent, selectedGenre, currentJudge, filteredParticipantsForJudge)" 
        buttonName="Submit Scores"></ReusableButton>
    </div>
</div>
<ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    @accept="()=>{showModal = false}"
    @close="()=>{showModal = false}"
  >
    <p>
      {{ modalMessage}}
    </p>
  </ActionDoneModal>
</template>

<style>
/* simple fade animation */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>