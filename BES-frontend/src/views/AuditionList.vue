<script setup>
import DynamicTable from '@/components/DynamicTable.vue';
import ReusableButton from '@/components/ReusableButton.vue';
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import { getAllJudges, getRegisteredParticipantsByEvent, submitParticipantScore, whoami } from '@/utils/api';
import { createClient, subscribeToChannel, deactivateClient } from '@/utils/websocket';
import { ref, computed, onMounted, onUnmounted, watch, toRaw } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import Timer from '@/components/Timer.vue';
import { getActiveEvent } from '@/utils/auth';
import SwipeableCardsV2 from '@/components/SwipeableCardsV2.vue';
import MiniScoreMenu from '@/components/MiniScoreMenu.vue';
import 'primeicons/primeicons.css'

const roles = ref(["Emcee", "Judge"])
const selectedEvent = ref(getActiveEvent()?.name || localStorage.getItem("selectedEvent") || "")
const selectedRole = ref(localStorage.getItem("selectedRole") || "")
const selectedGenre = ref(localStorage.getItem("selectedGenre") || "")
const filteredJudge = ref("")
const currentJudge = ref(localStorage.getItem("currentJudge") || "")
const allJudges = ref([])
const participants = ref([])

const modalTitle = ref("")
const modalMessage = ref("")
const modalVariant = ref("info")
const showModal = ref(false)
const showMiniMenu = ref(false)
const dynamicCallBack = ref(() => {})

const dynamicRole = async () => {
  const res = await whoami()
  const authority = res.role?.[0]?.authority
  if (authority === "ROLE_EMCEE") {
    roles.value = ["Emcee"]
    selectedRole.value = "Emcee"
  } else if (authority === "ROLE_JUDGE") {
    roles.value = ["Judge"]
    selectedRole.value = "Judge"
  } else if (authority === "ROLE_ORGANISER") {
    roles.value = ["Emcee"]
    selectedRole.value = "Emcee"
  } else if (authority === "ROLE_ADMIN") {
    roles.value = ["Emcee", "Judge"]
    selectedRole.value = localStorage.getItem("selectedRole") || ""
  }
}

const hasJudge = computed(() => participants.value.some(item => item.judgeName !== null))

const openModal = (title, message, variant = 'info') => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = variant
  showModal.value = true
  dynamicCallBack.value = () => { showModal.value = false }
}

const confirmReset = (title, message) => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = 'warning'
  showModal.value = true
  dynamicCallBack.value = () => { showModal.value = false; resetScore(); }
}

const confirmSubmit = async (title, message) => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = 'info'
  showModal.value = true
  dynamicCallBack.value = async () => {
    showModal.value = false;
    await submitScore(selectedEvent.value, selectedGenre.value,
      currentJudge.value, filteredParticipantsForJudge.value);
  }
}

const filteredParticipantsForJudge = computed({
  get() {
    return participants.value
      .filter(p =>
        p.genreName === selectedGenre.value &&
        p.judgeName === (filteredJudge.value === "" ? null : filteredJudge.value) &&
        p.auditionNumber !== null
      )
      .sort((a, b) => a.auditionNumber - b.auditionNumber)
  },
  set(updatedList) {
    updatedList.forEach(updated => {
      const idx = participants.value.findIndex(p => p.auditionNumber === updated.auditionNumber)
      if (idx !== -1) {
        participants.value[idx] = { ...participants.value[idx], ...updated }
      }
    })
  }
})

watch(filteredParticipantsForJudge, (newVal) => {
  const update = newVal.find(c => c.score !== 0)
  if (update) {
    localStorage.setItem("currentScore", JSON.stringify({ event: selectedEvent.value, scores: toRaw(newVal) }))
  }
}, { deep: true });

const filteredParticipantsForEmcee = computed({
  get() {
    if (filteredJudge.value === "") {
      return transformForTable(participants.value.filter(p => p.genreName === selectedGenre.value && p.auditionNumber != null))
    }
    return transformForTable(participants.value.filter(p =>
      p.genreName === selectedGenre.value && p.judgeName === filteredJudge.value && p.auditionNumber !== null
    ))
  },
  set(updatedSubset) {
    const byId = new Map(updatedSubset.map(r => [r.rowId, r]));
    participants.value = participants.value.map(org => {
      const updated = byId.get(org.rowId)
      return updated ? { ...org, ...updated } : org
    })
  }
})

watch(selectedEvent, async (newVal, oldVal) => {
  if (newVal) {
    localStorage.setItem("selectedEvent", newVal);
    if (oldVal !== undefined && oldVal !== newVal) {
      selectedGenre.value = ""
    }
    participants.value = []
    const res = await getRegisteredParticipantsByEvent(newVal)
    if (selectedEvent.value !== newVal) return
    participants.value = res.map((r, i) => ({ ...r, rowId: r.rowId ?? i, score: 0 }))
    try {
      const stored = localStorage.getItem("currentScore")
      if (stored) {
        const parsed = JSON.parse(stored)
        const cached = parsed.event === newVal ? parsed.scores : null
        if (cached) {
          participants.value = participants.value.map(p => {
            const found = cached.find(c => c.participantName === p.participantName &&
              c.genreName === p.genreName && c.judgeName === p.judgeName)
            return found ? { ...p, score: found.score } : p
          })
        }
      }
    } catch { /* ignore malformed cache */ }
  }
}, { immediate: true });

watch(selectedGenre, (newVal) => { if (newVal) localStorage.setItem("selectedGenre", newVal); }, { immediate: true });
watch(selectedRole, (newVal) => { if (newVal) localStorage.setItem("selectedRole", newVal); }, { immediate: true });
watch(currentJudge, (newVal) => { if (newVal) localStorage.setItem("currentJudge", newVal); }, { immediate: true });

const uniqueGenres = computed(() => {
  const genres = participants.value.map(p => p.genreName);
  return [...new Set(genres)].sort();
})

function transformForTable(data) {
  const judges = [...new Set(data.map(d => d.judgeName).filter(j => j !== null))];
  if (judges.length === 0) {
    return {
      columns: [
        { key: 'auditionNumber', label: 'No.', type: 'text', readonly: true },
        { key: 'participantName', label: 'Name', type: 'text', readonly: true },
        { key: 'marked', label: 'Done', type: 'boolean' }
      ],
      rows: data
        .sort((a, b) => a.auditionNumber - b.auditionNumber)
        .map((d) => ({ auditionNumber: d.auditionNumber, participantName: d.participantName, marked: false }))
    };
  }
  const auditions = {};
  data.forEach(d => {
    if (!auditions[d.auditionNumber]) {
      auditions[d.auditionNumber] = { auditionNumber: d.auditionNumber };
    }
    auditions[d.auditionNumber][d.judgeName] = d.participantName;
  });
  const rows = Object.values(auditions)
    .map(row => {
      const allEmpty = judges.every(j => !row[j]);
      if (allEmpty) return null;
      judges.forEach(j => { if (!row[j]) row[j] = ""; });
      return row;
    })
    .filter(Boolean)
    .sort((a, b) => a.auditionNumber - b.auditionNumber);
  return {
    columns: [
      { key: 'auditionNumber', label: 'Audition', type: 'text', readonly: true },
      ...judges.map(j => ({ key: j, label: j, type: 'text', readonly: true }))
    ],
    rows
  };
}

const submitScore = async (eventName, genreName, judgeName, participants) => {
  if (judgeName === "") {
    openModal("Missing Judge", "Please select a judge before submitting.", "warning")
    return
  }
  const p = participants.map(obj => ({ ...obj, score: parseFloat(obj.score) }))
  const res = await submitParticipantScore(eventName, genreName, judgeName, p)
  if (res.ok) {
    openModal("Scores Submitted", "All scores have been saved successfully.", "success")
  }
}

const resetScore = () => {
  localStorage.removeItem("currentScore");
  participants.value = participants.value.map(obj => ({ ...obj, score: 0 }))
}

const showFilters = ref(true)

const wsClients = []

onUnmounted(() => {
  wsClients.forEach(c => deactivateClient(c))
})

onMounted(async () => {
  await dynamicRole()
  const res = await getAllJudges()
  allJudges.value = ["", ...Object.values(res).map(item => item.judgeName)];
  const c1 = createClient()
  wsClients.push(c1)
  subscribeToChannel(c1, "/topic/audition/",
    (msg) => {
      if (msg.eventName && msg.eventName !== selectedEvent.value) return
      const idx = participants.value.findIndex(p =>
        p.participantName === msg.name &&
        p.genreName === msg.genre &&
        (p.judgeName === null ? 1 : p.judgeName === msg.judge)
      )
      if (idx !== -1) {
        participants.value[idx] = { ...participants.value[idx], auditionNumber: msg.auditionNumber }
      } else if (msg.eventName) {
        participants.value.push({
          participantName: msg.name,
          genreName: msg.genre,
          judgeName: msg.judge || null,
          auditionNumber: msg.auditionNumber,
          eventName: msg.eventName,
          participantId: msg.participantId,
          eventId: msg.eventId,
          genreId: msg.genreId,
          walkin: msg.walkin,
          emailSent: false,
          score: 0,
          rowId: participants.value.length
        })
      }
    })
  const c2 = createClient()
  wsClients.push(c2)
  subscribeToChannel(c2, "/topic/judge-update/",
    (msg) => {
      if (msg.eventName !== selectedEvent.value) return
      const idx = participants.value.findIndex(p =>
        p.participantName === msg.name && p.genreName === msg.genre
      )
      if (idx !== -1) {
        participants.value[idx] = { ...participants.value[idx], judgeName: msg.judge || null }
      }
    })
  const c3 = createClient()
  wsClients.push(c3)
  subscribeToChannel(c3, "/topic/participant-removed/",
    (msg) => {
      if (msg.eventName !== selectedEvent.value) return
      participants.value = participants.value.filter(p =>
        !(p.participantName === msg.name && p.genreName === msg.genre &&
          (msg.judge ? p.judgeName === msg.judge : true))
      )
    })
})
</script>

<template>
  <div class="page-container">

    <!-- Page header + action toolbar -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-6">
      <div>
        <h1 class="page-title">Audition List</h1>
        <p class="text-muted mt-1">
          {{ selectedRole === 'Judge' ? 'Score participants for your genre' : 'Track audition progress' }}
        </p>
      </div>

      <!-- Action buttons -->
      <div class="flex items-center gap-2 flex-wrap">
        <!-- Toggle filters -->
        <button
          @click="showFilters = !showFilters"
          class="flex items-center gap-2 px-3.5 py-2 rounded-xl border text-sm font-semibold transition-all duration-200"
          :class="showFilters
            ? 'bg-surface-600 text-content-primary border-surface-500'
            : 'bg-surface-800 text-content-secondary border-surface-600 hover:border-surface-500'"
        >
          <i class="pi text-xs" :class="showFilters ? 'pi-filter-slash' : 'pi-filter'"></i>
          Filters
        </button>

        <!-- Judge-only actions -->
        <template v-if="selectedRole === 'Judge'">
          <button
            @click="showMiniMenu = !showMiniMenu"
            class="flex items-center gap-2 px-3.5 py-2 rounded-xl border border-surface-600 bg-surface-800
                   text-sm font-semibold text-content-secondary hover:border-surface-500 transition-all duration-200"
          >
            <i class="pi pi-search text-xs"></i>
            Jump
          </button>
          <button
            @click="confirmSubmit('Submit Scores', 'Are you sure you want to submit all scores now?')"
            class="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-primary-600 text-white text-sm
                   font-semibold hover:bg-primary-700 shadow-sm transition-all duration-200 btn-glow"
          >
            <i class="pi pi-send text-xs"></i>
            Submit
          </button>
          <button
            @click="confirmReset('Reset Scores', 'Are you sure you want to reset all scores? This cannot be undone.')"
            class="flex items-center gap-2 px-3.5 py-2 rounded-xl border border-red-800/50 bg-transparent
                   text-sm font-semibold text-red-400 hover:bg-red-950 transition-all duration-200"
          >
            <i class="pi pi-undo text-xs"></i>
            Reset
          </button>
        </template>
      </div>
    </div>

    <!-- Filter panel -->
    <Transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="opacity-0 -translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 -translate-y-2"
    >
      <div v-if="showFilters" class="card p-5 mb-6">
        <div class="grid grid-cols-2 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          <ReusableDropdown v-model="selectedRole"  labelId="Role"  :options="roles" />
          <ReusableDropdown v-model="selectedGenre" labelId="Genre" :options="uniqueGenres" />
          <div class="flex flex-col gap-1">
            <span class="text-xs font-semibold text-content-muted uppercase tracking-wide">Event</span>
            <span class="badge-neutral text-sm px-3 py-1.5 self-start">{{ selectedEvent }}</span>
          </div>
          <ReusableDropdown v-if="hasJudge" v-model="filteredJudge" labelId="Judge" :options="allJudges" />
        </div>

        <!-- Current judge selector (judge role only) -->
        <div v-if="selectedRole === 'Judge'" class="mt-4 pt-4 border-t border-surface-600/30">
          <div class="max-w-xs">
            <ReusableDropdown v-model="currentJudge" labelId="You are judging as" :options="allJudges" />
          </div>
        </div>
      </div>
    </Transition>

    <!-- Emcee view: Timer + table -->
    <template v-if="selectedRole === 'Emcee' && filteredParticipantsForEmcee.rows.length > 0">
      <div class="sticky top-[72px] z-20 mb-4">
        <Timer />
      </div>
      <DynamicTable
        v-model:tableValue="filteredParticipantsForEmcee.rows"
        :tableConfig="filteredParticipantsForEmcee.columns"
      />
    </template>

    <!-- Judge view: swipeable score cards -->
    <template v-else-if="selectedRole === 'Judge' && filteredParticipantsForEmcee.rows.length > 0">
      <MiniScoreMenu
        :cards="filteredParticipantsForJudge"
        :show="showMiniMenu"
        title="Jump to Participant"
        @close="showMiniMenu = false"
      />
      <SwipeableCardsV2 :cards="filteredParticipantsForJudge" />
    </template>

    <!-- Empty state -->
    <div
      v-else-if="selectedRole && selectedGenre"
      class="flex flex-col items-center justify-center py-24 text-center"
    >
      <div class="icon-wrap w-14 h-14 rounded-2xl bg-surface-700 flex items-center justify-center mb-4">
        <i class="pi pi-list text-content-muted text-xl"></i>
      </div>
      <p class="font-heading font-semibold text-content-secondary">No participants found</p>
      <p class="text-muted text-sm mt-1">Select a different event or genre</p>
    </div>

    <!-- No role selected -->
    <div
      v-else-if="!selectedRole"
      class="flex flex-col items-center justify-center py-24 text-center"
    >
      <div class="w-14 h-14 rounded-2xl bg-primary-100 flex items-center justify-center mb-4">
        <i class="pi pi-filter text-primary-400 text-xl"></i>
      </div>
      <p class="font-heading font-semibold text-content-secondary">Select your role to begin</p>
      <p class="text-muted text-sm mt-1">Choose Emcee or Judge in the filter panel above</p>
    </div>

  </div>

  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    :variant="modalVariant"
    @accept="() => { dynamicCallBack() }"
    @close="() => { showModal = false }"
  >
    <p class="text-content-secondary leading-relaxed">{{ modalMessage }}</p>
  </ActionDoneModal>
</template>
