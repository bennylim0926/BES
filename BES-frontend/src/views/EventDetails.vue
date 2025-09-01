<script setup>
import { MultiSelect } from 'primevue';
import { Button } from 'primevue';
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
 <div>This is details for {{ props.eventName }}</div>
 <table border="1" cellspacing="0" cellpadding="6">
        <thead>
        <tr>
            <th>Genre</th>
            <th>Number of participants</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(value, key) in participantsNumBreakdown" :key="key">
            <td>{{ key }}</td>
            <td>{{ value }}</td>
        </tr>
        </tbody>
    </table>
 <div v-if="tableExist">
    <p>The table exist: {{ tableExist }}</p>
    <Button
    @click="refreshParticipant">
        Refresh participants
    </Button>
    <div v-if="verifiedParticipants != null">
    <table border="1" cellspacing="0" cellpadding="6">
        <thead>
        <tr>
            <th>no.</th>
            <th>Name</th>
            <th>Genre(s) participated</th>
            <th>Residency</th>
        </tr>
        </thead>
        <tbody>
        <tr v-for="(row, i) in verifiedParticipants" :key="i">
            <td> {{ i+1 }}</td>
            <td>{{ row.name }}</td>
            <td>{{ row.genre }}</td>
            <td>{{ row.residency }}</td>
        </tr>
        </tbody>
    </table>
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