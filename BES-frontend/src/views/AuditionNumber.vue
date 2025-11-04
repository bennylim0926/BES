<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue"
import ActionDoneModal from './ActionDoneModal.vue';
import { createClient, deactivateClient, subscribeToChannel } from "@/utils/websocket";
import { checkAuthStatus } from "@/utils/auth";

const loading = ref(true)
const auditionNumber = ref(null)
const fakeNumber = ref(null)
let intervalId = null
let client = ref(null)
const genre = ref(null)
const participantName = ref(null)
const judgeName = ref("")

const modalTitle = ref("")
const modalMessage = ref("")
const showModal = ref(false)

const openModal = (title, message) => {
    modalTitle.value = title
    modalMessage.value = message
    showModal.value = true
}

function startSlotAnimation(finalNumber = null) {
  loading.value = true
  clearInterval(intervalId)

  // start slot rolling
  intervalId = setInterval(() => {
    fakeNumber.value = Math.floor(Math.random() * 50) + 1
  }, 100)

  // stop rolling after 3s and reveal
  setTimeout(() => {
    clearInterval(intervalId)
    if (finalNumber !== null) auditionNumber.value = finalNumber
    loading.value = false
  }, 2000)
}

const onReceiveAuditionNumber = (msg)=>{
    startSlotAnimation(msg.auditionNumber)
    genre.value = msg.genre
    participantName.value = msg.name
    judgeName.value = msg.judge == "" ? "" : `Judge by: ${msg.judge}` 
}

const onRepeatAudition = (msg) =>{
    judgeName.value = msg.judge == "" ? "" : `Judge by: ${msg.judge}` 
        openModal(`Hey ${msg.name}!`, `Your audition number is ${msg.genre} #${msg.audition}\n ${judgeName.value}`)
}

onMounted( async() => {
    const ok = await checkAuthStatus(["admin","organiser"])
    if(!ok) return
    subscribeToChannel(createClient(), "/topic/audition/", (msg) => onReceiveAuditionNumber(msg))
    subscribeToChannel(createClient(), "/topic/error/", (msg) => onRepeatAudition(msg))
})

onBeforeUnmount(() => {
  if (intervalId) clearInterval(intervalId)
  deactivateClient(client.value)
})
</script>

<template>
  <!-- <div class="flex flex-col items-center justify-center min-h-[calc(100vh-4rem)] p-6"> -->
    <div class="flex flex-col bg-[#fffaf5] shadow-lg rounded p-6 w-full items-center text-center justify-center h-[30vh]">
      <div v-if="loading" class="animate-pulse text-orange-500 text-2xl">
        Drawing audition number...  
        <p class="mt-2 text-2xl font-mono font-bold text-black">
          {{ fakeNumber }}
        </p>
      </div>
      <div v-else>
        <p class="text-3xl text-gray-800">Good Luck</p>
        <p class="mt-2 text-4xl font-mono font-bold text-gray-800">
            {{ participantName }}
        </p>
        <p class="mt-2 text-4xl font-mono font-bold text-black">
          {{genre}} #<span class="text-orange-500"> {{ auditionNumber }} </span>
        </p>
        <p class="mt-2 text-5xl font-mono font-bold text-gray-800">{{ judgeName }}</p>
      </div>
    </div>
  <!-- </div> -->
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
