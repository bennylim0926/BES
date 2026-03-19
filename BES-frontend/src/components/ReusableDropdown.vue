<script setup>
import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from '@headlessui/vue'

const currentSelected = defineModel()

const props = defineProps({
  options:  { type: Array,  required: true,  default: () => [] },
  labelId:  { type: String, required: true,  default: '' },
  placeholder: { type: String, default: 'Select an option' }
})

if (!currentSelected.value && props.options.length > 0) {
  currentSelected.value = ''
}
</script>

<template>
  <div>
    <label class="block mb-1.5 text-sm font-medium text-surface-700">
      {{ labelId }}
    </label>

    <Listbox v-model="currentSelected" v-slot="{ open }">
      <div class="relative">

        <!-- Trigger Button -->
        <ListboxButton
          class="w-full flex items-center justify-between rounded-xl border bg-white px-4 py-2.5
                 text-left text-sm shadow-sm
                 hover:border-primary-400
                 focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500
                 transition-all duration-200"
          :class="[
            open
              ? 'border-primary-500 ring-2 ring-primary-500/30'
              : 'border-surface-200',
            currentSelected ? 'text-surface-900' : 'text-surface-400'
          ]"
        >
          <span class="truncate">{{ currentSelected || placeholder }}</span>
          <i
            class="pi pi-chevron-down flex-shrink-0 text-surface-400 text-xs
                   transition-transform duration-200"
            :class="open ? 'rotate-180' : ''"
          ></i>
        </ListboxButton>

        <!-- Options List -->
        <Transition
          enter-active-class="transition duration-150 ease-out"
          enter-from-class="opacity-0 -translate-y-2 scale-95"
          enter-to-class="opacity-100 translate-y-0 scale-100"
          leave-active-class="transition duration-100 ease-in"
          leave-from-class="opacity-100 translate-y-0 scale-100"
          leave-to-class="opacity-0 -translate-y-2 scale-95"
        >
          <ListboxOptions
            class="absolute z-50 mt-1.5 max-h-60 w-full overflow-auto
                   rounded-xl border border-surface-200 bg-white
                   shadow-xl shadow-surface-900/10
                   py-1 focus:outline-none"
          >
            <ListboxOption
              v-for="option in props.options"
              :key="option"
              :value="option"
              v-slot="{ active, selected }"
            >
              <li
                class="flex items-center justify-between px-4 py-2.5
                       cursor-pointer text-sm select-none
                       transition-colors duration-100"
                :class="[
                  active   ? 'bg-primary-50 text-primary-700' : 'text-surface-700',
                  selected ? 'font-semibold' : 'font-normal'
                ]"
              >
                {{ option }}
                <i v-if="selected" class="pi pi-check text-primary-500 text-xs flex-shrink-0"></i>
              </li>
            </ListboxOption>
          </ListboxOptions>
        </Transition>

      </div>
    </Listbox>
  </div>
</template>
