<script setup>
import { ref, onMounted, reactive, watch, computed } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import DynamicTable from '@/components/DynamicTable.vue';
import { checkTableExist, getFileId, getResponseDetails, fetchAllGenres, getGenresByEvent, getVerifiedParticipantsByEvent, addJudges, insertEventInTable, linkGenreToEvent, addParticipantToSystem, getSheetSize, getRegisteredParticipantsByEvent, getEmailTemplate, updateEmailTemplate, removeParticipantGenre, addGenreToParticipant, getUnverifiedParticipantsDB, verifyAndEmailParticipant, verifyAndEmailBatch } from '@/utils/api';
import { filterObject, useDelay } from '@/utils/utils';
import ReusableButton from '@/components/ReusableButton.vue';
import AuditionNumber from './AuditionNumber.vue';
import LoadingOverlay from '@/components/LoadingOverlay.vue';
import CreateParticipantForm from '@/components/CreateParticipantForm.vue';

const fileId = ref('')
const modalTitle = ref("")
const modalMessage = ref("")
const modalVariant = ref("success")
const inputs = ref([""])
const genreOptions = ref(null)
const eventGenres = ref([])
const tableExist = ref(true)
const loading = ref(false)
const onStartLoading = ref(false)
const expandedGenres = ref(new Set())
const expandedPeople = ref(new Set()) // 'notShownUp' | 'registered'

const verifiedFormParticipants = ref([])
const verifiedDbParticipants = ref([])
const unverifiedParticipants = ref([])
const selectedUnverified = ref(new Set())
const verifyingParticipantId = ref(null)
const batchVerifying = ref(false)
const participantsNumBreakdown = ref([])
const totalParticipants = ref(0)

const props = defineProps({
  eventName: String,
  folderID: String,
})

const eventName = ref(props.eventName.split(" ").join("%20"));

const createTable = reactive({ genres: [] })
const paymentRequired = ref(false)

const showModal = ref(false)
const handleAccept = () => { showModal.value = false }

// Walk-in form
const showWalkInForm = ref(false)

// Genre adjustment modal
const showAdjustModal = ref(false)
const adjustSearch = ref('')
const adjustParticipant = ref(null)
const adjustParticipantIds = ref({ participantId: null, eventId: null })
const pendingRemoveItem = ref(null)
const adjustLoading = ref(false)

// Email template popup
const showTemplateModal = ref(false)
const templateLoading = ref(false)
const templateSubject = ref('')
const templateBody = ref('')

const openTemplateModal = async () => {
  templateLoading.value = true
  showTemplateModal.value = true
  const tpl = await getEmailTemplate(props.eventName)
  if (tpl) {
    templateSubject.value = tpl.subject
    templateBody.value = tpl.body
  }
  templateLoading.value = false
}

const saveTemplate = async () => {
  templateLoading.value = true
  const res = await updateEmailTemplate(props.eventName, templateSubject.value, templateBody.value)
  templateLoading.value = false
  if (res && res.ok) {
    showTemplateModal.value = false
    openModal('Template Saved', 'Email template updated successfully.', 'success')
  } else {
    openModal('Error', 'Failed to save email template.', 'error')
  }
}

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
  const grouped = {}
  verifiedDbParticipants.value.forEach(p => {
    if (!grouped[p.participantName]) grouped[p.participantName] = []
    grouped[p.participantName].push(p)
  })
  // Walk-ins always count as registered (they showed up in person).
  // Form participants count only when all genres have audition numbers.
  return Object.values(grouped).filter(rows =>
    rows[0].walkin === true || rows.every(r => r.auditionNumber !== null)
  )
})

// Distinct form-signup participants who are in the DB (have EGP records, not walk-ins)
const totalVerified = computed(() =>
  new Set(
    verifiedDbParticipants.value
      .filter(p => !p.walkin)
      .map(p => p.participantName)
  ).size
)

const totalNotShownUp = computed(() => {
  const grouped = {}
  verifiedDbParticipants.value
    .filter(p => !p.walkin)
    .forEach(p => {
      if (!grouped[p.participantName]) grouped[p.participantName] = []
      grouped[p.participantName].push(p)
    })
  return Object.values(grouped)
    .filter(rows => rows.every(r => r.auditionNumber === null)).length
})

const notShownUpList = computed(() => {
  const grouped = {}
  verifiedDbParticipants.value
    .filter(p => !p.walkin)
    .forEach(p => {
      if (!grouped[p.participantName]) grouped[p.participantName] = []
      grouped[p.participantName].push(p)
    })
  return Object.entries(grouped)
    .filter(([, rows]) => rows.every(r => r.auditionNumber === null))
    .map(([name, rows]) => ({ name, genres: rows.map(r => r.genreName) }))
    .sort((a, b) => a.name.localeCompare(b.name))
})

const registeredList = computed(() => {
  const grouped = {}
  verifiedDbParticipants.value.forEach(p => {
    if (!grouped[p.participantName]) grouped[p.participantName] = []
    grouped[p.participantName].push(p)
  })
  return Object.entries(grouped)
    .filter(([, rows]) => rows[0].walkin === true || rows.every(r => r.auditionNumber !== null))
    .map(([name, rows]) => ({
      name,
      walkin: rows[0].walkin,
      entries: rows
        .filter(r => r.auditionNumber !== null)
        .map(r => ({ genre: r.genreName, auditionNumber: r.auditionNumber }))
        .sort((a, b) => a.genre.localeCompare(b.genre))
    }))
    .sort((a, b) => a.name.localeCompare(b.name))
})

const adjustSearchResults = computed(() => {
  if (!adjustSearch.value.trim()) return []
  const q = adjustSearch.value.toLowerCase()
  const names = new Set(
    verifiedDbParticipants.value
      .filter(p => p.participantName.toLowerCase().includes(q))
      .map(p => p.participantName)
  )
  return [...names]
})

const adjustParticipantGenres = computed(() =>
  verifiedDbParticipants.value.filter(p => p.participantName === adjustParticipant.value)
)

const adjustAvailableGenres = computed(() =>
  eventGenres.value.filter(g =>
    !adjustParticipantGenres.value.some(p => p.genreName === g.genreName)
  )
)

// Persist IDs whenever the selected participant has EGP rows (survives after all genres removed)
watch(adjustParticipantGenres, (genres) => {
  if (genres.length > 0) {
    adjustParticipantIds.value = { participantId: genres[0].participantId, eventId: genres[0].eventId }
  }
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
  await insertEventInTable(props.eventName, paymentRequired.value)
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
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
    unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
    selectedUnverified.value = new Set()
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

const handleWalkInCreated = async () => {
  showWalkInForm.value = false
  verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
}

const closeAdjustModal = () => {
  showAdjustModal.value = false
  adjustSearch.value = ''
  adjustParticipant.value = null
  adjustParticipantIds.value = { participantId: null, eventId: null }
  pendingRemoveItem.value = null
}

const requestRemoveGenre = (item) => {
  if (item.auditionNumber !== null) {
    pendingRemoveItem.value = item
  } else {
    confirmRemoveGenre(item)
  }
}

const confirmRemoveGenre = async (item) => {
  adjustLoading.value = true
  pendingRemoveItem.value = null
  await removeParticipantGenre(item.participantId, item.eventId, item.genreId)
  verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
  adjustLoading.value = false
}

const addGenre = async (genreName) => {
  const { participantId, eventId } = adjustParticipantIds.value
  if (!participantId || !eventId) return
  adjustLoading.value = true
  await addGenreToParticipant(participantId, eventId, genreName)
  verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
  adjustLoading.value = false
}

const emailedCount = computed(() => {
  const names = new Set(
    verifiedDbParticipants.value
      .filter(p => !p.walkin && p.emailSent)
      .map(p => p.participantName)
  )
  return names.size
})

const toggleUnverifiedSelect = (participantId) => {
  if (selectedUnverified.value.has(participantId)) {
    selectedUnverified.value.delete(participantId)
  } else {
    selectedUnverified.value.add(participantId)
  }
  selectedUnverified.value = new Set(selectedUnverified.value)
}

const handleVerifyAndEmail = async (participant) => {
  verifyingParticipantId.value = participant.participantId
  try {
    await verifyAndEmailParticipant(participant.participantId, participant.eventId)
    unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
    selectedUnverified.value.delete(participant.participantId)
    selectedUnverified.value = new Set(selectedUnverified.value)
  } catch (e) {
    openModal('Error', 'Failed to verify participant.', 'error')
  }
  verifyingParticipantId.value = null
}

const handleBatchVerify = async () => {
  if (selectedUnverified.value.size === 0) return
  batchVerifying.value = true
  const list = [...selectedUnverified.value].map(pid => {
    const p = unverifiedParticipants.value.find(x => x.participantId === pid)
    return { participantId: p.participantId, eventId: p.eventId }
  })
  try {
    await verifyAndEmailBatch(list)
    unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
    selectedUnverified.value = new Set()
  } catch (e) {
    openModal('Error', 'Batch verification failed.', 'error')
  }
  batchVerifying.value = false
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
  eventGenres.value = await getGenresByEvent(props.eventName)
  if (tableExist.value) {
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
    verifiedFormParticipants.value = await getVerifiedParticipantsByEvent(eventName.value)
    unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
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
      <div class="flex flex-wrap gap-2 self-start">
        <button
          @click="showWalkInForm = true"
          class="flex items-center gap-2 px-4 py-2 rounded-xl border border-surface-200 bg-white text-sm
                 font-semibold text-surface-700 hover:bg-surface-50 hover:border-surface-300
                 transition-all duration-200"
        >
          <i class="pi pi-user-plus text-sm"></i>
          Add Walk-in
        </button>
        <button
          @click="showAdjustModal = true"
          class="flex items-center gap-2 px-4 py-2 rounded-xl border border-surface-200 bg-white text-sm
                 font-semibold text-surface-700 hover:bg-surface-50 hover:border-surface-300
                 transition-all duration-200"
        >
          <i class="pi pi-sliders-h text-sm"></i>
          Adjust Genres
        </button>
        <button
          @click="openTemplateModal"
          class="flex items-center gap-2 px-4 py-2 rounded-xl border border-surface-200 bg-white text-sm
                 font-semibold text-surface-700 hover:bg-surface-50 hover:border-surface-300
                 transition-all duration-200"
        >
          <i class="pi pi-envelope text-sm"></i>
          Email Template
        </button>
        <button
          @click="refreshParticipant"
          :disabled="loading"
          class="flex items-center gap-2 px-4 py-2 rounded-xl border border-surface-200 bg-white text-sm
                 font-semibold text-surface-700 hover:bg-surface-50 hover:border-surface-300
                 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200"
        >
          <i class="pi text-sm" :class="loading ? 'pi-spinner pi-spin text-primary-500' : 'pi-refresh'"></i>
          {{ loading ? 'Refreshing…' : 'Refresh Participants' }}
        </button>
      </div>
    </div>

    <!-- Audition Number -->
    <div class="card p-4 mb-6">
      <AuditionNumber />
    </div>

    <!-- Stats cards -->
    <div class="grid grid-cols-1 sm:grid-cols-4 gap-4 mb-8">
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
          {{ totalVerified }}
        </p>
        <div class="flex flex-col gap-1 mt-2">
          <span class="text-xs text-surface-500">
            Pending payment: <strong class="text-red-600">{{ unverifiedParticipants.length }}</strong>
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
        <div class="flex gap-4 mt-2">
          <span class="text-xs text-surface-500">Audition numbers assigned</span>
          <span v-if="totalNotShownUp > 0" class="text-xs text-surface-500">
            Not shown up: <strong class="text-amber-600">{{ totalNotShownUp }}</strong>
          </span>
        </div>
      </div>

      <!-- Email Progress -->
      <div class="stat-card p-5">
        <div class="flex items-center gap-3 mb-3">
          <div class="w-9 h-9 rounded-xl bg-primary-50 flex items-center justify-center">
            <i class="pi pi-send text-primary-600 text-sm"></i>
          </div>
          <span class="text-xs font-semibold text-surface-500 uppercase tracking-wider">Email Progress</span>
        </div>
        <p class="text-3xl font-heading font-extrabold text-surface-900">
          {{ emailedCount }} / {{ totalVerified }}
        </p>
        <div class="flex gap-4 mt-2">
          <span class="text-xs text-surface-500">QR emails sent</span>
        </div>
      </div>
    </div>

    <!-- Participant detail panels -->
    <div v-if="verifiedDbParticipants.length > 0" class="space-y-2 mb-8">

      <!-- Not shown up -->
      <div v-if="notShownUpList.length > 0" class="card overflow-hidden">
        <button
          @click="expandedPeople.has('notShownUp') ? expandedPeople.delete('notShownUp') : expandedPeople.add('notShownUp'); expandedPeople = new Set(expandedPeople)"
          class="w-full flex items-center justify-between px-5 py-4 hover:bg-surface-50 transition-colors text-left"
        >
          <div class="flex items-center gap-3">
            <div class="w-7 h-7 rounded-lg bg-amber-50 flex items-center justify-center shrink-0">
              <i class="pi pi-clock text-amber-500 text-xs"></i>
            </div>
            <span class="font-heading font-bold text-surface-800">Not Shown Up</span>
            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-700">
              {{ notShownUpList.length }}
            </span>
          </div>
          <i class="pi pi-chevron-down text-surface-400 text-xs transition-transform duration-200"
             :class="{ 'rotate-180': expandedPeople.has('notShownUp') }"></i>
        </button>
        <div v-if="expandedPeople.has('notShownUp')" class="px-5 pb-4 border-t border-surface-100 pt-4">
          <div class="flex flex-wrap gap-2">
            <div
              v-for="p in notShownUpList"
              :key="p.name"
              class="flex items-center gap-2 px-3 py-2 rounded-xl bg-amber-50 border border-amber-100"
            >
              <span class="text-sm font-semibold text-surface-800">{{ p.name }}</span>
              <div class="flex gap-1">
                <span
                  v-for="g in p.genres"
                  :key="g"
                  class="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-white border border-amber-200 text-surface-600"
                >{{ g }}</span>
              </div>
              <span
                v-if="verifiedDbParticipants.find(v => v.participantName === p.name)?.emailSent"
                class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-emerald-50 text-emerald-600 border border-emerald-200"
              >
                <i class="pi pi-check text-xs"></i> Email Sent
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Unverified (payment pending — DB-driven) -->
      <div v-if="unverifiedParticipants.length > 0" class="card overflow-hidden">
        <button
          @click="expandedPeople.has('unverified') ? expandedPeople.delete('unverified') : expandedPeople.add('unverified'); expandedPeople = new Set(expandedPeople)"
          class="w-full flex items-center justify-between px-5 py-4 hover:bg-surface-50 transition-colors text-left"
        >
          <div class="flex items-center gap-3">
            <div class="w-7 h-7 rounded-lg bg-red-50 flex items-center justify-center shrink-0">
              <i class="pi pi-exclamation-circle text-red-500 text-xs"></i>
            </div>
            <span class="font-heading font-bold text-surface-800">Awaiting Payment Verification</span>
            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-700">
              {{ unverifiedParticipants.length }}
            </span>
          </div>
          <i class="pi pi-chevron-down text-surface-400 text-xs transition-transform duration-200"
             :class="{ 'rotate-180': expandedPeople.has('unverified') }"></i>
        </button>
        <div v-if="expandedPeople.has('unverified')" class="px-5 pb-5 border-t border-surface-100 pt-4">
          <!-- Batch action bar -->
          <div class="flex items-center justify-between mb-3">
            <p class="text-xs text-surface-400 flex items-center gap-1.5">
              <i class="pi pi-info-circle"></i>
              Select participants and click "Send Batch", or verify individually.
            </p>
            <button
              v-if="selectedUnverified.size > 0"
              @click="handleBatchVerify"
              :disabled="batchVerifying"
              class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-primary-500 text-white text-xs font-semibold
                     hover:bg-primary-600 active:scale-95 disabled:opacity-50 transition-all"
            >
              <i class="pi text-xs" :class="batchVerifying ? 'pi-spinner pi-spin' : 'pi-send'"></i>
              {{ batchVerifying ? 'Sending…' : `Send Batch (${selectedUnverified.size})` }}
            </button>
          </div>
          <div class="space-y-2">
            <div
              v-for="p in unverifiedParticipants"
              :key="p.participantId"
              class="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-red-50/60 border transition-colors"
              :class="selectedUnverified.has(p.participantId) ? 'border-primary-300 bg-primary-50/40' : 'border-red-100'"
            >
              <!-- Checkbox -->
              <input
                type="checkbox"
                :checked="selectedUnverified.has(p.participantId)"
                @change="toggleUnverifiedSelect(p.participantId)"
                class="w-4 h-4 rounded accent-primary-600 shrink-0"
              />
              <span class="text-sm font-semibold text-surface-800 min-w-0 shrink-0">{{ p.name }}</span>
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="g in p.genres"
                  :key="g"
                  class="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-white border border-red-200 text-surface-600"
                >{{ g }}</span>
              </div>
              <div class="flex items-center gap-2 ml-auto shrink-0">
                <a
                  v-if="p.screenshotUrl"
                  :href="p.screenshotUrl"
                  target="_blank"
                  class="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-surface-100 text-surface-600 text-xs font-medium
                         hover:bg-surface-200 border border-surface-200 transition-colors"
                >
                  <i class="pi pi-image text-xs"></i>
                  View Receipt
                </a>
                <button
                  @click="handleVerifyAndEmail(p)"
                  :disabled="verifyingParticipantId === p.participantId"
                  class="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-emerald-500 text-white text-xs font-semibold
                         hover:bg-emerald-600 active:scale-95 disabled:opacity-50 transition-all"
                >
                  <i class="pi text-xs" :class="verifyingParticipantId === p.participantId ? 'pi-spinner pi-spin' : 'pi-check'"></i>
                  {{ verifyingParticipantId === p.participantId ? 'Sending…' : 'Verify & Email' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Registered -->
      <div v-if="registeredList.length > 0" class="card overflow-hidden">
        <button
          @click="expandedPeople.has('registered') ? expandedPeople.delete('registered') : expandedPeople.add('registered'); expandedPeople = new Set(expandedPeople)"
          class="w-full flex items-center justify-between px-5 py-4 hover:bg-surface-50 transition-colors text-left"
        >
          <div class="flex items-center gap-3">
            <div class="w-7 h-7 rounded-lg bg-emerald-50 flex items-center justify-center shrink-0">
              <i class="pi pi-check-circle text-emerald-500 text-xs"></i>
            </div>
            <span class="font-heading font-bold text-surface-800">Registered</span>
            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-700">
              {{ registeredList.length }}
            </span>
          </div>
          <i class="pi pi-chevron-down text-surface-400 text-xs transition-transform duration-200"
             :class="{ 'rotate-180': expandedPeople.has('registered') }"></i>
        </button>
        <div v-if="expandedPeople.has('registered')" class="px-5 pb-4 border-t border-surface-100 pt-4">
          <div class="space-y-2">
            <div
              v-for="p in registeredList"
              :key="p.name"
              class="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-surface-50 border border-surface-200"
            >
              <div class="flex items-center gap-2 min-w-0">
                <span class="text-sm font-semibold text-surface-800 truncate">{{ p.name }}</span>
                <span
                  v-if="p.walkin"
                  class="shrink-0 inline-flex px-1.5 py-0.5 rounded text-xs font-medium bg-surface-100 text-surface-500 border border-surface-200"
                >walk-in</span>
                <span
                  v-else-if="p.entries.length > 0 && verifiedDbParticipants.find(v => v.participantName === p.name)?.emailSent"
                  class="shrink-0 inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-emerald-50 text-emerald-600 border border-emerald-200"
                >
                  <i class="pi pi-check text-xs"></i> Email Sent
                </span>
                <span
                  v-else-if="!p.walkin"
                  class="shrink-0 inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-amber-50 text-amber-600 border border-amber-200"
                >
                  <i class="pi pi-clock text-xs"></i> Email Pending
                </span>
              </div>
              <div class="flex flex-wrap gap-1.5 ml-auto">
                <span
                  v-for="e in p.entries"
                  :key="e.genre"
                  class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-white border border-primary-200 text-sm"
                >
                  <span class="text-surface-500 capitalize text-xs">{{ e.genre }}</span>
                  <span class="font-heading font-extrabold text-primary-600">#{{ e.auditionNumber }}</span>
                </span>
              </div>
            </div>
          </div>
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

      <!-- Payment required toggle -->
      <div class="mb-6">
        <label
          class="flex items-center gap-3 px-4 py-3 rounded-xl border cursor-pointer transition-all duration-150 w-fit"
          :class="paymentRequired
            ? 'bg-amber-50 border-amber-300 text-amber-700'
            : 'bg-white border-surface-200 text-surface-700 hover:border-surface-300'"
        >
          <input type="checkbox" v-model="paymentRequired" class="w-4 h-4 rounded accent-amber-500" />
          <div>
            <span class="text-sm font-semibold">Payment Required</span>
            <p class="text-xs mt-0.5" :class="paymentRequired ? 'text-amber-600' : 'text-surface-400'">
              {{ paymentRequired ? 'Participants will be placed in an unverified queue until you manually verify payment in-app.' : 'All participants will be auto-verified and emailed immediately on import.' }}
            </p>
          </div>
        </label>
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

  <!-- Walk-in Form -->
  <CreateParticipantForm
    :show="showWalkInForm"
    :event="props.eventName"
    :eventGenres="eventGenres"
    title="Add Walk-in Participant"
    @createNewEntry="handleWalkInCreated"
    @close="showWalkInForm = false"
  />

  <!-- Genre Adjustment Modal -->
  <Teleport to="body">
    <div
      v-if="showAdjustModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      @click.self="closeAdjustModal"
    >
      <div class="absolute inset-0 bg-black/40 backdrop-blur-sm"></div>
      <div class="relative bg-white rounded-2xl shadow-2xl w-full max-w-lg flex flex-col" style="max-height: 90vh;">
        <!-- Header -->
        <div class="flex items-center justify-between px-6 py-4 border-b border-surface-100">
          <div class="flex items-center gap-3">
            <div class="w-8 h-8 rounded-xl bg-primary-50 flex items-center justify-center">
              <i class="pi pi-sliders-h text-primary-600 text-sm"></i>
            </div>
            <div>
              <h2 class="font-heading font-bold text-surface-900 text-base">Adjust Genres</h2>
              <p class="text-xs text-surface-500">{{ props.eventName }}</p>
            </div>
          </div>
          <button
            @click="closeAdjustModal"
            class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-surface-100 text-surface-400 hover:text-surface-600 transition-colors"
          >
            <i class="pi pi-times text-sm"></i>
          </button>
        </div>

        <!-- Body -->
        <div class="flex-1 overflow-y-auto px-6 py-5 space-y-5">
          <!-- Search -->
          <div>
            <label class="block text-sm font-semibold text-surface-700 mb-1.5">Search Participant</label>
            <input
              v-model="adjustSearch"
              type="text"
              placeholder="Type a name…"
              class="w-full px-4 py-2.5 rounded-xl border border-surface-200 text-sm text-surface-900
                     focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors"
            />
          </div>

          <!-- Search results (only shown when no participant selected) -->
          <div v-if="adjustSearchResults.length > 0 && !adjustParticipant" class="flex flex-wrap gap-2">
            <button
              v-for="name in adjustSearchResults"
              :key="name"
              @click="adjustParticipant = name"
              class="px-3 py-1.5 rounded-full text-sm font-medium bg-surface-100 text-surface-700
                     hover:bg-primary-50 hover:text-primary-700 border border-surface-200 transition-colors"
            >
              {{ name }}
            </button>
          </div>

          <!-- Selected participant view -->
          <div v-if="adjustParticipant">
            <div class="flex items-center gap-2 mb-4">
              <span class="font-heading font-bold text-surface-900">{{ adjustParticipant }}</span>
              <button
                @click="adjustParticipant = null; adjustSearch = ''"
                class="text-surface-400 hover:text-surface-600 transition-colors"
              >
                <i class="pi pi-times-circle text-sm"></i>
              </button>
            </div>

            <!-- Current genres -->
            <div class="mb-5">
              <p class="text-xs font-semibold text-surface-500 uppercase tracking-wider mb-2">Current Genres</p>
              <div v-if="adjustParticipantGenres.length === 0" class="text-sm text-surface-400">No genres assigned</div>
              <div class="space-y-2">
                <div
                  v-for="p in adjustParticipantGenres"
                  :key="p.genreId"
                  class="flex items-center justify-between px-3 py-2 rounded-xl bg-surface-50 border border-surface-200"
                >
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-medium text-surface-700">{{ p.genreName }}</span>
                    <span
                      v-if="p.auditionNumber !== null"
                      class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-amber-100 text-amber-700"
                    >
                      #{{ p.auditionNumber }}
                    </span>
                  </div>
                  <!-- Confirm state -->
                  <div v-if="pendingRemoveItem === p" class="flex items-center gap-2">
                    <span class="text-xs text-amber-600">Audition #{{ p.auditionNumber }} will be withdrawn</span>
                    <button
                      @click="confirmRemoveGenre(p)"
                      class="px-2.5 py-1 rounded-lg bg-red-500 text-white text-xs font-semibold hover:bg-red-600 transition-colors"
                    >
                      Confirm
                    </button>
                    <button
                      @click="pendingRemoveItem = null"
                      class="px-2.5 py-1 rounded-lg border border-surface-200 text-xs font-semibold text-surface-600 hover:bg-surface-100 transition-colors"
                    >
                      Cancel
                    </button>
                  </div>
                  <button
                    v-else
                    @click="requestRemoveGenre(p)"
                    :disabled="adjustLoading"
                    class="px-2.5 py-1 rounded-lg bg-red-50 text-red-600 text-xs font-semibold
                           hover:bg-red-100 border border-red-100 disabled:opacity-50 transition-colors"
                  >
                    Remove
                  </button>
                </div>
              </div>
            </div>

            <!-- Add genres -->
            <div>
              <p class="text-xs font-semibold text-surface-500 uppercase tracking-wider mb-2">Add Genre</p>
              <div v-if="adjustAvailableGenres.length === 0" class="text-sm text-surface-400">All genres already assigned</div>
              <div class="space-y-2">
                <div
                  v-for="g in adjustAvailableGenres"
                  :key="g.genreName"
                  class="flex items-center justify-between px-3 py-2 rounded-xl bg-surface-50 border border-surface-200"
                >
                  <span class="text-sm font-medium text-surface-700">{{ g.genreName }}</span>
                  <button
                    @click="addGenre(g.genreName)"
                    :disabled="adjustLoading"
                    class="flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-primary-500 text-white text-xs font-semibold
                           hover:bg-primary-600 active:scale-95 disabled:opacity-50 transition-all"
                  >
                    <i class="pi pi-plus text-xs"></i>
                    Add
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- Email Template Modal -->
  <Teleport to="body">
    <div
      v-if="showTemplateModal"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
      @click.self="showTemplateModal = false"
    >
      <div class="absolute inset-0 bg-black/40 backdrop-blur-sm"></div>
      <div class="relative bg-white rounded-2xl shadow-2xl w-full max-w-2xl flex flex-col" style="max-height: 90vh;">
        <!-- Header -->
        <div class="flex items-center justify-between px-6 py-4 border-b border-surface-100">
          <div class="flex items-center gap-3">
            <div class="w-8 h-8 rounded-xl bg-primary-50 flex items-center justify-center">
              <i class="pi pi-envelope text-primary-600 text-sm"></i>
            </div>
            <div>
              <h2 class="font-heading font-bold text-surface-900 text-base">Email Template</h2>
              <p class="text-xs text-surface-500">{{ props.eventName }}</p>
            </div>
          </div>
          <button
            @click="showTemplateModal = false"
            class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-surface-100 text-surface-400 hover:text-surface-600 transition-colors"
          >
            <i class="pi pi-times text-sm"></i>
          </button>
        </div>

        <!-- Body -->
        <div v-if="templateLoading" class="flex-1 flex items-center justify-center py-12">
          <i class="pi pi-spinner pi-spin text-primary-500 text-2xl"></i>
        </div>
        <div v-else class="flex-1 overflow-y-auto px-6 py-5 space-y-5">
          <div>
            <label class="block text-sm font-semibold text-surface-700 mb-1.5">Subject</label>
            <input
              v-model="templateSubject"
              type="text"
              class="w-full px-4 py-2.5 rounded-xl border border-surface-200 text-sm text-surface-900
                     focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors"
              placeholder="Email subject…"
            />
          </div>
          <div>
            <label class="block text-sm font-semibold text-surface-700 mb-1.5">
              Body
              <span class="font-normal text-surface-400 ml-1">— use <code class="font-source text-xs bg-surface-100 px-1 rounded">{name}</code> for participant name</span>
            </label>
            <textarea
              v-model="templateBody"
              rows="10"
              class="w-full px-4 py-2.5 rounded-xl border border-surface-200 text-sm text-surface-900 font-source
                     focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors resize-none"
              placeholder="Email body…"
            ></textarea>
          </div>
        </div>

        <!-- Footer -->
        <div class="flex items-center justify-end gap-3 px-6 py-4 border-t border-surface-100">
          <button
            @click="showTemplateModal = false"
            class="px-4 py-2 rounded-xl border border-surface-200 text-sm font-semibold text-surface-700
                   hover:bg-surface-50 transition-colors"
          >
            Cancel
          </button>
          <button
            @click="saveTemplate"
            :disabled="templateLoading"
            class="flex items-center gap-2 px-4 py-2 rounded-xl bg-primary-500 text-white text-sm font-semibold
                   hover:bg-primary-600 active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
          >
            <i class="pi pi-check text-xs"></i>
            Save Template
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
