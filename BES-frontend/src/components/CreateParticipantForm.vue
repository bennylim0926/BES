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
const showAllExisting = ref(false)
const walkinResult = ref({ created: [], existing: [], failed: [] })

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

function toggleGenre(genreName) {
  const idx = createTable.genres.indexOf(genreName)
  if (idx >= 0) {
    createTable.genres.splice(idx, 1)
  } else {
    createTable.genres.push(genreName)
  }
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

const formTouched = ref(false)

const canSubmit = computed(() => {
  if (name.value.trim() === '') return false
  if (createTable.genres.length === 0) return false
  for (const g of createTable.genres) {
    if (isTeamFormat(g) && entryModes[g] !== 'solo') {
      const tName = (teamNames[g] || '').trim()
      if (tName === '') return false
      const count = additionalMembersCountForGenre(g)
      for (let i = 0; i < count; i++) {
        if (!(teamMemberNames[g] || [])[i]?.trim()) return false
      }
    }
  }
  return true
})

const submitNewEntry = async () => {
  // Validation guard — button is disabled, but double-check in case of bypass
  if (!canSubmit.value) return

  const results = { created: [], existing: [], failed: [] }
  for (const g of createTable.genres) {
    const mode = entryModes[g] || 'team'
    const members = mode === 'solo' ? [] : (teamMemberNames[g] || []).filter(m => m.trim() !== "")
    const tName = mode === 'solo' ? '' : (teamNames[g] || '')
    try {
      const res = await addWalkinToSystem(name.value, props.event, g, selectedJudge.value, members, tName, mode)
      if (!res || !res.ok) {
        results.failed.push(g)
      } else {
        const body = await res.json().catch(() => null)
        if (body?.status === 'created') results.created.push(g)
        else results.existing.push(g)
      }
    } catch {
      results.failed.push(g)
    }
  }
  name.value = ""
  createTable.genres = []
  Object.keys(entryModes).forEach(k => { delete entryModes[k]; delete teamNames[k]; delete teamMemberNames[k] })
  selectedJudge.value = ""
  formTouched.value = false
  emit("createNewEntry")
  walkinResult.value = results
  if (results.failed.length > 0) {
    showSubmitError.value = true
  } else if (results.created.length === 0 && results.existing.length > 0) {
    showAllExisting.value = true
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
  <!-- Main walk-in form -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
    <div
      v-if="props.show"
      class="fixed inset-0 z-50 flex items-end sm:items-center justify-center pb-6 sm:p-4"
    >
      <div class="absolute inset-0 bg-black/80 backdrop-blur-sm" @click="$emit('close')" />
      <div class="card-hover relative w-full sm:max-w-md flex flex-col" style="max-height: 85vh;">
        <div class="corner-bar-tl"></div>
        <div class="corner-bar-bl"></div>

          <!-- Header -->
          <div class="flex items-center justify-between px-4 py-3 border-b border-surface-600/30 shrink-0">
            <div class="flex items-center gap-2">
              <i class="pi pi-user-plus text-content-muted text-xs"></i>
              <span class="type-body text-content-primary">{{ props.title }}</span>
              <span class="badge-neutral type-label">{{ props.event }}</span>
            </div>
          </div>

          <!-- Body -->
          <div class="flex-1 overflow-y-auto p-4 space-y-4 min-h-0">

            <!-- Stage name -->
            <div>
              <label class="type-label text-content-muted mb-1.5 block">Stage Name</label>
              <input
                v-model="name"
                type="text"
                placeholder="Enter stage name…"
                class="input-base"
                :class="formTouched && !name.trim() ? '!border-red-400/60' : ''"
                @input="formTouched = true"
                @keyup.enter="submitNewEntry"
              />
            </div>

            <!-- Division chips -->
            <div>
              <div class="flex items-center justify-between mb-2">
                <label class="type-label text-content-muted">Divisions</label>
                <span class="type-label text-content-muted">tap to select</span>
              </div>
              <div class="flex flex-wrap gap-1.5">
                <template v-for="group in groupedDivisions" :key="group.genreId">
                  <button
                    v-for="g in group.divisions"
                    :key="g.genreName"
                    type="button"
                    @click="toggleGenre(g.genreName)"
                    class="para-chip-sm px-3 py-1.5 type-label transition-all flex items-center gap-1.5"
                    :class="createTable.genres.includes(g.genreName)
                      ? 'text-accent border-[color:var(--accent-muted)] bg-[var(--accent-subtle)]'
                      : 'text-content-secondary hover:text-accent'"
                  >
                    <span
                      class="inline-block w-1.5 h-1.5 rounded-full flex-shrink-0"
                      :style="createTable.genres.includes(g.genreName)
                        ? 'background:var(--accent-color);box-shadow:0 0 5px var(--accent-muted)'
                        : 'background:rgba(255,255,255,0.2)'"
                    ></span>
                    {{ g.genreName }}
                    <span v-if="g.format" class="opacity-40 normal-case">{{ g.format }}</span>
                  </button>
                </template>
              </div>
            </div>

            <!-- Per-genre team details (inline expansion) -->
            <template v-for="g in createTable.genres" :key="g">
              <template v-if="isTeamFormat(g)">
                <div class="section-rule">
                  <span class="section-rule-label">{{ g }} · {{ selectedFormat(g) }}</span>
                  <div class="section-rule-line"></div>
                </div>

                <!-- Solo not allowed warning -->
                <div
                  v-if="!isSoloAllowed(g)"
                  class="flex items-center gap-2 px-3 py-2 para-chip"
                  style="border-left: 3px solid rgb(251 191 36); background: rgba(251,191,36,0.07);"
                >
                  <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" style="box-shadow:0 0 6px rgba(251,191,36,0.6)"></span>
                  <span class="type-label text-amber-400">Solo entries not allowed — team entry only.</span>
                </div>

                <!-- Team / Solo toggle -->
                <div v-if="isSoloAllowed(g)">
                  <label class="type-label text-content-muted mb-2 block">Entry Type</label>
                  <div class="flex border border-surface-600/60 overflow-hidden">
                    <button
                      type="button"
                      @click="entryModes[g] = 'team'"
                      class="flex-1 px-4 py-2 type-label transition-all"
                      :class="entryModes[g] === 'team'
                        ? 'bg-[var(--accent-subtle)] text-accent'
                        : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
                    >
                      Team
                    </button>
                    <button
                      type="button"
                      @click="entryModes[g] = 'solo'"
                      class="flex-1 px-4 py-2 type-label transition-all border-l border-surface-600/60"
                      :class="entryModes[g] === 'solo'
                        ? 'bg-[var(--accent-subtle)] text-accent'
                        : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
                    >
                      Solo
                    </button>
                  </div>
                  <p v-if="entryModes[g] === 'solo'" class="type-label text-content-muted mt-1.5">
                    Auditions individually. Can be grouped into a crew after auditions.
                  </p>
                </div>

                <!-- Team name + members -->
                <template v-if="entryModes[g] !== 'solo' && additionalMembersCountForGenre(g) > 0">
                  <div>
                    <label class="type-label text-content-muted mb-1.5 block">Team Name</label>
                    <input
                      v-model="teamNames[g]"
                      type="text"
                      placeholder="Enter team name…"
                      class="input-base"
                      :class="formTouched && !(teamNames[g] || '').trim() ? '!border-red-400/60' : ''"
                      @input="formTouched = true"
                    />
                  </div>
                  <div>
                    <label class="type-label text-content-muted mb-1.5 block">Team Members</label>
                    <p class="type-label text-content-muted mb-2 normal-case" style="font-size:0.65rem">
                      {{ name || 'Stage name' }} is Member 1. Enter the other {{ additionalMembersCountForGenre(g) }}.
                    </p>
                    <div class="space-y-2">
                      <input
                        v-for="i in additionalMembersCountForGenre(g)"
                        :key="i"
                        :value="(teamMemberNames[g] || [])[i - 1] || ''"
                        @input="updateMemberName(g, i - 1, $event.target.value); formTouched = true"
                        type="text"
                        :placeholder="`Member ${i + 1} stage name…`"
                        class="input-base"
                        :class="formTouched && !((teamMemberNames[g] || [])[i - 1] || '').trim() ? '!border-red-400/60' : ''"
                      />
                    </div>
                  </div>
                </template>
              </template>
            </template>

          </div>

          <!-- Footer -->
          <div class="flex gap-2 justify-end px-4 py-3 border-t border-surface-600/30 shrink-0">
            <button
              @click="$emit('close')"
              class="para-chip-sm px-4 py-2 type-label text-content-muted hover:text-content-primary transition-colors"
            >
              Cancel
            </button>
            <button
              @click="submitNewEntry"
              :disabled="!canSubmit"
              class="para-chip-sm px-4 py-2 type-label transition-all disabled:opacity-40 disabled:cursor-not-allowed text-accent border-[color:var(--accent-muted)] hover:bg-[var(--accent-subtle)]"
            >
              Add Participant
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>

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
    title="Participant Processed"
    variant="info"
    acceptLabel="Done"
    @accept="() => { showSuccess = false; $emit('close') }"
    @close="() => { showSuccess = false; $emit('close') }"
  >
    <div class="space-y-2">
      <p v-if="walkinResult.created.length > 0" class="type-body text-emerald-400">
        ✅ Added to: {{ walkinResult.created.join(', ') }}
      </p>
      <p v-if="walkinResult.existing.length > 0" class="type-body text-content-muted">
        ℹ️ Already in: {{ walkinResult.existing.join(', ') }}
      </p>
    </div>
  </ActionDoneModal>

  <ActionDoneModal
    :show="showAllExisting"
    title="Already Registered"
    variant="info"
    acceptLabel="Done"
    @accept="() => { showAllExisting = false; $emit('close') }"
    @close="() => { showAllExisting = false; $emit('close') }"
  >
    <p class="type-body text-content-muted">
      ℹ️ {{ walkinResult.existing.join(', ') }} — already registered.
    </p>
  </ActionDoneModal>

  <ActionDoneModal
    :show="showSubmitError"
    title="Submission Failed"
    variant="error"
    @accept="showSubmitError = false"
    @close="showSubmitError = false"
  >
    <div class="space-y-2">
      <p v-if="walkinResult.created.length > 0" class="type-body text-emerald-400">
        ✅ Added to: {{ walkinResult.created.join(', ') }}
      </p>
      <p v-if="walkinResult.existing.length > 0" class="type-body text-content-muted">
        ℹ️ Already in: {{ walkinResult.existing.join(', ') }}
      </p>
      <p v-if="walkinResult.failed.length > 0" class="type-body text-red-400">
        ❌ Failed: {{ walkinResult.failed.join(', ') }}
      </p>
    </div>
  </ActionDoneModal>
</template>
