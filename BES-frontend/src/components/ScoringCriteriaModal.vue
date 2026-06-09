<script setup>
import { ref, computed, watch } from 'vue'
import { getScoringCriteriaStrict, addScoringCriteria, updateScoringCriteria, deleteScoringCriteria, deleteAllCriteriaForGenre } from '@/utils/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  eventName:  { type: String, required: true },
  genres:     { type: Array, default: () => [] },   // array of genre name strings
})
const emit = defineEmits(['update:modelValue'])

const close = () => emit('update:modelValue', false)

// ── Tabs ──────────────────────────────────────────────────────────────────────
// 'event-level' is the fallback default; one tab per genre
const allTabs = computed(() => ['event-level', ...props.genres])
const activeTab = ref('event-level')

// criteriaMap[tab] = array of { id, name, weight, displayOrder }
const criteriaMap = ref({})
const loading = ref(false)

const activeCriteria = computed(() => criteriaMap.value[activeTab.value] ?? [])

const genreNameForTab = (tab) => tab === 'event-level' ? null : tab

const loadTab = async (tab) => {
  loading.value = true
  const genre = genreNameForTab(tab)
  criteriaMap.value[tab] = await getScoringCriteriaStrict(props.eventName, genre)
  loading.value = false
}

const loadAll = async () => {
  await Promise.all(allTabs.value.map(t => loadTab(t)))
}

watch(() => props.modelValue, (open) => {
  if (open) {
    activeTab.value = props.genres[0] ?? 'event-level'
    loadAll()
  }
}, { immediate: false })

// ── Add ───────────────────────────────────────────────────────────────────────
const newName   = ref('')
const newWeight = ref('')
const saving    = ref(false)
const showAdd   = ref(false)

const add = async () => {
  if (!newName.value.trim()) return
  saving.value = true
  const result = await addScoringCriteria(props.eventName, {
    genreName:    genreNameForTab(activeTab.value),
    name:         newName.value.trim(),
    weight:       newWeight.value !== '' ? Number(newWeight.value) : null,
    displayOrder: activeCriteria.value.length,
  })
  if (result) {
    criteriaMap.value[activeTab.value] = [...activeCriteria.value, result]
    newName.value   = ''
    newWeight.value = ''
    showAdd.value   = false
  }
  saving.value = false
}

const cancelAdd = () => {
  showAdd.value   = false
  newName.value   = ''
  newWeight.value = ''
}

// ── Edit ──────────────────────────────────────────────────────────────────────
const editingId     = ref(null)
const editName      = ref('')
const editWeight    = ref('')
const editSaving    = ref(false)

const startEdit = (c) => {
  editingId.value  = c.id
  editName.value   = c.name
  editWeight.value = c.weight != null ? String(c.weight) : ''
}

const cancelEdit = () => {
  editingId.value = null
}

const saveEdit = async () => {
  editSaving.value = true
  const updated = await updateScoringCriteria(props.eventName, editingId.value, {
    name:   editName.value.trim() || undefined,
    weight: editWeight.value !== '' ? Number(editWeight.value) : null,
  })
  if (updated) {
    criteriaMap.value[activeTab.value] = activeCriteria.value.map(c =>
      c.id === editingId.value ? updated : c
    )
    editingId.value = null
  }
  editSaving.value = false
}

// ── Delete ────────────────────────────────────────────────────────────────────
const remove = async (id) => {
  const ok = await deleteScoringCriteria(props.eventName, id)
  if (ok) criteriaMap.value[activeTab.value] = activeCriteria.value.filter(c => c.id !== id)
}

// ── Apply to all genres ───────────────────────────────────────────────────────
const applyingAll = ref(false)
const appliedAll = ref(false)

const applyToAllGenres = async () => {
  if (!activeCriteria.value.length) return
  applyingAll.value = true
  const source = [...activeCriteria.value]
  const targets = allTabs.value.filter(t => t !== activeTab.value)

  await Promise.all(targets.map(async (tab) => {
    const genre = genreNameForTab(tab)
    await deleteAllCriteriaForGenre(props.eventName, genre)
    const added = []
    for (let i = 0; i < source.length; i++) {
      const r = await addScoringCriteria(props.eventName, {
        genreName:    genre,
        name:         source[i].name,
        weight:       source[i].weight,
        displayOrder: i,
      })
      if (r) added.push(r)
    }
    criteriaMap.value[tab] = added
  }))

  applyingAll.value = false
  appliedAll.value = true
  setTimeout(() => { appliedAll.value = false }, 1500)
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div
        v-if="modelValue"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        @click.self="close"
      >
        <!-- Backdrop -->
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="close" />

        <!-- Panel -->
        <div class="card-hover p-6 relative w-full max-w-xl flex flex-col max-h-[85vh]">
          <div class="corner-bar-tl"></div>
          <div class="corner-bar-bl"></div>

          <!-- Header -->
          <div class="flex items-center justify-between flex-shrink-0 mb-4">
            <div>
              <p class="type-page-title" style="font-size: 18px;">Scoring Criteria</p>
              <p class="type-label text-content-muted mt-0.5">Define what judges score on. Leave empty for a single 0–10 score.</p>
            </div>
            <button
              @click="close"
              class="p-1.5 para-chip-sm type-label text-content-muted hover:text-content-primary transition-colors"
            >
              <i class="pi pi-times text-sm" />
            </button>
          </div>

          <!-- Genre tabs -->
          <div class="flex gap-1 px-5 pt-4 pb-0 overflow-x-auto flex-shrink-0" style="scrollbar-width: none;">
            <button
              v-for="tab in allTabs"
              :key="tab"
              @click="activeTab = tab"
              class="flex-shrink-0 flex items-center gap-1.5 px-3 py-1.5 para-chip-sm type-label transition-all duration-150"
              :class="activeTab === tab
                ? 'bg-accent text-surface-900 border-accent'
                : 'text-content-muted hover:text-content-primary'"
            >
              <i v-if="tab === 'event-level'" class="pi pi-globe text-[10px]" />
              {{ tab === 'event-level' ? 'Event Default' : tab }}
              <span
                v-if="(criteriaMap[tab] ?? []).length > 0"
                class="px-1 rounded text-[10px] font-bold"
                :class="activeTab === tab ? 'bg-white/20 text-white' : 'bg-surface-600 text-surface-300'"
              >{{ (criteriaMap[tab] ?? []).length }}</span>
            </button>
          </div>

          <!-- Tab description -->
          <div class="px-5 pt-3 pb-0 flex-shrink-0">
            <p v-if="activeTab === 'event-level'" class="text-xs text-content-muted italic">
              These criteria apply to any genre that doesn't have its own specific criteria set.
            </p>
            <p v-else class="text-xs text-content-muted italic">
              Criteria specific to <span class="text-primary-400 font-semibold">{{ activeTab }}</span>. Overrides the Event Default.
            </p>
          </div>

          <!-- Criteria list (scrollable) -->
          <div class="flex-1 overflow-y-auto px-5 py-4 space-y-2 min-h-0">
            <div v-if="loading" class="text-xs text-content-muted py-4 text-center">Loading…</div>

            <div
              v-else-if="activeCriteria.length === 0 && !showAdd"
              class="text-xs text-content-muted italic py-4 text-center"
            >
              No criteria — judges use a single 0–10 score.
            </div>

            <template v-else>
              <div
                v-for="c in activeCriteria"
                :key="c.id"
                class="card-hover p-3 relative"
              >
                <!-- View row -->
                <div
                  v-if="editingId !== c.id"
                  class="flex items-center justify-between"
                >
                  <div class="flex items-center gap-2 flex-1 min-w-0">
                    <span class="text-sm font-semibold text-content-primary truncate">{{ c.name }}</span>
                    <span v-if="c.weight != null" class="badge-neutral type-label shrink-0">
                      ×{{ c.weight }}
                    </span>
                  </div>
                  <div class="flex items-center gap-1 ml-2 shrink-0">
                    <button
                      @click="startEdit(c)"
                      class="p-1.5 para-chip-sm type-label text-content-muted hover:text-accent transition-colors"
                      title="Edit"
                    >
                      <i class="pi pi-pencil text-xs" />
                    </button>
                    <button
                      @click="remove(c.id)"
                      class="p-1.5 para-chip-sm type-label text-content-muted hover:text-red-400 transition-colors"
                      title="Delete"
                    >
                      <i class="pi pi-times text-xs" />
                    </button>
                  </div>
                </div>

                <!-- Edit row -->
                <div v-else class="space-y-2">
                  <div class="flex gap-2">
                    <input
                      v-model="editName"
                      placeholder="Criterion name"
                      class="flex-1 min-w-0 input-base"
                      @keydown.enter="saveEdit"
                      @keydown.escape="cancelEdit"
                    />
                    <input
                      v-model="editWeight"
                      type="number" min="0" step="0.5" placeholder="Weight"
                      class="input-base"
                      style="width: 6rem; flex-shrink: 0"
                    />
                  </div>
                  <div class="flex gap-2">
                    <button
                      @click="saveEdit"
                      :disabled="editSaving || !editName.trim()"
                      class="flex-1 py-1.5 bg-accent para-chip type-label text-surface-900 disabled:opacity-40 disabled:cursor-not-allowed transition-all"
                    >
                      <i v-if="editSaving" class="pi pi-spin pi-spinner mr-1" />
                      Save
                    </button>
                    <button
                      @click="cancelEdit"
                      class="px-3 py-1.5 para-chip type-label border-accent text-content-muted hover:text-content-primary transition-all"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              </div>
            </template>

            <!-- Add form -->
            <div v-if="showAdd" class="card-hover p-3 space-y-2">
              <div class="flex gap-2">
                <input
                  v-model="newName"
                  placeholder="Criterion name (e.g. Musicality)"
                  class="flex-1 min-w-0 input-base"
                  @keydown.enter="add"
                  @keydown.escape="cancelAdd"
                />
                <input
                  v-model="newWeight"
                  type="number" min="0" step="0.5" placeholder="Weight"
                  class="input-base"
                  style="width: 6rem; flex-shrink: 0"
                />
              </div>
              <div class="flex gap-2">
                <button
                  @click="add"
                  :disabled="saving || !newName.trim()"
                  class="flex-1 py-1.5 bg-accent para-chip type-label text-surface-900 disabled:opacity-40 disabled:cursor-not-allowed transition-all"
                >
                  <i v-if="saving" class="pi pi-spin pi-spinner mr-1" />
                  Add
                </button>
                <button
                  @click="cancelAdd"
                  class="px-3 py-1.5 para-chip type-label border-accent text-content-muted hover:text-content-primary transition-all"
                >
                  Cancel
                </button>
              </div>
            </div>
          </div>

          <!-- Footer actions -->
          <div class="flex items-center gap-2 flex-shrink-0 pt-4 border-t border-[rgba(255,255,255,0.07)]">
            <button
              v-if="!showAdd"
              @click="showAdd = true"
              class="flex items-center gap-1.5 px-3 py-1.5 para-chip-sm type-label border-accent text-content-muted hover:text-accent transition-all duration-150"
            >
              <i class="pi pi-plus text-xs" />
              Add Criterion
            </button>

            <div class="flex-1" />

            <!-- Copy to all genres (not shown on Event Default tab) -->
            <button
              v-if="activeTab !== 'event-level' && genres.length > 1"
              @click="applyToAllGenres"
              :disabled="applyingAll || activeCriteria.length === 0"
              class="flex items-center gap-1.5 px-3 py-1.5 para-chip-sm type-label border-accent
                     disabled:opacity-40 disabled:cursor-not-allowed transition-all duration-150"
              :class="appliedAll ? 'text-emerald-400' : 'text-content-muted hover:text-accent'"
              title="Replaces criteria in all other genres with these"
            >
              <i v-if="applyingAll" class="pi pi-spin pi-spinner text-xs" />
              <i v-else-if="appliedAll" class="pi pi-check text-xs" />
              <i v-else class="pi pi-copy text-xs" />
              {{ appliedAll ? 'Copied!' : 'Copy to all genres' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease;
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}
</style>
