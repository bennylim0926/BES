<script setup>
import { ref, watch } from 'vue'
import { updateEventAccessCode } from '@/utils/api'

const props = defineProps({
  buttonName: { type: String, required: true, default: '' },
  accessCode: { type: String, default: null }
})

const emit = defineEmits(['onClick'])

const editingCode = ref(false)
const newCode = ref('')
const saving = ref(false)
const codeDisplay = ref(props.accessCode)
const codeVisible = ref(false)

watch(() => props.accessCode, (val) => { codeDisplay.value = val })

const startEdit = (e) => {
  e.stopPropagation()
  newCode.value = codeDisplay.value || ''
  editingCode.value = true
}

const saveCode = async (e, eventId) => {
  e.stopPropagation()
  saving.value = true
  try {
    emit('updateCode', newCode.value)
    codeDisplay.value = newCode.value
    editingCode.value = false
  } finally {
    saving.value = false
  }
}

const cancelEdit = (e) => {
  e.stopPropagation()
  editingCode.value = false
}
</script>

<template>
  <div
    class="group relative bg-surface-800 rounded-2xl border border-surface-600/50
           border-l-[4px] border-l-primary-500
           transition-all duration-200 cursor-pointer overflow-hidden w-full"
    style="box-shadow: 0 1px 3px rgba(0,0,0,0.4), 0 4px 16px rgba(0,0,0,0.3);"
    :style="{}"
    @mouseenter="$el.style.boxShadow = '0 0 0 1px rgba(6,182,212,0.25), 0 4px 24px rgba(6,182,212,0.1)'"
    @mouseleave="$el.style.boxShadow = '0 1px 3px rgba(0,0,0,0.4), 0 4px 16px rgba(0,0,0,0.3)'"
    @click="emit('onClick')"
  >
    <div class="flex items-center justify-between p-5 gap-4">
      <!-- Event name + access code -->
      <div class="flex-1 min-w-0">
        <h3 class="font-heading font-bold text-base text-content-primary leading-snug line-clamp-2">
          {{ props.buttonName }}
        </h3>
        <!-- Access code (admin only) -->
        <div v-if="props.accessCode !== null" class="mt-2 flex items-center gap-2" @click.stop>
          <template v-if="!editingCode">
            <span class="text-xs text-content-muted font-medium">Code:</span>
            <span class="font-source font-bold tracking-widest text-sm text-content-secondary min-w-[2rem]">
              {{ codeVisible ? codeDisplay : '••••' }}
            </span>
            <button
              @mousedown.stop="codeVisible = true"
              @mouseup.stop="codeVisible = false"
              @mouseleave.stop="codeVisible = false"
              @touchstart.prevent.stop="codeVisible = true"
              @touchend.stop="codeVisible = false"
              @touchcancel.stop="codeVisible = false"
              class="w-5 h-5 rounded flex items-center justify-center text-content-muted hover:text-primary-400
                     hover:bg-primary-100 transition-colors duration-150 select-none touch-none"
              title="Hold to reveal code"
            >
              <i class="pi text-xs" :class="codeVisible ? 'pi-eye-slash' : 'pi-eye'"></i>
            </button>
            <button
              @click="startEdit"
              class="w-5 h-5 rounded flex items-center justify-center text-content-muted hover:text-primary-400
                     hover:bg-primary-100 transition-colors duration-150"
              title="Edit access code"
            >
              <i class="pi pi-pencil text-xs"></i>
            </button>
          </template>
          <template v-else>
            <input
              v-model="newCode"
              type="text"
              inputmode="numeric"
              maxlength="4"
              class="w-16 px-2 py-0.5 rounded border border-primary-400 bg-surface-900 font-source text-sm tracking-widest text-center text-content-primary focus:outline-none focus:ring-1 focus:ring-primary-500"
              @click.stop
            />
            <button
              @click="saveCode"
              :disabled="saving"
              class="text-xs px-2 py-0.5 rounded bg-primary-600 text-white hover:bg-primary-700 transition-colors"
            >{{ saving ? '…' : 'Save' }}</button>
            <button
              @click="cancelEdit"
              class="text-xs px-2 py-0.5 rounded border border-surface-600 text-content-secondary hover:bg-surface-700 transition-colors"
            >Cancel</button>
          </template>
        </div>
      </div>

      <!-- Chevron indicator -->
      <div
        class="icon-wrap flex-shrink-0 w-8 h-8 rounded-full bg-surface-700
               group-hover:bg-primary-100 flex items-center justify-center
               transition-colors duration-200"
      >
        <i
          class="pi pi-chevron-right text-content-muted group-hover:text-primary-400 text-xs
                 group-hover:translate-x-0.5 transition-all duration-200"
        ></i>
      </div>
    </div>
  </div>
</template>
