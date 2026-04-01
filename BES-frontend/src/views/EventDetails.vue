<script setup>
import { ref, onMounted, reactive, watch, computed } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import DynamicTable from '@/components/DynamicTable.vue';
import { checkTableExist, getFileId, getResponseDetails, fetchAllGenres, getGenresByEvent, getVerifiedParticipantsByEvent, addJudges, insertEventInTable, linkGenreToEvent, addParticipantToSystem, getSheetSize, getRegisteredParticipantsByEvent, getEmailTemplate, updateEmailTemplate, resetEmailTemplate, removeParticipantGenre, addGenreToParticipant, getUnverifiedParticipantsDB, verifyAndEmailParticipant, verifyAndEmailBatch, updateEventGenreFormat } from '@/utils/api';
import { filterObject, useDelay } from '@/utils/utils';
import ReusableButton from '@/components/ReusableButton.vue';
import AuditionNumber from './AuditionNumber.vue';
import LoadingOverlay from '@/components/LoadingOverlay.vue';
import CreateParticipantForm from '@/components/CreateParticipantForm.vue'
import ScoringCriteriaModal from '@/components/ScoringCriteriaModal.vue';

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

const createTable = reactive({ genres: [], genreFormats: {} })

watch(() => [...createTable.genres], (newGenres) => {
  Object.keys(createTable.genreFormats).forEach(g => {
    if (!newGenres.includes(g)) delete createTable.genreFormats[g]
  })
})
const paymentRequired = ref(false)

const showModal = ref(false)
const handleAccept = () => { showModal.value = false }

// Scoring criteria modal
const showCriteriaModal = ref(false)

// Walk-in form
const showWalkInForm = ref(false)
const revealingRef = ref(null) // name of participant whose ref code is being held/revealed

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

const resetTemplate = async () => {
  templateLoading.value = true
  const tpl = await resetEmailTemplate(props.eventName)
  templateLoading.value = false
  if (tpl) {
    templateSubject.value = tpl.subject
    templateBody.value = tpl.body
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

const totalWalkIn = computed(() =>
  new Set(
    verifiedDbParticipants.value
      .filter(p => p.walkin)
      .map(p => p.participantId)
  ).size
)

const totalDbRegistered = computed(() => {
  const grouped = {}
  verifiedDbParticipants.value.forEach(p => {
    if (!grouped[p.participantId]) grouped[p.participantId] = []
    grouped[p.participantId].push(p)
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
      .map(p => p.participantId)
  ).size
)

const totalNotShownUp = computed(() => {
  const grouped = {}
  verifiedDbParticipants.value
    .filter(p => !p.walkin)
    .forEach(p => {
      if (!grouped[p.participantId]) grouped[p.participantId] = []
      grouped[p.participantId].push(p)
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
    .map(([name, rows]) => ({
      name,
      genres: rows.map(r => r.genreName),
      memberNames: rows.find(r => r.memberNames?.length)?.memberNames || []
    }))
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
      referenceCode: rows[0].referenceCode || null,
      memberNames: rows.find(r => r.memberNames?.length)?.memberNames || [],
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
  const resp = await linkGenreToEvent(props.eventName, createTable.genres, createTable.genreFormats)
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

// ── Genre format editing ────────────────────────────────────────────────────
const formatOptions = ['1v1', '2v2', '3v3', '4v4']
const editingFormatFor = ref(null)  // genreName currently being edited
const editingFormatValue = ref('')

const startEditFormat = (genre) => {
  editingFormatFor.value = genre.genreName
  editingFormatValue.value = genre.format || ''
}

const saveFormat = async (genreName) => {
  await updateEventGenreFormat(props.eventName, genreName, editingFormatValue.value || null)
  eventGenres.value = await getGenresByEvent(props.eventName)
  editingFormatFor.value = null
}
// ───────────────────────────────────────────────────────────────────────────

const emailedCount = computed(() =>
  new Set(
    verifiedDbParticipants.value
      .filter(p => !p.walkin && p.emailSent)
      .map(p => p.participantId)
  ).size
)

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
          class="flex items-center gap-2 px-4 py-2 rounded-xl border border-surface-600 bg-surface-800 text-sm
                 font-semibold text-content-secondary hover:bg-surface-700 hover:border-surface-500
                 transition-all duration-200"
        >
          <i class="pi pi-user-plus text-sm"></i>
          Add Walk-in
        </button>
        <button
          @click="showAdjustModal = true"
          class="flex items-center gap-2 px-4 py-2 rounded-xl border border-surface-600 bg-surface-800 text-sm
                 font-semibold text-content-secondary hover:bg-surface-700 hover:border-surface-500
                 transition-all duration-200"
        >
          <i class="pi pi-sliders-h text-sm"></i>
          Adjust Genres
        </button>
        <button
          @click="openTemplateModal"
          class="flex items-center gap-2 px-4 py-2 rounded-xl border border-surface-600 bg-surface-800 text-sm
                 font-semibold text-content-secondary hover:bg-surface-700 hover:border-surface-500
                 transition-all duration-200"
        >
          <i class="pi pi-envelope text-sm"></i>
          Email Template
        </button>
        <button
          @click="refreshParticipant"
          :disabled="loading"
          class="flex items-center gap-2 px-4 py-2 rounded-xl border border-surface-600 bg-surface-800 text-sm
                 font-semibold text-content-secondary hover:bg-surface-700 hover:border-surface-500
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
      <div class="stat-card stat-card-primary p-5 hover:bg-surface-700 transition-colors duration-200">
        <div class="flex items-center gap-3 mb-3">
          <div class="icon-wrap w-9 h-9 rounded-xl bg-surface-700 flex items-center justify-center">
            <i class="pi pi-users text-primary-400 text-sm"></i>
          </div>
          <span class="label-caps font-semibold text-content-muted uppercase">Total Participants</span>
        </div>
        <p class="text-3xl font-heading font-extrabold text-content-primary stat-number mt-1">
          {{ totalParticipants + totalWalkIn }}
        </p>
        <div class="flex gap-4 mt-3">
          <span class="text-[13px] text-content-secondary">Form: <strong class="text-content-primary">{{ totalParticipants }}</strong></span>
          <span class="text-[13px] text-content-secondary">Walk-in: <strong class="text-content-primary">{{ totalWalkIn }}</strong></span>
        </div>
      </div>

      <!-- Verified -->
      <div class="stat-card stat-card-success p-5 hover:bg-surface-700 transition-colors duration-200">
        <div class="flex items-center gap-3 mb-3">
          <div class="icon-wrap w-9 h-9 rounded-xl bg-surface-700 flex items-center justify-center">
            <i class="pi pi-check-circle text-teal-400 text-sm"></i>
          </div>
          <span class="label-caps font-semibold text-content-muted uppercase">Verification</span>
        </div>
        <p class="text-3xl font-heading font-extrabold text-content-primary stat-number mt-1">
          {{ totalVerified }}
        </p>
        <div class="flex flex-col gap-1 mt-3">
          <span class="text-[13px] text-content-secondary">
            Pending: <strong class="text-rose-300">{{ unverifiedParticipants.length }}</strong>
          </span>
        </div>
      </div>

      <!-- Registered -->
      <div class="stat-card stat-card-warning p-5 hover:bg-surface-700 transition-colors duration-200">
        <div class="flex items-center gap-3 mb-3">
          <div class="icon-wrap w-9 h-9 rounded-xl bg-surface-700 flex items-center justify-center">
            <i class="pi pi-id-card text-amber-300/60 text-sm"></i>
          </div>
          <span class="label-caps font-semibold text-content-muted uppercase">Registered</span>
        </div>
        <p class="text-3xl font-heading font-extrabold text-content-primary stat-number mt-1">
          {{ totalDbRegistered.length }}
        </p>
        <div class="flex gap-4 mt-3">
          <span class="text-[13px] text-content-secondary">Audition assigned</span>
          <span v-if="totalNotShownUp > 0" class="text-[13px] text-content-secondary">
            Absent: <strong class="text-amber-300">{{ totalNotShownUp }}</strong>
          </span>
        </div>
      </div>

      <!-- Email Progress -->
      <div class="stat-card stat-card-primary p-5 hover:bg-surface-700 transition-colors duration-200">
        <div class="flex items-center gap-3 mb-3">
          <div class="icon-wrap w-9 h-9 rounded-xl bg-surface-700 flex items-center justify-center">
            <i class="pi pi-send text-primary-400 text-sm"></i>
          </div>
          <span class="label-caps font-semibold text-content-muted uppercase">Email Progress</span>
        </div>
        <p class="text-3xl font-heading font-extrabold text-content-primary stat-number mt-1">
          {{ emailedCount }} / {{ totalVerified }}
        </p>
        <div class="flex gap-4 mt-3">
          <span class="text-[13px] text-content-secondary">QR emails sent</span>
        </div>
      </div>
    </div>

    <!-- Participant detail panels -->
    <div v-if="verifiedDbParticipants.length > 0" class="space-y-2 mb-8">

      <!-- Not shown up -->
      <div v-if="notShownUpList.length > 0" class="card overflow-hidden panel-warning">
        <button
          @click="expandedPeople.has('notShownUp') ? expandedPeople.delete('notShownUp') : expandedPeople.add('notShownUp'); expandedPeople = new Set(expandedPeople)"
          :aria-expanded="expandedPeople.has('notShownUp')"
          aria-controls="panel-not-shown-up"
          class="w-full flex items-center justify-between px-5 py-4 bg-surface-900/40 hover:bg-surface-700/60 transition-colors duration-150 text-left"
        >
          <div class="flex items-center gap-3">
            <div class="icon-wrap w-7 h-7 rounded-lg bg-surface-700 flex items-center justify-center shrink-0">
              <i class="pi pi-clock text-amber-300 text-xs"></i>
            </div>
            <span class="font-heading font-bold text-content-secondary">Not Shown Up</span>
            <span class="badge-warning inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-surface-700 text-amber-300 border border-amber-900/30">
              {{ notShownUpList.length }}
            </span>
          </div>
          <i class="pi pi-chevron-down text-content-muted text-xs transition-transform duration-200"
             :class="{ 'rotate-180': expandedPeople.has('notShownUp') }"></i>
        </button>
        <div v-if="expandedPeople.has('notShownUp')" id="panel-not-shown-up" class="px-4 pb-4 border-t border-surface-600/30 pt-4 shadow-[inset_0_4px_8px_rgba(0,0,0,0.2)]">
          <div class="space-y-2">
            <div
              v-for="p in notShownUpList"
              :key="p.name"
              class="flex flex-col gap-1.5 px-3 py-2.5 rounded-xl bg-surface-700/50 border border-surface-600"
            >
              <div class="flex items-center gap-2 flex-wrap">
                <span class="text-sm font-semibold text-content-secondary">{{ p.name }}</span>
                <span
                  v-if="verifiedDbParticipants.find(v => v.participantName === p.name)?.emailSent"
                  class="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-surface-700 text-teal-300 border border-teal-900/30"
                >
                  <i class="pi pi-check text-xs"></i> Email Sent
                </span>
              </div>
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="g in p.genres"
                  :key="g"
                  class="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-surface-800 border border-surface-600 text-content-muted"
                >{{ g }}</span>
              </div>
              <div v-if="p.memberNames.length" class="flex items-center gap-1.5 text-xs text-content-muted mt-0.5">
                <i class="pi pi-users" style="font-size:0.65rem"></i>
                <span>{{ p.memberNames.join(', ') }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Unverified (payment pending — DB-driven) -->
      <div v-if="unverifiedParticipants.length > 0" class="card overflow-hidden panel-danger">
        <button
          @click="expandedPeople.has('unverified') ? expandedPeople.delete('unverified') : expandedPeople.add('unverified'); expandedPeople = new Set(expandedPeople)"
          :aria-expanded="expandedPeople.has('unverified')"
          aria-controls="panel-unverified"
          class="w-full flex items-center justify-between px-5 py-4 bg-surface-900/40 hover:bg-surface-700/60 transition-colors duration-150 text-left"
        >
          <div class="flex items-center gap-3">
            <div class="icon-wrap w-7 h-7 rounded-lg bg-surface-700 flex items-center justify-center shrink-0">
              <i class="pi pi-exclamation-circle text-rose-300 text-xs"></i>
            </div>
            <span class="font-heading font-bold text-content-secondary truncate">Awaiting Payment Verification</span>
            <span class="badge-danger inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-surface-700 text-rose-300 border border-rose-900/30">
              {{ unverifiedParticipants.length }}
            </span>
          </div>
          <i class="pi pi-chevron-down text-content-muted text-xs transition-transform duration-200"
             :class="{ 'rotate-180': expandedPeople.has('unverified') }"></i>
        </button>
        <div v-if="expandedPeople.has('unverified')" id="panel-unverified" class="px-5 pb-5 border-t border-surface-600/30 pt-4 shadow-[inset_0_4px_8px_rgba(0,0,0,0.2)]">
          <!-- Batch action bar -->
          <div class="flex items-center justify-between mb-3">
            <p class="text-xs text-content-muted flex items-center gap-1.5">
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
              class="flex flex-col gap-2 px-3 py-2.5 rounded-xl bg-surface-700/50 border border-l-2 transition-colors"
              :class="selectedUnverified.has(p.participantId) ? 'border-primary-500/40 bg-primary-100/10 border-l-primary-500' : 'border-surface-600 border-l-rose-800/50'"
            >
              <!-- Top row: checkbox + name + genres -->
              <div class="flex items-start gap-3">
                <input
                  type="checkbox"
                  :checked="selectedUnverified.has(p.participantId)"
                  @change="toggleUnverifiedSelect(p.participantId)"
                  class="checkbox-custom shrink-0 mt-0.5"
                />
                <div class="flex-1 min-w-0">
                  <span class="text-sm font-semibold text-content-secondary block">{{ p.name }}</span>
                  <div class="flex flex-wrap gap-1 mt-1">
                    <span
                      v-for="g in p.genres"
                      :key="g"
                      class="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-surface-900 border border-surface-500/40 text-content-secondary"
                    >{{ g }}</span>
                  </div>
                </div>
              </div>
              <!-- Bottom row: action buttons -->
              <div class="flex items-center gap-2 justify-end">
                <a
                  v-if="p.screenshotUrl"
                  :href="p.screenshotUrl"
                  target="_blank"
                  class="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-surface-700 text-content-muted text-xs font-medium
                         hover:bg-surface-600 border border-surface-600 transition-colors"
                >
                  <i class="pi pi-image text-xs"></i>
                  View Receipt
                </a>
                <button
                  @click="handleVerifyAndEmail(p)"
                  :disabled="verifyingParticipantId === p.participantId"
                  class="flex items-center gap-1 px-2.5 py-1 rounded-lg bg-primary-600 text-white text-xs font-semibold
                         hover:bg-primary-500 hover:scale-[1.03] hover:shadow-[0_0_12px_rgba(34,211,238,0.3)]
                         active:scale-95 disabled:opacity-50 transition-all duration-150"
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
      <div v-if="registeredList.length > 0" class="card overflow-hidden panel-success">
        <button
          @click="expandedPeople.has('registered') ? expandedPeople.delete('registered') : expandedPeople.add('registered'); expandedPeople = new Set(expandedPeople)"
          :aria-expanded="expandedPeople.has('registered')"
          aria-controls="panel-registered"
          class="w-full flex items-center justify-between px-5 py-4 bg-surface-900/40 hover:bg-surface-700/60 transition-colors duration-150 text-left"
        >
          <div class="flex items-center gap-3">
            <div class="icon-wrap w-7 h-7 rounded-lg bg-surface-700 flex items-center justify-center shrink-0">
              <i class="pi pi-check-circle text-teal-400 text-xs"></i>
            </div>
            <span class="font-heading font-bold text-content-secondary">Registered</span>
            <span class="badge-success inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-surface-700 text-teal-300 border border-teal-900/30">
              {{ registeredList.length }}
            </span>
          </div>
          <i class="pi pi-chevron-down text-content-muted text-xs transition-transform duration-200"
             :class="{ 'rotate-180': expandedPeople.has('registered') }"></i>
        </button>
        <div v-if="expandedPeople.has('registered')" id="panel-registered" class="px-5 pb-4 border-t border-surface-600/30 pt-4 shadow-[inset_0_4px_8px_rgba(0,0,0,0.2)]">
          <div class="space-y-2">
            <div
              v-for="p in registeredList"
              :key="p.name"
              class="flex flex-col gap-2 px-3 py-2.5 rounded-xl bg-surface-900 border border-surface-600"
            >
              <!-- Row 1: name + status badge -->
              <div class="flex items-center gap-2 flex-wrap">
                <span class="text-sm font-semibold text-content-secondary">{{ p.name }}</span>
                <span
                  v-if="p.walkin"
                  class="shrink-0 inline-flex px-1.5 py-0.5 rounded text-xs font-medium bg-surface-700 text-content-muted border border-surface-600"
                >walk-in</span>
                <!-- Hold-to-reveal reference code for walk-ins -->
                <span
                  v-if="p.walkin && p.referenceCode"
                  class="relative shrink-0 inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-surface-800 border border-surface-600 cursor-pointer select-none touch-none"
                  @mousedown="revealingRef = p.name"
                  @mouseup="revealingRef = null"
                  @mouseleave="revealingRef = null"
                  @touchstart.prevent="revealingRef = p.name"
                  @touchend="revealingRef = null"
                  @touchcancel="revealingRef = null"
                >
                  <i class="pi pi-eye text-content-muted" style="font-size:0.65rem"></i>
                  <span class="text-content-muted">Ref code</span>
                  <!-- Tooltip above, away from cursor -->
                  <span
                    v-if="revealingRef === p.name"
                    class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-4 py-2.5 rounded-xl bg-surface-700 border border-surface-500 shadow-xl whitespace-nowrap z-50 pointer-events-none"
                  >
                    <span class="font-source tracking-widest text-primary-400 text-base font-bold">{{ p.referenceCode }}</span>
                    <!-- Arrow -->
                    <span class="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-surface-500"></span>
                  </span>
                </span>
                <span
                  v-else-if="p.entries.length > 0 && verifiedDbParticipants.find(v => v.participantName === p.name)?.emailSent"
                  class="shrink-0 inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-surface-700 text-teal-300 border border-teal-900/30"
                >
                  <i class="pi pi-check text-xs"></i> Email Sent
                </span>
                <span
                  v-else-if="!p.walkin"
                  class="shrink-0 inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-surface-700 text-amber-300 border border-amber-900/30"
                >
                  <i class="pi pi-clock text-xs"></i> Email Pending
                </span>
              </div>
              <!-- Row 2: genre + audition number badges -->
              <div class="flex flex-wrap gap-1.5">
                <span
                  v-for="e in p.entries"
                  :key="e.genre"
                  class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-800 border border-primary-200 text-sm"
                >
                  <span class="text-content-muted capitalize text-xs">{{ e.genre }}</span>
                  <span class="font-heading font-extrabold text-primary-400">#{{ e.auditionNumber }}</span>
                </span>
              </div>
              <!-- Row 3: team members (only for team entries) -->
              <div v-if="p.memberNames.length" class="flex items-center gap-1.5 text-xs text-content-muted">
                <i class="pi pi-users" style="font-size:0.65rem"></i>
                <span>{{ p.memberNames.join(', ') }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

    </div>

    <!-- Genre breakdown -->
    <div v-if="completeBreakdown.length > 0" class="mb-8">
      <div class="flex items-center justify-between mb-4">
        <h2 class="font-heading font-bold text-content-secondary text-lg">Genre Breakdown</h2>
        <button
          @click="showCriteriaModal = true"
          class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-surface-600 bg-surface-700/60
                 text-xs font-semibold text-content-muted hover:border-primary-500/50 hover:text-primary-400 transition-all duration-150"
        >
          <i class="pi pi-sliders-h text-xs" />
          Scoring Criteria
        </button>
      </div>
      <div class="space-y-2">
        <div
          v-for="genre in completeBreakdown"
          :key="genre.genre"
          class="card overflow-hidden"
        >
          <!-- Genre header (always visible) -->
          <button
            @click="toggleGenre(genre.genre)"
            :aria-expanded="expandedGenres.has(genre.genre)"
            class="w-full flex items-center justify-between px-5 py-4 bg-surface-900/40 hover:bg-surface-700/60 transition-colors duration-150 text-left"
          >
            <div class="flex items-center gap-4">
              <span class="font-heading font-bold text-content-primary capitalize">{{ genre.genre }}</span>
              <div class="flex items-center gap-3">
                <span class="badge-neutral text-xs">Total: {{ genre.total }}</span>
                <span class="badge-success inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-surface-700 text-teal-300 border border-teal-900/30">
                  Reg: {{ genre.registered }}
                </span>
                <span
                  v-if="genre.unregistered > 0"
                  class="badge-danger inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-surface-700 text-rose-300 border border-rose-900/30"
                >
                  Unreg: {{ genre.unregistered }}
                </span>
              </div>
            </div>
            <i
              class="pi pi-chevron-down text-content-muted text-xs transition-transform duration-200"
              :class="{ 'rotate-180': expandedGenres.has(genre.genre) }"
            ></i>
          </button>

          <!-- Expanded: unregistered list -->
          <div
            v-if="expandedGenres.has(genre.genre)"
            class="px-5 pb-4 border-t border-surface-600/30"
          >
            <div class="pt-4">
              <template v-if="getUnregistered(genre.genre).unregistered.length > 0">
                <p class="label-caps font-semibold text-content-muted uppercase mb-2">
                  Unregistered Participants
                </p>
                <div class="flex flex-wrap gap-2">
                  <span
                    v-for="p in getUnregistered(genre.genre).unregistered"
                    :key="p.participantName"
                    class="inline-flex items-center px-2.5 py-1 rounded-full bg-surface-800 text-rose-300 text-xs font-medium border border-rose-300/20 font-source"
                  >
                    {{ p.participantName }}
                  </span>
                </div>
              </template>
              <template v-else>
                <div class="flex items-center gap-2 text-teal-300 text-sm">
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
        <div class="icon-wrap w-9 h-9 rounded-xl bg-surface-700 flex items-center justify-center">
          <i class="pi pi-exclamation-triangle text-amber-300 text-sm"></i>
        </div>
        <div>
          <h2 class="font-heading font-bold text-content-secondary">Event Setup Required</h2>
          <p class="text-muted text-sm">No record found for this event. Select genres to initialise.</p>
        </div>
      </div>

      <div class="mb-6">
        <label class="block text-sm font-semibold text-content-secondary mb-3">
          Genres / Categories
        </label>
        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
          <div
            v-for="g in genreOptions"
            :key="g.genreName"
            class="flex flex-col gap-2 px-4 py-3 rounded-xl border transition-all duration-150"
            :class="createTable.genres.includes(g.genreName)
              ? 'bg-primary-100 border-primary-300'
              : 'bg-surface-800 border-surface-600 hover:border-surface-500'"
          >
            <label class="flex items-center gap-2.5 cursor-pointer"
              :class="createTable.genres.includes(g.genreName) ? 'text-primary-400' : 'text-content-secondary'"
            >
              <input
                type="checkbox"
                :id="g.genreName"
                :value="g.genreName"
                v-model="createTable.genres"
                class="w-4 h-4 rounded accent-primary-600"
              />
              <span class="text-sm font-medium capitalize">{{ g.genreName }}</span>
            </label>
            <select
              v-if="createTable.genres.includes(g.genreName)"
              v-model="createTable.genreFormats[g.genreName]"
              class="text-xs px-2 py-1 rounded-lg bg-white/70 border border-primary-300 text-primary-700 focus:outline-none focus:ring-1 focus:ring-primary-500/40 w-full"
            >
              <option value="">No format</option>
              <option v-for="opt in formatOptions" :key="opt" :value="opt">{{ opt }}</option>
            </select>
          </div>
        </div>
      </div>

      <!-- Payment required toggle -->
      <div class="mb-6">
        <label
          class="flex items-center gap-3 px-4 py-3 rounded-xl border cursor-pointer transition-all duration-150 w-fit"
          :class="paymentRequired
            ? 'bg-amber-950 border-amber-300 text-amber-400'
            : 'bg-surface-800 border-surface-600 text-content-secondary hover:border-surface-500'"
        >
          <input type="checkbox" v-model="paymentRequired" class="w-4 h-4 rounded accent-amber-500" />
          <div>
            <span class="text-sm font-semibold">Payment Required</span>
            <p class="text-xs mt-0.5" :class="paymentRequired ? 'text-amber-400' : 'text-content-muted'">
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

  <!-- Genre Format Management (shown when event is set up) -->
  <div v-if="tableExist && eventGenres.length > 0" class="card p-5 mt-6">
    <div class="flex items-center gap-3 mb-4">
      <div class="icon-wrap w-8 h-8 rounded-xl bg-surface-700 flex items-center justify-center">
        <i class="pi pi-tag text-content-muted text-sm"></i>
      </div>
      <div>
        <h2 class="font-heading font-bold text-content-secondary text-sm">Genre Formats</h2>
        <p class="text-xs text-content-muted mt-0.5">Set battle format per genre (e.g. 2v2). Required for team vs solo audition splitting.</p>
      </div>
    </div>
    <div class="flex flex-col gap-2">
      <div
        v-for="g in eventGenres"
        :key="g.genreName"
        class="flex items-center gap-3 px-3 py-2.5 rounded-xl bg-surface-800/60 border border-surface-600/30"
      >
        <span class="font-heading font-semibold text-content-secondary text-sm flex-1 capitalize">{{ g.genreName }}</span>

        <!-- Viewing mode -->
        <template v-if="editingFormatFor !== g.genreName">
          <span class="font-source text-xs px-2 py-0.5 rounded-md"
            :class="g.format ? 'bg-primary-500/15 text-primary-400 border border-primary-500/30' : 'bg-surface-700 text-surface-500 border border-surface-600/30'">
            {{ g.format || 'No format' }}
          </span>
          <button
            @click="startEditFormat(g)"
            class="text-xs px-2.5 py-1 rounded-lg border border-surface-600/50 text-content-muted hover:border-surface-500 hover:text-content-primary transition-all"
          >
            <i class="pi pi-pencil" style="font-size:0.65rem"></i>
            Edit
          </button>
        </template>

        <!-- Editing mode -->
        <template v-else>
          <select
            v-model="editingFormatValue"
            class="text-xs px-2.5 py-1.5 rounded-lg bg-surface-700 border border-primary-500/50 text-content-primary focus:outline-none focus:ring-1 focus:ring-primary-500/40"
          >
            <option value="">No format</option>
            <option v-for="opt in formatOptions" :key="opt" :value="opt">{{ opt }}</option>
          </select>
          <button
            @click="saveFormat(g.genreName)"
            class="text-xs px-2.5 py-1 rounded-lg bg-primary-600 text-white hover:bg-primary-700 transition-all font-semibold"
          >
            Save
          </button>
          <button
            @click="editingFormatFor = null"
            class="text-xs px-2 py-1 rounded-lg border border-surface-600/50 text-content-muted hover:border-surface-500 transition-all"
          >
            Cancel
          </button>
        </template>
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
    <p class="text-content-secondary leading-relaxed">{{ modalMessage }}</p>
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
      <div class="relative bg-surface-800 rounded-2xl shadow-2xl w-full max-w-lg flex flex-col" style="max-height: 90vh;">
        <!-- Header -->
        <div class="flex items-center justify-between px-6 py-4 border-b border-surface-600/30">
          <div class="flex items-center gap-3">
            <div class="w-8 h-8 rounded-xl bg-primary-100 flex items-center justify-center">
              <i class="pi pi-sliders-h text-primary-400 text-sm"></i>
            </div>
            <div>
              <h2 class="font-heading font-bold text-content-primary text-base">Adjust Genres</h2>
              <p class="text-xs text-content-muted">{{ props.eventName }}</p>
            </div>
          </div>
          <button
            @click="closeAdjustModal"
            class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-surface-700 text-content-muted hover:text-content-secondary transition-colors"
          >
            <i class="pi pi-times text-sm"></i>
          </button>
        </div>

        <!-- Body -->
        <div class="flex-1 overflow-y-auto px-6 py-5 space-y-5">
          <!-- Search -->
          <div>
            <label class="block text-sm font-semibold text-content-secondary mb-1.5">Search Participant</label>
            <input
              v-model="adjustSearch"
              type="text"
              placeholder="Type a name…"
              class="w-full px-4 py-2.5 rounded-xl border border-surface-600 text-sm text-content-primary
                     focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors"
            />
          </div>

          <!-- Search results (only shown when no participant selected) -->
          <div v-if="adjustSearchResults.length > 0 && !adjustParticipant" class="flex flex-wrap gap-2">
            <button
              v-for="name in adjustSearchResults"
              :key="name"
              @click="adjustParticipant = name"
              class="badge-neutral px-3 py-1.5 rounded-full text-sm font-medium bg-surface-700 text-content-secondary
                     hover:bg-primary-100 hover:text-primary-400 border border-surface-600 transition-colors"
            >
              {{ name }}
            </button>
          </div>

          <!-- Selected participant view -->
          <div v-if="adjustParticipant">
            <div class="flex items-center gap-2 mb-4">
              <span class="font-heading font-bold text-content-primary">{{ adjustParticipant }}</span>
              <button
                @click="adjustParticipant = null; adjustSearch = ''"
                class="text-content-muted hover:text-content-secondary transition-colors"
              >
                <i class="pi pi-times-circle text-sm"></i>
              </button>
            </div>

            <!-- Current genres -->
            <div class="mb-5">
              <p class="text-xs font-semibold text-content-muted uppercase tracking-wider mb-2">Current Genres</p>
              <div v-if="adjustParticipantGenres.length === 0" class="text-sm text-content-muted">No genres assigned</div>
              <div class="space-y-2">
                <div
                  v-for="p in adjustParticipantGenres"
                  :key="p.genreId"
                  class="flex items-center justify-between px-3 py-2 rounded-xl bg-surface-900 border border-surface-600"
                >
                  <div class="flex items-center gap-2">
                    <span class="text-sm font-medium text-content-secondary">{{ p.genreName }}</span>
                    <span
                      v-if="p.auditionNumber !== null"
                      class="badge-warning inline-flex items-center px-2 py-0.5 rounded-full text-xs font-semibold bg-surface-700 text-amber-300 border border-amber-900/30"
                    >
                      #{{ p.auditionNumber }}
                    </span>
                  </div>
                  <!-- Confirm state -->
                  <div v-if="pendingRemoveItem === p" class="flex items-center gap-2">
                    <span class="text-xs text-amber-300">Audition #{{ p.auditionNumber }} will be withdrawn</span>
                    <button
                      @click="confirmRemoveGenre(p)"
                      class="px-2.5 py-1 rounded-lg bg-rose-800 text-white text-xs font-semibold hover:bg-rose-700 transition-colors"
                    >
                      Confirm
                    </button>
                    <button
                      @click="pendingRemoveItem = null"
                      class="px-2.5 py-1 rounded-lg border border-surface-600 text-xs font-semibold text-content-muted hover:bg-surface-700 transition-colors"
                    >
                      Cancel
                    </button>
                  </div>
                  <button
                    v-else
                    @click="requestRemoveGenre(p)"
                    :disabled="adjustLoading"
                    class="px-2.5 py-1 rounded-lg bg-surface-700 text-rose-300 text-xs font-semibold
                           hover:bg-surface-600 border border-rose-900/30 disabled:opacity-50 transition-colors"
                  >
                    Remove
                  </button>
                </div>
              </div>
            </div>

            <!-- Add genres -->
            <div>
              <p class="text-xs font-semibold text-content-muted uppercase tracking-wider mb-2">Add Genre</p>
              <div v-if="adjustAvailableGenres.length === 0" class="text-sm text-content-muted">All genres already assigned</div>
              <div class="space-y-2">
                <div
                  v-for="g in adjustAvailableGenres"
                  :key="g.genreName"
                  class="flex items-center justify-between px-3 py-2 rounded-xl bg-surface-900 border border-surface-600"
                >
                  <span class="text-sm font-medium text-content-secondary">{{ g.genreName }}</span>
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
      <div class="relative bg-surface-800 rounded-2xl shadow-2xl w-full max-w-2xl flex flex-col" style="max-height: 90vh;">
        <!-- Header -->
        <div class="flex items-center justify-between px-6 py-4 border-b border-surface-600/30">
          <div class="flex items-center gap-3">
            <div class="w-8 h-8 rounded-xl bg-primary-100 flex items-center justify-center">
              <i class="pi pi-envelope text-primary-400 text-sm"></i>
            </div>
            <div>
              <h2 class="font-heading font-bold text-content-primary text-base">Email Template</h2>
              <p class="text-xs text-content-muted">{{ props.eventName }}</p>
            </div>
          </div>
          <button
            @click="showTemplateModal = false"
            class="w-8 h-8 flex items-center justify-center rounded-lg hover:bg-surface-700 text-content-muted hover:text-content-secondary transition-colors"
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
            <label class="block text-sm font-semibold text-content-secondary mb-1.5">Subject</label>
            <input
              v-model="templateSubject"
              type="text"
              class="w-full px-4 py-2.5 rounded-xl border border-surface-600 text-sm text-content-primary
                     focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors"
              placeholder="Email subject…"
            />
          </div>
          <div>
            <label class="block text-sm font-semibold text-content-secondary mb-2">Body</label>

            <!-- Variable reference -->
            <div class="mb-3 p-3 rounded-xl bg-surface-900/60 border border-surface-600/40 space-y-2 text-xs text-content-muted">
              <p class="font-semibold text-content-secondary uppercase tracking-wider text-[10px]">Available Variables</p>
              <div class="grid grid-cols-2 gap-x-4 gap-y-1">
                <div><code class="font-source bg-surface-700 px-1 rounded text-primary-300">{name}</code> — display name (team name or stage name)</div>
                <div><code class="font-source bg-surface-700 px-1 rounded text-primary-300">{stageName}</code> — representative's stage name</div>
                <div><code class="font-source bg-surface-700 px-1 rounded text-primary-300">{teamName}</code> — crew/team name</div>
                <div><code class="font-source bg-surface-700 px-1 rounded text-primary-300">{members}</code> — all team members (comma-separated)</div>
                <div><code class="font-source bg-surface-700 px-1 rounded text-primary-300">{soloCategories}</code> — solo (1v1) categories entered</div>
                <div><code class="font-source bg-surface-700 px-1 rounded text-primary-300">{teamCategories}</code> — team categories entered</div>
                <div><code class="font-source bg-surface-700 px-1 rounded text-primary-300">{refCode}</code> — results reference code</div>
              </div>
              <p class="font-semibold text-content-secondary uppercase tracking-wider text-[10px] pt-1">Conditional Blocks (for mixed events)</p>
              <div class="space-y-1">
                <div><code class="font-source bg-surface-700 px-1 rounded text-amber-300">{if:team}...{endif:team}</code> — shown only if participant has team categories</div>
                <div><code class="font-source bg-surface-700 px-1 rounded text-amber-300">{if:solo}...{endif:solo}</code> — shown only if participant has solo categories</div>
              </div>
            </div>

            <textarea
              v-model="templateBody"
              rows="10"
              class="w-full px-4 py-2.5 rounded-xl border border-surface-600 text-sm text-content-primary font-source
                     focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors resize-none"
              placeholder="Email body…"
            ></textarea>
          </div>
        </div>

        <!-- Footer -->
        <div class="flex items-center justify-between gap-3 px-6 py-4 border-t border-surface-600/30">
          <button
            @click="resetTemplate"
            :disabled="templateLoading"
            class="flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold text-content-muted
                   hover:text-content-secondary hover:bg-surface-700 disabled:opacity-50 transition-colors"
            title="Regenerate smart default based on event genre formats"
          >
            <i class="pi pi-refresh text-xs"></i>
            Reset to default
          </button>
          <div class="flex items-center gap-3">
            <button
              @click="showTemplateModal = false"
              class="px-4 py-2 rounded-xl border border-surface-600 text-sm font-semibold text-content-secondary
                     hover:bg-surface-700 transition-colors"
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
    </div>
  </Teleport>

  <!-- Scoring Criteria Modal -->
  <ScoringCriteriaModal
    v-model="showCriteriaModal"
    :eventName="props.eventName"
    :genres="completeBreakdown.map(g => g.genre)"
  />
</template>
