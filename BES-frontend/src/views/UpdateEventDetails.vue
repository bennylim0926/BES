<script setup>
import DynamicTable from '@/components/DynamicTable.vue';
import { onMounted, ref, watch, computed} from 'vue';
import ActionDoneModal from './ActionDoneModal.vue'
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import ReusableButton from '@/components/ReusableButton.vue';
import { checkAuthStatus } from '@/utils/auth';
import CreateParticipantForm from '@/components/CreateParticipantForm.vue';

const selectedEvent = ref(localStorage.getItem("selectedEvent") || "")
const selectedGenre = ref(localStorage.getItem("selectedGenre") || "All")
const allEvents = ref([])
const participants = ref([])
const allJudges = ref([])
const showCreateNewEntry = ref(false)

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
      if (!Array.isArray(participants.value)) return;
      const byId = new Map(updatedSubset.map(r => [r.rowId, r]));
      participants.value = participants.value.map(org => {
          const updated = byId.get(org.rowId)
          return updated ? {...org, ...updated}: org
      })
    }
});

const updateParticipantJudge = async()=>{
    const updateResponse = await fetch("/api/v1/event/participants-judge/", {
        method: 'POST',
        credentials: "include",
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

// IF THERE IS ANY ISSUE LOADING, PUT BACK THE IMMEDIATE
watch(selectedEvent, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedEvent", newVal);
    await fetchAllParticipantInEvent(newVal)
  }
});

watch(selectedGenre, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedGenre", newVal);
  }
},{immediate: true});

const fetchAllParticipantInEvent = async(eventName) =>{
  try{
    const res = await fetch(`/api/v1/event/participants/${eventName}`,{
      credentials: "include"
    })
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

const fetchAllEvents = async () => {
  try {
    const res = await fetch('/api/v1/folders',{
      credentials: "include"
    });
    if (!res.ok) throw new Error('Failed to fetch event data');
    const result = await res.json();
    allEvents.value = result;

    // if no saved event, pick the first
    if (!selectedEvent.value && allEvents.value.length > 0) {
      selectedEvent.value = allEvents.value[0].folderName;
    }
    // manually trigger once here, ensures participants load
    if (selectedEvent.value) {
      await fetchAllParticipantInEvent(selectedEvent.value);
    }
  } catch (err) {
    console.log(err);
  }
};

const fetchAllJudges = async() =>{
  try{
    const res = await fetch('/api/v1/event/judges',{
      credentials: "include"
    })
    if(!res.ok) throw new Error('Failed to fetch event data')
    res.json().then(result =>{
        allJudges.value = Object.values(result).map(item => item.judgeName);
    })
  }catch(err){
    console.log(err)
  }
}
onMounted(async()=>{
    const ok = await checkAuthStatus(["admin","organiser"])
    if(!ok) return
    fetchAllEvents()
    fetchAllJudges()
})
</script>

<template>
  <div class="m-10">
    <div class="flex justify-end items-center mb-3">
      <ReusableButton @onClick="showCreateNewEntry = !showCreateNewEntry" buttonName="Add participant"></ReusableButton>
    </div>
  </div>
    <form class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-2 lg:grid-cols-2 gap-5 m-10">
        <ReusableDropdown v-model="selectedEvent" labelId="Event" :options="allEvents.map(e => e.folderName)" />
        <ReusableDropdown v-model="selectedGenre" labelId="Genre" :options="uniqueGenres" />
    </form>
    <div class="mx-5">
    <DynamicTable 
    v-if="participants.length > 0"
    v-model:tableValue="filteredParticipants"
    :tableConfig="[
        { key: 'eventName', label: 'Event', type: 'text', readonly: true },
        { key: 'participantName', label: 'Name', type: 'text', readonly:true },
        { key: 'genreName', label: 'Genre', type: 'text', readonly:true },
        { key: 'judgeName', label: 'Judge', type: 'select', options: ['', ...(allJudges || [])]}
    ]"></DynamicTable>
    </div>

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
    <CreateParticipantForm
    :event="selectedEvent"
    :show="showCreateNewEntry" 
    title="New participant entry"
    @createNewEntry="()=>{showCreateNewEntry = !showCreateNewEntry}"
    @close="()=>{showCreateNewEntry = !showCreateNewEntry}"
    ></CreateParticipantForm>
</template>