<script setup>
import {ref, onMounted, reactive, readonly} from 'vue';
import { useRoute } from 'vue-router';
import ActionDoneModal from './ActionDoneModal.vue';
import DynamicInputs from '@/components/DynamicInputs.vue';
import DynamicTable from '@/components/DynamicTable.vue';

const modalTitle = ref("")
const modalMessage = ref("")

const headers = ["Genre", "NO"]

const inputs = ref([""]) // start with one textbox

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

// const route = useRoute();

const props = defineProps({
    eventName: String,
    folderID: String,
})

const tableExist = ref(true)
const fileId = ref('')
const eventName = ref(props.eventName.split(" ").join("%20"));
const verifiedParticipants = ref([])
const participantsNumBreakdown = ref([])

const createTable = reactive({
    genres: []
})

const showModal = ref(false)

const handleAccept = () => {
  showModal.value = false
}

const genreOptions = ref(null)
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
    await fetch("http://localhost:5050/api/v1/event/judges", {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            judges: inputs.value,
        })
    })
    await fetch("http://localhost:5050/api/v1/sheets/payment-status", {
        method: 'POST',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            fileId: fileId.value,
        })
    })
    
    // create table with eventName
    await fetch("http://localhost:5050/api/v1/event", {
        method: 'POST',
        headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            eventName: props.eventName,
        })
    })

    // link table to selected genres
    const addGenreResponse = await fetch("http://localhost:5050/api/v1/event/genre", {
        method: 'POST',
        headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            eventName: props.eventName,
            genreName: createTable.genres
        })
    })
    addGenreResponse.json().then(result =>{
        openModal(addGenreResponse.status , result)
        if(addGenreResponse.ok){
            tableExist.value = true
        }
    })
}

const refreshParticipant = async() =>{
    const createEventResponse = await fetch("http://localhost:5050/api/v1/event/participants/", {
        method: 'POST',
        headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            fileId: fileId.value,
            eventName: props.eventName,
        })
    })
    if(createEventResponse.ok){
        getParticipantsByEvent()
        createEventResponse.json().then(result=>[
            openModal(createEventResponse.status , result)
        ])
    }else if(createEventResponse.status == 404){
        createEventResponse.json().then(result=>[
            openModal(createEventResponse.status , result)
        ])
    }
}

const checkTableExist = async()=>{
    try{
        const res = await fetch(`http://localhost:5050/api/v1/event/${eventName.value}`)
        res.json().then(result =>{
            tableExist.value = result
        })
    }catch(e){
        console.log(e)
    }
}

const getFileId = async ()=>{
    try{
        const res = await fetch(`http://localhost:5050/api/v1/files/${props.folderID}`)
        res.json().then(result =>{
            fileId.value = result[0].fileId
            getResponseDetails(fileId.value)
        })
    }catch(e){
        console.log(e)
    }
}

const getAllGenres = async()=>{
    try{
        const res = await fetch(`http://localhost:5050/api/v1/event/genre`)
        res.json().then(result =>{
            genreOptions.value = result
        })
    }catch(e){
        console.log(e)
    }
}

const getParticipantsByEvent = async() =>{
    try{
        const res = await fetch(`http://localhost:5050/api/v1/event/verified-participant/${eventName.value}`)
        if(res.ok){
            res.json().then(result =>{
            verifiedParticipants.value = result
            })
        }else if (res.status === 404) {
            console.log("404: event not exist");
        }
    }catch(e){
        console.log(e)
    }
}

const getResponseDetails = async(fileId) =>{
    try{
        const res = await fetch(`http://localhost:5050/api/v1/sheets/participants/breakdown/${fileId}`)
        if (!res.ok) throw new Error('Failed to read')
        res.json().then(result =>{
            participantsNumBreakdown.value = result}) 
    }catch(err){
    }
}

onMounted(()=>{
    checkTableExist()
    getFileId()
    getAllGenres()
    getParticipantsByEvent()
})
</script>

<template>
<!-- 
    if no database, give an option to create and with genre
        to check database exist, 
        to create event with genre, need eventName and genreName
    once created, show verified and unverified participants
 -->
    <h1 class="flex justify-center gap-2 text-4xl font-extrabold leading-none tracking-tight text-gray-900 md:text-5xl lg:text-6xl dark:text-white mb-3">{{ props.eventName }}</h1>
    <DynamicTable
        v-model:tableValue="participantsNumBreakdown"
        :table-config="[
            { key: 'key', label: 'Genre', type: 'text', readonly: true },
            { key: 'value', label: 'Count', type: 'number', readonly:true }
        ]"
        />
    <div v-if="tableExist">
        <div class="flex justify-center">
            <button class="row-span-2 bg-transparent hover:bg-gray-500 text-gray-400 font-semibold hover:text-white py-2 px-4 border border-gray-500 hover:border-transparent rounded mb-3"
            @click="refreshParticipant">
                Refresh participants
            </button>
        </div>
        <DynamicTable
        v-if="verifiedParticipants.length > 0 && tableExist" 
        v-model:tableValue="verifiedParticipants"
        :table-config="[
            { key: 'name', label: 'Name', type: 'text', readonly: true },
            { key: 'genre', label: 'Genre', type: 'text', readonly:true },
            { key: 'residency', label: 'Residency', type: 'text', readonly:true }
        ]"
        />
        <div v-else class="flex justify-center"> Please check your response form and mark it if the participant paid</div>
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
    >
        <p>
        {{ modalMessage}}
        </p>
    </ActionDoneModal>
</template>