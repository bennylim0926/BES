<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router'
import {fetchAllEvents} from "@/utils/api"
import { checkAuthStatus, useAuthStore } from '@/utils/auth';
import EventCard from '@/components/EventCard.vue';
import { useDelay } from '@/utils/utils';

const events = ref([])
const router = useRouter()
const authStore = useAuthStore()

async function goToEventDetails(eventName, folderID) {
  await useDelay().wait(200);
  router.push({
    name: 'Event Details', 
    params: { eventName: eventName},
    query: {folderID}
});
}

onMounted(async ()=>{
  const ok = await checkAuthStatus(["admin","organiser"])
  if(!ok) return
  events.value = await fetchAllEvents()
})
</script>

<template>
  <div class="flex justify-center items-center ">
    <div class="grid grid-cols-1 text-black mt-4 mx-3">
      <h1 class="text-4xl sm:text-4xl md:text-6xl lg:text-6xl font-semi-bold">EVENTS</h1>
      <p class="text-md sm:text-md md:text-xl lg:text-xl">Response data are linked to google response in the drive folder</p>
      <hr class="h-px mb-8 mt-4 bg-gray-200 border-0 dark:bg-gray-700"></hr>
      <div class="grid grid-cols-1 sm:grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-4 gap-1 px-2 sm:px-10 justify-items-center">
        <EventCard
        v-for="event in events" 
          :key="event.folderID"
          :buttonName="event.folderName" @onClick="goToEventDetails(event.folderName, event.folderID)"
        ></EventCard>
      </div>
    </div>
  </div>
</template>
<style>

html,
body {
  background-color: white;
}
</style>