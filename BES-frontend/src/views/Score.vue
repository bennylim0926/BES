<script setup>
import { ref, computed, watch } from 'vue';
import { getParticipantScore } from '@/utils/api';
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import DynamicTable from '@/components/DynamicTable.vue';
import { getActiveEvent } from '@/utils/auth';
import UpdateScoreForm from '@/components/UpdateScoreForm.vue';

const selectedEvent = ref(getActiveEvent()?.name || localStorage.getItem("selectedEvent") || "")
const selectedGenre = ref(localStorage.getItem("selectedGenre") || "All")
const selectedTabulation = ref(localStorage.getItem("selectedTabMethod") || "")
const selectedTopN = ref("All")
const participants = ref([])
const tabulationMethod = ref(["By Total", "By Judge"])
const topNOptions = ["All", "Top 8", "Top 16", "Top 32"]
const selectedParticipant = ref("")
const showSubmitScore = ref(false)

// Tie-breaker resolution state
const tieBreakerWinners = ref(new Set())
const tieBreakerConfirmed = ref(false)

// localStorage key scoped to event + genre + tabulation + topN
const tbKey = computed(() =>
  `tb_${selectedEvent.value}_${selectedGenre.value}_${selectedTabulation.value}_${selectedTopN.value}`
)
const tbResolvedKey = computed(() => {
  const n = selectedTopN.value === 'All' ? 0 : parseInt(selectedTopN.value.replace('Top ', ''))
  return `tbResolved_${selectedEvent.value}_${selectedGenre.value}_${n}`
})

const saveTieBreaker = () => {
  localStorage.setItem(tbKey.value, JSON.stringify({
    winners: [...tieBreakerWinners.value],
    confirmed: tieBreakerConfirmed.value,
  }))
}
const loadTieBreaker = () => {
  const saved = localStorage.getItem(tbKey.value)
  if (saved) {
    const { winners, confirmed } = JSON.parse(saved)
    tieBreakerWinners.value = new Set(winners)
    tieBreakerConfirmed.value = confirmed
  } else {
    tieBreakerWinners.value = new Set()
    tieBreakerConfirmed.value = false
  }
}
const resetTieBreaker = () => {
  tieBreakerWinners.value = new Set()
  tieBreakerConfirmed.value = false
  localStorage.removeItem(tbKey.value)
  localStorage.removeItem(tbResolvedKey.value)
}

const editScore = (name) => {
  selectedParticipant.value = name
  showSubmitScore.value = !showSubmitScore.value
}

const uniqueGenres = computed(() => {
  const genres = participants.value.map(p => p.genreName);
  return [...new Set(genres)].sort();
})

watch(selectedEvent, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedEvent", newVal);
    const res = await getParticipantScore(newVal)
    participants.value = res.map((r, i) => ({ ...r, id: i + 1 }))
  }
}, { immediate: true });

watch(selectedGenre, (newVal) => {
  if (newVal) { localStorage.setItem("selectedGenre", newVal); selectedTopN.value = 'All' }
}, { immediate: true });
watch(selectedTabulation, (newVal) => {
  if (newVal) { localStorage.setItem("selectedTabMethod", newVal); selectedTopN.value = 'All' }
  resetTieBreaker()
}, { immediate: true });
// Load persisted tie-breaker state whenever the scoped key changes
watch(tbKey, loadTieBreaker, { immediate: true })

const filteredParticipantsForScore = computed({
  get() {
    return transformForScore(participants.value.filter(p => p.genreName === selectedGenre.value))
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

const confirmTieBreaker = () => {
  if (tieBreakerWinners.value.size === spotsFromTie.value) {
    tieBreakerConfirmed.value = true
    saveTieBreaker()
    // Save resolved names for BattleControl to consume
    const resolved = finalRows.value.map(r => r.participantName)
    localStorage.setItem(tbResolvedKey.value, JSON.stringify(resolved))
  }
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

function transformForScore(data) {
  const judges = [...new Set(data.map(d => d.judgeName).filter(j => j !== null))];
  const byTotal = {}
  if (selectedTabulation.value === 'By Total') {
    data.forEach(d => {
      if (!byTotal[d.participantName]) {
        byTotal[d.participantName] = { participantName: d.participantName, totalScore: 0 }
      }
      byTotal[d.participantName][d.judgeName] = d.score
      byTotal[d.participantName].totalScore += d.score;
    });
    const rows = Object.values(byTotal)
      .map(r => ({ ...r, totalScore: Number(r.totalScore.toFixed(1)) }))
      .sort((a, b) => b.totalScore - a.totalScore)
      .map((r, i) => ({ ...r, id: i + 1 }))
    return {
      columns: [
        { key: 'id', label: 'Rank', type: 'text', readonly: true },
        { key: 'participantName', label: 'Participant', type: 'link' },
        { key: 'totalScore', label: 'Total Score', type: 'text', readonly: true },
        ...judges.map(j => ({ key: j, label: j, type: 'text', readonly: true }))
      ],
      rows
    }
  } else {
    const byJudge = {}
    data.forEach(d => {
      if (!byJudge[d.judgeName]) {
        byJudge[d.judgeName] = {
          columns: [
            { key: 'id', label: 'Rank', type: 'text', readonly: true },
            { key: 'participantName', label: 'Participant', type: 'link' },
            { key: 'score', label: 'Score', type: 'text', readonly: true },
          ],
          rows: []
        }
      }
      byJudge[d.judgeName].rows.push({ participantName: d.participantName, score: d.score });
    });
    Object.values(byJudge).forEach(group => {
      group.rows = group.rows
        .sort((a, b) => b.score - a.score)
        .map((r, i) => ({ ...r, id: i + 1 }))
    })
    return { byJudge }
  }
}
</script>

<template>
  <div class="page-container">

    <!-- Page header -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-8">
      <div>
        <h1 class="page-title">Scoreboard</h1>
        <p class="text-muted mt-1">View and compare scores across genres and judges</p>
      </div>
    </div>

    <!-- Filter card -->
    <div class="card p-5 mb-6">
      <div class="grid grid-cols-1 sm:grid-cols-4 gap-4">
        <div class="flex flex-col gap-1">
          <span class="text-xs font-semibold text-content-muted uppercase tracking-wide">Event</span>
          <span class="badge-neutral text-sm px-3 py-1.5 self-start">{{ selectedEvent }}</span>
        </div>
        <ReusableDropdown v-model="selectedGenre"      labelId="Genre"    :options="uniqueGenres" />
        <ReusableDropdown v-model="selectedTabulation" labelId="Group By" :options="tabulationMethod" />
        <ReusableDropdown v-model="selectedTopN"       labelId="Show Top" :options="topNOptions" />
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

        <!-- Top 3 podium cards -->
        <div
          v-if="finalRows.length >= 3"
          class="grid grid-cols-3 gap-4 mb-6"
        >
          <div class="stat-card p-5 text-center order-1 border-t-2 border-t-content-secondary">
            <div class="text-2xl font-heading font-extrabold text-content-muted mb-1">2</div>
            <div class="font-heading font-bold text-content-secondary text-sm leading-tight mb-2">
              {{ finalRows[1].participantName }}
            </div>
            <div class="text-2xl font-source font-extrabold text-content-secondary">
              {{ finalRows[1].totalScore }}
            </div>
          </div>
          <div
            class="stat-card p-5 text-center order-2 ring-2 ring-primary-500/30 relative border-t-2 border-t-accent-500 animate-float"
            style="box-shadow: 0 0 0 1px rgba(6,182,212,0.3), 0 8px 40px rgba(6,182,212,0.15);"
          >
            <div class="absolute -top-3 left-1/2 -translate-x-1/2">
              <span
                class="px-2 py-0.5 rounded-full bg-primary-600 text-white text-xs font-bold"
                style="box-shadow: 0 0 12px rgba(6,182,212,0.5);"
              >1st</span>
            </div>
            <div class="text-2xl font-heading font-extrabold text-primary-400 mb-1">1</div>
            <div class="font-heading font-bold text-content-primary text-sm leading-tight mb-2">
              {{ finalRows[0].participantName }}
            </div>
            <div class="text-4xl font-source font-extrabold text-primary-400">
              {{ finalRows[0].totalScore }}
            </div>
          </div>
          <div class="stat-card p-5 text-center order-3 border-t-2 border-t-accent-700">
            <div class="text-2xl font-heading font-extrabold text-surface-600 mb-1">3</div>
            <div class="font-heading font-bold text-content-muted text-sm leading-tight mb-2">
              {{ finalRows[2].participantName }}
            </div>
            <div class="text-2xl font-source font-extrabold text-content-muted">
              {{ finalRows[2].totalScore }}
            </div>
          </div>
        </div>

        <!-- Full rankings table -->
        <DynamicTable
          @onClick="editScore"
          v-model:tableValue="finalRows"
          :tableConfig="topNResult.columns"
        />

        <p
          v-if="topNResult.hasTieBreaker && !tieBreakerConfirmed"
          class="text-xs text-amber-400/70 mt-3 text-center"
        >
          Showing {{ topNResult.rows.length }} participants — {{ topNResult.tiedCount }} tied at the Top {{ topNResult.cutoff }} cutoff
        </p>
      </div>

      <!-- Empty state -->
      <div v-else class="flex flex-col items-center justify-center py-20 text-center">
        <div class="icon-wrap w-14 h-14 rounded-2xl bg-surface-700 flex items-center justify-center mb-4">
          <i class="pi pi-chart-bar text-content-muted text-xl"></i>
        </div>
        <p class="font-heading font-semibold text-content-secondary">No scores yet</p>
        <p class="text-muted text-sm mt-1">Select an event and genre to view scores</p>
      </div>
    </template>

    <!-- By Judge: separate tables per judge -->
    <template v-if="selectedTabulation === 'By Judge'">
      <div
        v-for="(group, judge) in filteredParticipantsForScore.byJudge"
        :key="judge"
        class="mb-8"
      >
        <div class="flex items-center gap-3 mb-3">
          <div class="w-8 h-8 rounded-full bg-surface-600 flex items-center justify-center">
            <i class="pi pi-user text-content-secondary text-xs"></i>
          </div>
          <h2 class="font-heading font-bold text-content-secondary">{{ judge }}</h2>
          <span class="badge-neutral text-xs">{{ group.rows.length }} participants</span>
        </div>
        <DynamicTable
          @onClick="editScore"
          v-model:tableValue="group.rows"
          :tableConfig="group.columns"
        />
      </div>
    </template>

  </div>

  <UpdateScoreForm
    :event="selectedEvent"
    :show="showSubmitScore"
    title="Update Score"
    :genre="selectedGenre"
    :name="selectedParticipant"
    @updateScore="showSubmitScore = false"
    @close="showSubmitScore = false"
  />
</template>
