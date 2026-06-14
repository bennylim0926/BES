<script setup>
import { ref, reactive, onMounted, onUnmounted, watch, computed } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import { checkTableExist, getFileId, getResponseDetails, fetchAllGenres, getGenresByEvent, getVerifiedParticipantsByEvent, insertEventInTable, linkGenresToEvent, getLinkedGenres, unlinkGenreFromEvent, addParticipantToSystem, getSheetSize, getRegisteredParticipantsByEvent, removeParticipantGenre, addGenreToParticipant, getUnverifiedParticipantsDB, verifyPayment, verifyPaymentBatch, updateEventGenreFormat, getJudgesByEvent, getJudgesByDivision, addJudgeToEvent, assignJudgeToDivision, removeJudgeFromDivision, removeEventJudge, getScoringCriteria, fetchAllFolderEvents, fetchAllEvents, getCheckinList, checkInParticipant, sendCheckinPreview, getCheckinPreviews, addDivision, renameDivision, updateDivisionSoloAllowed, deleteDivision, getSheetCategories, getSessionTokens, revokeSessionToken, generateToken, getFeedbackEnabled, setFeedbackEnabled } from '@/utils/api';
import { setActiveEvent, useAuthStore } from '@/utils/auth';
import { useDelay } from '@/utils/utils';

const authStore = useAuthStore()
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
const modalErrors = ref([])
const modalWarnings = ref([])
const modalInfo = ref([])
const importCounts = ref(null) // { imported, existing, skipped } — set only during sheet import
const genreOptions = ref(null)
const eventGenres = ref([])
const linkedGenres = ref([]) // genres declared for this event; groups still render with zero categories
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
const confirming = ref(false)
// participantId → true when any operator has that participant's dialog open
const previewingIds = reactive({})
const checkinSearch = ref('')
const participantsNumBreakdown = ref([])
const totalParticipants = ref(0)

const props = defineProps({
  eventName: String,
  folderID: String,
})

const eventName = ref(props.eventName.split(" ").join("%20"));

const selectedInitGenres = ref([])
const paymentRequired = ref(false)
const feedbackEnabled = ref(true)
const feedbackSaving = ref(false)
const sheetCategories = ref([])
const pendingSuggestionCat = ref(null)
const divRenameActive = ref(null)
const divRenameInput = ref('')
const divFormatOptions = ['', '1v1', '2v2', '3v3', '4v4', '5v5', '7 to smoke', 'solo']

const showModal = ref(false)
const importError = ref('')
const reloadOnClose = ref(false)
const skippedExpanded = ref(false)
const modalScrollable = computed(() =>
  modalErrors.value.length > 0 || modalWarnings.value.length > 0 || modalInfo.value.length > 0
)

const handleAccept = () => {
  showModal.value = false
  modalErrors.value = []
  modalWarnings.value = []
  modalInfo.value = []
  importCounts.value = null
  skippedExpanded.value = false
  if (reloadOnClose.value) window.location.reload()
}

// Confirm dialog
const confirmDialog = ref({ show: false, title: '', message: '', onConfirm: null, confirmLabel: 'Confirm', destructive: true })
const askConfirm = (title, message, onConfirm, { confirmLabel = 'Confirm', destructive = true } = {}) => {
  confirmDialog.value = { show: true, title, message, onConfirm, confirmLabel, destructive }
}
const confirmYes = () => {
  confirmDialog.value.onConfirm?.()
  confirmDialog.value = { show: false, title: '', message: '', onConfirm: null, confirmLabel: 'Confirm', destructive: true }
}
const confirmNo = () => {
  confirmDialog.value = { show: false, title: '', message: '', onConfirm: null, confirmLabel: 'Confirm', destructive: true }
}
const askRemoveDivision = (div) => askConfirm(
  'Remove Category?',
  `"${div.name}" will be permanently removed. Participants already enrolled will block this action — remove them first.`,
  () => removeDivisionFromSection(div.eventGenreId)
)
const askRemoveJudgeGlobal = (j) => askConfirm(
  'Remove Judge from Event?',
  `Remove ${j.judgeName} from all divisions?`,
  () => submitRemoveJudgeGlobal(j.judgeId)
)
const askToggleSolo = (div) => askConfirm(
  div.soloAllowed ? 'Block Solo Entries?' : 'Allow Solo Entries?',
  div.soloAllowed
    ? `Participants in "${div.name}" will no longer be able to register as Solo (pickup crew).`
    : `Solo entries will be permitted for "${div.name}".`,
  () => toggleSoloAllowed(div)
)

// Scoring criteria modal
const showCriteriaModal = ref(false)

// Walk-in form
const showWalkInForm = ref(false)
const revealingRef = ref(null) // name of participant whose ref code is being held/revealed
const activeTab = ref(authStore.user?.role?.[0]?.authority === 'ROLE_HELPER' ? 'event-day' : 'setup') // 'setup' | 'event-day'
const activeGenreTab = ref(null)
const poolTab = ref(null) // active division tab in number pool
let refreshInterval = null
let wsClient = null

// Genre adjustment modal
const showAdjustModal = ref(false)
const adjustSearch = ref('')
const adjustParticipant = ref(null)
const adjustParticipantIds = ref({ participantId: null, eventId: null })
const adjustLoading = ref(false)

// Team form for adding a team-format genre
const genreAddForm = reactive({
  show: false,
  genre: null,
  entryMode: 'team',
  teamName: '',
  members: []   // array of strings, length = additionalMemberCount
})

function isTeamFormatLocal(fmt) {
  return !!fmt && /^\d+v\d+$/i.test(fmt) && fmt.toLowerCase() !== '1v1'
}
function additionalMemberCount(fmt) {
  if (!fmt) return 0
  const m = fmt.match(/^(\d+)v\d+$/i)
  return m ? parseInt(m[1]) - 1 : 0
}


const openModal = (title, message, variant = 'success', errors = [], warnings = [], info = []) => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = variant
  modalErrors.value = errors
  modalWarnings.value = warnings
  modalInfo.value = info
  showModal.value = true
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


const divisionAuditionStats = computed(() => {
  const map = {}
  for (const p of checkinList.value) {
    for (const g of p.genres) {
      if (!map[g.genreName]) map[g.genreName] = { total: 0, drawn: [] }
      map[g.genreName].total++
      if (g.auditionNumber !== null) map[g.genreName].drawn.push(g.auditionNumber)
    }
  }
  return Object.entries(map).map(([name, data]) => {
    const drawnSet = new Set(data.drawn)
    const remaining = Array.from({ length: data.total }, (_, i) => i + 1).filter(n => !drawnSet.has(n))
    return { name, total: data.total, drawn: data.drawn.sort((a, b) => a - b), remaining }
  }).sort((a, b) => a.name.localeCompare(b.name))
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

// Eligible participants — deduplicated by participantId using checkinList so
// a person with both solo and team entries only appears once.
const eligibleParticipants = computed(() =>
  checkinList.value
    .filter(p => p.genres.length > 0 && p.genres.some(g => g.auditionNumber === null))
    .map(p => ({ label: p.label, participantId: p.participantId, eventId: p.eventId }))
    .sort((a, b) => a.label.localeCompare(b.label))
)

const adjustSearchResults = computed(() => {
  const q = adjustSearch.value.trim().toLowerCase()
  if (!q) return eligibleParticipants.value
  return eligibleParticipants.value.filter(p => p.label.toLowerCase().includes(q))
})

// Filter all EGP rows for the currently selected participant by participantId (not display name)
const adjustParticipantGenres = computed(() =>
  adjustParticipantIds.value.participantId
    ? verifiedDbParticipants.value.filter(p => p.participantId === adjustParticipantIds.value.participantId)
    : []
)

const adjustParticipantLocked = computed(() =>
  adjustParticipantGenres.value.some(p => p.auditionNumber !== null)
)

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
  await insertEventInTable(props.eventName, paymentRequired.value)
  const genreIds = selectedInitGenres.value.map(g => g.id)
  const resp = await linkGenresToEvent(props.eventName, genreIds)
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
      openModal('Event Initialised', 'Genres ready. Map categories from your Google Sheet or add them manually below.', 'success')
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
  importError.value = ''
  loading.value = true
  try {
    const createEventResponse = await addParticipantToSystem(fileId.value, props.eventName)
    if (createEventResponse.ok) {
      await Promise.all([
        getVerifiedParticipantsByEvent(eventName.value).then(r => { verifiedFormParticipants.value = r }),
        getRegisteredParticipantsByEvent(eventName.value).then(r => { verifiedDbParticipants.value = r }),
        getUnverifiedParticipantsDB(props.eventName).then(r => { unverifiedParticipants.value = r }),
        getGenresByEvent(props.eventName).then(r => { eventGenres.value = r }),
        getLinkedGenres(props.eventName).then(r => { linkedGenres.value = r }),
        fetchCheckinList(),
      ])
      selectedUnverified.value = new Set()
      createEventResponse.json().then(result => {
        const r = typeof result === 'string' ? JSON.parse(result) : result
        const imported = r?.imported ?? r?.IMPORTED ?? 0
        const existing = r?.existing ?? 0
        const skipped = r?.skipped ?? r?.SKIPPED ?? 0
        const errors = r?.errors ?? r?.ERRORS ?? []
        const warnings = r?.warnings ?? []
        const info = r?.info ?? []
        importCounts.value = { imported, existing, skipped }
        openModal('Import Complete', '', 'success', errors, warnings, info)
      })
    } else if (createEventResponse.status == 404) {
      createEventResponse.json().then(result => {
        importError.value = typeof result === 'string' ? result : 'Google Sheet not found. Check the folder ID and try again.'
        openModal("Not Found", importError.value, "error")
      })
    } else {
      importError.value = `Import failed (${createEventResponse.status}). Check the sheet connection and try again.`
      openModal("Import Error", importError.value, "error")
    }
  } catch (e) {
    importError.value = `Network error during import: ${e.message}`
    openModal("Import Error", importError.value, "error")
  }
  loading.value = false
}

const refreshFromDb = async () => {
  verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
  unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
}

const onFeedbackEnabledToggle = async (e) => {
  const next = e.target.checked
  const prev = feedbackEnabled.value
  feedbackEnabled.value = next
  feedbackSaving.value = true
  try {
    const res = await setFeedbackEnabled(props.eventName, next)
    if (!res?.ok) {
      feedbackEnabled.value = prev
      openModal('Save Failed', `Could not update feedback setting (${res?.status ?? 'no response'}).`, 'warning')
    }
  } catch (err) {
    feedbackEnabled.value = prev
    console.error('setFeedbackEnabled error:', err)
  } finally {
    feedbackSaving.value = false
  }
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

const toggleAdjustGenre = (genre) => {
  if (adjustParticipantLocked.value || adjustLoading.value) return
  const isEnrolled = adjustParticipantGenres.value.some(p => p.genreName === genre.name)
  if (isEnrolled) {
    // Guard: prevent removing the last division
    if (adjustParticipantGenres.value.length <= 1) {
      askConfirm(
        'Cannot Remove',
        `"${adjustParticipant.value}" must be in at least one division. Add another division first before removing this one.`,
        () => {},
        { confirmLabel: 'OK', destructive: false }
      )
      return
    }
    askConfirm(
      'Remove Division',
      `Remove "${adjustParticipant.value}" from ${genre.name}? This cannot be undone.`,
      () => doGenreChange(genre, 'remove'),
      { confirmLabel: 'Remove', destructive: true }
    )
  } else {
    // Team format — show the team details form
    if (isTeamFormatLocal(genre.format)) {
      const count = additionalMemberCount(genre.format)
      genreAddForm.show = true
      genreAddForm.genre = genre
      genreAddForm.entryMode = 'team'
      genreAddForm.teamName = ''
      genreAddForm.members = Array(count).fill('')
      return
    }
    // Non-team — simple confirm
    const fmtLabel = genre.format ? ` · ${genre.format}` : ''
    askConfirm(
      'Add Division',
      `Add "${adjustParticipant.value}" to ${genre.name}${fmtLabel}?`,
      () => doGenreChange(genre, 'add'),
      { confirmLabel: 'Add', destructive: false }
    )
  }
}

const submitGenreAddForm = () => {
  const { genre, entryMode, teamName, members } = genreAddForm
  genreAddForm.show = false
  doGenreChange(genre, 'add', { entryMode, teamName, teamMembers: members.filter(m => m.trim()) })
}

const doGenreChange = async (genre, action, teamOpts = {}) => {
  adjustLoading.value = true
  try {
    if (action === 'remove') {
      const egp = adjustParticipantGenres.value.find(p => p.genreName === genre.name)
      if (egp) {
        const res = await removeParticipantGenre(egp.participantId, egp.eventId, egp.eventGenreId)
        if (res && !res.ok) throw new Error(`Remove failed: ${res.status}`)
      }
    } else {
      const { participantId, eventId } = adjustParticipantIds.value
      if (!participantId || !eventId) {
        const listed = checkinList.value.find(p => p.label === adjustParticipant.value)
        if (listed) adjustParticipantIds.value = { participantId: listed.participantId, eventId: listed.eventId }
      }
      const { participantId: pid, eventId: eid } = adjustParticipantIds.value
      if (pid && eid) {
        const res = await addGenreToParticipant(pid, eid, genre.name, teamOpts.entryMode, teamOpts.teamName, teamOpts.teamMembers)
        if (res && !res.ok) throw new Error(`Add failed: ${res.status}`)
      } else {
        throw new Error('Could not resolve participant ID')
      }
    }
    await Promise.all([
      getRegisteredParticipantsByEvent(eventName.value).then(r => { verifiedDbParticipants.value = r }),
      fetchCheckinList()
    ])
    const verb = action === 'add' ? 'added to' : 'removed from'
    openModal(`Division ${action === 'add' ? 'Added' : 'Removed'}`,
      `"${adjustParticipant.value}" has been ${verb} ${genre.name}.`, 'success')
  } catch (e) {
    console.error(e)
    askConfirm('Update Failed', `Could not update division: ${e.message}`, () => {}, { confirmLabel: 'OK', destructive: false })
  }
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
// Groups are seeded from `linkedGenres` so genres with zero categories still show a header.
// Divisions without a genreId fall into a synthetic 'custom' group.
const divisionsByGenre = computed(() => {
  const groups = {}
  for (const g of linkedGenres.value) {
    groups[g.id] = { genreId: g.id, label: g.genreName, divisions: [], linked: true }
  }
  for (const div of eventGenres.value) {
    const key = div.genreId ?? 'custom'
    if (!groups[key]) {
      let label = 'Custom'
      if (div.genreId != null && genreOptions.value) {
        const found = genreOptions.value.find(g => g.id === div.genreId)
        if (found) label = found.genreName
      }
      groups[key] = { genreId: key, label, divisions: [], linked: div.genreId != null }
    }
    groups[key].divisions.push(div)
  }
  return Object.values(groups)
})

const removeGenreGroup = async (group) => {
  if (group.genreId === 'custom' || !group.linked) return
  const res = await unlinkGenreFromEvent(props.eventName, group.genreId)
  if (res && res.ok) {
    linkedGenres.value = linkedGenres.value.filter(g => g.id !== group.genreId)
  } else {
    openModal('Cannot Remove Genre', 'Could not unlink this genre. Try again.', 'error')
  }
}

const askRemoveGenreGroup = (group) => askConfirm(
  'Remove Genre?',
  `"${group.label}" will be removed from this event.${group.divisions.length > 0 ? ' Its categories will become unlinked.' : ''}`,
  () => removeGenreGroup(group)
)

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
      if (names.some(n => cat === n)) count++
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
      if (names.some(n => cat.toLowerCase() === n)) matched.add(cat.toLowerCase())
    }
  }
  const unique = [...new Set(sheetCategories.value.map(s => s.toLowerCase()))]
  return unique.filter(v => !matched.has(v))
})

const allSheetSuggestions = computed(() => {
  return [...new Set(sheetCategories.value)]
})

// True when any category has more sheet matches than imported participants
const hasUnimportedParticipants = computed(() => {
  if (!sheetCategories.value.length) return false
  return eventGenres.value.some(div =>
    (matchCounts.value[div.eventGenreId] || 0) > div.participantCount
  ) || unmatchedSheetValues.value.length > 0
})

const suggestionCoveredSet = computed(() => {
  const covered = new Set()
  for (const div of eventGenres.value) {
    const names = [div.name.toLowerCase()]
    if (div.sheetAliases) {
      names.push(...div.sheetAliases.split(',').map(a => a.trim().toLowerCase()).filter(Boolean))
    }
    for (const cat of sheetCategories.value) {
      if (names.some(n => cat.toLowerCase() === n)) covered.add(cat)
    }
  }
  return covered
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
  // Team formats default to no solo
  if (format && /^\d+v\d+$/i.test(format) && format.toLowerCase() !== '1v1') {
    if (div.soloAllowed !== false) {
      await updateDivisionSoloAllowed(props.eventName, div.eventGenreId, false)
      div.soloAllowed = false
    }
  }
}

const toggleSoloAllowed = async (div) => {
  const newVal = !div.soloAllowed
  await updateDivisionSoloAllowed(props.eventName, div.eventGenreId, newVal)
  div.soloAllowed = newVal
}

const addSuggestionToGenre = async (genreId, _genreLabel) => {
  if (!pendingSuggestionCat.value) return
  const name = pendingSuggestionCat.value
  pendingSuggestionCat.value = null
  const existingNames = eventGenres.value.map(d => d.name.toLowerCase())
  let finalName = name
  let i = 2
  while (existingNames.includes(finalName.toLowerCase())) {
    finalName = `${name} ${i++}`
  }
  const resp = await addDivision(props.eventName, finalName, null, genreId === 'custom' ? null : genreId)
  if (resp && resp.ok) {
    eventGenres.value = await getGenresByEvent(props.eventName)
    linkedGenres.value = await getLinkedGenres(props.eventName)
  }
}

const addDivisionToGroup = async (genreId, genreLabel) => {
  const existingNames = eventGenres.value.map(d => d.name.toLowerCase())
  let name = genreLabel
  let i = 2
  while (existingNames.includes(name.toLowerCase())) {
    name = `${genreLabel} ${i++}`
  }
  const resp = await addDivision(props.eventName, name, null, genreId === 'custom' ? null : genreId)
  if (resp && resp.ok) {
    eventGenres.value = await getGenresByEvent(props.eventName)
    linkedGenres.value = await getLinkedGenres(props.eventName)
  } else if (resp) {
    const err = await resp.text().catch(() => 'Unknown error')
    console.error('Add division failed:', resp.status, err)
  }
}

const removeDivisionFromSection = async (divId) => {
  const res = await deleteDivision(props.eventName, divId)
  if (res && !res.ok) {
    openModal('Cannot Delete Category', 'Remove all participants from the category before deleting it.', 'error')
    return
  }
  eventGenres.value = eventGenres.value.filter(d => d.eventGenreId !== divId)
}
// ───────────────────────────────────────────────────────────────────────────

// ── Judges (global + per division) ───────────────────────────────────────
const divisionJudges = ref({})
const allEventJudges = ref([])
const globalJudgeInput = ref('')

const loadJudgesForDivision = async (genre) => {
  if (!genre) return
  divisionJudges.value[genre.name] = await getJudgesByDivision(props.eventName, genre.eventGenreId)
}
// Judges not yet assigned to a specific division
const categoriesAssignedToJudge = (judgeId) => {
  const result = []
  for (const [genreName, judges] of Object.entries(divisionJudges.value)) {
    if (judges.some(j => j.judgeId === judgeId)) {
      const genre = eventGenres.value.find(g => g.name === genreName)
      if (genre) result.push(genre)
    }
  }
  return result
}

const categoriesUnassignedToJudge = (judgeId) => {
  const assigned = new Set(categoriesAssignedToJudge(judgeId).map(g => g.eventGenreId))
  return eventGenres.value.filter(g => !assigned.has(g.eventGenreId))
}

const submitAddJudgeGlobal = async () => {
  if (!globalJudgeInput.value.trim()) return
  const res = await addJudgeToEvent(props.eventName, globalJudgeInput.value.trim())
  if (res?.ok) {
    const judges = await res.json()
    allEventJudges.value = judges
    // Auto-generate session token for the new judge
    const newJudge = judges[judges.length - 1]
    if (newJudge && dbEventId.value) {
      await generateToken('JUDGE', dbEventId.value, newJudge.judgeId)
      await loadSessionTokens()
    }
  }
  globalJudgeInput.value = ''
}

const submitRemoveJudgeGlobal = async (judgeId) => {
  // Revoke session token for this judge before removing them
  const judgeToken = sessionTokens.value.find(t => t.role === 'JUDGE' && t.judgeId === judgeId)
  if (judgeToken) await revokeSessionToken(judgeToken.tokenId)

  const res = await removeEventJudge(props.eventName, judgeId)
  if (res?.ok) {
    allEventJudges.value = await res.json()
    for (const genre of eventGenres.value) {
      if (divisionJudges.value[genre.name]) {
        divisionJudges.value[genre.name] = await getJudgesByDivision(props.eventName, genre.eventGenreId)
      }
    }
    await loadSessionTokens()
  }
}

const submitAssignJudge = async (divisionId, judgeId) => {
  const res = await assignJudgeToDivision(props.eventName, divisionId, judgeId)
  if (res?.ok) {
    const judges = await res.json()
    const genre = eventGenres.value.find(g => g.eventGenreId === divisionId)
    if (genre) divisionJudges.value[genre.name] = judges
  }
}

const submitRemoveJudge = async (divisionId, judgeId) => {
  const res = await removeJudgeFromDivision(props.eventName, divisionId, judgeId)
  if (res?.ok) {
    const judges = await res.json()
    const genre = eventGenres.value.find(g => g.eventGenreId === divisionId)
    if (genre) divisionJudges.value[genre.name] = judges
    allEventJudges.value = await getJudgesByEvent(props.eventName) ?? []
  }
}

watch(activeGenreTab, async (tabName) => {
  if (tabName && !divisionJudges.value[tabName]) {
    const genre = eventGenres.value.find(g => g.name === tabName)
    if (genre) await loadJudgesForDivision(genre)
  }
})
// ───────────────────────────────────────────────────────────────────────────

// ── Session Links ──────────────────────────────────────────────────────────
const sessionTokens = ref([])
const sessionTokensLoading = ref(false)
const copiedTokenId = ref(null)

const isAdminOrOrganiser = computed(() => {
  const auth = authStore.user?.role?.[0]?.authority
  return auth === 'ROLE_ADMIN' || auth === 'ROLE_ORGANISER'
})

const isHelper = computed(() => authStore.user?.role?.[0]?.authority === 'ROLE_HELPER')

const loadSessionTokens = async () => {
  if (!dbEventId.value) return
  sessionTokensLoading.value = true
  sessionTokens.value = await getSessionTokens(dbEventId.value)

  // Auto-generate permanent roles if missing
  const hasEmcee = sessionTokens.value.some(t => t.role === 'EMCEE')
  const hasHelper = sessionTokens.value.some(t => t.role === 'HELPER')
  if (!hasEmcee) await generateToken('EMCEE', dbEventId.value, null)
  if (!hasHelper) await generateToken('HELPER', dbEventId.value, null)

  // Auto-repair JUDGE tokens: revoke orphans (no judgeId), create for any judge missing a valid token
  const judgesWithValidToken = new Set(
    sessionTokens.value.filter(t => t.role === 'JUDGE' && t.judgeId).map(t => t.judgeId)
  )
  const orphanTokens = sessionTokens.value.filter(t => t.role === 'JUDGE' && !t.judgeId)
  const judgesNeedingTokens = allEventJudges.value.filter(j => !judgesWithValidToken.has(j.judgeId))
  if (orphanTokens.length > 0 || judgesNeedingTokens.length > 0) {
    await Promise.all(orphanTokens.map(t => revokeSessionToken(t.tokenId)))
    await Promise.all(judgesNeedingTokens.map(j => generateToken('JUDGE', dbEventId.value, j.judgeId)))
  }

  const needsRefresh = !hasEmcee || !hasHelper || orphanTokens.length > 0 || judgesNeedingTokens.length > 0
  if (needsRefresh) sessionTokens.value = await getSessionTokens(dbEventId.value)

  sessionTokensLoading.value = false
}

const refreshTokenNow = async (token) => {
  await revokeSessionToken(token.tokenId)
  await generateToken(token.role, dbEventId.value, token.judgeId ?? null)
  await loadSessionTokens()
}

const handleRefreshToken = (token) => {
  const who = token.judgeName ? `${token.role} (${token.judgeName})` : token.role
  askConfirm(
    'Reset this session link?',
    `The current ${who} link will stop working — anyone using it will need the new URL. Continue?`,
    () => refreshTokenNow(token),
    { confirmLabel: 'Reset link', destructive: true }
  )
}

function copyToClipboard(text) {
  // Fallback path using execCommand — works on all browsers including mobile
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.left = '-9999px'
  ta.style.top = '-9999px'
  ta.setAttribute('readonly', '')
  document.body.appendChild(ta)
  ta.select()
  ta.setSelectionRange(0, text.length)
  document.execCommand('copy')
  document.body.removeChild(ta)
}

const copyTokenLink = (url, tokenId) => {
  copyToClipboard(window.location.origin + url)
  copiedTokenId.value = tokenId
  setTimeout(() => { copiedTokenId.value = null }, 2000)
}

const auditionLinkCopied = ref(false)
function copyAuditionScreenLink() {
  copyToClipboard(window.location.origin + '/event/audition-number?event=' + encodeURIComponent(props.eventName))
  auditionLinkCopied.value = true
  setTimeout(() => { auditionLinkCopied.value = false }, 2000)
}

const displayLinkCopied = ref(false)
function copyDisplayLink() {
  copyToClipboard(window.location.origin + '/audition/display?event=' + encodeURIComponent(props.eventName))
  displayLinkCopied.value = true
  setTimeout(() => { displayLinkCopied.value = false }, 2000)
}

const formatExpiry = (expiresAt) => {
  const d = new Date(expiresAt)
  return d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const isExpiryWarning = (expiresAt) => {
  return (new Date(expiresAt) - Date.now()) < 3 * 24 * 60 * 60 * 1000
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

const filteredCheckinList = computed(() => {
  const q = checkinSearch.value.trim().toLowerCase()
  if (!q) return sortedCheckinList.value
  return sortedCheckinList.value.filter(p =>
    p.label.toLowerCase().includes(q) ||
    (p.memberNames ?? []).some(m => m.toLowerCase().includes(q))
  )
})

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
    const res = await checkInParticipant(p.participantId, p.eventId)
    if (res?.status === 409) {
      checkinConfirm.value.phase = 'error'
      checkinConfirm.value.errorMessage = 'Already checked in at another desk.'
      checkingInId.value = null
      return
    }
    await Promise.all([fetchCheckinList(), refreshFromDb()])
  } catch (e) {
    console.error(e)
  }
  checkingInId.value = null
}

const checkinConfirm = ref({ show: false, participant: null, phase: 'confirm', refCode: null, errorMessage: '' })
// phase: 'confirm' → 'generating' → 'done'

// Dialog slot machine animation state
const dialogFakeNums = ref({})
const dialogRollingIntervals = {}
const dialogNumberQueue = []
function processNextDialogNumber() {
  if (dialogNumberQueue.length === 0) {
    checkinConfirm.value.phase = 'done'
    return
  }
  const { genre, auditionNumber } = dialogNumberQueue.shift()
  const g = checkinConfirm.value.participant?.genres.find(x => x.genreName === genre)
  if (!g) { processNextDialogNumber(); return }

  g.rolling = true
  clearInterval(dialogRollingIntervals[genre])
  dialogRollingIntervals[genre] = setInterval(() => {
    dialogFakeNums.value = { ...dialogFakeNums.value, [genre]: Math.floor(Math.random() * 99) + 1 }
  }, 80)

  setTimeout(() => {
    clearInterval(dialogRollingIntervals[genre])
    delete dialogRollingIntervals[genre]
    const next = { ...dialogFakeNums.value }
    delete next[genre]
    dialogFakeNums.value = next
    g.rolling = false
    g.auditionNumber = auditionNumber
    processNextDialogNumber()
  }, 2000)
}

const askCheckIn = (p) => {
  checkinConfirm.value = {
    show: true,
    participant: { ...p, genres: p.genres.map(g => ({ ...g, rolling: false })) },
    phase: 'confirm',
    refCode: null
  }
  // Broadcast preview to AuditionNumber display (fire-and-forget)
  sendCheckinPreview(eventName.value, {
    participantId: p.participantId,
    name: p.label,
    memberNames: p.memberNames ?? [],
    genres: p.genres.map(g => ({ genreName: g.genreName, auditionNumber: g.auditionNumber ?? null }))
  })
}

const closeCheckinDialog = () => {
  if (checkinConfirm.value.phase === 'confirm' && checkinConfirm.value.participant) {
    sendCheckinPreview(eventName.value, {
      participantId: checkinConfirm.value.participant.participantId,
      cancelled: true
    })
  }
  checkinConfirm.value.show = false
}

const confirmCheckIn = async () => {
  const p = checkinConfirm.value.participant
  if (!p || confirming.value) return
  confirming.value = true

  // Reset genre display state
  p.genres.forEach(g => { g.auditionNumber = null; g.rolling = false })

  // Reset animation queue
  dialogNumberQueue.length = 0
  Object.values(dialogRollingIntervals).forEach(clearInterval)
  for (const k in dialogRollingIntervals) delete dialogRollingIntervals[k]
  dialogFakeNums.value = {}

  checkinConfirm.value.phase = 'generating'
  checkinConfirm.value.refCode = null

  // checkIn awaits both the HTTP assignment AND fetchCheckinList + refreshFromDb,
  // so when it resolves we have fresh numbers in checkinList.value and verifiedDbParticipants.value
  await checkIn(p)
  confirming.value = false

  // Early exit if checkIn set an error phase (e.g. 409)
  if (checkinConfirm.value.phase === 'error') return

  // Get ref code from fresh verifiedDbParticipants data
  const refEntry = verifiedDbParticipants.value.find(ep => ep.participantId === p.participantId)
  checkinConfirm.value.refCode = refEntry?.referenceCode || null

  // Queue animations from the now-fresh checkinList data (no WS dependency)
  const freshParticipant = checkinList.value.find(ep => ep.participantId === p.participantId)
  if (freshParticipant) {
    for (const ug of freshParticipant.genres) {
      if (ug.auditionNumber != null) {
        dialogNumberQueue.push({ genre: ug.genreName, auditionNumber: ug.auditionNumber })
      }
    }
  }

  if (dialogNumberQueue.length === 0) {
    checkinConfirm.value.phase = 'done'
    return
  }

  processNextDialogNumber()
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
  try {
    await checkTableExist(eventName, tableExist)
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
    eventGenres.value = await getGenresByEvent(props.eventName) ?? []
    linkedGenres.value = await getLinkedGenres(props.eventName) ?? []
    allEventJudges.value = await getJudgesByEvent(props.eventName) ?? []
    if (eventGenres.value.length > 0) {
      activeGenreTab.value = eventGenres.value[0].name
      await loadJudgesForDivision(eventGenres.value[0])
    }
    await loadCriteriaForAllGenres(eventGenres.value)
    if (tableExist.value) {
      verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
      verifiedFormParticipants.value = await getVerifiedParticipantsByEvent(eventName.value)
      unverifiedParticipants.value = await getUnverifiedParticipantsDB(props.eventName)
      await fetchCheckinList()
      loadSessionTokens()
      const fb = await getFeedbackEnabled(props.eventName)
      if (fb && typeof fb.feedbackEnabled === 'boolean') feedbackEnabled.value = fb.feedbackEnabled
    }
  } catch (e) {
    console.error('EventDetails mount error:', e)
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
      // Participant is now checked in — no longer in preview
      if (msg.participantId) delete previewingIds[msg.participantId]
      if (!refreshPending) {
        refreshPending = true
        refreshFromDb().finally(() => { refreshPending = false })
      }
    })
    subscribeToChannel(wsClient, '/topic/checkin-preview/', (msg) => {
      if (msg.eventName && msg.eventName !== props.eventName) return
      if (!msg.participantId) return
      if (msg.cancelled) {
        delete previewingIds[msg.participantId]
      } else {
        previewingIds[msg.participantId] = true
      }
    })
    subscribeToChannel(wsClient, '/topic/walkin/', (msg) => {
      if (msg.eventName !== props.eventName) return
      fetchCheckinList()
    })
    // Hydrate previewingIds from server state so late-joining/refreshed clients see current previews
    const existingPreviews = await getCheckinPreviews(props.eventName)
    for (const id of existingPreviews) {
      previewingIds[id] = true
    }
  }
})

const anyModalOpen = computed(() =>
  showModal.value ||
  showCriteriaModal.value ||
  showAdjustModal.value ||
  showWalkInForm.value ||
  genreAddForm.show ||
  checkinConfirm.value.show ||
  confirmDialog.value.show
)
watch(anyModalOpen, (open) => { document.body.style.overflow = open ? 'hidden' : '' })

onUnmounted(() => {
  document.body.style.overflow = ''
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
      <div class="flex flex-col sm:flex-row sm:flex-wrap gap-3 self-start">
        <!-- Manage cluster — buttons that mutate participants/data -->
        <div class="flex flex-col">
          <span class="type-label text-content-muted mb-1.5" style="font-size:10px;letter-spacing:0.22em;">Manage</span>
          <div class="flex flex-wrap gap-2">
            <button
              @click="showWalkInForm = true"
              class="flex items-center gap-2 px-4 py-2 para-chip type-label border-accent"
            >
              <i class="pi pi-user-plus text-sm"></i>
              Add Participant
            </button>
            <button
              @click="showAdjustModal = true"
              class="flex items-center gap-2 px-4 py-2 para-chip type-label border-accent"
            >
              <i class="pi pi-sliders-h text-sm"></i>
              Edit Entries
            </button>
            <button
              v-if="!isHelper"
              @click="refreshParticipant"
              :disabled="loading"
              class="flex items-center gap-2 px-4 py-2 para-chip type-label disabled:opacity-50 disabled:cursor-not-allowed transition-all"
              :class="hasUnimportedParticipants && !loading
                ? 'border-amber-500/60 text-amber-400'
                : 'bg-accent'"
            >
              <span
                v-if="hasUnimportedParticipants && !loading"
                class="inline-block w-2 h-2 rounded-full bg-amber-400 shrink-0"
                style="box-shadow:0 0 6px rgba(245,158,11,0.7)"
              ></span>
              <i v-else class="pi text-sm" :class="loading ? 'pi-spinner pi-spin' : 'pi-cloud-download'"></i>
              {{ loading ? 'Importing…' : 'Import from Sheets' }}
            </button>
          </div>
        </div>

        <!-- Visual cut between clusters -->
        <div class="hidden sm:block w-px self-stretch bg-surface-700/40 mx-1" aria-hidden="true"></div>

        <!-- Display-source cluster — copy-link buttons, read-only -->
        <div class="flex flex-col">
          <span class="type-label text-content-muted mb-1.5" style="font-size:10px;letter-spacing:0.22em;">Display Sources</span>
          <div class="flex flex-wrap gap-2">
            <button
              @click="copyAuditionScreenLink"
              class="flex items-center gap-2 px-4 py-2 para-chip type-label transition-all duration-200"
              :class="auditionLinkCopied
                ? 'text-emerald-400 border-emerald-400/40'
                : 'text-content-muted hover:text-content-primary'"
              style="border-style:dashed;"
              title="Copy the link participants use to see their audition number"
            >
              <i class="pi text-sm" :class="auditionLinkCopied ? 'pi-check' : 'pi-link'"></i>
              {{ auditionLinkCopied ? 'Link Copied!' : 'Number Draw' }}
            </button>
            <button
              @click="copyDisplayLink"
              class="flex items-center gap-2 px-4 py-2 para-chip type-label transition-all duration-200"
              :class="displayLinkCopied
                ? 'text-emerald-400 border-emerald-400/40'
                : 'text-content-muted hover:text-content-primary'"
              style="border-style:dashed;"
              title="Copy the broadcast link for the live audition display (current name + timer)"
            >
              <i class="pi text-sm" :class="displayLinkCopied ? 'pi-check' : 'pi-link'"></i>
              {{ displayLinkCopied ? 'Link Copied!' : 'Stage Display' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Tab toggle -->
    <div class="tab-bar mb-6">
      <button
        v-if="!isHelper"
        @click="activeTab = 'setup'"
        class="tab-item"
        :class="{ 'is-active': activeTab === 'setup' }"
      >
        <i class="pi pi-cog text-xs"></i>
        Setup
      </button>
      <button
        @click="activeTab = 'event-day'"
        class="tab-item"
        :class="{ 'is-active': activeTab === 'event-day' }"
      >
        <i class="pi pi-calendar text-xs"></i>
        Event Day
        <span
          v-if="totalNotShownUp > 0"
          class="inline-flex items-center justify-center badge-warning px-1.5 py-0.5 text-[10px]"
        >{{ totalNotShownUp }}</span>
        <span
          v-else-if="unverifiedParticipants.length > 0 && activeTab !== 'event-day'"
          class="inline-flex items-center justify-center badge-danger px-1.5 py-0.5 text-[10px]"
        >{{ unverifiedParticipants.length }}</span>
      </button>
    </div>

    <!-- ── SETUP TAB ─────────────────────────────────────────────────────── -->
    <template v-if="activeTab === 'setup'">

    <!-- Stat strip -->
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-6" style="max-width:360px;">
      <div class="stat-card relative">
        <div class="corner-bar-tl"></div>
        <template v-if="unverifiedParticipants.length > 0">
          <div class="type-stat text-amber-400">{{ unverifiedParticipants.length }}</div>
          <div class="type-label text-amber-400">Pending Verification</div>
          <p class="type-prose-sm mt-1">{{ totalVerified }} form sign-ups verified.</p>
        </template>
        <template v-else>
          <div class="flex items-center gap-2 mb-1">
            <span class="inline-block w-2 h-2 rounded-full bg-emerald-400 shrink-0" style="box-shadow:0 0 6px rgba(52,211,153,0.6)"></span>
            <div class="type-stat" style="font-size:36px;">All Verified</div>
          </div>
          <p class="type-prose-sm">{{ totalVerified }} form sign-ups.</p>
        </template>
      </div>
    </div>

    <!-- Import error banner -->
    <div
      v-if="importError"
      class="flex items-center gap-3 px-4 py-3 mb-4"
      style="border-left:3px solid rgb(239 68 68);background:rgba(239,68,68,0.1)"
    >
      <span class="inline-block w-2 h-2 rounded-full bg-red-400 flex-shrink-0" style="box-shadow:0 0 6px rgba(239,68,68,0.8)"></span>
      <span class="type-label text-red-300/90 flex-1">{{ importError }}</span>
      <button
        @click="importError = ''"
        class="para-chip-sm px-2.5 py-1 type-label text-content-muted hover:text-content-primary transition-colors flex-shrink-0"
      >DISMISS</button>
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
                  <span class="type-name text-content-secondary block">{{ p.name }}</span>
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


    <!-- Event Settings (existing event) -->
    <div v-if="tableExist" class="card-hover p-4 relative mt-6">
      <div class="corner-bar-tl"></div>
      <div class="section-rule mb-4">
        <span class="section-rule-label">Event Settings</span>
        <div class="section-rule-line"></div>
      </div>
      <label
        class="flex items-center gap-3 px-4 py-3 para-chip cursor-pointer transition-all duration-150 w-fit"
        :class="feedbackEnabled ? 'border-accent' : ''"
      >
        <input
          type="checkbox"
          :checked="feedbackEnabled"
          :disabled="feedbackSaving"
          @change="onFeedbackEnabledToggle"
          class="w-4 h-4"
        />
        <div>
          <span class="type-body">Feedback System</span>
          <p class="type-prose-sm mt-0.5">
            {{ feedbackEnabled
              ? 'Judges can submit feedback tags and notes for each participant.'
              : 'Disabled — judges will only see the score keypad.' }}
          </p>
        </div>
      </label>
    </div>

    <!-- Setup section (when no table exists) -->
    <div v-if="!tableExist" class="card-hover p-6 relative">
      <div class="corner-bar-tl"></div>
      <div class="flex items-center gap-3 mb-6">
        <i class="pi pi-exclamation-triangle text-amber-300 text-sm"></i>
        <div>
          <div class="type-body text-content-secondary">Event Setup Required</div>
          <p class="type-prose">No record found for this event. Select genres to initialise.</p>
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
            <span class="type-name">{{ g.genreName }}</span>
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

  <!-- Empty state: no participants -->
  <div v-if="tableExist && verifiedDbParticipants.length === 0 && unverifiedParticipants.length === 0 && eventGenres.length > 0" class="card p-8 text-center mt-6">
    <div class="type-page-title text-content-muted mb-3">NO PARTICIPANTS YET</div>
    <p class="type-body text-content-muted mb-4">Import from Google Sheets or add a walk-in to get started.</p>
    <div class="flex justify-center gap-3">
      <button @click="refreshParticipant" class="para-chip-sm px-5 py-3 type-label text-accent">IMPORT SHEET</button>
      <button @click="showWalkInForm = true" class="para-chip-sm px-5 py-3 type-label text-content-primary">ADD WALK-IN</button>
    </div>
  </div>

  <!-- Divisions (post-init) — grouped by parent genre -->
  <div v-if="tableExist && (eventGenres.length > 0 || linkedGenres.length > 0)" class="card-hover p-4 relative mt-6">
    <div class="corner-bar-tl"></div>

    <div class="section-rule mb-3">
      <span class="section-rule-label">Categories</span>
      <div class="section-rule-line"></div>
    </div>
    <p class="type-prose mb-4">
      Categories are competition formats within each genre (e.g. Popping 1v1, Popping 7 to Smoke).
      Names must match your Google Sheet column values exactly.
    </p>

    <!-- Sheet suggestions strip — only when sheet is connected -->
    <div v-if="allSheetSuggestions.length > 0" class="mb-4 p-3 border border-white/7">
      <p class="type-label text-content-muted mb-3">FROM YOUR SHEET — click to add as a category</p>
      <div class="flex flex-wrap gap-2">
        <span
          v-for="cat in allSheetSuggestions"
          :key="cat"
          class="relative"
        >
          <!-- Covered: visible but muted with strikethrough -->
          <span
            v-if="suggestionCoveredSet.has(cat)"
            class="para-chip-sm px-3 py-1 type-name-sm text-content-muted opacity-40 line-through"
          >{{ cat }}</span>
          <!-- Uncovered: clearly clickable button -->
          <button
            v-else
            @click="pendingSuggestionCat = pendingSuggestionCat === cat ? null : cat"
            class="para-chip-sm px-3 py-1.5 type-name-sm text-content-secondary hover:text-accent transition-colors"
            style="border-style:dashed;"
          >+ {{ cat }}</button>
          <!-- Inline genre picker — rendered outside clip-path ancestor -->
          <div
            v-if="pendingSuggestionCat === cat"
            class="absolute top-full left-0 mt-1 z-50 min-w-[160px]"
            style="background:var(--color-surface-800,#1a1a1a);border:1px solid rgba(255,255,255,0.12);"
          >
            <p class="type-label text-content-muted px-3 pt-2 pb-1">ADD TO GENRE:</p>
            <button
              v-for="group in divisionsByGenre"
              :key="group.genreId"
              @click="addSuggestionToGenre(group.genreId, group.label)"
              class="block w-full text-left px-3 py-2 type-name-sm text-content-secondary hover:text-accent transition-colors"
            >{{ group.label }}</button>
          </div>
        </span>
      </div>
    </div>

    <div class="space-y-4">
      <div v-for="group in divisionsByGenre" :key="group.genreId" class="space-y-2">
        <!-- Genre group header -->
        <div class="flex items-center gap-2">
          <span class="type-section-header text-content-secondary">{{ group.label }}</span>
          <span class="badge-neutral type-label px-2 py-0.5 text-sm">{{ group.divisions.length }}</span>
          <button
            v-if="group.linked && group.genreId !== 'custom'"
            @click="askRemoveGenreGroup(group)"
            class="type-label text-content-muted hover:text-red-400 transition-colors ml-auto"
            title="Remove genre from event"
          ><i class="pi pi-times text-xs"></i></button>
        </div>

        <!-- Division cards — 2-col on sm+, full-width on phone -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
        <div v-for="div in group.divisions" :key="div.eventGenreId">
          <div
            class="para-chip p-3 h-full flex flex-col gap-2"
            :class="sheetCategories.length > 0
              ? (matchCounts[div.eventGenreId] || 0) > 0
                ? (div.participantCount >= (matchCounts[div.eventGenreId] || 0)
                  ? 'border-l-[3px] border-l-emerald-500'
                  : 'border-l-[3px] border-l-amber-500')
                : 'border-l-[3px] border-l-amber-500'
              : div.participantCount > 0 ? 'border-l-[3px] border-l-emerald-500' : ''"
          >
            <!-- Display mode -->
            <template v-if="divRenameActive !== div.eventGenreId">
              <!-- Row 1: name (left) + delete × (top-right, like judge card) -->
              <div class="flex items-start justify-between gap-2">
                <button
                  @click="divRenameActive = div.eventGenreId; divRenameInput = div.name"
                  class="type-name text-content-secondary hover:text-accent text-left flex-1 min-w-0 leading-snug transition-colors"
                  title="Click to rename"
                >{{ div.name }}</button>
                <button
                  @click="askRemoveDivision(div)"
                  class="type-label text-content-muted hover:text-red-400 transition-colors shrink-0 mt-0.5"
                  title="Remove category"
                ><i class="pi pi-times text-xs"></i></button>
              </div>
              <!-- Row 2: count dots -->
              <div class="flex items-center gap-2">
                <div v-if="div.participantCount > 0" class="flex items-center gap-1 text-emerald-400">
                  <span class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 shrink-0" style="box-shadow:0 0 6px rgba(52,211,153,0.6)"></span>
                  <span class="type-label">{{ div.participantCount }}</span>
                </div>
                <div v-if="sheetCategories.length > 0 && ((matchCounts[div.eventGenreId] || 0) - div.participantCount) > 0" class="flex items-center gap-1 text-amber-400">
                  <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" style="box-shadow:0 0 6px rgba(245,158,11,0.6)"></span>
                  <span class="type-label">{{ (matchCounts[div.eventGenreId] || 0) - div.participantCount }}</span>
                </div>
                <div v-if="sheetCategories.length > 0 && (matchCounts[div.eventGenreId] || 0) === 0 && div.participantCount === 0" class="flex items-center gap-1 text-amber-400">
                  <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" style="box-shadow:0 0 6px rgba(245,158,11,0.6)"></span>
                  <span class="type-label">0</span>
                </div>
              </div>
              <!-- Row 3: format select + solo toggle -->
              <div class="flex items-center gap-1.5 mt-auto">
                <select
                  :value="div.format || ''"
                  @change="saveDivisionFormat(div, $event.target.value)"
                  class="text-xs px-2 py-1 para-chip-sm bg-transparent text-content-secondary flex-1 min-w-0"
                >
                  <option value="">No format</option>
                  <template v-for="opt in divFormatOptions" :key="opt">
                    <option v-if="opt" :value="opt">{{ opt }}</option>
                  </template>
                </select>
                <button
                  v-if="div.format && /^\d+v\d+$/i.test(div.format) && div.format.toLowerCase() !== '1v1'"
                  @click="askToggleSolo(div)"
                  :class="div.soloAllowed ? 'text-content-muted hover:text-amber-400' : 'text-amber-400 hover:text-content-muted'"
                  class="para-chip-sm px-2 py-1 type-label transition-colors shrink-0"
                  :title="div.soloAllowed ? 'Solo entries allowed' : 'Solo entries blocked'"
                >{{ div.soloAllowed ? 'SOLO OK' : 'NO SOLO' }}</button>
              </div>
            </template>

            <!-- Edit / rename mode -->
            <template v-else>
              <input
                v-model="divRenameInput"
                type="text"
                class="input-base w-full"
                placeholder="Category name"
                autofocus
                @keyup.enter="saveDivisionName(div)"
                @keyup.escape="divRenameActive = null"
              />
              <div class="flex items-center gap-2">
                <button
                  @click="saveDivisionName(div)"
                  class="para-chip-sm px-2.5 py-1.5 type-label text-emerald-400 hover:text-emerald-300 transition-colors flex-1 flex items-center justify-center gap-1"
                ><i class="pi pi-check text-sm"></i> Save</button>
                <button
                  @click="divRenameActive = null"
                  class="para-chip-sm px-2.5 py-1.5 type-label text-content-muted hover:text-content-primary transition-colors flex-1 flex items-center justify-center gap-1"
                ><i class="pi pi-times text-sm"></i> Cancel</button>
              </div>
            </template>

          </div>
        </div>
        </div><!-- end grid -->

        <!-- Add division to group -->
        <button
          @click="addDivisionToGroup(group.genreId, group.label)"
          class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1.5 type-label text-content-muted hover:text-accent transition-all"
        >+ Add {{ group.label }} category</button>
      </div>
    </div>

    <!-- Unmatched sheet values strip -->
    <div
      v-if="unmatchedSheetValues.length > 0"
      class="mt-4 pt-3 border-t border-surface-600/30"
    >
      <div class="flex items-center gap-2 mb-2">
        <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" style="box-shadow: 0 0 6px rgba(251,191,36,0.6)"></span>
        <span class="type-label text-amber-400 text-sm">Unmatched sheet values</span>
      </div>
      <div class="flex flex-wrap gap-1">
        <span
          v-for="val in unmatchedSheetValues"
          :key="val"
          class="type-name-sm text-content-muted bg-amber-950/30 px-2.5 py-1 para-chip"
        >{{ val }}</span>
      </div>
    </div>
  </div>

  <!-- Judge Pool — sister card, kept above Genre Configuration so the two sections don't compete -->
  <div v-if="tableExist && (eventGenres.length > 0 || linkedGenres.length > 0)" class="card-hover p-4 relative mt-6">
    <div class="corner-bar-tl"></div>

    <div class="section-rule mb-1">
      <span class="section-rule-label">Judge Pool</span>
      <div class="section-rule-line"></div>
    </div>
    <p class="type-prose mb-4">
      Add your judges here first — then assign them to categories below.
    </p>

    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <!-- Per-judge card -->
      <div
        v-for="j in allEventJudges"
        :key="j.judgeId"
        class="para-chip p-3 flex flex-col gap-2"
        :class="categoriesAssignedToJudge(j.judgeId).length === 0
          ? 'border-l-[3px] border-l-amber-500 bg-amber-950/10'
          : 'border-l-[3px] border-l-emerald-500/40'"
      >
        <!-- Judge name + remove -->
        <div class="flex items-center justify-between gap-2">
          <div class="flex items-center gap-1.5">
            <span
              class="inline-block w-1.5 h-1.5 rounded-full shrink-0"
              :class="categoriesAssignedToJudge(j.judgeId).length === 0
                ? 'bg-amber-400' : 'bg-emerald-400'"
              :style="categoriesAssignedToJudge(j.judgeId).length === 0
                ? 'box-shadow:0 0 5px rgba(245,158,11,0.5)'
                : 'box-shadow:0 0 5px rgba(52,211,153,0.5)'"
            ></span>
            <span class="type-name-sm text-content-secondary">{{ j.judgeName }}</span>
          </div>
          <button
            @click="askRemoveJudgeGlobal(j)"
            class="type-label text-content-muted hover:text-red-400 transition-colors"
            title="Remove from event"
          ><i class="pi pi-times text-xs"></i></button>
        </div>

        <!-- Assigned categories -->
        <div>
          <p class="type-label text-content-muted mb-1">CATEGORIES</p>
          <div v-if="categoriesAssignedToJudge(j.judgeId).length > 0" class="flex flex-wrap gap-1.5 mb-1.5">
            <span
              v-for="cat in categoriesAssignedToJudge(j.judgeId)"
              :key="cat.eventGenreId"
              class="inline-flex items-center gap-2 para-chip-sm px-2.5 py-1.5 type-name-sm text-content-secondary"
            >
              {{ cat.name }}
              <button
                @click="submitRemoveJudge(cat.eventGenreId, j.judgeId)"
                class="hover:text-red-400 transition-colors leading-none"
              ><i class="pi pi-times text-sm"></i></button>
            </span>
          </div>
          <p v-else class="type-prose text-amber-400/90 mb-1.5">No categories assigned yet.</p>
        </div>

        <!-- Assign — native select avoids clip-path clipping -->
        <select
          v-if="categoriesUnassignedToJudge(j.judgeId).length > 0"
          @change="submitAssignJudge(Number($event.target.value), j.judgeId); $event.target.value = ''"
          class="w-full px-3 py-2.5 type-name-sm text-accent bg-surface-800 border border-[color:var(--accent-muted)] para-chip-sm"
        >
          <option value="" disabled selected>+ Assign category</option>
          <option
            v-for="cat in categoriesUnassignedToJudge(j.judgeId)"
            :key="cat.eventGenreId"
            :value="cat.eventGenreId"
          >{{ cat.name }}</option>
        </select>
      </div>

      <!-- Add judge card -->
      <div class="para-chip p-3 flex flex-col items-center justify-center gap-2" style="border-style:dashed;border-color:rgba(255,255,255,0.1);">
        <div class="flex items-center gap-1.5 w-full">
          <input
            v-model="globalJudgeInput"
            type="text"
            placeholder="Judge name…"
            autocomplete="off"
            class="bg-transparent type-name-sm placeholder:text-content-muted focus:outline-none flex-1 min-w-0"
            @keyup.enter="submitAddJudgeGlobal()"
          />
          <button
            @click="submitAddJudgeGlobal()"
            class="type-label text-accent hover:opacity-80 transition-opacity shrink-0"
            title="Add judge"
          ><i class="pi pi-plus text-xs"></i></button>
        </div>
        <span v-if="allEventJudges.length === 0" class="type-label text-content-muted" >No judges yet</span>
      </div>
    </div>
  </div>

  <!-- Genre Configuration — per-category tabs (roster, scoring, judges, audition display) -->
  <div v-if="tableExist && eventGenres.length > 0" class="card-hover p-4 relative mt-6" style="overflow: visible">
    <div class="corner-bar-tl"></div>

    <div class="section-rule mb-4">
      <span class="section-rule-label">Genre Configuration</span>
      <div class="section-rule-line"></div>
    </div>

    <!-- Genre tab bar -->
    <div class="tab-bar mb-4">
      <button
        v-for="g in eventGenres"
        :key="g.name"
        @click="activeGenreTab = g.name"
        class="tab-item tab-item-data"
        :class="{ 'is-active': activeGenreTab === g.name }"
      >
        {{ g.name }}
        <span
          v-if="completeBreakdown.find(b => b.genre === normalizeGenreName(g.name))"
          class="opacity-60 text-sm font-normal"
        >{{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).total }}</span>
      </button>
    </div>

    <!-- Tab content for each genre -->
    <template v-for="g in eventGenres" :key="g.name + '-content'">
      <div v-if="activeGenreTab === g.name" class="px-1 sm:px-3 py-3 sm:py-5 space-y-6 sm:space-y-7">

        <!-- ROSTER STATUS -->
        <section v-if="completeBreakdown.find(b => b.genre === normalizeGenreName(g.name))">
          <div class="section-rule section-rule-lg mb-3">
            <span class="section-rule-label">Roster Status</span>
            <div class="section-rule-line"></div>
          </div>
          <div class="flex items-center gap-2 flex-wrap">
            <span class="para-chip-sm px-2.5 py-1 type-label text-content-secondary">{{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).total }} total</span>
            <span class="para-chip-sm px-2.5 py-1 type-label" style="border-color:rgba(52,211,153,0.35);color:#34d399;">
              <span class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 mr-1.5 shrink-0" style="box-shadow:0 0 5px rgba(52,211,153,0.5)"></span>
              {{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).registered }} registered
            </span>
            <span
              v-if="completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).unregistered > 0"
              class="para-chip-sm px-2.5 py-1 type-label"
              style="border-color:rgba(245,158,11,0.35);color:#f59e0b;"
            >
              <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 mr-1.5 shrink-0" style="box-shadow:0 0 5px rgba(245,158,11,0.5)"></span>
              {{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).unregistered }} unregistered
            </span>
          </div>

          <!-- Unregistered list -->
          <div v-if="getUnregistered(normalizeGenreName(g.name)).unregistered.length > 0" class="mt-2">
            <div class="flex flex-wrap gap-2">
              <span
                v-for="p in getUnregistered(normalizeGenreName(g.name)).unregistered"
                :key="p.participantName"
                class="para-chip-sm px-2.5 py-1 type-name-sm text-amber-400"
                style="border-color:rgba(245,158,11,0.25);"
              >{{ p.participantName }}</span>
            </div>
          </div>
          <div v-else class="flex items-center gap-2 type-body text-emerald-400 mt-2">
            <i class="pi pi-check-circle"></i>
            <span>All participants registered</span>
          </div>
        </section>

        <!-- SCORING CRITERIA -->
        <section>
          <div class="flex items-center gap-2 mb-3">
            <div class="section-rule section-rule-lg flex-1 min-w-0">
              <span class="section-rule-label">Scoring Criteria</span>
              <div class="section-rule-line"></div>
            </div>
            <button
              @click="showCriteriaModal = true"
              class="para-chip-sm px-3 py-1.5 type-label shrink-0"
            ><i class="pi pi-sliders-h mr-1" style="font-size:0.7rem"></i>Configure</button>
          </div>
          <div v-if="criteriaByGenre[g.name]?.length" class="flex flex-wrap gap-2">
            <span
              v-for="c in criteriaByGenre[g.name]"
              :key="c.id"
              class="para-chip-sm px-3 py-1 type-label text-content-secondary inline-flex items-center gap-1.5"
            >
              {{ c.name }}
              <span v-if="c.weight != null" class="text-content-muted">×{{ c.weight }}</span>
            </span>
          </div>
          <p v-else class="type-prose">Default — single 0–10 score per judge.</p>
        </section>

        <!-- JUDGES (read-only) -->
        <section>
          <div class="section-rule section-rule-lg mb-3">
            <span class="section-rule-label">Judges</span>
            <div class="section-rule-line"></div>
            <span class="type-prose-sm hidden sm:inline" style="white-space:nowrap;">manage in Judge Pool above</span>
          </div>
          <div v-if="(divisionJudges[g.name] || []).length > 0" class="flex flex-wrap gap-2">
            <span
              v-for="j in (divisionJudges[g.name] || [])"
              :key="j.judgeId"
              class="flex items-center gap-1.5 para-chip px-2.5 py-1"
            >
              <span class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 shrink-0" style="box-shadow:0 0 5px rgba(52,211,153,0.5)"></span>
              <span class="type-name-sm text-content-secondary">{{ j.judgeName }}</span>
            </span>
          </div>
          <p v-else class="type-prose">No judges assigned. Add them in the Judge Pool above, then assign to this category.</p>
        </section>

      </div>
    </template>
  </div>

  <!-- Session Links — always visible, auto-generated -->
  <div v-if="isAdminOrOrganiser && tableExist" class="card-hover p-4 relative mt-6">
    <div class="corner-bar-tl"></div>

    <div class="section-rule mb-1">
      <span class="section-rule-label">Session Links</span>
      <div class="section-rule-line"></div>
    </div>
    <p class="type-prose-sm mb-4">Auto-generated. Refresh a link to extend it — the old URL will stop working.</p>

    <div v-if="sessionTokensLoading" class="flex items-center gap-2 type-label text-content-muted py-4">
      <i class="pi pi-spinner pi-spin text-xs"></i> Loading…
    </div>
    <div v-else class="space-y-2">
      <div
        v-for="t in sessionTokens"
        :key="t.tokenId"
        class="para-chip px-3 py-2 flex flex-col gap-1.5"
      >
        <!-- Row 1: role badge + name -->
        <div class="flex items-center gap-2">
          <span
            class="badge-neutral text-sm shrink-0"
            :class="t.role === 'JUDGE' ? 'badge-accent' : 'badge-neutral'"
          >{{ t.role }}</span>
          <span v-if="t.judgeName" class="type-name text-content-secondary">{{ t.judgeName }}</span>
        </div>
        <!-- Row 2: expiry + actions -->
        <div class="flex items-center gap-2">
          <span
            class="type-label flex-1"
            :class="isExpiryWarning(t.expiresAt) ? 'text-amber-400' : 'text-content-muted'"
          >{{ formatExpiry(t.expiresAt) }}<span v-if="isExpiryWarning(t.expiresAt)"> ⚠</span></span>
          <button
            @click="handleRefreshToken(t)"
            class="para-chip-sm w-10 h-10 inline-flex items-center justify-center type-label text-content-muted hover:text-accent transition-colors shrink-0"
            title="Reset link — current URL stops working"
          ><i class="pi pi-refresh text-base"></i></button>
          <button
            @click="copyTokenLink(t.url, t.tokenId)"
            class="para-chip-sm w-10 h-10 inline-flex items-center justify-center type-label transition-colors shrink-0"
            :class="copiedTokenId === t.tokenId ? 'text-emerald-400' : 'text-content-muted hover:text-accent'"
            title="Copy link"
          ><i class="pi text-base" :class="copiedTokenId === t.tokenId ? 'pi-check' : 'pi-copy'"></i></button>
        </div>
      </div>
      <p class="type-prose-sm mt-3">Judge links are removed automatically when a judge is deleted.</p>
    </div>
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
        <p class="type-prose-sm mt-1">Have audition numbers.</p>
      </div>
      <div class="stat-card relative">
        <div class="corner-bar-tl"></div>
        <div class="type-stat" :class="totalNotShownUp > 0 ? 'text-amber-400' : ''">{{ totalNotShownUp }}</div>
        <div class="type-label">Not Shown Up</div>
        <p class="type-prose-sm mt-1">Verified but no audition number yet.</p>
      </div>
    </div>

    <!-- Audition Number Pool ── tabbed per division -->
    <template v-if="divisionAuditionStats.length > 0">
      <div class="section-rule mb-3">
        <span class="section-rule-label">Audition Number Pool</span>
        <div class="section-rule-line"></div>
      </div>
      <div class="card-hover p-4 relative mb-6">
        <div class="corner-bar-tl"></div>

        <!-- Division tabs -->
        <div class="tab-bar mb-4">
          <button
            v-for="div in divisionAuditionStats"
            :key="div.name"
            @click="poolTab = div.name"
            class="tab-item"
            :class="{ 'is-active': (poolTab ?? divisionAuditionStats[0]?.name) === div.name }"
          >
            {{ div.name }}
            <span class="tabular-nums opacity-60">{{ div.drawn.length }}/{{ div.total }}</span>
          </button>
        </div>

        <!-- Active division panel -->
        <template v-for="div in divisionAuditionStats" :key="div.name">
          <div v-if="(poolTab ?? divisionAuditionStats[0]?.name) === div.name">
            <!-- Stats row -->
            <div class="flex items-center gap-4 mb-3 flex-wrap">
              <span class="flex items-center gap-1.5 type-label">
                <span class="inline-block w-2 h-2 rounded-full" style="background:var(--accent-color);box-shadow:0 0 6px var(--accent-muted)"></span>
                <span class="text-accent">{{ div.drawn.length }} drawn</span>
              </span>
              <span class="flex items-center gap-1.5 type-label text-content-muted">
                <span class="inline-block w-2 h-2 rounded-full bg-white/15"></span>
                {{ div.remaining.length }} remaining
              </span>
              <!-- Progress bar -->
              <div class="flex-1 min-w-[6rem] h-1 rounded-full bg-white/10 overflow-hidden">
                <div
                  class="h-full rounded-full transition-all duration-500"
                  style="background:var(--accent-color)"
                  :style="{ width: div.total > 0 ? (div.drawn.length / div.total * 100) + '%' : '0%' }"
                ></div>
              </div>
            </div>

            <!-- Number grid: accent = drawn, dim = remaining -->
            <div class="flex flex-wrap gap-1.5">
              <span
                v-for="n in div.total"
                :key="n"
                class="inline-flex items-center justify-center type-label tabular-nums"
                style="width:2.1rem;height:1.7rem;clip-path:polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
                :style="div.drawn.includes(n)
                  ? 'background:var(--accent-subtle);border:1px solid var(--accent-muted);color:var(--accent-color)'
                  : 'background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.07);color:rgba(255,255,255,0.2)'"
              >{{ n }}</span>
            </div>
          </div>
        </template>
      </div>
    </template>

    <!-- Side by side: Check-In + Registered -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">

      <!-- Check-In card -->
      <div class="card-hover p-4 relative flex flex-col h-[520px]">
        <div class="corner-bar-tl"></div>
        <div class="flex items-center justify-between mb-3 shrink-0">
          <div class="flex items-center gap-3">
            <i class="pi pi-users text-content-muted text-xs"></i>
            <span class="type-body text-content-secondary">Check-In</span>
            <span class="badge-warning">{{ checkinList.filter(p => !isCheckedIn(p)).length }} pending</span>
          </div>
        </div>
        <!-- Search bar -->
        <div class="mb-3 shrink-0">
          <div class="relative">
            <i class="pi pi-search absolute left-4 top-1/2 -translate-y-1/2 text-content-muted text-xs pointer-events-none"></i>
            <input
              v-model="checkinSearch"
              type="text"
              placeholder="Search by name or member…"
              autocomplete="off"
              class="input-base"
              style="padding-left: 2.25rem"
            />
            <button v-if="checkinSearch" @click="checkinSearch = ''"
              class="absolute right-2.5 top-1/2 -translate-y-1/2 text-content-muted hover:text-content-secondary transition-colors">
              <i class="pi pi-times text-xs"></i>
            </button>
          </div>
          <p v-if="checkinSearch" class="type-label text-content-muted mt-1.5">
            {{ filteredCheckinList.length }} result{{ filteredCheckinList.length !== 1 ? 's' : '' }}
          </p>
        </div>
        <div class="flex-1 overflow-y-auto space-y-2 min-h-0">
          <div v-if="loadingCheckinList" class="flex items-center justify-center h-full type-label text-content-muted">
            <i class="pi pi-spinner pi-spin mr-2"></i> Loading…
          </div>
          <div v-else-if="checkinList.length === 0" class="flex items-center justify-center h-full type-prose">
            No participants found.
          </div>
          <div v-else-if="filteredCheckinList.length === 0" class="flex items-center justify-center h-full type-prose">
            No results match your search.
          </div>
          <template v-else>
            <div v-for="p in filteredCheckinList" :key="p.participantId"
              class="para-chip p-3 transition-colors"
              :class="isCheckedIn(p) ? 'opacity-50' : ''"
            >
              <div class="flex-1 min-w-0">
                <p class="type-name text-content-secondary truncate">{{ p.label }}</p>
                <!-- Member names for team entries -->
                <div v-if="p.memberNames && p.memberNames.length" class="flex items-center gap-1.5 type-prose text-content-muted mt-0.5">
                  <i class="pi pi-users" style="font-size:0.65rem"></i>
                  <span>{{ p.memberNames.join(', ') }}</span>
                </div>
                <div class="flex flex-wrap gap-1 mt-1">
                  <!-- No divisions assigned -->
                  <span v-if="p.genres.length === 0" class="inline-flex items-center gap-1 badge-neutral">
                    <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" style="box-shadow:0 0 5px rgba(245,158,11,0.7)"></span>
                    <span class="type-label text-amber-400/80">No division assigned</span>
                  </span>
                  <!-- Per-division status: green = linked to division -->
                  <span v-for="g in p.genres" :key="g.genreName"
                    class="inline-flex items-center gap-1 badge-neutral"
                    style="text-transform:none;letter-spacing:0.02em;"
                  >
                    <span
                      class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 shrink-0"
                      style="box-shadow:0 0 5px rgba(52,211,153,0.7)"
                    ></span>
                    {{ g.genreName }}
                    <span v-if="g.auditionNumber !== null" class="text-accent">#{{ g.auditionNumber }}</span>
                  </span>
                </div>
              </div>
              <div class="flex items-center gap-2 mt-2 justify-end">
                <button v-if="!isCheckedIn(p)" @click="askCheckIn(p)"
                  :disabled="checkingInId === p.participantId || !!previewingIds[p.participantId]"
                  class="bg-accent para-chip-sm px-3 py-1.5 type-label disabled:opacity-50"
                >
                  <i class="pi text-xs" :class="checkingInId === p.participantId ? 'pi-spinner pi-spin' : previewingIds[p.participantId] ? 'pi-clock' : 'pi-check'"></i>
                  {{ checkingInId === p.participantId ? '…' : previewingIds[p.participantId] ? 'In Preview' : 'Check In' }}
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
              class="input-base"
              style="width: auto; flex-shrink: 0"
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
          <div v-if="registeredList.length === 0" class="flex items-center justify-center h-full type-prose">
            No registered participants yet.
          </div>
          <div v-else-if="filteredRegisteredList.length === 0" class="flex items-center justify-center h-full type-prose">
            No results match your search.
          </div>
          <template v-else>
            <div v-for="p in filteredRegisteredList" :key="p.name"
              class="para-chip p-3"
            >
              <div class="flex items-center gap-2 flex-wrap">
                <span class="type-name text-content-secondary">{{ p.name }}</span>
                <span v-if="p.walkin" class="badge-neutral">walk-in</span>
                <span v-if="p.referenceCode"
                  class="shrink-0 inline-flex items-center gap-1 px-2 py-0.5 badge-neutral cursor-pointer select-none touch-none"
                  @mousedown="revealingRef = p.name" @mouseup="revealingRef = null" @mouseleave="revealingRef = null"
                  @touchstart.prevent="revealingRef = p.name" @touchend="revealingRef = null" @touchcancel="revealingRef = null"
                >
                  <i class="pi pi-eye text-content-muted" style="font-size:0.65rem"></i>
                  <span v-if="revealingRef === p.name" class="font-source tracking-widest text-accent" style="font-size:0.72rem;letter-spacing:0.15em">{{ p.referenceCode }}</span>
                  <span v-else class="text-content-muted">Ref code</span>
                </span>
              </div>
              <div v-if="p.memberNames.length" class="flex items-center gap-1.5 type-prose text-content-muted mt-0.5">
                <i class="pi pi-users" style="font-size:0.65rem"></i>
                <span>{{ p.memberNames.join(', ') }}</span>
              </div>
              <div class="flex flex-wrap gap-1.5 mt-1">
                <span v-for="e in p.entries" :key="e.genre" class="badge-neutral" style="text-transform:none;letter-spacing:0.02em;">
                  {{ e.genre }}<span style="color:var(--accent-color);margin-left:0.25rem">#{{ e.auditionNumber }}</span>
                </span>
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
    :scrollable="modalScrollable"
    :wide="modalScrollable"
    @accept="handleAccept"
    @close="handleAccept"
  >
    <!-- Import summary badges -->
    <template v-if="importCounts">
      <div class="flex flex-wrap gap-2 mb-5">
        <span v-if="importCounts.imported > 0" class="badge badge-success px-4 py-1.5 text-sm uppercase tracking-wide">{{ importCounts.imported }} added</span>
        <span v-if="importCounts.existing > 0" class="badge badge-neutral px-4 py-1.5 text-sm uppercase tracking-wide">{{ importCounts.existing }} existing</span>
        <span v-if="importCounts.skipped > 0" class="badge badge-warning px-4 py-1.5 text-sm uppercase tracking-wide">{{ importCounts.skipped }} error</span>
        <span v-if="importCounts.imported === 0 && importCounts.existing === 0 && importCounts.skipped === 0" class="text-sm text-content-muted uppercase tracking-wide">No participants found in sheet.</span>
      </div>
    </template>
    <template v-else>
      <p class="type-body text-content-secondary" :class="modalScrollable ? '' : 'mb-0'">{{ modalMessage }}</p>
    </template>

    <!-- Errors -->
    <div v-if="modalErrors.length > 0" class="mb-4">
      <div class="section-rule mb-2">
        <span class="section-rule-label" style="color:#ef4444">errors</span>
        <span class="badge badge-danger type-label px-2 py-0.5 ml-1">{{ modalErrors.length }}</span>
        <div class="section-rule-line"></div>
      </div>
      <div class="space-y-1.5">
        <div
          v-for="(e, i) in modalErrors.slice(0, 20)"
          :key="'err-'+i"
          class="px-3 py-3 border-l-2 border-red-400/40 bg-red-400/5"
        >
          <div class="flex gap-2 items-center">
            <span class="text-xs flex-shrink-0 uppercase tracking-wider" style="color:#ef4444;opacity:0.7">R{{ e.row }}</span>
            <span class="text-sm text-content-secondary uppercase tracking-wide flex-1">{{ e.name }}</span>
          </div>
          <div class="text-xs uppercase tracking-wide mt-1 pl-7" style="color:#ef4444;opacity:0.8">{{ e.reason }}</div>
        </div>
        <div v-if="modalErrors.length > 20" class="text-xs text-content-muted uppercase tracking-wide px-3 py-1">
          + {{ modalErrors.length - 20 }} more errors
        </div>
      </div>
    </div>

    <!-- Skipped (collapsible) -->
    <div v-if="modalWarnings.length > 0" class="mb-2">
      <button
        class="section-rule mb-2 w-full text-left cursor-pointer"
        @click="skippedExpanded = !skippedExpanded"
      >
        <span class="section-rule-label" style="color:#f59e0b">skipped</span>
        <span class="badge badge-warning type-label px-2 py-0.5 ml-1">{{ modalWarnings.length }}</span>
        <div class="section-rule-line"></div>
        <span class="text-xs uppercase tracking-wider flex-shrink-0" style="color:#f59e0b;opacity:0.6">
          {{ skippedExpanded ? '▲' : '▼' }}
        </span>
      </button>
      <div v-if="skippedExpanded" class="space-y-1.5">
        <div
          v-for="(w, i) in modalWarnings.slice(0, 20)"
          :key="'warn-'+i"
          class="px-3 py-3 border-l-2 border-amber-400/40 bg-amber-400/5"
        >
          <div class="flex gap-2 items-center">
            <span class="text-xs flex-shrink-0 uppercase tracking-wider" style="color:#f59e0b;opacity:0.7">R{{ w.row }}</span>
            <span class="text-sm text-content-secondary uppercase tracking-wide flex-1">{{ w.name }}</span>
          </div>
          <div class="text-xs uppercase tracking-wide mt-1 pl-7" style="color:#f59e0b;opacity:0.8">{{ w.reason }}</div>
        </div>
        <div v-if="modalWarnings.length > 20" class="text-xs text-content-muted uppercase tracking-wide px-3 py-1">
          + {{ modalWarnings.length - 20 }} more
        </div>
      </div>
    </div>
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
        class="fixed inset-0 z-50 flex items-end sm:items-center justify-center pb-6 sm:p-4"
      >
        <div class="absolute inset-0 bg-black/80 backdrop-blur-sm" @click="closeAdjustModal" />
        <div class="card-hover relative w-full sm:max-w-md flex flex-col" style="max-height: 85vh;">
          <div class="corner-bar-tl"></div>
          <div class="corner-bar-bl"></div>

          <!-- Header -->
          <div class="flex items-center px-4 py-3 border-b border-surface-600/30 shrink-0">
            <div class="flex items-center gap-2">
              <i class="pi pi-sliders-h text-content-muted text-xs"></i>
              <span class="type-body text-content-primary">Genre Entries</span>
              <span class="badge-neutral" style="text-transform:none;letter-spacing:0.02em;">{{ props.eventName }}</span>
            </div>
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
                  v-for="p in adjustSearchResults"
                  :key="p.participantId"
                  @click="adjustParticipant = p.label; adjustSearch = p.label; adjustParticipantIds = { participantId: p.participantId, eventId: p.eventId }"
                  class="para-chip-sm px-3 py-1.5 type-name-sm transition-all"
                  :class="adjustParticipantIds.participantId === p.participantId ? 'text-accent border-[color:var(--accent-color)]' : 'text-content-secondary hover:text-accent'"
                >{{ p.label }}</button>
              </div>
              <p v-else-if="adjustSearchResults.length === 0 && eligibleParticipants.length === 0" class="type-prose px-1">
                No eligible participants — all have audition numbers.
              </p>
              <p v-else-if="adjustSearchResults.length === 0" class="type-prose px-1">
                No matches.
              </p>
            </div>

            <!-- Selected participant -->
            <template v-if="adjustParticipant">
              <div class="section-rule">
                <span class="type-name text-content-secondary" style="font-size:16px;">{{ adjustParticipant }}</span>
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
                <span class="type-prose" style="color:rgb(251 191 36);">Auditions have started — genre changes locked.</span>
              </div>

              <!-- Genre rows -->
              <div class="space-y-2">
                <div
                  v-for="g in eventGenres"
                  :key="g.name"
                  class="para-chip flex items-center gap-3 px-3 py-2.5 transition-all duration-150"
                  :class="adjustParticipantGenres.some(p => p.genreName === g.name)
                    ? 'border-[color:var(--accent-muted)] bg-[var(--accent-subtle)]'
                    : 'border-surface-600/40'"
                >
                  <!-- Status indicator -->
                  <span
                    class="inline-block w-2 h-2 rounded-full flex-shrink-0"
                    :style="adjustParticipantGenres.some(p => p.genreName === g.name)
                      ? 'background:var(--accent-color);box-shadow:0 0 8px var(--accent-muted)'
                      : 'background:rgba(255,255,255,0.15)'"
                  ></span>

                  <!-- Name + format -->
                  <div class="flex-1 min-w-0">
                    <p class="type-name text-content-primary truncate">{{ g.name }}</p>
                    <p v-if="g.format" class="type-label text-content-muted mt-0.5">{{ g.format }}</p>
                  </div>

                  <!-- Action button -->
                  <button
                    v-if="!adjustParticipantLocked"
                    @click="toggleAdjustGenre(g)"
                    :disabled="adjustLoading"
                    class="para-chip-sm px-3 py-1 type-label transition-all disabled:opacity-40 disabled:cursor-not-allowed flex-shrink-0"
                    :class="adjustParticipantGenres.some(p => p.genreName === g.name)
                      ? 'text-red-400 border-red-500/40 hover:bg-red-500/10'
                      : 'text-accent border-[color:var(--accent-muted)] hover:bg-[var(--accent-subtle)]'"
                  >
                    {{ adjustParticipantGenres.some(p => p.genreName === g.name) ? 'Remove' : 'Add' }}
                  </button>
                </div>
              </div>

              <p v-if="eventGenres.length === 0" class="type-prose text-center py-4">
                No genres configured for this event.
              </p>
            </template>


          </div>
          <p class="type-prose text-center py-2 shrink-0 opacity-60">Tap outside to close</p>
        </div>
      </div>
    </Transition>
  </Teleport>

  <!-- Genre Add Team Form -->
  <Teleport to="body">
    <Transition enter-active-class="transition duration-150 ease-out" enter-from-class="opacity-0" enter-to-class="opacity-100"
                leave-active-class="transition duration-100 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
      <div v-if="genreAddForm.show" class="fixed inset-0 z-[70] flex items-end sm:items-center justify-center pb-6 sm:p-4">
        <div class="absolute inset-0 bg-black/80 backdrop-blur-sm" @click="genreAddForm.show = false" />
        <div class="card-hover relative w-full sm:max-w-sm flex flex-col" style="max-height:85vh">
          <div class="corner-bar-tl"></div>
          <div class="corner-bar-bl"></div>

          <!-- Header -->
          <div class="px-4 py-3 border-b border-surface-600/30 shrink-0">
            <p class="type-body text-content-primary">Add Division</p>
            <p class="type-label text-content-muted mt-0.5">{{ genreAddForm.genre?.name }} · {{ genreAddForm.genre?.format }}</p>
          </div>

          <!-- Body -->
          <div class="flex-1 overflow-y-auto p-4 space-y-4 min-h-0">
            <!-- Solo/Team toggle -->
            <div v-if="genreAddForm.genre?.soloAllowed !== false">
              <label class="type-label text-content-muted mb-2 block">Entry Type</label>
              <div class="flex rounded-xl border border-surface-600/60 overflow-hidden text-sm">
                <button type="button" @click="genreAddForm.entryMode = 'team'"
                  class="flex-1 px-4 py-2 type-label transition-all"
                  :class="genreAddForm.entryMode === 'team' ? 'bg-[var(--accent-subtle)] text-accent border-r border-surface-600/60' : 'bg-surface-800 text-content-secondary hover:bg-surface-700 border-r border-surface-600/60'">
                  Team
                </button>
                <button type="button" @click="genreAddForm.entryMode = 'solo'"
                  class="flex-1 px-4 py-2 type-label transition-all"
                  :class="genreAddForm.entryMode === 'solo' ? 'bg-[var(--accent-subtle)] text-accent' : 'bg-surface-800 text-content-secondary hover:bg-surface-700'">
                  Solo
                </button>
              </div>
            </div>
            <p v-else class="type-prose" style="color:rgb(251 191 36 / 0.85);">Solo entries not allowed — team entry only.</p>

            <!-- Team details -->
            <template v-if="genreAddForm.entryMode === 'team'">
              <div>
                <label class="type-label text-content-muted mb-1.5 block">Team Name</label>
                <input v-model="genreAddForm.teamName" type="text" placeholder="Enter team name…" class="input-base" />
              </div>
              <div v-if="genreAddForm.members.length > 0">
                <label class="type-label text-content-muted mb-1.5 block">Team Members</label>
                <p class="type-prose-sm mb-2">{{ adjustParticipant }} is Member 1. Enter the other {{ genreAddForm.members.length }}.</p>
                <div class="space-y-2">
                  <input v-for="(_, i) in genreAddForm.members" :key="i"
                    v-model="genreAddForm.members[i]"
                    type="text" :placeholder="`Member ${i + 2} stage name…`" class="input-base" />
                </div>
              </div>
            </template>
          </div>

          <!-- Footer -->
          <div class="flex gap-2 justify-end px-4 py-3 border-t border-surface-600/30 shrink-0">
            <button @click="genreAddForm.show = false" class="para-chip-sm px-4 py-2 type-label text-content-muted hover:text-content-primary transition-colors">
              Cancel
            </button>
            <button @click="submitGenreAddForm"
              :disabled="genreAddForm.entryMode === 'team' && !genreAddForm.teamName.trim()"
              class="para-chip-sm px-4 py-2 type-label text-accent border-[color:var(--accent-muted)] hover:bg-[var(--accent-subtle)] transition-all disabled:opacity-40 disabled:cursor-not-allowed">
              Add to Division
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>

  <!-- Confirm Dialog -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-100 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="confirmDialog.show" class="fixed inset-0 z-[60] flex items-center justify-center p-4">
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="confirmNo" />
        <div class="card-hover relative w-full max-w-sm p-5 flex flex-col gap-4">
          <div class="corner-bar-tl"></div>
          <div class="flex items-start gap-3">
            <i class="pi pi-exclamation-triangle text-amber-400 text-sm shrink-0 mt-0.5"></i>
            <div>
              <p class="type-body text-content-primary mb-1">{{ confirmDialog.title }}</p>
              <p class="type-prose">{{ confirmDialog.message }}</p>
            </div>
          </div>
          <div class="flex gap-2 justify-end">
            <button @click="confirmNo" class="para-chip-sm px-4 py-2 type-label text-content-muted hover:text-content-primary transition-colors">
              Cancel
            </button>
            <button
              @click="confirmYes"
              class="para-chip-sm px-4 py-2 type-label transition-all"
              :class="confirmDialog.destructive
                ? 'text-red-400 border-red-500/40 hover:bg-red-500/10'
                : 'text-accent border-[color:var(--accent-muted)] hover:bg-[var(--accent-subtle)]'"
            >
              {{ confirmDialog.confirmLabel }}
            </button>
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

  <!-- Check-In Confirmation Dialog -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="checkinConfirm.show" class="fixed inset-0 z-50 flex items-center justify-center p-4">
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm"
          @click="checkinConfirm.phase !== 'generating' && closeCheckinDialog()" />
        <div class="card-hover p-5 relative w-full max-w-md" style="clip-path:none">
          <div class="corner-bar-tl"></div>
          <div class="corner-bar-bl"></div>

          <!-- Header -->
          <div class="flex items-center justify-between mb-4">
            <p class="type-body text-content-secondary">
              {{ checkinConfirm.phase === 'confirm' ? 'Confirm Check-In' : checkinConfirm.phase === 'generating' ? 'Generating…' : checkinConfirm.phase === 'error' ? 'Check-In Failed' : 'Check-In Complete' }}
            </p>
            <button v-if="checkinConfirm.phase !== 'generating'" @click="closeCheckinDialog"
              class="p-1 text-content-muted hover:text-content-primary transition-colors">
              <i class="pi pi-times text-sm" />
            </button>
          </div>

          <!-- Participant name -->
          <div class="mb-4">
            <p class="type-label text-content-muted mb-0.5">Good Luck</p>
            <p class="type-name text-accent" style="font-size:1.5rem;line-height:1.2">{{ checkinConfirm.participant?.label }}</p>
            <div v-if="checkinConfirm.participant?.memberNames?.length" class="flex items-center gap-1.5 type-prose text-content-muted mt-1.5">
              <i class="pi pi-users" style="font-size:0.65rem"></i>
              <span>{{ checkinConfirm.participant.memberNames.join(' · ') }}</span>
            </div>
          </div>

          <!-- Divisions -->
          <div class="section-rule mb-3">
            <span class="section-rule-label">Divisions</span>
            <div class="section-rule-line"></div>
          </div>
          <div class="space-y-2 mb-5">
            <div v-if="!checkinConfirm.participant?.genres?.length" class="type-label text-amber-400/80">
              No divisions assigned
            </div>
            <div v-for="g in checkinConfirm.participant?.genres" :key="g.genreName"
              class="flex items-center gap-3 para-chip-sm px-3 py-2"
              :style="g.auditionNumber !== null
                ? { borderColor: 'var(--accent-muted)', background: 'var(--accent-subtle)' } : {}"
            >
              <!-- Status dot -->
              <span class="inline-block w-1.5 h-1.5 rounded-full flex-shrink-0"
                :style="g.auditionNumber !== null
                  ? 'background:var(--accent-color);box-shadow:0 0 6px var(--accent-muted)'
                  : g.rolling
                    ? 'background:rgba(245,158,11,0.7);box-shadow:0 0 6px rgba(245,158,11,0.5)'
                    : 'background:rgba(255,255,255,0.15)'">
              </span>
              <span class="type-name text-content-primary flex-1">{{ g.genreName }}</span>
              <!-- Number area -->
              <div class="flex items-baseline gap-1 tabular-nums min-w-[4rem] justify-end">
                <template v-if="g.rolling">
                  <span class="type-label text-amber-400/60 text-xs">Drawing</span>
                  <span class="type-stat text-amber-400" style="font-size:1.4rem">
                    {{ dialogFakeNums[g.genreName] ?? '—' }}
                  </span>
                </template>
                <template v-else-if="g.auditionNumber !== null">
                  <span class="type-label text-accent/60 text-xs">#</span>
                  <span class="type-stat text-accent" style="font-size:1.4rem">{{ g.auditionNumber }}</span>
                </template>
                <template v-else-if="checkinConfirm.phase === 'generating'">
                  <span class="type-stat text-content-muted/20" style="font-size:1.4rem">—</span>
                </template>
                <template v-else>
                  <span class="type-label text-content-muted/40">Pending #</span>
                </template>
              </div>
            </div>
          </div>

          <!-- Ref code (done state only) -->
          <div v-if="checkinConfirm.phase === 'done' && checkinConfirm.refCode" class="mb-5">
            <div class="section-rule mb-3">
              <span class="section-rule-label">Ref Code</span>
              <div class="section-rule-line"></div>
            </div>
            <div class="para-chip-sm px-4 py-3 flex items-center justify-center">
              <span class="font-source tracking-widest text-accent" style="font-size:1.1rem;letter-spacing:0.25em">
                {{ checkinConfirm.refCode }}
              </span>
            </div>
          </div>

          <!-- Actions -->
          <div class="flex gap-3">
            <!-- Confirm phase -->
            <template v-if="checkinConfirm.phase === 'confirm'">
              <button @click="closeCheckinDialog"
                class="flex-1 py-2 para-chip-sm type-label text-content-muted hover:text-content-primary transition-all">
                Cancel
              </button>
              <button @click="confirmCheckIn"
                :disabled="confirming"
                class="flex-1 py-2 para-chip type-label text-white transition-all disabled:opacity-50"
                style="background:rgba(255,255,255,0.12);box-shadow:0 0 12px var(--accent-subtle)"
              >
                <i class="pi pi-check mr-1.5 text-xs"></i> Confirm
              </button>
            </template>
            <!-- Generating phase -->
            <div v-else-if="checkinConfirm.phase === 'generating'"
              class="flex-1 py-2 flex items-center justify-center gap-2 type-label text-content-muted">
              <i class="pi pi-spin pi-spinner text-xs"></i> Generating…
            </div>
            <!-- Error phase (e.g. 409 already checked in) -->
            <div v-else-if="checkinConfirm.phase === 'error'"
              class="flex-1 flex flex-col items-center gap-3">
              <div class="flex items-center gap-2 type-label text-red-400">
                <i class="pi pi-exclamation-circle text-sm"></i>
                {{ checkinConfirm.errorMessage }}
              </div>
              <button @click="checkinConfirm.show = false"
                class="px-4 py-2 para-chip-sm type-label text-content-muted hover:text-content-primary transition-colors">
                Close
              </button>
            </div>
            <!-- Done phase -->
            <button v-else @click="checkinConfirm.show = false"
              class="flex-1 py-2 para-chip type-label text-white transition-all"
              style="background:rgba(255,255,255,0.12);box-shadow:0 0 12px var(--accent-subtle)"
            >
              <i class="pi pi-check mr-1.5 text-xs"></i> Done
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
