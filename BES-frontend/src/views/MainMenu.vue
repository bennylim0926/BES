<!-- 
 Main menu should consists of :
 1. Choose Event Button
 if event is chosen then show:
    2. Registration Button
    3. Emcee page Button
    4. Judges page Button
    5. Information consist of:<template>
    - Audience number
    - Number of participants in different categories
-->
<script setup>
import Button from "primevue/button"
import { ref, onMounted } from "vue";
const selectedEvent = ref(null)
const events = ref(null)
const participantsNumBreakdown = ref(null)
const csvFileId = ref('')
const fetchEventData = async () =>{
    try{
        const res = await fetch('http://localhost:5050/api/v1/folders')
        if (!res.ok) throw new Error('Failed to fetch')
        events.value = res.json().then(result =>{
            events.value = result})
        // console.log(data.value)
    }catch(err){
        console.log(err)
    }finally{
    }
}

const onEventSelected = async(event)=>{
    // take the folder id and find its csv
    // console.log(event.folderID)
    try{
        const res = await fetch(`http://localhost:5050/api/v1/files/${event.folderID}`)
        if (!res.ok) throw new Error('Failed to fetch')
        res.json().then(result =>{
            findResponseSheetId(result)}) 
    }catch(err){
    }
}

const getResponseDetails = async(fileId) =>{
    try{
        const res = await fetch(`http://localhost:5050/api/v1/sheets/${fileId}`)
        if (!res.ok) throw new Error('Failed to fetch')
        res.json().then(result =>{
            participantsNumBreakdown.value = result}) 
    }catch(err){
    }
}

const findResponseSheetId = (data) =>{
    for (let i = 0; i < data.length ; i++) {
        if(data[i].fileType === "application/vnd.google-apps.spreadsheet"){
            getResponseDetails(data[i].fileId)
        }
}
}

onMounted(()=>{
    fetchEventData()
})
</script>

<template>
<select class="dropdown" v-model="selectedEvent" @change="()=>onEventSelected(selectedEvent)">
    <option :value="''"></option>
 <option v-for="(event) in events" :key="event.folderID" :value="event">{{event.folderName}}</option>
</select>
<div v-if="selectedEvent !== null">
    <br></br>
    <Button>Registration</Button>
    <br></br>
    <Button>Emcee Page</Button>
    <br></br>
    <Button>Judges Page</Button>
    <div v-if="participantsNumBreakdown !=null">
        Participants Info:
        <div>Audience: {{ participantsNumBreakdown['audience'] }} </div>
        <div>Open: {{ participantsNumBreakdown['open'] }}</div>
        <div>Popping: {{ participantsNumBreakdown['popping'] }}</div>
        <div>Locking: {{ participantsNumBreakdown['locking'] }}</div>
        <div>Waacking: {{ participantsNumBreakdown['waacking'] }}</div>
        <div>Hip Hop: {{ participantsNumBreakdown['hiphop'] }}</div>
        <div>Breaking: {{ participantsNumBreakdown['breaking'] }}</div>
    </div>
</div>

</template>