<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router'
import {fetchAllEvents} from "@/utils/api"
import ReusableButton from '@/components/ReusableButton.vue';
import { checkAuthStatus, useAuthStore } from '@/utils/auth';

const events = ref([])
const router = useRouter()
const authStore = useAuthStore()

function goToEventDetails(eventName, folderID) {
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
  <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-6 2xl:grid-cols-8 gap-4 mt-10 px-4 sm:px-10">
    <ReusableButton
      v-for="event in events" 
      :key="event.folderID"
      :buttonName="event.folderName" @onClick="goToEventDetails(event.folderName, event.folderID)">
    </ReusableButton>
  </div>
</template>