<script setup>
defineProps({
  show:          { type: Boolean, default: false },
  title:         { type: String,  default: 'Notification' },
  variant:       { type: String,  default: 'info' },
  acceptLabel:   { type: String,  default: 'OK' },
  scrollable:    { type: Boolean, default: false },
  disableAccept: { type: Boolean, default: false },
  wide:          { type: Boolean, default: false }
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
        class="card-hover relative w-full mx-4"
        :class="[wide ? 'max-w-lg' : 'max-w-sm', scrollable ? 'flex flex-col p-0' : 'p-8']"
        :style="scrollable ? 'max-height:90dvh' : ''"
      >
        <div class="corner-bar-tl"></div>
        <div class="corner-bar-bl"></div>

        <!-- scrollable layout: sticky header + scrollable body + sticky footer -->
        <template v-if="scrollable">
          <div class="flex-shrink-0 px-8 pt-8 pb-3">
            <p class="type-page-title">{{ title }}</p>
          </div>
          <div
            class="flex-1 min-h-0 overflow-y-auto px-8"
            style="scrollbar-width:thin;scrollbar-color:rgba(255,255,255,0.1) transparent"
          >
            <div v-if="variant === 'warning'" class="semantic-chip-warning p-4 mb-4 flex items-start gap-3">
              <div class="w-2 h-2 rounded-full flex-shrink-0 mt-1" style="background:#f59e0b;box-shadow:0 0 6px rgba(245,158,11,0.8)"></div>
              <slot></slot>
            </div>
            <div v-else-if="variant === 'error'" class="semantic-chip-error p-4 mb-4 flex items-start gap-3">
              <div class="w-2 h-2 rounded-full flex-shrink-0 mt-1" style="background:#f87171;box-shadow:0 0 6px rgba(239,68,68,0.8)"></div>
              <slot></slot>
            </div>
            <div v-else class="type-body text-content-muted">
              <slot></slot>
            </div>
          </div>
          <div class="flex-shrink-0 px-8 pb-8 pt-4 border-t border-surface-600/30">
            <button
              @click="$emit('accept')"
              class="para-chip type-label px-6 py-3 w-full border-2 transition-colors"
              :class="disableAccept ? 'border-surface-600 text-content-muted cursor-not-allowed opacity-40' : 'border-accent text-accent hover:bg-accent hover:text-surface-900'"
              :disabled="disableAccept"
            >{{ acceptLabel }}</button>
          </div>
        </template>

        <!-- default non-scrollable layout (unchanged) -->
        <template v-else>
          <p class="type-page-title mb-4">{{ title }}</p>
          <div v-if="variant === 'warning'" class="semantic-chip-warning p-4 mb-6 flex items-start gap-3">
            <div class="w-2 h-2 rounded-full flex-shrink-0 mt-1" style="background:#f59e0b;box-shadow:0 0 6px rgba(245,158,11,0.8)"></div>
            <slot></slot>
          </div>
          <div v-else-if="variant === 'error'" class="semantic-chip-error p-4 mb-6 flex items-start gap-3">
            <div class="w-2 h-2 rounded-full flex-shrink-0 mt-1" style="background:#f87171;box-shadow:0 0 6px rgba(239,68,68,0.8)"></div>
            <slot></slot>
          </div>
          <div v-else class="type-body text-content-muted mb-6">
            <slot></slot>
          </div>
          <button
            @click="$emit('accept')"
            class="para-chip type-label px-6 py-3 w-full border-2 transition-colors"
            :class="disableAccept ? 'border-surface-600 text-content-muted cursor-not-allowed opacity-40' : 'border-accent text-accent hover:bg-accent hover:text-surface-900'"
            :disabled="disableAccept"
          >{{ acceptLabel }}</button>
        </template>
      </div>
    </div>
  </Transition>
</template>
