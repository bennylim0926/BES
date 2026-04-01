<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue"
import ActionDoneModal from './ActionDoneModal.vue';
import { createClient, deactivateClient, subscribeToChannel } from "@/utils/websocket";

const loading = ref(false)
const fakeNumber = ref(null)
let intervalId = null
let client = ref(null)

const assignments = ref([])
const currentAssignment = ref(null)
const queue = ref([])
const isAnimating = ref(false)

const modalTitle = ref("")
const modalMessage = ref("")
const showModal = ref(false)

const revealingRef = ref(null)

const groupedHistory = computed(() => {
  const order = []
  const map = new Map()
  for (const a of assignments.value) {
    if (map.has(a.name)) {
      const idx = order.indexOf(a.name)
      order.splice(idx, 1)
    } else {
      map.set(a.name, { refCode: a.refCode || '', genres: new Map() })
    }
    order.push(a.name)
    map.get(a.name).genres.set(a.genre, a)
  }
  return order.reverse().map(name => ({
    name,
    refCode: map.get(name).refCode,
    entries: [...map.get(name).genres.values()]
  }))
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

onMounted(() => {
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
    <div v-if="loading" class="space-y-3">
      <p class="label-caps font-semibold text-content-muted uppercase">Drawing audition number</p>
      <div class="text-6xl font-heading font-extrabold tabular-nums animate-slot-roll shimmer-text">
        {{ fakeNumber ?? '—' }}
      </div>
      <div class="w-24 h-1 mx-auto rounded-full overflow-hidden bg-surface-700">
        <div class="h-full shimmer-bar" style="background: linear-gradient(90deg, transparent 0%, rgba(34,211,238,0.6) 50%, transparent 100%); background-size: 200% 100%; animation: shimmerMove 1.2s ease-in-out infinite;"></div>
      </div>
    </div>

    <!-- Current revealed assignment -->
    <div v-else-if="currentAssignment" class="space-y-1">
      <p class="label-caps font-semibold text-content-muted uppercase">Good Luck</p>
      <p class="font-heading font-extrabold text-2xl text-content-primary">{{ currentAssignment.name }}</p>
      <div class="flex items-baseline justify-center gap-2 mt-1">
        <span class="text-sm font-semibold text-content-muted capitalize">{{ currentAssignment.genre }}</span>
        <span class="text-4xl font-heading font-extrabold text-primary-400">#{{ currentAssignment.auditionNumber }}</span>
      </div>
      <p v-if="currentAssignment.judge" class="text-sm font-medium text-content-muted mt-1">Judge: {{ currentAssignment.judge }}</p>
    </div>

    <!-- Idle state -->
    <div v-else-if="assignments.length === 0" class="scan-zone">
      <div class="scan-zone-inner flex flex-col items-center gap-3"
           style="background: radial-gradient(ellipse 60% 50% at 50% 50%, rgba(34,211,238,0.04) 0%, transparent 100%);">
        <p class="label-caps font-semibold text-content-muted uppercase animate-scan-pulse">Waiting for scan…</p>
        <div class="text-6xl font-heading font-extrabold tabular-nums text-surface-600/50">—</div>
      </div>
    </div>

    <!-- History: one row per participant -->
    <div v-if="groupedHistory.length > 0" class="mt-5 w-full">
      <div class="flex items-center justify-between mb-2">
        <p class="label-caps font-semibold text-content-muted uppercase">History</p>
        <button
          @click="clearHistory"
          class="text-xs font-medium text-content-muted hover:text-red-400 transition-colors"
        >
          Clear
        </button>
      </div>

      <div class="divide-y divide-surface-600/30">
        <div
          v-for="(group, i) in groupedHistory"
          :key="group.name"
          :class="i === 0
            ? 'flex items-center gap-4 py-3 bg-primary-100/30 rounded-xl px-3 -mx-3 mb-1'
            : 'flex items-center gap-3 py-2 opacity-60'"
        >
          <!-- Name -->
          <span
            :class="i === 0
              ? 'font-heading font-extrabold text-base text-content-primary w-32 shrink-0 truncate'
              : 'text-sm font-semibold text-content-secondary w-28 shrink-0 truncate'"
          >{{ group.name }}</span>
          <!-- Genre + number pills -->
          <div class="flex flex-wrap gap-1.5 flex-1">
            <span
              v-for="a in group.entries"
              :key="a.genre"
              class="badge-neutral"
              :class="i === 0
                ? 'inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-700 border border-primary-500/30 text-sm shadow-sm'
                : 'inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-surface-700 border border-primary-500/20 text-xs'"
            >
              <span class="text-content-muted capitalize">{{ a.genre }}</span>
              <span
                :class="i === 0 ? 'font-heading font-extrabold text-primary-400 text-base' : 'font-heading font-extrabold text-primary-400'"
              >#{{ a.auditionNumber }}</span>
            </span>
          </div>
          <!-- Hold-to-reveal ref code -->
          <span
            v-if="group.refCode"
            class="relative shrink-0 inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-surface-800 border border-surface-600 cursor-pointer select-none touch-none"
            @mousedown="revealingRef = group.name"
            @mouseup="revealingRef = null"
            @mouseleave="revealingRef = null"
            @touchstart.prevent="revealingRef = group.name"
            @touchend="revealingRef = null"
            @touchcancel="revealingRef = null"
          >
            <i class="pi pi-eye text-content-muted" style="font-size:0.65rem"></i>
            <span class="text-content-muted">Ref code</span>
            <span
              v-if="revealingRef === group.name"
              class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-4 py-2.5 rounded-xl bg-surface-700 border border-surface-500 shadow-xl whitespace-nowrap z-50 pointer-events-none"
            >
              <span class="font-source tracking-widest text-primary-400 text-base font-bold">{{ group.refCode }}</span>
              <span class="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-surface-500"></span>
            </span>
          </span>
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
    <p class="text-content-secondary leading-relaxed">{{ modalMessage }}</p>
  </ActionDoneModal>
</template>
