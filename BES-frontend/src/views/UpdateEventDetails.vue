<script setup>
import DynamicTable from '@/components/DynamicTable.vue';
import { event } from '@primeuix/themes/aura/timeline';
import { onMounted, ref, watch, computed} from 'vue';
import ActionDoneModal from './ActionDoneModal.vue'
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import ReusableButton from '@/components/ReusableButton.vue';

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
    <form class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-2 lg:grid-cols-2 gap-5 m-10">
        <ReusableDropdown v-model="selectedEvent" labelId="Event" :options="allEvents.map(e => e.folderName)" />
        <ReusableDropdown v-model="selectedGenre" labelId="Genre" :options="uniqueGenres" />
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
        <ReusableButton
            buttonName="Update Judges" @onClick="updateParticipantJudge">
        </ReusableButton>
    </div>
    <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    @accept="handleAccept"
    @close="()=>{showModal=false}"
    >
        <p>
        {{ modalMessage}}
        </p>
    </ActionDoneModal>
</template>