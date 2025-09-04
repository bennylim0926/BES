<!-- components/Modal.vue -->
<template>
    <div
      v-if="show"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
    >
      <div class="relative w-full max-w-sm p-4 bg-white rounded-lg shadow dark:bg-gray-700">
        <!-- Header -->
        <div class="flex items-center justify-center px-4">
          <h3 class="text-xl font-semibold text-gray-900 dark:text-white">
            {{ title }}
          </h3>
        </div>
  
        <!-- Body -->
        <div class="p-4 text-center space-y-4">
            <div class="whitespace-pre-line">
          <slot/>
        </div>
        </div>
  
        <!-- Footer -->
        <div class="flex justify-center gap-3  px-4">
          <button
            @click="$emit('accept')"
            class="px-5 w-md py-2.5 text-sm rounded-lg text-white bg-blue-700 hover:bg-blue-800"
          >
            Ok
          </button>
        </div>
      </div>
    </div>
  </template>
  
  <script setup>
  defineProps({
    show: { type: Boolean, default: false },
    title: { type: String, default: "Modal Title" }
  })
  </script>
  