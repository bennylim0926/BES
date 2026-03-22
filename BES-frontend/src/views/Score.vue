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
const participants = ref([])
const tabulationMethod = ref(["By Total", "By Judge"])
const selectedParticipant = ref("")
const showSubmitScore = ref(false)

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

watch(selectedGenre, (newVal) => { if (newVal) localStorage.setItem("selectedGenre", newVal); }, { immediate: true });
watch(selectedTabulation, (newVal) => { if (newVal) localStorage.setItem("selectedTabMethod", newVal); }, { immediate: true });

const filteredParticipantsForScore = computed({
  get() {
    return transformForScore(participants.value.filter(p => p.genreName === selectedGenre.value))
  }
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
      <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div class="flex flex-col gap-1">
          <span class="text-xs font-semibold text-content-muted uppercase tracking-wide">Event</span>
          <span class="badge-neutral text-sm px-3 py-1.5 self-start">{{ selectedEvent }}</span>
        </div>
        <ReusableDropdown v-model="selectedGenre"      labelId="Genre"    :options="uniqueGenres" />
        <ReusableDropdown v-model="selectedTabulation" labelId="Group By" :options="tabulationMethod" />
      </div>
    </div>

    <!-- By Total: ranked leaderboard -->
    <template v-if="selectedTabulation === 'By Total'">
      <div v-if="filteredParticipantsForScore.rows && filteredParticipantsForScore.rows.length > 0">
        <!-- Top 3 podium cards (if enough participants) -->
        <div
          v-if="filteredParticipantsForScore.rows.length >= 3"
          class="grid grid-cols-3 gap-4 mb-6"
        >
          <!-- 2nd place -->
          <div class="stat-card p-5 text-center order-1 border-t-2 border-t-content-secondary">
            <div class="text-2xl font-heading font-extrabold text-content-muted mb-1">2</div>
            <div class="font-heading font-bold text-content-secondary text-sm leading-tight mb-2">
              {{ filteredParticipantsForScore.rows[1].participantName }}
            </div>
            <div class="text-2xl font-source font-extrabold text-content-secondary">
              {{ filteredParticipantsForScore.rows[1].totalScore }}
            </div>
          </div>
          <!-- 1st place -->
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
              {{ filteredParticipantsForScore.rows[0].participantName }}
            </div>
            <div class="text-4xl font-source font-extrabold text-primary-400">
              {{ filteredParticipantsForScore.rows[0].totalScore }}
            </div>
          </div>
          <!-- 3rd place -->
          <div class="stat-card p-5 text-center order-3 border-t-2 border-t-accent-700">
            <div class="text-2xl font-heading font-extrabold text-surface-600 mb-1">3</div>
            <div class="font-heading font-bold text-content-muted text-sm leading-tight mb-2">
              {{ filteredParticipantsForScore.rows[2].participantName }}
            </div>
            <div class="text-2xl font-source font-extrabold text-content-muted">
              {{ filteredParticipantsForScore.rows[2].totalScore }}
            </div>
          </div>
        </div>

        <!-- Full rankings table -->
        <DynamicTable
          @onClick="editScore"
          v-model:tableValue="filteredParticipantsForScore.rows"
          :tableConfig="filteredParticipantsForScore.columns"
        />
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
