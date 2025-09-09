<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';
import ReusableDropdown from './ReusableDropdown.vue';
import { onMounted, ref } from 'vue';
import { addWalkinToSystem, fetchAllGenres, getAllJudges } from '@/utils/api';
const props = defineProps({
    show: { type: Boolean, default: false },
    title: { type: String, default: "Modal Title" },
    event: { type: String, default: "" }
    })

const emit = defineEmits(['createNewEntry',"close"])

const name = ref("")
const selectedGenre = ref("")
const selectedJudge = ref("")
const genreOptions = ref([])
const allJudges = ref([])

const showModel = ref(false)

const submitNewEntry = async ()=>{
    if(name.value == ""){
        showModel.value = true
        return
    }
    emit("createNewEntry")
    await addWalkinToSystem(name.value, props.event, selectedGenre.value, selectedJudge.value);
    name.value = ""
}

onMounted(async()=>{
    const genres = await fetchAllGenres()
    genreOptions.value = genres.map(g => g.genreName)
    const res = await getAllJudges()
    allJudges.value =["", ...Object.values(res).map(item => item.judgeName)];
})
</script>

<template>
    <ActionDoneModal
        :show="props.show"
        :title="props.title"
        @accept="submitNewEntry"
        @close="$emit('close')">
        <div>
            <form>
                <!-- Name -->
                 <label class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Name</label>
                 <input
                  placeholder="Enter stage name"
                  v-model="name"
                 class="border rounded-lg px-3 py-2 w-full focus:ring focus:ring-blue-300"></input>
                <!-- Genre should--> 
                <ReusableDropdown v-model="selectedGenre"
                class="my-3"
                labelId="Genre" :options="genreOptions"></ReusableDropdown>
                <!-- Judge -->
                <ReusableDropdown v-model="selectedJudge"
                labelId="Judge" :options="allJudges"></ReusableDropdown>
            </form>
        </div>
    </ActionDoneModal>
    <ActionDoneModal
        :show="showModel"
        title="Opps!"
        @accept="()=>{showModel = false}"
        @close="()=>{showModel = false}">
        Name cannot be empty!
    </ActionDoneModal>
</template>