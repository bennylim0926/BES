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

const events = ref(null)
const currentEvent = ref(null)
const participantsNumBreakdown = ref(null)

// step 1
const fetchEventData = async () =>{
    try{
        const res = await fetch('http://localhost:5050/api/v1/folders')
        if (!res.ok) throw new Error('Failed to fetch')
        events.value = res.json().then(result =>{
            events.value = result})
    }catch(err){
        console.log(err)
    }finally{
    }
}

// step 2
const onEventSelected = async(event)=>{
    try{
        const res = await fetch(`http://localhost:5050/api/v1/files/${event.folderID}`)
        if (!res.ok) throw new Error('Failed to fetch')
        res.json().then(result =>{
            findResponseSheetId(result)}) 
    }catch(err){
    }
}

// step 3
// if no csv found it should display no data found
const findResponseSheetId = (data) =>{
    console.log(data)
    if(data.length == 0)participantsNumBreakdown.value = null
    else getResponseDetails(data[0].fileId)
}

// step 4
const getResponseDetails = async(fileId) =>{
    try{
        const res = await fetch(`http://localhost:5050/api/v1/sheets/${fileId}`)
        if (!res.ok) throw new Error('Failed to read')
        res.json().then(result =>{
            participantsNumBreakdown.value = result}) 
    }catch(err){
    }
}

onMounted(()=>{
    fetchEventData()
})
</script>

<template>
<select 
    class="dropdown" 
    v-model="currentEvent" 
    @change="()=>onEventSelected(currentEvent)">
    <option 
        v-for="(event) in events" 
        :key="event.folderID" 
        :value="event">{{event.folderName}}
    </option>
</select>
<div v-if="currentEvent !== null">
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
    <div v-else>
        <div>No data found</div>
    </div>
</div>

</template>