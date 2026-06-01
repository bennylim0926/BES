<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import ActionDoneModal from './ActionDoneModal.vue'
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket'

// ── State ──────────────────────────────────────────────────────────────────
const currentPerson = ref(null)
// { name, refCode, memberNames: [], genres: [{ genreName, auditionNumber, rolling }] }

const history = ref([])
const revealingRef = ref(null)

// Per-division slot animation
const fakeNums = ref({})        // { [genreName]: number }
const rollingIntervals = {}     // { [genreName]: intervalId }

// Sequential animation queue — processes one division at a time
const auditionQueue = []
let queueRunning = false

const modalTitle = ref('')
const modalMessage = ref('')
const showModal = ref(false)

const wsClients = []

// ── Helpers ─────────────────────────────────────────────────────────────────
const flushCurrentToHistory = () => {
  if (currentPerson.value) {
    // Capture any numbers still pending in the queue so history is accurate
    const pendingNums = {}
    auditionQueue.forEach(m => { if (isSamePerson(currentPerson.value, m)) pendingNums[m.genre] = m.auditionNumber })
    const genres = currentPerson.value.genres.map(g => ({
      ...g,
      auditionNumber: g.auditionNumber ?? pendingNums[g.genreName] ?? null
    }))
    history.value.unshift({ ...currentPerson.value, genres })
  }
}

function startRolling(genreName) {
  clearInterval(rollingIntervals[genreName])
  rollingIntervals[genreName] = setInterval(() => {
    fakeNums.value = { ...fakeNums.value, [genreName]: Math.floor(Math.random() * 99) + 1 }
  }, 80)
}

function stopRolling(genreName) {
  clearInterval(rollingIntervals[genreName])
  delete rollingIntervals[genreName]
  const next = { ...fakeNums.value }
  delete next[genreName]
  fakeNums.value = next
}

function processNextInQueue() {
  if (auditionQueue.length === 0) {
    queueRunning = false
    return
  }
  queueRunning = true
  const msg = auditionQueue.shift()
  animateAuditionNumber(msg)
}

function animateAuditionNumber(msg) {
  // If person moved to history before animation ran, update history and skip animation
  if (!isSamePerson(currentPerson.value, msg)) {
    const historyEntry = history.value.find(h => h.name === msg.name)
    if (historyEntry) {
      const hGenre = historyEntry.genres.find(g => g.genreName === msg.genre)
      if (hGenre) hGenre.auditionNumber = msg.auditionNumber
    }
    processNextInQueue()
    return
  }

  // Update refCode once we have it
  if (msg.refCode && !currentPerson.value.refCode) {
    currentPerson.value.refCode = msg.refCode
  }

  // Find or create the genre entry (auditionNumber stays null until animation reveals it)
  let genre = currentPerson.value.genres.find(g => g.genreName === msg.genre)
  if (!genre) {
    genre = { genreName: msg.genre, auditionNumber: null, rolling: false }
    currentPerson.value.genres.push(genre)
  }

  // Slot animation — reveal the number only when animation finishes (no spoiler)
  genre.rolling = true
  startRolling(msg.genre)
  setTimeout(() => {
    stopRolling(msg.genre)
    const g = currentPerson.value?.genres.find(g => g.genreName === msg.genre)
    if (g) {
      g.rolling = false
      g.auditionNumber = msg.auditionNumber
    }
    processNextInQueue()
  }, 2000)
}

// ── WS Handlers ─────────────────────────────────────────────────────────────
const isSamePerson = (a, b) =>
  a && b && (a.participantId != null && b.participantId != null
    ? a.participantId === b.participantId
    : a.name === b.name)

const onPreview = (msg) => {
  if (isSamePerson(currentPerson.value, msg)) {
    // Same person — merge any new genres without overwriting existing
    if (msg.refCode && !currentPerson.value.refCode) currentPerson.value.refCode = msg.refCode
    const existing = new Set(currentPerson.value.genres.map(g => g.genreName))
    for (const g of (msg.genres ?? [])) {
      if (!existing.has(g.genreName)) {
        currentPerson.value.genres.push({ genreName: g.genreName, auditionNumber: g.auditionNumber ?? null, rolling: false })
      }
    }
  } else {
    // Different person — discard pending animations and flush current to history
    if (currentPerson.value) {
      auditionQueue.length = 0
      flushCurrentToHistory()
    }
    currentPerson.value = {
      participantId: msg.participantId ?? null,
      name: msg.name,
      refCode: msg.refCode ?? null,
      memberNames: msg.memberNames ?? [],
      genres: (msg.genres ?? []).map(g => ({ genreName: g.genreName, auditionNumber: g.auditionNumber ?? null, rolling: false }))
    }
  }
}

const onReceiveAuditionNumber = (msg) => {
  if (isSamePerson(currentPerson.value, msg)) {
    // Ensure genre row exists (pending state) — number revealed only after animation
    if (!currentPerson.value.genres.find(g => g.genreName === msg.genre)) {
      currentPerson.value.genres.push({ genreName: msg.genre, auditionNumber: null, rolling: false })
    }
    if (msg.refCode && !currentPerson.value.refCode) currentPerson.value.refCode = msg.refCode
    auditionQueue.push(msg)
    if (!queueRunning) processNextInQueue()
  } else {
    // Late message — update history directly, no animation
    const historyEntry = history.value.find(h => h.name === msg.name)
    if (historyEntry) {
      const hGenre = historyEntry.genres.find(g => g.genreName === msg.genre)
      if (hGenre) hGenre.auditionNumber = msg.auditionNumber
    } else {
      auditionQueue.push(msg)
      if (!queueRunning) processNextInQueue()
    }
  }
}

const onRepeatAudition = (msg) => {
  const judgeLabel = msg.judge ? ` · Judge: ${msg.judge}` : ''
  modalTitle.value = `Hey ${msg.name}!`
  modalMessage.value = `Your audition number is ${msg.genre} #${msg.audition}${judgeLabel}`
  showModal.value = true
}

const clearHistory = () => { history.value = [] }

onMounted(() => {
  const c1 = createClient(); wsClients.push(c1)
  subscribeToChannel(c1, '/topic/checkin-preview/', onPreview)
  const c2 = createClient(); wsClients.push(c2)
  subscribeToChannel(c2, '/topic/audition/', onReceiveAuditionNumber)
  const c3 = createClient(); wsClients.push(c3)
  subscribeToChannel(c3, '/topic/error/', onRepeatAudition)
})

onBeforeUnmount(() => {
  Object.values(rollingIntervals).forEach(clearInterval)
  wsClients.forEach(c => deactivateClient(c))
})
</script>

<template>
  <div class="page-container">
    <div class="color-bleed"></div>

    <!-- ── Current Participant ─────────────────────────────────────────── -->
    <div class="section-rule mb-4">
      <span class="section-rule-label">Current Participant</span>
      <div class="section-rule-line"></div>
    </div>

    <!-- Idle state -->
    <div v-if="!currentPerson" class="flex flex-col items-center justify-center py-16 text-center">
      <div class="para-chip-sm w-16 h-16 flex items-center justify-center mb-4">
        <i class="pi pi-qrcode text-content-muted text-2xl"></i>
      </div>
      <p class="type-body text-content-secondary">Waiting for check-in…</p>
      <p class="type-label text-content-muted mt-1">Check in a participant on the Event Details page</p>
    </div>

    <!-- Active participant card -->
    <div v-else class="card-hover p-5 relative mb-6">
      <div class="corner-bar-tl"></div>
      <div class="corner-bar-bl"></div>

      <!-- Name row -->
      <div class="flex items-start justify-between gap-4 mb-1">
        <div class="flex-1 min-w-0">
          <p class="type-label text-content-muted mb-1">Good Luck</p>
          <p class="type-page-title text-content-primary leading-tight" style="font-size:clamp(1.4rem,4vw,2.2rem)">
            {{ currentPerson.name }}
          </p>
        </div>
        <!-- Ref code chip (hold to reveal) -->
        <div
          v-if="currentPerson.refCode"
          class="flex-shrink-0 para-chip-sm px-3 py-2 cursor-pointer select-none flex flex-col items-end gap-0.5"
          @mousedown="revealingRef = 'current'" @mouseup="revealingRef = null" @mouseleave="revealingRef = null"
          @touchstart="revealingRef = 'current'" @touchend="revealingRef = null" @touchcancel="revealingRef = null"
        >
          <span class="type-label text-content-muted">Ref Code</span>
          <span v-if="revealingRef === 'current'" class="font-source tracking-widest text-accent" style="font-size:1rem;letter-spacing:0.2em">
            {{ currentPerson.refCode }}
          </span>
          <span v-else class="type-label text-content-muted/40">Hold to reveal</span>
        </div>
        <div v-else-if="currentPerson.genres.some(g => g.auditionNumber === null)" class="flex-shrink-0 para-chip-sm px-3 py-2 flex items-center gap-1.5">
          <i class="pi pi-spin pi-spinner text-content-muted text-xs"></i>
          <span class="type-label text-content-muted">Assigning…</span>
        </div>
      </div>

      <!-- Team members -->
      <div v-if="currentPerson.memberNames?.length" class="flex items-center gap-1.5 type-label text-content-muted mb-3">
        <i class="pi pi-users" style="font-size:0.65rem"></i>
        <span>{{ currentPerson.memberNames.join(' · ') }}</span>
      </div>

      <!-- Divisions -->
      <div class="section-rule my-3">
        <span class="section-rule-label">Divisions</span>
        <div class="section-rule-line"></div>
      </div>

      <div class="space-y-2">
        <div
          v-for="g in currentPerson.genres"
          :key="g.genreName"
          class="flex items-center gap-3 para-chip-sm px-3 py-2.5"
          :style="g.auditionNumber !== null ? { borderColor: 'var(--accent-muted)', background: 'var(--accent-subtle)' } : {}"
        >
          <!-- Status dot -->
          <span
            class="inline-block w-2 h-2 rounded-full flex-shrink-0"
            :style="g.auditionNumber !== null
              ? 'background:var(--accent-color);box-shadow:0 0 8px var(--accent-muted)'
              : g.rolling
                ? 'background:rgba(245,158,11,0.7);box-shadow:0 0 6px rgba(245,158,11,0.5)'
                : 'background:rgba(255,255,255,0.15)'"
          ></span>

          <!-- Division name -->
          <span class="type-body text-content-primary flex-1">{{ g.genreName }}</span>

          <!-- Number area -->
          <div class="flex items-baseline gap-1 tabular-nums min-w-[5rem] justify-end">
            <!-- Rolling -->
            <template v-if="g.rolling">
              <span class="type-label text-amber-400/60 text-xs">Drawing</span>
              <span class="type-stat text-amber-400" style="font-size:1.6rem">
                {{ fakeNums[g.genreName] ?? '—' }}
              </span>
            </template>
            <!-- Revealed -->
            <template v-else-if="g.auditionNumber !== null">
              <span class="type-label text-accent/60 text-xs">#</span>
              <span class="type-stat text-accent" style="font-size:1.6rem">{{ g.auditionNumber }}</span>
            </template>
            <!-- Pending -->
            <template v-else>
              <span class="type-stat text-content-muted/20" style="font-size:1.6rem">—</span>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- ── History ──────────────────────────────────────────────────────── -->
    <template v-if="history.length > 0">
      <div class="section-rule mb-3">
        <span class="section-rule-label">History</span>
        <div class="section-rule-line"></div>
        <button @click="clearHistory" class="para-chip-sm px-2 py-0.5 type-label text-content-muted hover:text-content-primary transition-colors ml-3 flex-shrink-0">
          Clear
        </button>
      </div>

      <div class="space-y-2">
        <div
          v-for="(person, i) in history"
          :key="person.name + i"
          class="para-chip-sm px-3 py-2.5 flex items-center gap-3 flex-wrap"
          :class="i === 0 ? 'border-white/15' : 'opacity-50'"
        >
          <!-- Name -->
          <span class="type-body text-content-primary shrink-0 min-w-[6rem]">{{ person.name }}</span>

          <!-- Division chips -->
          <div class="flex flex-wrap gap-1.5 flex-1 min-w-0">
            <span
              v-for="g in person.genres"
              :key="g.genreName"
              class="inline-flex items-center gap-1 badge-neutral capitalize"
            >
              <span class="text-content-muted">{{ g.genreName }}</span>
              <span class="text-accent">#{{ g.auditionNumber }}</span>
            </span>
          </div>

          <!-- Ref code (press and hold to reveal) -->
          <span
            v-if="person.refCode"
            class="relative ml-auto shrink-0 inline-flex items-center gap-1.5 para-chip-sm px-2.5 py-1 type-label cursor-pointer select-none transition-colors"
            :class="revealingRef === person.name + i ? 'text-accent' : 'text-content-muted hover:text-accent'"
            @click="revealingRef = revealingRef === person.name + i ? null : person.name + i"
          >
            <i class="pi pi-eye" style="font-size:0.6rem"></i>
            <template v-if="revealingRef === person.name + i">
              <span class="font-source tracking-widest" style="font-size:0.75rem;letter-spacing:0.2em">{{ person.refCode }}</span>
            </template>
            <template v-else>Ref</template>
          </span>
        </div>
      </div>
    </template>
  </div>

  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    variant="info"
    @accept="() => { showModal = false }"
    @close="() => { showModal = false }"
  >
    <p class="type-body text-content-secondary">{{ modalMessage }}</p>
  </ActionDoneModal>
</template>
