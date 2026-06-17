<script setup>
import { ref, computed } from 'vue'

const props = defineProps({
  buttonName: { type: String, required: true, default: '' },
  expanded:   { type: Boolean, default: false },  // controlled by parent
  isAdmin:    { type: Boolean, default: false },
})

const emit = defineEmits(['onDetails', 'onAudition', 'onParticipants', 'onScoreboard', 'onBattle', 'toggle', 'onDelete'])

const isHovered = ref(false)  // desktop only

const actions = computed(() => {
  const base = [
    { key: 'onDetails',      icon: 'pi-cog',      label: 'Details'  },
    { key: 'onAudition',     icon: 'pi-list',      label: 'Audition' },
    { key: 'onParticipants', icon: 'pi-users',     label: 'People'   },
    { key: 'onScoreboard',   icon: 'pi-chart-bar', label: 'Score'    },
    { key: 'onBattle',       icon: 'pi-bolt',      label: 'Battle'   },
  ]
  if (props.isAdmin) {
    base.push({ key: 'onDelete', icon: 'pi-trash', label: 'Delete' })
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
      @click.stop="emit('toggle')"
    >
      <div class="corner-bar-tl"></div>
      <div class="flex items-center gap-3">
        <div class="type-name flex-1 line-clamp-2">
          {{ props.buttonName }}
        </div>

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
        <div :class="['grid', props.isAdmin ? 'grid-cols-6' : 'grid-cols-5']">
          <button
            v-for="action in actions"
            :key="action.key"
            @click.stop="emit(action.key)"
            class="flex flex-col items-center gap-1.5 py-3.5 transition-all duration-150"
            :class="action.key === 'onDelete'
              ? 'text-content-muted hover:text-red-400 hover:bg-red-950/30'
              : 'text-content-muted hover:text-accent hover:bg-[rgba(255,255,255,0.04)]'"
          >
            <i class="pi text-base" :class="[action.icon, action.key === 'onDelete' ? '' : 'text-accent']"></i>
            <span class="type-label">{{ action.label }}</span>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>
