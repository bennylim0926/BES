<script setup>
defineProps({
  show:        { type: Boolean, default: false },
  title:       { type: String,  default: 'Notification' },
  variant:     { type: String,  default: 'info' },
  acceptLabel: { type: String,  default: 'OK' }
})

defineEmits(['close', 'accept'])
</script>

<template>
  <Transition
    enter-active-class="transition duration-200 ease-out"
    enter-from-class="opacity-0"
    enter-to-class="opacity-100"
    leave-active-class="transition duration-150 ease-in"
    leave-from-class="opacity-100"
    leave-to-class="opacity-0"
  >
    <div
      v-if="show"
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
    >
      <div
        class="absolute inset-0 bg-black/80 backdrop-blur-sm"
        @click="$emit('close')"
      ></div>

      <div
        class="card-hover p-8 relative max-w-sm w-full mx-4"
      >
        <div class="corner-bar-tl"></div>
        <div class="corner-bar-bl"></div>

        <p class="type-page-title mb-4">{{ title }}</p>

        <div
          v-if="variant === 'warning'"
          class="semantic-chip-warning p-4 mb-6 flex items-start gap-3"
        >
          <div class="w-2 h-2 rounded-full flex-shrink-0 mt-1" style="background:#f59e0b;box-shadow:0 0 6px rgba(245,158,11,0.8)"></div>
          <slot></slot>
        </div>
        <div
          v-else-if="variant === 'error'"
          class="semantic-chip-error p-4 mb-6 flex items-start gap-3"
        >
          <div class="w-2 h-2 rounded-full flex-shrink-0 mt-1" style="background:#f87171;box-shadow:0 0 6px rgba(239,68,68,0.8)"></div>
          <slot></slot>
        </div>
        <div v-else class="type-body text-content-muted mb-6">
          <slot></slot>
        </div>

        <button
          @click="$emit('accept')"
          class="para-chip type-label px-6 py-3 w-full border-2 border-accent text-accent hover:bg-accent hover:text-surface-900 transition-colors"
        >{{ acceptLabel }}</button>
      </div>
    </div>
  </Transition>
</template>
