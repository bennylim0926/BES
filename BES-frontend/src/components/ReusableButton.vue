<script setup>
/**
 * Button variants follow the 60-30-10 color rule:
 *   primary   → cyan brand color  (the ONE "do this" action)
 *   secondary → dark surface fill  (strong secondary — no competing hue)
 *   ghost     → transparent + cyan text  (soft tertiary)
 *   outline   → bordered, neutral text   (low-emphasis action)
 *   danger    → red fill          (destructive / irreversible only)
 *
 * Orange is NOT a button variant — it is reserved for accent/medal contexts.
 */
const props = defineProps({
  buttonName: { type: String, default: '' },
  disabled:   { type: Boolean, default: false },
  variant:    { type: String, default: 'primary' }, // primary | secondary | ghost | outline | danger
  size:       { type: String, default: 'md' }        // sm | md | lg
})

const emit = defineEmits(['onClick'])

const sizeMap = {
  sm: 'text-xs  px-3.5 py-1.5 gap-1.5',
  md: 'text-sm  px-5   py-2.5 gap-2',
  lg: 'text-base px-7  py-3   gap-2.5',
}

const variantMap = {
  // Cyan brand — primary-600 base
  primary:   'bg-primary-600 hover:bg-primary-700 active:bg-primary-700 text-white shadow-sm hover:shadow-md focus:ring-primary-600/40 btn-glow',
  // Dark surface — secondary solid action
  secondary: 'bg-surface-600 hover:bg-surface-500 active:bg-surface-500 text-content-primary shadow-sm hover:shadow-md focus:ring-surface-500/40',
  // Transparent — tertiary / text-button style
  ghost:     'bg-transparent text-primary-400 hover:bg-primary-100 active:bg-primary-100 focus:ring-primary-500/40',
  // Bordered — secondary action with low fill weight
  outline:   'bg-transparent border border-surface-500 text-content-secondary hover:bg-surface-700 hover:border-surface-400 active:bg-surface-700 focus:ring-surface-400/40',
  // Red — destructive actions only
  danger:    'bg-red-500 hover:bg-red-600 active:bg-red-700 text-white shadow-sm hover:shadow-md focus:ring-red-500/40 btn-glow',
}
</script>

<template>
  <div class="flex justify-center">
    <button
      :disabled="props.disabled"
      class="relative w-full font-semibold rounded-xl inline-flex items-center justify-center
             transition-all duration-200
             active:scale-95
             disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100
             focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-offset-surface-800"
      :class="[sizeMap[props.size], variantMap[props.variant]]"
      @click="emit('onClick')"
    >
      <slot>{{ props.buttonName }}</slot>
    </button>
  </div>
</template>
