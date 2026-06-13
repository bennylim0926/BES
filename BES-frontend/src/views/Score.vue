<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue';
import { getParticipantScore, getParticipantFeedback, getResultsStatus, releaseResults, getParticipantRefs, getScoringCriteria, setResolvedParticipants } from '@/utils/api';
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

const includeNextInPool = () => {
  const next = nextEligibleAdd.value
  if (!next) return
  const s = new Set(addedToPool.value)
  s.add(next.participantName)
  addedToPool.value = s
  tieBreakerConfirmed.value = false
  saveTieBreaker()
}

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
  // Winners can come from the tie base OR the manually-added pool.
  const poolRows = [...tiedParticipants.value, ...addedToPoolOrdered.value]
  const winners = poolRows.filter(r => tieBreakerWinners.value.has(r.participantName))
  return [...above, ...winners].map((r, i) => ({ ...r, id: i + 1 }))
})

// Per-row judge-score meta line — "A 7.2 · J 7.1 · M 7.0 · S 7.1".
function judgeMetaLine(row) {
  if (!topNResult.value.columns) return ''
  const judgeKeys = topNResult.value.columns
    .filter(c => !['id', 'participantName', 'totalScore'].includes(c.key))
    .map(c => c.key)
  if (judgeKeys.length === 0) return ''
  return judgeKeys.map(j => {
    const v = row[j]
    return v != null ? `${j[0].toUpperCase()} ${v}` : `${j[0].toUpperCase()} —`
  }).join(' · ')
}

// Standard in-cut rows (after rank 1, excluding the tied band rows when they're rendering).
const inCutStandardRows = computed(() => {
  if (!finalRows.value.length) return []
  const skipFromEnd = (topNResult.value.hasTieBreaker && !tieBreakerConfirmed.value)
    ? topNResult.value.tiedCount
    : 0
  return finalRows.value.slice(1, finalRows.value.length - skipFromEnd)
})

// Eliminated rows (below the cut), only computed when expanded.
const eliminatedRows = computed(() => {
  const visible = new Set(finalRows.value.map(r => r.participantName))
  return allRowsForPool.value
    .filter(r => !visible.has(r.participantName))
    .map((r, i) => ({ ...r, id: finalRows.value.length + i + 1 }))
})

// Control-mode status banner — sits below the Top N picker.
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
const eliminatedSummary = computed(() => {
  const all = allRowsForPool.value
  const visible = new Set(finalRows.value.map(r => r.participantName))
  const eliminated = all.filter(r => !visible.has(r.participantName))
  if (eliminated.length === 0) return null
  const lowestScore = Math.min(...eliminated.map(r => r.totalScore))
  return { count: eliminated.length, lowestScore }
})

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
// eslint-disable-next-line no-unused-vars
const totalTablePages = computed(() => Math.max(1, Math.ceil(finalRows.value.length / PAGE_SIZE)))
// eslint-disable-next-line no-unused-vars
const pagedFinalRows = computed(() =>
  finalRows.value.slice((tablePage.value - 1) * PAGE_SIZE, tablePage.value * PAGE_SIZE)
)

// Judge column keys for the custom admin table
// eslint-disable-next-line no-unused-vars
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

    <!-- Header row -->
    <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between mb-6">
      <div>
        <p class="type-label text-content-muted mb-1">SCOREBOARD · {{ selectedEvent }}</p>
        <h1 class="type-page-title">
          {{ selectedGenre || 'No Genre' }}
          <template v-if="hasTeamAndSoloMix"> — {{ selectedEntryType.toUpperCase() }}</template>
        </h1>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <!-- Release Results pill (admin/organiser only) -->
        <button
          v-if="isAdminOrOrganiser"
          @click="toggleRelease"
          :aria-pressed="resultsReleased"
          class="para-chip-sm px-3 py-1.5 type-label inline-flex items-center gap-1.5 transition-all"
          :class="resultsReleased
            ? 'border-emerald-500/40 text-emerald-400'
            : 'text-content-muted hover:text-content-primary'"
        >
          <span class="w-1.5 h-1.5 rounded-full" :class="resultsReleased ? 'bg-emerald-400 shadow-[0_0_6px_rgba(52,211,153,0.7)]' : 'bg-content-muted/40'"></span>
          {{ resultsReleased ? 'RELEASED' : 'HIDDEN' }}
        </button>
        <!-- Mode toggle -->
        <div class="flex p-0.5 border border-surface-600 bg-surface-800/50" role="group" aria-label="Display mode">
          <button
            v-for="m in ['control', 'broadcast']"
            :key="m"
            @click="mode = m"
            :aria-pressed="mode === m"
            class="para-chip-sm px-3 py-1.5 type-label transition-all"
            :class="mode === m
              ? 'text-accent border-[color:var(--accent-muted)]'
              : 'text-content-muted hover:text-content-primary'"
          >{{ m.toUpperCase() }}</button>
        </div>
      </div>
    </div>

    <!-- Secondary filter row (Control mode only) -->
    <div v-if="mode === 'control'" class="card p-3 mb-6">
      <div class="flex flex-wrap items-center gap-3">
        <!-- Genre -->
        <div class="flex flex-wrap gap-1" role="group" aria-label="Filter by genre">
          <span class="type-label text-content-muted self-center mr-1">GENRE</span>
          <button
            v-for="g in uniqueGenres"
            :key="g"
            @click="selectedGenre = g"
            :aria-pressed="selectedGenre === g"
            class="para-chip-sm px-3 py-1.5 type-label transition-all"
            :class="selectedGenre === g ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
          >{{ g }}</button>
        </div>
        <span class="text-surface-600 select-none" aria-hidden="true">|</span>
        <!-- View (formerly "Group By") -->
        <div class="flex flex-wrap gap-1" role="group" aria-label="View">
          <span class="type-label text-content-muted self-center mr-1">VIEW</span>
          <button
            v-for="t in tabulationMethod"
            :key="t"
            @click="selectedTabulation = t"
            :aria-pressed="selectedTabulation === t"
            class="para-chip-sm px-3 py-1.5 type-label transition-all"
            :class="selectedTabulation === t ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
          >{{ t.toUpperCase() }}</button>
        </div>
        <template v-if="hasTeamAndSoloMix">
          <span class="text-surface-600 select-none" aria-hidden="true">|</span>
          <div class="flex flex-wrap gap-1" role="group" aria-label="Entry type">
            <span class="type-label text-content-muted self-center mr-1">TYPE</span>
            <button
              v-for="t in ['Teams', 'Solo']"
              :key="t"
              @click="selectedEntryType = t"
              :aria-pressed="selectedEntryType === t"
              class="para-chip-sm px-3 py-1.5 type-label transition-all"
              :class="selectedEntryType === t ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
            >{{ t.toUpperCase() }}</button>
          </div>
        </template>
      </div>
    </div>

    <!-- Top N hero picker (Control mode only) -->
    <div v-if="mode === 'control' && selectedTabulation === 'By Total'" class="mb-6">
      <p class="type-label text-content-muted mb-3">QUALIFY</p>
      <div class="grid grid-cols-4 gap-2" role="group" aria-label="Qualify top N">
        <button
          v-for="n in topNOptions"
          :key="n"
          @click="selectedTopN = n"
          :aria-pressed="selectedTopN === n"
          class="para-chip py-5 flex flex-col items-center justify-center gap-1 transition-all"
          :class="selectedTopN === n
            ? 'border-[color:var(--accent-color)] shadow-[0_0_20px_var(--accent-subtle)]'
            : 'opacity-50 hover:opacity-90'"
        >
          <span class="type-stat" style="font-size: 28px; line-height: 1">{{ n === 'All' ? 'ALL' : n.replace('Top ', '') }}</span>
          <span class="type-label" style="font-size: 9px">{{ n === 'All' ? `· ${allRowsForPool.length}` : 'TOP' }}</span>
        </button>
      </div>

      <!-- Status banner -->
      <div
        v-if="statusBanner"
        role="status"
        aria-live="polite"
        class="mt-4 px-4 py-3 flex items-center gap-3 border-l-[3px]"
        :class="{
          'border-emerald-400 bg-emerald-500/8': statusBanner.tone === 'clean',
          'border-amber-400 bg-amber-500/8':    statusBanner.tone === 'tie',
          'border-rose-400 bg-rose-500/8':      statusBanner.tone === 'insufficient',
        }"
      >
        <span
          class="w-1.5 h-1.5 rounded-full flex-shrink-0"
          :class="{
            'bg-emerald-400 shadow-[0_0_8px_rgba(52,211,153,0.6)]': statusBanner.tone === 'clean',
            'bg-amber-400 shadow-[0_0_8px_rgba(245,158,11,0.6)]':   statusBanner.tone === 'tie',
            'bg-rose-400 shadow-[0_0_8px_rgba(244,63,94,0.6)]':     statusBanner.tone === 'insufficient',
          }"
        ></span>
        <p class="type-label flex flex-wrap gap-2" :class="{
          'text-emerald-400': statusBanner.tone === 'clean',
          'text-amber-400':   statusBanner.tone === 'tie',
          'text-rose-400':    statusBanner.tone === 'insufficient',
        }">{{ statusBanner.message }}</p>
      </div>
    </div>

    <!-- By Total: cut-line leaderboard -->
    <template v-if="mode === 'control' && selectedTabulation === 'By Total'">
      <div v-if="topNResult.rows && topNResult.rows.length > 0">
        <p class="type-label text-content-muted mb-3 flex justify-between">
          <span>RANKINGS · {{ allRowsForPool.length }} SCORED</span>
        </p>

        <!-- Rows -->
        <div class="flex flex-col gap-1">
          <!-- Leader row: rank 1, in cut, larger -->
          <div
            v-if="finalRows[0]"
            class="para-chip flex items-start gap-3 px-4 py-3 border-l-[3px] border-[color:var(--accent-color)]"
          >
            <span class="type-stat flex-shrink-0" style="font-size: 24px; line-height: 1; min-width: 40px">{{ finalRows[0].id }}</span>
            <div class="flex-1 min-w-0">
              <p class="type-body text-content-primary" style="font-size: 16px">{{ finalRows[0].participantName }}</p>
              <p v-if="judgeMetaLine(finalRows[0])" class="type-label text-content-muted/70 mt-0.5">{{ judgeMetaLine(finalRows[0]) }}</p>
            </div>
            <span class="type-stat flex-shrink-0" style="font-size: 28px; line-height: 1">{{ finalRows[0].totalScore }}</span>
            <div v-if="isAdminOrOrganiser" class="flex gap-1 flex-shrink-0 opacity-50 hover:opacity-100 transition-opacity">
              <button v-if="topNResult.isMultiAspect" @click="viewBreakdown(finalRows[0].participantName)" :aria-label="`View score breakdown for ${finalRows[0].participantName}`" class="p-2 rounded text-content-muted hover:text-accent"><i class="pi pi-chart-bar text-sm" aria-hidden="true"></i></button>
              <button @click="viewFeedback(finalRows[0].participantName)" :aria-label="`View judge feedback for ${finalRows[0].participantName}`" class="p-2 rounded text-content-muted hover:text-accent"><i class="pi pi-comment text-sm" aria-hidden="true"></i></button>
              <button v-if="resultsReleased && refsMap[finalRows[0].participantName]" @click="openQR(finalRows[0].participantName)" :aria-label="`Show results QR code for ${finalRows[0].participantName}`" class="p-2 rounded text-content-muted hover:text-emerald-400"><i class="pi pi-qrcode text-sm" aria-hidden="true"></i></button>
            </div>
          </div>

          <!-- Standard rows: rank 2 through N (excluding tied rows when band is rendering) -->
          <template v-for="row in inCutStandardRows" :key="row.participantName">
            <div class="para-chip flex items-start gap-3 px-4 py-2 bg-surface-700/10">
              <span class="type-stat flex-shrink-0 text-content-secondary" style="font-size: 18px; line-height: 1; min-width: 40px">{{ row.id }}</span>
              <div class="flex-1 min-w-0">
                <p class="type-body text-content-primary">{{ row.participantName }}</p>
                <p v-if="judgeMetaLine(row)" class="type-label text-content-muted/50 mt-0.5">{{ judgeMetaLine(row) }}</p>
              </div>
              <span class="type-stat flex-shrink-0" style="font-size: 20px; line-height: 1">{{ row.totalScore }}</span>
              <div v-if="isAdminOrOrganiser" class="flex gap-1 flex-shrink-0 opacity-50 hover:opacity-100 transition-opacity">
                <button v-if="topNResult.isMultiAspect" @click="viewBreakdown(row.participantName)" :aria-label="`View score breakdown for ${row.participantName}`" class="p-2 rounded text-content-muted hover:text-accent"><i class="pi pi-chart-bar text-sm" aria-hidden="true"></i></button>
                <button @click="viewFeedback(row.participantName)" :aria-label="`View judge feedback for ${row.participantName}`" class="p-2 rounded text-content-muted hover:text-accent"><i class="pi pi-comment text-sm" aria-hidden="true"></i></button>
                <button v-if="resultsReleased && refsMap[row.participantName]" @click="openQR(row.participantName)" :aria-label="`Show results QR code for ${row.participantName}`" class="p-2 rounded text-content-muted hover:text-emerald-400"><i class="pi pi-qrcode text-sm" aria-hidden="true"></i></button>
              </div>
            </div>
          </template>

          <!-- Tie resolver band -->
          <div
            v-if="topNResult.hasTieBreaker && !tieBreakerConfirmed"
            role="region"
            aria-label="Tie-breaker resolution"
            class="border border-amber-500/40 bg-amber-500/[0.05] p-4 my-3"
          >
            <!-- Header -->
            <div class="flex justify-between items-baseline mb-3 flex-wrap gap-2">
              <span class="type-label text-amber-400 font-bold">
                RESOLVE TIE @ {{ topNResult.tieBreakerScore }} — POOL OF {{ eligibilityPoolNames.size }} FOR {{ spotsFromTie }} SPOT{{ spotsFromTie > 1 ? 'S' : '' }}
              </span>
              <span class="type-label text-content-muted">{{ tieBreakerWinners.size }} SELECTED</span>
            </div>

            <!-- Tie base section -->
            <p class="type-label text-content-muted/60 mb-1" style="font-size: 8px; letter-spacing: 0.24em">TIE BASE · LOCKED</p>
            <div class="flex flex-col gap-1 mb-3">
              <button
                v-for="p in tiedParticipants"
                :key="p.participantName"
                @click="toggleWinner(p.participantName)"
                :disabled="!tieBreakerWinners.has(p.participantName) && tieBreakerWinners.size >= spotsFromTie"
                :aria-pressed="tieBreakerWinners.has(p.participantName)"
                class="flex items-center justify-between px-3 py-2 border-l-2 transition-all disabled:opacity-40 disabled:cursor-not-allowed text-left"
                :class="tieBreakerWinners.has(p.participantName)
                  ? 'bg-emerald-500/10 border-emerald-400 ring-1 ring-emerald-500/30'
                  : 'bg-surface-700/10 border-surface-600/40 hover:border-surface-500'"
              >
                <div class="flex items-center gap-3">
                  <span class="w-4 h-4 rounded-full border-2 flex items-center justify-center flex-shrink-0"
                    :class="tieBreakerWinners.has(p.participantName) ? 'bg-emerald-500 border-emerald-500' : 'border-surface-500'">
                    <i v-if="tieBreakerWinners.has(p.participantName)" class="pi pi-check text-white" style="font-size: 9px"></i>
                  </span>
                  <span class="type-body" :class="tieBreakerWinners.has(p.participantName) ? 'text-emerald-400 font-bold' : 'text-content-primary'">
                    {{ p.participantName }}
                  </span>
                </div>
                <div class="flex items-center gap-2">
                  <span class="type-stat" style="font-size: 14px">{{ p.totalScore }}</span>
                  <span v-if="tieBreakerWinners.has(p.participantName)" class="type-label px-2 py-0.5 bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">ADVANCES</span>
                </div>
              </button>
            </div>

            <!-- Added pool section -->
            <template v-if="addedToPoolOrdered.length > 0">
              <div class="relative h-px my-3" style="background: linear-gradient(90deg, transparent, rgba(245,158,11,0.4), transparent);">
                <span class="absolute left-1/2 -translate-x-1/2 -top-2.5 bg-surface-900 px-2 type-label text-amber-400" style="font-size: 8px">ADDED · {{ addedToPoolOrdered.length }}</span>
              </div>
              <div class="flex flex-col gap-1 mb-3">
                <button
                  v-for="p in addedToPoolOrdered"
                  :key="p.participantName"
                  @click="toggleWinner(p.participantName)"
                  :disabled="!tieBreakerWinners.has(p.participantName) && tieBreakerWinners.size >= spotsFromTie"
                  :aria-pressed="tieBreakerWinners.has(p.participantName)"
                  class="flex items-center justify-between px-3 py-2 border-l-2 border-amber-500/50 transition-all disabled:opacity-40 disabled:cursor-not-allowed text-left"
                  :class="tieBreakerWinners.has(p.participantName) ? 'bg-emerald-500/10 ring-1 ring-emerald-500/30' : 'bg-surface-700/10 hover:bg-surface-700/30'"
                >
                  <div class="flex items-center gap-3">
                    <span class="w-4 h-4 rounded-full border-2 flex items-center justify-center flex-shrink-0"
                      :class="tieBreakerWinners.has(p.participantName) ? 'bg-emerald-500 border-emerald-500' : 'border-surface-500'">
                      <i v-if="tieBreakerWinners.has(p.participantName)" class="pi pi-check text-white" style="font-size: 9px"></i>
                    </span>
                    <span class="type-body" :class="tieBreakerWinners.has(p.participantName) ? 'text-emerald-400 font-bold' : 'text-content-primary'">
                      {{ p.participantName }}
                    </span>
                  </div>
                  <div class="flex items-center gap-2">
                    <span class="type-stat" style="font-size: 14px">{{ p.totalScore }}</span>
                    <span v-if="tieBreakerWinners.has(p.participantName)" class="type-label px-2 py-0.5 bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">ADVANCES</span>
                  </div>
                </button>
              </div>
            </template>

            <!-- Stepper buttons -->
            <div class="grid grid-cols-2 gap-2 mb-3">
              <button
                @click="excludeLastFromPool"
                :disabled="!nextEligibleRemove"
                :aria-label="nextEligibleRemove ? `Exclude ${nextEligibleRemove.participantName} from tie pool, score ${nextEligibleRemove.totalScore}` : 'Exclude (disabled)'"
                :aria-disabled="!nextEligibleRemove"
                class="para-chip-sm px-3 py-2.5 type-label text-left disabled:opacity-30 disabled:cursor-not-allowed text-content-muted hover:text-content-primary"
              >
                <span class="opacity-50">−  EXCLUDE</span>&nbsp;
                <span v-if="nextEligibleRemove">{{ nextEligibleRemove.participantName }} · {{ nextEligibleRemove.totalScore }}</span>
                <span v-else>—</span>
              </button>
              <button
                @click="includeNextInPool"
                :disabled="!nextEligibleAdd"
                :aria-label="nextEligibleAdd ? `Include ${nextEligibleAdd.participantName} in tie pool, score ${nextEligibleAdd.totalScore}` : 'Include (disabled)'"
                :aria-disabled="!nextEligibleAdd"
                class="para-chip-sm px-3 py-2.5 type-label text-left bg-amber-500/10 border-amber-500/40 text-amber-400 hover:bg-amber-500/15 disabled:opacity-30 disabled:cursor-not-allowed"
              >
                <span class="opacity-70">+  INCLUDE</span>&nbsp;
                <span v-if="nextEligibleAdd">{{ nextEligibleAdd.participantName }} · {{ nextEligibleAdd.totalScore }}</span>
                <span v-else>—</span>
              </button>
            </div>

            <!-- Confirm + Reset -->
            <div class="flex gap-2">
              <button
                @click="confirmTieBreaker"
                :disabled="tieBreakerWinners.size !== spotsFromTie"
                class="flex-1 py-2.5 type-label font-bold disabled:opacity-30 disabled:cursor-not-allowed transition-all"
                :class="tieBreakerWinners.size === spotsFromTie ? 'bg-emerald-500 text-surface-900 hover:bg-emerald-400' : 'bg-surface-700 text-content-muted border border-surface-600'"
              >
                <i class="pi pi-check mr-1"></i>CONFIRM TOP {{ topNResult.cutoff }}
              </button>
              <button @click="resetTieBreaker" class="px-4 py-2.5 type-label border border-surface-600 text-content-muted hover:text-content-primary">RESET</button>
            </div>
          </div>
          <div v-else-if="topNResult.hasTieBreaker && tieBreakerConfirmed" class="px-4 py-2 border-l-2 border-emerald-400 bg-emerald-500/8 type-label text-emerald-400 flex items-center justify-between">
            <span>✓ TOP {{ topNResult.cutoff }} CONFIRMED — {{ tieBreakerWinners.size }} ADVANCED</span>
            <button @click="resetTieBreaker" class="type-label text-content-muted hover:text-content-primary">RESET</button>
          </div>

          <!-- Top N line (separator) -->
          <div
            v-if="topNResult.rows.length >= (selectedTopN === 'All' ? 0 : parseInt(selectedTopN.replace('Top ','')))
                  && selectedTopN !== 'All'
                  && (!topNResult.hasTieBreaker || tieBreakerConfirmed)"
            role="separator"
            :aria-label="`Top ${topNResult.cutoff} line`"
            class="relative h-px my-3"
            style="background: linear-gradient(90deg, transparent, var(--accent-color) 50%, transparent); box-shadow: 0 0 12px var(--accent-muted);"
          >
            <span class="absolute right-0 -top-5 type-label text-content-muted">▲ TOP {{ topNResult.cutoff }}</span>
          </div>

          <!-- Eliminated rows (collapsed by default) -->
          <template v-if="eliminatedSummary">
            <template v-if="eliminatedExpanded">
              <div v-for="row in eliminatedRows" :key="row.participantName" class="para-chip flex items-center gap-3 px-4 py-2 opacity-30">
                <span class="type-stat flex-shrink-0" style="font-size: 16px; line-height: 1; min-width: 40px">{{ row.id }}</span>
                <span class="flex-1 type-body text-content-primary">{{ row.participantName }}</span>
                <span class="type-stat flex-shrink-0" style="font-size: 18px; line-height: 1">{{ row.totalScore }}</span>
              </div>
            </template>
            <button
              @click="eliminatedExpanded = !eliminatedExpanded"
              class="type-label text-content-muted/60 hover:text-content-primary text-center py-2 mt-1"
            >⋯ {{ eliminatedSummary.count }} {{ eliminatedExpanded ? 'ELIMINATED · COLLAPSE' : `MORE ELIMINATED · LOWEST ${eliminatedSummary.lowestScore} · EXPAND` }} ⋯</button>
          </template>
        </div>
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

    <!-- By Judge sub-view (Control mode only) -->
    <template v-if="mode === 'control' && selectedTabulation === 'By Judge'">
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
          <div class="flex flex-col gap-1">
            <div
              v-for="row in group.rows"
              :key="row.participantName"
              class="para-chip flex items-center gap-3 px-4 py-2 bg-surface-700/10"
            >
              <span class="type-stat flex-shrink-0 text-content-secondary" style="font-size: 18px; line-height: 1; min-width: 40px">{{ row.id }}</span>
              <span class="flex-1 type-body text-content-primary">{{ row.participantName }}</span>
              <span class="type-stat flex-shrink-0" style="font-size: 20px; line-height: 1">{{ row.score }}</span>
            </div>
          </div>
        </div>
      </template>
      <div v-else class="flex flex-col items-center justify-center py-20 text-center">
        <div class="para-chip-sm w-14 h-14 flex items-center justify-center mb-4">
          <i class="pi pi-chart-bar text-content-muted text-xl"></i>
        </div>
        <p class="type-body text-content-secondary">{{ selectedGenre ? `No scores for ${selectedGenre}` : 'No scores yet' }}</p>
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
