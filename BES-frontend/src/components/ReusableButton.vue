<script setup>
import { defineProps, defineEmits } from 'vue';
const props = defineProps({
    buttonName: {
        type: String,
        required: true,
        default: ()=>("")
    },
    disabled:{
        type: Boolean,
        required: false,
        default: ()=>(false)
    }
})

const emit = defineEmits(["onClick"])
</script>

<template>
    <div class="flex justify-center">
        <button
        :disabled="props.disabled" 
        class="bg-orange-400 hover:bg-orange-300 text-white font-semibold py-2 px-4 rounded 
        active:bg-orange-200 sm:min-w-[20px] md:min-w-[70px] lg:min-w-[100px]"
        :class="$attrs.class"
        @click="emit('onClick')"> {{ props.buttonName }}</button>
    </div>
</template>