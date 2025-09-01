<script setup>
import {ref, onMounted, reactive} from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();

const props = defineProps({
    eventName: String,
    folderID: String,
})

const tableExist = ref(true)
const fileId = ref('')
const eventName = ref(props.eventName.split(" ").join("%20"));
const verifiedParticipants = ref(null)
const participantsNumBreakdown = ref(null)

const createTable = reactive({
    genres: []
})

const genreOptions = ref(null)
const onSubmit = async () =>{
    // insert payment-status column if not exist
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
    if(addGenreResponse.ok){
        tableExist.value = true
    }
}

const refreshParticipant = async() =>{
    const createEventResponse = await fetch("http://localhost:5050/api/v1/sheets/participants/", {
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
        res.json().then(result =>{
            verifiedParticipants.value = result
        })
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
 <h1 class="text-4xl font-extrabold leading-none tracking-tight text-gray-900 md:text-5xl lg:text-6xl dark:text-white mb-3">{{ props.eventName }}</h1>
 <div class="relative overflow-x-auto shadow-md sm:rounded-lg mb-3">
    <table class="text-sm text-left rtl:text-right text-gray-500 dark:text-gray-400 rounded-lg overflow-hidden">
        <thead class="text-xs text-gray-700 uppercase bg-gray-50 dark:bg-gray-700 dark:text-gray-400">
        <tr>
            <th scope="col" class="px-6 py-3">Genre</th>
            <th scope="col" class="px-6 py-3">Number of participants</th>
        </tr>
        </thead>
        <tbody>
        <tr class="bg-white border-b dark:bg-gray-800 dark:border-gray-700 border-gray-200" v-for="(value, key) in participantsNumBreakdown" :key="key">
            <td class="px-6 py-2">{{ key }}</td>
            <td class="px-6 py-2 text-center">{{ value }}</td>
        </tr>
        </tbody>
    </table>
</div>
 <div v-if="tableExist">
    <button class="bg-transparent hover:bg-gray-500 text-gray-400 font-semibold hover:text-white py-2 px-4 border border-gray-500 hover:border-transparent rounded mb-3"
    @click="refreshParticipant">
        Refresh participants
    </button>
    <div v-if="verifiedParticipants != null">
        <div class="relative overflow-x-auto shadow-md">
        <table class="text-sm text-left rtl:text-right text-gray-500 dark:text-gray-400 rounded-lg overflow-hidden">
            <thead class="text-xs text-gray-700 uppercase bg-gray-50 dark:bg-gray-700 dark:text-gray-400">
            <tr>
                <th scope="col" class="px-6 py-3"> no.</th>
                <th scope="col" class="px-6 py-3"> Name</th>
                <th scope="col" class="px-6 py-3"> Genre(s) participated</th>
                <th scope="col" class="px-6 py-3"> Residency</th>
            </tr>
            </thead>
            <tbody>
            <tr class="bg-white border-b dark:bg-gray-800 dark:border-gray-700 border-gray-200" v-for="(row, i) in verifiedParticipants" :key="i">
                <td class="px-6 py-2"> {{ i+1 }}</td>
                <td class="px-6 py-2">{{ row.name }}</td>
                <td class="px-6 py-2">{{ row.genre }}</td>
                <td class="px-6 py-2">{{ row.residency }}</td>
            </tr>
            </tbody>
        </table>
    </div>
    </div>
    <div v-else> Please check your response form and mark it if the participant paid</div>

</div>
 <div v-else>
    <p>The table does not exist</p>
    <form @submit.prevent="onSubmit">
        <h4>Create database</h4>
        <input type="text" disabled :value="route.params.eventName"></input>
        <input class="button" type="submit" value="Submit"></input>
        <MultiSelect v-model="createTable.genres" :options="genreOptions"
        optionLabel="genreName" 
        optionValue="genreName">
        </MultiSelect>
    </form>
</div>
</template>