<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import ActionDoneModal from './ActionDoneModal.vue'
import { createClient, deactivateClient, subscribeToChannel } from '@/utils/websocket'

// ── State ──────────────────────────────────────────────────────────────────
const slotKey = (participantId, name) => String(participantId ?? name)

const activeSlots = ref([])
// { slotId: string, person: { participantId, name, refCode, memberNames, genres[] }, queue: [], running: false }

const history = ref([])
const revealingRef = ref({})        // { [slotId | historyKey]: boolean }

// Per-slot rolling animation state — keyed by "${slotId}-${genreName}"
const fakeNums = ref({})
const rollingIntervals = {}         // { "${slotId}-${genreName}": intervalId }

const modalTitle = ref('')
const modalMessage = ref('')
const showModal = ref(false)

const wsClients = []

// ── Helpers ─────────────────────────────────────────────────────────────────
const setReveal = (key, val) => { revealingRef.value = { ...revealingRef.value, [key]: val } }
const toggleReveal = (key) => { revealingRef.value = { ...revealingRef.value, [key]: !revealingRef.value[key] } }

function stopRolling(slotId, genreName) {
  const key = `${slotId}-${genreName}`
  clearInterval(rollingIntervals[key])
  delete rollingIntervals[key]
  const next = { ...fakeNums.value }
  delete next[key]
  fakeNums.value = next
}

function stopAllSlotRolling(slotId) {
  const slot = activeSlots.value.find(s => s.slotId === slotId)
  if (!slot) return
  const allGenreKeys = new Set()
  slot.person.genres.forEach(g => allGenreKeys.add(`${slotId}-${g.genreName}`))
  allGenreKeys.forEach(k => {
    clearInterval(rollingIntervals[k])
    delete rollingIntervals[k]
  })
  const next = { ...fakeNums.value }
  allGenreKeys.forEach(k => delete next[k])
  fakeNums.value = next
}

const flushSlotToHistory = (slotId) => {
  const idx = activeSlots.value.findIndex(s => s.slotId === slotId)
  if (idx !== -1) {
    history.value.unshift({ ...activeSlots.value[idx].person })
    activeSlots.value.splice(idx, 1)
  }
}

function animateSlot(slotId, msg) {
  const slot = activeSlots.value.find(s => s.slotId === slotId)
  if (!slot) return

  if (msg.refCode && !slot.person.refCode) slot.person.refCode = msg.refCode

  let genre = slot.person.genres.find(g => g.genreName === msg.genre)
  if (!genre) {
    genre = { genreName: msg.genre, auditionNumber: null, rolling: false }
    slot.person.genres.push(genre)
  }

  genre.rolling = true
  const intervalKey = `${slotId}-${msg.genre}`
  clearInterval(rollingIntervals[intervalKey])
  rollingIntervals[intervalKey] = setInterval(() => {
    fakeNums.value = { ...fakeNums.value, [intervalKey]: Math.floor(Math.random() * 99) + 1 }
  }, 80)

  setTimeout(() => {
    stopRolling(slotId, msg.genre)
    const currentSlot = activeSlots.value.find(s => s.slotId === slotId)
    if (currentSlot) {
      const g = currentSlot.person.genres.find(g => g.genreName === msg.genre)
      if (g) {
        g.rolling = false
        g.auditionNumber = msg.auditionNumber
      }
    }
    processSlotQueue(slotId)
  }, 2000)
}

function processSlotQueue(slotId) {
  const slot = activeSlots.value.find(s => s.slotId === slotId)
  if (!slot) return

  if (slot.queue.length === 0) {
    slot.running = false
    if (slot.person.genres.every(g => g.auditionNumber !== null)) {
      setTimeout(() => flushSlotToHistory(slotId), 1500)
    }
    return
  }

  slot.running = true
  const msg = slot.queue.shift()
  animateSlot(slotId, msg)
}

// ── WS Handlers ─────────────────────────────────────────────────────────────
const onPreview = (msg) => {
  if (msg.cancelled) {
    const id = slotKey(msg.participantId, msg.name)
    const idx = activeSlots.value.findIndex(s => s.slotId === id)
    if (idx !== -1) {
      stopAllSlotRolling(id)
      activeSlots.value.splice(idx, 1)
    }
    return
  }

  const id = slotKey(msg.participantId, msg.name)
  const existing = activeSlots.value.find(s => s.slotId === id)
  if (existing) {
    if (msg.refCode && !existing.person.refCode) existing.person.refCode = msg.refCode
    const existingGenres = new Set(existing.person.genres.map(g => g.genreName))
    for (const g of (msg.genres ?? [])) {
      if (!existingGenres.has(g.genreName)) {
        existing.person.genres.push({ genreName: g.genreName, auditionNumber: g.auditionNumber ?? null, rolling: false })
      }
    }
  } else {
    activeSlots.value.push({
      slotId: id,
      person: {
        participantId: msg.participantId ?? null,
        name: msg.name,
        refCode: msg.refCode ?? null,
        memberNames: msg.memberNames ?? [],
        genres: (msg.genres ?? []).map(g => ({ genreName: g.genreName, auditionNumber: g.auditionNumber ?? null, rolling: false }))
      },
      queue: [],
      running: false
    })
  }
}

const onReceiveAuditionNumber = (msg) => {
  const id = slotKey(msg.participantId, msg.name)
  const slot = activeSlots.value.find(s => s.slotId === id)
  if (slot) {
    if (!slot.person.genres.find(g => g.genreName === msg.genre)) {
      slot.person.genres.push({ genreName: msg.genre, auditionNumber: null, rolling: false })
    }
    if (msg.refCode && !slot.person.refCode) slot.person.refCode = msg.refCode
    slot.queue.push(msg)
    if (!slot.running) processSlotQueue(id)
  } else {
    const historyEntry = history.value.find(h => slotKey(h.participantId, h.name) === id)
    if (historyEntry) {
      const hGenre = historyEntry.genres.find(g => g.genreName === msg.genre)
      if (hGenre) hGenre.auditionNumber = msg.auditionNumber
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

    <!-- ── Current Participants ────────────────────────────────────────── -->
    <div class="section-rule mb-4">
      <span class="section-rule-label">Current Participant</span>
      <div class="section-rule-line"></div>
    </div>

    <!-- Idle state -->
    <div v-if="activeSlots.length === 0" class="flex flex-col items-center justify-center py-16 text-center">
      <div class="para-chip-sm w-16 h-16 flex items-center justify-center mb-4">
        <i class="pi pi-qrcode text-content-muted text-2xl"></i>
      </div>
      <p class="type-body text-content-secondary">Waiting for check-in…</p>
      <p class="type-label text-content-muted mt-1">Check in a participant on the Event Details page</p>
    </div>

    <!-- Active slots grid -->
    <div
      v-else
      class="grid gap-4 mb-6"
      :style="{ gridTemplateColumns: `repeat(${Math.min(activeSlots.length, 3)}, 1fr)` }"
    >
      <div
        v-for="slot in activeSlots"
        :key="slot.slotId"
        class="card-hover p-5 relative"
      >
        <div class="corner-bar-tl"></div>
        <div class="corner-bar-bl"></div>

        <!-- Name row -->
        <div class="flex items-start justify-between gap-4 mb-1">
          <div class="flex-1 min-w-0">
            <p class="type-label text-content-muted mb-1">Good Luck</p>
            <p class="type-page-title text-content-primary leading-tight" style="font-size:clamp(1.4rem,4vw,2.2rem)">
              {{ slot.person.name }}
            </p>
          </div>
          <!-- Ref code chip (hold to reveal) -->
          <div
            v-if="slot.person.refCode"
            class="flex-shrink-0 para-chip-sm px-3 py-2 cursor-pointer select-none flex flex-col items-end gap-0.5"
            @mousedown="setReveal(slot.slotId, true)" @mouseup="setReveal(slot.slotId, false)" @mouseleave="setReveal(slot.slotId, false)"
            @touchstart="setReveal(slot.slotId, true)" @touchend="setReveal(slot.slotId, false)" @touchcancel="setReveal(slot.slotId, false)"
          >
            <span class="type-label text-content-muted">Ref Code</span>
            <span v-if="revealingRef[slot.slotId]" class="font-source tracking-widest text-accent" style="font-size:1rem;letter-spacing:0.2em">
              {{ slot.person.refCode }}
            </span>
            <span v-else class="type-label text-content-muted/40">Hold to reveal</span>
          </div>
          <div v-else-if="slot.person.genres.some(g => g.auditionNumber === null)" class="flex-shrink-0 para-chip-sm px-3 py-2 flex items-center gap-1.5">
            <i class="pi pi-spin pi-spinner text-content-muted text-xs"></i>
            <span class="type-label text-content-muted">Assigning…</span>
          </div>
        </div>

        <!-- Team members -->
        <div v-if="slot.person.memberNames?.length" class="flex items-center gap-1.5 type-label text-content-muted mb-3">
          <i class="pi pi-users" style="font-size:0.65rem"></i>
          <span>{{ slot.person.memberNames.join(' · ') }}</span>
        </div>

        <!-- Divisions -->
        <div class="section-rule my-3">
          <span class="section-rule-label">Divisions</span>
          <div class="section-rule-line"></div>
        </div>

        <div class="space-y-2">
          <div
            v-for="g in slot.person.genres"
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
                  {{ fakeNums[`${slot.slotId}-${g.genreName}`] ?? '—' }}
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

          <!-- Ref code (click to reveal) -->
          <span
            v-if="person.refCode"
            class="relative ml-auto shrink-0 inline-flex items-center gap-1.5 para-chip-sm px-2.5 py-1 type-label cursor-pointer select-none transition-colors"
            :class="revealingRef['h-' + person.name + '-' + i] ? 'text-accent' : 'text-content-muted hover:text-accent'"
            @click="toggleReveal('h-' + person.name + '-' + i)"
          >
            <i class="pi pi-eye" style="font-size:0.6rem"></i>
            <template v-if="revealingRef['h-' + person.name + '-' + i]">
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
