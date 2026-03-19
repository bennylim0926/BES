<script setup>
import { ref, onMounted, reactive, watch, computed } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import DynamicTable from '@/components/DynamicTable.vue';
import { checkTableExist, getFileId, getResponseDetails, fetchAllGenres, getVerifiedParticipantsByEvent, addJudges, insertPaymenColumnInSheet, insertEventInTable, linkGenreToEvent, addParticipantToSystem, getSheetSize, getRegisteredParticipantsByEvent } from '@/utils/api';
import { filterObject, useDelay } from '@/utils/utils';
import ReusableButton from '@/components/ReusableButton.vue';
import AuditionNumber from './AuditionNumber.vue';
import LoadingOverlay from '@/components/LoadingOverlay.vue';

const fileId = ref('')
const modalTitle = ref("")
const modalMessage = ref("")
const modalVariant = ref("success")
const inputs = ref([""])
const genreOptions = ref(null)
const tableExist = ref(true)
const loading = ref(false)
const onStartLoading = ref(false)
const expandedGenres = ref(new Set())

const verifiedFormParticipants = ref([])
const verifiedDbParticipants = ref([])
const participantsNumBreakdown = ref([])
const totalParticipants = ref(0)

const props = defineProps({
  eventName: String,
  folderID: String,
})

const eventName = ref(props.eventName.split(" ").join("%20"));

const createTable = reactive({ genres: [] })

const showModal = ref(false)
const handleAccept = () => { showModal.value = false }

const openModal = (title, message, variant = 'success') => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = variant
  showModal.value = true
}

const getTitle = (statusCode) => {
  if (statusCode >= 200 && statusCode <= 299) {
    modalTitle.value = "Success"
    modalVariant.value = "success"
  } else {
    modalTitle.value = "Failed"
    modalVariant.value = "error"
  }
}

const filteredBreakdown = computed(() => {
  return filterObject(participantsNumBreakdown.value, value => value > 0)
})

const genreCounts = computed(() => {
  const counts = {}
  verifiedDbParticipants.value.forEach(p => {
    let key = p.genreName.toLowerCase()
    if (key.includes('smoke')) key = 'smoke'
    else key = key.replace(/\s+/g, '')
    counts[key] = (counts[key] || 0) + 1
  })
  return counts
})

const totalWalkIn = computed(() => {
  const uniqueParticipants = [
    ...new Map(verifiedDbParticipants.value.map(p => [p.participantName, p])).values()
  ]
  return uniqueParticipants.filter(p => p.walkin == true).length
})

const totalDbRegistered = computed(() => {
  const uniqueParticipants = [
    ...new Map(verifiedDbParticipants.value.map(p => [p.participantName, p])).values()
  ]
  return uniqueParticipants.filter(p => p.auditionNumber !== null)
})

function normalizeGenreName(name) {
  const normalized = name.trim().toLowerCase().replace(/\s+/g, '');
  if (normalized.includes('7tosmoke')) return 'smoke';
  return normalized
}

const getUnregistered = (genre) => {
  const participants = verifiedDbParticipants.value.map(p => ({
    ...p,
    genreName: normalizeGenreName(p.genreName)
  }))
  return {
    "registered": participants
      .filter(p => p.genreName === genre && p.auditionNumber !== null && p.walkin === false)
      .sort((a, b) => a.auditionNumber - b.auditionNumber),
    "unregistered": participants.filter(p => p.genreName === genre && p.auditionNumber === null && p.walkin === false)
  }
}

const completeBreakdown = computed(() => {
  const genreStats = {};
  for (const item of verifiedDbParticipants.value) {
    const genre = normalizeGenreName(item.genreName);
    if (!genreStats[genre]) {
      genreStats[genre] = { registered: 0, unregistered: 0 };
    }
    if (item.auditionNumber !== null) {
      genreStats[genre].registered++;
    } else {
      genreStats[genre].unregistered++;
    }
  }
  return Object.entries(genreCounts.value).map(([genre, total]) => {
    const stats = genreStats[genre] || { registered: 0, unregistered: 0 };
    return { genre, total, registered: stats.registered, unregistered: stats.unregistered };
  });
})

const toggleGenre = (genre) => {
  if (expandedGenres.value.has(genre)) {
    expandedGenres.value.delete(genre)
  } else {
    expandedGenres.value.add(genre)
  }
  // trigger reactivity
  expandedGenres.value = new Set(expandedGenres.value)
}

const onSubmit = async () => {
  if (loading.value) return
  if (createTable.genres.length == 0) {
    openModal("Missing Genres", "Please select at least one genre/category.", "warning")
    return
  }
  loading.value = true
  await addJudges(inputs.value)
  await insertPaymenColumnInSheet(fileId.value)
  await insertEventInTable(props.eventName)
  const resp = await linkGenreToEvent(props.eventName, createTable.genres)
  resp.json().then(result => {
    loading.value = false
    getTitle(resp.status)
    modalMessage.value = result
    showModal.value = true
    tableExist.value = true
  })
}

const refreshParticipant = async () => {
  loading.value = true
  const createEventResponse = await addParticipantToSystem(fileId.value, props.eventName)
  if (createEventResponse.ok) {
    verifiedFormParticipants.value = await getVerifiedParticipantsByEvent(eventName.value)
    createEventResponse.json().then(result => {
      getTitle(createEventResponse.status)
      modalMessage.value = result
      showModal.value = true
    })
  } else if (createEventResponse.status == 404) {
    createEventResponse.json().then(result => {
      openModal("Not Found", result, "error")
    })
  }
  loading.value = false
}

watch(
  fileId,
  async () => {
    if (fileId.value !== null) {
      participantsNumBreakdown.value = await getResponseDetails(fileId.value)
      totalParticipants.value = await getSheetSize(fileId.value)
    }
  }
)

onMounted(async () => {
  onStartLoading.value = true
  tableExist.value = checkTableExist(eventName, tableExist)
  fileId.value = await getFileId(props.folderID)
  genreOptions.value = await fetchAllGenres()
  if (tableExist.value) {
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
    verifiedFormParticipants.value = await getVerifiedParticipantsByEvent(eventName.value)
  }
  await useDelay().wait(2500)
  onStartLoading.value = false
})
</script>

<template>
  <div class="page-container">

    <!-- Page header -->
    <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-8">
      <div>
        <h1 class="page-title">{{ props.eventName }}</h1>
        <p class="text-muted mt-1">Event overview and participant registration status</p>
      </div>
      <button
        @click="refreshParticipant"
        :disabled="loading"
        class="flex items-center gap-2 px-4 py-2 rounded-xl border border-surface-200 bg-white text-sm
               font-semibold text-surface-700 hover:bg-surface-50 hover:border-surface-300
               disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 self-start"
      >
        <i class="pi text-sm" :class="loading ? 'pi-spinner pi-spin text-primary-500' : 'pi-refresh'"></i>
        {{ loading ? 'Refreshing…' : 'Refresh Participants' }}
      </button>
    </div>

    <!-- Audition Number -->
    <div class="card p-4 mb-6">
      <AuditionNumber />
    </div>

    <!-- Stats cards -->
    <div class="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
      <!-- Total -->
      <div class="stat-card p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-9 h-9 rounded-xl bg-primary-50 flex items-center justify-center">
            <i class="pi pi-users text-primary-600 text-sm"></i>
          </div>
          <span class="text-xs font-semibold text-surface-500 uppercase tracking-wider">Total Participants</span>
        </div>
        <p class="text-3xl font-heading font-extrabold text-surface-900">
          {{ totalParticipants + totalWalkIn }}
        </p>
        <div class="flex gap-4 mt-2">
          <span class="text-xs text-surface-500">Form: <strong class="text-surface-700">{{ totalParticipants }}</strong></span>
          <span class="text-xs text-surface-500">Walk-in: <strong class="text-surface-700">{{ totalWalkIn }}</strong></span>
        </div>
      </div>

      <!-- Verified -->
      <div class="stat-card p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-9 h-9 rounded-xl bg-emerald-50 flex items-center justify-center">
            <i class="pi pi-check-circle text-emerald-500 text-sm"></i>
          </div>
          <span class="text-xs font-semibold text-surface-500 uppercase tracking-wider">Verification</span>
        </div>
        <p class="text-3xl font-heading font-extrabold text-surface-900">
          {{ verifiedFormParticipants.length - totalWalkIn }}
        </p>
        <div class="flex gap-4 mt-2">
          <span class="text-xs text-surface-500">
            Unverified: <strong class="text-red-600">{{ totalParticipants - (verifiedFormParticipants.length - totalWalkIn) }}</strong>
          </span>
        </div>
      </div>

      <!-- Registered -->
      <div class="stat-card p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-9 h-9 rounded-xl bg-surface-100 flex items-center justify-center">
            <i class="pi pi-id-card text-surface-600 text-sm"></i>
          </div>
          <span class="text-xs font-semibold text-surface-500 uppercase tracking-wider">Registered</span>
        </div>
        <p class="text-3xl font-heading font-extrabold text-surface-900">
          {{ totalDbRegistered.length }}
        </p>
        <div class="mt-2">
          <span class="text-xs text-surface-500">Audition numbers assigned</span>
        </div>
      </div>
    </div>

    <!-- Genre breakdown -->
    <div v-if="completeBreakdown.length > 0" class="mb-8">
      <h2 class="font-heading font-bold text-surface-800 text-lg mb-4">Genre Breakdown</h2>
      <div class="space-y-2">
        <div
          v-for="genre in completeBreakdown"
          :key="genre.genre"
          class="card overflow-hidden"
        >
          <!-- Genre header (always visible) -->
          <button
            @click="toggleGenre(genre.genre)"
            class="w-full flex items-center justify-between px-5 py-4 hover:bg-surface-50 transition-colors text-left"
          >
            <div class="flex items-center gap-4">
              <span class="font-heading font-bold text-surface-900 capitalize">{{ genre.genre }}</span>
              <div class="flex items-center gap-3">
                <span class="badge-neutral text-xs">Total: {{ genre.total }}</span>
                <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-700">
                  Reg: {{ genre.registered }}
                </span>
                <span
                  v-if="genre.unregistered > 0"
                  class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-700"
                >
                  Unreg: {{ genre.unregistered }}
                </span>
              </div>
            </div>
            <i
              class="pi pi-chevron-down text-surface-400 text-xs transition-transform duration-200"
              :class="{ 'rotate-180': expandedGenres.has(genre.genre) }"
            ></i>
          </button>

          <!-- Expanded: unregistered list -->
          <div
            v-if="expandedGenres.has(genre.genre)"
            class="px-5 pb-4 border-t border-surface-100"
          >
            <div class="pt-4">
              <template v-if="getUnregistered(genre.genre).unregistered.length > 0">
                <p class="text-xs font-semibold text-surface-500 uppercase tracking-wider mb-2">
                  Unregistered Participants
                </p>
                <div class="flex flex-wrap gap-2">
                  <span
                    v-for="p in getUnregistered(genre.genre).unregistered"
                    :key="p.participantName"
                    class="inline-flex items-center px-2.5 py-1 rounded-lg bg-red-50 text-red-700 text-xs font-medium border border-red-100"
                  >
                    {{ p.participantName }}
                  </span>
                </div>
              </template>
              <template v-else>
                <div class="flex items-center gap-2 text-emerald-600 text-sm">
                  <i class="pi pi-check-circle"></i>
                  <span>All verified participants have registered</span>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Setup section (when no table exists) -->
    <div v-if="!tableExist" class="card p-6">
      <div class="flex items-center gap-3 mb-6">
        <div class="w-9 h-9 rounded-xl bg-amber-50 flex items-center justify-center">
          <i class="pi pi-exclamation-triangle text-amber-500 text-sm"></i>
        </div>
        <div>
          <h2 class="font-heading font-bold text-surface-800">Event Setup Required</h2>
          <p class="text-muted text-sm">No record found for this event. Select genres to initialise.</p>
        </div>
      </div>

      <div class="mb-6">
        <label class="block text-sm font-semibold text-surface-700 mb-3">
          Genres / Categories
        </label>
        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
          <label
            v-for="g in genreOptions"
            :key="g.genreName"
            class="flex items-center gap-2.5 px-4 py-3 rounded-xl border cursor-pointer transition-all duration-150"
            :class="createTable.genres.includes(g.genreName)
              ? 'bg-primary-50 border-primary-300 text-primary-700'
              : 'bg-white border-surface-200 text-surface-700 hover:border-surface-300'"
          >
            <input
              type="checkbox"
              :id="g.genreName"
              :value="g.genreName"
              v-model="createTable.genres"
              class="w-4 h-4 rounded accent-primary-600"
            />
            <span class="text-sm font-medium">{{ g.genreName }}</span>
          </label>
        </div>
      </div>

      <div class="flex justify-end">
        <ReusableButton variant="primary" @onClick="onSubmit" :disabled="loading">
          <template #default>
            <i class="pi text-xs" :class="loading ? 'pi-spinner pi-spin' : 'pi-database'"></i>
            {{ loading ? 'Setting up…' : 'Initialise Event' }}
          </template>
        </ReusableButton>
      </div>
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

  <LoadingOverlay v-if="onStartLoading">Loading event data…</LoadingOverlay>
</template>
