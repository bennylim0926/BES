<script setup>
import { defineProps, defineEmits } from 'vue';
const props = defineProps({
    buttonName: {
        type: String,
        required: true,
        default: ()=>("")
    }
})

const emit = defineEmits(["onClick"])
</script>

<template>
    <div class="flex justify-center">
        <button class="bg-transparent hover:bg-orange-300 text-gray-700 dark:text-gray-100 font-semibold hover:text-white py-2 px-4 border border-orange-400 hover:border-transparent rounded mb-3
        active:bg-orange-500 active:border-2 active:text-gray-100"
        ontouchstart="bg-orange-500 border-2 text-gray-100" 
        @click="emit('onClick')"> {{ props.buttonName }}</button>
    </div>
</template>