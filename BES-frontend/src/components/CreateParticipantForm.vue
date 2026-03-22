<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';
import { onMounted, ref, reactive, watch } from 'vue';
import { addWalkinToSystem, fetchAllGenres, getAllJudges } from '@/utils/api';

const props = defineProps({
  show:  { type: Boolean, default: false },
  title: { type: String, default: 'New Participant' },
  event: { type: String, default: '' },
  eventGenres: { type: Array, default: null }
})

const emit = defineEmits(['createNewEntry', 'close'])

const name = ref("")
const selectedJudge = ref("")
const genreOptions = ref([])
const allJudges = ref([])
const createTable = reactive({ genres: [] })
const showError = ref(false)

const submitNewEntry = async () => {
  if (name.value == "") {
    showError.value = true
    return
  }
  for (const g of createTable.genres) {
    await addWalkinToSystem(name.value, props.event, g, selectedJudge.value)
  }
  name.value = ""
  createTable.genres = []
  emit("createNewEntry")
}

// Watch eventGenres prop so genres update when the parent fetches them
watch(() => props.eventGenres, (newGenres) => {
  if (newGenres && newGenres.length > 0) {
    genreOptions.value = newGenres.map(g => g.genreName)
  }
}, { immediate: true })

onMounted(async () => {
  // Fallback to all genres only if no event-specific genres were provided
  if (!props.eventGenres || props.eventGenres.length === 0) {
    const genres = await fetchAllGenres()
    genreOptions.value = genres.map(g => g.genreName)
  }
  const res = await getAllJudges()
  allJudges.value = ["", ...Object.values(res).map(item => item.judgeName)]
})
</script>

<template>
  <ActionDoneModal
    :show="props.show"
    :title="props.title"
    variant="info"
    @accept="submitNewEntry"
    @close="$emit('close')"
  >
    <div class="space-y-4 mt-1">
      <!-- Name field -->
      <div>
        <label class="block text-xs font-semibold text-surface-600 uppercase tracking-wider mb-1.5">
          Stage Name
        </label>
        <input
          v-model="name"
          type="text"
          placeholder="Enter stage name…"
          class="input-base"
          @keyup.enter="submitNewEntry"
        />
      </div>

      <!-- Genre checkboxes -->
      <div>
        <label class="block text-xs font-semibold text-surface-600 uppercase tracking-wider mb-2">
          Genres
        </label>
        <div class="grid grid-cols-2 gap-2">
          <label
            v-for="g in genreOptions"
            :key="g"
            class="flex items-center gap-2.5 px-3 py-2.5 rounded-xl border cursor-pointer transition-all"
            :class="createTable.genres.includes(g)
              ? 'bg-primary-50 border-primary-300 text-primary-700'
              : 'bg-white border-surface-200 text-surface-700 hover:border-surface-300'"
          >
            <input
              type="checkbox"
              :value="g"
              v-model="createTable.genres"
              class="w-4 h-4 rounded accent-primary-600"
            />
            <span class="text-sm font-medium">{{ g }}</span>
          </label>
        </div>
      </div>
    </div>
  </ActionDoneModal>

  <ActionDoneModal
    :show="showError"
    title="Name Required"
    variant="error"
    @accept="showError = false"
    @close="showError = false"
  >
    <p class="text-surface-600">Please enter a stage name before submitting.</p>
  </ActionDoneModal>
</template>
