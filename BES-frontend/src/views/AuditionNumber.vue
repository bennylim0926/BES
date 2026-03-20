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
  intervalId = setInterval(() => {
    fakeNumber.value = Math.floor(Math.random() * 50) + 1
  }, 100)
  setTimeout(() => {
    clearInterval(intervalId)
    if (finalNumber !== null) auditionNumber.value = finalNumber
    loading.value = false
  }, 2000)
}

const onReceiveAuditionNumber = (msg) => {
  startSlotAnimation(msg.auditionNumber)
  genre.value = msg.genre
  participantName.value = msg.name
  judgeName.value = msg.judge == "" ? "" : `Judge: ${msg.judge}`
}

const onRepeatAudition = (msg) => {
  judgeName.value = msg.judge == "" ? "" : `Judge: ${msg.judge}`
  openModal(`Hey ${msg.name}!`, `Your audition number is ${msg.genre} #${msg.audition}\n${judgeName.value}`)
}

onMounted(async () => {
  const ok = await checkAuthStatus(["admin", "organiser"])
  if (!ok) return
  subscribeToChannel(createClient(), "/topic/audition/", (msg) => onReceiveAuditionNumber(msg))
  subscribeToChannel(createClient(), "/topic/error/", (msg) => onRepeatAudition(msg))
})

onBeforeUnmount(() => {
  if (intervalId) clearInterval(intervalId)
  deactivateClient(client.value)
})
</script>

<template>
  <div class="w-full flex flex-col items-center justify-center min-h-[180px] text-center">

    <!-- Waiting/rolling state -->
    <div v-if="loading" class="space-y-2">
      <p class="text-xs font-semibold text-surface-400 uppercase tracking-widest">Drawing audition number</p>
      <div class="text-6xl font-heading font-extrabold tabular-nums text-surface-200 animate-pulse">
        {{ fakeNumber ?? '—' }}
      </div>
    </div>

    <!-- Revealed state -->
    <div v-else class="space-y-1">
      <p class="text-xs font-semibold text-surface-400 uppercase tracking-widest">Good Luck</p>
      <p class="font-heading font-extrabold text-2xl text-surface-900">{{ participantName }}</p>
      <div class="flex items-baseline justify-center gap-2 mt-1">
        <span class="text-sm font-semibold text-surface-500">{{ genre }}</span>
        <span class="text-4xl font-heading font-extrabold text-primary-600">#{{ auditionNumber }}</span>
      </div>
      <p v-if="judgeName" class="text-sm font-medium text-surface-500 mt-1">{{ judgeName }}</p>
    </div>

  </div>

  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    variant="info"
    @accept="() => { showModal = false }"
    @close="() => { showModal = false }"
  >
    <p class="text-surface-600 leading-relaxed">{{ modalMessage }}</p>
  </ActionDoneModal>
</template>
