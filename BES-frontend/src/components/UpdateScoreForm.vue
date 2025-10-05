<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';
import ReusableDropdown from './ReusableDropdown.vue';
import { onMounted, ref } from 'vue';
import { addWalkinToSystem, fetchAllGenres, getAllJudges, submitParticipantScore } from '@/utils/api';
const props = defineProps({
    show: { type: Boolean, default: false },
    title: { type: String, default: "Modal Title" },
    event: { type: String, default: "" },
    genre: { type: String, default: "" },
    name: { type: String, default: "" },
    score: {type: Number, default: 0}
    })

const emit = defineEmits(['updateScore',"close"])

const selectedJudge = ref("")
const allJudges = ref([])

const modalTitle = ref("")
const modalMessage = ref("")
const showModal = ref(false)

const openModal = (title, message) => {
    modalTitle.value = title
    modalMessage.value = message
    showModal.value = true
}

const updateScore = async ()=>{
    if(selectedJudge.value === ""){
        openModal("Opps", "Judge cannot be empty")
        return
    }else if(props.score > 10 || props.score <= 0){
        openModal("Oops", "Score must be between 0 and 10")
        return
    }
    emit("updateScore")
    await submitParticipantScore(props.event, props.genre, selectedJudge.value, [{"participantName":props.name, "score": props.score}] )
}

onMounted(async()=>{
    const res = await getAllJudges()
    allJudges.value =["", ...Object.values(res).map(item => item.judgeName)];
})
</script>

<template>
    <ActionDoneModal
        :show="props.show"
        :title="props.title"
        @accept="updateScore"
        @close="$emit('close')">
        <div>
            <form>
                <!-- Name -->
                 <h1 class="text-2xl font-semibold text-gray-900 dark:text-white mb-2">Name: {{ props.name }}</h1>
                 <label class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Score</label>
                 <input
                    type="number"
                    min="0"
                    max="10"
                    step=".1"
                    placeholder="Score"
                    v-model="props.score"
                    class="border rounded-lg px-3 py-2 w-full focus:ring focus:ring-blue-300"></input>
                <!-- Judge -->
                <ReusableDropdown v-model="selectedJudge"
                labelId="Judge" :options="allJudges"></ReusableDropdown>
            </form>
        </div>
    </ActionDoneModal>
    <ActionDoneModal
        :show="showModal"
        :title="modalTitle"
        @accept="()=>{showModal = false}"
        @close="()=>{showModal = false}">
        {{modalMessage}}
    </ActionDoneModal>
</template>