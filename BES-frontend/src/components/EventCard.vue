<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  buttonName:    { type: String,  required: true, default: '' },
  expanded:      { type: Boolean, default: false },  // controlled by parent
  isAdmin:       { type: Boolean, default: false },
  inDatabase:    { type: Boolean, default: true },
  isActive:      { type: Boolean, default: false },
})

const emit = defineEmits(['onDetails', 'toggle', 'onSetup', 'onSetActive'])

const isHovered = ref(false)  // desktop only

const actions = computed(() => {
  // Non-DB events only get a Setup action
  if (!props.inDatabase) {
    return [
      { key: 'onSetup', icon: 'pi-plus-circle', label: 'Setup' },
    ]
  }
  const base = [
    { key: 'onDetails', icon: 'pi-cog', label: 'Details' },
  ]
  if (!props.isActive) {
    base.push({ key: 'onSetActive', icon: 'pi-check-circle', label: 'Set Active' })
  }
  return base
})
</script>

<template>
  <div
    class="relative"
    @mouseenter="isHovered = true"
    @mouseleave="isHovered = false"
  >
    <!-- ── Header card (always visible) ── -->
    <div
      class="card-hover p-4 cursor-pointer relative"
      :class="[!props.inDatabase ? 'opacity-50' : '', props.isActive ? 'border-[color:var(--accent-muted)]' : '']"
      @click.stop="emit('toggle')"
    >
      <div class="corner-bar-tl"></div>
      <div class="flex items-center gap-3">
        <div class="type-name flex-1 line-clamp-2">
          {{ props.buttonName }}
        </div>

        <!-- Active indicator -->
        <span
          v-if="props.isActive"
          class="flex-shrink-0 w-2 h-2 rounded-full bg-accent"
          style="box-shadow: 0 0 6px var(--accent-muted)"
          title="Currently active event"
        ></span>

        <!-- Non-DB indicator -->
        <span
          v-if="!props.inDatabase"
          class="badge-neutral type-label px-2 py-0.5 text-[9px] tracking-[0.16em] flex-shrink-0"
          style="border-color: rgba(245,158,11,0.3); color: rgba(245,158,11,0.7)"
        >Not in DB</span>

        <i class="pi pi-chevron-down text-xs text-surface-500 transition-all duration-200 flex-shrink-0"
           :class="isHovered || props.expanded ? 'text-accent rotate-180' : ''"></i>
      </div>
    </div>

    <!-- ── Action panel (absolutely positioned, expands over content below) ── -->
    <Transition
      enter-active-class="transition-all duration-200 ease-out"
      enter-from-class="opacity-0 -translate-y-1"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition-all duration-150 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 -translate-y-1"
    >
      <div
        v-if="isHovered || props.expanded"
        class="absolute top-full left-0 right-0 z-20 bg-surface-800 border border-[rgba(255,255,255,0.07)] overflow-hidden shadow-[0_8px_24px_rgba(0,0,0,0.5)]"
        style="clip-path: polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)"
        @click.stop="emit('toggle')"
      >
        <div class="grid" :style="{ gridTemplateColumns: `repeat(${actions.length}, minmax(0, 1fr))` }">
          <button
            v-for="action in actions"
            :key="action.key"
            @click.stop="emit(action.key)"
            class="flex flex-col items-center gap-1.5 py-3.5 transition-all duration-150 text-content-muted hover:text-accent hover:bg-[rgba(255,255,255,0.04)]"
          >
            <i class="pi text-base text-accent" :class="action.icon"></i>
            <span class="type-label">{{ action.label }}</span>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>
