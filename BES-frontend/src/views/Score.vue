<script setup>
import { ref, computed, onMounted, watch, readonly } from 'vue';
import { fetchAllEvents, getParticipantScore } from '@/utils/api';
import ReusableDropdown from '@/components/ReusableDropdown.vue';
import DynamicTable from '@/components/DynamicTable.vue';
import { checkAuthStatus } from '@/utils/auth';
import UpdateScoreForm from '@/components/UpdateScoreForm.vue';
const selectedEvent = ref(localStorage.getItem("selectedEvent") || "")
const selectedGenre = ref(localStorage.getItem("selectedGenre") || "All")
const selectedTabulation = ref(localStorage.getItem("selectedTabMethod") || "")
const allEvents = ref([])
const participants = ref([])
const tabulationMethod = ref(["By Total", "By Judge"])
const selectedParticipant = ref("")
const showSubmitScore = ref(false)

const editScore = (name) =>{
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
    participants.value = res.map((r,i)=>({
        ...r,
        id: i+1
    }))
  }
}, {immediate: true});

watch(selectedGenre, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedGenre", newVal);
  }
},{immediate: true});

watch(selectedTabulation, async (newVal) => {
  if (newVal) {
    localStorage.setItem("selectedTabMethod", newVal);
  }
},{immediate: true});


const filteredParticipantsForScore = computed({
    get(){
        return transformForScore(participants.value
            .filter(p => p.genreName === selectedGenre.value)
    )
    }
})

const fetchEventsAndInit = async()=>{
    allEvents.value = await fetchAllEvents()
    const savedEvent = localStorage.getItem("selectedEvent")
    selectedEvent.value = savedEvent || (allEvents.value[0]?.folderName || "")
}

onMounted(async () => {
    const ok = await checkAuthStatus(["admin","emcee","organiser"])
    if(!ok) return
    await fetchEventsAndInit()
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
        const rows = Object.values(byTotal)
                    .map(r => ({
                        ...r,
                        totalScore: Number(r.totalScore.toFixed(1))
                    }))
                    .sort((a,b)=> b.totalScore - a.totalScore)
                    .map((r,i)=>({
                        ...r,
                        id: i+1
                    }))
        return {
            columns :[
            { key: 'id', label: 'Top', type: 'text', readonly:true},
            { key: 'participantName', label: 'Participant', type: 'link'},
            { key: 'totalScore', label: 'Total Score', type: 'text', readonly: true },
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
                        { key: 'id', label: 'Top', type: 'text', readonly:true},
                        { key: 'participantName', label: 'Participant', type: 'link'},
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
            group.rows = group.rows
                .sort((a, b) => b.score - a.score)
                .map((r,i)=>({
                        ...r,
                        id: i+1
                    }))
        })
        return {
            byJudge
        }
    }
}
</script>


<template>
    <div class="m-10">
    <form class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-3 gap-5">
        <ReusableDropdown v-model="selectedEvent" labelId="Event" :options="allEvents.map(e => e.folderName)" />
        <ReusableDropdown v-model="selectedGenre" labelId="Genre" :options="uniqueGenres" />
        <ReusableDropdown v-model="selectedTabulation" labelId="Group By" :options="tabulationMethod" />
      </form>

      <div class="m-10">
    <DynamicTable 
        v-if="selectedTabulation == 'By Total' && filteredParticipantsForScore.rows.length>0"
        @onClick="editScore"
        v-model:tableValue="filteredParticipantsForScore.rows"
        :tableConfig="filteredParticipantsForScore.columns">
    </DynamicTable>
     <div v-if="selectedTabulation == 'By Judge'">
    <div v-for="(group, judge) in filteredParticipantsForScore.byJudge" :key="judge" class="mb-8">
        <h2 class="text-lg font-bold mb-2">{{ judge }}</h2>
        <DynamicTable 
        @onClick="editScore"
        v-model:tableValue="group.rows"
        :tableConfig="group.columns">
        </DynamicTable>
    </div>
</div>
</div>
</div>
<UpdateScoreForm
    :event="selectedEvent"
    :show="showSubmitScore" 
    title="Update Score"
    :genre="selectedGenre"
    :name="selectedParticipant"
    @updateScore="()=>{showSubmitScore = !showSubmitScore}"
    @close="()=>{showSubmitScore = !showSubmitScore}"
    ></UpdateScoreForm>
</template>