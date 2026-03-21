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
    // Note: EventCard only gets name, not id. Code update is triggered from parent via eventId.
    // For now, emit an event to parent so parent can call updateEventAccessCode.
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
    class="group relative bg-white rounded-2xl border border-surface-200/80 shadow-sm
           hover:shadow-md hover:border-primary-200
           border-l-[4px] border-l-primary-500
           transition-all duration-200 cursor-pointer overflow-hidden w-full"
    @click="emit('onClick')"
  >
    <div class="flex items-center justify-between p-5 gap-4">
      <!-- Event name + access code -->
      <div class="flex-1 min-w-0">
        <h3 class="font-heading font-bold text-base text-surface-900 leading-snug line-clamp-2">
          {{ props.buttonName }}
        </h3>
        <!-- Access code (admin only) -->
        <div v-if="props.accessCode !== null" class="mt-2 flex items-center gap-2" @click.stop>
          <template v-if="!editingCode">
            <span class="text-xs text-surface-500 font-medium">Code:</span>
            <span class="font-source font-bold tracking-widest text-sm text-surface-800 min-w-[2rem]">
              {{ codeVisible ? codeDisplay : '••••' }}
            </span>
            <button
              @click.stop="codeVisible = !codeVisible"
              class="w-5 h-5 rounded flex items-center justify-center text-surface-400 hover:text-primary-600
                     hover:bg-primary-50 transition-colors duration-150"
              :title="codeVisible ? 'Hide code' : 'Show code'"
            >
              <i class="pi text-xs" :class="codeVisible ? 'pi-eye-slash' : 'pi-eye'"></i>
            </button>
            <button
              @click="startEdit"
              class="w-5 h-5 rounded flex items-center justify-center text-surface-400 hover:text-primary-600
                     hover:bg-primary-50 transition-colors duration-150"
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
              class="w-16 px-2 py-0.5 rounded border border-primary-400 font-source text-sm tracking-widest text-center focus:outline-none focus:ring-1 focus:ring-primary-500"
              @click.stop
            />
            <button
              @click="saveCode"
              :disabled="saving"
              class="text-xs px-2 py-0.5 rounded bg-primary-600 text-white hover:bg-primary-700 transition-colors"
            >{{ saving ? '…' : 'Save' }}</button>
            <button
              @click="cancelEdit"
              class="text-xs px-2 py-0.5 rounded border border-surface-200 text-surface-600 hover:bg-surface-50 transition-colors"
            >Cancel</button>
          </template>
        </div>
      </div>

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
