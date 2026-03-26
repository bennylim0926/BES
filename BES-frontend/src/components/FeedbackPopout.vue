<script setup>
import { ref, watch } from 'vue'
import ReusableButton from '@/components/ReusableButton.vue'

const props = defineProps({
  visible:          { type: Boolean, default: false },
  participant:      { type: Object,  default: null },
  tagGroups:        { type: Array,   default: () => [] },
  existingFeedback: { type: Object,  default: null },   // { tagIds: [], note: '' }
})

const emit = defineEmits(['close', 'save'])

const selectedTagIds = ref(new Set())
const note = ref('')

// When the popout opens or existing feedback changes, pre-populate selections
watch(() => [props.visible, props.existingFeedback], () => {
  if (props.visible) {
    selectedTagIds.value = new Set(props.existingFeedback?.tagIds ?? [])
    note.value = props.existingFeedback?.note ?? ''
  }
}, { immediate: true })

function toggleTag(tagId) {
  const next = new Set(selectedTagIds.value)
  if (next.has(tagId)) next.delete(tagId)
  else next.add(tagId)
  selectedTagIds.value = next
}

function handleSave() {
  emit('save', {
    tagIds: [...selectedTagIds.value],
    note: note.value.trim() || null,
  })
}

// Color scheme: index 0 (Strengths) → emerald, index 1 (Areas to Improve) → amber, rest → cyan
function chipColors(groupIndex, isSelected) {
  if (groupIndex === 0) {
    return isSelected
      ? 'bg-emerald-500 text-white border-emerald-500'
      : 'bg-surface-700 text-surface-300 border-surface-600 hover:border-emerald-500/60 hover:text-emerald-300'
  }
  if (groupIndex === 1) {
    return isSelected
      ? 'bg-amber-500 text-white border-amber-500'
      : 'bg-surface-700 text-surface-300 border-surface-600 hover:border-amber-500/60 hover:text-amber-300'
  }
  return isSelected
    ? 'bg-primary-600 text-white border-primary-600'
    : 'bg-surface-700 text-surface-300 border-surface-600 hover:border-primary-500/60 hover:text-primary-300'
}
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
      v-if="visible"
      class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
    >
      <!-- Backdrop -->
      <div
        class="absolute inset-0 bg-black/60 backdrop-blur-sm"
        @click="emit('close')"
      />

      <!-- Sheet / Modal -->
      <div
        class="relative w-full sm:max-w-lg max-h-[85vh] overflow-y-auto
               bg-surface-800 rounded-t-2xl sm:rounded-2xl shadow-2xl
               border border-surface-600/40 animate-scale-in flex flex-col"
      >
        <!-- Header -->
        <div class="flex items-center justify-between px-5 pt-5 pb-3 flex-shrink-0">
          <div>
            <p class="text-xs font-medium text-surface-400 uppercase tracking-wider mb-0.5">Leave Feedback</p>
            <div class="flex items-center gap-2">
              <span class="text-xs font-source text-primary-400 bg-primary-900/40 px-2 py-0.5 rounded-full">
                #{{ participant?.auditionNumber }}
              </span>
              <h3 class="text-base font-heading font-bold text-content-primary">
                {{ participant?.participantName }}
              </h3>
            </div>
          </div>
          <button
            @click="emit('close')"
            class="p-1.5 rounded-lg text-surface-400 hover:text-content-primary hover:bg-surface-700 transition-colors"
          >
            <i class="pi pi-times text-sm" />
          </button>
        </div>

        <!-- Tag Groups -->
        <div class="px-5 pb-3 flex-1 space-y-4">
          <div
            v-for="(group, groupIndex) in tagGroups"
            :key="group.id"
            v-show="group.tags?.length"
          >
            <p class="text-xs font-semibold text-surface-400 uppercase tracking-wider mb-2">
              {{ group.name }}
            </p>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="tag in group.tags"
                :key="tag.id"
                @click="toggleTag(tag.id)"
                class="px-3 py-1.5 rounded-full text-xs font-medium border transition-all duration-150 active:scale-95"
                :class="chipColors(groupIndex, selectedTagIds.has(tag.id))"
              >
                {{ tag.label }}
              </button>
            </div>
          </div>

          <!-- Optional note -->
          <div>
            <p class="text-xs font-semibold text-surface-400 uppercase tracking-wider mb-2">
              Optional Note
            </p>
            <textarea
              v-model="note"
              rows="2"
              placeholder="Optional note for this dancer…"
              class="w-full bg-surface-700 border border-surface-600 rounded-xl px-3 py-2
                     text-sm text-content-primary placeholder-surface-400 resize-none
                     focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500
                     transition-colors"
            />
          </div>
        </div>

        <!-- Footer -->
        <div class="px-5 pb-5 pt-2 flex gap-3 flex-shrink-0">
          <div class="flex-1">
            <ReusableButton buttonName="Skip" variant="outline" @onClick="emit('close')" />
          </div>
          <div class="flex-1">
            <ReusableButton buttonName="Save Feedback" variant="primary" @onClick="handleSave" />
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>
