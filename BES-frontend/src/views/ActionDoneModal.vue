<script setup>
import ReusableButton from '@/components/ReusableButton.vue';

defineProps({
show: { type: Boolean, default: false },
title: { type: String, default: "Modal Title" }
})
</script>

<template>
    <div
        v-if="show"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
    >
        <div class="relative w-auto max-w-[90vw] max-h-[90vh]
        overflow-y-auto p-4 bg-white rounded-lg shadow dark:bg-gray-700">
            <div class="flex justify-end">
            <button
            @click="$emit('close')"
            class="text-gray-400 hover:text-gray-900 dark:hover:text-white"
            >
            ✖
        </button>
            </div>
        <div class="flex items-center justify-center px-4">
            <h3 class="text-xl font-semibold text-gray-900 dark:text-white">
            {{ title }}
            </h3>
            
        </div>

        <div class="p-4 text-center space-y-4">
            <div class="whitespace-pre-line">
            <slot/>
        </div>
        </div>

        <div class="flex justify-center gap-3  px-4">
            <ReusableButton @onClick="$emit('accept')" buttonName="Ok"></ReusableButton>
        </div>
        </div>
    </div>
</template>     
  