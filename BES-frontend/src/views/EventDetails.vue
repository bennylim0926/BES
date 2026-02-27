<script setup>
import {ref, onMounted, reactive, watch, computed} from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import DynamicInputs from '@/components/DynamicInputs.vue';
import DynamicTable from '@/components/DynamicTable.vue';
import { checkTableExist, getFileId, getResponseDetails, fetchAllGenres, getVerifiedParticipantsByEvent, addJudges, insertPaymenColumnInSheet, insertEventInTable, linkGenreToEvent, addParticipantToSystem, getSheetSize, getRegisteredParticipantsByEvent} from '@/utils/api';
import { filterObject, useDelay } from '@/utils/utils';
import ReusableButton from '@/components/ReusableButton.vue';
import AuditionNumber from './AuditionNumber.vue';
import LoadingOverlay from '@/components/LoadingOverlay.vue';

const fileId = ref('')
const modalTitle = ref("")
const modalMessage = ref("")
const inputs = ref([""]) 
const genreOptions = ref(null)
const tableExist = ref(true)
const loading = ref(false)
const onStartLoading = ref(false)

const verifiedFormParticipants = ref([])
const verifiedDbParticipants = ref([])
const participantsNumBreakdown = ref([])
const totalParticipants = ref(0)
const props = defineProps({
    eventName: String,
    folderID: String,
})
const eventName = ref(props.eventName.split(" ").join("%20"));

const createTable = reactive({
    genres: []
})

const showModal = ref(false)
const handleAccept = () => {
  showModal.value = false
}

const openModal = (title, message) => {
    getTitle(title)
    modalMessage.value = message
    showModal.value = true
}

const getTitle = (statusCode) =>{
    if(statusCode >= 200 && statusCode <= 299){
        modalTitle.value = "Success"
    }else{
        modalTitle.value = "Failed"
    }
}

const filteredBreakdown = computed(()=>{
    console.log(participantsNumBreakdown.value)
    return filterObject(participantsNumBreakdown.value, value => value>0)
})

const genreCounts = computed(() => {
  const counts = {}
  verifiedDbParticipants.value.forEach(p => {
    // normalize genre names
    let key = p.genreName.toLowerCase()
    if (key.includes('smoke')) key = 'smoke'
    else key = key.replace(/\s+/g, '') // remove spaces (hip hop → hiphop)

    counts[key] = (counts[key] || 0) + 1
  })
  return counts
})

const totalWalkIn = computed(()=>{
    const uniqueParticipants = [
    ...new Map(
      verifiedDbParticipants.value.map(p => [p.participantName, p])
    ).values()
    ]
    return uniqueParticipants.filter(p => p.walkin == true).length
})

const totalDbRegistered = computed(()=>{
    const uniqueParticipants = [
    ...new Map(
      verifiedDbParticipants.value.map(p => [p.participantName, p])
    ).values()
  ]
  // Step 2: Filter by those who have an auditionNumber
  return uniqueParticipants.filter(p => p.auditionNumber !== null)
})

function normalizeGenreName(name) {
    const normalized = name.trim().toLowerCase().replace(/\s+/g, '');
    if (normalized.includes('7tosmoke')) return 'smoke';
    return normalized
  
}

const getUnregistered = (genre) =>{
    // const g = normalizeGenreName(genre)
    const participants = 
        verifiedDbParticipants.value
        .map(
            p => ({
                ...p,
                genreName: normalizeGenreName(p.genreName)
            })
        )
    return {
        "registered": participants
                        .filter(p => p.genreName === genre && p.auditionNumber !== null && p.walkin === false)
                        .sort((a,b)=> a.auditionNumber - b.auditionNumber),
        "unregistered": participants.filter(p => p.genreName === genre && p.auditionNumber === null && p.walkin === false)
    }
}

const completeBreakdown = computed(()=>{
    const genreStats = {};

    for (const item of verifiedDbParticipants.value) {
        // if(item.walkin) continue
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

    const result = Object.entries(genreCounts.value).map(([genre, total]) => {
        const stats = genreStats[genre] || { registered: 0, unregistered: 0 };
        return {
            genre,
            total,
            registered: stats.registered,
            unregistered: stats.unregistered
        };
    });
    return result
})

const onSubmit = async () =>{
    if(loading.value){
        return
    }
    // insert payment-status column if not exist
    if(createTable.genres.length == 0){
        openModal(404 , "Need to add at least one genre/category")
        return
    }
    loading.value = true
    // for (let index = 0; index < inputs.value.length; index++) {
    //     if(inputs.value[index] === ""){
    //         console.log("cannot have empty value")
    //         openModal(404 , "Cannot have empty value in Judges")
    //         return
    //     }
    // }
    await addJudges(inputs.value)
    await insertPaymenColumnInSheet(fileId.value)
    // create table with eventName
    await insertEventInTable(props.eventName)

    // link table to selected genres
    const resp = await linkGenreToEvent(props.eventName, createTable.genres)
    resp.json().then(result=>{
        loading.value = false
        openModal(resp.status , result)
        tableExist.value = true
    })
    
}

const refreshParticipant = async() =>{
    loading.value = true
    const createEventResponse = await addParticipantToSystem(fileId.value, props.eventName)
    if(createEventResponse.ok){
        verifiedFormParticipants.value = await getVerifiedParticipantsByEvent(eventName.value)
        createEventResponse.json().then(result=>[
            openModal(createEventResponse.status , result)
        ])
    }else if(createEventResponse.status == 404){
        createEventResponse.json().then(result=>[
            openModal(createEventResponse.status , result)
        ])
    }
    loading.value = false
}

watch(
    fileId,
    async () =>{
        if(fileId.value !== null){
            participantsNumBreakdown.value = await getResponseDetails(fileId.value)
            totalParticipants.value = await getSheetSize(fileId.value)
        }
    }
)

onMounted( async () =>{
    onStartLoading.value =true
    tableExist.value = checkTableExist(eventName, tableExist)
    fileId.value = await getFileId(props.folderID)
    genreOptions.value = await fetchAllGenres()
    if(tableExist.value) {
        verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
        verifiedFormParticipants.value = await getVerifiedParticipantsByEvent(eventName.value)
    }
    await useDelay().wait(2500)
    onStartLoading.value =false
})
</script>

<template>
    
    <h1
  class="flex flex-wrap text-2xl md:text-6xl lg:text-6xl items-center justify-center leading-none tracking-tight  mt-4 mx-3 mb-2"
>
  <span class="font-semibold text-black">{{ props.eventName }} </span>

  <svg
    v-if="loading"
    class="w-10 h-10 text-gray-800 animate-spin shrink-0 mx-2"
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
  >
    <path
      stroke="currentColor"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      d="M12 3v3m0 12v3m9-9h-3M6 12H3m15.364 6.364l-2.121-2.121M6.757 6.757L4.636 4.636m12.728 0l-2.121 2.121M6.757 17.243l-2.121 2.121"
    />
  </svg>

  <svg
    v-else
    class="w-10 h-10 mx-2 text-gray-800 shrink-0"
    xmlns="http://www.w3.org/2000/svg"
    fill="none"
    viewBox="0 0 24 24"
    @click="refreshParticipant"
  >
  <path stroke="currentColor" 
        stroke-linecap="round" 
        stroke-linejoin="round" 
        stroke-width="2" 
        d="M17.651 7.65a7.131 7.131 0 0 0-12.68 3.15M18.001 4v4h-4m-7.652 8.35a7.13 7.13 0 0 0 12.68-3.15M6 20v-4h4"/>
  </svg>
</h1>
    <div class="flex justify-center m-5">
        <AuditionNumber></AuditionNumber>
    </div>
    <div class="grid grid-cols-1 sm:grid-cols-1 md:grid-cols-3 lg:grid-cols-3 xl:grid-cols-3 2xl:grid-cols-3 gap-2 px-2 justify-items-center mb-5 mx-4">
        <div class=" bg-[#fffaf5] shadow-lg w-full h-auto p-4 rounded items-center justify-center "> 
            <div class="text-2xl font-semibold">Total: {{ totalParticipants + totalWalkIn }}</div> 
            <div class="flex">
            <div class="text-lg grid grid-cols-2 gap-2 justify-center items-center">
                <div> Form: {{ totalParticipants }}</div>
                <div>Walkin: {{ totalWalkIn }}</div>
            </div>
        </div>
        </div>
        <!-- {{ verifiedFormParticipants }} -->
        <div class=" bg-[#fffaf5] shadow-lg w-full h-auto p-4 rounded"> 
            <div class="text-2xl font-semibold">Verified: {{ verifiedFormParticipants.length - totalWalkIn }}</div> 
            <div class="text-2xl font-semibold">Unverified: {{ totalParticipants - (verifiedFormParticipants.length -totalWalkIn) }}</div> 
        </div>
        <div class=" bg-[#fffaf5] shadow-lg w-full h-auto p-4 rounded"> 
            <div class="text-2xl font-semibold">Registered: {{ totalDbRegistered.length }}</div> 
            <!-- <div class="text-2xl font-semibold">Unregistered: {{ totalDbRegistered.length }}</div>  -->
        </div>
    </div>
    <div class="m-8">
        <div class=" grid grid-cols-1 sm:grid-cols-1 md:grid-cols-2 lg:grid-cols-2 gap-2">
            <div v-for="genre in completeBreakdown"
            class="flip-card">
            <!-- @click="openCategoryDetails(genre.genre)"> -->
            <div class="flip-card-inner">
                <div class="flip-card-front grid grid-cols-1">
                    <p class=" text-3xl font-semibold">{{ genre.genre }}</p>
                    <hr class="h-px my-4  bg-gray-700"></hr>
                    <p class="text-lg">Total: {{ genre.total }}</p>
                    <p class="text-lg">Registered: {{ genre.registered }}</p>
                    <p class="text-lg">Unregistered: {{ genre.unregistered }}</p>
                </div>
                <div class="flip-card-back">
                    <div v-if="getUnregistered(genre.genre).unregistered.length > 0">
                        <p class=" text-2xl font-semibold">Unregistered</p>
                        <p>
                            {{
                                getUnregistered(genre.genre).unregistered
                                .map(v => v.participantName)
                                .join(', ')
                            }}
                        </p>
                    </div>
                    <div v-else><p class=" text-2xl font-semibold">All verified participants had registered</p></div>
                </div>
            </div>
            </div>
        </div>
    </div>
    <div v-if="tableExist">
    </div>
    <div v-else class="flex items-center gap-2 bg-[#fffaf5] p-5 m-6 shadow-xl">
        <div class="mx-auto relative overflow-x-auto">        
            <label class="block mb-2 text-sm text-gray-900">
                <h1 class="flex justify-center gap-2 text-xl leading-none tracking-tight text-gray-900 md:text-5xl lg:text-2xl mb-3">
                Genres/Categories</h1>
            </label>

            <div class="grid grid-cols-2 gap-3 w-fit mb-2">
                <div
                v-for="g in genreOptions"
                :key="g.genreName"
                class="flex items-center px-3 py-2 sm:min-w-48 md:min-w-62 lg:min-w-62 h-auto rounded bg-white shadow-md">
                    <input
                        type="checkbox"
                        :id="g.genreName"
                        :value="g.genreName"
                        v-model="createTable.genres"
                        class="w-4 h-4 text-orange-400 bg-gray-100 border-gray-300 rounded-sm
                            focus:ring-orange-400
                            focus:ring-2"
                    />
                    <label
                        :for="g.genreName"
                        class="ms-2 sm:text-lg md:text-xl lg:text-xl font-medium text-black"
                    >
                        {{ g.genreName }}
                    </label>
                </div>
            </div>
            <!-- <DynamicInputs  v-model="inputs"></DynamicInputs> -->
            <div class="flex justify-center">
                <ReusableButton @onClick="onSubmit" buttonName="Update database"></ReusableButton>
            </div>
        </div>
    </div>
    <ActionDoneModal
        :show="showModal"
        :title="modalTitle"
        @accept="handleAccept"
        @close="()=>{showModal = false}"
    >
        <p>
        {{ modalMessage}}
        </p>
    </ActionDoneModal>
    <LoadingOverlay v-if="onStartLoading"></LoadingOverlay>
</template>

<style>

.flip-card {
  height: 180px; /* pick a consistent height */
}

.flip-card-inner {
  position: relative;
  width: 100%;
  height: 100%; /* or a fixed height like 300px */

  transition: transform 0.8s;
  transform-style: preserve-3d;
}

/* Do an horizontal flip when you move the mouse over the flip box container */
.flip-card:hover .flip-card-inner {
  transform: rotateY(180deg);
}

.flip-card-front,
.flip-card-back {
    box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.2),
    0 8px 10px -6px rgba(0, 0, 0, 0.2);
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  padding: 10px;
  backface-visibility: hidden;
}

.flip-card-front {
  background-color: #fffaf5;
  border-radius: 4px;
}

.flip-card-back {
  background-color: #ff8904; /* ✅ keep background visible after flip */
  color: white;
  transform: rotateY(180deg);
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: flex-start;
  padding: 1rem;
  border-radius: 4px;
}
</style>