<script setup>
import { MultiSelect } from 'primevue';
import { Button } from 'primevue';
import {ref, onMounted, reactive} from 'vue';
import { useRoute } from 'vue-router';

const tableExist = ref(true)
const fileId = ref('')

const createTable = reactive({
    genres: []
})

const genreOptions = [
    {genre: "popping"},
    {genre: "waacking"},
    {genre: "open"},
    {genre: "rookie"}
]

const props = defineProps({
    eventName: String,
    folderID: String,
})

const onSubmit = async () =>{
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
    
    const createEventResponse = await fetch("http://localhost:5050/api/v1/event", {
        method: 'POST',
        headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            eventName: props.eventName,
        })
    })
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
    console.log(props.eventName)
    console.log(route.query.folderID)
    
    
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
    createEventResponse.json().then(result =>{
        console.log(result)
    })
}

const route = useRoute();
const eventName = ref(props.eventName.split(" ").join("%20"));
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
        })
    }catch(e){
        console.log(e)
    }
}

onMounted(()=>{
    checkTableExist()
    getFileId()
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
 <div v-if="tableExist">
    <p>The table exist: {{ tableExist }}</p>
    <p>Show button to add partcipant to database to make it verified</p>
    <Button
    @click="refreshParticipant">
    Refresh participants
    </Button>
    

</div>
 <div v-else>
    <p>The table does not exist</p>
    <form @submit.prevent="onSubmit">
        <h4>Create database</h4>
        <input type="text" disabled :value="route.params.eventName"></input>
        <input class="button" type="submit" value="Submit"></input>
        <!-- <select multiple="multiple">
            <option v-for="genre in genreOptions">{{genre.genre}}</option>
        </select> -->
        <MultiSelect v-model="createTable.genres" :options="genreOptions"
        optionLabel="genre" 
        optionValue="genre">
        </MultiSelect>
    </form>
</div>
</template>