<script setup>
import { ref, watch, defineProps, defineEmits } from "vue"
import DynamicTable from "./DynamicTable.vue"

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => [""],
  },
})

const emit = defineEmits(["update:modelValue"])

const inputs = ref([...props.modelValue])

// Sync with parent
watch(inputs, (newVal) => {
  emit("update:modelValue", newVal)
}, { deep: true })
</script>

<template>
    
  <div class=" mx-auto p-4 bg-gray shadow rounded-lg space-y-3">
    <h1 class="flex justify-center gap-2 text-2xl font-extrabold leading-none tracking-tight text-gray-900 md:text-5xl lg:text-2xl dark:text-white mb-3">Judges</h1>
    <div v-for="(value, index) in inputs" :key="index" class="flex items-center gap-2">
      <input
        v-model="inputs[index]"
        type="text"
        placeholder="Enter text"
        class="border rounded-lg px-3 py-2 w-full focus:ring focus:ring-blue-300"
      />
      <button
        type="button"
        class="px-2 py-1 bg-red-400 text-white rounded-lg hover:bg-red-300"
        @click="inputs.splice(index, 1)"
      >
        ✕
      </button>
    </div>

    <button
      type="button"
      class="px-3 py-2 bg-gray-700 text-white rounded-lg hover:bg-gray-500 w-full"
      @click="inputs.length < 10 ? inputs.push('') : null"
      :disabled="inputs.length >= 10"
    >
      + Add
    </button>
  </div>
</template>
