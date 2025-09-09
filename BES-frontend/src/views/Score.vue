<script setup>
import { ref, computed, onMounted, watch, readonly } from 'vue';
import { fetchAllEvents, getParticipantScore } from '@/utils/api';
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import DynamicTable from '@/components/DynamicTable.vue';
const selectedEvent = ref("")
const selectedGenre = ref("")
const selectedTabulation = ref("")
const allEvents = ref([])
const participants = ref([])
const tabulationMethod = ref(["By Total", "By Judge"])

const uniqueGenres = computed(() => {
    const genres = participants.value.map(p => p.genreName);
    return [...new Set(genres)].sort();
})

watch(selectedEvent, async (newVal) => {
  if (newVal) {
    const res = await getParticipantScore(newVal)
    participants.value = res.map((r,i)=>({
        ...r,
        rowId: r.rowId ?? i
    }))
  }
});

const filteredParticipantsForScore = computed({
    get(){
        return transformForScore(participants.value
            .filter(p => p.genreName === selectedGenre.value)
    )
    }
})

onMounted(async () => {
    allEvents.value = await fetchAllEvents()
    selectedEvent.value = allEvents.value[0].folderName
})

function transformForScore(data){
    const judges = [...new Set(data.map(d => d.judgeName).filter(j => j !== null))];
    const byTotal = {}
    if(selectedTabulation.value === 'By Total'){
        data.forEach(d => {
            if(!byTotal[d.participantName]){
                byTotal[d.participantName] = {participantName : d.participantName, totalScore:0}
            }
            byTotal[d.participantName][d.judgeName] = d.score
            byTotal[d.participantName].totalScore += d.score;
        });
        const rows = Object.values(byTotal).sort((a,b)=> b.totalScore - a.totalScore)
        return {
            columns :[
            { key: 'participantName', label: 'Participant', type: 'text', readonly: true },
            { key: 'totalScore', label: 'Tota Score', type: 'text', readonly: true },
        ...judges.map(j => ({ key: j, label: j, type: 'text', readonly: true }))
            ],
            rows
        }
    }else{
        const byJudge = {}
        data.forEach(d => {
            if(!byJudge[d.judgeName]){
                byJudge[d.judgeName] = {
                    columns: [
                        { key: 'participantName', label: 'Participant', type: 'text', readonly: true },
                        { key: 'score', label: 'Score', type: 'text', readonly: true },
                        ],
                    rows : []}
            }
            byJudge[d.judgeName].rows.push({
                participantName: d.participantName,
                score: d.score
            });
        });
        Object.values(byJudge).forEach(group => {
            group.rows.sort((a, b) => b.score - a.score)
        })
        return {
            byJudge
        }
    }
}
</script>


<template>
    <div class="flex justify-center items-center mb-3">
    <form class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-3 gap-10">
        <ReusableDropdown v-model="selectedEvent" labelId="Event" :options="allEvents.map(e => e.folderName)" />
        <ReusableDropdown v-model="selectedGenre" labelId="Genre" :options="uniqueGenres" />
        <ReusableDropdown v-model="selectedTabulation" labelId="Group By" :options="tabulationMethod" />
      </form>
      
    </div>

    <DynamicTable 
        v-if="selectedTabulation == 'By Total' && filteredParticipantsForScore.rows.length>0"
        v-model:tableValue="filteredParticipantsForScore.rows"
        :tableConfig="filteredParticipantsForScore.columns">
    </DynamicTable>
     <div v-if="selectedTabulation == 'By Judge'">
    <div v-for="(group, judge) in filteredParticipantsForScore.byJudge" :key="judge" class="mb-8">
        <h2 class="text-lg font-bold mb-2">{{ judge }}</h2>
        <DynamicTable 
        v-model:tableValue="group.rows"
        :tableConfig="group.columns">
        </DynamicTable>
    </div>
</div>
</template>