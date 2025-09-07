<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue"
import ActionDoneModal from './ActionDoneModal.vue';
import { createClient, deactivateClient, subscribeToChannel } from "@/utils/websocket";

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
    fakeNumber.value = Math.floor(Math.random() * 100) + 1
  }, 100)

  // stop rolling after 3s and reveal
  setTimeout(() => {
    clearInterval(intervalId)
    if (finalNumber !== null) auditionNumber.value = finalNumber
    loading.value = false
  }, 2000)
}

const onReceiveAuditionNumber = (msg)=>{
    console.log("audition number")
    startSlotAnimation(msg.auditionNumber)
    genre.value = msg.genre
    participantName.value = msg.name
    judgeName.value = msg.judge == "" ? "" : `Judge by: ${msg.judge}` 
}

const onRepeatAudition = (msg) =>{
    judgeName.value = msg.judge == "" ? "" : `Judge by: ${msg.judge}` 
        openModal(`Hey ${msg.name}!`, `Your audition number is ${msg.genre} #${msg.audition}\n ${judgeName.value}`)
}

onMounted( () => {
    subscribeToChannel(createClient(), "/topic/audition/", (msg) => onReceiveAuditionNumber(msg))
    subscribeToChannel(createClient(), "/topic/error/", (msg) => onRepeatAudition(msg))
})

onBeforeUnmount(() => {
  if (intervalId) clearInterval(intervalId)
  deactivateClient(client.value)
})
</script>

<template>
  <div class="flex flex-col items-center justify-center min-h-[calc(100vh-4rem)]  bg-gray-900 p-6">
    <div class="bg-white shadow-lg rounded-2xl p-6 w-full max-w-md text-center">
      <div v-if="loading" class="animate-pulse text-gray-500">
        Drawing audition number...  
        <p class="mt-2 text-2xl font-mono font-bold text-gray-700">
          {{ fakeNumber }}
        </p>
      </div>
      <div v-else>
        <p class="text-lg text-gray-600">Good Luck</p>
        <p class="mt-2 text-2xl font-mono font-bold text-gray-600">
            {{ participantName }}
        </p>
        <p class="mt-2 text-2xl font-mono font-bold text-gray-900">
          {{genre}} #{{ auditionNumber }}
        </p>
        <p class="mt-2 text-md font-mono font-bold text-gray-900">{{ judgeName }}</p>
      </div>
    </div>
  </div>
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
