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
        <button class="bg-transparent hover:bg-orange-300 text-gray-700 dark:text-gray-100 font-semibold hover:text-white py-2 px-4 border border-orange-400 hover:border-transparent rounded 
        active:bg-orange-400 active:border active:text-gray-100 sm:min-w-[20px] md:min-w-[70px] lg:min-w-[100px]"
        :class="$attrs.class"
        @click="emit('onClick')"> {{ props.buttonName }}</button>
    </div>
</template>