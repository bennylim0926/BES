<script setup>
import ReusableButton from '@/components/ReusableButton.vue';
import { addGenre, addJudge, deleteGenre, deleteImage, deleteJudge, deleteScore, getAllImages, updateGenre, updateJudge } from '@/utils/adminApi';
import { checkInputNull } from '@/utils/utils';
import { onMounted, ref } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import { fetchAllEvents, fetchAllGenres, getAllJudges } from '@/utils/api';
import UpdateFieldForm from '@/components/UpdateFieldForm.vue';
import { title } from '@primeuix/themes/aura/card';

const addJudgeInput = ref('');
const addGenreInput = ref('');

const updateJudgeInput = ref('');
const updateGenreInput = ref('');

const modalTitle = ref("")
const modalMessage = ref("")
const showModal = ref(false)

const updateModalTitle = ref("Update")
const updateModalMessage = ref("")
const showUpdateModal = ref(false)
const updateType = ref("text")
const updateField = ref("")
const selectedId = ref(0)

const openModal = (title, message) => {
    modalTitle.value = title
    modalMessage.value = message
    showModal.value = true
    dynamicHandler.value = ()=>{
        showModal.value = false
    }
}

const selectId = (id, field)=>{
    selectedId.value = id
    showUpdateModal.value = true
    updateField.value = field
}

const submitUpdate = (value)=>{
    if(updateField.value === "judge"){
        submitUpdateJudge(selectedId.value, value)
        judges.value = judges.value.map(x =>
            x.judgeId === selectedId.value ? { ...x, judgeName: value } : x
        )
    }else if(updateField.value === "genre"){
        submitUpdateGenre(selectedId.value, value)
        genres.value = genres.value.map(x =>
            x.id === selectedId.value ? { ...x, genreName: value } : x
        )
    }
    showUpdateModal.value = false   
}

const judges = ref([])
const genres = ref([])
const events = ref([])
const images = ref([])

const dynamicHandler = ref(()=>{})

const confirmResetScore = (id, title, message) =>{
    modalTitle.value = title
    modalMessage.value = message
    showModal.value = true
    dynamicHandler.value = async ()=>{
        await deleteScore(id)
        showModal.value = false
    }
}

const confirmRemoveImage = (name, title, message) =>{
    modalTitle.value = title
    modalMessage.value = message
    showModal.value = true
    dynamicHandler.value = async ()=>{
        await submitDeleteImage(name)
        showModal.value = false
    }
}

const confirmRemoveJudge = (id, title, message) =>{   
    modalTitle.value = title
    modalMessage.value = message
    showModal.value = true
    dynamicHandler.value = async ()=>{
        await submitDeleteJudge(id)
        showModal.value = false
    }
}

const confirmRemoveGenre = (id, title, message) =>{
    modalTitle.value = title
    modalMessage.value = message
    showModal.value = true
    dynamicHandler.value = async ()=>{
        await submitDeleteGenre(id)
        showModal.value = false
    }
}

const submitAddGenre = async()=>{
    if(checkInputNull(addGenreInput.value)){
        openModal("Failed to add", "Field cannot be empty")
    }else{
        const res = await addGenre(addGenreInput.value)
        genres.value = await res.json()
        addGenreInput.value = ''
    }
}

const submitAddJudge = async()=>{
    if(checkInputNull(addJudgeInput.value)){
        openModal("Failed to add", "Field cannot be empty")
    }else{
        const res = await addJudge(addJudgeInput.value)
        judges.value = await res.json()
        addJudgeInput.value = ''
    }
}

const submitUpdateGenre = async(id, value)=>{
    const res = await updateGenre(id,value)
}

const submitUpdateJudge = async(id, value)=>{
    const res = await updateJudge(id,value)
}

const submitDeleteJudge = async(id)=>{
    const res = await deleteJudge(id)
    if(res.ok){
        judges.value = judges.value.filter(j => j.judgeId !== id)
    }
}

const submitDeleteGenre = async(id)=>{
    const res = await deleteGenre(id)
    if(res.ok){
        genres.value = genres.value.filter(g => g.id !== id)
    }
}

const submitDeleteImage = async(name)=>{
    const res = await deleteImage(name)
    if(res.ok){
        images.value = images.value.filter(i => i != name)
    }
}

onMounted(async ()=>{
    genres.value =  await fetchAllGenres()
    judges.value =  await getAllJudges()
    events.value = await fetchAllEvents()
    images.value = await getAllImages()
})
</script>

<template>
    <!-- 
        The admin page should have this:
        1. Add/Remove/Update Judge
            Able to see Judge in which event
        2. Add/Update Genre     
            Able to see Genre in which event
        3. Delete Score by Event
        4. List/Remove uploaded images
    -->
            <div class="m-5">
            <div class="flex justify-start mx-3">
                <div>
                <input
                    v-model="addJudgeInput"
                    type="text"
                    placeholder="Judge Name"
                    class="mb-3 border rounded-lg px-3 py-2 w-full focus:ring focus:ring-blue-300"
                ></input>
                </div>
                <ReusableButton 
                    buttonName="Add Judge"
                    @onClick="submitAddJudge"
                    class="ml-3"
                    ></ReusableButton>
            </div>
            <div class="grid grid-cols-1">
                <div class="grid grid-cols-5">
                    <div v-for="(value,idx) in judges" :key="value.judgeId"
                    class="grid grid-cols-[4fr_1fr] m-3 p-3 rounded bg-[#fffaf5] shadow-xl">
                        <div class="text-xl" @click="selectId(value.judgeId, 'judge')">
                            {{ value.judgeName }}
                        </div>
                        <div class="text-lg ml-3 justify-end hover:text-orange-300"
                        @click="confirmRemoveJudge(value.judgeId, 'Confirm remove?', 'Are you sure you want to remove this judge?')"> x </div>
                    </div>
                    </div>
                </div>
            </div>
            <!-- </div> -->
             <div class="m-5">
            <div>
                <div class="flex justify-start mx-3">
                    <div>
                <input
                    v-model="addGenreInput"
                    type="text"
                    placeholder="Genre Name"
                    class="mb-3 border rounded-lg px-3 py-2 w-full focus:ring focus:ring-blue-300"
                ></input>
            </div>
                <ReusableButton 
                    buttonName="Add Genre"
                    @onClick="submitAddGenre"
                    class="ml-3"
                ></ReusableButton>
            </div>
            
            </div>
            <div class="grid grid-cols-1">
                <div class="grid grid-cols-5">
                    <div v-for="(value,idx) in genres" :key="value.id"
                    class="grid grid-cols-[4fr_1fr] m-3 p-3 rounded bg-[#fffaf5] shadow-xl">
                        <div class="text-xl" @click="selectId(value.id,'genre')">
                            {{ value.genreName }}
                        </div>
                        <div class="text-lg ml-3 flex justify-end hover:text-orange-300"
                            @click="confirmRemoveGenre(value.id, 'Confirm remove', 'Are you sure you want to remove this genre?')"> x </div>
                    </div>
                </div>
            </div>
        </div>
    <div class="w-fit m-5">
        <div class="text-xl">Reset Score</div>
        <div class="grid grid-cols-4">
        <div v-for="(value,idx) in events" :key="value.id" 
            class="grid grid-cols-[4fr_1fr] m-3 p-3 rounded bg-[#fffaf5] shadow-xl">
                <div class="text-xl">
                    {{ value.name }}
        </div>
        <div class="flex items-center justify-end text-lg ml-3 hover:text-orange-300"
            @click="confirmResetScore(value.id, 'Confirm reset', `Are you sure you want to reset the score for ${value.name}?`)"> Reset 
        </div>
    </div>
</div>
</div>
<div class="w-fit m-5">
    <div class="text-xl">Images</div>
    <div class="grid grid-cols-4">
        <div v-for="(value,idx) in images"  
            class="grid grid-cols-[4fr_1fr] m-3 p-3 rounded bg-[#fffaf5] shadow-xl">
                <div class="text-xl">
                    {{ value }}
        </div>
        <div class="text-lg ml-3 flex justify-end hover:text-orange-300"
            @click="confirmRemoveImage(value, `Confirm delete ${value}`, 'Are you sure you want to delete this image?')"
            > x 
        </div>
    </div>
</div>
</div>
    <ActionDoneModal
        :show="showModal"
        :title="modalTitle"
        @accept="()=>{dynamicHandler()}"
        @close="()=>{showModal = false}"
    >
    <p>
      {{ modalMessage}}
    </p>
  </ActionDoneModal>
  <UpdateFieldForm
    :show="showUpdateModal"
    :title="updateModalTitle"
    :type="updateType"
    @close="()=>{showUpdateModal = !showUpdateModal}"
    @submitUpdate="submitUpdate"
  > {{ updateModalMessage }}</UpdateFieldForm>
</template>