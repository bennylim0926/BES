<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';
import { onMounted, ref, reactive, watch, computed } from 'vue';
import { addWalkinToSystem, getAllJudges } from '@/utils/api';

const props = defineProps({
  show:  { type: Boolean, default: false },
  title: { type: String, default: 'New Participant' },
  event: { type: String, default: '' },
  eventCategories: { type: Array, default: null }
})

const emit = defineEmits(['createNewEntry', 'close'])

const name = ref("")
const selectedJudge = ref("")
const categoryOptions = ref([])
const allJudges = ref([])
const adminCategoryMap = ref({})
const createTable = reactive({ categories: [] })
const showError = ref(false)
const showNoDivisionError = ref(false)
const showSuccess = ref(false)
const showSubmitError = ref(false)
const showAllExisting = ref(false)
const walkinResult = ref({ created: [], existing: [], failed: [] })

const entryModes = reactive({})
const teamNames = reactive({})
const teamMemberNames = reactive({})

function selectedFormat(categoryName) {
  const option = categoryOptions.value.find(o => o.categoryName === categoryName)
  return option ? option.format : null
}

function isTeamFormat(categoryName) {
  const fmt = selectedFormat(categoryName)
  if (!fmt) return false
  return /^\d+v\d+$/i.test(fmt) && fmt.toLowerCase() !== '1v1'
}

function isSoloAllowed(categoryName) {
  const option = categoryOptions.value.find(o => o.categoryName === categoryName)
  return option ? option.soloAllowed !== false : true
}

function additionalMembersCountForCategory(categoryName) {
  const fmt = selectedFormat(categoryName)
  if (!fmt || entryModes[categoryName] === 'solo') return 0
  const match = fmt.match(/^(\d+)v\d+$/i)
  return match ? parseInt(match[1]) - 1 : 0
}

function updateMemberName(category, index, value) {
  const arr = teamMemberNames[category] || []
  arr[index] = value
  teamMemberNames[category] = [...arr]
}

function toggleCategory(categoryName) {
  const idx = createTable.categories.indexOf(categoryName)
  if (idx >= 0) {
    createTable.categories.splice(idx, 1)
  } else {
    createTable.categories.push(categoryName)
  }
}

watch(() => createTable.categories.slice(), (selected) => {
  for (const categoryName of selected) {
    if (isTeamFormat(categoryName)) {
      if (!(categoryName in entryModes)) entryModes[categoryName] = 'team'
      else if (!isSoloAllowed(categoryName) && entryModes[categoryName] === 'solo') entryModes[categoryName] = 'team'
      if (!(categoryName in teamNames)) teamNames[categoryName] = ''
      if (!(categoryName in teamMemberNames)) teamMemberNames[categoryName] = []
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
  for (const opt of categoryOptions.value) {
    const key = opt.categoryId ?? 'custom'
    if (!groups[key]) {
      groups[key] = { categoryId: key, label: opt.groupLabel || 'Custom', divisions: [] }
    }
    groups[key].divisions.push(opt)
  }
  return Object.values(groups)
})

const formTouched = ref(false)

const canSubmit = computed(() => {
  if (name.value.trim() === '') return false
  if (createTable.categories.length === 0) return false
  for (const c of createTable.categories) {
    if (isTeamFormat(c) && entryModes[c] !== 'solo') {
      const tName = (teamNames[c] || '').trim()
      if (tName === '') return false
      const count = additionalMembersCountForCategory(c)
      for (let i = 0; i < count; i++) {
        if (!(teamMemberNames[c] || [])[i]?.trim()) return false
      }
    }
  }
  return true
})

const submitNewEntry = async () => {
  // Validation guard — button is disabled, but double-check in case of bypass
  if (!canSubmit.value) return

  const results = { created: [], existing: [], failed: [] }
  for (const c of createTable.categories) {
    const mode = entryModes[c] || 'team'
    const members = mode === 'solo' ? [] : (teamMemberNames[c] || []).filter(m => m.trim() !== "")
    const tName = mode === 'solo' ? '' : (teamNames[c] || '')
    try {
      const res = await addWalkinToSystem(name.value, props.event, c, selectedJudge.value, members, tName, mode)
      if (!res || !res.ok) {
        results.failed.push(c)
      } else {
        const body = await res.json().catch(() => null)
        if (body?.status === 'created') results.created.push(c)
        else results.existing.push(c)
      }
    } catch {
      results.failed.push(c)
    }
  }
  name.value = ""
  createTable.categories = []
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

watch(() => props.eventCategories, (newCategories) => {
  if (newCategories && newCategories.length > 0) {
    categoryOptions.value = newCategories.map(c => ({
      categoryName: c.name,
      format: c.format || null,
      categoryId: c.categoryId ?? null,
      groupLabel: c.categoryId ? (adminCategoryMap.value[c.categoryId] ?? 'Other') : 'Custom',
      soloAllowed: c.soloAllowed !== false,
    }))
  }
}, { immediate: true })

onMounted(async () => {
  if (!props.eventCategories || props.eventCategories.length === 0) {
    categoryOptions.value = []
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
              <span class="badge-neutral" style="text-transform:none;letter-spacing:0.02em;">{{ props.event }}</span>
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
                <template v-for="group in groupedDivisions" :key="group.categoryId">
                  <button
                    v-for="c in group.divisions"
                    :key="c.categoryName"
                    type="button"
                    @click="toggleCategory(c.categoryName)"
                    class="para-chip-sm px-3 py-1.5 type-name-sm transition-all flex items-center gap-1.5"
                    :class="createTable.categories.includes(c.categoryName)
                      ? 'text-accent border-[color:var(--accent-muted)] bg-[var(--accent-subtle)]'
                      : 'text-content-secondary hover:text-accent'"
                  >
                    <span
                      class="inline-block w-1.5 h-1.5 rounded-full flex-shrink-0"
                      :style="createTable.categories.includes(c.categoryName)
                        ? 'background:var(--accent-color);box-shadow:0 0 5px var(--accent-muted)'
                        : 'background:rgba(255,255,255,0.2)'"
                    ></span>
                    {{ c.categoryName }}
                    <span v-if="c.format" class="opacity-50" style="font-size:11px;">· {{ c.format }}</span>
                  </button>
                </template>
              </div>
            </div>

            <!-- Per-category team details (inline expansion) -->
            <template v-for="c in createTable.categories" :key="c">
              <template v-if="isTeamFormat(c)">
                <div class="section-rule">
                  <span class="type-name text-content-secondary" style="font-size:14px;">{{ c }} <span class="type-label text-content-muted">· {{ selectedFormat(c) }}</span></span>
                  <div class="section-rule-line"></div>
                </div>

                <!-- Solo not allowed warning -->
                <div
                  v-if="!isSoloAllowed(c)"
                  class="flex items-center gap-2 px-3 py-2 para-chip"
                  style="border-left: 3px solid rgb(251 191 36); background: rgba(251,191,36,0.07);"
                >
                  <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" style="box-shadow:0 0 6px rgba(251,191,36,0.6)"></span>
                  <span class="type-prose" style="color:rgb(251 191 36);">Solo entries not allowed — team entry only.</span>
                </div>

                <!-- Team / Solo toggle -->
                <div v-if="isSoloAllowed(c)">
                  <label class="type-label text-content-muted mb-2 block">Entry Type</label>
                  <div class="flex border border-surface-600/60 overflow-hidden">
                    <button
                      type="button"
                      @click="entryModes[c] = 'team'"
                      class="flex-1 px-4 py-2 type-label transition-all"
                      :class="entryModes[c] === 'team'
                        ? 'bg-[var(--accent-subtle)] text-accent'
                        : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
                    >
                      Team
                    </button>
                    <button
                      type="button"
                      @click="entryModes[c] = 'solo'"
                      class="flex-1 px-4 py-2 type-label transition-all border-l border-surface-600/60"
                      :class="entryModes[c] === 'solo'
                        ? 'bg-[var(--accent-subtle)] text-accent'
                        : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
                    >
                      Solo
                    </button>
                  </div>
                  <p v-if="entryModes[c] === 'solo'" class="type-prose mt-1.5">
                    Auditions individually. Can be grouped into a crew after auditions.
                  </p>
                </div>

                <!-- Team name + members -->
                <template v-if="entryModes[c] !== 'solo' && additionalMembersCountForCategory(c) > 0">
                  <div>
                    <label class="type-label text-content-muted mb-1.5 block">Team Name</label>
                    <input
                      v-model="teamNames[c]"
                      type="text"
                      placeholder="Enter team name…"
                      class="input-base"
                      :class="formTouched && !(teamNames[c] || '').trim() ? '!border-red-400/60' : ''"
                      @input="formTouched = true"
                    />
                  </div>
                  <div>
                    <label class="type-label text-content-muted mb-1.5 block">Team Members</label>
                    <p class="type-prose mb-2">
                      {{ name || 'Stage name' }} is Member 1. Enter the other {{ additionalMembersCountForCategory(c) }}.
                    </p>
                    <div class="space-y-2">
                      <input
                        v-for="i in additionalMembersCountForCategory(c)"
                        :key="i"
                        :value="(teamMemberNames[c] || [])[i - 1] || ''"
                        @input="updateMemberName(c, i - 1, $event.target.value); formTouched = true"
                        type="text"
                        :placeholder="`Member ${i + 1} stage name…`"
                        class="input-base"
                        :class="formTouched && !((teamMemberNames[c] || [])[i - 1] || '').trim() ? '!border-red-400/60' : ''"
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
