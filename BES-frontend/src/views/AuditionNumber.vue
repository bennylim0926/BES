<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue"
import ActionDoneModal from './ActionDoneModal.vue';
import { createClient, deactivateClient, subscribeToChannel } from "@/utils/websocket";
import { checkAuthStatus } from "@/utils/auth";

const loading = ref(false)
const fakeNumber = ref(null)
let intervalId = null
let client = ref(null)

const assignments = ref([])   // flat list of all completed {name, genre, auditionNumber, judge}
const currentAssignment = ref(null)
const queue = ref([])
const isAnimating = ref(false)

const modalTitle = ref("")
const modalMessage = ref("")
const showModal = ref(false)

// Group completed assignments by participant name, newest first
const groupedHistory = computed(() => {
  const map = new Map()
  for (const a of assignments.value) {
    if (!map.has(a.name)) map.set(a.name, [])
    map.get(a.name).push(a)
  }
  return [...map.entries()].map(([name, entries]) => ({ name, entries })).reverse()
})

const openModal = (title, message) => {
  modalTitle.value = title
  modalMessage.value = message
  showModal.value = true
}

const clearHistory = () => {
  assignments.value = []
}

function startSlotAnimation(finalNumber, onDone) {
  loading.value = true
  clearInterval(intervalId)
  intervalId = setInterval(() => {
    fakeNumber.value = Math.floor(Math.random() * 50) + 1
  }, 100)
  setTimeout(() => {
    clearInterval(intervalId)
    fakeNumber.value = null
    loading.value = false
    onDone()
  }, 2000)
}

const processQueue = () => {
  if (queue.value.length === 0) {
    isAnimating.value = false
    return
  }
  isAnimating.value = true
  const next = queue.value.shift()
  currentAssignment.value = next
  startSlotAnimation(next.auditionNumber, () => {
    assignments.value.push(next)
    currentAssignment.value = null
    processQueue()
  })
}

const onReceiveAuditionNumber = (msg) => {
  queue.value.push(msg)
  if (!isAnimating.value) processQueue()
}

const onRepeatAudition = (msg) => {
  const judgeLabel = msg.judge === "" ? "" : `\nJudge: ${msg.judge}`
  openModal(`Hey ${msg.name}!`, `Your audition number is ${msg.genre} #${msg.audition}${judgeLabel}`)
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

    <!-- Animating state -->
    <div v-if="loading" class="space-y-2">
      <p class="text-xs font-semibold text-surface-400 uppercase tracking-widest">Drawing audition number</p>
      <div class="text-6xl font-heading font-extrabold tabular-nums text-surface-200 animate-pulse">
        {{ fakeNumber ?? '—' }}
      </div>
    </div>

    <!-- Current revealed assignment -->
    <div v-else-if="currentAssignment" class="space-y-1">
      <p class="text-xs font-semibold text-surface-400 uppercase tracking-widest">Good Luck</p>
      <p class="font-heading font-extrabold text-2xl text-surface-900">{{ currentAssignment.name }}</p>
      <div class="flex items-baseline justify-center gap-2 mt-1">
        <span class="text-sm font-semibold text-surface-500 capitalize">{{ currentAssignment.genre }}</span>
        <span class="text-4xl font-heading font-extrabold text-primary-600">#{{ currentAssignment.auditionNumber }}</span>
      </div>
      <p v-if="currentAssignment.judge" class="text-sm font-medium text-surface-500 mt-1">Judge: {{ currentAssignment.judge }}</p>
    </div>

    <!-- Idle state -->
    <div v-else-if="assignments.length === 0" class="space-y-2">
      <p class="text-xs font-semibold text-surface-400 uppercase tracking-widest">Waiting for scan…</p>
      <div class="text-6xl font-heading font-extrabold tabular-nums text-surface-200">—</div>
    </div>

    <!-- History: one row per participant -->
    <div v-if="groupedHistory.length > 0" class="mt-5 w-full">
      <div class="flex items-center justify-between mb-2">
        <p class="text-xs font-semibold text-surface-400 uppercase tracking-widest">History</p>
        <button
          @click="clearHistory"
          class="text-xs font-medium text-surface-400 hover:text-red-500 transition-colors"
        >
          Clear
        </button>
      </div>

      <div class="divide-y divide-surface-100">
        <div
          v-for="(group, i) in groupedHistory"
          :key="group.name"
          :class="i === 0
            ? 'flex items-center gap-4 py-3 bg-primary-50/60 rounded-xl px-3 -mx-3 mb-1'
            : 'flex items-center gap-3 py-2 opacity-60'"
        >
          <!-- Name -->
          <span
            :class="i === 0
              ? 'font-heading font-extrabold text-base text-surface-900 w-32 shrink-0 truncate'
              : 'text-sm font-semibold text-surface-700 w-28 shrink-0 truncate'"
          >{{ group.name }}</span>
          <!-- Genre + number pills -->
          <div class="flex flex-wrap gap-1.5">
            <span
              v-for="a in group.entries"
              :key="a.genre"
              :class="i === 0
                ? 'inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-white border border-primary-200 text-sm shadow-sm'
                : 'inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-primary-50 border border-primary-100 text-xs'"
            >
              <span class="text-surface-500 capitalize">{{ a.genre }}</span>
              <span
                :class="i === 0 ? 'font-heading font-extrabold text-primary-600 text-base' : 'font-heading font-extrabold text-primary-600'"
              >#{{ a.auditionNumber }}</span>
            </span>
          </div>
        </div>
      </div>
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
