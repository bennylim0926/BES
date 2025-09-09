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
        <button class="bg-transparent hover:bg-gray-500 text-gray-400 font-semibold hover:text-white py-2 px-4 border border-gray-500 hover:border-transparent rounded mb-3" @click="emit('onClick')"> {{ props.buttonName }}</button>
    </div>
</template>