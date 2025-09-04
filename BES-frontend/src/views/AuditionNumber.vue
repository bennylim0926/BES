<script setup>
import { ref, onMounted, onBeforeUnmount } from "vue"
import { Client } from "@stomp/stompjs"
import ActionDoneModal from './ActionDoneModal.vue';

const loading = ref(true)
const auditionNumber = ref(null)
const fakeNumber = ref(null)
let intervalId = null
let client = null
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

const handleAccept = () => {
  showModal.value = false
}

const WS_URL = "ws://localhost:5050/ws"

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

onMounted(async () => {
  // 🛰️ Connect to WebSocket and listen for updates
  client = new Client({
    brokerURL: WS_URL,
    reconnectDelay: 5000,
    onConnect: () => {
      client.subscribe(`/topic/audition/`, (msg) => {
        const updated = JSON.parse(msg.body)
        // trigger slot animation again on update
        startSlotAnimation(updated.auditionNumber)
        genre.value = updated.genre
        participantName.value = updated.name
        judgeName.value = updated.judge == "" ? "" : `Judge by: ${updated.judge}` 
      }),
      client.subscribe(`/topic/error/`, (msg) => {
        const updated = JSON.parse(msg.body)
        judgeName.value = updated.judge == "" ? "" : `Judge by: ${updated.judge}` 
        openModal(`Hey ${updated.name}!`, `Your audition number is ${updated.genre} #${updated.audition}\n ${judgeName.value}`)
      })
    },
  })
  client.activate()
})

onBeforeUnmount(() => {
  if (intervalId) clearInterval(intervalId)
  if (client) client.deactivate()
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
    @accept="handleAccept"
  >
    <p>
      {{ modalMessage}}
    </p>
  </ActionDoneModal>
</template>
