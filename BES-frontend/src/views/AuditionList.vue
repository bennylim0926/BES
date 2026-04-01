<script setup>
import DynamicTable from '@/components/DynamicTable.vue';
import ReusableButton from '@/components/ReusableButton.vue';
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import { getAllJudges, getRegisteredParticipantsByEvent, submitParticipantScore, whoami, getJudgingMode, setJudgingMode, submitAuditionFeedback, getAuditionFeedback, getScoringCriteria } from '@/utils/api';
import { getFeedbackGroups } from '@/utils/adminApi';
import { createClient, subscribeToChannel, deactivateClient } from '@/utils/websocket';
import { ref, computed, onMounted, onUnmounted, watch, toRaw } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import FeedbackPopout from '@/components/FeedbackPopout.vue';
import Timer from '@/components/Timer.vue';
import { getActiveEvent } from '@/utils/auth';
import SwipeableCardsV2 from '@/components/SwipeableCardsV2.vue';
import PairScoreCards from '@/components/PairScoreCards.vue';
import EmceeRoundView from '@/components/EmceeRoundView.vue';
import MiniScoreMenu from '@/components/MiniScoreMenu.vue';
import 'primeicons/primeicons.css'

const roles = ref(["Emcee", "Judge"])
const selectedEvent = ref(getActiveEvent()?.name || localStorage.getItem("selectedEvent") || "")
const selectedRole = ref(localStorage.getItem("selectedRole") || "")
const selectedGenre = ref(localStorage.getItem("selectedGenre") || "")
const selectedEntryType = ref('Teams') // 'Teams' | 'Solo'
const filteredJudge = ref("")
const currentJudge = ref(localStorage.getItem("currentJudge") || "")
const allJudges = ref([])
const participants = ref([])
const judgingMode = ref("SOLO")
const isAdmin = ref(false)

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
    isAdmin.value = true
  }
}

const hasJudge = computed(() => participants.value.some(item => item.judgeName !== null))

const hasTeamAndSoloMix = computed(() => {
  const gp = participants.value.filter(p => p.genreName === selectedGenre.value)
  const hasTeam = gp.some(p => p.format && p.format !== '1v1')
  const hasSolo = gp.some(p => !p.format)
  return hasTeam && hasSolo
})

const matchesEntryType = (p) => {
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
        p.auditionNumber !== null &&
        matchesEntryType(p)
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

const filteredParticipantsForEmceeView = computed(() => {
  const base = filteredJudge.value === ""
    ? participants.value.filter(p => p.genreName === selectedGenre.value && p.auditionNumber !== null && matchesEntryType(p))
    : participants.value.filter(p => p.genreName === selectedGenre.value && p.judgeName === filteredJudge.value && p.auditionNumber !== null && matchesEntryType(p))
  return base.sort((a, b) => a.auditionNumber - b.auditionNumber)
})

const filteredParticipantsForEmcee = computed({
  get() {
    if (filteredJudge.value === "") {
      return transformForTable(participants.value.filter(p => p.genreName === selectedGenre.value && p.auditionNumber != null && matchesEntryType(p)))
    }
    return transformForTable(participants.value.filter(p =>
      p.genreName === selectedGenre.value && p.judgeName === filteredJudge.value && p.auditionNumber !== null && matchesEntryType(p)
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
    const [res, modeRes] = await Promise.all([
      getRegisteredParticipantsByEvent(newVal),
      getJudgingMode(newVal)
    ])
    if (selectedEvent.value !== newVal) return
    participants.value = res.map((r, i) => ({ ...r, rowId: r.rowId ?? i, score: 0 }))
    if (modeRes?.judgingMode) judgingMode.value = modeRes.judgingMode
    try {
      const stored = localStorage.getItem("currentScore")
      if (stored) {
        const parsed = JSON.parse(stored)
        const cached = parsed.event === newVal ? parsed.scores : null
        if (cached) {
          participants.value = participants.value.map(p => {
            const found = cached.find(c => c.participantName === p.participantName &&
              c.genreName === p.genreName && c.judgeName === p.judgeName)
            return found ? { ...p, score: found.score, absent: found.absent } : p
          })
        }
      }
    } catch { /* ignore malformed cache */ }
  }
}, { immediate: true });

watch(selectedGenre, (newVal) => {
  if (newVal) localStorage.setItem("selectedGenre", newVal)
  selectedEntryType.value = 'Teams'
}, { immediate: true });
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

const submitScore = async (eventName, genreName, judgeName, participantList) => {
  if (judgeName === "") {
    openModal("Missing Judge", "Please select a judge before submitting.", "warning")
    return
  }
  const hasCriteria = criteria.value.length > 0
  const p = participantList.map(obj => {
    if (hasCriteria && obj.criteriaScores) {
      return {
        participantName: obj.participantName,
        score: null,
        aspects: criteria.value.map(c => ({ aspect: c.name, score: parseFloat(obj.criteriaScores[c.name] ?? 0) }))
      }
    }
    return { participantName: obj.participantName, score: parseFloat(obj.score) }
  })
  const res = await submitParticipantScore(eventName, genreName, judgeName, p)
  if (res.ok) {
    openModal("Scores Submitted", "All scores have been saved successfully.", "success")
  }
}

const resetScore = () => {
  localStorage.removeItem("currentScore")
  participants.value = participants.value.map(obj => {
    const cs = {}
    if (obj.criteriaScores) {
      Object.keys(obj.criteriaScores).forEach(k => { cs[k] = 0 })
    }
    return { ...obj, score: 0, criteriaScores: cs }
  })
}

// ── Scoring criteria ─────────────────────────────────────────────────────────
const criteria = ref([])

const loadCriteria = async () => {
  if (!selectedEvent.value || !selectedGenre.value) { criteria.value = []; return }
  criteria.value = await getScoringCriteria(selectedEvent.value, selectedGenre.value)
  // Re-initialize criteriaScores on each participant for the active genre
  participants.value = participants.value.map(p => {
    if (p.genreName !== selectedGenre.value) return p
    const cs = {}
    criteria.value.forEach(c => { cs[c.name] = p.criteriaScores?.[c.name] ?? 0 })
    return { ...p, criteriaScores: cs }
  })
}

watch([selectedEvent, selectedGenre], loadCriteria, { immediate: true })
// ─────────────────────────────────────────────────────────────────────────────

// ── Feedback state ──────────────────────────────────────────────────────────
const tagGroups = ref([])
const feedbackGiven = ref(new Map())  // auditionNumber → { tagIds, note, tagLabels: [{id, label}] }
const feedbackPopout = ref({ visible: false, participant: null, existing: null })

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
    selectedEvent.value, selectedGenre.value, currentJudge.value,
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

const removeTag = async ({ auditionNumber, tagId }) => {
  const existing = feedbackGiven.value.get(auditionNumber)
  if (!existing) return
  const newTagIds = existing.tagIds.filter(id => id !== tagId)
  await submitAuditionFeedback(
    selectedEvent.value, selectedGenre.value, currentJudge.value,
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

// Reload feedback data when judge or genre changes
const loadFeedbackGiven = async () => {
  if (!selectedEvent.value || !selectedGenre.value || !currentJudge.value) {
    feedbackGiven.value = new Map()
    return
  }
  const newMap = new Map()
  for (const p of filteredParticipantsForJudge.value) {
    const fb = await getAuditionFeedback(selectedEvent.value, selectedGenre.value, currentJudge.value, p.auditionNumber)
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

watch([currentJudge, selectedGenre], loadFeedbackGiven)
// ───────────────────────────────────────────────────────────────────────────

const showFilters = ref(true)

const wsClients = []

onUnmounted(() => {
  wsClients.forEach(c => deactivateClient(c))
})

onMounted(async () => {
  await dynamicRole()
  const [judgeRes, groupRes] = await Promise.all([getAllJudges(), getFeedbackGroups()])
  allJudges.value = ["", ...Object.values(judgeRes).map(item => item.judgeName)]
  tagGroups.value = groupRes ?? []

  // On page refresh, judge/genre/event are restored from localStorage so the watch
  // on [currentJudge, selectedGenre] never fires. Load feedback once participants arrive.
  if (selectedEvent.value && selectedGenre.value && currentJudge.value) {
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
  const c4 = createClient()
  wsClients.push(c4)
  subscribeToChannel(c4, "/topic/judging-mode/",
    (msg) => {
      if (msg.eventName !== selectedEvent.value) return
      judgingMode.value = msg.judgingMode
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
          <ReusableDropdown
            v-if="hasTeamAndSoloMix"
            v-model="selectedEntryType"
            labelId="Type"
            :options="['Teams', 'Solo']"
          />
          <ReusableDropdown
            v-if="isAdmin && selectedEvent"
            v-model="judgingMode"
            labelId="Judging Mode"
            :options="['SOLO', 'PAIR']"
            @update:modelValue="(val) => setJudgingMode(selectedEvent, val)"
          />
        </div>

        <!-- Current judge selector (judge role only) -->
        <div v-if="selectedRole === 'Judge'" class="mt-4 pt-4 border-t border-surface-600/30">
          <div class="max-w-xs">
            <ReusableDropdown v-model="currentJudge" labelId="You are judging as" :options="allJudges" />
          </div>
        </div>
      </div>
    </Transition>

    <!-- Emcee view: Timer + round view -->
    <template v-if="selectedRole === 'Emcee' && filteredParticipantsForEmceeView.length > 0">
      <div class="sticky top-[72px] z-20 mb-4">
        <Timer />
      </div>
      <EmceeRoundView
        :participants="filteredParticipantsForEmceeView"
        :mode="judgingMode"
      />
    </template>

    <!-- Judge view: swipeable score cards -->
    <template v-else-if="selectedRole === 'Judge' && filteredParticipantsForJudge.length > 0">
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
        @open-feedback="openFeedbackPopout"
        @remove-tag="removeTag"
      />
      <SwipeableCardsV2
        v-else
        :cards="filteredParticipantsForJudge"
        :feedbackData="feedbackGiven"
        :criteria="criteria"
        @open-feedback="openFeedbackPopout"
        @remove-tag="removeTag"
      />
    </template>

    <!-- Empty state -->
    <div
      v-else-if="selectedRole && selectedGenre && filteredParticipantsForEmceeView.length === 0 && filteredParticipantsForJudge.length === 0"
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

  <FeedbackPopout
    :visible="feedbackPopout.visible"
    :participant="feedbackPopout.participant"
    :tagGroups="tagGroups"
    :existingFeedback="feedbackPopout.existing"
    @close="feedbackPopout.visible = false"
    @save="saveFeedback"
  />
</template>
