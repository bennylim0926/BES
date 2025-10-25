<script setup>
import {ref, onMounted, reactive, watch, computed} from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import DynamicInputs from '@/components/DynamicInputs.vue';
import DynamicTable from '@/components/DynamicTable.vue';
import { checkTableExist, getFileId, getResponseDetails, fetchAllGenres, getVerifiedParticipantsByEvent, addJudges, insertPaymenColumnInSheet, insertEventInTable, linkGenreToEvent, addParticipantToSystem, getSheetSize, getRegisteredParticipantsByEvent} from '@/utils/api';
import ReusableButton from '@/components/ReusableButton.vue';
import { filterObject } from '@/utils/utils';

const fileId = ref('')
const modalTitle = ref("")
const modalMessage = ref("")
const inputs = ref([""]) 
const genreOptions = ref(null)
const tableExist = ref(true)

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

const openCategoryDetails = (genre) =>{
    const res = getUnregistered(genre)
    showModal.value = true
    if(res.unregistered.length === 0){
        modalTitle.value = "All participants registered"
        modalMessage.value = ""
    }
    else{
        modalTitle.value = "Unregistered participants"
        modalMessage.value = res.unregistered.map(p => p.participantName).join(", ")
    }
}

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
    return filterObject(participantsNumBreakdown.value, value => value>0)
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

const sumBreakdown = computed(()=>{
    return [verifiedFormParticipants.value.length ,totalParticipants.value]
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
        if(item.walkin) continue
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

    const result = Object.entries(filteredBreakdown.value).map(([genre, total]) => {
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
    // insert payment-status column if not exist
    if(createTable.genres.length == 0){
        openModal(404 , "Need to add at least one genre/category")
        return
    }
    for (let index = 0; index < inputs.value.length; index++) {
        if(inputs.value[index] === ""){
            console.log("cannot have empty value")
            openModal(404 , "Cannot have empty value in Judges")
            return
        }
    }
    await addJudges(inputs.value)
    await insertPaymenColumnInSheet(fileId.value)
    
    // create table with eventName
    await insertEventInTable(props.eventName)

    // link table to selected genres
    const resp = await linkGenreToEvent(props.eventName, createTable.genres)
    resp.json().then(result=>{
        openModal(resp.status , result)
        tableExist.value = true
    })
    
}

const refreshParticipant = async() =>{
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
}

const tableConfig = computed(()=>{
    const base = [
        { key: 'name', label: 'Name', type: 'text', readonly: true },
        { key: 'genre', label: 'Genre', type: 'text', readonly: true }
    ]
    const hasResidency = verifiedFormParticipants.value.some(p => p.residency)
    if(hasResidency){
        base.push({ key: 'residency', label: 'Residency', type: 'text', readonly: true })
    }
    return base
})

watch(
    fileId,
    async () =>{
        participantsNumBreakdown.value = await getResponseDetails(fileId.value)
        totalParticipants.value = await getSheetSize(fileId.value)
    }
)

onMounted( async () =>{
    tableExist.value = checkTableExist(eventName, tableExist)
    fileId.value = await getFileId(props.folderID)
    genreOptions.value = await fetchAllGenres()
    verifiedFormParticipants.value = await getVerifiedParticipantsByEvent(eventName.value)
    verifiedDbParticipants.value = await getRegisteredParticipantsByEvent(eventName.value)
})
</script>

<template>
<!-- 
    if no database, give an option to create and with genre
        to check database exist, 
        to create event with genre, need eventName and genreName
    once created, show verified and unverified participants
 -->
    <h1 class="flex justify-center gap-2 text-2xl font-extrabold leading-none tracking-tight text-gray-900 md:text-3xl lg:text-4xl dark:text-white mb-3">{{ props.eventName }}</h1>
    <div class="mx-10">
        <h1 class="flex justify-center gap-2 text-xl font-extrabold leading-none tracking-tight text-gray-900 md:text-xl lg:text-xl dark:text-white mb-3">
            <!-- Email Received: {{ sumBreakdown[0] }}/{{ sumBreakdown[1] }} -->
            Total Participants: {{ totalParticipants + totalWalkIn}}
            <br></br>
            Total Form Sign Up: {{ totalParticipants }}
            <br></br>
            Total Walk In: {{ totalWalkIn }}
            <br></br>
            Total Form Sign Up (Verified): {{ verifiedFormParticipants.length - totalWalkIn}}
            <br></br>
            Total Registered (With Audition Number): {{ totalDbRegistered.length }}
            <br></br>
        </h1>
    <DynamicTable
        @onClick="openCategoryDetails"
        v-model:tableValue="completeBreakdown"
        :table-config="[
            { key: 'genre', label: 'Genre', type: 'link'},
            { key: 'total', label: 'Total Form Sign Up', type: 'number', readonly:true },
            { key: 'unregistered', label: 'Unregistered', type: 'number', readonly:true },
            { key: 'registered', label: 'Registered', type: 'number', readonly:true }
        ]"
        />
    </div>
    <div v-if="tableExist">
        <div class="flex justify-center">
            <ReusableButton @onClick="refreshParticipant" buttonName="Refresh"></ReusableButton>
        </div>
        <!-- <div class="mx-10">
        <DynamicTable
        v-if="verifiedFormParticipants.length > 0 && tableExist" 
        v-model:tableValue="verifiedFormParticipants"
        :table-config="tableConfig"
        />
        <div v-else class="flex justify-center"> Please check your response form and mark it if the participant paid</div>
    </div> -->
    </div>
    <div v-else class="flex items-center gap-2">
        <form class="mx-auto relative overflow-x-auto " @submit.prevent="onSubmit">        
            <label class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">
                <h1 class="flex justify-center gap-2 text-2xl font-extrabold leading-none tracking-tight text-gray-900 md:text-5xl lg:text-2xl dark:text-white mb-3">
                Genres</h1>
            </label>

            <div class="grid grid-cols-2 gap-3 w-fit mb-2">
                <div
                v-for="g in genreOptions"
                :key="g.genreName"
                class="flex items-center px-3 py-2 border border-gray-200 rounded-md 
                        dark:border-gray-700 bg-white dark:bg-gray-800 shadow-sm"
                >
                    <input
                        type="checkbox"
                        :id="g.genreName"
                        :value="g.genreName"
                        v-model="createTable.genres"
                        class="w-4 h-4 text-blue-600 bg-gray-100 border-gray-300 rounded-sm
                            focus:ring-blue-500 dark:focus:ring-blue-600 dark:ring-offset-gray-800
                            focus:ring-2 dark:bg-gray-700 dark:border-gray-600"
                    />
                    <label
                        :for="g.genreName"
                        class="ms-2 text-sm font-medium text-gray-900 dark:text-gray-300"
                    >
                        {{ g.genreName }}
                    </label>
                </div>
            </div>
            <DynamicInputs  v-model="inputs"></DynamicInputs>
            <div class="flex justify-center">
                <button class="bg-transparent hover:bg-gray-500 text-gray-400 font-semibold hover:text-white py-2 px-4 border border-gray-500 hover:border-transparent rounded mb-3" type="submit" value="Submit">
                            Create Table
                </button>
            </div>
        </form>
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
</template>