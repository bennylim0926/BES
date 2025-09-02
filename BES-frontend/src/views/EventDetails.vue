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
 <div class="relative overflow-x-auto sm:rounded-lg mb-3">
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
    <form class="mx-auto relative overflow-x-auto" @submit.prevent="onSubmit">
        <!-- <div>
            <label for="small-input" class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Event Name</label>
            <input type="text" disabled :value="route.params.eventName" class="block w-full p-2 text-gray-900 border border-gray-300 rounded-lg bg-gray-50 text-xs focus:ring-blue-500 focus:border-blue-500 dark:bg-gray-700 dark:border-gray-600 dark:placeholder-gray-400 dark:text-white dark:focus:ring-blue-500 dark:focus:border-blue-500"></input>
        </div> -->
        
        
        <label class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">
    Select genres
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
  <p class="mt-2 text-sm mb-2">Selected: {{ createTable.genres.join(', ') }}</p>
  <button class="bg-transparent hover:bg-gray-500 text-gray-400 font-semibold hover:text-white py-2 px-4 border border-gray-500 hover:border-transparent rounded mb-3" type="submit" value="Submit">
            Create Table
        </button>
    </form>
</div>

</template>