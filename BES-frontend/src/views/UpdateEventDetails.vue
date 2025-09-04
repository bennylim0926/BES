<script setup>
import DynamicTable from '@/components/DynamicTable.vue';
import { event } from '@primeuix/themes/aura/timeline';
import { onMounted, ref, watch, computed} from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';

const selectedEvent = ref("")
const selectedGenre = ref("All")
const allEvents = ref([])
const participants = ref([])
const allJudges = ref([])

const modalTitle = ref("")
const modalMessage = ref("")
const showModal = ref(false)
const openModal = (title, message) => {
    modalTitle.value = title
    modalMessage.value = message
    showModal.value = true
}
const handleAccept = () => {
  showModal.value = false
}


const uniqueGenres = computed(() => {
    const genres = participants.value.map(p => p.genreName);
    return [...new Set(genres)].sort();
})

const filteredParticipants = computed({
    get(){
        if (selectedGenre.value === "All") return participants.value;
        return participants.value.filter(p => p.genreName === selectedGenre.value);
    },
    set(updatedSubset){
            const byId = new Map(updatedSubset.map(r => [r.rowId, r]));
            participants.value = participants.value.map(org => {
                const updated = byId.get(org.rowId)
                return updated ? {...org, ...updated}: org
            })
        }
});

const updateParticipantJudge = async()=>{
    const updateResponse = await fetch("http://localhost:5050/api/v1/event/participants-judge/", {
        method: 'POST',
        headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            updatedList: participants.value,
        })
    })
    const result = await updateResponse.json()
    openModal("Success", "Updated!")
    
}


watch(selectedEvent, async (newVal) => {
  if (newVal) {
    await fetchAllParticipantInEvent(newVal)
  }
});

const fetchAllParticipantInEvent = async(eventName) =>{
  try{
    const res = await fetch(`http://localhost:5050/api/v1/event/participants/${eventName}`)
    if(!res.ok) throw new Error('Failed to fetch event data')
    const result = await res.json()
    participants.value = result.map((r, i) => ({
        ...r,
        rowId: r.rowId ?? i
    }))
  }catch(err){
    console.log(err)
  }
}
const capsFirst = (text) =>{
    return String(text).charAt(0).toUpperCase() + String(text).slice(1);
}


const fetchAllEvents = async() =>{
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

const fetchAllJudges = async() =>{
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
onMounted(()=>{
    fetchAllEvents()
    fetchAllJudges()
})
</script>

<template>
    <form class="max-w-sm mx-auto mb-3">
        <div class="grid grid-cols-2 gap-5">
            <div>
    <label for="events" class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Events</label>
    <select v-model="selectedEvent" id="events" class="bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white dark:focus:ring-blue-500 dark:focus:border-blue-500">
        <!-- <option selected disabled>Event</option> -->
        <option v-for="event in allEvents" :value="event.folderName">{{ event.folderName }}</option>
    </select>
</div>

<div>
    <label for="genres" class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Genres</label>
    <select v-model="selectedGenre" id="genres" class="bg-gray-50 border border-gray-300 text-gray-900 text-sm rounded-lg focus:ring-blue-500 focus:border-blue-500 block w-full p-2.5 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white dark:focus:ring-blue-500 dark:focus:border-blue-500">
        <option selected>All</option>
        <option v-for="genre in uniqueGenres" :value="genre">{{ capsFirst(genre) }}</option>
    </select>
</div>
</div>
    </form>
<DynamicTable 
v-if="participants.length > 0"
v-model:tableValue="filteredParticipants"
    :tableConfig="[
            { key: 'eventName', label: 'Event', type: 'text', readonly: true },
            { key: 'participantName', label: 'Name', type: 'text', readonly:true },
            { key: 'genreName', label: 'Genre', type: 'text', readonly:true },
            { key: 'judgeName', label: 'Judge', type: 'select', options: ['', ...allJudges]}
        ]"></DynamicTable>
<div class="flex justify-center">
    <button class="bg-transparent hover:bg-gray-500 text-gray-400 font-semibold hover:text-white py-2 px-4 border border-gray-500 hover:border-transparent rounded mb-3" @click="updateParticipantJudge">Update Judges</button>
</div>
<ActionDoneModal
        :show="showModal"
        :title="modalTitle"
        @accept="handleAccept"
    >
        <p>
        {{ modalMessage}}
        </p>
    </ActionDoneModal>
</template>