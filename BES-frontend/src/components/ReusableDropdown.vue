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
    <label :for="labelId" class="block mb-2 text-sm font-medium text-black">
      {{ labelId }}
    </label>

    <Listbox v-model="currentSelected">
      <div class="relative">
        <!-- Button -->
        <ListboxButton
          class="w-full rounded-lg border border-gray-300 bg-white p-2.5 text-left text-gray-900 text-sm"
        >
          {{ currentSelected || "Select an option" }}
        </ListboxButton>

        <!-- Options -->
        <ListboxOptions
          class="absolute z-51 mt-1 max-h-60 w-full overflow-auto rounded-lg border border-gray-300 bg-white shadow-lg text-sm"
        >
          <ListboxOption
            v-for="option in props.options"
            :key="option"
            :value="option"
            class="cursor-pointer px-4 py-2 hover:bg-blue-100
            hover:text-orange-400 "
          >
            {{ option }}
          </ListboxOption>
        </ListboxOptions>
      </div>
    </Listbox>
  </div>
</template>
