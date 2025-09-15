<script setup>
import { ref } from 'vue'
import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from '@headlessui/vue'

const currentSelected = defineModel() // keep your v-model
const props = defineProps({
  options: {
    type: Array,
    required: true,
    default: () => []
  },
  labelId: {
    type: String,
    required: true,
    default: () => ""
  }
})

// make sure currentSelected always has a value
if (!currentSelected.value && props.options.length > 0) {
  currentSelected.value = ""
}
</script>

<template>
  <div>
    <label :for="labelId" class="block mb-2 text-sm font-medium text-gray-900 dark:text-white">
      {{ labelId }}
    </label>

    <Listbox v-model="currentSelected">
      <div class="relative">
        <!-- Button -->
        <ListboxButton
          class="w-full rounded-lg border border-gray-300 bg-white p-2.5 text-left text-gray-900 text-sm dark:bg-gray-700 dark:text-white"
        >
          {{ currentSelected || "Select an option" }}
        </ListboxButton>

        <!-- Options -->
        <ListboxOptions
          class="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-lg border border-gray-300 bg-white shadow-lg text-sm dark:bg-gray-700 dark:text-white"
        >
          <ListboxOption
            v-for="option in props.options"
            :key="option"
            :value="option"
            class="cursor-pointer px-4 py-2 hover:bg-blue-100 dark:hover:bg-gray-600
            hover:text-orange-400 "
          >
            {{ option }}
          </ListboxOption>
        </ListboxOptions>
      </div>
    </Listbox>
  </div>
</template>
