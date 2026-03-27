<script setup>
import { ref, watch } from 'vue'
import { getScoringCriteria, addScoringCriteria, deleteScoringCriteria } from '@/utils/api'

const props = defineProps({
  eventName: { type: String, required: true },
  genreName: { type: String, default: null },  // null = event-level panel
})

const criteria = ref([])
const loading = ref(false)
const saving = ref(false)

const newName = ref('')
const newMaxScore = ref(10)
const newWeight = ref(null)
const showAddForm = ref(false)

const load = async () => {
  if (!props.eventName) return
  loading.value = true
  criteria.value = await getScoringCriteria(props.eventName, props.genreName)
  loading.value = false
}

watch(() => [props.eventName, props.genreName], load, { immediate: true })

const add = async () => {
  if (!newName.value.trim()) return
  saving.value = true
  const result = await addScoringCriteria(props.eventName, {
    genreName: props.genreName || null,
    name: newName.value.trim(),
    maxScore: Number(newMaxScore.value) || 10,
    weight: newWeight.value ? Number(newWeight.value) : null,
    displayOrder: criteria.value.length,
  })
  if (result) {
    criteria.value.push(result)
    newName.value = ''
    newMaxScore.value = 10
    newWeight.value = null
    showAddForm.value = false
  }
  saving.value = false
}

const remove = async (id) => {
  const ok = await deleteScoringCriteria(props.eventName, id)
  if (ok) criteria.value = criteria.value.filter(c => c.id !== id)
}
</script>

<template>
  <div class="mt-3">
    <!-- Criteria list -->
    <div v-if="loading" class="text-xs text-content-muted py-2">Loading criteria…</div>

    <div v-else-if="criteria.length === 0 && !showAddForm" class="text-xs text-content-muted italic py-1">
      No criteria defined — judges use a single 0–10 score.
    </div>

    <div v-else class="space-y-1.5 mb-3">
      <div
        v-for="c in criteria"
        :key="c.id"
        class="flex items-center justify-between px-3 py-2 rounded-xl bg-surface-700/60 border border-surface-600/50"
      >
        <div class="flex items-center gap-2 flex-1 min-w-0">
          <span class="text-sm font-semibold text-content-primary truncate">{{ c.name }}</span>
          <span class="text-xs text-content-muted shrink-0">max {{ c.maxScore }}</span>
          <span v-if="c.weight != null" class="text-xs px-1.5 py-0.5 rounded bg-primary-500/15 text-primary-400 border border-primary-500/20 shrink-0">
            ×{{ c.weight }}
          </span>
        </div>
        <button
          @click="remove(c.id)"
          class="ml-2 p-1 rounded-lg text-surface-400 hover:text-red-400 hover:bg-red-500/10 transition-colors shrink-0"
          title="Remove criterion"
        >
          <i class="pi pi-times text-xs" />
        </button>
      </div>
    </div>

    <!-- Add form -->
    <div v-if="showAddForm" class="bg-surface-700/40 border border-surface-600/50 rounded-xl p-3 mb-3 space-y-2">
      <input
        v-model="newName"
        placeholder="Criterion name (e.g. Musicality)"
        class="w-full bg-surface-700 border border-surface-600 rounded-lg px-3 py-2 text-sm text-content-primary
               placeholder-surface-400 focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors"
        @keydown.enter="add"
      />
      <div class="grid grid-cols-2 gap-2">
        <div>
          <label class="text-xs text-content-muted mb-1 block">Max Score</label>
          <input
            v-model="newMaxScore"
            type="number" min="1" max="100" step="1"
            class="w-full bg-surface-700 border border-surface-600 rounded-lg px-3 py-1.5 text-sm text-content-primary
                   focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors"
          />
        </div>
        <div>
          <label class="text-xs text-content-muted mb-1 block">Weight <span class="text-surface-500">(optional)</span></label>
          <input
            v-model="newWeight"
            type="number" min="0" step="0.5" placeholder="—"
            class="w-full bg-surface-700 border border-surface-600 rounded-lg px-3 py-1.5 text-sm text-content-primary
                   placeholder-surface-500 focus:outline-none focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500 transition-colors"
          />
        </div>
      </div>
      <div class="flex gap-2">
        <button
          @click="add"
          :disabled="saving || !newName.trim()"
          class="flex-1 py-1.5 rounded-lg bg-primary-600 text-white text-xs font-semibold
                 hover:bg-primary-500 active:bg-primary-700 disabled:opacity-40 disabled:cursor-not-allowed transition-all"
        >
          <i v-if="saving" class="pi pi-spin pi-spinner mr-1" />
          Add
        </button>
        <button
          @click="showAddForm = false; newName = ''; newMaxScore = 10; newWeight = null"
          class="px-3 py-1.5 rounded-lg border border-surface-600 bg-surface-700 text-xs text-content-muted
                 hover:border-surface-500 hover:bg-surface-600 transition-all"
        >
          Cancel
        </button>
      </div>
    </div>

    <!-- Add button -->
    <button
      v-if="!showAddForm"
      @click="showAddForm = true"
      class="flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-dashed border-surface-600
             text-xs text-content-muted hover:border-primary-500/50 hover:text-primary-400 transition-all duration-150"
    >
      <i class="pi pi-plus text-xs" />
      Add Criterion
    </button>
  </div>
</template>
