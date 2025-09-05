<script setup>
import DynamicTable from '@/components/DynamicTable.vue';
import ReusableButton from '@/components/ReusableButton.vue';
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import SwipeableCards from '@/components/SwipeableCards.vue';
import { fetchAllEvents, getAllJudges, getRegisteredParticipantsByEvent } from '@/utils/api';
import { ref, computed, onMounted, watch } from 'vue';

const roles = ref(["Emcee", "Judge"])
const selectedEvent = ref("")
const selectedRole = ref("")
const selectedGenre = ref("")
const filteredJudge = ref("")
const currentJudge = ref("")
const allJudges = ref([])
const allEvents = ref([])
const participants = ref([])

const filteredParticipantsForJudge = computed({
  get() {
    return participants.value
    //   .map( p=>({
    //     ...p,
    //     score: p.score ?? 0
    // }))
      .filter(p =>
        p.genreName === selectedGenre.value &&
        p.judgeName === (filteredJudge.value === "" ? null : filteredJudge.value)
      )
      .sort((a, b) => a.auditionNumber - b.auditionNumber)
  },
  set(updatedList) {
    // sync changes back into participants
    updatedList.forEach(updated => {
      const idx = participants.value.findIndex(p => p.auditionNumber === updated.auditionNumber)
      if (idx !== -1) {
        participants.value[idx] = { ...participants.value[idx], ...updated }
      }
    })
  }
})

const filteredParticipantsForEmcee = computed({
    get(){
        if (selectedGenre.value === "All" && filteredJudge.value === "") return transformForTable(participants.value);
        if (selectedGenre.value === "All") return transformForTable(participants.value.filter(p =>  p.judgeName === filteredJudge.value));
        if (filteredJudge.value === "") return transformForTable(participants.value.filter(p => p.genreName === selectedGenre.value));
        return transformForTable(participants.value.filter(p => p.genreName === selectedGenre.value && p.judgeName === filteredJudge.value));
    },
    set(updatedSubset){
        const byId = new Map(updatedSubset.map(r => [r.rowId, r]));
        participants.value = participants.value.map(org => {
            const updated = byId.get(org.rowId)
            return updated ? {...org, ...updated}: org
        })
    }
});

watch(selectedEvent, async (newVal) => {
  if (newVal) {
    const res = await getRegisteredParticipantsByEvent(newVal)
    participants.value = res.map((r,i)=>({
        ...r,
        rowId: r.rowId ?? i,
        score: 0
    }))
  }
});

const uniqueGenres = computed(() => {
    const genres = participants.value.map(p => p.genreName);
    return [...new Set(genres)].sort();
})

function transformForTable(data) {
  const judges = [...new Set(data.map(d => d.judgeName).filter(j => j !== null))];
  
  // If all judgeName are null → just return audition + participant
  if (judges.length === 0) {
    return {
      columns: [
        { key: 'auditionNumber', label: 'Audition', type: 'text', readonly: true },
        { key: 'participantName', label: 'Name', type: 'text', readonly: true }
      ],
      rows: data
        .sort((a, b) => a.auditionNumber - b.auditionNumber)
        .map(d => ({
          auditionNumber: d.auditionNumber,
          participantName: d.participantName
        }))
    };
  }

  // Otherwise → pivot by auditionNumber
  const auditions = {};
  data.forEach(d => {
    if (!auditions[d.auditionNumber]) {
      auditions[d.auditionNumber] = { auditionNumber: d.auditionNumber };
    }
    auditions[d.auditionNumber][d.judgeName] = d.participantName;
  });

  // Clean rows → drop if all judges undefined, else replace missing with ""
  const rows = Object.values(auditions)
    .map(row => {
      const allEmpty = judges.every(j => !row[j]);
      if (allEmpty) return null;
      judges.forEach(j => {
        if (!row[j]) row[j] = ""; // replace missing with ""
      });
      return row;
    })
    .filter(Boolean) // remove dropped rows
    .sort((a, b) => a.auditionNumber - b.auditionNumber);

  return {
    columns: [
      { key: 'auditionNumber', label: 'Audition', type: 'text', readonly: true },
      ...judges.map(j => ({ key: j, label: j, type: 'text', readonly: true }))
    ],
    rows
  };
}

const showFilters = ref(true)

onMounted(async () => {
    allEvents.value = await fetchAllEvents()
    selectedEvent.value = allEvents.value[0].folderName
    const res = await getAllJudges()
    allJudges.value =["", ...Object.values(res).map(item => item.judgeName)];
})
</script>

<!-- Choose whether you are emcee or judge -->
<!-- If emcee, should see all participants in one event, one genre 
        the header either like | no. | judge 1 | judge 2| or no. | name |
    If judge, need to extra select to who you are so that the score given can be tracked
-->

<template>
    <div class="max-w-5xl mx-auto mb-3">
    <div class="flex justify-end items-center mb-3">
      <ReusableButton @onClick="showFilters = !showFilters" :buttonName="showFilters ? 'Hide filter' : 'Show filter'"></ReusableButton>
    </div>

    <!-- Collapsible content -->
    <transition name="fade">
      <form v-if="showFilters" class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
        <ReusableDropdown v-model="selectedRole" labelId="Role" :options="roles" />
        <ReusableDropdown v-model="selectedEvent" labelId="Event" :options="allEvents.map(e => e.folderName)" />
        <ReusableDropdown v-model="selectedGenre" labelId="Genre" :options="uniqueGenres" />
        <ReusableDropdown v-model="filteredJudge" labelId="Judge" :options="allJudges" />
      </form>
    </transition>

    <!-- Optional extra dropdown (only for judges) -->
    <div class="flex justify-center mt-3" v-if="showFilters && selectedRole === 'Judge'">
      <div class="w-50">
        <ReusableDropdown v-model="currentJudge" labelId="Current Judge by:" :options="allJudges" />
      </div>
    </div>
  </div>

<div class="m-3" v-if="selectedRole==='Emcee' && filteredParticipantsForEmcee.rows.length > 0">
    <DynamicTable 
        v-model:tableValue="filteredParticipantsForEmcee.rows"
        :tableConfig="filteredParticipantsForEmcee.columns"></DynamicTable>
</div>
<div v-else-if="selectedRole==='Judge' && filteredParticipantsForEmcee.rows.length>0">
    <SwipeableCards v-model:cards="filteredParticipantsForJudge"></SwipeableCards>
</div>

<!-- </div> -->
</template>

<style>
/* simple fade animation */
.fade-enter-active, .fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>