<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  visible:          { type: Boolean, default: false },
  participant:      { type: Object,  default: null },
  tagGroups:        { type: Array,   default: () => [] },
  existingFeedback: { type: Object,  default: null },
  saving:           { type: Boolean, default: false },
})


const emit = defineEmits(['close', 'save', 'change'])

const selectedTagIds = ref(new Set())
const note = ref('')
let noteTimer = null

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
  emit('change', { tagIds: [...selectedTagIds.value], note: note.value.trim() || null })
}

function onNoteInput() {
  clearTimeout(noteTimer)
  noteTimer = setTimeout(() => {
    emit('change', { tagIds: [...selectedTagIds.value], note: note.value.trim() || null })
  }, 800)
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
      class="fixed inset-0 z-50 flex items-end sm:items-center justify-center pb-6 sm:p-4"
    >
      <!-- Backdrop -->
      <div
        class="absolute inset-0 bg-black/80 backdrop-blur-sm"
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
        </div>

        <!-- Tag Groups -->
        <div class="flex-1 space-y-4 mb-4">
          <div
            v-for="group in tagGroups"
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
                class="para-chip-sm type-name-sm inline-flex items-center gap-1.5 px-2.5 py-1 transition-all duration-150"
                :class="selectedTagIds.has(tag.id)
                  ? 'text-accent border-[color:var(--accent-color)]'
                  : 'text-content-primary border-white/20 hover:border-white/40'"
                :style="selectedTagIds.has(tag.id)
                  ? { background: 'var(--accent-muted)', boxShadow: '0 0 8px var(--accent-muted)' }
                  : {}"
              >
                <i v-if="selectedTagIds.has(tag.id)" class="pi pi-check" style="font-size: 10px;" />
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
              @input="onNoteInput"
              rows="2"
              placeholder="Optional note for this dancer…"
              class="input-base resize-none"
            />
          </div>
        </div>

        <!-- Footer -->
        <div class="flex flex-col items-center gap-0.5 flex-shrink-0 pt-1 pb-1">
          <div class="h-5 flex items-center gap-2">
            <template v-if="saving">
              <i class="pi pi-spin pi-spinner text-accent/70 text-xs"></i>
              <span class="type-label text-accent/70 normal-case">Saving…</span>
            </template>
            <template v-else-if="selectedTagIds.size > 0 || note.trim()">
              <i class="pi pi-check-circle text-emerald-400 text-xs"></i>
              <span class="type-label text-emerald-400 normal-case">Feedback saved</span>
            </template>
          </div>
          <p class="type-label text-content-muted/40">Tap outside to close</p>
        </div>
      </div>
    </div>
  </Transition>
</template>
