<script setup>
import { Button } from 'primevue';
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router'

const events = ref(null)
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
</script>

<template>
  <Button 
  v-for="event in events"
  @click="goToEventDetails(event.folderName, event.folderID)"
  >{{ event.folderName }}</Button>

</template>