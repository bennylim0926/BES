<script setup>
const props = defineProps({
  buttonName: { type: String, required: true, default: '' }
})

const emit = defineEmits(['onClick'])
</script>

<template>
  <div
    class="group relative bg-white rounded-2xl border border-surface-200/80 shadow-sm
           hover:shadow-md hover:border-primary-200
           border-l-[4px] border-l-primary-500
           transition-all duration-200 cursor-pointer overflow-hidden w-full"
    @click="emit('onClick')"
  >
    <div class="flex items-center justify-between p-5 gap-4">
      <!-- Event name -->
      <h3 class="font-heading font-bold text-base text-surface-900 leading-snug line-clamp-2 flex-1">
        {{ props.buttonName }}
      </h3>

      <!-- Chevron indicator -->
      <div
        class="flex-shrink-0 w-8 h-8 rounded-full bg-surface-100
               group-hover:bg-primary-100 flex items-center justify-center
               transition-colors duration-200"
      >
        <i
          class="pi pi-chevron-right text-surface-400 group-hover:text-primary-600 text-xs
                 group-hover:translate-x-0.5 transition-all duration-200"
        ></i>
      </div>
    </div>
  </div>
</template>
