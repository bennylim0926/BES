<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';
import { ref } from 'vue';

const props = defineProps({
    show: { type: Boolean, default: false },
    title: { type: String, default: "Modal Title" },
    type: { type: String, default: "text" }
    })

const emit = defineEmits(['submitUpdate',"close"])

const name = ref("")
const showModel = ref(false)

const modalTitle = ref('')
const modalMessage = ref('')

function isNumeric(str) {
  return !isNaN(str) && !isNaN(parseFloat(str));
}

const submitUpdate = async ()=>{
    if(props.type === 'text'){
        if(name.value == ""){
            showModel.value = true
            modalTitle.value = "Failed to update"
            modalMessage.value = "Field cannot be empty"
            return
        }
        emit("submitUpdate", name.value)
    }else if(props.type === 'number'){
        if(isNumeric(name.value) && Number(name.value) > 0){
            emit("submitUpdate", Number(name.value))
        }else{
            showModel.value = true
            modalTitle.value = "Failed to update"
            modalMessage.value = "Field must be numeric number > 0 & cannot be empty"
        }
    }
    name.value = ""
}

</script>

<template>
    <ActionDoneModal
        :show="props.show"
        :title="props.title"
        @accept="submitUpdate"
        @close="$emit('close')">
        <div>
            <form>
                 <label class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">Name</label>
                 <input
                  placeholder="Enter new value"
                  v-model="name"
                 class="border rounded-lg px-3 py-2 w-full focus:ring focus:ring-blue-300"></input>
            </form>
        </div>
    </ActionDoneModal>
    <ActionDoneModal
        :show="showModel"
        :title=modalTitle
        @accept="()=>{showModel = false}"
        @close="()=>{showModel = false}">
        {{modalMessage}}
    </ActionDoneModal>
</template>