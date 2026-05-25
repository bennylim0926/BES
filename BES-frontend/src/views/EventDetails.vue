<script setup>
import { ref, onMounted, onUnmounted, reactive, watch, computed } from 'vue';
import { RouterLink } from 'vue-router';
import ActionDoneModal from './ActionDoneModal.vue';
import DynamicTable from '@/components/DynamicTable.vue';
import { checkTableExist, getFileId, getResponseDetails, fetchAllGenres, getGenresByEvent, getVerifiedParticipantsByEvent, addJudges, insertEventInTable, linkGenreToEvent, addParticipantToSystem, getSheetSize, getRegisteredParticipantsByEvent, getEmailTemplate, updateEmailTemplate, resetEmailTemplate, removeParticipantGenre, addGenreToParticipant, getUnverifiedParticipantsDB, verifyAndEmailParticipant, verifyAndEmailBatch, updateEventGenreFormat, getEventJudges, addEventJudge, removeEventJudge, getScoringCriteria, fetchAllFolderEvents, fetchAllEvents } from '@/utils/api';
import { setActiveEvent } from '@/utils/auth';
import { filterObject, useDelay } from '@/utils/utils';
import { createClient, subscribeToChannel, deactivateClient } from '@/utils/websocket';
import ReusableButton from '@/components/ReusableButton.vue';
import AuditionNumber from './AuditionNumber.vue';
import LoadingOverlay from '@/components/LoadingOverlay.vue';
import CreateParticipantForm from '@/components/CreateParticipantForm.vue'
import ScoringCriteriaModal from '@/components/ScoringCriteriaModal.vue';

const fileId = ref('')
const dbEventId = ref(null)
const activeFolderID = ref(null)
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
const activeTab = ref('setup') // 'setup' | 'event-day'
const activeGenreTab = ref(null)
let refreshInterval = null
let wsClient = null

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

const registeredSearch = ref('')
const registeredGenreFilter = ref('')
const registeredPage = ref(1)
const REGISTERED_PAGE_SIZE = 10

const registeredGenreOptions = computed(() => {
  const genres = new Set()
  registeredList.value.forEach(p => p.entries.forEach(e => genres.add(e.genre)))
  return [...genres].sort()
})

const filteredRegisteredList = computed(() => {
  let list = registeredList.value
  const q = registeredSearch.value.trim().toLowerCase()
  if (q) list = list.filter(p =>
    p.name.toLowerCase().includes(q) ||
    p.entries.some(e => e.genre.toLowerCase().includes(q))
  )
  if (registeredGenreFilter.value)
    list = list.filter(p => p.entries.some(e => e.genre === registeredGenreFilter.value))
  return list
})

const registeredTotalPages = computed(() => Math.max(1, Math.ceil(filteredRegisteredList.value.length / REGISTERED_PAGE_SIZE)))

const paginatedRegisteredList = computed(() => {
  const start = (registeredPage.value - 1) * REGISTERED_PAGE_SIZE
  return filteredRegisteredList.value.slice(start, start + REGISTERED_PAGE_SIZE)
})

watch([registeredSearch, registeredGenreFilter], () => { registeredPage.value = 1 })

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

// Clear selected participant when search input is cleared
watch(adjustSearch, (val) => {
  if (!val.trim()) adjustParticipant.value = null
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
  resp.json().then(async result => {
    loading.value = false
    getTitle(resp.status)
    modalMessage.value = result
    showModal.value = true
    tableExist.value = true
    if (!dbEventId.value) {
      const dbEvents = await fetchAllEvents() ?? []
      const dbEvent = dbEvents.find(e => e.name === props.eventName)
      if (dbEvent) dbEventId.value = dbEvent.id
    }
    if (dbEventId.value) setActiveEvent(dbEventId.value, props.eventName, activeFolderID.value)
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

const refreshFromDb = async () => {
  verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
  unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
}

const handleWalkInCreated = async () => {
  showWalkInForm.value = false
  await refreshFromDb()
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

// ── Scoring criteria (per-genre, for inline display) ────────────────────────
const criteriaByGenre = ref({}) // genreName → array of { id, name, weight }

const loadCriteriaForAllGenres = async (genres) => {
  const map = {}
  await Promise.all(genres.map(async (g) => {
    map[g.genreName] = await getScoringCriteria(props.eventName, g.genreName) ?? []
  }))
  criteriaByGenre.value = map
}
// ───────────────────────────────────────────────────────────────────────────

// ── Judges ──────────────────────────────────────────────────────────────────
const eventJudges = ref([])
const addJudgeInput = ref('')

const submitAddJudge = async () => {
  if (!addJudgeInput.value.trim()) return
  const res = await addEventJudge(props.eventName, addJudgeInput.value.trim())
  if (res?.ok) eventJudges.value = await res.json()
  addJudgeInput.value = ''
}

const submitRemoveJudge = async (judgeId) => {
  const res = await removeEventJudge(props.eventName, judgeId)
  if (res?.ok) eventJudges.value = await res.json()
}
// ───────────────────────────────────────────────────────────────────────────

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
    if (fileId.value) {
      participantsNumBreakdown.value = await getResponseDetails(fileId.value)
      totalParticipants.value = await getSheetSize(fileId.value) ?? 0
    }
  }
)

onMounted(async () => {
  onStartLoading.value = true
  tableExist.value = checkTableExist(eventName, tableExist)
  // folderID may be absent when navigating from the navbar dropdown or EventSelector redirect
  let resolvedFolderID = props.folderID
  if (!resolvedFolderID) {
    const folderEvents = await fetchAllFolderEvents()
    const match = folderEvents?.find(e => e.folderName === props.eventName)
    resolvedFolderID = match?.folderID ?? null
  }
  activeFolderID.value = resolvedFolderID
  fileId.value = await getFileId(resolvedFolderID)
  const dbEvents = await fetchAllEvents() ?? []
  const dbEvent = dbEvents.find(e => e.name === props.eventName)
  if (dbEvent) {
    dbEventId.value = dbEvent.id
    setActiveEvent(dbEvent.id, dbEvent.name, resolvedFolderID)
  }
  genreOptions.value = await fetchAllGenres()
  eventGenres.value = await getGenresByEvent(props.eventName)
  if (eventGenres.value.length > 0) activeGenreTab.value = eventGenres.value[0].genreName
  await loadCriteriaForAllGenres(eventGenres.value)
  eventJudges.value = await getEventJudges(props.eventName)
  if (tableExist.value) {
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
    verifiedFormParticipants.value = await getVerifiedParticipantsByEvent(eventName.value)
    unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
  }
  await useDelay().wait(2500)
  onStartLoading.value = false
  if (tableExist.value) {
    refreshInterval = setInterval(refreshFromDb, 30000)
    wsClient = createClient()
    let refreshPending = false
    subscribeToChannel(wsClient, '/topic/audition/', (msg) => {
      if (msg.eventName !== props.eventName) return
      if (!refreshPending) {
        refreshPending = true
        refreshFromDb().finally(() => { refreshPending = false })
      }
    })
  }
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
  if (wsClient) deactivateClient(wsClient)
})
</script>

<template>
  <div class="page-container">

    <!-- Page header -->
    <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-6">
      <div>
        <h1 class="page-title">{{ props.eventName }}</h1>
        <p class="text-muted mt-1">Event overview and participant management</p>
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
        <RouterLink
          to="/event/audition-number"
          class="flex items-center gap-2 px-4 py-2 rounded-xl border border-surface-600 bg-surface-800 text-sm
                 font-semibold text-content-secondary hover:bg-surface-700 hover:border-surface-500
                 transition-all duration-200"
        >
          <i class="pi pi-hashtag text-sm"></i>
          Audition Screen
        </RouterLink>
        <button
          @click="refreshParticipant"
          :disabled="loading"
          class="flex items-center gap-2 px-4 py-2 rounded-xl border border-surface-600 bg-surface-800 text-sm
                 font-semibold text-content-secondary hover:bg-surface-700 hover:border-surface-500
                 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200"
        >
          <i class="pi text-sm" :class="loading ? 'pi-spinner pi-spin text-primary-500' : 'pi-cloud-download'"></i>
          {{ loading ? 'Importing…' : 'Import from Sheets' }}
        </button>
      </div>
    </div>

    <!-- Tab toggle -->
    <div class="flex rounded-xl overflow-hidden border border-surface-600 w-fit mb-6">
      <button
        @click="activeTab = 'setup'"
        class="px-5 py-2 text-sm font-semibold transition-all duration-150"
        :class="activeTab === 'setup'
          ? 'bg-primary-600 text-white'
          : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
      >
        <i class="pi pi-cog text-xs mr-1.5"></i>
        Setup
      </button>
      <button
        @click="activeTab = 'event-day'"
        class="px-5 py-2 text-sm font-semibold transition-all duration-150"
        :class="activeTab === 'event-day'
          ? 'bg-primary-600 text-white'
          : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
      >
        <i class="pi pi-calendar text-xs mr-1.5"></i>
        Event Day
        <span
          v-if="totalNotShownUp > 0"
          class="ml-1.5 inline-flex items-center justify-center w-4 h-4 rounded-full text-[10px] font-bold bg-amber-500 text-white"
        >{{ totalNotShownUp }}</span>
        <span
          v-else-if="unverifiedParticipants.length > 0 && activeTab !== 'event-day'"
          class="ml-1.5 inline-flex items-center justify-center w-4 h-4 rounded-full text-[10px] font-bold bg-rose-500 text-white"
        >{{ unverifiedParticipants.length }}</span>
      </button>
    </div>

    <!-- ── SETUP TAB ─────────────────────────────────────────────────────── -->
    <template v-if="activeTab === 'setup'">

    <!-- Stat strip -->
    <div class="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-6">
      <div class="card p-4">
        <p class="text-xs font-semibold text-content-muted uppercase tracking-wide mb-1">Total</p>
        <p class="text-2xl font-heading font-extrabold text-content-primary">{{ (totalParticipants || 0) + totalWalkIn }}</p>
        <div class="flex gap-3 mt-1">
          <span class="text-xs text-content-muted">Form: <strong class="text-content-secondary">{{ totalParticipants || 0 }}</strong></span>
          <span class="text-xs text-content-muted">Walk-in: <strong class="text-content-secondary">{{ totalWalkIn }}</strong></span>
        </div>
      </div>
      <div class="card p-4">
        <p class="text-xs font-semibold text-content-muted uppercase tracking-wide mb-1">Pending Verification</p>
        <p class="text-2xl font-heading font-extrabold" :class="unverifiedParticipants.length > 0 ? 'text-rose-300' : 'text-content-primary'">
          {{ unverifiedParticipants.length }}
        </p>
        <span class="text-xs text-content-muted">{{ totalVerified }} verified</span>
      </div>
      <div class="card p-4">
        <p class="text-xs font-semibold text-content-muted uppercase tracking-wide mb-1">Emails Sent</p>
        <p class="text-2xl font-heading font-extrabold text-content-primary">{{ emailedCount }}<span class="text-base font-medium text-content-muted"> / {{ totalVerified }}</span></p>
        <span class="text-xs text-content-muted">QR emails</span>
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

  <!-- Genre Configuration — unified per-genre tabs (participants, format, criteria, judges) -->
  <div v-if="tableExist && eventGenres.length > 0" class="card overflow-hidden mt-6">

    <!-- Genre tab bar -->
    <div class="flex border-b border-surface-700/50 bg-surface-900/30 overflow-x-auto">
      <button
        v-for="g in eventGenres"
        :key="g.genreName"
        @click="activeGenreTab = g.genreName"
        class="px-5 py-3 text-sm font-semibold whitespace-nowrap transition-all duration-150 border-b-2"
        :class="activeGenreTab === g.genreName
          ? 'border-primary-500 text-content-primary bg-surface-800/50'
          : 'border-transparent text-content-muted hover:text-content-secondary hover:bg-surface-700/30'"
      >
        {{ g.genreName }}
        <span
          v-if="completeBreakdown.find(b => b.genre === normalizeGenreName(g.genreName))"
          class="ml-1.5 text-xs font-normal opacity-60"
        >{{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.genreName)).total }}</span>
      </button>
    </div>

    <!-- Tab content for each genre -->
    <template v-for="g in eventGenres" :key="g.genreName + '-content'">
      <div v-if="activeGenreTab === g.genreName" class="p-5 space-y-4">

        <!-- Participant counts (only when data available) -->
        <template v-if="completeBreakdown.find(b => b.genre === normalizeGenreName(g.genreName))">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="badge-neutral text-xs">Total: {{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.genreName)).total }}</span>
            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-surface-700 text-teal-300 border border-teal-900/30">
              Reg: {{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.genreName)).registered }}
            </span>
            <span
              v-if="completeBreakdown.find(b => b.genre === normalizeGenreName(g.genreName)).unregistered > 0"
              class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-surface-700 text-rose-300 border border-rose-900/30"
            >Unreg: {{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.genreName)).unregistered }}</span>
          </div>

          <!-- Unregistered list -->
          <div v-if="getUnregistered(normalizeGenreName(g.genreName)).unregistered.length > 0">
            <p class="text-xs font-semibold text-content-muted uppercase tracking-wide mb-1.5">Unregistered Participants</p>
            <div class="flex flex-wrap gap-2">
              <span
                v-for="p in getUnregistered(normalizeGenreName(g.genreName)).unregistered"
                :key="p.participantName"
                class="inline-flex items-center px-2.5 py-1 rounded-full bg-surface-800 text-rose-300 text-xs font-medium border border-rose-300/20 font-source"
              >{{ p.participantName }}</span>
            </div>
          </div>
          <div v-else class="flex items-center gap-2 text-teal-300 text-xs">
            <i class="pi pi-check-circle"></i>
            <span>All participants registered</span>
          </div>

          <div class="h-px bg-surface-700/40"></div>
        </template>

        <!-- Format + Scoring Criteria -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">

          <!-- Battle Format -->
          <div class="flex flex-col gap-2 p-3 rounded-xl bg-surface-800/60 border border-surface-600/30">
            <p class="text-xs font-semibold text-content-muted uppercase tracking-wide">Battle Format</p>
            <template v-if="editingFormatFor !== g.genreName">
              <div class="flex items-center justify-between">
                <span class="font-source text-sm" :class="g.format ? 'text-primary-400' : 'text-content-muted'">
                  {{ g.format || 'No format' }}
                </span>
                <button
                  @click="startEditFormat(g)"
                  class="text-xs px-2.5 py-1 rounded-lg border border-surface-600/50 text-content-muted hover:border-surface-500 hover:text-content-primary transition-all"
                ><i class="pi pi-pencil" style="font-size:0.65rem"></i> Edit</button>
              </div>
            </template>
            <template v-else>
              <div class="flex items-center gap-2">
                <select
                  v-model="editingFormatValue"
                  class="flex-1 text-xs px-2.5 py-1.5 rounded-lg bg-surface-700 border border-primary-500/50 text-content-primary focus:outline-none focus:ring-1 focus:ring-primary-500/40"
                >
                  <option value="">No format</option>
                  <option v-for="opt in formatOptions" :key="opt" :value="opt">{{ opt }}</option>
                </select>
                <button @click="saveFormat(g.genreName)" class="text-xs px-2.5 py-1 rounded-lg bg-primary-600 text-white hover:bg-primary-700 transition-all font-semibold">Save</button>
                <button @click="editingFormatFor = null" class="text-xs px-2 py-1 rounded-lg border border-surface-600/50 text-content-muted hover:border-surface-500 transition-all">Cancel</button>
              </div>
            </template>
          </div>

          <!-- Scoring Criteria -->
          <div class="flex flex-col gap-2 p-3 rounded-xl bg-surface-800/60 border border-surface-600/30">
            <div class="flex items-center justify-between">
              <p class="text-xs font-semibold text-content-muted uppercase tracking-wide">Scoring Criteria</p>
              <button
                @click="showCriteriaModal = true"
                class="text-xs px-2.5 py-1 rounded-lg border border-surface-600/50 text-content-muted hover:border-primary-500/50 hover:text-primary-400 transition-all whitespace-nowrap"
              ><i class="pi pi-sliders-h" style="font-size:0.65rem"></i> Configure</button>
            </div>
            <template v-if="criteriaByGenre[g.genreName]?.length">
              <div class="flex flex-wrap gap-1.5">
                <span
                  v-for="c in criteriaByGenre[g.genreName]"
                  :key="c.id"
                  class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-surface-700 border border-surface-600/50 text-xs text-content-secondary"
                >
                  {{ c.name }}
                  <span v-if="c.weight != null" class="font-source text-primary-400">×{{ c.weight }}</span>
                </span>
              </div>
            </template>
            <span v-else class="text-xs text-content-muted">Default (single score)</span>
          </div>
        </div>

        <!-- Judges -->
        <div>
          <p class="text-xs font-semibold text-content-muted uppercase tracking-wide mb-2">
            Judges <span class="normal-case font-normal opacity-60">(shared across all genres)</span>
          </p>
          <div class="flex flex-col gap-1.5">
            <div
              v-for="j in eventJudges"
              :key="j.judgeId"
              class="flex items-center gap-3 px-3 py-2 rounded-xl bg-surface-800/60 border border-surface-600/30"
            >
              <i class="pi pi-user text-content-muted text-xs shrink-0"></i>
              <span class="font-heading font-semibold text-content-secondary text-sm flex-1">{{ j.judgeName }}</span>
              <button
                @click="submitRemoveJudge(j.judgeId)"
                class="text-xs px-2.5 py-1 rounded-lg border border-surface-600/50 text-content-muted hover:border-red-800/50 hover:text-red-400 transition-all"
              >Remove</button>
            </div>
            <div class="flex items-center gap-2 px-3 py-2 rounded-xl border border-dashed border-surface-600/40 mt-0.5">
              <input
                v-model="addJudgeInput"
                type="text"
                placeholder="Add judge…"
                class="flex-1 bg-transparent text-sm text-content-secondary placeholder:text-content-muted focus:outline-none"
                @keyup.enter="submitAddJudge"
              />
              <button
                @click="submitAddJudge"
                class="flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-semibold bg-surface-600 text-content-secondary hover:bg-surface-500 transition-colors"
              ><i class="pi pi-plus" style="font-size:0.65rem"></i> Add</button>
            </div>
            <p v-if="eventJudges.length === 0" class="text-xs text-content-muted px-1">No judges added yet</p>
          </div>
        </div>

      </div>
    </template>
  </div>

  </template>
  <!-- ── END SETUP TAB ─────────────────────────────────────────────────────── -->

  <!-- ── EVENT DAY TAB ─────────────────────────────────────────────────────── -->
  <template v-if="activeTab === 'event-day'">

    <!-- Stat strip -->
    <div class="grid grid-cols-2 gap-3 mb-6">
      <div class="card p-4">
        <p class="text-xs font-semibold text-content-muted uppercase tracking-wide mb-1">Registered</p>
        <p class="text-2xl font-heading font-extrabold text-content-primary">{{ totalDbRegistered.length }}</p>
        <span class="text-xs text-content-muted">Have audition numbers</span>
      </div>
      <div class="card p-4">
        <p class="text-xs font-semibold text-content-muted uppercase tracking-wide mb-1">Not Shown Up</p>
        <p class="text-2xl font-heading font-extrabold" :class="totalNotShownUp > 0 ? 'text-amber-300' : 'text-content-primary'">
          {{ totalNotShownUp }}
        </p>
        <span class="text-xs text-content-muted">Verified but no audition number yet</span>
      </div>
    </div>

    <!-- Not Shown Up panel -->
    <div class="space-y-2 mb-6">
      <div v-if="notShownUpList.length > 0" class="card overflow-hidden panel-warning">
        <button
          @click="expandedPeople.has('notShownUp') ? expandedPeople.delete('notShownUp') : expandedPeople.add('notShownUp'); expandedPeople = new Set(expandedPeople)"
          :aria-expanded="expandedPeople.has('notShownUp')"
          class="w-full flex items-center justify-between px-5 py-4 bg-surface-900/40 hover:bg-surface-700/60 transition-colors duration-150 text-left"
        >
          <div class="flex items-center gap-3">
            <div class="icon-wrap w-7 h-7 rounded-lg bg-surface-700 flex items-center justify-center shrink-0">
              <i class="pi pi-clock text-amber-300 text-xs"></i>
            </div>
            <span class="font-heading font-bold text-content-secondary">Not Shown Up</span>
            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-surface-700 text-amber-300 border border-amber-900/30">
              {{ notShownUpList.length }}
            </span>
          </div>
          <i class="pi pi-chevron-down text-content-muted text-xs transition-transform duration-200"
             :class="{ 'rotate-180': expandedPeople.has('notShownUp') }"></i>
        </button>
        <div v-if="expandedPeople.has('notShownUp')" class="px-4 pb-4 border-t border-surface-600/30 pt-4">
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
                ><i class="pi pi-check text-xs"></i> Email Sent</span>
              </div>
              <div class="flex flex-wrap gap-1">
                <span v-for="g in p.genres" :key="g"
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
      <p v-else class="text-sm text-teal-300 flex items-center gap-2 px-1">
        <i class="pi pi-check-circle"></i> All verified participants have shown up
      </p>
    </div>

    <!-- Registered panel -->
    <div v-if="registeredList.length > 0" class="card overflow-hidden panel-success">
      <button
        @click="expandedPeople.has('registered') ? expandedPeople.delete('registered') : expandedPeople.add('registered'); expandedPeople = new Set(expandedPeople)"
        :aria-expanded="expandedPeople.has('registered')"
        class="w-full flex items-center justify-between px-5 py-4 bg-surface-900/40 hover:bg-surface-700/60 transition-colors duration-150 text-left"
      >
        <div class="flex items-center gap-3">
          <div class="icon-wrap w-7 h-7 rounded-lg bg-surface-700 flex items-center justify-center shrink-0">
            <i class="pi pi-check-circle text-teal-400 text-xs"></i>
          </div>
          <span class="font-heading font-bold text-content-secondary">Registered</span>
          <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-surface-700 text-teal-300 border border-teal-900/30">
            {{ registeredList.length }}
          </span>
        </div>
        <i class="pi pi-chevron-down text-content-muted text-xs transition-transform duration-200"
           :class="{ 'rotate-180': expandedPeople.has('registered') }"></i>
      </button>
      <div v-if="expandedPeople.has('registered')" class="px-5 pb-4 border-t border-surface-600/30 pt-4">
        <!-- Search + genre filter -->
        <div class="flex gap-2 mb-3">
          <div class="relative flex-1">
            <i class="pi pi-search absolute left-3 top-1/2 -translate-y-1/2 text-content-muted text-xs pointer-events-none"></i>
            <input
              v-model="registeredSearch"
              type="text"
              placeholder="Search by name or genre…"
              autocomplete="off"
              class="w-full pl-8 pr-8 py-2 rounded-lg border border-surface-600 bg-surface-900 text-sm text-content-primary placeholder-content-muted
                     focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors"
            />
            <button v-if="registeredSearch" @click="registeredSearch = ''"
              class="absolute right-2.5 top-1/2 -translate-y-1/2 text-content-muted hover:text-content-secondary transition-colors">
              <i class="pi pi-times text-xs"></i>
            </button>
          </div>
          <select
            v-model="registeredGenreFilter"
            class="px-3 py-2 rounded-lg border border-surface-600 bg-surface-900 text-sm text-content-secondary
                   focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors"
          >
            <option value="">All genres</option>
            <option v-for="g in registeredGenreOptions" :key="g" :value="g">{{ g }}</option>
          </select>
        </div>
        <!-- Result count -->
        <p v-if="registeredSearch || registeredGenreFilter" class="text-xs text-content-muted mb-2">
          {{ filteredRegisteredList.length }} result{{ filteredRegisteredList.length !== 1 ? 's' : '' }}
        </p>
        <div class="space-y-2">
          <div
            v-for="p in paginatedRegisteredList"
            :key="p.name"
            class="flex flex-col gap-2 px-3 py-2.5 rounded-xl bg-surface-900 border border-surface-600"
          >
            <div class="flex items-center gap-2 flex-wrap">
              <span class="text-sm font-semibold text-content-secondary">{{ p.name }}</span>
              <span v-if="p.walkin"
                class="shrink-0 inline-flex px-1.5 py-0.5 rounded text-xs font-medium bg-surface-700 text-content-muted border border-surface-600"
              >walk-in</span>
              <span
                v-if="p.walkin && p.referenceCode"
                class="relative shrink-0 inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium bg-surface-800 border border-surface-600 cursor-pointer select-none touch-none"
                @mousedown="revealingRef = p.name" @mouseup="revealingRef = null" @mouseleave="revealingRef = null"
                @touchstart.prevent="revealingRef = p.name" @touchend="revealingRef = null" @touchcancel="revealingRef = null"
              >
                <i class="pi pi-eye text-content-muted" style="font-size:0.65rem"></i>
                <span class="text-content-muted">Ref code</span>
                <span v-if="revealingRef === p.name"
                  class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-4 py-2.5 rounded-xl bg-surface-700 border border-surface-500 shadow-xl whitespace-nowrap z-50 pointer-events-none"
                >
                  <span class="font-source tracking-widest text-primary-400 text-base font-bold">{{ p.referenceCode }}</span>
                  <span class="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-surface-500"></span>
                </span>
              </span>
              <span v-else-if="p.entries.length > 0 && verifiedDbParticipants.find(v => v.participantName === p.name)?.emailSent"
                class="shrink-0 inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-surface-700 text-teal-300 border border-teal-900/30"
              ><i class="pi pi-check text-xs"></i> Email Sent</span>
              <span v-else-if="!p.walkin"
                class="shrink-0 inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs font-medium bg-surface-700 text-amber-300 border border-amber-900/30"
              ><i class="pi pi-clock text-xs"></i> Email Pending</span>
            </div>
            <div class="flex flex-wrap gap-1.5">
              <span v-for="e in p.entries" :key="e.genre"
                class="inline-flex items-center gap-1 px-2.5 py-1 rounded-full bg-surface-800 border border-primary-200 text-sm"
              >
                <span class="text-content-muted capitalize text-xs">{{ e.genre }}</span>
                <span class="font-heading font-extrabold text-primary-400">#{{ e.auditionNumber }}</span>
              </span>
            </div>
            <div v-if="p.memberNames.length" class="flex items-center gap-1.5 text-xs text-content-muted">
              <i class="pi pi-users" style="font-size:0.65rem"></i>
              <span>{{ p.memberNames.join(', ') }}</span>
            </div>
          </div>
        </div>
        <!-- Pagination -->
        <div v-if="registeredTotalPages > 1" class="flex items-center justify-between mt-4 pt-3 border-t border-surface-600/30">
          <span class="text-xs text-content-muted">
            Page {{ registeredPage }} of {{ registeredTotalPages }}
          </span>
          <div class="flex items-center gap-1">
            <button
              @click="registeredPage--"
              :disabled="registeredPage === 1"
              class="w-7 h-7 flex items-center justify-center rounded-lg border border-surface-600 text-content-muted
                     hover:bg-surface-700 hover:text-content-secondary disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            ><i class="pi pi-chevron-left text-xs"></i></button>
            <button
              v-for="n in registeredTotalPages"
              :key="n"
              @click="registeredPage = n"
              :class="n === registeredPage
                ? 'w-7 h-7 flex items-center justify-center rounded-lg text-xs font-semibold bg-primary-500 text-white'
                : 'w-7 h-7 flex items-center justify-center rounded-lg text-xs font-medium border border-surface-600 text-content-muted hover:bg-surface-700 hover:text-content-secondary transition-colors'"
            >{{ n }}</button>
            <button
              @click="registeredPage++"
              :disabled="registeredPage === registeredTotalPages"
              class="w-7 h-7 flex items-center justify-center rounded-lg border border-surface-600 text-content-muted
                     hover:bg-surface-700 hover:text-content-secondary disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
            ><i class="pi pi-chevron-right text-xs"></i></button>
          </div>
        </div>
      </div>
    </div>

  </template>
  <!-- ── END EVENT DAY TAB ──────────────────────────────────────────────────── -->

  </div><!-- end page-container -->

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
          <div
            class="relative"
            :style="adjustSearchResults.length > 0 && adjustSearch !== adjustParticipant ? 'padding-bottom: 200px' : ''"
          >
            <label class="block text-sm font-semibold text-content-secondary mb-1.5">Search Participant</label>
            <div class="relative">
              <input
                v-model="adjustSearch"
                type="text"
                placeholder="Type a name…"
                autocomplete="off"
                class="w-full px-4 py-2.5 pr-9 rounded-xl border border-surface-600 text-sm text-content-primary
                       focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors"
              />
              <button
                v-if="adjustSearch"
                @click="adjustSearch = ''; adjustParticipant = null"
                class="absolute right-2.5 top-1/2 -translate-y-1/2 text-content-muted hover:text-content-secondary transition-colors"
              >
                <i class="pi pi-times text-xs"></i>
              </button>
            </div>
            <!-- Realtime dropdown results -->
            <div
              v-if="adjustSearchResults.length > 0 && adjustSearch !== adjustParticipant"
              class="absolute z-20 left-0 right-0 mt-1 rounded-xl border border-surface-600
                     bg-surface-800 shadow-xl overflow-hidden"
            >
              <button
                v-for="name in adjustSearchResults"
                :key="name"
                @click="adjustParticipant = name; adjustSearch = name"
                class="w-full text-left px-4 py-2.5 text-sm font-medium text-content-secondary
                       hover:bg-primary-500/10 hover:text-primary-400 transition-colors border-b border-surface-700
                       last:border-b-0"
              >
                {{ name }}
              </button>
            </div>
          </div>

          <!-- Selected participant view -->
          <div v-if="adjustParticipant">
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
    :genres="eventGenres.map(g => g.genreName)"
    @update:modelValue="(v) => { if (!v) loadCriteriaForAllGenres(eventGenres) }"
  />
</template>
