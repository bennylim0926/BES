<script setup>
import DynamicTable from '@/components/DynamicTable.vue';
import { onMounted, ref, watch, computed } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue'
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import ReusableButton from '@/components/ReusableButton.vue';
import { checkAuthStatus, getActiveEvent } from '@/utils/auth';
import CreateParticipantForm from '@/components/CreateParticipantForm.vue';
const selectedEvent = ref(getActiveEvent()?.name || localStorage.getItem("selectedEvent") || "")
const selectedGenre = ref(localStorage.getItem("selectedGenre") || "All")
const participants = ref([])
const allJudges = ref([])
const showCreateNewEntry = ref(false)

const modalTitle = ref("")
const modalMessage = ref("")
const modalVariant = ref("success")
const showModal = ref(false)
const openModal = (title, message, variant = 'success') => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = variant
  showModal.value = true
}
const handleAccept = () => {
  showModal.value = false
}

const uniqueGenres = computed(() => {
  const genres = participants.value.map(p => p.genreName);
  return [...new Set(genres)].sort();
})

const genreOptions = computed(() => ['All', ...uniqueGenres.value])

const filteredParticipants = computed({
  get() {
    if (selectedGenre.value === "All") return participants.value;
    return participants.value.filter(p => p.genreName === selectedGenre.value);
  },
  set(updatedSubset) {
    if (!Array.isArray(participants.value)) return;
    const byId = new Map(updatedSubset.map(r => [r.rowId, r]));
    participants.value = participants.value.map(org => {
      const updated = byId.get(org.rowId)
      return updated ? { ...org, ...updated } : org
    })
  }
});

const updateParticipantJudge = async () => {
  try {
    const updateResponse = await fetch("/api/v1/event/participants-judge/", {
      method: 'POST',
      credentials: "include",
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ updatedList: participants.value })
    })
    await updateResponse.json()
    openModal("Judges Updated", "Judge assignments have been saved successfully.", "success")
  } catch {
    openModal("Update Failed", "Unable to save changes. Please try again.", "error")
  }
}

watch(selectedEvent, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedEvent", newVal);
    await fetchAllParticipantInEvent(newVal)
  }
});

watch(selectedGenre, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedGenre", newVal);
  }
}, { immediate: true });

const fetchAllParticipantInEvent = async (eventName) => {
  try {
    const res = await fetch(`/api/v1/event/participants/${eventName}`, { credentials: "include" })
    if (!res.ok) throw new Error('Failed to fetch event data')
    const result = await res.json()
    participants.value = result.map((r, i) => ({ ...r, rowId: r.rowId ?? i }))
  } catch (err) {
    console.log(err)
  }
}

const fetchAllJudges = async () => {
  try {
    const res = await fetch('/api/v1/event/judges', { credentials: "include" })
    if (!res.ok) throw new Error('Failed to fetch judges')
    const result = await res.json()
    allJudges.value = Object.values(result).map(item => item.judgeName)
  } catch (err) {
    console.log(err)
  }
}

onMounted(async () => {
  const ok = await checkAuthStatus(["ROLE_ADMIN", "ROLE_ORGANISER"])
  if (!ok) return
  if (selectedEvent.value) {
    await fetchAllParticipantInEvent(selectedEvent.value)
  }
  fetchAllJudges()
})
</script>

<template>
  <div class="page-container">

    <!-- Page header -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-8">
      <div>
        <h1 class="page-title">Participants</h1>
        <p class="text-muted mt-1">Assign judges and manage participant entries</p>
      </div>
      <ReusableButton
        variant="primary"
        size="sm"
        @onClick="showCreateNewEntry = true"
      >
        <template #default>
          <i class="pi pi-plus text-xs"></i>
          Add Participant
        </template>
      </ReusableButton>
    </div>

    <!-- Filter bar -->
    <div class="card p-5 mb-6">
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div class="flex flex-col gap-1">
          <span class="text-xs font-semibold text-surface-500 uppercase tracking-wide">Event</span>
          <span class="badge-neutral text-sm px-3 py-1.5 self-start">{{ selectedEvent }}</span>
        </div>
        <div>
          <ReusableDropdown
            v-model="selectedGenre"
            labelId="Genre"
            :options="genreOptions"
            placeholder="All genres"
          />
        </div>
      </div>

      <!-- Participant count -->
      <div class="flex items-center gap-2 mt-4 pt-4 border-t border-surface-100">
        <i class="pi pi-users text-surface-400 text-sm"></i>
        <span class="text-sm text-surface-600">
          Showing <strong class="text-surface-900">{{ filteredParticipants.length }}</strong>
          of <strong class="text-surface-900">{{ participants.length }}</strong> participants
        </span>
      </div>
    </div>

    <!-- Table -->
    <div class="mb-6">
      <DynamicTable
        v-if="participants.length > 0"
        v-model:tableValue="filteredParticipants"
        :tableConfig="[
          { key: 'eventName',       label: 'Event',  type: 'text',   readonly: true },
          { key: 'participantName', label: 'Name',   type: 'text',   readonly: true },
          { key: 'genreName',       label: 'Genre',  type: 'text',   readonly: true },
          { key: 'judgeName',       label: 'Judge',  type: 'select', options: ['', ...(allJudges || [])] }
        ]"
      />

      <!-- Empty state -->
      <div
        v-if="participants.length === 0 && selectedEvent"
        class="flex flex-col items-center justify-center py-20 text-center"
      >
        <div class="w-14 h-14 rounded-2xl bg-surface-100 flex items-center justify-center mb-4">
          <i class="pi pi-users text-surface-400 text-xl"></i>
        </div>
        <p class="font-heading font-semibold text-surface-700">No participants found</p>
        <p class="text-muted text-sm mt-1">Select a different event or add a participant</p>
      </div>
    </div>

    <!-- Save action -->
    <div class="flex justify-end">
      <ReusableButton variant="primary" @onClick="updateParticipantJudge">
        <template #default>
          <i class="pi pi-check text-xs"></i>
          Save Judge Assignments
        </template>
      </ReusableButton>
    </div>

  </div>

  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    :variant="modalVariant"
    @accept="handleAccept"
    @close="handleAccept"
  >
    <p class="text-surface-600 leading-relaxed">{{ modalMessage }}</p>
  </ActionDoneModal>

  <CreateParticipantForm
    :event="selectedEvent"
    :show="showCreateNewEntry"
    title="New participant entry"
    @createNewEntry="showCreateNewEntry = false"
    @close="showCreateNewEntry = false"
  />
</template>
