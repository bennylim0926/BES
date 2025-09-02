<script setup>
import { Button } from 'primevue';
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router'

import Splitter from 'primevue/splitter';
import SplitterPanel from 'primevue/splitterpanel';

const events = ref([])
const router = useRouter()

function goToEventDetails(eventName, folderID) {
  router.push({
    name: 'Event Details', 
    params: { eventName: eventName},
    query: {folderID}
});
}

const fetchAllEvents = async() =>{
  try{
    const res = await fetch('http://localhost:5050/api/v1/folders')
    if(!res.ok) throw new Error('Failed to fetch event data')
    res.json().then(result =>{
      events.value = result
    })
  }catch(err){
    console.log(err)
  }
}

onMounted(()=>{
  fetchAllEvents()
})

function chunkArray(arr, size) {
  const result = []
  for (let i = 0; i < arr.length; i += size) {
    result.push(arr.slice(i, i + size))
  }
  return result
}

const rows = computed(() => chunkArray(events.value, 7))
</script>

<template>
  <div class="grid grid-cols-7 gap-4">
    <div
      v-for="event in events"
      :key="event.folderID"
      @click="goToEventDetails(event.folderName, event.folderID)"
      class="flex items-center justify-center p-6 bg-gray-700 rounded-xl text-gray-200 shadow 
             cursor-pointer hover:bg-gray-500 hover:shadow-lg transition"
    >
      {{ event.folderName }}
    </div>
  </div>
</template>