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
        class="card-hover p-4 relative w-full sm:max-w-lg max-h-[85vh] overflow-y-auto flex flex-col"
      >
        <div class="corner-bar-tl"></div>
        <div class="corner-bar-bl"></div>
        <!-- Header -->
        <div class="flex items-center justify-between flex-shrink-0 mb-3">
          <div>
            <p class="type-label text-content-muted mb-0.5">Leave Feedback</p>
            <div class="flex items-center gap-2">
              <span class="badge-neutral type-label">
                #{{ participant?.auditionNumber }}
              </span>
              <span class="type-body text-content-primary">
                {{ participant?.participantName }}
              </span>
            </div>
          </div>
          <button
            @click="emit('close')"
            class="p-1.5 para-chip-sm type-label text-content-muted hover:text-content-primary transition-colors"
          >
            <i class="pi pi-times text-sm" />
          </button>
        </div>

        <!-- Tag Groups -->
        <div class="flex-1 space-y-4 mb-4">
          <div
            v-for="(group, groupIndex) in tagGroups"
            :key="group.id"
            v-show="group.tags?.length"
          >
            <div class="section-rule mb-2">
              <span class="section-rule-label">{{ group.name }}</span>
              <div class="section-rule-line"></div>
            </div>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="tag in group.tags"
                :key="tag.id"
                @click="toggleTag(tag.id)"
                class="transition-all duration-150 active:scale-95"
                :class="selectedTagIds.has(tag.id)
                  ? 'bg-accent para-chip-sm type-label text-surface-900'
                  : 'badge-neutral type-label'"
              >
                {{ tag.label }}
              </button>
            </div>
          </div>

          <!-- Optional note -->
          <div>
            <div class="section-rule mb-2">
              <span class="section-rule-label">Optional Note</span>
              <div class="section-rule-line"></div>
            </div>
            <textarea
              v-model="note"
              rows="2"
              placeholder="Optional note for this dancer…"
              class="input-base resize-none"
            />
          </div>
        </div>

        <!-- Footer -->
        <div class="flex gap-3 flex-shrink-0">
          <button
            @click="emit('close')"
            class="flex-1 py-2 para-chip type-label text-content-muted hover:text-content-primary transition-all"
          >Skip</button>
          <button
            @click="handleSave"
            class="flex-1 py-2 bg-accent para-chip type-label text-surface-900 transition-all"
          >Save Feedback</button>
        </div>
      </div>
    </div>
  </Transition>
</template>
