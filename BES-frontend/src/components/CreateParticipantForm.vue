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
const genreOptions = ref([])
const allJudges = ref([])
const adminGenreMap = ref({})
const createTable = reactive({ genres: [] })
const showError = ref(false)
const showNoDivisionError = ref(false)
const showSuccess = ref(false)
const showSubmitError = ref(false)

const entryModes = reactive({})
const teamNames = reactive({})
const teamMemberNames = reactive({})

function selectedFormat(genreName) {
  const option = genreOptions.value.find(o => o.genreName === genreName)
  return option ? option.format : null
}

function isTeamFormat(genreName) {
  const fmt = selectedFormat(genreName)
  if (!fmt) return false
  return /^\d+v\d+$/i.test(fmt) && fmt.toLowerCase() !== '1v1'
}

function isSoloAllowed(genreName) {
  const option = genreOptions.value.find(o => o.genreName === genreName)
  return option ? option.soloAllowed !== false : true
}

function additionalMembersCountForGenre(genreName) {
  const fmt = selectedFormat(genreName)
  if (!fmt || entryModes[genreName] === 'solo') return 0
  const match = fmt.match(/^(\d+)v\d+$/i)
  return match ? parseInt(match[1]) - 1 : 0
}

function updateMemberName(genre, index, value) {
  const arr = teamMemberNames[genre] || []
  arr[index] = value
  teamMemberNames[genre] = [...arr]
}

watch(() => createTable.genres.slice(), (selected) => {
  for (const genreName of selected) {
    if (isTeamFormat(genreName)) {
      if (!(genreName in entryModes)) entryModes[genreName] = 'team'
      else if (!isSoloAllowed(genreName) && entryModes[genreName] === 'solo') entryModes[genreName] = 'team'
      if (!(genreName in teamNames)) teamNames[genreName] = ''
      if (!(genreName in teamMemberNames)) teamMemberNames[genreName] = []
    }
  }
  for (const key of Object.keys(entryModes)) {
    if (!selected.includes(key)) {
      delete entryModes[key]
      delete teamNames[key]
      delete teamMemberNames[key]
    }
  }
}, { deep: true })

const groupedDivisions = computed(() => {
  const groups = {}
  for (const opt of genreOptions.value) {
    const key = opt.genreId ?? 'custom'
    if (!groups[key]) {
      groups[key] = { genreId: key, label: opt.groupLabel || 'Custom', divisions: [] }
    }
    groups[key].divisions.push(opt)
  }
  return Object.values(groups)
})

const submitNewEntry = async () => {
  if (name.value.trim() === "") {
    showError.value = true
    return
  }
  if (createTable.genres.length === 0) {
    showNoDivisionError.value = true
    return
  }
  let failed = false
  for (const g of createTable.genres) {
    const mode = entryModes[g] || 'team'
    const members = mode === 'solo' ? [] : (teamMemberNames[g] || []).filter(m => m.trim() !== "")
    const tName = mode === 'solo' ? '' : (teamNames[g] || '')
    try {
      const res = await addWalkinToSystem(name.value, props.event, g, selectedJudge.value, members, tName, mode)
      if (!res || !res.ok) failed = true
    } catch {
      failed = true
    }
  }
  name.value = ""
  createTable.genres = []
  Object.keys(entryModes).forEach(k => { delete entryModes[k]; delete teamNames[k]; delete teamMemberNames[k] })
  selectedJudge.value = ""
  emit("createNewEntry")
  if (failed) {
    showSubmitError.value = true
  } else {
    showSuccess.value = true
  }
}

watch(() => props.eventGenres, (newGenres) => {
  if (newGenres && newGenres.length > 0) {
    genreOptions.value = newGenres.map(g => ({
      genreName: g.name,
      format: g.format || null,
      genreId: g.genreId ?? null,
      groupLabel: g.genreId ? (adminGenreMap.value[g.genreId] ?? 'Other') : 'Custom',
      soloAllowed: g.soloAllowed !== false,
    }))
  }
}, { immediate: true })

onMounted(async () => {
  const genres = await fetchAllGenres()
  adminGenreMap.value = Object.fromEntries((genres || []).map(g => [g.id, g.genreName]))
  if (!props.eventGenres || props.eventGenres.length === 0) {
    genreOptions.value = genres.map(g => ({ genreName: g.genreName, format: g.format || null, genreId: g.id, groupLabel: g.genreName }))
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
    acceptLabel="Add Participant"
    :scrollable="true"
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

      <!-- Genre checkboxes -- grouped by parent genre -->
      <div>
        <label class="block text-xs font-semibold text-surface-600 uppercase tracking-wider mb-2">
          Genres
        </label>
        <div class="space-y-3">
          <template v-for="group in groupedDivisions" :key="group.genreId">
            <div class="type-label text-content-muted text-xs mb-1 mt-2 first:mt-0">{{ group.label }}</div>
            <div class="grid grid-cols-1 gap-2">
              <label
                v-for="g in group.divisions"
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
          </template>
        </div>
      </div>

      <!-- Per-genre Team|Solo toggles -->
      <div v-for="g in createTable.genres" :key="g">
        <template v-if="isTeamFormat(g)">
          <label class="block text-xs font-semibold text-surface-600 uppercase tracking-wider mb-2">
            {{ g }} Entry Type
            <span class="ml-1 text-primary-400 normal-case font-normal font-source">{{ selectedFormat(g) }}</span>
          </label>
          <div class="flex rounded-xl border border-surface-600/60 overflow-hidden text-sm font-semibold mb-3">
            <button
              type="button"
              @click="entryModes[g] = 'team'"
              class="flex-1 px-4 py-2 transition-all"
              :class="entryModes[g] === 'team'
                ? 'bg-primary-600 text-white'
                : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
            >
              Team entry
            </button>
            <button
              v-if="isSoloAllowed(g)"
              type="button"
              @click="entryModes[g] = 'solo'"
              class="flex-1 px-4 py-2 border-l border-surface-600/60 transition-all"
              :class="entryModes[g] === 'solo'
                ? 'bg-primary-600 text-white'
                : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
            >
              Solo (pickup crew)
            </button>
          </div>
          <p v-if="entryModes[g] === 'solo'" class="text-xs text-content-muted mt-1.5 mb-2">
            Auditions individually. Can be grouped into a crew after auditions.
          </p>
          <p v-if="!isSoloAllowed(g)" class="text-xs text-amber-400/80 mt-1.5 mb-2">
            Solo entries not allowed for this division — team entry only.
          </p>

          <!-- Team name + members for this genre -->
          <template v-if="entryModes[g] === 'team' && additionalMembersCountForGenre(g) > 0">
            <div class="mb-2">
              <label class="block text-xs font-semibold text-surface-600 uppercase tracking-wider mb-1.5">
                {{ g }} Team Name
                <span class="ml-1 text-primary-400 normal-case font-normal font-source">{{ selectedFormat(g) }}</span>
              </label>
              <input
                v-model="teamNames[g]"
                type="text"
                placeholder="Enter team name…"
                class="input-base"
              />
            </div>
            <div>
              <label class="block text-xs font-semibold text-surface-600 uppercase tracking-wider mb-1">
                {{ g }} Team Members
              </label>
              <p class="text-xs text-surface-500 mb-2">
                Stage name is Member 1 (representative). Enter the other {{ additionalMembersCountForGenre(g) }}.
              </p>
              <div class="space-y-2">
                <input
                  v-for="i in additionalMembersCountForGenre(g)"
                  :key="i"
                  :value="(teamMemberNames[g] || [])[i - 1] || ''"
                  @input="updateMemberName(g, i - 1, $event.target.value)"
                  type="text"
                  :placeholder="`Member ${i + 1} stage name…`"
                  class="input-base"
                />
              </div>
            </div>
          </template>
        </template>
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
    <p class="type-body text-content-secondary">Please enter a stage name before submitting.</p>
  </ActionDoneModal>

  <ActionDoneModal
    :show="showNoDivisionError"
    title="No Division Selected"
    variant="error"
    @accept="showNoDivisionError = false"
    @close="showNoDivisionError = false"
  >
    <p class="type-body text-content-secondary">Please select at least one division before adding the participant.</p>
  </ActionDoneModal>

  <ActionDoneModal
    :show="showSuccess"
    title="Participant Added"
    variant="info"
    acceptLabel="Done"
    @accept="() => { showSuccess = false; $emit('close') }"
    @close="() => { showSuccess = false; $emit('close') }"
  >
    <p class="type-body text-content-secondary">The participant has been added successfully.</p>
  </ActionDoneModal>

  <ActionDoneModal
    :show="showSubmitError"
    title="Submission Failed"
    variant="error"
    @accept="showSubmitError = false"
    @close="showSubmitError = false"
  >
    <p class="type-body text-content-secondary">One or more divisions could not be registered. Please check the details and try again.</p>
  </ActionDoneModal>
</template>
