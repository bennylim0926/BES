<script setup>
import { ref, computed, watch } from 'vue'
import { getActiveEvent } from '@/utils/auth'
import { getParticipantScore, getScoringCriteria } from '@/utils/api'
import { getPickupCrews, createPickupCrew, deletePickupCrew } from '@/utils/api'
import ReusableDropdown from '@/components/ReusableDropdown.vue'
import ActionDoneModal from '@/views/ActionDoneModal.vue'

const selectedEvent = ref(getActiveEvent()?.name || localStorage.getItem('selectedEvent') || '')
const selectedGenre = ref('')
const participants = ref([])   // raw score entries for the genre
const crews = ref([])
const criteria = ref([])
const topN = ref(null)  // null = no filter; 4/8/16/32 = show only top N as leaders

// Modal state
const showCrewModal = ref(false)
const pendingCrewName = ref('')
const pendingMembers = ref([])   // [{participantId, displayName}]
const modalError = ref('')

// Error/success feedback
const toast = ref({ show: false, message: '', variant: 'success' })
const showToast = (message, variant = 'success') => {
  toast.value = { show: true, message, variant }
  setTimeout(() => { toast.value.show = false }, 3000)
}

// ── Derived data ───────────────────────────────────────────────────────────────

// Unique genres that have at least one solo pickup entry (format = null or empty)
const uniqueGenres = computed(() => {
  const genres = participants.value.map(p => p.genreName)
  return [...new Set(genres)].sort()
})

// All solo pickup entries for the selected genre (format null/empty)
const soloEntries = computed(() => {
  if (!selectedGenre.value) return []
  // Dedupe by participantName — score rows can be one per judge
  const seen = new Map()
  for (const p of participants.value) {
    if (p.genreName !== selectedGenre.value) continue
    if (p.format && p.format !== '') continue  // skip pre-formed team entries
    if (!seen.has(p.participantName)) {
      seen.set(p.participantName, {
        participantId: p.participantId,
        displayName: p.participantName,
        totalScore: 0,
        scoreCount: 0,
      })
    }
    const entry = seen.get(p.participantName)
    entry.totalScore += p.score ?? 0
    entry.scoreCount++
  }
  return [...seen.values()]
    .map(e => ({ ...e, avgScore: e.scoreCount > 0 ? Math.round(e.totalScore / e.scoreCount * 100) / 100 : null }))
    .sort((a, b) => (b.avgScore ?? -1) - (a.avgScore ?? -1))
})

// Participant IDs already assigned to a crew
const assignedIds = computed(() => {
  const ids = new Set()
  for (const crew of crews.value) {
    for (const m of crew.members) ids.add(m.participantId)
  }
  return ids
})

// IDs of the overall top-N ranked solos (ignoring crew assignment — positions are fixed)
const topNIds = computed(() => {
  if (!topN.value) return new Set()
  const ranked = soloEntries.value.filter(p => p.avgScore !== null)
  return new Set(ranked.slice(0, topN.value).map(p => p.participantId))
})

// Ranked solos (has a score, not yet in a crew); limited to top N when filter is active
const rankedSolos = computed(() =>
  soloEntries.value.filter(p => {
    if (p.avgScore === null) return false
    if (assignedIds.value.has(p.participantId)) return false
    if (topN.value && !topNIds.value.has(p.participantId)) return false
    return true
  })
)

// Available pool (no score OR not ranked, not yet in a crew)
const availablePool = computed(() =>
  soloEntries.value.filter(p => !assignedIds.value.has(p.participantId) && p.avgScore === null)
)

// Crew size from genre format (e.g. "2v2" → 2)
const crewSize = computed(() => {
  // Try to infer from existing crews
  if (crews.value.length > 0 && crews.value[0].members.length > 0) {
    return crews.value[0].members.length
  }
  return null
})

// ── Draft state ────────────────────────────────────────────────────────────────
const draftLeader = ref(null)       // solo from rankedSolos
const selectedPartners = ref([])    // solos from the pool (all non-assigned non-leader entries)

const partnerPool = computed(() =>
  soloEntries.value.filter(p => {
    if (assignedIds.value.has(p.participantId)) return false
    if (p.participantId === draftLeader.value?.participantId) return false
    // When top-N filter active, Top N members cannot be partners
    if (topN.value && topNIds.value.has(p.participantId)) return false
    return true
  })
)

// How many more partners needed
const partnersNeeded = computed(() => {
  if (!crewSize.value || !draftLeader.value) return 0
  return crewSize.value - 1 - selectedPartners.value.length
})

const selectLeader = (solo) => {
  if (draftLeader.value?.participantId === solo.participantId) {
    draftLeader.value = null
    selectedPartners.value = []
  } else {
    draftLeader.value = solo
    selectedPartners.value = []
  }
}

const togglePartner = (solo) => {
  const idx = selectedPartners.value.findIndex(p => p.participantId === solo.participantId)
  if (idx !== -1) {
    selectedPartners.value.splice(idx, 1)
  } else {
    const needed = crewSize.value ? crewSize.value - 1 : Infinity
    if (selectedPartners.value.length < needed) {
      selectedPartners.value.push(solo)
    }
  }
}

const canFormCrew = computed(() => {
  if (!draftLeader.value) return false
  if (crewSize.value) return selectedPartners.value.length === crewSize.value - 1
  return selectedPartners.value.length > 0
})

const openCrewModal = () => {
  pendingCrewName.value = ''
  modalError.value = ''
  pendingMembers.value = [
    { participantId: draftLeader.value.participantId, displayName: draftLeader.value.displayName },
    ...selectedPartners.value.map(p => ({ participantId: p.participantId, displayName: p.displayName })),
  ]
  showCrewModal.value = true
}

const submitCrew = async () => {
  if (!pendingCrewName.value.trim()) {
    modalError.value = 'Please enter a crew name.'
    return
  }
  const ids = pendingMembers.value.map(m => m.participantId)
  const result = await createPickupCrew(selectedEvent.value, selectedGenre.value, pendingCrewName.value.trim(), ids)
  if (result?.error) {
    modalError.value = result.error
    return
  }
  showCrewModal.value = false
  draftLeader.value = null
  selectedPartners.value = []
  await loadCrews()
  showToast(`Crew "${pendingCrewName.value}" created!`)
}

const removeCrew = async (crewId) => {
  await deletePickupCrew(crewId)
  await loadCrews()
  showToast('Crew removed.', 'warning')
}

// ── Data loading ───────────────────────────────────────────────────────────────

const loadCrews = async () => {
  if (!selectedEvent.value || !selectedGenre.value) { crews.value = []; return }
  crews.value = await getPickupCrews(selectedEvent.value, selectedGenre.value)
}

watch(topN, () => {
  draftLeader.value = null
  selectedPartners.value = []
})

watch(selectedGenre, async (val) => {
  draftLeader.value = null
  selectedPartners.value = []
  crews.value = []
  topN.value = null
  if (!val) return
  await loadCrews()
})

watch(selectedEvent, async (val) => {
  if (!val) return
  selectedGenre.value = ''
  const res = await getParticipantScore(val)
  participants.value = res.map((r, i) => ({ ...r, id: i + 1 }))
}, { immediate: true })
</script>

<template>
  <div class="page-container">

    <!-- Header -->
    <div class="mb-6">
      <h1 class="page-title">Crew Formation</h1>
      <p class="text-muted mt-1">Form pickup crews from solo auditioners for team-format battles</p>
    </div>

    <!-- Filters -->
    <div class="card p-5 mb-6">
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="flex flex-col gap-1">
          <span class="text-xs font-semibold text-content-muted uppercase tracking-wide">Event</span>
          <span class="badge-neutral text-sm px-3 py-1.5 self-start">{{ selectedEvent }}</span>
        </div>
        <ReusableDropdown v-model="selectedGenre" labelId="Genre" :options="uniqueGenres" />
        <div v-if="selectedGenre" class="flex flex-col gap-1">
          <span class="text-xs font-semibold text-content-muted uppercase tracking-wide">Draft Mode</span>
          <div class="flex gap-1 flex-wrap">
            <button
              v-for="n in [null, 4, 8, 16, 32]"
              :key="n ?? 'all'"
              @click="topN = n"
              class="px-2.5 py-1 rounded-lg text-xs font-semibold transition-all"
              :class="topN === n
                ? 'bg-primary-600 text-white shadow-sm'
                : 'bg-surface-700 text-content-muted hover:text-content-primary hover:bg-surface-600'"
            >
              {{ n === null ? 'All' : `Top ${n}` }}
            </button>
          </div>
        </div>
        <div v-else-if="crewSize" class="flex flex-col gap-1">
          <span class="text-xs font-semibold text-content-muted uppercase tracking-wide">Format</span>
          <span class="badge-neutral text-sm px-3 py-1.5 self-start font-source">
            {{ crewSize }}v{{ crewSize }} · {{ crewSize }} members per crew
          </span>
        </div>
      </div>
    </div>

    <!-- No genre selected -->
    <div v-if="!selectedGenre" class="flex flex-col items-center justify-center py-24 text-center">
      <div class="w-14 h-14 rounded-2xl bg-surface-700 flex items-center justify-center mb-4">
        <i class="pi pi-users text-content-muted text-xl"></i>
      </div>
      <p class="font-heading font-semibold text-content-secondary">Select a genre to begin</p>
      <p class="text-muted text-sm mt-1">Only genres with solo pickup entries will be listed</p>
    </div>

    <template v-else>

      <!-- Toast -->
      <Transition enter-active-class="transition duration-200" enter-from-class="opacity-0 -translate-y-2"
        enter-to-class="opacity-100 translate-y-0" leave-active-class="transition duration-150"
        leave-from-class="opacity-100" leave-to-class="opacity-0">
        <div v-if="toast.show"
          class="fixed top-20 left-1/2 -translate-x-1/2 z-50 px-5 py-3 rounded-xl shadow-xl text-sm font-semibold border"
          :class="toast.variant === 'success'
            ? 'bg-emerald-500/10 border-emerald-500/40 text-emerald-400'
            : 'bg-amber-500/10 border-amber-500/40 text-amber-400'"
        >
          {{ toast.message }}
        </div>
      </Transition>

      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">

        <!-- Left column: Ranked solos + pool -->
        <div class="flex flex-col gap-4">

          <!-- Draft hint -->
          <div v-if="draftLeader" class="flex items-center gap-3 px-4 py-3 rounded-xl border border-primary-500/30 bg-primary-500/8">
            <i class="pi pi-info-circle text-primary-400 flex-shrink-0"></i>
            <div class="text-sm text-primary-300">
              <span class="font-bold">{{ draftLeader.displayName }}</span> selected as leader.
              <template v-if="crewSize">
                Pick {{ crewSize - 1 - selectedPartners.length }} more partner{{ crewSize - 1 - selectedPartners.length !== 1 ? 's' : '' }}.
              </template>
              <template v-else>Pick partners from below.</template>
            </div>
            <button @click="draftLeader = null; selectedPartners = []"
              class="ml-auto flex-shrink-0 text-xs px-2 py-1 rounded-lg border border-surface-600/50 text-content-muted hover:text-content-primary hover:border-surface-500 transition-all">
              Cancel
            </button>
          </div>

          <!-- Ranked solos -->
          <div class="card overflow-hidden">
            <div class="px-4 pt-4 pb-3 border-b border-surface-600/30">
              <h2 class="font-heading font-bold text-content-primary text-sm">
                Ranked Solos
                <span v-if="topN" class="ml-1.5 px-1.5 py-0.5 rounded-md bg-primary-500/20 text-primary-400 text-xs font-source">Top {{ topN }}</span>
              </h2>
              <p class="text-xs text-content-muted mt-0.5">Click to select as crew leader</p>
            </div>
            <div v-if="rankedSolos.length === 0" class="px-4 py-8 text-center text-sm text-content-muted">
              No ranked solos available
            </div>
            <ul v-else class="divide-y divide-surface-600/20">
              <li
                v-for="(solo, idx) in rankedSolos"
                :key="solo.participantId"
                @click="selectLeader(solo)"
                class="flex items-center gap-3 px-4 py-3 cursor-pointer transition-colors"
                :class="draftLeader?.participantId === solo.participantId
                  ? 'bg-primary-500/15 border-l-2 border-primary-500'
                  : 'hover:bg-surface-700/40'"
              >
                <!-- Rank badge -->
                <div class="flex-shrink-0 w-7 h-7 rounded-full flex items-center justify-center text-xs font-source font-bold"
                  :class="idx === 0 ? 'bg-amber-500/20 text-amber-400' : idx === 1 ? 'bg-surface-500/30 text-surface-300' : idx === 2 ? 'bg-orange-900/30 text-orange-400' : 'bg-surface-700 text-surface-400'">
                  {{ idx + 1 }}
                </div>
                <div class="flex-1 min-w-0">
                  <div class="font-heading font-bold text-content-primary truncate">{{ solo.displayName }}</div>
                  <div class="text-xs text-content-muted">#{{ solo.auditionNumber ?? '—' }}</div>
                </div>
                <div class="flex-shrink-0 text-right">
                  <div class="font-source font-bold text-primary-400 text-sm">{{ solo.avgScore }}</div>
                  <div class="text-xs text-content-muted">avg</div>
                </div>
                <i v-if="draftLeader?.participantId === solo.participantId" class="pi pi-check-circle text-primary-400 flex-shrink-0"></i>
              </li>
            </ul>
          </div>

          <!-- Partner pool (only shown when a leader is selected) -->
          <div v-if="draftLeader" class="card overflow-hidden">
            <div class="px-4 pt-4 pb-3 border-b border-surface-600/30">
              <h2 class="font-heading font-bold text-content-primary text-sm">Partner Pool</h2>
              <p class="text-xs text-content-muted mt-0.5">
                <template v-if="topN">Non-Top {{ topN }} pool · click to add/remove</template>
                <template v-else>Click to add/remove partners</template>
              </p>
            </div>
            <div v-if="partnerPool.length === 0" class="px-4 py-8 text-center text-sm text-content-muted">
              No available partners
            </div>
            <ul v-else class="divide-y divide-surface-600/20">
              <li
                v-for="solo in partnerPool"
                :key="solo.participantId"
                @click="togglePartner(solo)"
                class="flex items-center gap-3 px-4 py-3 cursor-pointer transition-colors"
                :class="selectedPartners.some(p => p.participantId === solo.participantId)
                  ? 'bg-primary-500/15 border-l-2 border-primary-500'
                  : partnersNeeded === 0 && !selectedPartners.some(p => p.participantId === solo.participantId)
                    ? 'opacity-40 cursor-not-allowed'
                    : 'hover:bg-surface-700/40'"
              >
                <div class="flex-1 min-w-0">
                  <div class="font-heading font-bold text-content-primary truncate">{{ solo.displayName }}</div>
                  <div class="text-xs text-content-muted">
                    #{{ solo.auditionNumber ?? '—' }}
                    <span v-if="solo.avgScore !== null" class="ml-2 text-primary-400 font-source font-bold">{{ solo.avgScore }}</span>
                    <span v-else class="ml-2 italic">no score</span>
                  </div>
                </div>
                <i v-if="selectedPartners.some(p => p.participantId === solo.participantId)"
                  class="pi pi-check-circle text-primary-400 flex-shrink-0"></i>
              </li>
            </ul>
          </div>

          <!-- Form crew button -->
          <button
            v-if="draftLeader"
            @click="openCrewModal"
            :disabled="!canFormCrew"
            class="w-full py-3 rounded-xl font-semibold text-sm transition-all duration-200"
            :class="canFormCrew
              ? 'bg-primary-600 text-white hover:bg-primary-700 shadow-sm btn-glow'
              : 'bg-surface-700 text-surface-500 cursor-not-allowed'"
          >
            <i class="pi pi-users mr-2"></i>
            Form Crew
            <span v-if="crewSize" class="ml-1 opacity-70">({{ 1 + selectedPartners.length }}/{{ crewSize }})</span>
          </button>
        </div>

        <!-- Right column: Formed crews -->
        <div>
          <div class="card overflow-hidden">
            <div class="px-4 pt-4 pb-3 border-b border-surface-600/30 flex items-center justify-between">
              <div>
                <h2 class="font-heading font-bold text-content-primary text-sm">Formed Crews</h2>
                <p class="text-xs text-content-muted mt-0.5">{{ crews.length }} crew{{ crews.length !== 1 ? 's' : '' }} formed</p>
              </div>
            </div>

            <div v-if="crews.length === 0" class="px-4 py-12 text-center">
              <i class="pi pi-users text-surface-600 text-3xl mb-3 block"></i>
              <p class="text-sm text-content-muted">No crews formed yet</p>
              <p class="text-xs text-surface-500 mt-1">Select a leader and partners on the left</p>
            </div>

            <ul v-else class="divide-y divide-surface-600/20">
              <li v-for="crew in crews" :key="crew.id" class="p-4">
                <div class="flex items-start justify-between gap-3 mb-2">
                  <div>
                    <div class="font-heading font-bold text-content-primary">{{ crew.crewName }}</div>
                    <div v-if="crew.avgScore !== null" class="text-xs text-content-muted mt-0.5">
                      Avg score: <span class="font-source font-bold text-primary-400">{{ crew.avgScore }}</span>
                    </div>
                  </div>
                  <button
                    @click="removeCrew(crew.id)"
                    class="flex-shrink-0 p-1.5 rounded-lg text-surface-500 hover:text-red-400 hover:bg-red-950/50 transition-all"
                  >
                    <i class="pi pi-trash text-xs"></i>
                  </button>
                </div>
                <div class="flex flex-wrap gap-2">
                  <span
                    v-for="(member, idx) in crew.members"
                    :key="member.participantId"
                    class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold border"
                    :class="idx === 0
                      ? 'bg-primary-500/15 border-primary-500/30 text-primary-300'
                      : 'bg-surface-700/50 border-surface-600/40 text-content-secondary'"
                  >
                    <i v-if="idx === 0" class="pi pi-star-fill" style="font-size: 0.55rem"></i>
                    {{ member.displayName }}
                  </span>
                </div>
              </li>
            </ul>
          </div>
        </div>

      </div>
    </template>

  </div>

  <!-- Crew name modal -->
  <ActionDoneModal
    :show="showCrewModal"
    title="Name Your Crew"
    variant="info"
    @accept="submitCrew"
    @close="showCrewModal = false"
  >
    <div class="space-y-4 mt-1">
      <!-- Members preview -->
      <div class="flex flex-wrap gap-2 p-3 rounded-xl bg-surface-900 border border-surface-600/50">
        <span
          v-for="(m, idx) in pendingMembers"
          :key="m.participantId"
          class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg text-xs font-semibold border"
          :class="idx === 0
            ? 'bg-primary-500/15 border-primary-500/30 text-primary-300'
            : 'bg-surface-700/50 border-surface-600/40 text-content-secondary'"
        >
          <i v-if="idx === 0" class="pi pi-star-fill" style="font-size: 0.55rem"></i>
          {{ m.displayName }}
        </span>
      </div>
      <div>
        <label class="block text-xs font-semibold text-surface-600 uppercase tracking-wider mb-1.5">
          Crew Name
        </label>
        <input
          v-model="pendingCrewName"
          type="text"
          placeholder="Enter crew name…"
          class="input-base"
          @keyup.enter="submitCrew"
        />
        <p v-if="modalError" class="text-xs text-red-400 mt-1.5">{{ modalError }}</p>
      </div>
    </div>
  </ActionDoneModal>
</template>
