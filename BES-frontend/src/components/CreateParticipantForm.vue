<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';
import { onMounted, ref, reactive, watch, computed } from 'vue';
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
const genreOptions = ref([])  // array of { genreName, format }
const allJudges = ref([])
const createTable = reactive({ genres: [] })
const showError = ref(false)
const teamMemberNames = ref([])
const teamName = ref("")

// First crew format among selected genres (e.g. "3v3")
const crewFormat = computed(() => {
  for (const genreName of createTable.genres) {
    const option = genreOptions.value.find(o => o.genreName === genreName)
    if (option && option.format && option.format.toLowerCase() !== '1v1') {
      return option.format
    }
  }
  return null
})

// How many additional member fields to show (crew size minus the representative)
const additionalMembersCount = computed(() => {
  if (!crewFormat.value) return 0
  const match = crewFormat.value.match(/^(\d+)v\d+$/i)
  return match ? parseInt(match[1]) - 1 : 0
})

// Keep teamMemberNames array sized to additionalMembersCount
watch(additionalMembersCount, (count) => {
  while (teamMemberNames.value.length < count) teamMemberNames.value.push("")
  teamMemberNames.value.splice(count)
})

const submitNewEntry = async () => {
  if (name.value.trim() === "") {
    showError.value = true
    return
  }
  const members = teamMemberNames.value.filter(m => m.trim() !== "")
  for (const g of createTable.genres) {
    await addWalkinToSystem(name.value, props.event, g, selectedJudge.value, members, teamName.value)
  }
  name.value = ""
  teamName.value = ""
  createTable.genres = []
  teamMemberNames.value = []
  emit("createNewEntry")
}

// Watch eventGenres prop so genres update when the parent fetches them
watch(() => props.eventGenres, (newGenres) => {
  if (newGenres && newGenres.length > 0) {
    genreOptions.value = newGenres.map(g => ({ genreName: g.genreName, format: g.format || null }))
  }
}, { immediate: true })

onMounted(async () => {
  // Fallback to all genres only if no event-specific genres were provided
  if (!props.eventGenres || props.eventGenres.length === 0) {
    const genres = await fetchAllGenres()
    genreOptions.value = genres.map(g => ({ genreName: g.genreName, format: g.format || null }))
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
      <!-- Stage name field -->
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
            :key="g.genreName"
            class="flex items-center gap-2.5 px-3 py-2.5 rounded-xl border cursor-pointer transition-all"
            :class="createTable.genres.includes(g.genreName)
              ? 'bg-primary-100 border-primary-400 text-primary-400'
              : 'bg-surface-800 border-surface-600 text-content-secondary hover:border-surface-500'"
          >
            <input
              type="checkbox"
              :value="g.genreName"
              v-model="createTable.genres"
              class="w-4 h-4 rounded accent-primary-600"
            />
            <span class="text-sm font-medium">{{ g.genreName }}</span>
            <span
              v-if="g.format"
              class="ml-auto text-xs font-source opacity-50"
            >{{ g.format }}</span>
          </label>
        </div>
      </div>

      <!-- Team name + members — shown only when a crew-format genre is selected -->
      <template v-if="additionalMembersCount > 0">
        <div>
          <label class="block text-xs font-semibold text-surface-600 uppercase tracking-wider mb-1.5">
            Team Name
            <span class="ml-1 text-primary-400 normal-case font-normal font-source">{{ crewFormat }}</span>
          </label>
          <input
            v-model="teamName"
            type="text"
            placeholder="Enter team name…"
            class="input-base"
          />
        </div>

        <div>
          <label class="block text-xs font-semibold text-surface-600 uppercase tracking-wider mb-1">
            Team Members
          </label>
          <p class="text-xs text-surface-500 mb-2">
            Stage name is Member 1 (representative). Enter the other {{ additionalMembersCount }}.
          </p>
          <div class="space-y-2">
            <input
              v-for="i in additionalMembersCount"
              :key="i"
              v-model="teamMemberNames[i - 1]"
              type="text"
              :placeholder="`Member ${i + 1} stage name…`"
              class="input-base"
            />
          </div>
        </div>
      </template>
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
