<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { getActiveEvent } from '@/utils/auth'
import { getCheckinList, assignAuditionNumbersBatch, swapAuditionNumbers, releaseAuditionNumbers } from '@/utils/api'
import { createClient, subscribeToChannel, deactivateClient } from '@/utils/websocket'
import ActionDoneModal from './ActionDoneModal.vue'

// ── State ──────────────────────────────────────────────────────────────────
const activeEvent = getActiveEvent()
const eventName   = activeEvent?.name ?? ''
const eventId     = activeEvent?.id ?? null

const mode = ref('assign') // 'assign' | 'swap' | 'release'

const checkinList = ref([])
const loading     = ref(false)
let wsClient = null

// Assign mode
const assignPart   = ref(null)
const assignBuffer = ref({}) // { [eventCategoryId]: number | null } — only for unassigned categories

// Swap mode
const swapCategory      = ref(null)
const swapPart1      = ref(null)
const swapPart2      = ref(null)

// Release mode
const releasePart    = ref(null)

// Confirm dialog
const confirm = ref({ show: false, title: '', body: '', onOk: null })
const askConfirm = (title, body, onOk) => { confirm.value = { show: true, title, body, onOk } }
const doConfirm  = () => { confirm.value.onOk?.(); confirm.value.show = false }
const noConfirm  = () => { confirm.value.show = false }

// Result modal
const result = ref({ show: false, title: '', message: '', variant: 'success' })
const showResult = (title, message, variant = 'success') => { result.value = { show: true, title, message, variant } }
const closeResult = () => { result.value.show = false }

// ── Derived data ──────────────────────────────────────────────────────────
const divisionStats = computed(() => {
  const map = {}
  for (const p of checkinList.value) {
    for (const g of p.categories) {
      if (!map[g.eventCategoryId]) map[g.eventCategoryId] = { name: g.categoryName, eventCategoryId: g.eventCategoryId, total: 0, taken: [] }
      map[g.eventCategoryId].total++
      if (g.auditionNumber !== null) map[g.eventCategoryId].taken.push(g.auditionNumber)
    }
  }
  return Object.values(map).sort((a, b) => a.name.localeCompare(b.name))
})

const divisionStatsMap = computed(() => {
  const m = {}
  for (const d of divisionStats.value) m[d.eventCategoryId] = d
  return m
})

const getAvailableNumbers = (eventCategoryId) => {
  const d = divisionStatsMap.value[eventCategoryId]
  if (!d) return []
  const takenSet = new Set(d.taken)
  return Array.from({ length: d.total }, (_, i) => i + 1).filter(n => !takenSet.has(n))
}

const selectAssignPart = (p) => {
  assignPart.value = p
  const buf = {}
  for (const g of p.categories) {
    if (g.auditionNumber === null) buf[g.eventCategoryId] = null
  }
  assignBuffer.value = buf
}

const pickNumber = (ecid, n) => { assignBuffer.value[ecid] = n }

const unassignedParticipants = computed(() =>
  checkinList.value
    .filter(p => p.categories.some(g => g.auditionNumber === null))
    .sort((a, b) => a.label.localeCompare(b.label))
)

const pendingCategories = computed(() => {
  if (!assignPart.value) return []
  return assignPart.value.categories.filter(g => g.auditionNumber === null)
})

const alreadyAssignedCategories = computed(() => {
  if (!assignPart.value) return []
  return assignPart.value.categories.filter(g => g.auditionNumber !== null)
})

const allAssignBufferFilled = computed(() => {
  const entries = Object.values(assignBuffer.value)
  return entries.length > 0 && entries.every(v => v !== null)
})

const missingCount = computed(() =>
  Object.values(assignBuffer.value).filter(v => v === null).length
)

const assignedInSwapCategory = computed(() => {
  if (!swapCategory.value) return []
  return checkinList.value
    .filter(p => p.categories.some(g => g.eventCategoryId === swapCategory.value.eventCategoryId && g.auditionNumber !== null))
    .sort((a, b) => a.label.localeCompare(b.label))
})

const swapNum = (p) => {
  if (!swapCategory.value || !p) return null
  return p.categories.find(g => g.eventCategoryId === swapCategory.value.eventCategoryId)?.auditionNumber ?? null
}

const assignedParticipants = computed(() =>
  checkinList.value
    .filter(p => p.categories.some(g => g.auditionNumber !== null))
    .sort((a, b) => a.label.localeCompare(b.label))
)

// ── Data load ──────────────────────────────────────────────────────────────
const load = async () => {
  if (!eventName) return
  loading.value = true
  const encoded = eventName.split(' ').join('%20')
  const res = await getCheckinList(encoded)
  if (res?.ok) checkinList.value = await res.json()
  loading.value = false
}

// Reload data + reset all selections
const reload = () => {
  assignPart.value   = null
  assignBuffer.value = {}
  swapCategory.value    = null
  swapPart1.value    = null
  swapPart2.value    = null
  releasePart.value  = null
  load()
}

// ── Real-time: patch checkinList on /topic/audition/ ──────────────────────
const applyAuditionMsg = (msg) => {
  if (msg.eventId !== eventId) return
  const participant = checkinList.value.find(p => p.participantId === msg.participantId)
  if (!participant) return
  const category = participant.categories.find(g => g.eventCategoryId === msg.categoryId)
  if (category) category.auditionNumber = msg.auditionNumber
}

// ── Operations ─────────────────────────────────────────────────────────────
const doAssign = () => {
  if (!assignPart.value || !allAssignBufferFilled.value) return
  const assignments = Object.entries(assignBuffer.value).map(([egid, num]) => ({
    category: assignPart.value.categories.find(g => g.eventCategoryId === Number(egid)),
    eventCategoryId: Number(egid),
    auditionNumber: num
  }))
  const lines = assignments.map(a => `${a.category.categoryName} → #${a.auditionNumber}`).join('\n')
  askConfirm(
    'Assign Audition Numbers',
    `${assignPart.value.label}:\n${lines}`,
    async () => {
      const payload = assignments.map(a => ({ eventCategoryId: a.eventCategoryId, auditionNumber: a.auditionNumber }))
      const res = await assignAuditionNumbersBatch(eventId, assignPart.value.participantId, payload)
      if (res?.ok) {
        showResult('Assigned', assignments.map(a => `${a.category.categoryName} → #${a.auditionNumber}`).join(', '))
        reload()
      } else {
        const body = res ? await res.json().catch(() => ({})) : {}
        showResult('Failed — All Rolled Back', body.error ?? 'Could not assign numbers', 'error')
        reload()
      }
    }
  )
}

const doSwap = () => {
  if (!swapPart1.value || !swapPart2.value || !swapCategory.value) return
  const n1 = swapNum(swapPart1.value)
  const n2 = swapNum(swapPart2.value)
  askConfirm(
    'Swap Audition Numbers',
    `${swapPart1.value.label} #${n1} ↔ ${swapPart2.value.label} #${n2} in ${swapCategory.value.name}?`,
    async () => {
      const res = await swapAuditionNumbers(eventId, swapCategory.value.eventCategoryId, swapPart1.value.participantId, swapPart2.value.participantId)
      if (res?.ok) {
        showResult('Swapped', `${swapPart1.value.label} now #${n2}, ${swapPart2.value.label} now #${n1}`)
        reload()
      } else {
        const body = res ? await res.json().catch(() => ({})) : {}
        showResult('Failed', body.error ?? 'Could not swap numbers', 'error')
      }
    }
  )
}

const doRelease = () => {
  if (!releasePart.value) return
  const assigned = releasePart.value.categories.filter(g => g.auditionNumber !== null)
  askConfirm(
    'Release Audition Numbers',
    `Clear ALL ${assigned.length} audition number${assigned.length > 1 ? 's' : ''} for ${releasePart.value.label}? This cannot be undone.`,
    async () => {
      const res = await releaseAuditionNumbers(eventId, releasePart.value.participantId)
      if (res?.ok) {
        showResult('Released', `All audition numbers cleared for ${releasePart.value.label}`)
        reload()
      } else {
        const body = res ? await res.json().catch(() => ({})) : {}
        showResult('Failed', body.error ?? 'Could not release numbers', 'error')
      }
    }
  )
}

// ── Swap selection helper ──────────────────────────────────────────────────
const selectSwap = (p) => {
  if (!swapPart1.value) {
    swapPart1.value = p
  } else if (swapPart1.value.participantId === p.participantId) {
    swapPart1.value = null
  } else if (!swapPart2.value) {
    swapPart2.value = p
  } else {
    swapPart2.value = p
  }
}

const resetSwapOnCategoryChange = (g) => {
  swapCategory.value = g
  swapPart1.value = null
  swapPart2.value = null
}

onMounted(() => {
  load()
  if (eventName) {
    wsClient = createClient()
    subscribeToChannel(wsClient, '/topic/audition/', applyAuditionMsg)
  }
})

onUnmounted(() => {
  if (wsClient) deactivateClient(wsClient)
})
</script>

<template>
  <div class="page-container">
    <div class="color-bleed"></div>

    <!-- Header — h1 for document outline -->
    <div class="mb-4">
      <h1 class="type-page-title mb-1">Audition Adjustment</h1>
      <p class="type-prose">Manually assign, swap, or release audition numbers.</p>
    </div>

    <!-- No event guard -->
    <div v-if="!eventId" class="flex flex-col items-center justify-center py-20 text-center">
      <div class="para-chip-sm w-16 h-16 flex items-center justify-center mb-4">
        <i class="pi pi-exclamation-triangle text-amber-400 text-2xl"></i>
      </div>
      <p class="type-body text-content-secondary">No active event selected</p>
      <p class="type-prose-sm mt-1">Select an event first from the event menu.</p>
    </div>

    <template v-else>
      <!-- Mode tabs -->
      <div class="flex gap-2 mb-4 flex-wrap">
        <button v-for="m in [
          { key: 'assign',  label: 'Assign',  icon: 'pi-hashtag' },
          { key: 'swap',    label: 'Swap',    icon: 'pi-arrow-right-arrow-left' },
          { key: 'release', label: 'Release', icon: 'pi-undo' },
        ]" :key="m.key"
          @click="mode = m.key"
          :aria-pressed="mode === m.key"
          class="para-chip-sm px-5 py-2 type-label transition-all duration-150 flex items-center gap-2"
          :class="mode === m.key ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
        >
          <i class="pi text-xs" :class="m.icon" aria-hidden="true"></i>
          {{ m.label }}
        </button>
        <div class="ml-auto flex items-center gap-2">
          <!-- role=status: live update indicator is announced -->
          <span v-if="loading" class="type-label text-content-muted flex items-center gap-1.5" role="status">
            <i class="pi pi-spinner pi-spin text-xs" aria-hidden="true"></i> Updating…
          </span>
          <button @click="reload" class="para-chip-sm px-3 py-2 type-label text-content-muted hover:text-content-primary transition-colors flex items-center gap-1.5">
            <i class="pi pi-refresh text-xs" aria-hidden="true"></i>
            Refresh
          </button>
        </div>
      </div>

      <!-- ── ASSIGN MODE ─────────────────────────────────────────────────── -->
      <template v-if="mode === 'assign'">
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4" style="height:calc(100vh - 260px)">

          <!-- Step 1: Participant -->
          <div class="card-hover p-4 relative flex flex-col overflow-hidden">
            <div class="corner-bar-tl"></div>
            <div class="section-rule mb-3 flex-shrink-0">
              <span class="section-rule-label">1 · Participant</span>
              <div class="section-rule-line"></div>
            </div>
            <div class="flex-1 overflow-y-auto space-y-1.5 min-h-0" style="scrollbar-width:thin;scrollbar-color:rgba(255,255,255,0.1) transparent">
              <p v-if="unassignedParticipants.length === 0" class="type-label text-content-muted py-4 text-center">All participants assigned</p>
              <button
                v-for="p in unassignedParticipants"
                :key="p.participantId"
                @click="selectAssignPart(p)"
                class="w-full para-chip-sm px-3 py-2 type-name-sm text-left transition-all duration-150 flex items-center justify-between"
                :class="assignPart?.participantId === p.participantId
                  ? 'text-accent border-[color:var(--accent-muted)]'
                  : 'text-content-muted hover:text-content-primary'"
              >
                <span>{{ p.label }}</span>
                <span class="tabular-nums flex-shrink-0 ml-2 text-amber-400/60" style="font-size:0.68rem;letter-spacing:0.12em">
                  {{ p.categories.filter(g => g.auditionNumber === null).length }} unassigned
                </span>
              </button>
            </div>
          </div>

          <!-- Step 2: Assign All Divisions -->
          <div class="card-hover p-4 relative flex flex-col overflow-hidden">
            <div class="corner-bar-tl"></div>
            <div class="section-rule mb-3 flex-shrink-0">
              <span class="section-rule-label">2 · Assign Numbers</span>
              <div class="section-rule-line"></div>
            </div>
            <p v-if="!assignPart" class="type-label text-content-muted py-4 text-center">Select a participant first</p>
            <template v-else>
              <!-- Edge case: all categories got assigned via WS while panel was open -->
              <div v-if="pendingCategories.length === 0" class="type-label text-content-muted py-4 text-center">
                All divisions already assigned for {{ assignPart.label }}
              </div>
              <template v-else>
                <div class="flex-1 overflow-y-auto min-h-0 space-y-4" style="scrollbar-width:thin;scrollbar-color:rgba(255,255,255,0.1) transparent">

                  <!-- For each unassigned category, show a number grid -->
                  <div v-for="g in pendingCategories" :key="g.eventCategoryId">
                    <div class="section-rule mb-2">
                      <span class="section-rule-label" :class="assignBuffer[g.eventCategoryId] ? 'text-accent' : 'text-amber-400/80'">
                        {{ g.categoryName }}
                      </span>
                      <div class="section-rule-line"></div>
                      <span class="type-label ml-2 flex-shrink-0 tabular-nums" :class="assignBuffer[g.eventCategoryId] ? 'text-accent' : 'text-content-muted/30'">
                        {{ assignBuffer[g.eventCategoryId] ? `#${assignBuffer[g.eventCategoryId]}` : '—' }}
                      </span>
                    </div>
                    <div class="flex flex-wrap gap-1.5 pb-1">
                      <p v-if="getAvailableNumbers(g.eventCategoryId).length === 0" class="type-label text-content-muted">No available numbers</p>
                      <button
                        v-for="n in getAvailableNumbers(g.eventCategoryId)"
                        :key="n"
                        @click="pickNumber(g.eventCategoryId, n)"
                        :aria-pressed="assignBuffer[g.eventCategoryId] === n"
                        class="inline-flex items-center justify-center type-label tabular-nums transition-all duration-150"
                        style="width:2.75rem;height:2.75rem;clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
                        :style="assignBuffer[g.eventCategoryId] === n
                          ? 'background:var(--accent-subtle);border:1px solid var(--accent-muted);color:var(--accent-color)'
                          : 'background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.07);color:rgba(255,255,255,0.5)'"
                      >{{ n }}</button>
                    </div>
                  </div>

                  <!-- Already assigned categories (locked, shown for reference) -->
                  <div v-if="alreadyAssignedCategories.length > 0">
                    <div class="section-rule mb-2">
                      <span class="section-rule-label text-content-muted/40">Already Assigned</span>
                      <div class="section-rule-line"></div>
                    </div>
                    <div class="space-y-1.5">
                      <div v-for="g in alreadyAssignedCategories" :key="g.categoryName"
                        class="flex items-center justify-between para-chip-sm px-3 py-2 opacity-40">
                        <span class="type-name-sm text-content-secondary">{{ g.categoryName }}</span>
                        <span class="type-label text-content-muted tabular-nums">#{{ g.auditionNumber }}</span>
                      </div>
                    </div>
                  </div>

                </div>

                <!-- Validation banner + Submit -->
                <div class="pt-3 border-t border-surface-600/30 flex-shrink-0 mt-3">
                  <div v-if="!allAssignBufferFilled" class="flex items-start gap-2 p-3 mb-3"
                    style="border-left:3px solid rgba(245,158,11,0.8);background:rgba(245,158,11,0.06)">
                    <span class="inline-block w-2 h-2 rounded-full mt-1 flex-shrink-0"
                      style="background:rgba(245,158,11,0.8);box-shadow:0 0 6px rgba(245,158,11,0.5)"></span>
                    <p class="type-label text-amber-400/80">
                      {{ missingCount }} division{{ missingCount !== 1 ? 's' : '' }} still need{{ missingCount === 1 ? 's' : '' }} a number — all must be assigned together
                    </p>
                  </div>
                  <button @click="doAssign"
                    class="w-full para-chip-sm px-4 py-2 type-label transition-all duration-150"
                    :class="allAssignBufferFilled ? 'bg-accent' : 'opacity-40 cursor-not-allowed'"
                    :disabled="!allAssignBufferFilled"
                  >
                    Confirm Assign{{ Object.keys(assignBuffer).length > 1 ? ' All' : '' }}
                  </button>
                </div>
              </template>
            </template>
          </div>

        </div>
      </template>

      <!-- ── SWAP MODE ───────────────────────────────────────────────────── -->
      <template v-else-if="mode === 'swap'">
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4" style="height:calc(100vh - 260px)">

          <!-- Division -->
          <div class="card-hover p-4 relative flex flex-col overflow-hidden">
            <div class="corner-bar-tl"></div>
            <div class="section-rule mb-3 flex-shrink-0">
              <span class="section-rule-label">1 · Division</span>
              <div class="section-rule-line"></div>
            </div>
            <div class="flex-1 overflow-y-auto space-y-1.5 min-h-0" style="scrollbar-width:thin;scrollbar-color:rgba(255,255,255,0.1) transparent">
              <button
                v-for="d in divisionStats"
                :key="d.eventCategoryId"
                @click="resetSwapOnCategoryChange(d)"
                class="w-full para-chip-sm px-3 py-2 type-name-sm text-left transition-all duration-150 flex items-center justify-between"
                :class="swapCategory?.eventCategoryId === d.eventCategoryId
                  ? 'text-accent border-[color:var(--accent-muted)]'
                  : 'text-content-muted hover:text-content-primary'"
              >
                <span>{{ d.name }}</span>
                <span class="tabular-nums text-content-muted/60 flex-shrink-0 ml-2">{{ d.taken.length }} assigned</span>
              </button>
              <div v-if="divisionStats.length === 0" class="type-label text-content-muted py-4 text-center">No divisions</div>
            </div>
          </div>

          <!-- Participant selection + confirm -->
          <div class="card-hover p-4 relative flex flex-col overflow-hidden">
            <div class="corner-bar-tl"></div>
            <div class="section-rule mb-3 flex-shrink-0">
              <span class="section-rule-label">2 · Select Two</span>
              <div class="section-rule-line"></div>
            </div>
            <p v-if="!swapCategory" class="type-label text-content-muted py-4 text-center">Select a division first</p>
            <p v-else-if="assignedInSwapCategory.length < 2" class="type-label text-content-muted py-4 text-center">Fewer than 2 participants have numbers in this division</p>
            <template v-else>
              <div class="flex-1 overflow-y-auto space-y-1.5 min-h-0" style="scrollbar-width:thin;scrollbar-color:rgba(255,255,255,0.1) transparent">
                <button
                  v-for="p in assignedInSwapCategory"
                  :key="p.participantId"
                  @click="selectSwap(p)"
                  class="w-full para-chip-sm px-3 py-2 type-name-sm text-left transition-all duration-150 flex items-center justify-between"
                  :class="swapPart1?.participantId === p.participantId || swapPart2?.participantId === p.participantId
                    ? 'text-accent border-[color:var(--accent-muted)]'
                    : 'text-content-muted hover:text-content-primary'"
                >
                  <span>{{ p.label }}</span>
                  <span
                    class="type-stat tabular-nums flex-shrink-0"
                    style="font-size:1.1rem"
                    :class="swapPart1?.participantId === p.participantId || swapPart2?.participantId === p.participantId ? 'text-accent' : 'text-content-muted/40'"
                  >#{{ swapNum(p) }}</span>
                </button>
              </div>
              <div v-if="swapPart1 && swapPart2" class="pt-3 border-t border-surface-600/30 flex-shrink-0">
                <div class="flex items-center gap-3 mb-3 flex-wrap">
                  <span class="type-name text-content-primary">{{ swapPart1.label }} <span class="text-accent">#{{ swapNum(swapPart1) }}</span></span>
                  <i class="pi pi-arrow-right-arrow-left text-content-muted text-xs"></i>
                  <span class="type-name text-content-primary">{{ swapPart2.label }} <span class="text-accent">#{{ swapNum(swapPart2) }}</span></span>
                </div>
                <button @click="doSwap" class="w-full bg-accent para-chip-sm px-4 py-2 type-label">
                  Confirm Swap
                </button>
              </div>
              <p v-else-if="swapPart1" class="type-label text-content-muted pt-3 border-t border-surface-600/30 flex-shrink-0 text-center">
                Select a second participant to swap with
              </p>
            </template>
          </div>

        </div>
      </template>

      <!-- ── RELEASE MODE ────────────────────────────────────────────────── -->
      <template v-else-if="mode === 'release'">
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-4" style="height:calc(100vh - 260px)">

          <!-- Participant list -->
          <div class="card-hover p-4 relative flex flex-col overflow-hidden">
            <div class="corner-bar-tl"></div>
            <div class="section-rule mb-3 flex-shrink-0">
              <span class="section-rule-label">1 · Participant</span>
              <div class="section-rule-line"></div>
            </div>
            <div class="flex-1 overflow-y-auto space-y-1.5 min-h-0" style="scrollbar-width:thin;scrollbar-color:rgba(255,255,255,0.1) transparent">
              <p v-if="assignedParticipants.length === 0" class="type-label text-content-muted py-4 text-center">No participants have audition numbers yet</p>
              <button
                v-for="p in assignedParticipants"
                :key="p.participantId"
                @click="releasePart = p"
                class="w-full para-chip-sm px-3 py-2 type-name-sm text-left transition-all duration-150 flex items-center justify-between"
                :class="releasePart?.participantId === p.participantId
                  ? 'text-accent border-[color:var(--accent-muted)]'
                  : 'text-content-muted hover:text-content-primary'"
              >
                <span>{{ p.label }}</span>
                <span class="tabular-nums text-content-muted/60 flex-shrink-0 ml-2">{{ p.categories.filter(g => g.auditionNumber !== null).length }} div</span>
              </button>
            </div>
          </div>

          <!-- Selected participant details + release -->
          <div class="card-hover p-4 relative flex flex-col overflow-hidden">
            <div class="corner-bar-tl"></div>
            <div class="section-rule mb-3 flex-shrink-0">
              <span class="section-rule-label">2 · Confirm Release</span>
              <div class="section-rule-line"></div>
            </div>
            <p v-if="!releasePart" class="type-label text-content-muted py-4 text-center">Select a participant</p>
            <template v-else>
              <div class="flex-1 overflow-y-auto min-h-0" style="scrollbar-width:thin;scrollbar-color:rgba(255,255,255,0.1) transparent">
                <p class="type-name text-content-primary mb-3" style="font-size:16px;">{{ releasePart.label }}</p>
                <div class="space-y-1.5 mb-4">
                  <div
                    v-for="g in releasePart.categories.filter(g => g.auditionNumber !== null)"
                    :key="g.categoryName"
                    class="flex items-center justify-between para-chip-sm px-3 py-2"
                  >
                    <span class="type-name-sm text-content-secondary">{{ g.categoryName }}</span>
                    <span class="type-stat text-accent" style="font-size:1.1rem">#{{ g.auditionNumber }}</span>
                  </div>
                </div>
                <div class="flex items-start gap-2 p-3"
                  style="border-left:3px solid rgba(245,158,11,0.8);background:rgba(245,158,11,0.06)">
                  <span class="inline-block w-2 h-2 rounded-full mt-1 flex-shrink-0" style="background:rgba(245,158,11,0.8);box-shadow:0 0 6px rgba(245,158,11,0.5)"></span>
                  <p class="type-label text-amber-400/80">
                    All {{ releasePart.categories.filter(g => g.auditionNumber !== null).length }} number{{ releasePart.categories.filter(g => g.auditionNumber !== null).length > 1 ? 's' : '' }} will be cleared. The participant must check in again to receive new numbers.
                  </p>
                </div>
              </div>
              <button @click="doRelease"
                class="w-full para-chip-sm px-4 py-2 type-label transition-colors mt-3 flex-shrink-0"
                style="border-color:rgba(245,158,11,0.4);color:rgba(245,158,11,0.9)">
                Release All Numbers
              </button>
            </template>
          </div>

        </div>
      </template>
    </template>

  </div>

  <!-- Confirm Dialog -->
  <ActionDoneModal
    :show="confirm.show"
    :title="confirm.title"
    variant="warning"
    @accept="doConfirm"
    @close="noConfirm"
  >
    <p class="type-body text-content-secondary" style="white-space:pre-line">{{ confirm.body }}</p>
  </ActionDoneModal>

  <!-- Result Modal -->
  <ActionDoneModal
    :show="result.show"
    :title="result.title"
    :variant="result.variant"
    @accept="closeResult"
    @close="closeResult"
  >
    <p class="type-body text-content-secondary">{{ result.message }}</p>
  </ActionDoneModal>
</template>
