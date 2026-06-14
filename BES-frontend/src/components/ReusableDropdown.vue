<script setup>
import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from '@headlessui/vue'

const currentSelected = defineModel()

const props = defineProps({
  options:  { type: Array,  required: true,  default: () => [] },
  labelId:  { type: String, required: true,  default: '' },
  placeholder: { type: String, default: 'Select an option' },
  openUp: { type: Boolean, default: false },
})

if (!currentSelected.value && props.options.length > 0) {
  currentSelected.value = ''
}
</script>

<template>
  <div>
    <label class="block mb-1.5 text-sm font-medium text-content-secondary">
      {{ labelId }}
    </label>

    <Listbox v-model="currentSelected" v-slot="{ open }">
      <div class="relative">

        <!-- Trigger Button -->
        <ListboxButton
          class="w-full flex items-center justify-between gap-2 px-3 py-2 para-chip type-name-sm transition-all duration-200"
          :class="[
            currentSelected ? 'text-accent' : 'text-content-muted'
          ]"
        >
          <span class="break-all leading-tight">{{ currentSelected || placeholder }}</span>
          <i
            class="pi pi-chevron-down flex-shrink-0 text-content-muted text-xs
                   transition-transform duration-200"
            :class="open ? 'rotate-180' : ''"
          ></i>
        </ListboxButton>

        <!-- Options List -->
        <Transition
          enter-active-class="transition duration-150 ease-out"
          enter-from-class="opacity-0 -translate-y-2"
          enter-to-class="opacity-100 translate-y-0"
          leave-active-class="transition duration-100 ease-in"
          leave-from-class="opacity-100 translate-y-0"
          leave-to-class="opacity-0 -translate-y-2"
        >
          <ListboxOptions
            :class="['absolute z-50 max-h-60 w-full overflow-auto card-hover py-1 focus:outline-none', openUp ? 'bottom-full mb-1.5' : 'mt-1.5']"
          >
            <ListboxOption
              v-for="option in props.options"
              :key="option"
              :value="option"
              v-slot="{ active, selected }"
            >
              <li
                class="flex items-center justify-between px-4 py-2.5
                       cursor-pointer type-name select-none
                       transition-colors duration-100"
                :class="[
                  active   ? 'text-accent bg-[rgba(255,255,255,0.04)]' : 'text-content-secondary',
                  selected ? 'text-accent' : ''
                ]"
              >
                {{ option }}
                <i v-if="selected" class="pi pi-check text-accent text-xs flex-shrink-0"></i>
              </li>
            </ListboxOption>
          </ListboxOptions>
        </Transition>

      </div>
    </Listbox>
  </div>
</template>
