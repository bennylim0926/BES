<script setup>
import { ref, onMounted, onUnmounted, watch, computed } from 'vue';
import { RouterLink } from 'vue-router';
import ActionDoneModal from './ActionDoneModal.vue';
import { checkTableExist, getFileId, getResponseDetails, fetchAllGenres, getGenresByEvent, getVerifiedParticipantsByEvent, addJudges, insertEventInTable, linkGenreToEvent, addParticipantToSystem, getSheetSize, getRegisteredParticipantsByEvent, removeParticipantGenre, addGenreToParticipant, getUnverifiedParticipantsDB, verifyPayment, verifyPaymentBatch, updateEventGenreFormat, getEventJudges, addEventJudge, removeEventJudge, getScoringCriteria, fetchAllFolderEvents, fetchAllEvents, getCheckinList, checkInParticipant, addDivision, renameDivision, updateDivisionAliases, deleteDivision, getSheetCategories } from '@/utils/api';
import { setActiveEvent } from '@/utils/auth';
import { useDelay } from '@/utils/utils';
import { createClient, subscribeToChannel, deactivateClient } from '@/utils/websocket';
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
const expandedPeople = ref(new Set()) // 'notShownUp' | 'registered'

const verifiedFormParticipants = ref([])
const verifiedDbParticipants = ref([])
const unverifiedParticipants = ref([])
const selectedUnverified = ref(new Set())
const verifyingParticipantId = ref(null)
const batchVerifying = ref(false)
const checkinList = ref([])
const loadingCheckinList = ref(false)
const checkingInId = ref(null)
const participantsNumBreakdown = ref([])
const totalParticipants = ref(0)

const props = defineProps({
  eventName: String,
  folderID: String,
})

const eventName = ref(props.eventName.split(" ").join("%20"));

const selectedInitGenres = ref([])
const paymentRequired = ref(false)
const sheetCategories = ref([])
const divAliasExpanded = ref(null)
const divAliasInput = ref('')
const divRenameActive = ref(null)
const divRenameInput = ref('')
const divFormatOptions = ['', '1v1', '2v2', '3v3', '4v4', '5v5', '7 to smoke', 'solo']

const showModal = ref(false)
const reloadOnClose = ref(false)
const handleAccept = () => {
  showModal.value = false
  if (reloadOnClose.value) window.location.reload()
}

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
const adjustLoading = ref(false)


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
  if (registeredGenreFilter.value) {
    list = list
      .filter(p => p.entries.some(e => e.genre === registeredGenreFilter.value))
      .map(p => ({
        ...p,
        entries: [
          ...p.entries.filter(e => e.genre === registeredGenreFilter.value),
          ...p.entries.filter(e => e.genre !== registeredGenreFilter.value)
        ]
      }))
      .sort((a, b) => (a.entries[0]?.auditionNumber ?? 0) - (b.entries[0]?.auditionNumber ?? 0))
  }
  return list
})

// Participants with no audition numbers in any genre — eligible for genre changes
const eligibleParticipants = computed(() => {
  const locked = new Set(
    verifiedDbParticipants.value
      .filter(p => p.auditionNumber !== null)
      .map(p => p.participantName)
  )
  const names = new Set(
    verifiedDbParticipants.value
      .filter(p => !locked.has(p.participantName))
      .map(p => p.participantName)
  )
  return [...names].sort()
})

const adjustSearchResults = computed(() => {
  const q = adjustSearch.value.trim().toLowerCase()
  if (!q) return eligibleParticipants.value
  return eligibleParticipants.value.filter(n => n.toLowerCase().includes(q))
})

const adjustParticipantGenres = computed(() =>
  verifiedDbParticipants.value.filter(p => p.participantName === adjustParticipant.value)
)

const adjustParticipantLocked = computed(() =>
  adjustParticipantGenres.value.some(p => p.auditionNumber !== null)
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

const onSubmit = async () => {
  if (loading.value) return
  if (selectedInitGenres.value.length == 0) {
    openModal("Missing Genres", "Please add at least one genre.", "warning")
    return
  }
  loading.value = true
  await addJudges(inputs.value)
  await insertEventInTable(props.eventName, paymentRequired.value)
  const divisions = selectedInitGenres.value.map(g => ({ name: g.genreName, format: null, genreId: g.id }))
  const resp = await linkGenreToEvent(props.eventName, divisions)
  if (!resp) { loading.value = false; return }
  resp.json().then(async result => {
    loading.value = false
    if (resp.ok) {
      if (!dbEventId.value) {
        const dbEvents = await fetchAllEvents() ?? []
        const dbEvent = dbEvents.find(e => e.name === props.eventName)
        if (dbEvent) dbEventId.value = dbEvent.id
      }
      if (dbEventId.value) setActiveEvent(dbEventId.value, props.eventName, activeFolderID.value)
      reloadOnClose.value = true
      openModal('Event Initialised', 'Divisions created. Configure names, formats and aliases below.', 'success')
    } else {
      openModal('Error', typeof result === 'string' ? result : 'Failed to initialise event.', 'error')
    }
  })
}

const toggleInitGenre = (g, checked) => {
  if (checked) {
    selectedInitGenres.value.push(g)
  } else {
    selectedInitGenres.value = selectedInitGenres.value.filter(s => s.id !== g.id)
  }
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
      const r = typeof result === 'string' ? JSON.parse(result) : result
      const imported = r?.imported ?? r?.IMPORTED ?? 0
      const skipped = r?.skipped ?? r?.SKIPPED ?? 0
      const errors = r?.errors ?? r?.ERRORS ?? []
      let msg = `${imported} participant${imported !== 1 ? 's' : ''} imported`
      if (skipped > 0) msg += `, ${skipped} skipped`
      if (errors.length > 0) msg += `\n\nErrors:\n${errors.join('\n')}`
      openModal('Import Complete', msg, errors.length > 0 ? 'warning' : 'success')
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
  await Promise.all([refreshFromDb(), fetchCheckinList()])
}

const closeAdjustModal = () => {
  showAdjustModal.value = false
  adjustSearch.value = ''
  adjustParticipant.value = null
  adjustParticipantIds.value = { participantId: null, eventId: null }
}

const toggleAdjustGenre = async (genre) => {
  if (adjustParticipantLocked.value || adjustLoading.value) return
  adjustLoading.value = true
  const isEnrolled = adjustParticipantGenres.value.some(p => p.genreName === genre.name)
  if (isEnrolled) {
    const egp = adjustParticipantGenres.value.find(p => p.genreName === genre.name)
    await removeParticipantGenre(egp.participantId, egp.eventId, egp.eventGenreId)
  } else {
    const { participantId, eventId } = adjustParticipantIds.value
    if (participantId && eventId) {
      await addGenreToParticipant(participantId, eventId, genre.name)
    }
  }
  await Promise.all([
    getRegisteredParticipantsByEvent(eventName.value).then(r => { verifiedDbParticipants.value = r }),
    fetchCheckinList()
  ])
  adjustLoading.value = false
}

// ── Scoring criteria (per-genre, for inline display) ────────────────────────
const criteriaByGenre = ref({}) // genreName → array of { id, name, weight }

const loadCriteriaForAllGenres = async (genres) => {
  const map = {}
  await Promise.all(genres.map(async (g) => {
    map[g.name] = await getScoringCriteria(props.eventName, g.name) ?? []
  }))
  criteriaByGenre.value = map
}
// ───────────────────────────────────────────────────────────────────────────

// ── Divisions (post-init) ────────────────────────────────────────────────────
const divisionsByGenre = computed(() => {
  const groups = {}
  for (const div of eventGenres.value) {
    const key = div.genreId ?? 'custom'
    if (!groups[key]) {
      let label = 'Custom'
      if (div.genreId != null && genreOptions.value) {
        const found = genreOptions.value.find(g => g.id === div.genreId)
        if (found) label = found.genreName
      }
      groups[key] = { genreId: key, label, divisions: [] }
    }
    groups[key].divisions.push(div)
  }
  return Object.values(groups)
})

const matchCounts = computed(() => {
  const counts = {}
  const cats = sheetCategories.value.map(s => s.toLowerCase())
  for (const div of eventGenres.value) {
    const names = [div.name.toLowerCase()]
    if (div.sheetAliases) {
      names.push(...div.sheetAliases.split(',').map(a => a.trim().toLowerCase()).filter(Boolean))
    }
    let count = 0
    for (const cat of cats) {
      if (names.some(n => cat.includes(n))) count++
    }
    counts[div.eventGenreId] = count
  }
  return counts
})

const unmatchedSheetValues = computed(() => {
  if (!sheetCategories.value.length) return []
  const matched = new Set()
  for (const div of eventGenres.value) {
    const names = [div.name.toLowerCase()]
    if (div.sheetAliases) {
      names.push(...div.sheetAliases.split(',').map(a => a.trim().toLowerCase()).filter(Boolean))
    }
    for (const cat of sheetCategories.value) {
      if (names.some(n => cat.toLowerCase().includes(n))) matched.add(cat.toLowerCase())
    }
  }
  const unique = [...new Set(sheetCategories.value.map(s => s.toLowerCase()))]
  return unique.filter(v => !matched.has(v))
})

const loadSheetCategories = async () => {
  if (fileId.value) {
    sheetCategories.value = await getSheetCategories(fileId.value)
  }
}

const saveDivisionName = async (div) => {
  await renameDivision(props.eventName, div.eventGenreId, divRenameInput.value)
  div.name = divRenameInput.value
  divRenameActive.value = null
}

const saveDivisionFormat = async (div, format) => {
  await updateEventGenreFormat(props.eventName, div.eventGenreId, format || null)
  div.format = format || null
}

const addAlias = async (div) => {
  if (!divAliasInput.value.trim()) return
  const existing = div.sheetAliases ? div.sheetAliases.split(',').map(s => s.trim()).filter(Boolean) : []
  existing.push(divAliasInput.value.trim())
  const joined = existing.join(', ')
  await updateDivisionAliases(props.eventName, div.eventGenreId, joined)
  div.sheetAliases = joined
  divAliasInput.value = ''
}

const removeAlias = async (div, alias) => {
  const existing = div.sheetAliases ? div.sheetAliases.split(',').map(s => s.trim()).filter(Boolean) : []
  const updated = existing.filter(a => a !== alias)
  const joined = updated.join(', ')
  await updateDivisionAliases(props.eventName, div.eventGenreId, joined)
  div.sheetAliases = joined
}

const addDivisionToGroup = async (genreId, genreLabel) => {
  await addDivision(props.eventName, '', null, genreId === 'custom' ? null : genreId)
  eventGenres.value = await getGenresByEvent(props.eventName)
}

const removeDivisionFromSection = async (divId) => {
  await deleteDivision(props.eventName, divId)
  eventGenres.value = eventGenres.value.filter(d => d.eventGenreId !== divId)
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
const formatOptions = ['1v1', '2v2', '3v3', '4v4', '5v5', '7 to smoke', 'solo']
const editingFormatFor = ref(null)  // genre name currently being edited
const editingFormatValue = ref('')

const startEditFormat = (genre) => {
  editingFormatFor.value = genre.name
  editingFormatValue.value = genre.format || ''
}

const saveFormat = async (genreName) => {
  const eg = eventGenres.value.find(g => g.name === genreName)
  if (!eg) return
  await updateEventGenreFormat(props.eventName, eg.eventGenreId, editingFormatValue.value || null)
  eventGenres.value = await getGenresByEvent(props.eventName)
  editingFormatFor.value = null
}
// ───────────────────────────────────────────────────────────────────────────


const toggleUnverifiedSelect = (participantId) => {
  if (selectedUnverified.value.has(participantId)) {
    selectedUnverified.value.delete(participantId)
  } else {
    selectedUnverified.value.add(participantId)
  }
  selectedUnverified.value = new Set(selectedUnverified.value)
}

const handleVerifyPayment = async (participant) => {
  verifyingParticipantId.value = participant.participantId
  try {
    await verifyPayment(participant.participantId, participant.eventId)
    unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
    selectedUnverified.value.delete(participant.participantId)
    selectedUnverified.value = new Set(selectedUnverified.value)
  } catch (_e) {
    openModal('Error', 'Failed to verify participant.', 'error')
  }
  verifyingParticipantId.value = null
}

const handleBatchVerifyPayment = async () => {
  if (selectedUnverified.value.size === 0) return
  batchVerifying.value = true
  const list = [...selectedUnverified.value].map(pid => {
    const p = unverifiedParticipants.value.find(x => x.participantId === pid)
    return { participantId: p.participantId, eventId: p.eventId }
  })
  try {
    await verifyPaymentBatch(list)
    unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
    selectedUnverified.value = new Set()
  } catch (_e) {
    openModal('Error', 'Batch verification failed.', 'error')
  }
  batchVerifying.value = false
}

const isCheckedIn = (p) => p.genres.length > 0 && p.genres.every(g => g.auditionNumber !== null)

const sortedCheckinList = computed(() =>
  [...checkinList.value].sort((a, b) => {
    const aIn = isCheckedIn(a)
    const bIn = isCheckedIn(b)
    if (aIn !== bIn) return aIn ? 1 : -1
    return a.label.localeCompare(b.label)
  })
)

const fetchCheckinList = async () => {
  loadingCheckinList.value = true
  try {
    const res = await getCheckinList(eventName.value)
    if (res?.ok) checkinList.value = await res.json()
  } catch (e) {
    console.error(e)
  }
  loadingCheckinList.value = false
}

const checkIn = async (p) => {
  checkingInId.value = p.participantId
  try {
    await checkInParticipant(p.participantId, p.eventId)
    await Promise.all([fetchCheckinList(), refreshFromDb()])
  } catch (e) {
    console.error(e)
  }
  checkingInId.value = null
}

watch(
  fileId,
  async () => {
    if (fileId.value) {
      participantsNumBreakdown.value = await getResponseDetails(fileId.value)
      totalParticipants.value = await getSheetSize(fileId.value) ?? 0
      await loadSheetCategories()
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
  if (eventGenres.value.length > 0) activeGenreTab.value = eventGenres.value[0].name
  await loadCriteriaForAllGenres(eventGenres.value)
  eventJudges.value = await getEventJudges(props.eventName)
  if (tableExist.value) {
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
    verifiedFormParticipants.value = await getVerifiedParticipantsByEvent(eventName.value)
    unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
    await fetchCheckinList()
  }
  await useDelay().wait(2500)
  onStartLoading.value = false
  if (tableExist.value) {
    refreshInterval = setInterval(refreshFromDb, 30000)
    wsClient = createClient()
    let refreshPending = false
    subscribeToChannel(wsClient, '/topic/audition/', (msg) => {
      if (msg.eventName !== props.eventName) return
      const participant = checkinList.value.find(p => p.participantId === msg.participantId)
      if (participant) {
        const genre = participant.genres.find(g => g.genreName === msg.genre)
        if (genre) genre.auditionNumber = msg.auditionNumber
      }
      if (!refreshPending) {
        refreshPending = true
        refreshFromDb().finally(() => { refreshPending = false })
      }
    })
    subscribeToChannel(wsClient, '/topic/walkin/', (msg) => {
      if (msg.eventName !== props.eventName) return
      fetchCheckinList()
    })
  }
})

onUnmounted(() => {
  if (refreshInterval) clearInterval(refreshInterval)
  if (wsClient) deactivateClient(wsClient)
})
</script>

<template>
  <div class="page-container relative">
    <div class="color-bleed"></div>
    <div class="relative z-10">

    <!-- Page header -->
    <div class="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4 mb-6">
      <div>
        <div class="type-page-title mb-1">{{ props.eventName }}</div>
        <p class="type-label text-content-muted">Event overview and participant management</p>
      </div>
      <div class="flex flex-wrap gap-2 self-start">
        <button
          @click="showWalkInForm = true"
          class="flex items-center gap-2 px-4 py-2 para-chip type-label border-accent"
        >
          <i class="pi pi-user-plus text-sm"></i>
          Add Walk-in
        </button>
        <button
          @click="showAdjustModal = true"
          class="flex items-center gap-2 px-4 py-2 para-chip type-label border-accent"
        >
          <i class="pi pi-sliders-h text-sm"></i>
          Genre Entries
        </button>
        <RouterLink
          to="/event/audition-number"
          class="flex items-center gap-2 px-4 py-2 para-chip type-label border-accent"
        >
          <i class="pi pi-hashtag text-sm"></i>
          Audition Screen
        </RouterLink>
        <button
          @click="refreshParticipant"
          :disabled="loading"
          class="flex items-center gap-2 px-4 py-2 bg-accent para-chip type-label disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <i class="pi text-sm" :class="loading ? 'pi-spinner pi-spin' : 'pi-cloud-download'"></i>
          {{ loading ? 'Importing…' : 'Import from Sheets' }}
        </button>
      </div>
    </div>

    <!-- Tab toggle -->
    <div class="flex gap-2 mb-6">
      <button
        @click="activeTab = 'setup'"
        class="para-chip-sm px-5 py-2 type-label transition-all duration-150"
        :class="activeTab === 'setup'
          ? 'text-accent border-accent'
          : 'text-content-muted hover:text-content-primary'"
      >
        <i class="pi pi-cog text-xs mr-1.5"></i>
        Setup
      </button>
      <button
        @click="activeTab = 'event-day'"
        class="para-chip-sm px-5 py-2 type-label transition-all duration-150"
        :class="activeTab === 'event-day'
          ? 'text-accent border-accent'
          : 'text-content-muted hover:text-content-primary'"
      >
        <i class="pi pi-calendar text-xs mr-1.5"></i>
        Event Day
        <span
          v-if="totalNotShownUp > 0"
          class="ml-1.5 inline-flex items-center justify-center badge-warning px-1.5 py-0.5 text-[10px]"
        >{{ totalNotShownUp }}</span>
        <span
          v-else-if="unverifiedParticipants.length > 0 && activeTab !== 'event-day'"
          class="ml-1.5 inline-flex items-center justify-center badge-danger px-1.5 py-0.5 text-[10px]"
        >{{ unverifiedParticipants.length }}</span>
      </button>
    </div>

    <!-- ── SETUP TAB ─────────────────────────────────────────────────────── -->
    <template v-if="activeTab === 'setup'">

    <!-- Stat strip -->
    <div class="grid grid-cols-2 sm:grid-cols-3 gap-3 mb-6">
      <div class="stat-card relative">
        <div class="corner-bar-tl"></div>
        <div class="type-stat">{{ (totalParticipants || 0) + totalWalkIn }}</div>
        <div class="type-label">Total</div>
        <div class="flex gap-3 mt-1">
          <span class="type-label text-content-muted">Form: {{ totalParticipants || 0 }}</span>
          <span class="type-label text-content-muted">Walk-in: {{ totalWalkIn }}</span>
        </div>
      </div>
      <div class="stat-card relative">
        <div class="corner-bar-tl"></div>
        <div class="type-stat" :class="unverifiedParticipants.length > 0 ? 'text-red-400' : ''">{{ unverifiedParticipants.length }}</div>
        <div class="type-label">Pending Verification</div>
        <div class="type-label text-content-muted mt-1">{{ totalVerified }} verified</div>
      </div>
    </div>

    <!-- Unverified (payment pending — DB-driven) -->
      <div v-if="unverifiedParticipants.length > 0" class="card-hover p-4 relative">
        <div class="corner-bar-tl"></div>
        <button
          @click="expandedPeople.has('unverified') ? expandedPeople.delete('unverified') : expandedPeople.add('unverified'); expandedPeople = new Set(expandedPeople)"
          :aria-expanded="expandedPeople.has('unverified')"
          aria-controls="panel-unverified"
          class="w-full flex items-center justify-between text-left"
        >
          <div class="flex items-center gap-3">
            <i class="pi pi-exclamation-circle text-rose-300 text-xs"></i>
            <span class="type-body text-content-secondary truncate">Awaiting Payment Verification</span>
            <span class="badge-danger">{{ unverifiedParticipants.length }}</span>
          </div>
          <i class="pi pi-chevron-down text-content-muted text-xs transition-transform duration-200"
             :class="{ 'rotate-180': expandedPeople.has('unverified') }"></i>
        </button>
        <div v-if="expandedPeople.has('unverified')" id="panel-unverified" class="mt-4 pt-4 border-t border-surface-600/30">
          <!-- Batch action bar -->
          <div class="flex items-center justify-between mb-3">
            <p class="text-xs text-content-muted flex items-center gap-1.5">
              <i class="pi pi-info-circle"></i>
              Select participants and click "Verify Batch", or verify individually.
            </p>
            <button
              v-if="selectedUnverified.size > 0"
              @click="handleBatchVerifyPayment"
              :disabled="batchVerifying"
              class="bg-accent para-chip-sm px-3 py-1.5 type-label disabled:opacity-50"
            >
              <i class="pi text-xs" :class="batchVerifying ? 'pi-spinner pi-spin' : 'pi-check'"></i>
              {{ batchVerifying ? 'Verifying…' : `Verify Batch (${selectedUnverified.size})` }}
            </button>
          </div>
          <div class="space-y-2">
            <div
              v-for="p in unverifiedParticipants"
              :key="p.participantId"
              class="para-chip p-3"
              :class="selectedUnverified.has(p.participantId) ? 'border-accent' : ''"
            >
              <div class="flex items-start gap-3">
                <input
                  type="checkbox"
                  :checked="selectedUnverified.has(p.participantId)"
                  @change="toggleUnverifiedSelect(p.participantId)"
                  class="shrink-0 mt-0.5"
                />
                <div class="flex-1 min-w-0">
                  <span class="type-body text-content-secondary block">{{ p.name }}</span>
                  <div class="flex flex-wrap gap-1 mt-1">
                    <span
                      v-for="g in p.genres"
                      :key="g"
                      class="badge-neutral text-xs"
                    >{{ g }}</span>
                  </div>
                </div>
              </div>
              <div class="flex items-center gap-2 justify-end mt-2">
                <a
                  v-if="p.screenshotUrl && /^https?:\/\//.test(p.screenshotUrl)"
                  :href="p.screenshotUrl"
                  target="_blank"
                  class="para-chip-sm px-2.5 py-1 type-label"
                >
                  <i class="pi pi-image text-xs"></i>
                  View Receipt
                </a>
                <button
                  @click="handleVerifyPayment(p)"
                  :disabled="verifyingParticipantId === p.participantId"
                  class="bg-accent para-chip-sm px-2.5 py-1 type-label disabled:opacity-50"
                >
                  <i class="pi text-xs" :class="verifyingParticipantId === p.participantId ? 'pi-spinner pi-spin' : 'pi-check'"></i>
                  {{ verifyingParticipantId === p.participantId ? 'Verifying…' : 'Verify Payment' }}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>


    <!-- Setup section (when no table exists) -->
    <div v-if="!tableExist" class="card-hover p-6 relative">
      <div class="corner-bar-tl"></div>
      <div class="flex items-center gap-3 mb-6">
        <i class="pi pi-exclamation-triangle text-amber-300 text-sm"></i>
        <div>
          <div class="type-body text-content-secondary">Event Setup Required</div>
          <p class="type-label text-content-muted">No record found for this event. Select genres to initialise.</p>
        </div>
      </div>

      <div class="section-rule mb-4">
        <span class="section-rule-label">Genres / Categories</span>
        <div class="section-rule-line"></div>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2 mb-4">
        <div
          v-for="g in genreOptions"
          :key="g.genreName"
          class="para-chip-sm p-3 transition-all duration-150"
          :class="selectedInitGenres.some(s => s.id === g.id) ? 'border-accent' : ''"
        >
          <label class="flex items-center gap-2.5 cursor-pointer">
            <input
              type="checkbox"
              :id="g.genreName"
              :checked="selectedInitGenres.some(s => s.id === g.id)"
              @change="toggleInitGenre(g, $event.target.checked)"
              class="w-4 h-4"
            />
            <span class="type-body capitalize">{{ g.genreName }}</span>
          </label>
        </div>
      </div>

      <!-- Payment required toggle -->
      <div class="mb-6">
        <label
          class="flex items-center gap-3 px-4 py-3 para-chip cursor-pointer transition-all duration-150 w-fit"
          :class="paymentRequired ? 'border-accent' : ''"
        >
          <input type="checkbox" v-model="paymentRequired" class="w-4 h-4" />
          <div>
            <span class="type-body">Payment Required</span>
            <p class="type-label mt-0.5" :class="paymentRequired ? 'text-amber-400' : 'text-content-muted'">
              {{ paymentRequired ? 'Participants will be placed in an unverified queue until you manually verify payment in-app.' : 'All participants will be auto-verified on import.' }}
            </p>
          </div>
        </label>
      </div>

      <div class="flex justify-end">
        <button
          @click="onSubmit"
          :disabled="loading"
          class="bg-accent para-chip type-label disabled:opacity-50 px-5 py-2 flex items-center gap-2"
        >
          <i class="pi text-xs" :class="loading ? 'pi-spinner pi-spin' : 'pi-database'"></i>
          {{ loading ? 'Setting up…' : 'Initialise Event' }}
        </button>
      </div>
    </div>

  <!-- Divisions (post-init) — grouped by parent genre -->
  <div v-if="tableExist && eventGenres.length > 0" class="card-hover p-4 relative mt-6">
    <div class="corner-bar-tl"></div>

    <div class="section-rule mb-4">
      <span class="section-rule-label">Divisions</span>
      <div class="section-rule-line"></div>
    </div>

    <div class="space-y-4">
      <div v-for="group in divisionsByGenre" :key="group.genreId" class="space-y-2">
        <!-- Genre group header -->
        <div class="flex items-center gap-2">
          <span class="type-section-header text-content-secondary">{{ group.label }}</span>
          <span class="badge-neutral type-label px-2 py-0.5 text-xs">{{ group.divisions.length }}</span>
        </div>

        <!-- Division rows -->
        <div v-for="div in group.divisions" :key="div.eventGenreId">
          <div
            class="para-chip p-3 flex flex-col gap-2"
            :class="sheetCategories.length > 0 && (matchCounts[div.eventGenreId] || 0) > 0
              ? 'border-l-[3px] border-l-emerald-500'
              : ''"
          >
            <div class="flex items-center gap-2">
              <!-- Division name (click to rename) -->
              <template v-if="divRenameActive !== div.eventGenreId">
                <button
                  @click="divRenameActive = div.eventGenreId; divRenameInput = div.name"
                  class="type-body text-content-secondary hover:text-accent text-left truncate flex-1 transition-colors"
                >{{ div.name }}</button>
              </template>
              <template v-else>
                <input
                  v-model="divRenameInput"
                  type="text"
                  class="input-base flex-1 min-w-0"
                  placeholder="Division name"
                  @keyup.enter="saveDivisionName(div)"
                  @keyup.escape="divRenameActive = null"
                  @blur="saveDivisionName(div)"
                />
              </template>

              <!-- Match count badge -->
              <div
                v-if="sheetCategories.length > 0"
                class="flex items-center gap-1 shrink-0"
                :class="(matchCounts[div.eventGenreId] || 0) > 0 ? 'text-emerald-400' : 'text-content-muted'"
              >
                <span
                  class="inline-block w-1.5 h-1.5 rounded-full shrink-0"
                  :class="(matchCounts[div.eventGenreId] || 0) > 0
                    ? 'bg-emerald-400'
                    : 'bg-content-muted'"
                  :style="(matchCounts[div.eventGenreId] || 0) > 0 ? 'box-shadow: 0 0 6px rgba(52,211,153,0.6)' : ''"
                ></span>
                <span class="type-label text-xs">{{ matchCounts[div.eventGenreId] || 0 }}</span>
              </div>

              <!-- Format dropdown -->
              <select
                :value="div.format || ''"
                @change="saveDivisionFormat(div, $event.target.value)"
                class="shrink-0 text-xs px-2 py-1 para-chip-sm bg-transparent text-content-secondary"
              >
                <option value="">No format</option>
                <template v-for="opt in divFormatOptions" :key="opt">
                  <option v-if="opt" :value="opt">{{ opt }}</option>
                </template>
              </select>

              <!-- Alias toggle -->
              <button
                @click="divAliasExpanded = divAliasExpanded === div.eventGenreId ? null : div.eventGenreId"
                class="para-chip-sm px-2 py-1 type-label text-content-muted hover:text-accent transition-colors"
                title="Sheet aliases"
              ><i class="pi pi-tags text-xs"></i></button>

              <!-- Remove -->
              <button
                @click="removeDivisionFromSection(div.eventGenreId)"
                class="para-chip-sm px-2 py-1 type-label text-content-muted hover:text-red-400 transition-colors"
                title="Remove division"
              ><i class="pi pi-times text-xs"></i></button>
            </div>

            <!-- Alias chips (always visible if exist) -->
            <div v-if="div.sheetAliases && div.sheetAliases.split(',').map(s => s.trim()).filter(Boolean).length" class="flex flex-wrap gap-1">
              <span
                v-for="alias in div.sheetAliases.split(',').map(s => s.trim()).filter(Boolean)"
                :key="alias"
                class="type-label text-content-muted bg-surface-600/30 px-1.5 py-0.5 para-chip text-xs normal-case flex items-center gap-1"
              >
                {{ alias }}
                <button @click="removeAlias(div, alias)" class="text-content-muted hover:text-red-400 leading-none"><i class="pi pi-times" style="font-size:0.5rem"></i></button>
              </span>
            </div>

            <!-- Add alias input row -->
            <div v-if="divAliasExpanded === div.eventGenreId" class="flex gap-1.5 items-center">
              <input
                v-model="divAliasInput"
                type="text"
                placeholder="Add alias…"
                class="input-base flex-1 text-xs py-1"
                @keyup.enter="addAlias(div)"
              />
              <button @click="addAlias(div)" class="para-chip-sm px-2 py-1 type-label text-accent"><i class="pi pi-plus text-xs"></i></button>
            </div>
          </div>
        </div>

        <!-- Add division to group -->
        <button
          @click="addDivisionToGroup(group.genreId, group.label)"
          class="para-chip-sm px-3 py-1.5 type-label text-content-muted hover:text-accent transition-all"
        >+ Add {{ group.label }} division</button>
      </div>
    </div>

    <!-- Unmatched sheet values strip -->
    <div
      v-if="unmatchedSheetValues.length > 0"
      class="mt-4 pt-3 border-t border-surface-600/30"
    >
      <div class="flex items-center gap-2 mb-2">
        <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" style="box-shadow: 0 0 6px rgba(251,191,36,0.6)"></span>
        <span class="type-label text-amber-400 text-xs">Unmatched sheet values</span>
      </div>
      <div class="flex flex-wrap gap-1">
        <span
          v-for="val in unmatchedSheetValues"
          :key="val"
          class="type-label text-content-muted bg-amber-950/30 px-1.5 py-0.5 para-chip text-xs normal-case"
        >{{ val }}</span>
      </div>
    </div>
  </div>

  <!-- Genre Configuration — unified per-genre tabs (participants, format, criteria, judges) -->
  <div v-if="tableExist && eventGenres.length > 0" class="card-hover p-4 relative mt-6">
    <div class="corner-bar-tl"></div>

    <div class="section-rule mb-4">
      <span class="section-rule-label">Genre Configuration</span>
      <div class="section-rule-line"></div>
    </div>

    <!-- Genre tab bar -->
    <div class="flex flex-wrap gap-2 mb-4">
      <button
        v-for="g in eventGenres"
        :key="g.name"
        @click="activeGenreTab = g.name"
        class="para-chip-sm px-4 py-2 type-label transition-all duration-150"
        :class="activeGenreTab === g.name
          ? 'text-accent border-accent'
          : 'text-content-muted hover:text-content-primary'"
      >
        {{ g.name }}
        <span
          v-if="completeBreakdown.find(b => b.genre === normalizeGenreName(g.name))"
          class="ml-1.5 text-xs font-normal opacity-60"
        >{{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).total }}</span>
      </button>
    </div>

    <!-- Tab content for each genre -->
    <template v-for="g in eventGenres" :key="g.name + '-content'">
      <div v-if="activeGenreTab === g.name" class="p-5 space-y-4">

        <!-- Participant counts (only when data available) -->
        <template v-if="completeBreakdown.find(b => b.genre === normalizeGenreName(g.name))">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="badge-neutral text-xs">Total: {{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).total }}</span>
            <span class="badge-success">
              Reg: {{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).registered }}
            </span>
            <span
              v-if="completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).unregistered > 0"
              class="badge-danger"
            >Unreg: {{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).unregistered }}</span>
          </div>

          <!-- Unregistered list -->
          <div v-if="getUnregistered(normalizeGenreName(g.name)).unregistered.length > 0">
            <p class="text-xs font-semibold text-content-muted uppercase tracking-wide mb-1.5">Unregistered Participants</p>
            <div class="flex flex-wrap gap-2">
              <span
                v-for="p in getUnregistered(normalizeGenreName(g.name)).unregistered"
                :key="p.participantName"
                class="badge-danger font-source"
              >{{ p.participantName }}</span>
            </div>
          </div>
          <div v-else class="flex items-center gap-2 type-body text-emerald-400">
            <i class="pi pi-check-circle"></i>
            <span>All participants registered</span>
          </div>

          <div class="h-px bg-surface-700/40"></div>
        </template>

        <!-- Format + Scoring Criteria -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">

          <!-- Battle Format -->
          <div class="flex flex-col gap-2 p-3 para-chip">
            <p class="type-label text-content-muted">Battle Format</p>
            <template v-if="editingFormatFor !== g.name">
              <div class="flex items-center justify-between">
                <span class="type-body" :class="g.format ? 'text-accent' : 'text-content-muted'">
                  {{ g.format || 'No format' }}
                </span>
                <button
                  @click="startEditFormat(g)"
                  class="para-chip-sm px-2.5 py-1 type-label"
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
                <button @click="saveFormat(g.name)" class="text-xs px-2.5 py-1 rounded-lg bg-primary-600 text-white hover:bg-primary-700 transition-all font-semibold">Save</button>
                <button @click="editingFormatFor = null" class="text-xs px-2 py-1 rounded-lg border border-surface-600/50 text-content-muted hover:border-surface-500 transition-all">Cancel</button>
              </div>
            </template>
          </div>

          <!-- Scoring Criteria -->
          <div class="flex flex-col gap-2 p-3 para-chip">
            <div class="flex items-center justify-between">
              <p class="type-label text-content-muted">Scoring Criteria</p>
              <button
                @click="showCriteriaModal = true"
                class="para-chip-sm px-2.5 py-1 type-label"
              ><i class="pi pi-sliders-h" style="font-size:0.65rem"></i> Configure</button>
            </div>
            <template v-if="criteriaByGenre[g.name]?.length">
              <div class="flex flex-wrap gap-1.5">
                <span
                  v-for="c in criteriaByGenre[g.name]"
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
          <div class="flex flex-wrap items-center gap-2">
            <div
              v-for="j in eventJudges"
              :key="j.judgeId"
              class="flex items-center gap-2 para-chip px-2.5 py-1"
            >
              <i class="pi pi-user text-content-muted text-xs shrink-0"></i>
              <span class="type-body text-content-secondary">{{ j.judgeName }}</span>
              <button
                @click="submitRemoveJudge(j.judgeId)"
                class="type-label text-content-muted hover:text-content-primary transition-colors"
                title="Remove"
              ><i class="pi pi-times text-xs"></i></button>
            </div>
            <div class="flex items-center gap-1.5 para-chip px-2.5 py-1">
              <input
                v-model="addJudgeInput"
                type="text"
                placeholder="Add judge…"
                autocomplete="off"
                class="bg-transparent type-body placeholder:text-content-muted focus:outline-none w-28"
                @keyup.enter="submitAddJudge"
              />
              <button
                @click="submitAddJudge"
                class="type-label text-accent hover:opacity-80 transition-opacity shrink-0"
                title="Add"
              ><i class="pi pi-plus text-xs"></i></button>
            </div>
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
      <div class="stat-card relative">
        <div class="corner-bar-tl"></div>
        <div class="type-stat">{{ totalDbRegistered.length }}</div>
        <div class="type-label">Registered</div>
        <div class="type-label text-content-muted mt-1">Have audition numbers</div>
      </div>
      <div class="stat-card relative">
        <div class="corner-bar-tl"></div>
        <div class="type-stat" :class="totalNotShownUp > 0 ? 'text-amber-400' : ''">{{ totalNotShownUp }}</div>
        <div class="type-label">Not Shown Up</div>
        <div class="type-label text-content-muted mt-1">Verified but no audition number yet</div>
      </div>
    </div>

    <!-- Side by side: Check-In + Registered -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">

      <!-- Check-In card -->
      <div class="card-hover p-4 relative flex flex-col h-[520px]">
        <div class="corner-bar-tl"></div>
        <div class="flex items-center justify-between mb-4 shrink-0">
          <div class="flex items-center gap-3">
            <i class="pi pi-users text-content-muted text-xs"></i>
            <span class="type-body text-content-secondary">Check-In</span>
            <span class="badge-warning">{{ checkinList.filter(p => !isCheckedIn(p)).length }} pending</span>
          </div>
        </div>
        <div class="flex-1 overflow-y-auto space-y-2 min-h-0">
          <div v-if="loadingCheckinList" class="flex items-center justify-center h-full type-label text-content-muted">
            <i class="pi pi-spinner pi-spin mr-2"></i> Loading…
          </div>
          <div v-else-if="checkinList.length === 0" class="flex items-center justify-center h-full type-label text-content-muted">
            No participants found
          </div>
          <template v-else>
            <div v-for="p in sortedCheckinList" :key="p.participantId"
              class="para-chip p-3 transition-colors"
              :class="isCheckedIn(p) ? 'opacity-50' : ''"
            >
              <div class="flex-1 min-w-0">
                <p class="type-body text-content-secondary truncate">{{ p.label }}</p>
                <div class="flex flex-wrap gap-1 mt-0.5">
                  <span v-for="g in p.genres" :key="g.genreName" class="badge-neutral capitalize">
                    {{ g.genreName }}<span v-if="g.auditionNumber !== null" style="color:var(--accent-color);margin-left:0.25rem">#{{ g.auditionNumber }}</span>
                  </span>
                </div>
              </div>
              <div class="flex items-center gap-2 mt-2 justify-end">
                <button v-if="!isCheckedIn(p)" @click="checkIn(p)" :disabled="checkingInId === p.participantId"
                  class="bg-accent para-chip-sm px-3 py-1.5 type-label disabled:opacity-50"
                >
                  <i class="pi text-xs" :class="checkingInId === p.participantId ? 'pi-spinner pi-spin' : 'pi-check'"></i>
                  {{ checkingInId === p.participantId ? '…' : 'Check In' }}
                </button>
                <i v-else class="pi pi-check-circle text-emerald-400 text-sm"></i>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- Registered card -->
      <div class="card-hover p-4 relative flex flex-col h-[520px]">
        <div class="corner-bar-tl"></div>
        <div class="flex items-center justify-between mb-4 shrink-0">
          <div class="flex items-center gap-3">
            <i class="pi pi-check-circle text-emerald-400 text-xs"></i>
            <span class="type-body text-content-secondary">Registered</span>
            <span class="badge-success">{{ registeredList.length }}</span>
          </div>
        </div>
        <div class="mb-4 shrink-0">
          <div class="flex gap-2">
            <div class="relative flex-1">
              <i class="pi pi-search absolute left-4 top-1/2 -translate-y-1/2 text-content-muted text-xs pointer-events-none"></i>
              <input v-model="registeredSearch" type="text" placeholder="Search by name or genre…" autocomplete="off"
                class="input-base pr-8"
                style="padding-left: 2.25rem"
              />
              <button v-if="registeredSearch" @click="registeredSearch = ''"
                class="absolute right-2.5 top-1/2 -translate-y-1/2 text-content-muted hover:text-content-secondary transition-colors">
                <i class="pi pi-times text-xs"></i>
              </button>
            </div>
            <select v-model="registeredGenreFilter"
              class="input-base w-auto"
            >
              <option value="">All genres</option>
              <option v-for="g in registeredGenreOptions" :key="g" :value="g">{{ g }}</option>
            </select>
          </div>
          <p v-if="registeredSearch || registeredGenreFilter" class="type-label text-content-muted mt-2">
            {{ filteredRegisteredList.length }} result{{ filteredRegisteredList.length !== 1 ? 's' : '' }}
          </p>
        </div>
        <div class="flex-1 overflow-y-auto space-y-2 min-h-0">
          <div v-if="registeredList.length === 0" class="flex items-center justify-center h-full type-label text-content-muted">
            No registered participants yet
          </div>
          <div v-else-if="filteredRegisteredList.length === 0" class="flex items-center justify-center h-full type-label text-content-muted">
            No results match your search
          </div>
          <template v-else>
            <div v-for="p in filteredRegisteredList" :key="p.name"
              class="para-chip p-3"
            >
              <div class="flex items-center gap-2 flex-wrap">
                <span class="type-body text-content-secondary">{{ p.name }}</span>
                <span v-if="p.walkin" class="badge-neutral">walk-in</span>
                <span v-if="p.walkin && p.referenceCode"
                  class="relative shrink-0 inline-flex items-center gap-1 px-2 py-0.5 badge-neutral cursor-pointer select-none touch-none"
                  @mousedown="revealingRef = p.name" @mouseup="revealingRef = null" @mouseleave="revealingRef = null"
                  @touchstart.prevent="revealingRef = p.name" @touchend="revealingRef = null" @touchcancel="revealingRef = null"
                >
                  <i class="pi pi-eye text-content-muted" style="font-size:0.65rem"></i>
                  <span class="text-content-muted">Ref code</span>
                  <span v-if="revealingRef === p.name"
                    class="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 px-4 py-2.5 para-chip shadow-xl whitespace-nowrap z-50 pointer-events-none"
                  >
                    <span class="font-source tracking-widest text-accent text-base font-bold">{{ p.referenceCode }}</span>
                    <span class="absolute top-full left-1/2 -translate-x-1/2 border-4 border-transparent border-t-surface-500"></span>
                  </span>
                </span>
              </div>
              <div class="flex flex-wrap gap-1.5 mt-1">
                <span v-for="e in p.entries" :key="e.genre" class="badge-neutral capitalize">
                  {{ e.genre }}<span style="color:var(--accent-color);margin-left:0.25rem">#{{ e.auditionNumber }}</span>
                </span>
              </div>
              <div v-if="p.memberNames.length" class="flex items-center gap-1.5 type-label text-content-muted mt-0.5">
                <i class="pi pi-users" style="font-size:0.65rem"></i>
                <span>{{ p.memberNames.join(', ') }}</span>
              </div>
            </div>
          </template>
        </div>
      </div>

    </div>

  </template>
  <!-- ── END EVENT DAY TAB ──────────────────────────────────────────────────── -->

  </div><!-- end relative z-10 -->
  </div><!-- end page-container -->

  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    :variant="modalVariant"
    @accept="handleAccept"
    @close="handleAccept"
  >
    <p class="type-body text-content-secondary">{{ modalMessage }}</p>
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
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="showAdjustModal"
        class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
      >
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="closeAdjustModal" />
        <div class="card-hover relative w-full sm:max-w-md flex flex-col" style="max-height: 85vh;">
          <div class="corner-bar-tl"></div>
          <div class="corner-bar-bl"></div>

          <!-- Header -->
          <div class="flex items-center justify-between px-4 py-3 border-b border-surface-600/30 shrink-0">
            <div class="flex items-center gap-2">
              <i class="pi pi-sliders-h text-content-muted text-xs"></i>
              <span class="type-body text-content-primary">Genre Entries</span>
              <span class="badge-neutral type-label">{{ props.eventName }}</span>
            </div>
            <button @click="closeAdjustModal" class="para-chip-sm px-2 py-1 type-label text-content-muted hover:text-content-primary transition-colors">
              <i class="pi pi-times text-xs"></i>
            </button>
          </div>

          <!-- Body -->
          <div class="flex-1 overflow-y-auto p-4 space-y-4 min-h-0">

            <!-- Search + eligible list -->
            <div>
              <div class="flex items-center justify-between mb-1.5">
                <label class="type-label text-content-muted">Eligible Participants</label>
                <span class="type-label text-content-muted">{{ eligibleParticipants.length }} available</span>
              </div>
              <div class="relative mb-2">
                <i class="pi pi-search absolute left-4 top-1/2 -translate-y-1/2 text-content-muted text-xs pointer-events-none"></i>
                <input
                  v-model="adjustSearch"
                  type="text"
                  placeholder="Filter by name…"
                  autocomplete="off"
                  class="input-base"
                  style="padding-left: 2.25rem"
                />
                <button
                  v-if="adjustSearch"
                  @click="adjustSearch = ''; adjustParticipant = null"
                  class="absolute right-2.5 top-1/2 -translate-y-1/2 text-content-muted hover:text-content-secondary transition-colors"
                >
                  <i class="pi pi-times text-xs"></i>
                </button>
              </div>
              <!-- Participant list — always visible, filtered by search -->
              <div v-if="adjustSearchResults.length > 0 && adjustSearch !== adjustParticipant" class="flex flex-wrap gap-1.5">
                <button
                  v-for="name in adjustSearchResults"
                  :key="name"
                  @click="adjustParticipant = name; adjustSearch = name"
                  class="para-chip-sm px-3 py-1.5 type-label transition-all"
                  :class="adjustParticipant === name ? 'text-accent border-[color:var(--accent-color)]' : 'text-content-secondary hover:text-accent'"
                >{{ name }}</button>
              </div>
              <p v-else-if="adjustSearchResults.length === 0 && eligibleParticipants.length === 0" class="type-label text-content-muted px-1">
                No eligible participants — all have audition numbers
              </p>
              <p v-else-if="adjustSearchResults.length === 0" class="type-label text-content-muted px-1">
                No matches
              </p>
            </div>

            <!-- Selected participant -->
            <template v-if="adjustParticipant">
              <div class="section-rule">
                <span class="section-rule-label">{{ adjustParticipant }}</span>
                <div class="section-rule-line"></div>
              </div>

              <!-- Lock warning -->
              <div
                v-if="adjustParticipantLocked"
                class="flex items-center gap-2 px-3 py-2 para-chip"
                style="border-left: 3px solid rgb(251 191 36); background: rgba(251,191,36,0.07);"
              >
                <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" style="box-shadow: 0 0 6px rgba(251,191,36,0.6)"></span>
                <i class="pi pi-lock text-amber-400 text-xs shrink-0"></i>
                <span class="type-label text-amber-400">Auditions started — genre changes locked</span>
              </div>

              <!-- Genre checklist -->
              <div class="grid grid-cols-2 gap-2">
                <button
                  v-for="g in eventGenres"
                  :key="g.name"
                  @click="toggleAdjustGenre(g)"
                  :disabled="adjustParticipantLocked || adjustLoading"
                  class="para-chip p-3 flex items-center gap-2 text-left transition-all duration-150 disabled:opacity-50 disabled:cursor-not-allowed"
                  :class="adjustParticipantGenres.some(p => p.genreName === g.name)
                    ? 'text-accent border-[color:var(--accent-color)]'
                    : 'text-content-secondary'"
                >
                  <i
                    class="pi text-xs shrink-0"
                    :class="adjustParticipantGenres.some(p => p.genreName === g.name)
                      ? 'pi-check-circle text-accent'
                      : 'pi-circle text-content-muted'"
                  ></i>
                  <span class="type-body flex-1 truncate">{{ g.name }}</span>
                  <span v-if="g.format" class="type-label text-content-muted shrink-0">{{ g.format }}</span>
                </button>
              </div>

              <p v-if="eventGenres.length === 0" class="type-label text-content-muted text-center py-4">
                No genres configured for this event
              </p>
            </template>


          </div>
        </div>
      </div>
    </Transition>
  </Teleport>

  <!-- Scoring Criteria Modal -->
  <ScoringCriteriaModal
    v-model="showCriteriaModal"
    :eventName="props.eventName"
    :genres="eventGenres.map(g => g.name)"
    @update:modelValue="(v) => { if (!v) loadCriteriaForAllGenres(eventGenres) }"
  />
</template>
