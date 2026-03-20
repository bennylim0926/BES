<script setup>
import ReusableButton from '@/components/ReusableButton.vue'

const props = defineProps({
  show:    { type: Boolean, default: false },
  title:   { type: String,  default: 'Notification' },
  variant: { type: String,  default: 'info' }  // info | success | error | warning
})

defineEmits(['close', 'accept'])

const iconConfig = {
  info:    { icon: 'pi-info-circle',         color: 'text-primary-500',   bg: 'bg-primary-50'  },
  success: { icon: 'pi-check-circle',         color: 'text-emerald-500',   bg: 'bg-emerald-50'  },
  error:   { icon: 'pi-times-circle',         color: 'text-red-500',       bg: 'bg-red-50'      },
  warning: { icon: 'pi-exclamation-triangle', color: 'text-amber-500',     bg: 'bg-amber-50'    },
}
</script>

<template>
  <!-- Overlay fade -->
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
      <!-- Backdrop -->
      <div
        class="absolute inset-0 bg-surface-900/50 backdrop-blur-sm"
        @click="$emit('close')"
      ></div>

      <!-- Card (scale-in on mount) -->
      <div
        class="relative w-full max-w-md max-h-[90vh] overflow-y-auto
               bg-white rounded-2xl shadow-2xl border border-surface-200/60
               animate-scale-in"
      >
        <!-- Header -->
        <div class="flex items-start justify-between px-6 pt-6 pb-4">
          <div class="flex items-center gap-3">
            <div
              class="flex-shrink-0 w-10 h-10 rounded-xl flex items-center justify-center"
              :class="iconConfig[props.variant]?.bg ?? iconConfig.info.bg"
            >
              <i
                class="pi text-xl"
                :class="[
                  iconConfig[props.variant]?.icon  ?? iconConfig.info.icon,
                  iconConfig[props.variant]?.color ?? iconConfig.info.color
                ]"
              ></i>
            </div>
            <h3 class="text-lg font-heading font-bold text-surface-900 leading-snug">
              {{ title }}
            </h3>
          </div>

          <button
            @click="$emit('close')"
            class="flex-shrink-0 ml-4 p-1.5 rounded-lg text-surface-400
                   hover:text-surface-600 hover:bg-surface-100
                   transition-colors duration-150"
          >
            <i class="pi pi-times text-sm"></i>
          </button>
        </div>

        <!-- Body -->
        <div class="px-6 pb-2 text-surface-600 text-sm leading-relaxed whitespace-pre-line">
          <slot />
        </div>

        <!-- Footer -->
        <div class="px-6 py-5">
          <ReusableButton
            buttonName="Got it"
            @onClick="$emit('accept')"
          />
        </div>
      </div>

    </div>
  </Transition>
</template>
