<script setup>
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import { getRegisteredParticipantsByEvent, submitParticipantScore, getParticipantScore, whoami, getJudgingMode, setJudgingMode, getFeedbackEnabled, submitAuditionFeedback, getAuditionFeedback, getScoringCriteria, getCategoriesByEvent, getJudgesByDivision, resetJudgeScores, resetJudgeFeedback } from '@/utils/api';
import { getFeedbackGroups } from '@/utils/adminApi';
import { createClient, subscribeToChannel, deactivateClient } from '@/utils/websocket';
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { RouterLink, useRouter, onBeforeRouteLeave } from 'vue-router';
import ActionDoneModal from './ActionDoneModal.vue';
import FeedbackPopout from '@/components/FeedbackPopout.vue';
import { useAuthStore } from '@/utils/auth';
import SwipeableCardsV2 from '@/components/SwipeableCardsV2.vue';
import PairScoreCards from '@/components/PairScoreCards.vue';
import EmceeRoundView from '@/components/EmceeRoundView.vue';
import MiniScoreMenu from '@/components/MiniScoreMenu.vue';
import 'primeicons/primeicons.css'

const authStore = useAuthStore()
const roles = ref(["Emcee", "Judge"])
const selectedEvent = computed(() => authStore.activeEvent?.name || localStorage.getItem("selectedEvent") || "")
const selectedRole = ref(localStorage.getItem("selectedRole") || "")
const selectedCategory = ref(localStorage.getItem("selectedCategory") || "")
const selectedEntryType = ref('Teams') // 'Teams' | 'Solo'
const filteredJudge = ref("")
const currentJudge = ref(localStorage.getItem("currentJudge") || "")
const allJudges = ref([])
const participants = ref([])
const judgingMode = ref("SOLO")
const feedbackEnabled = ref(true)
const eventDivisions = ref([]) // { eventCategoryId, name } for current event — used to map division name → ID
const isAdmin = ref(false)
const isOrganiser = ref(false)
const isEmcee = ref(false)

const modalTitle = ref("")
const modalMessage = ref("")
const modalVariant = ref("info")
const showModal = ref(false)
const showMiniMenu = ref(false)
const dynamicCallBack = ref(() => {})
const emceeRoundRef = ref(null)


const dynamicRole = async () => {
  const res = await whoami()
  const authority = res.role?.[0]?.authority
  if (authority === "ROLE_EMCEE") {
    roles.value = ["Emcee"]
    selectedRole.value = "Emcee"
    isEmcee.value = true
  } else if (authority === "ROLE_JUDGE") {
    roles.value = ["Judge"]
    selectedRole.value = "Judge"
    const jName = res.judgeName || authStore.judgeName
    if (jName) {
      currentJudge.value = jName
      filteredJudge.value = jName
      localStorage.setItem('currentJudge', jName)
    }
  } else if (authority === "ROLE_ORGANISER") {
    roles.value = ["Emcee", "Judge"]
    selectedRole.value = localStorage.getItem("selectedRole") || "Emcee"
    isOrganiser.value = true
  } else if (authority === "ROLE_ADMIN") {
    roles.value = ["Emcee", "Judge"]
    selectedRole.value = localStorage.getItem("selectedRole") || ""
    isAdmin.value = true
  }
}

const hasJudge = computed(() => participants.value.some(item => item.judgeName !== null))

const noJudgesConfigured = computed(() => selectedEvent.value && selectedCategory.value && allJudges.value.length <= 1)

const availableJudges = computed(() => allJudges.value.filter(j => j !== ''))

const pendingJudge = ref(null)
const showJudgeConfirm = ref(false)

const hasTeamAndSoloMix = computed(() => {
  const gp = participants.value.filter(p => p.categoryName === selectedCategory.value)
  const hasTeam = gp.some(p => p.format && p.format !== '1v1')
  const hasSolo = gp.some(p => !p.format)
  return hasTeam && hasSolo
})

const matchesEntryType = (p) => {
  if (!hasTeamAndSoloMix.value) return true
  if (selectedEntryType.value === 'Teams') return p.format && p.format !== '1v1'
  if (selectedEntryType.value === 'Solo') return !p.format
  return true
}

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
  dynamicCallBack.value = async () => {
    showModal.value = false
    await resetScore()
  }
}

const switchCategory = (g) => {
  if (g === selectedCategory.value) return
  const hasUnsaved = filteredParticipantsForJudge.value.some(p => p.score > 0 && !p.submitted)
  if (hasUnsaved) {
    modalTitle.value = `Switch to ${g}?`
    modalMessage.value = `You have unsaved scores for ${selectedCategory.value}. They're cached and won't be lost, but submit them first to be safe.`
    modalVariant.value = 'warning'
    showModal.value = true
    dynamicCallBack.value = () => { showModal.value = false; selectedCategory.value = g }
  } else {
    selectedCategory.value = g
  }
}

const confirmSubmit = async (title, message) => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = 'info'
  showModal.value = true
  dynamicCallBack.value = async () => {
    showModal.value = false;
    await submitScore(selectedEvent.value, selectedCategory.value,
      currentJudge.value, filteredParticipantsForJudge.value);
  }
}

const filteredParticipantsForJudge = computed({
  get() {
    // For pure judges (not admin/organiser), the filter identity is always the current judge.
    // Admin/organiser can set filteredJudge independently via a dropdown.
    const effectiveJudge = (!isAdmin.value && !isOrganiser.value && currentJudge.value)
      ? currentJudge.value
      : filteredJudge.value
    return participants.value
      .filter(p => {
        if (p.categoryName !== selectedCategory.value) return false
        if (p.auditionNumber === null) return false
        if (!matchesEntryType(p)) return false
        // If no participants have per-participant judge assignments, use event-category scope
        if (!hasJudge.value) return true
        // Otherwise, filter by per-participant judgeName
        return p.judgeName === (effectiveJudge === "" ? null : effectiveJudge)
      })
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

// Load saved scores from DB and apply to participants.
// aspect == "" → single score mode; aspect != "" → criteria mode (one row per criterion).
const loadScoresFromDb = async (event, category, judge) => {
  if (!event || !category || !judge) return
  // Guard: don't apply scores if participants for this category haven't loaded yet.
  // The selectedCategory watcher (immediate) can fire before the selectedEvent watcher
  // finishes fetching participants. Without this check, scores are applied to an
  // empty array via .map(), then participants load with score:0 overwriting everything.
  // The selectedEvent watcher will call loadScoresFromDb again after participants arrive.
  if (!participants.value.some(p => p.categoryName === category)) return
  const allScores = await getParticipantScore(event)
  if (!allScores || !Array.isArray(allScores)) return

  const relevant = allScores.filter(s => s.judgeName === judge && s.categoryName === category)
  if (relevant.length === 0) return

  const byParticipant = {}
  for (const s of relevant) {
    if (!byParticipant[s.participantName]) byParticipant[s.participantName] = { score: 0, criteriaScores: {} }
    if (s.aspect && s.aspect !== '') {
      byParticipant[s.participantName].criteriaScores[s.aspect] = s.score
    } else {
      byParticipant[s.participantName].score = s.score
    }
  }

  participants.value = participants.value.map(p => {
    if (p.categoryName !== category) return p
    const saved = byParticipant[p.participantName]
    if (!saved) return p
    const hasCriteria = Object.keys(saved.criteriaScores).length > 0
    return {
      ...p,
      score: hasCriteria ? p.score : saved.score,
      criteriaScores: hasCriteria ? { ...(p.criteriaScores ?? {}), ...saved.criteriaScores } : p.criteriaScores,
      submitted: true
    }
  })
}

const filteredParticipantsForEmceeView = computed(() => {
  // If no participants have per-participant judge assignments, use event-category scope.
  // Otherwise, respect the admin/org's judge filter dropdown.
  const base = (!hasJudge.value || filteredJudge.value === "")
    ? participants.value.filter(p => p.categoryName === selectedCategory.value && p.auditionNumber !== null && matchesEntryType(p))
    : participants.value.filter(p => p.categoryName === selectedCategory.value && p.judgeName === filteredJudge.value && p.auditionNumber !== null && matchesEntryType(p))
  return base.sort((a, b) => a.auditionNumber - b.auditionNumber)
})


watch(selectedEvent, async (newVal, oldVal) => {
  if (newVal) {
    localStorage.setItem("selectedEvent", newVal);
    if (oldVal !== undefined && oldVal !== newVal) {
      selectedCategory.value = ""
    }
    participants.value = []
    const [res, modeRes, divRes, feedbackRes] = await Promise.all([
      getRegisteredParticipantsByEvent(newVal),
      getJudgingMode(newVal),
      getCategoriesByEvent(newVal),
      getFeedbackEnabled(newVal)
    ])
    eventDivisions.value = divRes ?? []
    // Now that divisions are loaded, reload judges for the selected category (fixes race on page load)
    if (selectedCategory.value) {
      const div = (divRes ?? []).find(d => d.name.toLowerCase() === selectedCategory.value.toLowerCase())
      if (div) {
        const judges = await getJudgesByDivision(newVal, div.eventCategoryId)
        const judgeNames = judges.map(j => j.judgeName)
        allJudges.value = ["", ...judgeNames]
        const saved = localStorage.getItem("currentJudge")
        if (saved && judgeNames.includes(saved)) {
          currentJudge.value = saved
        } else {
          currentJudge.value = ""
          localStorage.removeItem("currentJudge")
        }
      } else {
        allJudges.value = [""]
        currentJudge.value = ""
        localStorage.removeItem("currentJudge")
      }
    } else {
      allJudges.value = [""]
      currentJudge.value = ""
      localStorage.removeItem("currentJudge")
    }
    if (!authStore.judgeName) filteredJudge.value = ""
    if (selectedEvent.value !== newVal) return
    participants.value = res.map((r, i) => ({ ...r, rowId: r.rowId ?? i, score: 0 }))
    // Auto-select the first category assigned to a session-link judge who has no category selected yet
    if (!selectedCategory.value && authStore.judgeName) {
      const myCategory = res.find(p => p.judgeName === authStore.judgeName)?.categoryName
      if (myCategory) selectedCategory.value = myCategory
    }
    // Fallback: if still no category selected, pick the first category from participants or event divisions
    if (!selectedCategory.value) {
      const firstCategory = res.find(p => p.categoryName)?.categoryName
        || (divRes && divRes.length > 0 ? divRes[0].name : null)
      if (firstCategory) selectedCategory.value = firstCategory
    }
    if (modeRes?.judgingMode) judgingMode.value = modeRes.judgingMode
    if (feedbackRes && typeof feedbackRes.feedbackEnabled === 'boolean') feedbackEnabled.value = feedbackRes.feedbackEnabled
    await loadScoresFromDb(newVal, selectedCategory.value, currentJudge.value)
  }
}, { immediate: true });

watch(selectedCategory, async (newVal) => {
  if (newVal) localStorage.setItem("selectedCategory", newVal)
  else localStorage.removeItem("selectedCategory")
  selectedEntryType.value = 'Teams'
  if (!newVal || !selectedEvent.value) return
  if (eventDivisions.value.length === 0) {
    // Divisions not loaded yet (race on page load) — selectedEvent watcher will reload judges once they arrive
    return
  }
  const division = eventDivisions.value.find(d => d.name.toLowerCase() === newVal.toLowerCase())
  if (division) {
    const judges = await getJudgesByDivision(selectedEvent.value, division.eventCategoryId)
    const judgeNames = judges.map(j => j.judgeName)
    allJudges.value = ["", ...judgeNames]
    const sessionJudge = authStore.judgeName
    if (!judgeNames.includes(currentJudge.value)) {
      if (sessionJudge && judgeNames.includes(sessionJudge)) {
        currentJudge.value = sessionJudge
        localStorage.setItem('currentJudge', sessionJudge)
      } else if (!sessionJudge || currentJudge.value !== sessionJudge) {
        // Only clear if currentJudge doesn't match a session-linked judge identity.
        // Session-linked judges keep their identity even if not in this division's explicit judge list.
        currentJudge.value = ""
        localStorage.removeItem("currentJudge")
      }
    }
    if (!judgeNames.includes(filteredJudge.value)) {
      if (sessionJudge && judgeNames.includes(sessionJudge)) {
        filteredJudge.value = sessionJudge
      } else if (!sessionJudge || filteredJudge.value !== sessionJudge) {
        filteredJudge.value = ""
      }
    }
    await loadScoresFromDb(selectedEvent.value, newVal, currentJudge.value)
  } else {
    allJudges.value = [""]
    currentJudge.value = ""
    filteredJudge.value = ""
    localStorage.removeItem("currentJudge")
  }
}, { immediate: true });
watch(selectedRole, (newVal) => { if (newVal) localStorage.setItem("selectedRole", newVal); }, { immediate: true });
watch(currentJudge, async (newVal) => {
  if (newVal) {
    localStorage.setItem("currentJudge", newVal)
    await loadScoresFromDb(selectedEvent.value, selectedCategory.value, newVal)
  }
}, { immediate: true });

const uniqueCategories = computed(() => {
  const categories = participants.value.map(p => p.categoryName);
  return [...new Set(categories)].sort();
})

const currentDivision = computed(() =>
  eventDivisions.value.find(d => d.name.toLowerCase() === selectedCategory.value.toLowerCase()) ?? null
)

const submitScore = async (eventName, categoryName, judgeName, participantList) => {
  scoreError.value = ''
  if (judgeName === "") {
    openModal("Missing Judge", "Please select a judge before submitting.", "warning")
    return
  }
  const hasCriteria = criteria.value.length > 0
  const p = participantList.map(obj => {
    if (hasCriteria && obj.criteriaScores) {
      return {
        participantName: obj.participantName,
        auditionNumber: obj.auditionNumber,
        score: null,
        aspects: criteria.value.map(c => ({ aspect: c.name, score: parseFloat(obj.criteriaScores[c.name] ?? 0) }))
      }
    }
    return { participantName: obj.participantName, auditionNumber: obj.auditionNumber, score: parseFloat(obj.score) }
  })
  if (p.length === 0) {
    openModal("No Participants", "No participants found for this judge/category combination. Select a judge filter to view your assigned participants.", "warning")
    return
  }
  try {
    const res = await submitParticipantScore(eventName, categoryName, judgeName, p)
    if (res?.ok) {
      participants.value = participants.value.map(obj =>
        participantList.some(pl => pl.auditionNumber === obj.auditionNumber && pl.categoryName === obj.categoryName)
          ? { ...obj, submitted: true }
          : obj
      )
      openModal("Scores Submitted", "All scores have been saved successfully.", "success")
    } else {
      const errText = res ? await res.text().catch(() => "") : "Network error"
      scoreError.value = `Could not save scores (${res?.status ?? "no response"}). ${errText}`
      openModal("Submit Failed", scoreError.value, "warning")
    }
  } catch (e) {
    scoreError.value = `Score submission failed: ${e.message}`
    openModal("Submit Failed", scoreError.value, "error")
  }
}

const resetScore = async () => {
  // Only reset scores for the currently selected category
  participants.value = participants.value.map(obj => {
    if (obj.categoryName !== selectedCategory.value) return obj
    const cs = {}
    if (obj.criteriaScores) {
      Object.keys(obj.criteriaScores).forEach(k => { cs[k] = 0 })
    }
    return { ...obj, score: 0, criteriaScores: cs, submitted: false }
  })
  if (currentJudge.value && selectedEvent.value && selectedCategory.value) {
    await Promise.all([
      resetJudgeScores(selectedEvent.value, selectedCategory.value, currentJudge.value),
      resetJudgeFeedback(selectedEvent.value, selectedCategory.value, currentJudge.value),
    ])
    // Clear in-memory feedback for the current category
    const categoryAuditionNums = new Set(
      participants.value
        .filter(p => p.categoryName === selectedCategory.value)
        .map(p => p.auditionNumber)
    )
    const newMap = new Map(feedbackGiven.value)
    for (const key of newMap.keys()) {
      if (categoryAuditionNums.has(key)) newMap.delete(key)
    }
    feedbackGiven.value = newMap
  }
}

// ── Scoring criteria ─────────────────────────────────────────────────────────
const criteria = ref([])

const loadCriteria = async () => {
  if (!selectedEvent.value || !selectedCategory.value) { criteria.value = []; return }
  criteria.value = await getScoringCriteria(selectedEvent.value, selectedCategory.value)
  // Re-initialize criteriaScores on each participant for the active category
  participants.value = participants.value.map(p => {
    if (p.categoryName !== selectedCategory.value) return p
    const cs = {}
    criteria.value.forEach(c => { cs[c.name] = p.criteriaScores?.[c.name] ?? 0 })
    return { ...p, criteriaScores: cs }
  })
}

watch([selectedEvent, selectedCategory], loadCriteria, { immediate: true })

// ── Auto-save on score change ─────────────────────────────────────────────────
const autoSaveTimers = {}
const clearSaving = (auditionNumber, categoryName, participantName, submitted = false) => {
  participants.value = participants.value.map(p =>
    p.auditionNumber === auditionNumber && p.categoryName === categoryName && p.participantName === participantName
      ? { ...p, saving: false, submitted }
      : p
  )
}
const autoSave = (card) => {
  const { auditionNumber, categoryName, participantName } = card
  participants.value = participants.value.map(p =>
    p.auditionNumber === auditionNumber && p.categoryName === categoryName && p.participantName === participantName
      ? { ...p, saving: true, submitted: false }
      : p
  )
  clearTimeout(autoSaveTimers[participantName])
  autoSaveTimers[participantName] = setTimeout(async () => {
    if (!currentJudge.value || !selectedEvent.value || !selectedCategory.value) {
      clearSaving(auditionNumber, categoryName, participantName)
      return
    }
    const fresh = participants.value.find(p => p.auditionNumber === auditionNumber && p.categoryName === categoryName && p.participantName === participantName)
    if (!fresh) { clearSaving(auditionNumber, categoryName, participantName); return }
    const hasCrit = criteria.value.length > 0
    const payload = hasCrit && fresh.criteriaScores
      ? { participantName, auditionNumber, score: null, aspects: criteria.value.map(c => ({ aspect: c.name, score: parseFloat(fresh.criteriaScores[c.name] ?? 0) })) }
      : { participantName, auditionNumber, score: parseFloat(fresh.score) }
    try {
      const res = await submitParticipantScore(selectedEvent.value, selectedCategory.value, currentJudge.value, [payload])
      clearSaving(auditionNumber, categoryName, participantName, !!res?.ok)
    } catch {
      clearSaving(auditionNumber, categoryName, participantName)
    }
  }, 600)
}
// ─────────────────────────────────────────────────────────────────────────────

// ── Feedback state ──────────────────────────────────────────────────────────
const tagGroups = ref([])
const feedbackGiven = ref(new Map())  // auditionNumber → { tagIds, note, tagLabels: [{id, label}] }
const feedbackPopout = ref({ visible: false, participant: null, existing: null })
const feedbackSaving = ref(false)

const resolveTagLabels = (tagIds) => {
  const labels = []
  for (const group of tagGroups.value) {
    for (const tag of group.tags ?? []) {
      if (tagIds.includes(tag.id)) labels.push({ id: tag.id, label: tag.label })
    }
  }
  return labels
}

const openFeedbackPopout = (card) => {
  const existing = feedbackGiven.value.get(card.auditionNumber)
  feedbackPopout.value = {
    visible: true,
    participant: card,
    existing: existing ? { tagIds: existing.tagIds, note: existing.note } : null
  }
}

const saveFeedback = async ({ tagIds, note }) => {
  const card = feedbackPopout.value.participant
  await submitAuditionFeedback(
    selectedEvent.value, selectedCategory.value, currentJudge.value,
    card.auditionNumber, tagIds, note
  )
  const newMap = new Map(feedbackGiven.value)
  if (tagIds.length || note) {
    newMap.set(card.auditionNumber, { tagIds, note: note ?? null, tagLabels: resolveTagLabels(tagIds) })
  } else {
    newMap.delete(card.auditionNumber)
  }
  feedbackGiven.value = newMap
  feedbackPopout.value.visible = false
}

const autoSaveFeedback = async ({ tagIds, note }) => {
  const card = feedbackPopout.value.participant
  if (!card || !currentJudge.value || !selectedEvent.value || !selectedCategory.value) return
  feedbackSaving.value = true
  try {
    await submitAuditionFeedback(
      selectedEvent.value, selectedCategory.value, currentJudge.value,
      card.auditionNumber, tagIds, note
    )
    const newMap = new Map(feedbackGiven.value)
    if (tagIds.length || note) {
      newMap.set(card.auditionNumber, { tagIds, note: note ?? null, tagLabels: resolveTagLabels(tagIds) })
    } else {
      newMap.delete(card.auditionNumber)
    }
    feedbackGiven.value = newMap
  } finally {
    feedbackSaving.value = false
  }
}

const removeTag = async ({ auditionNumber, tagId }) => {
  const existing = feedbackGiven.value.get(auditionNumber)
  if (!existing) return
  const newTagIds = existing.tagIds.filter(id => id !== tagId)
  await submitAuditionFeedback(
    selectedEvent.value, selectedCategory.value, currentJudge.value,
    auditionNumber, newTagIds, existing.note
  )
  const newMap = new Map(feedbackGiven.value)
  if (newTagIds.length || existing.note) {
    newMap.set(auditionNumber, { tagIds: newTagIds, note: existing.note, tagLabels: resolveTagLabels(newTagIds) })
  } else {
    newMap.delete(auditionNumber)
  }
  feedbackGiven.value = newMap
}

// Reload feedback data when judge or category changes
const loadFeedbackGiven = async () => {
  if (!selectedEvent.value || !selectedCategory.value || !currentJudge.value) {
    feedbackGiven.value = new Map()
    return
  }
  const newMap = new Map()
  for (const p of filteredParticipantsForJudge.value) {
    const fb = await getAuditionFeedback(selectedEvent.value, selectedCategory.value, currentJudge.value, p.auditionNumber)
    if (fb && (fb.tagIds?.length || fb.note)) {
      newMap.set(p.auditionNumber, {
        tagIds: fb.tagIds ?? [],
        note: fb.note ?? null,
        tagLabels: resolveTagLabels(fb.tagIds ?? [])
      })
    }
  }
  feedbackGiven.value = newMap
}

watch([currentJudge, selectedCategory], loadFeedbackGiven)
// ───────────────────────────────────────────────────────────────────────────

const showFilters = ref(false)
const scoreError = ref('')
const hasActiveSession = computed(() => !!selectedCategory.value && !!selectedRole.value)
const router = useRouter()
const isJudgeSession = computed(() => !!authStore.judgeName && !!authStore.judgeId)

const wsClients = []

watch(hasActiveSession, (active) => {
  if (active) {
    document.documentElement.style.overflow = 'hidden'
    document.documentElement.style.height = '100dvh'
    document.documentElement.style.touchAction = 'manipulation'
    document.body.style.position = 'fixed'
    document.body.style.overflow = 'hidden'
    document.body.style.width = '100%'
    document.body.style.height = '100dvh'
    window.scrollTo(0, 0)
  } else {
    document.documentElement.style.overflow = ''
    document.documentElement.style.height = ''
    document.documentElement.style.touchAction = ''
    document.body.style.position = ''
    document.body.style.overflow = ''
    document.body.style.width = ''
    document.body.style.height = ''
  }
}, { immediate: true })

// Reset the audition timer when navigating away (e.g., to Score page).
// The timer slides up and out, then navigation proceeds.
onBeforeRouteLeave(() => {
  emceeRoundRef.value?.resetTimer()
  return new Promise(resolve => setTimeout(resolve, 300))
})

onUnmounted(() => {
  wsClients.forEach(c => deactivateClient(c));
  [document.documentElement, document.body].forEach(el => {
    el.style.overflow = ''
    el.style.height = ''
  })
  document.body.style.position = ''
  document.body.style.width = ''
  document.documentElement.style.touchAction = ''
})

onMounted(async () => {
  await dynamicRole()
  const [groupRes, divRes] = await Promise.all([getFeedbackGroups(), getCategoriesByEvent(selectedEvent.value)])
  tagGroups.value = groupRes ?? []
  eventDivisions.value = divRes ?? []
  // Judge list will be populated by the selectedCategory watch (already runs with { immediate: true })

  // On page refresh, judge/category/event are restored from localStorage so the watch
  // on [currentJudge, selectedCategory] never fires. Load feedback once participants arrive.
  if (selectedEvent.value && selectedCategory.value && currentJudge.value) {
    const stopOnce = watch(participants, (list) => {
      if (list.length > 0) { loadFeedbackGiven(); stopOnce() }
    }, { immediate: true })
  }

  const c1 = createClient()
  wsClients.push(c1)
  subscribeToChannel(c1, "/topic/audition/",
    (msg) => {
      if (msg.eventName && msg.eventName !== selectedEvent.value) return
      const idx = participants.value.findIndex(p =>
        p.participantName === msg.name &&
        p.categoryName === msg.category &&
        (p.judgeName === null ? 1 : p.judgeName === msg.judge)
      )
      if (idx !== -1) {
        participants.value[idx] = { ...participants.value[idx], auditionNumber: msg.auditionNumber }
      } else if (msg.eventName) {
        participants.value.push({
          participantName: msg.name,
          categoryName: msg.category,
          judgeName: msg.judge || null,
          auditionNumber: msg.auditionNumber,
          eventName: msg.eventName,
          participantId: msg.participantId,
          eventId: msg.eventId,
          categoryId: msg.categoryId,
          walkin: msg.walkin,
          score: 0,
          format: msg.format || null,
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
        p.participantName === msg.name && p.categoryName === msg.category
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
        !(p.participantName === msg.name && p.categoryName === msg.category &&
          (msg.judge ? p.judgeName === msg.judge : true))
      )
    })
  const c4 = createClient()
  wsClients.push(c4)
  subscribeToChannel(c4, "/topic/judging-mode/",
    (msg) => {
      if (msg.eventName !== selectedEvent.value) return
      judgingMode.value = msg.judgingMode
    })
  const c5 = createClient()
  wsClients.push(c5)
  subscribeToChannel(c5, "/topic/feedback-enabled/",
    (msg) => {
      if (msg.eventName !== selectedEvent.value) return
      feedbackEnabled.value = !!msg.feedbackEnabled
    })
})
</script>

<template>
  <div class="page-container relative" :style="hasActiveSession ? { display: 'flex', flexDirection: 'column', height: 'calc(100dvh - 64px)', overflow: 'hidden', padding: '8px 16px' } : {}">
    <div class="color-bleed"></div>

    <!-- Context bar (active session: category + role both selected) -->
    <div
      v-if="hasActiveSession"
      class="para-chip px-4 py-2.5 mb-4 flex-shrink-0 flex items-center justify-between"
    >
      <div class="flex items-center gap-2 type-name-sm text-content-muted flex-wrap">
        <button
          v-if="isJudgeSession"
          @click="router.push({ name: 'JudgeSession' })"
          class="type-label text-accent hover:text-content-primary transition-colors whitespace-nowrap"
        >← SESSION</button>
        <span v-if="isJudgeSession" class="text-content-muted opacity-30">·</span>
        <span v-if="isAdmin || isOrganiser" class="text-accent">{{ selectedEvent }}</span>
        <span v-if="isAdmin || isOrganiser" class="text-content-muted opacity-30">·</span>
        <span>{{ selectedCategory }}</span>
        <template v-if="isAdmin || isOrganiser">
          <span class="text-content-muted opacity-30">·</span>
          <span class="type-label">{{ judgingMode }}</span>
        </template>
        <span v-if="!isJudgeSession" class="text-content-muted opacity-30">·</span>
        <span v-if="!isJudgeSession" class="type-label">{{ selectedRole }}</span>
        <template v-if="isEmcee && selectedCategory">
          <span class="text-content-muted opacity-30">·</span>
          <button
            class="type-label text-content-muted hover:text-content-primary transition-colors"
            @click="selectedCategory = ''"
          >‹ CHANGE CATEGORY</button>
        </template>
      </div>
      <div class="flex items-center gap-1 flex-shrink-0">
        <template v-if="selectedRole === 'Judge' || isJudgeSession">
          <!-- Destructive action: red hover distinguishes reset from neutral controls; aria-label for icon-only -->
          <button
            @click="confirmReset('Reset Scores', 'Are you sure you want to reset all scores? This cannot be undone.')"
            class="para-chip-sm px-3 py-2.5 sm:px-2 sm:py-1 type-label text-content-muted hover:text-red-400 transition-all"
            style="min-width:44px"
            title="Reset scores"
            aria-label="Reset all scores"
          ><i class="pi pi-undo text-sm sm:text-xs" aria-hidden="true"></i></button>
          <button
            @click="showMiniMenu = true"
            class="para-chip-sm px-3 py-2.5 sm:px-2 sm:py-1 type-label text-content-muted hover:text-content-primary transition-all"
            style="min-width:44px"
            title="Go to participant"
            aria-label="Jump to participant"
          ><i class="pi pi-search text-sm sm:text-xs" aria-hidden="true"></i></button>
        </template>
        <button
          v-if="isAdmin || isOrganiser || isEmcee"
          @click="showFilters = !showFilters"
          :aria-expanded="showFilters"
          aria-label="Toggle filters"
          class="para-chip-sm px-3 py-2.5 sm:px-2 sm:py-1 type-label text-content-muted hover:text-content-primary transition-all"
          style="min-width:44px"
        >
          <i class="pi pi-sliders-h text-sm sm:text-xs" aria-hidden="true"></i>
        </button>
      </div>
    </div>

    <!-- Page header (no active session yet) -->
    <div v-else class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-6">
      <div>
        <!-- h1 for document outline -->
        <h1 class="type-page-title mb-1">Audition List</h1>
        <p class="type-label text-content-muted">
          {{ selectedRole === 'Judge' ? 'Score participants for your category' : 'Track audition progress' }}
        </p>
      </div>
      <button
        @click="showFilters = !showFilters"
        :aria-expanded="showFilters"
        class="para-chip-sm px-3 py-1.5 type-label text-content-muted hover:text-content-primary transition-all duration-200 self-start"
      >
        <i class="pi text-xs" :class="showFilters ? 'pi-filter-slash' : 'pi-filter'" aria-hidden="true"></i>
        Filters
      </button>
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
      <div v-if="showFilters || !hasActiveSession" class="card p-5" :class="hasActiveSession ? 'fixed left-0 right-0 z-40 mx-4' : 'mb-6'" :style="hasActiveSession ? { top: '138px', background: '#1a1a1a', borderColor: 'rgba(255,255,255,0.12)', boxShadow: '0 0 0 1px rgba(255,255,255,0.08), 0 20px 60px rgba(0,0,0,0.9)', clipPath: 'none' } : { clipPath: 'none' }">
        <!-- Mobile: vertical stack; Tablet+: horizontal wrap -->
        <div class="flex flex-col sm:flex-row sm:flex-wrap sm:items-center gap-4 sm:gap-3">
          <!-- Event name (hidden for session judges — locked) -->
          <span v-if="!isJudgeSession" class="type-name text-content-primary whitespace-nowrap">{{ selectedEvent }}</span>
          <span v-if="!isJudgeSession" class="text-surface-600 select-none hidden sm:inline">|</span>

          <!-- Role toggle (hidden for session judges and emcees — role is fixed) -->
          <div v-if="!isJudgeSession && !isEmcee" class="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-1">
            <span class="section-rule-label sm:hidden">Role</span>
            <div class="flex flex-wrap gap-2 sm:gap-1">
              <button
                v-for="r in roles"
                :key="r"
                @click="selectedRole = r"
                :aria-pressed="selectedRole === r"
                class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1 type-label transition-all duration-150"
                :class="selectedRole === r
                  ? 'text-accent border-[color:var(--accent-muted)]'
                  : 'text-content-muted hover:text-content-primary'"
              >{{ r }}</button>
            </div>
          </div>
          <span v-if="!isJudgeSession && (isAdmin || isOrganiser)" class="text-surface-600 select-none hidden sm:inline">|</span>

          <!-- Category toggle (hidden for session judges — locked to assigned category) -->
          <div v-if="isAdmin || isOrganiser || (isEmcee && !selectedCategory)" class="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-1">
            <span class="section-rule-label sm:hidden">Category</span>
            <div class="flex flex-wrap gap-2 sm:gap-1">
              <button
                v-for="g in uniqueCategories"
                :key="g"
                @click="switchCategory(g)"
                :aria-pressed="selectedCategory === g"
                class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1 type-name-sm transition-all duration-150"
                :class="selectedCategory === g
                  ? 'text-accent border-[color:var(--accent-muted)]'
                  : 'text-content-muted hover:text-content-primary'"
              >{{ g }}</button>
            </div>
          </div>

          <!-- Type toggle (conditional) -->
          <template v-if="hasTeamAndSoloMix">
            <span class="text-surface-600 select-none hidden sm:inline">|</span>
            <div class="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-1">
              <span class="section-rule-label sm:hidden">Type</span>
              <div class="flex gap-2 sm:gap-1">
                <button
                  v-for="t in ['Teams', 'Solo']"
                  :key="t"
                  @click="selectedEntryType = t"
                  :aria-pressed="selectedEntryType === t"
                  class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1 type-label transition-all duration-150"
                  :class="selectedEntryType === t
                    ? 'text-accent border-[color:var(--accent-muted)]'
                    : 'text-content-muted hover:text-content-primary'"
                >{{ t }}</button>
              </div>
            </div>
          </template>

          <!-- Judging mode toggle (admin only) -->
          <template v-if="isAdmin && selectedEvent">
            <span class="text-surface-600 select-none hidden sm:inline">|</span>
            <div class="flex flex-col sm:flex-row sm:items-center gap-2 sm:gap-1">
              <span class="section-rule-label sm:hidden">Mode</span>
              <div class="flex gap-2 sm:gap-1">
                <button
                  v-for="m in ['SOLO', 'PAIR']"
                  :key="m"
                  @click="judgingMode = m; setJudgingMode(selectedEvent, m)"
                  :aria-pressed="judgingMode === m"
                  class="para-chip-sm px-4 sm:px-3 py-3 sm:py-1 type-label transition-all duration-150"
                  :class="judgingMode === m
                    ? 'text-accent border-[color:var(--accent-muted)]'
                    : 'text-content-muted hover:text-content-primary'"
                >{{ m }}</button>
              </div>
            </div>
          </template>

          <!-- Judge filter + identity dropdowns: full-width on mobile, right-aligned on sm+ -->
          <div class="flex flex-col sm:flex-row sm:ml-auto sm:items-center gap-3 w-full sm:w-auto">
            <div v-if="hasJudge && (isAdmin || isOrganiser)" class="w-full sm:w-48">
              <ReusableDropdown v-model="filteredJudge" labelId="Judge" :options="allJudges" />
            </div>
            <div v-if="selectedRole === 'Judge' && (isAdmin || isOrganiser)" class="w-full sm:min-w-[12rem] sm:max-w-[16rem]">
              <ReusableDropdown v-model="currentJudge" labelId="You are judging as" :options="allJudges" />
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- No judges banner -->
    <div
      v-if="noJudgesConfigured"
      class="semantic-chip-warning flex items-center gap-3 px-4 py-3 mb-4 flex-shrink-0"
    >
      <div class="w-2 h-2 rounded-full bg-amber-400 flex-shrink-0" style="box-shadow: 0 0 6px rgba(245,158,11,0.8)"></div>
      <span class="type-label text-amber-300/90">
        No judges configured for <span class="text-amber-200">{{ selectedCategory }}</span>.
      </span>
      <RouterLink
        :to="`/events/${encodeURIComponent(selectedEvent)}`"
        class="ml-auto flex-shrink-0 type-label text-accent underline underline-offset-2 transition-colors"
      >
        Add judges →
      </RouterLink>
    </div>

    <!-- Score error banner — role=alert announces the failure immediately -->
    <div
      v-if="scoreError"
      role="alert"
      class="flex items-center gap-3 px-4 py-3 mb-4"
      style="border-left:3px solid rgb(239 68 68);background:rgba(239,68,68,0.1)"
    >
      <span class="inline-block w-2 h-2 rounded-full bg-red-400 flex-shrink-0" style="box-shadow:0 0 6px rgba(239,68,68,0.8)"></span>
      <span class="type-label text-red-300/90 flex-1">{{ scoreError }}</span>
      <button
        @click="scoreError = ''"
        class="para-chip-sm px-2.5 py-1 type-label text-content-muted hover:text-content-primary transition-colors flex-shrink-0"
      >DISMISS</button>
    </div>

    <!-- Emcee view: Timer + round view -->
    <div class="flex-1 min-h-0" style="overflow: hidden;">
    <template v-if="selectedRole === 'Emcee' && filteredParticipantsForEmceeView.length > 0">
      <EmceeRoundView
        ref="emceeRoundRef"
        :key="selectedCategory"
        :participants="filteredParticipantsForEmceeView"
        :mode="judgingMode"
        :eventName="selectedEvent"
        :categoryName="selectedCategory"
        :roundLabel="currentDivision?.roundLabel ?? null"
        :numberColor="currentDivision?.numberColor ?? null"
      />
    </template>

    <!-- Emcee: no participants in this category -->
    <div
      v-else-if="selectedRole === 'Emcee' && selectedCategory"
      class="flex flex-col items-center justify-center h-full text-center"
    >
      <div class="para-chip-sm w-14 h-14 flex items-center justify-center mb-4">
        <i class="pi pi-list text-content-muted text-xl"></i>
      </div>
      <p class="type-body text-content-secondary">No participants in {{ selectedCategory }}</p>
      <p class="type-prose-sm mt-1">Participants will appear here once they're checked in and have audition numbers.</p>
      <div class="mt-4 px-5 py-3" style="border-left:3px solid rgba(245,158,11,0.8);background:rgba(245,158,11,0.07);clip-path:polygon(8px 0%,100% 0%,calc(100% - 8px) 100%,0% 100%)">
        <p class="type-label text-amber-300/80">
          {{ participants.filter(p => p.categoryName === selectedCategory).length }} total in {{ selectedCategory }}
        </p>
      </div>
    </div>

    <!-- Judge: no identity selected -->
    <div
      v-else-if="selectedRole === 'Judge' && !currentJudge"
      class="flex flex-col items-center justify-center py-16 text-center gap-6 px-4"
    >
      <div>
        <p class="type-page-title text-content-primary mb-1" style="font-size:1.4rem">Who are you?</p>
        <p class="type-prose">Select your name to start scoring.</p>
      </div>

      <!-- No judges configured -->
      <div v-if="availableJudges.length === 0" class="flex flex-col items-center gap-3">
        <div
          class="px-5 py-4 flex items-center gap-3"
          style="border-left:3px solid rgba(245,158,11,0.8);background:rgba(245,158,11,0.07);clip-path:polygon(8px 0%,100% 0%,calc(100% - 8px) 100%,0% 100%)"
        >
          <span class="inline-block w-2 h-2 rounded-full bg-amber-400 flex-shrink-0" style="box-shadow:0 0 6px rgba(245,158,11,0.8)"></span>
          <span class="type-label text-amber-300/80">No judges configured for this division.</span>
        </div>
        <RouterLink
          :to="`/events/${encodeURIComponent(selectedEvent)}`"
          class="type-label text-accent underline underline-offset-2"
        >Add judges →</RouterLink>
      </div>

      <!-- Judge buttons -->
      <div v-else class="flex flex-col gap-2 w-full" style="max-width:320px">
        <button
          v-for="judge in availableJudges"
          :key="judge"
          @click="pendingJudge = judge; showJudgeConfirm = true"
          class="w-full para-chip px-4 py-3.5 type-name text-content-primary hover:text-accent hover:border-[color:var(--accent-muted)] transition-all duration-150 text-center"
        >
          {{ judge }}
        </button>
      </div>
    </div>

    <!-- Judge view: swipeable score cards -->
    <template v-else-if="selectedRole === 'Judge' && currentJudge && filteredParticipantsForJudge.length > 0">
      <MiniScoreMenu
        :cards="filteredParticipantsForJudge"
        :show="showMiniMenu"
        title="Jump to Participant"
        @close="showMiniMenu = false"
      />
      <PairScoreCards
        v-if="judgingMode === 'PAIR'"
        :cards="filteredParticipantsForJudge"
        :feedbackData="feedbackGiven"
        :criteria="criteria"
        :feedbackEnabled="feedbackEnabled"
        @open-feedback="openFeedbackPopout"
        @remove-tag="removeTag"
        @score-change="autoSave"
        @submit="confirmSubmit('Submit Scores', 'Are you sure you want to submit all scores now?')"
        @reset="confirmReset('Reset Scores', 'Are you sure you want to reset all scores? This cannot be undone.')"
        @jump="showMiniMenu = true"
      />
      <SwipeableCardsV2
        v-else
        :key="selectedCategory"
        :cards="filteredParticipantsForJudge"
        :feedbackData="feedbackGiven"
        :criteria="criteria"
        :feedbackEnabled="feedbackEnabled"
        @open-feedback="openFeedbackPopout"
        @remove-tag="removeTag"
        @score-change="autoSave"
        @submit="confirmSubmit('Submit Scores', 'Are you sure you want to submit all scores now?')"
        @reset="confirmReset('Reset Scores', 'Are you sure you want to reset all scores? This cannot be undone.')"
        @jump="showMiniMenu = true"
      />
    </template>

    <!-- Empty state: no participants in this category -->
    <div
      v-else-if="selectedRole === 'Judge' && selectedCategory && currentJudge && filteredParticipantsForJudge.length === 0"
      class="flex flex-col items-center justify-center h-full text-center"
    >
      <div class="para-chip-sm w-14 h-14 flex items-center justify-center mb-4">
        <i class="pi pi-user text-content-muted text-xl"></i>
      </div>
      <p class="type-body text-content-secondary">No participants in {{ selectedCategory }}</p>
      <p class="type-label text-content-muted mt-1">
        {{ currentJudge }} — {{ selectedCategory }}
      </p>
      <div class="mt-4 px-5 py-3 flex flex-col items-center gap-1" style="border-left:3px solid rgba(245,158,11,0.8);background:rgba(245,158,11,0.07);clip-path:polygon(8px 0%,100% 0%,calc(100% - 8px) 100%,0% 100%)">
        <p class="type-label text-amber-300/80">
          {{ participants.filter(p => p.categoryName === selectedCategory).length }} total participants in {{ selectedCategory }}
        </p>
        <p class="type-label text-amber-300/80">
          {{ participants.filter(p => p.categoryName === selectedCategory && p.auditionNumber !== null).length }} have audition numbers
        </p>
      </div>
      <p class="type-prose-sm mt-3 max-w-xs">
        No participants found in this division. Try a different category or ask an organiser to add participants.
      </p>
    </div>

    <!-- Empty state: no participants in this category (general) -->
    <div
      v-else-if="selectedRole && selectedCategory && filteredParticipantsForEmceeView.length === 0 && filteredParticipantsForJudge.length === 0"
      class="flex flex-col items-center justify-center h-full text-center"
    >
      <div class="para-chip-sm w-14 h-14 flex items-center justify-center mb-4">
        <i class="pi pi-list text-content-muted text-xl"></i>
      </div>
      <p class="type-body text-content-secondary">No participants found</p>
      <p class="type-prose-sm mt-1">Try a different event or category.</p>
    </div>

    <!-- No role selected -->
    <div
      v-else-if="!selectedRole"
      class="flex flex-col items-center justify-center py-24 text-center"
    >
      <div class="para-chip-sm w-14 h-14 flex items-center justify-center mb-4">
        <i class="pi pi-filter text-accent text-xl"></i>
      </div>
      <p class="type-body text-content-secondary">Select your role to begin</p>
      <p class="type-prose-sm mt-1">Choose Emcee or Judge in the filter panel above.</p>
    </div>

    <!-- Catch-all fallback: prevent blank page while data is loading -->
    <div
      v-else
      class="flex flex-col items-center justify-center h-full text-center"
    >
      <div class="para-chip-sm w-14 h-14 flex items-center justify-center mb-4">
        <i class="pi pi-spinner pi-spin text-accent text-xl"></i>
      </div>
      <p class="type-body text-content-secondary">Loading…</p>
      <p class="type-prose-sm mt-1">Fetching audition data for {{ selectedEvent }}…</p>
    </div>

    </div>
  </div>

  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    :variant="modalVariant"
    @accept="() => { dynamicCallBack() }"
    @close="() => { showModal = false }"
  >
    <p class="type-body text-content-secondary">{{ modalMessage }}</p>
  </ActionDoneModal>

  <FeedbackPopout
    :visible="feedbackPopout.visible"
    :participant="feedbackPopout.participant"
    :tagGroups="tagGroups"
    :existingFeedback="feedbackPopout.existing"
    :saving="feedbackSaving"
    @close="feedbackPopout.visible = false"
    @save="saveFeedback"
    @change="autoSaveFeedback"
  />

  <!-- Judge identity confirmation -->
  <ActionDoneModal
    :show="showJudgeConfirm"
    title="Confirm Identity"
    variant="info"
    @accept="() => { currentJudge = pendingJudge; filteredJudge = pendingJudge; showJudgeConfirm = false; pendingJudge = null }"
    @close="() => { showJudgeConfirm = false; pendingJudge = null }"
  >
    <p class="type-prose text-content-secondary" style="font-size:14px;">
      You are judging as <span class="type-name text-accent">{{ pendingJudge }}</span>?
    </p>
  </ActionDoneModal>
</template>
