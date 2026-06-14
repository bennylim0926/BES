<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  buttonName: { type: String, required: true, default: '' },
  accessCode: { type: String, default: null },
  expanded:   { type: Boolean, default: false },  // controlled by parent
})

const emit = defineEmits(['onDetails', 'onAudition', 'onParticipants', 'onScoreboard', 'onBattle', 'updateCode', 'toggle'])

const isHovered = ref(false)  // desktop only

const editingCode = ref(false)
const newCode     = ref('')
const saving      = ref(false)
const codeDisplay = ref(props.accessCode)
const codeVisible = ref(false)

watch(() => props.accessCode, (val) => { codeDisplay.value = val })

const startEdit  = (e) => { e.stopPropagation(); newCode.value = codeDisplay.value || ''; editingCode.value = true }
const saveCode   = async (e) => {
  e.stopPropagation(); saving.value = true
  try { emit('updateCode', newCode.value); codeDisplay.value = newCode.value; editingCode.value = false }
  finally { saving.value = false }
}
const cancelEdit = (e) => { e.stopPropagation(); editingCode.value = false }

const actions = [
  { key: 'onDetails',      icon: 'pi-cog',      label: 'Details'  },
  { key: 'onAudition',     icon: 'pi-list',      label: 'Audition' },
  { key: 'onParticipants', icon: 'pi-users',     label: 'People'   },
  { key: 'onScoreboard',   icon: 'pi-chart-bar', label: 'Score'    },
  { key: 'onBattle',       icon: 'pi-bolt',      label: 'Battle'   },
]
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

        <!-- Access code (admin only) -->
        <div v-if="props.accessCode !== null" class="flex items-center gap-1.5 flex-shrink-0" @click.stop>
          <template v-if="!editingCode">
            <span class="font-source text-xs text-content-muted tracking-widest">
              {{ codeVisible ? codeDisplay : '••••' }}
            </span>
            <button
              @mousedown.stop="codeVisible = true" @mouseup.stop="codeVisible = false"
              @mouseleave.stop="codeVisible = false" @touchstart.prevent.stop="codeVisible = true"
              @touchend.stop="codeVisible = false"
              class="text-content-muted hover:text-accent transition-colors select-none touch-none"
            ><i class="pi pi-eye text-xs"></i></button>
            <button @click.stop="startEdit" class="text-content-muted hover:text-accent transition-colors"
            ><i class="pi pi-pencil text-xs"></i></button>
          </template>
          <template v-else>
            <input v-model="newCode" type="text" inputmode="numeric" maxlength="4" @click.stop
              class="w-14 px-1.5 py-0.5 border border-[color:var(--accent-muted)] bg-surface-900 font-source text-xs tracking-widest text-center text-content-primary focus:outline-none"
              style="clip-path: polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
            />
            <button @click.stop="saveCode" :disabled="saving"
              class="text-xs px-1.5 py-0.5 bg-accent text-surface-900 transition-colors"
              style="clip-path: polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
            >{{ saving ? '…' : 'Save' }}</button>
            <button @click.stop="cancelEdit"
              class="text-xs px-1.5 py-0.5 border border-surface-600 text-content-secondary hover:bg-surface-700 transition-colors"
              style="clip-path: polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)"
            >✕</button>
          </template>
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
        <div class="grid grid-cols-5">
          <button
            v-for="action in actions"
            :key="action.key"
            @click.stop="emit(action.key)"
            class="flex flex-col items-center gap-1.5 py-3.5 text-content-muted
                   hover:text-accent hover:bg-[rgba(255,255,255,0.04)] transition-all duration-150"
          >
            <i class="pi text-base text-accent" :class="action.icon"></i>
            <span class="type-label">{{ action.label }}</span>
          </button>
        </div>
      </div>
    </Transition>
  </div>
</template>
