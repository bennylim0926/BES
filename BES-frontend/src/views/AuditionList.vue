<script setup>
import DynamicTable from '@/components/DynamicTable.vue';
import { event } from '@primeuix/themes/aura/timeline';
import { ref, computed, onMounted, watch } from 'vue';

const roles = ref(["Emcee", "Judge"])
const selectedEvent = ref("")
const selectedRole = ref("")
const selectedGenre = ref("All")
const filteredJudge = ref("")
const currentJudge = ref("")
const allJudges = ref([])
const allEvents = ref([])
const participants = ref([])
const genreFromThatEvent = ref([])

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
        allJudges.value = Object.values(result).map(item => item.judgeName);
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
<form class="max-w-5xl mx-auto mb-3">
        <div class="grid grid-cols-4 gap-5">
            <div>
    <label for="role" class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Role</label>
    <select v-model="selectedRole" id="role" class="bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white dark:focus:ring-blue-500 dark:focus:border-blue-500">
        <option selected> </option>
        <option v-for="role in roles" :value="role">{{ role }}</option>
    </select>
</div>
<div>
    <label for="events" class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Event</label>
    <select v-model="selectedEvent" id="events" class="bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white dark:focus:ring-blue-500 dark:focus:border-blue-500">
        <option selected> </option>
        <option v-for="event in allEvents" :value="event.folderName">{{ capsFirst(event.folderName) }}</option>
    </select>
</div>
<div>
    <label for="genres" class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Genres</label>
    <select v-model="selectedGenre" id="genres" class="bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white dark:focus:ring-blue-500 dark:focus:border-blue-500">
        <option selected>All</option>
        <option v-for="genre in uniqueGenres" :value="genre">{{ capsFirst(genre) }}</option>
    </select>
</div>
<div v-if="selectedRole == 'Judge'">
    <label for="judges" class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Judge by</label>
    <select v-model="filteredJudge" id="judges" class="bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white dark:focus:ring-blue-500 dark:focus:border-blue-500">
        <option selected> </option>
        <option v-for="judge in allJudges" :value="judge">{{ capsFirst(judge) }}</option>
    </select>
</div>
</div>
    </form>
    <div class="flex justify-center">
    <div v-if="selectedRole == 'Judge'" class="w-50">
    <label for="currentJudge" class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">You are</label>
    <select v-model="currentJudge" id="currentJudge" class="bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white dark:focus:ring-blue-500 dark:focus:border-blue-500">
        <option selected> </option>
        <option v-for="judge in allJudges" :value="judge">{{ capsFirst(judge) }}</option>
    </select>
</div>
</div>

<div v-if="selectedRole==='Emcee'">
    <DynamicTable 
        v-if="participants.length > 0"
        v-model:tableValue="filteredParticipants.rows"
        :tableConfig="filteredParticipants.columns"></DynamicTable>
</div>

</template>