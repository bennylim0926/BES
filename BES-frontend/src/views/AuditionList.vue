<script setup>
import DynamicTable from '@/components/DynamicTable.vue';
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import { event } from '@primeuix/themes/aura/timeline';
import { ref, computed, onMounted, watch } from 'vue';

const roles = ref(["Emcee", "Judge"])
const selectedEvent = ref("")
const selectedRole = ref("")
const selectedGenre = ref("")
const filteredJudge = ref("")
const currentJudge = ref("")
const allJudges = ref([])
const allEvents = ref([])
const participants = ref([])

const capsFirst = (text) =>{
    return String(text).charAt(0).toUpperCase() + String(text).slice(1);
}

const filteredParticipants = computed({
    get(){
        if (selectedGenre.value === "All" && filteredJudge.value === "") return transformForTable(participants.value);
        if (selectedGenre.value === "All") return transformForTable(participants.value.filter(p =>  p.judgeName === filteredJudge.value));
        if (filteredJudge.value === "") return transformForTable(participants.value.filter(p => p.genreName === selectedGenre.value));
        return transformForTable(participants.value.filter(p => p.genreName === selectedGenre.value && p.judgeName === filteredJudge.value));
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
    await fetchAllParticipantInEvent(newVal)
  }
});

const fetchAllParticipantInEvent = async(eventName) =>{
  try{
    console.log(eventName)
    const res = await fetch(`http://localhost:5050/api/v1/event/participants/${eventName}`)
    if(!res.ok) throw new Error('Failed to fetch event data')
    const result = await res.json()
    participants.value = result.map((r, i) => ({
        ...r,
        rowId: r.rowId ?? i
    }))
    console.log(participants.value)
  }catch(err){
    console.log(err)
  }
}

const uniqueGenres = computed(() => {
    const genres = participants.value.map(p => p.genreName);
    return [...new Set(genres)].sort();
})

const getEvents = async () =>{
    try{
    const res = await fetch('http://localhost:5050/api/v1/folders')
    if(!res.ok) throw new Error('Failed to fetch event data')
    res.json().then(result =>{
        allEvents.value = result
        selectedEvent.value = allEvents.value[0].folderName
    })
  }catch(err){
    console.log(err)
  }
}
const getJudges = async()=>{
    try{
    const res = await fetch('http://localhost:5050/api/v1/event/judges')
    if(!res.ok) throw new Error('Failed to fetch event data')
    res.json().then(result =>{
        allJudges.value = ["", ...Object.values(result).map(item => item.judgeName)];
    })
  }catch(err){
    console.log(err)
  }
}

function transformForTable(data) {
  const judges = [...new Set(data.map(d => d.judgeName).filter(j => j !== null))];
  
  // If all judgeName are null → just return audition + participant
  if (judges.length === 0) {
    return {
      columns: [
        { key: 'auditionNumber', label: 'Audition', type: 'text', readonly: true },
        { key: 'participantName', label: 'Name', type: 'text', readonly: true }
      ],
      rows: data
        .sort((a, b) => a.auditionNumber - b.auditionNumber)
        .map(d => ({
          auditionNumber: d.auditionNumber,
          participantName: d.participantName
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

const showFilters = ref(true)

onMounted(() => {
    getEvents()
    getJudges()
})


</script>

<!-- Choose whether you are emcee or judge -->
<!-- If emcee, should see all participants in one event, one genre 
        the header either like | no. | judge 1 | judge 2| or no. | name |
    If judge, need to extra select to who you are so that the score given can be tracked
-->

<template>
    <div class="max-w-5xl mx-auto mb-3">
    <!-- Header with toggle button -->
    <div class="flex justify-end items-center mb-3">
      <!-- <h1 class="text-2xl font-extrabold leading-none tracking-tight text-gray-900 md:text-2xl lg:text-4xl dark:text-white">
        FILTER
      </h1> -->
      <button
        @click="showFilters = !showFilters"
        class="px-3 py-1 text-sm font-medium text-white bg-blue-600 rounded-md hover:bg-blue-700"
      >
        {{ showFilters ? "Hide filter" : "Show filter" }}
      </button>
    </div>

    <!-- Collapsible content -->
    <transition name="fade">
      <form v-if="showFilters" class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
        <ReusableDropdown v-model="selectedRole" labelId="Role" :options="roles" />
        <ReusableDropdown v-model="selectedEvent" labelId="Event" :options="allEvents.map(e => e.folderName)" />
        <ReusableDropdown v-model="selectedGenre" labelId="Genre" :options="uniqueGenres" />
        <ReusableDropdown v-model="filteredJudge" labelId="Judge" :options="allJudges" />
      </form>
    </transition>

    <!-- Optional extra dropdown (only for judges) -->
    <div class="flex justify-center mt-3" v-if="showFilters && selectedRole === 'Judge'">
      <div class="w-50">
        <ReusableDropdown v-model="currentJudge" labelId="Current Judge by:" :options="allJudges" />
      </div>
    </div>
  </div>

<div class="m-3" v-if="selectedRole==='Emcee'">
    <DynamicTable 
        v-if="participants.length > 0"
        v-model:tableValue="filteredParticipants.rows"
        :tableConfig="filteredParticipants.columns"></DynamicTable>
</div>


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