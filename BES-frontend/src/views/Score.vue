<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { getParticipantScore, getParticipantFeedback, getResultsStatus, releaseResults, getParticipantRefs, getScoringCriteria, setResolvedParticipants } from '@/utils/api';
import DynamicTable from '@/components/DynamicTable.vue';
import { computeNextEligibleAdd, computeNextEligibleRemove, addedPoolOrdered } from '@/utils/scoreTiePool';
import { useAuthStore } from '@/utils/auth';

const authStore = useAuthStore()
const selectedEvent = computed(() => authStore.activeEvent?.name || localStorage.getItem("selectedEvent") || "")
const selectedGenre = ref(localStorage.getItem("selectedGenre") || "All")
const selectedTabulation = ref(localStorage.getItem("selectedTabMethod") || "")
const selectedTopN = ref("All")
const selectedEntryType = ref('Teams') // 'Teams' | 'Solo'
const participants = ref([])
const tabulationMethod = ref(["By Total", "By Judge"])
const topNOptions = ["All", "Top 8", "Top 16", "Top 32"]

// Auth
const userRole = computed(() => authStore.user?.role?.[0]?.authority)
const isAdminOrOrganiser = computed(() => ['ROLE_ADMIN', 'ROLE_ORGANISER'].includes(userRole.value))

// ── Mode toggle: Control (organiser) vs Broadcast (emcee) ─────────────────
// Default is role-driven; user choice persists per event in localStorage.
const modeKey = computed(() => `score_mode_${selectedEvent.value || 'global'}`)
const initialMode = (() => {
  const saved = localStorage.getItem(`score_mode_${authStore.activeEvent?.name || localStorage.getItem('selectedEvent') || 'global'}`)
  if (saved === 'control' || saved === 'broadcast') return saved
  return userRole.value === 'ROLE_EMCEE' ? 'broadcast' : 'control'
})()
const mode = ref(initialMode)
watch([mode, modeKey], ([m, k]) => { if (k && (m === 'control' || m === 'broadcast')) localStorage.setItem(k, m) })

// Results release state (admin/organiser only)
const resultsReleased = ref(false)
const refsMap = ref({}) // participantName -> referenceCode

// Criteria for the selected genre (used for multi-aspect score aggregation)
const criteriaForGenre = ref([])

// Score breakdown modal
const showBreakdown = ref(false)
const breakdownParticipant = ref('')

// Feedback panel state
const showFeedbackPanel = ref(false)
const feedbackParticipant = ref('')
const feedbackData = ref([])
const feedbackLoading = ref(false)

// Tie-breaker resolution state
const tieBreakerWinners = ref(new Set())
const tieBreakerConfirmed = ref(false)
const addedToPool = ref(new Set()) // manually-added pool members for the tie resolver

// localStorage key scoped to event + genre + tabulation + topN
const tbKey = computed(() =>
  `tb_${selectedEvent.value}_${selectedGenre.value}_${selectedTabulation.value}_${selectedTopN.value}`
)
const saveTieBreaker = () => {
  localStorage.setItem(tbKey.value, JSON.stringify({
    winners: [...tieBreakerWinners.value],
    confirmed: tieBreakerConfirmed.value,
    addedToPool: [...addedToPool.value],
  }))
}
const loadTieBreaker = () => {
  const saved = localStorage.getItem(tbKey.value)
  if (saved) {
    const { winners, confirmed, addedToPool: added } = JSON.parse(saved)
    tieBreakerWinners.value = new Set(winners)
    tieBreakerConfirmed.value = confirmed
    addedToPool.value = new Set(Array.isArray(added) ? added : [])
  } else {
    tieBreakerWinners.value = new Set()
    tieBreakerConfirmed.value = false
    addedToPool.value = new Set()
  }
}
const resetTieBreaker = () => {
  tieBreakerWinners.value = new Set()
  tieBreakerConfirmed.value = false
  addedToPool.value = new Set()
  localStorage.removeItem(tbKey.value)
}

const uniqueGenres = computed(() => {
  const genres = participants.value.map(p => p.genreName);
  return [...new Set(genres)].sort();
})

const hasTeamAndSoloMix = computed(() => {
  const gp = participants.value.filter(p => p.genreName === selectedGenre.value)
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

// Load admin/organiser specific data for an event
const loadAdminData = async (eventName) => {
  if (!isAdminOrOrganiser.value || !eventName) return
  const [status, refs] = await Promise.all([
    getResultsStatus(eventName),
    getParticipantRefs(eventName)
  ])
  resultsReleased.value = status?.released ?? false
  refsMap.value = Object.fromEntries((refs || []).map(r => [r.participantName, r.referenceCode]))
}

watch(selectedEvent, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedEvent", newVal);
    const res = await getParticipantScore(newVal)
    participants.value = res.map((r, i) => ({ ...r, id: i + 1 }))
    await loadAdminData(newVal)
  }
}, { immediate: true });

watch(selectedGenre, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedGenre", newVal)
    selectedTopN.value = 'All'
    selectedEntryType.value = 'Teams'
    if (selectedEvent.value && newVal !== 'All') {
      criteriaForGenre.value = await getScoringCriteria(selectedEvent.value, newVal)
    } else {
      criteriaForGenre.value = []
    }
  }
}, { immediate: true });
watch(selectedTabulation, (newVal) => {
  if (newVal) { localStorage.setItem("selectedTabMethod", newVal); selectedTopN.value = 'All' }
  resetTieBreaker()
}, { immediate: true });
// Load persisted tie-breaker state whenever the scoped key changes
watch(tbKey, loadTieBreaker, { immediate: true })

const filteredParticipantsForScore = computed({
  get() {
    return transformForScore(participants.value.filter(p => p.genreName === selectedGenre.value && matchesEntryType(p)))
  }
})

// Apply Top N filter with tie-breaker detection
const topNResult = computed(() => {
  const base = filteredParticipantsForScore.value
  if (!base.rows) return { hasTieBreaker: false }

  const n = selectedTopN.value === 'All' ? Infinity : parseInt(selectedTopN.value.replace('Top ', ''))
  const rows = base.rows

  if (n === Infinity || rows.length <= n) {
    return { ...base, hasTieBreaker: false }
  }

  const cutoffScore = rows[n - 1].totalScore
  const nextScore   = rows[n].totalScore

  if (cutoffScore === nextScore) {
    const above = rows.filter(r => r.totalScore > cutoffScore)
    const tied  = rows.filter(r => r.totalScore === cutoffScore)
    return {
      ...base,
      rows: [...above, ...tied],
      hasTieBreaker: true,
      tieBreakerScore: cutoffScore,
      tiedCount: tied.length,
      aboveCount: above.length,
      cutoff: n,
    }
  }

  return { ...base, rows: rows.slice(0, n), hasTieBreaker: false }
})

// How many spots need to be filled from the tied group
const spotsFromTie = computed(() => {
  if (!topNResult.value.hasTieBreaker) return 0
  return topNResult.value.cutoff - topNResult.value.aboveCount
})

// The tied participants (last tiedCount rows)
const tiedParticipants = computed(() => {
  if (!topNResult.value.hasTieBreaker) return []
  const rows = topNResult.value.rows
  return rows.slice(rows.length - topNResult.value.tiedCount)
})

// Full ranked rows for the current genre+type, used to feed the pool helpers.
const allRowsForPool = computed(() => filteredParticipantsForScore.value.rows ?? [])

// Tie base = the auto-detected tied participants (cannot be removed from pool).
const tieBaseNames = computed(() => new Set(tiedParticipants.value.map(r => r.participantName)))

// Full pool = base + added (set).
const eligibilityPoolNames = computed(() => {
  const s = new Set(tieBaseNames.value)
  for (const n of addedToPool.value) s.add(n)
  return s
})

// Ordered list of added members (rank-sorted).
// eslint-disable-next-line no-unused-vars
const addedToPoolOrdered = computed(() =>
  addedPoolOrdered(allRowsForPool.value, addedToPool.value)
)

// Next eligible to add — drives "+ INCLUDE <name> · <score>" button label.
const nextEligibleAdd = computed(() =>
  topNResult.value.hasTieBreaker
    ? computeNextEligibleAdd(allRowsForPool.value, eligibilityPoolNames.value)
    : null
)

// Next eligible to remove — drives "− EXCLUDE <name> · <score>" button label.
const nextEligibleRemove = computed(() =>
  computeNextEligibleRemove(allRowsForPool.value, tieBaseNames.value, addedToPool.value)
)

const toggleWinner = (name) => {
  const w = new Set(tieBreakerWinners.value)
  if (w.has(name)) {
    w.delete(name)
  } else if (w.size < spotsFromTie.value) {
    w.add(name)
  }
  tieBreakerWinners.value = w
  tieBreakerConfirmed.value = false
  saveTieBreaker()
}

const confirmTieBreaker = async () => {
  if (tieBreakerWinners.value.size === spotsFromTie.value) {
    // Save resolved names server-side FIRST so BattleControl can read them from DB.
    // Only confirm locally after the API succeeds — prevents false-positive UI state
    // when the server call fails silently.
    const resolved = finalRows.value.map(r => r.participantName)
    const res = await setResolvedParticipants(selectedEvent.value, selectedGenre.value, resolved)
    if (res && res.ok) {
      tieBreakerConfirmed.value = true
      saveTieBreaker()
    }
  }
}

// eslint-disable-next-line no-unused-vars
const includeNextInPool = () => {
  const next = nextEligibleAdd.value
  if (!next) return
  const s = new Set(addedToPool.value)
  s.add(next.participantName)
  addedToPool.value = s
  tieBreakerConfirmed.value = false
  saveTieBreaker()
}

// eslint-disable-next-line no-unused-vars
const excludeLastFromPool = () => {
  const last = nextEligibleRemove.value
  if (!last) return
  const s = new Set(addedToPool.value)
  s.delete(last.participantName)
  // Also drop them from the winners selection if they were picked.
  const w = new Set(tieBreakerWinners.value)
  w.delete(last.participantName)
  addedToPool.value = s
  tieBreakerWinners.value = w
  tieBreakerConfirmed.value = false
  saveTieBreaker()
}

// Final rows for table & podium
const finalRows = computed(() => {
  if (!topNResult.value.rows) return []
  if (!topNResult.value.hasTieBreaker || !tieBreakerConfirmed.value) {
    return topNResult.value.rows
  }
  const above = topNResult.value.rows.slice(0, topNResult.value.aboveCount)
  const winners = tiedParticipants.value.filter(r => tieBreakerWinners.value.has(r.participantName))
  return [...above, ...winners].map((r, i) => ({ ...r, id: i + 1 }))
})

// Control-mode status banner — sits below the Top N picker.
// eslint-disable-next-line no-unused-vars
const statusBanner = computed(() => {
  const t = topNResult.value
  const total = filteredParticipantsForScore.value.rows?.length ?? 0
  const n = selectedTopN.value === 'All' ? Infinity : parseInt(selectedTopN.value.replace('Top ', ''))
  if (!Number.isFinite(n)) return null
  if (total < n) {
    return { tone: 'insufficient', message: `ONLY ${total} SCORED — TOP ${n} NOT YET REACHABLE` }
  }
  if (t.hasTieBreaker) {
    return { tone: 'tie', message: `TIE AT RANK ${t.cutoff} — ${t.tiedCount} AT ${t.tieBreakerScore} — RESOLVE BELOW` }
  }
  return { tone: 'clean', message: `TOP ${n} READY — 0 TIES` }
})

// Summary of eliminated participants — drives the "⋯ N MORE ELIMINATED ⋯" collapse.
// eslint-disable-next-line no-unused-vars
const eliminatedSummary = computed(() => {
  const all = allRowsForPool.value
  const visible = new Set(finalRows.value.map(r => r.participantName))
  const eliminated = all.filter(r => !visible.has(r.participantName))
  if (eliminated.length === 0) return null
  const lowestScore = Math.min(...eliminated.map(r => r.totalScore))
  return { count: eliminated.length, lowestScore }
})

// eslint-disable-next-line no-unused-vars
const eliminatedExpanded = ref(false)

// Broadcast-mode column count — 1 below 640px or for very small N, 2 otherwise.
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1024)
const onResize = () => { viewportWidth.value = window.innerWidth }
onMounted(() => { window.addEventListener('resize', onResize) })
onUnmounted(() => { window.removeEventListener('resize', onResize) })

// eslint-disable-next-line no-unused-vars
const broadcastColumns = computed(() => {
  if (viewportWidth.value < 640) return '1col'
  const n = selectedTopN.value === 'All'
    ? (allRowsForPool.value?.length ?? 0)
    : parseInt(selectedTopN.value.replace('Top ', ''))
  return n <= 12 ? '1col' : '2col'
})

// Pagination for the admin/organiser table
const PAGE_SIZE = 10
const tablePage = ref(1)
watch(finalRows, () => { tablePage.value = 1 })
const totalTablePages = computed(() => Math.max(1, Math.ceil(finalRows.value.length / PAGE_SIZE)))
const pagedFinalRows = computed(() =>
  finalRows.value.slice((tablePage.value - 1) * PAGE_SIZE, tablePage.value * PAGE_SIZE)
)

// Judge column keys for the custom admin table
const judgeColumnKeys = computed(() => {
  if (!topNResult.value.columns) return []
  return topNResult.value.columns
    .filter(c => !['id', 'participantName', 'totalScore'].includes(c.key))
    .map(c => c.key)
})

// Release results toggle
const toggleRelease = async () => {
  const newVal = !resultsReleased.value
  const res = await releaseResults(selectedEvent.value, newVal)
  if (res !== null) {
    resultsReleased.value = newVal
    if (newVal) {
      // Refresh refs in case new participants were added
      const refs = await getParticipantRefs(selectedEvent.value)
      refsMap.value = Object.fromEntries((refs || []).map(r => [r.participantName, r.referenceCode]))
    }
  }
}

// Score breakdown: raw per-aspect scores for a participant grouped by judge
const breakdownRows = computed(() => {
  if (!breakdownParticipant.value) return {}
  const raw = participants.value.filter(
    p => p.participantName === breakdownParticipant.value && p.genreName === selectedGenre.value
  )
  const byJudge = {}
  raw.forEach(d => {
    if (!d.judgeName) return
    if (!byJudge[d.judgeName]) byJudge[d.judgeName] = {}
    const aspect = d.aspect || ''
    byJudge[d.judgeName][aspect || 'Score'] = d.score
  })
  return byJudge
})

const viewBreakdown = (name) => {
  breakdownParticipant.value = name
  showBreakdown.value = true
}

// Open feedback panel for a participant
const viewFeedback = async (name) => {
  feedbackParticipant.value = name
  feedbackData.value = []
  feedbackLoading.value = true
  showFeedbackPanel.value = true
  const res = await getParticipantFeedback(selectedEvent.value, selectedGenre.value, name)
  feedbackData.value = res ?? []
  feedbackLoading.value = false
}

// Open QR page in new tab
const openQR = (name) => {
  const ref = refsMap.value[name]
  if (ref) {
    window.open(`/results-qr?ref=${encodeURIComponent(ref)}&name=${encodeURIComponent(name)}`, '_blank')
  }
}

// Group feedback tags by group name for display
const groupTags = (tags) => {
  const groups = {}
  for (const tag of tags) {
    if (!groups[tag.groupName]) groups[tag.groupName] = []
    groups[tag.groupName].push(tag)
  }
  return groups
}

// Compute a judge's aggregate score for one participant from multi-aspect data
function aggregateJudgeScore(aspectMap) {
  const criteria = criteriaForGenre.value
  if (!criteria || criteria.length === 0) {
    // No criteria defined — sum all aspect values (should be one entry)
    return Object.values(aspectMap).reduce((s, v) => s + (v ?? 0), 0)
  }
  const hasWeights = criteria.some(c => c.weight != null)
  const totalWeight = hasWeights
    ? criteria.reduce((s, c) => s + (c.weight ?? 1), 0)
    : criteria.length
  let weighted = 0
  criteria.forEach(c => {
    const score = aspectMap[c.name] ?? 0
    weighted += score * (hasWeights ? (c.weight ?? 1) : 1)
  })
  return weighted / totalWeight
}

function transformForScore(data) {
  const judges = [...new Set(data.map(d => d.judgeName).filter(j => j !== null))]
  const isMultiAspect = data.some(d => d.aspect && d.aspect !== '')

  if (selectedTabulation.value === 'By Total') {
    const byTotal = {}

    if (isMultiAspect) {
      // Group by participant → judge → aspect
      const grouped = {}
      data.forEach(d => {
        if (!grouped[d.participantName]) grouped[d.participantName] = {}
        if (!grouped[d.participantName][d.judgeName]) grouped[d.participantName][d.judgeName] = {}
        grouped[d.participantName][d.judgeName][d.aspect] = d.score
      })
      Object.entries(grouped).forEach(([name, judgeMap]) => {
        byTotal[name] = { participantName: name, totalScore: 0 }
        Object.entries(judgeMap).forEach(([judge, aspects]) => {
          const agg = aggregateJudgeScore(aspects)
          byTotal[name][judge] = Number(agg.toFixed(2))
          byTotal[name].totalScore += agg
        })
      })
    } else {
      data.forEach(d => {
        if (!byTotal[d.participantName]) {
          byTotal[d.participantName] = { participantName: d.participantName, totalScore: 0 }
        }
        byTotal[d.participantName][d.judgeName] = d.score
        byTotal[d.participantName].totalScore += d.score
      })
    }

    const rows = Object.values(byTotal)
      .map(r => ({ ...r, totalScore: Number(r.totalScore.toFixed(1)) }))
      .sort((a, b) => b.totalScore - a.totalScore)
      .map((r, i) => ({ ...r, id: i + 1 }))
    return {
      columns: [
        { key: 'id', label: 'Rank', type: 'text', readonly: true },
        { key: 'participantName', label: 'Participant', type: 'text' },
        { key: 'totalScore', label: 'Total Score', type: 'text', readonly: true },
        ...judges.map(j => ({ key: j, label: j, type: 'text', readonly: true }))
      ],
      rows,
      isMultiAspect,
    }
  } else {
    const byJudge = {}
    if (isMultiAspect) {
      // Aggregate per participant+judge first
      const grouped = {}
      data.forEach(d => {
        if (!grouped[d.judgeName]) grouped[d.judgeName] = {}
        if (!grouped[d.judgeName][d.participantName]) grouped[d.judgeName][d.participantName] = {}
        grouped[d.judgeName][d.participantName][d.aspect] = d.score
      })
      Object.entries(grouped).forEach(([judge, participantMap]) => {
        byJudge[judge] = {
          columns: [
            { key: 'id', label: 'Rank', type: 'text', readonly: true },
            { key: 'participantName', label: 'Participant', type: 'text' },
            { key: 'score', label: 'Score (avg)', type: 'text', readonly: true },
          ],
          rows: Object.entries(participantMap).map(([name, aspects]) => ({
            participantName: name,
            score: Number(aggregateJudgeScore(aspects).toFixed(2))
          }))
        }
        byJudge[judge].rows = byJudge[judge].rows
          .sort((a, b) => b.score - a.score)
          .map((r, i) => ({ ...r, id: i + 1 }))
      })
    } else {
      data.forEach(d => {
        if (!byJudge[d.judgeName]) {
          byJudge[d.judgeName] = {
            columns: [
              { key: 'id', label: 'Rank', type: 'text', readonly: true },
              { key: 'participantName', label: 'Participant', type: 'text' },
              { key: 'score', label: 'Score', type: 'text', readonly: true },
            ],
            rows: []
          }
        }
        byJudge[d.judgeName].rows.push({ participantName: d.participantName, score: d.score })
      })
      Object.values(byJudge).forEach(group => {
        group.rows = group.rows
          .sort((a, b) => b.score - a.score)
          .map((r, i) => ({ ...r, id: i + 1 }))
      })
    }
    return { byJudge }
  }
}
</script>

<template>
  <div class="page-container relative">
    <div class="color-bleed"></div>
    <div class="relative z-10">

    <!-- Page header -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-8">
      <div>
        <!-- h1 for document outline -->
        <h1 class="type-page-title mb-1">Scoreboard</h1>
        <p class="type-label text-content-muted">View and compare scores across genres and judges</p>
      </div>
    </div>

    <!-- Filter card -->
    <div class="card p-5 mb-6">
      <div class="flex flex-wrap items-center gap-3">
        <!-- Event name -->
        <span class="type-body text-content-primary whitespace-nowrap">{{ selectedEvent }}</span>
        <span class="text-surface-600 select-none">|</span>

        <!-- Genre toggle — role=group + aria-pressed so toggle state is exposed beyond color -->
        <div class="flex flex-wrap gap-1" role="group" aria-label="Filter by genre">
          <button
            v-for="g in uniqueGenres"
            :key="g"
            @click="selectedGenre = g"
            :aria-pressed="selectedGenre === g"
            class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150"
            :class="selectedGenre === g
              ? 'text-accent border-[color:var(--accent-muted)]'
              : 'text-content-muted hover:text-content-primary'"
          >{{ g }}</button>
        </div>
        <span class="text-surface-600 select-none" aria-hidden="true">|</span>

        <!-- Group By toggle -->
        <div class="flex flex-wrap gap-1" role="group" aria-label="Tabulation method">
          <button
            v-for="t in tabulationMethod"
            :key="t"
            @click="selectedTabulation = t"
            :aria-pressed="selectedTabulation === t"
            class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150"
            :class="selectedTabulation === t
              ? 'text-accent border-[color:var(--accent-muted)]'
              : 'text-content-muted hover:text-content-primary'"
          >{{ t }}</button>
        </div>
        <span class="text-surface-600 select-none" aria-hidden="true">|</span>

        <!-- Show Top toggle -->
        <div class="flex flex-wrap gap-1" role="group" aria-label="Show top N">
          <button
            v-for="n in topNOptions"
            :key="n"
            @click="selectedTopN = n"
            :aria-pressed="selectedTopN === n"
            class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150"
            :class="selectedTopN === n
              ? 'text-accent border-[color:var(--accent-muted)]'
              : 'text-content-muted hover:text-content-primary'"
          >{{ n }}</button>
        </div>

        <!-- Type toggle (conditional) -->
        <template v-if="hasTeamAndSoloMix">
          <span class="text-surface-600 select-none" aria-hidden="true">|</span>
          <div class="flex flex-wrap gap-1" role="group" aria-label="Entry type">
            <button
              v-for="t in ['Teams', 'Solo']"
              :key="t"
              @click="selectedEntryType = t"
              :aria-pressed="selectedEntryType === t"
              class="para-chip-sm px-3 py-1.5 type-label transition-all duration-150"
              :class="selectedEntryType === t
                ? 'text-accent border-[color:var(--accent-muted)]'
                : 'text-content-muted hover:text-content-primary'"
            >{{ t }}</button>
          </div>
        </template>
      </div>

      <!-- Release Results toggle (admin/organiser only) -->
      <div v-if="isAdminOrOrganiser" class="section-rule mt-4 pt-4">
        <span class="section-rule-label">Results Portal</span>
        <div class="section-rule-line"></div>
      </div>
      <div v-if="isAdminOrOrganiser" class="flex items-center justify-between mt-3">
        <p class="type-label text-content-muted">
          {{ resultsReleased ? 'Participants can view their scores and feedback' : 'Results are hidden from participants' }}
        </p>
        <!-- aria-pressed: release toggle exposes its on/off state to assistive tech -->
        <button
          @click="toggleRelease"
          :aria-pressed="resultsReleased"
          class="type-label transition-all duration-200 inline-flex items-center gap-1.5"
          :class="resultsReleased
            ? 'bg-accent para-chip-sm px-3 py-1.5 text-surface-900'
            : 'para-chip-sm px-3 py-1.5 border-accent text-content-muted hover:text-content-primary'"
        >
          <i :class="resultsReleased ? 'pi pi-eye' : 'pi pi-eye-slash'" aria-hidden="true"></i>
          {{ resultsReleased ? 'Released' : 'Release Results' }}
        </button>
      </div>
    </div>

    <!-- By Total: ranked leaderboard -->
    <template v-if="selectedTabulation === 'By Total'">
      <div v-if="topNResult.rows && topNResult.rows.length > 0">

        <!-- Tie-breaker section -->
        <template v-if="topNResult.hasTieBreaker">

          <!-- Resolved banner -->
          <div
            v-if="tieBreakerConfirmed"
            class="flex items-center justify-between gap-3 px-4 py-3 mb-5 rounded-xl border border-emerald-500/40 bg-emerald-500/8"
          >
            <div class="flex items-center gap-3">
              <i class="pi pi-check-circle text-emerald-400 flex-shrink-0"></i>
              <div>
                <p class="text-sm font-bold text-emerald-400">Tie-breaker resolved</p>
                <p class="text-xs text-emerald-300/80 mt-0.5">
                  {{ tieBreakerWinners.size }} participant{{ tieBreakerWinners.size > 1 ? 's' : '' }} advanced from the tie at score {{ topNResult.tieBreakerScore }}.
                  {{ topNResult.tiedCount - tieBreakerWinners.size }} eliminated.
                </p>
              </div>
            </div>
            <button
              @click="resetTieBreaker"
              class="flex-shrink-0 text-xs px-3 py-1.5 rounded-lg border border-surface-600/50 bg-surface-700/50
                     text-content-muted hover:border-surface-500 hover:bg-surface-700 transition-all"
            >
              Reset
            </button>
          </div>

          <!-- Pending banner + resolution panel -->
          <template v-else>
            <!-- Warning banner -->
            <div class="flex items-start gap-3 px-4 py-3 mb-4 rounded-xl border border-amber-500/40 bg-amber-500/8">
              <i class="pi pi-exclamation-triangle text-amber-400 mt-0.5 flex-shrink-0"></i>
              <div>
                <p class="text-sm font-bold text-amber-400">Tie-breaker required at rank {{ topNResult.cutoff }}</p>
                <p class="text-xs text-amber-300/80 mt-0.5">
                  {{ topNResult.tiedCount }} participants tied at score {{ topNResult.tieBreakerScore }}.
                  {{ spotsFromTie }} spot{{ spotsFromTie > 1 ? 's' : '' }} available — select who advances below.
                </p>
              </div>
            </div>

            <!-- Resolution panel -->
            <div class="card p-5 mb-6 border-amber-500/25">
              <div class="flex items-center justify-between mb-4">
                <div>
                  <h3 class="font-heading font-bold text-content-primary text-sm">Tie-breaker Resolution</h3>
                  <p class="text-xs text-content-muted mt-0.5">
                    Select <span class="font-bold text-amber-400">{{ spotsFromTie }}</span>
                    participant{{ spotsFromTie > 1 ? 's' : '' }} who advance{{ spotsFromTie === 1 ? 's' : '' }}
                  </p>
                </div>
                <!-- Slot counter -->
                <div class="flex items-center gap-1.5">
                  <span
                    class="font-source font-bold text-lg leading-none"
                    :class="tieBreakerWinners.size === spotsFromTie ? 'text-emerald-400' : 'text-amber-400'"
                  >{{ tieBreakerWinners.size }}</span>
                  <span class="text-content-muted text-sm">/</span>
                  <span class="font-source font-bold text-lg text-content-muted leading-none">{{ spotsFromTie }}</span>
                  <span class="text-xs text-content-muted ml-1">selected</span>
                </div>
              </div>

              <!-- Tied participant toggles -->
              <div class="flex flex-col gap-2 mb-5">
                <button
                  v-for="p in tiedParticipants"
                  :key="p.participantName"
                  @click="toggleWinner(p.participantName)"
                  :disabled="!tieBreakerWinners.has(p.participantName) && tieBreakerWinners.size >= spotsFromTie"
                  class="flex items-center justify-between px-4 py-3 rounded-xl border transition-all duration-150 text-left
                         disabled:opacity-40 disabled:cursor-not-allowed"
                  :class="tieBreakerWinners.has(p.participantName)
                    ? 'bg-emerald-500/10 border-emerald-500/50 ring-1 ring-emerald-500/30'
                    : 'bg-surface-700/30 border-surface-600/40 hover:border-surface-500/60 hover:bg-surface-700/50'"
                >
                  <div class="flex items-center gap-3">
                    <!-- Check indicator -->
                    <div
                      class="w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 transition-all"
                      :class="tieBreakerWinners.has(p.participantName)
                        ? 'bg-emerald-500 border-emerald-500'
                        : 'border-surface-500'"
                    >
                      <i v-if="tieBreakerWinners.has(p.participantName)" class="pi pi-check text-white" style="font-size:10px"></i>
                    </div>
                    <span
                      class="font-heading font-bold text-sm"
                      :class="tieBreakerWinners.has(p.participantName) ? 'text-emerald-400' : 'text-content-primary'"
                    >
                      {{ p.participantName }}
                    </span>
                  </div>
                  <div class="flex items-center gap-2">
                    <span class="font-source font-bold text-content-muted text-sm">{{ p.totalScore }}</span>
                    <span
                      v-if="tieBreakerWinners.has(p.participantName)"
                      class="text-xs px-2 py-0.5 rounded-full bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 font-semibold"
                    >Advances</span>
                    <span
                      v-else-if="tieBreakerWinners.size >= spotsFromTie"
                      class="text-xs px-2 py-0.5 rounded-full bg-surface-700 text-surface-400 border border-surface-600/30 font-semibold"
                    >Eliminated</span>
                  </div>
                </button>
              </div>

              <!-- Confirm button -->
              <button
                @click="confirmTieBreaker"
                :disabled="tieBreakerWinners.size !== spotsFromTie"
                class="w-full py-2.5 rounded-xl text-sm font-bold transition-all duration-200
                       disabled:opacity-30 disabled:cursor-not-allowed"
                :class="tieBreakerWinners.size === spotsFromTie
                  ? 'bg-emerald-600 text-white hover:bg-emerald-500 active:bg-emerald-700'
                  : 'bg-surface-700 text-content-muted border border-surface-600'"
              >
                <i class="pi pi-check mr-2"></i>
                Confirm Tie-breaker Result
              </button>
            </div>
          </template>
        </template>

        <!-- Top 3 podium cards — single column on mobile (winner first), 2-1-3 layout on sm+ -->
        <div
          v-if="finalRows.length >= 3"
          class="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6"
        >
          <div class="stat-card relative order-2 sm:order-1">
            <div class="corner-bar-tl"></div>
            <span class="badge-neutral type-label mb-1">2nd</span>
            <div class="type-body text-content-secondary mb-1">
              {{ finalRows[1].participantName }}
            </div>
            <div class="type-stat">
              {{ finalRows[1].totalScore }}
            </div>
          </div>
          <div
            class="stat-card relative order-1 sm:order-2"
            style="box-shadow: 0 0 0 1px var(--accent-muted), 0 8px 40px var(--accent-subtle);"
          >
            <div class="corner-bar-tl"></div>
            <span class="badge-neutral type-label mb-1">1st</span>
            <div class="type-body text-content-primary mb-1">
              {{ finalRows[0].participantName }}
            </div>
            <div class="type-stat text-accent">
              {{ finalRows[0].totalScore }}
            </div>
          </div>
          <div class="stat-card relative order-3">
            <div class="corner-bar-tl"></div>
            <span class="badge-neutral type-label mb-1">3rd</span>
            <div class="type-body text-content-muted mb-1">
              {{ finalRows[2].participantName }}
            </div>
            <div class="type-stat">
              {{ finalRows[2].totalScore }}
            </div>
          </div>
        </div>

        <div class="section-rule mb-4 mt-8">
          <span class="section-rule-label">Full Rankings</span>
          <div class="section-rule-line"></div>
        </div>

        <!-- Full rankings table: custom for admin/organiser, standard for others -->
        <template v-if="isAdminOrOrganiser">
          <div class="w-full overflow-x-auto rounded-xl border border-surface-600/50 shadow-sm">
            <table class="min-w-full text-sm text-content-primary">
              <thead>
                <tr class="bg-surface-900 text-content-secondary">
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider whitespace-nowrap">Rank</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider whitespace-nowrap">Participant</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider whitespace-nowrap">Total Score</th>
                  <th
                    v-for="judge in judgeColumnKeys"
                    :key="judge"
                    class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider whitespace-nowrap"
                  >{{ judge }}</th>
                  <th class="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wider whitespace-nowrap">Actions</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-surface-600/30">
                <tr
                  v-for="row in pagedFinalRows"
                  :key="row.id"
                  class="bg-surface-800 even:bg-surface-700/40 hover:bg-accent/5 transition-colors duration-150"
                >
                  <td class="px-4 py-3 whitespace-nowrap">
                    <span class="text-content-secondary">{{ row.id }}</span>
                  </td>
                  <td class="px-4 py-3 whitespace-nowrap">
                    <span class="type-body text-content-primary">{{ row.participantName }}</span>
                  </td>
                  <td class="px-4 py-3 whitespace-nowrap">
                    <span class="text-content-secondary">{{ row.totalScore }}</span>
                  </td>
                  <td
                    v-for="judge in judgeColumnKeys"
                    :key="judge"
                    class="px-4 py-3 whitespace-nowrap"
                  >
                    <span class="text-content-secondary">{{ row[judge] !== undefined && row[judge] !== null ? row[judge] : '—' }}</span>
                  </td>
                  <td class="px-4 py-3 whitespace-nowrap">
                    <div class="flex items-center gap-1.5">
                      <!-- Score breakdown button (multi-criteria only) -->
                      <!-- aria-labels: icon-only row actions get accessible names; p-2 enlarges hit area -->
                      <button
                        v-if="topNResult.isMultiAspect"
                        @click="viewBreakdown(row.participantName)"
                        title="View score breakdown"
                        :aria-label="`View score breakdown for ${row.participantName}`"
                        class="p-2 rounded-lg text-content-muted hover:text-accent hover:bg-surface-700 transition-colors"
                      >
                        <i class="pi pi-chart-bar text-sm" aria-hidden="true"></i>
                      </button>
                      <!-- Feedback button -->
                      <button
                        @click="viewFeedback(row.participantName)"
                        title="View judge feedback"
                        :aria-label="`View judge feedback for ${row.participantName}`"
                        class="p-2 rounded-lg text-content-muted hover:text-accent hover:bg-surface-700 transition-colors"
                      >
                        <i class="pi pi-comment text-sm" aria-hidden="true"></i>
                      </button>
                      <!-- QR button: only visible when results are released and ref code exists -->
                      <button
                        v-if="resultsReleased && refsMap[row.participantName]"
                        @click="openQR(row.participantName)"
                        title="Show QR code for results portal"
                        :aria-label="`Show results QR code for ${row.participantName}`"
                        class="p-2 rounded-lg text-content-muted hover:text-emerald-400 hover:bg-surface-700 transition-colors"
                      >
                        <i class="pi pi-qrcode text-sm" aria-hidden="true"></i>
                      </button>
                    </div>
                  </td>
                </tr>
                <tr v-if="finalRows.length === 0">
                  <td :colspan="3 + judgeColumnKeys.length + 1" class="px-4 py-10 text-center text-content-muted text-sm">
                    <i class="pi pi-inbox text-2xl block mb-2 opacity-40"></i>
                    No data available
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Pagination bar -->
          <div v-if="totalTablePages > 1" class="flex items-center justify-between mt-3 px-1">
            <span class="text-xs text-content-muted">
              {{ (tablePage - 1) * PAGE_SIZE + 1 }}–{{ Math.min(tablePage * PAGE_SIZE, finalRows.length) }}
              of {{ finalRows.length }}
            </span>
            <div class="flex items-center gap-1">
              <!-- aria-label: icon-only pagination controls -->
              <button
                @click="tablePage--"
                :disabled="tablePage === 1"
                aria-label="Previous page"
                class="px-2.5 py-1.5 rounded-lg text-sm font-semibold border border-surface-600 bg-surface-800
                       text-content-secondary hover:bg-surface-700 disabled:opacity-30 disabled:cursor-not-allowed transition-all"
              ><i class="pi pi-chevron-left text-xs" aria-hidden="true"></i></button>
              <button
                v-for="p in totalTablePages"
                :key="p"
                @click="tablePage = p"
                class="w-8 h-8 rounded-lg text-sm font-semibold border transition-all"
                :class="tablePage === p
                  ? 'bg-accent text-surface-900 border-accent'
                  : 'border-surface-600 bg-surface-800 text-content-secondary hover:bg-surface-700'"
              >{{ p }}</button>
              <button
                @click="tablePage++"
                :disabled="tablePage === totalTablePages"
                aria-label="Next page"
                class="px-2.5 py-1.5 rounded-lg text-sm font-semibold border border-surface-600 bg-surface-800
                       text-content-secondary hover:bg-surface-700 disabled:opacity-30 disabled:cursor-not-allowed transition-all"
              ><i class="pi pi-chevron-right text-xs" aria-hidden="true"></i></button>
            </div>
          </div>
        </template>
        <template v-else>
          <DynamicTable
            v-model:tableValue="finalRows"
            :tableConfig="topNResult.columns"
          />
        </template>

        <p
          v-if="topNResult.hasTieBreaker && !tieBreakerConfirmed"
          class="text-xs text-amber-400/70 mt-3 text-center"
        >
          Showing {{ topNResult.rows.length }} participants — {{ topNResult.tiedCount }} tied at the Top {{ topNResult.cutoff }} cutoff
        </p>
      </div>

      <!-- Empty state -->
      <div v-else class="flex flex-col items-center justify-center py-20 text-center">
        <div class="para-chip-sm w-14 h-14 flex items-center justify-center mb-4">
          <i class="pi pi-chart-bar text-content-muted text-xl"></i>
        </div>
        <p class="type-body text-content-secondary">{{ selectedGenre ? `No scores for ${selectedGenre}` : 'No scores yet' }}</p>
        <p class="type-label text-content-muted mt-1">{{ selectedGenre ? 'Judges need to submit scores for this genre' : 'Select an event and genre to view scores' }}</p>
      </div>
    </template>

    <!-- By Judge: separate tables per judge -->
    <template v-if="selectedTabulation === 'By Judge'">
      <template v-if="filteredParticipantsForScore.byJudge && Object.keys(filteredParticipantsForScore.byJudge).length > 0">
        <div
          v-for="(group, judge) in filteredParticipantsForScore.byJudge"
          :key="judge"
          class="mb-8"
        >
          <div class="section-rule mb-3">
            <span class="section-rule-label">{{ judge }}</span>
            <div class="section-rule-line"></div>
          </div>
          <DynamicTable
            v-model:tableValue="group.rows"
            :tableConfig="group.columns"
          />
        </div>
      </template>

      <!-- Empty state -->
      <div v-else class="flex flex-col items-center justify-center py-20 text-center">
        <div class="para-chip-sm w-14 h-14 flex items-center justify-center mb-4">
          <i class="pi pi-chart-bar text-content-muted text-xl"></i>
        </div>
        <p class="type-body text-content-secondary">{{ selectedGenre ? `No scores for ${selectedGenre}` : 'No scores yet' }}</p>
        <p class="type-label text-content-muted mt-1">{{ selectedGenre ? 'Judges need to submit scores for this genre' : 'Select an event and genre to view scores' }}</p>
      </div>
    </template>

  </div>
  </div>

  <!-- Feedback Panel Modal -->
  <Teleport to="body">
    <div
      v-if="showFeedbackPanel"
      class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
    >
      <!-- Backdrop -->
      <div
        class="absolute inset-0 bg-black/60 backdrop-blur-sm"
        @click="showFeedbackPanel = false"
      ></div>

      <!-- Panel — role=dialog so the modal is announced; labelled close button -->
      <div role="dialog" aria-modal="true" aria-label="Judge feedback"
        class="relative z-10 w-full sm:max-w-lg bg-surface-800 rounded-t-2xl sm:rounded-2xl border border-surface-600/50 shadow-xl max-h-[85vh] flex flex-col">
        <!-- Header -->
        <div class="flex items-center justify-between px-5 py-4 border-b border-surface-700/50">
          <div>
            <h3 class="font-heading font-bold text-content-primary">Judge Feedback</h3>
            <p class="text-xs text-content-muted mt-0.5">
              {{ feedbackParticipant }}
              <span v-if="selectedGenre" class="opacity-60"> · {{ selectedGenre }}</span>
            </p>
          </div>
          <button
            @click="showFeedbackPanel = false"
            aria-label="Close feedback panel"
            class="w-11 h-11 flex items-center justify-center rounded-lg text-content-muted hover:text-content-primary hover:bg-surface-700 transition-colors"
          >
            <i class="pi pi-times text-sm" aria-hidden="true"></i>
          </button>
        </div>

        <!-- Content -->
        <div class="overflow-y-auto px-5 py-4 flex-1">
          <!-- Loading -->
          <div v-if="feedbackLoading" class="flex items-center justify-center py-12">
            <i class="pi pi-spin pi-spinner text-accent text-2xl"></i>
          </div>

          <!-- No feedback empty state -->
          <div
            v-else-if="feedbackData.length === 0"
            class="flex flex-col items-center justify-center py-12 text-center"
          >
            <div class="w-12 h-12 rounded-2xl bg-surface-700 flex items-center justify-center mb-3">
              <i class="pi pi-comment text-content-muted text-lg"></i>
            </div>
            <p class="font-heading font-semibold text-content-secondary text-sm">No feedback yet</p>
            <p class="text-xs text-content-muted mt-1">Judges haven't submitted feedback for this participant</p>
          </div>

          <!-- Feedback list -->
          <div v-else class="space-y-5">
            <div
              v-for="item in feedbackData"
              :key="item.judgeName"
              class="rounded-xl border border-surface-600/40 bg-surface-700/30 p-4"
            >
              <!-- Judge name -->
              <div class="flex items-center gap-2 mb-3">
                <div class="w-7 h-7 rounded-full bg-surface-600 flex items-center justify-center flex-shrink-0">
                  <i class="pi pi-user text-content-secondary" style="font-size: 11px"></i>
                </div>
                <span class="font-heading font-bold text-content-primary text-sm">{{ item.judgeName }}</span>
              </div>

              <!-- Tags grouped by group -->
              <template v-if="item.tags && item.tags.length > 0">
                <div
                  v-for="(tags, groupName) in groupTags(item.tags)"
                  :key="groupName"
                  class="mb-2"
                >
                  <p class="text-xs text-content-muted font-semibold uppercase tracking-wide mb-1.5">{{ groupName }}</p>
                  <div class="flex flex-wrap gap-1.5">
                    <span
                      v-for="tag in tags"
                      :key="tag.label"
                      class="text-xs px-2.5 py-1 rounded-full font-medium border"
                      :class="groupName === 'Strengths'
                        ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30'
                        : 'bg-amber-500/10 text-amber-400 border-amber-500/30'"
                    >{{ tag.label }}</span>
                  </div>
                </div>
              </template>
              <p v-else-if="!item.note" class="text-xs text-content-muted italic">No tags selected</p>

              <!-- Judge note -->
              <div v-if="item.note" class="mt-3 pt-3 border-t border-surface-600/40">
                <p class="text-xs text-content-muted font-semibold uppercase tracking-wide mb-1">Note</p>
                <p class="text-sm text-content-secondary italic">"{{ item.note }}"</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>

  <!-- Score Breakdown Modal -->
  <Teleport to="body">
    <div
      v-if="showBreakdown"
      class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
    >
      <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="showBreakdown = false"></div>
      <!-- role=dialog + labelled close button -->
      <div role="dialog" aria-modal="true" aria-label="Score breakdown"
        class="relative z-10 w-full sm:max-w-lg bg-surface-800 rounded-t-2xl sm:rounded-2xl border border-surface-600/50 shadow-xl max-h-[85vh] flex flex-col">
        <!-- Header -->
        <div class="flex items-center justify-between px-5 py-4 border-b border-surface-700/50">
          <div>
            <h3 class="font-heading font-bold text-content-primary">Score Breakdown</h3>
            <p class="text-xs text-content-muted mt-0.5">{{ breakdownParticipant }} · {{ selectedGenre }}</p>
          </div>
          <button
            @click="showBreakdown = false"
            aria-label="Close score breakdown"
            class="w-11 h-11 flex items-center justify-center rounded-lg text-content-muted hover:text-content-primary hover:bg-surface-700 transition-colors"
          >
            <i class="pi pi-times text-sm" aria-hidden="true"></i>
          </button>
        </div>
        <!-- Content -->
        <div class="overflow-y-auto px-5 py-4 flex-1 space-y-5">
          <div
            v-for="(aspects, judge) in breakdownRows"
            :key="judge"
            class="rounded-xl border border-surface-600/40 bg-surface-700/20 p-4"
          >
            <p class="text-xs font-bold text-content-secondary mb-3">{{ judge }}</p>
            <div class="space-y-1.5">
              <div
                v-for="(score, aspect) in aspects"
                :key="aspect"
                class="flex items-center justify-between px-3 py-2 rounded-lg bg-surface-700/50"
              >
                <span class="text-sm text-content-secondary">{{ aspect }}</span>
                <span class="font-source font-bold text-accent text-sm">{{ score }}</span>
              </div>
            </div>
          </div>
          <div v-if="Object.keys(breakdownRows).length === 0" class="text-center py-10">
            <p class="text-sm text-content-muted">No score data available</p>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>
