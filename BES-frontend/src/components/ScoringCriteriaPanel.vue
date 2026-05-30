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
        class="card-hover p-3 relative"
      >
        <div class="corner-bar-tl"></div>
        <div class="flex items-center gap-2 flex-1 min-w-0">
          <span class="text-sm font-semibold text-content-primary truncate">{{ c.name }}</span>
          <span class="text-xs text-content-muted shrink-0">max {{ c.maxScore }}</span>
          <span v-if="c.weight != null" class="badge-neutral type-label shrink-0">
            ×{{ c.weight }}
          </span>
        </div>
        <button
          @click="remove(c.id)"
          class="ml-2 p-1 para-chip-sm type-label text-content-muted hover:text-red-400 transition-colors shrink-0"
          title="Remove criterion"
        >
          <i class="pi pi-times text-xs" />
        </button>
      </div>
    </div>

    <!-- Add form -->
    <div v-if="showAddForm" class="card-hover p-3 mb-3 space-y-2">
      <input
        v-model="newName"
        placeholder="Criterion name (e.g. Musicality)"
        class="w-full input-base"
        @keydown.enter="add"
      />
      <div class="grid grid-cols-2 gap-2">
        <div>
          <label class="text-xs text-content-muted mb-1 block">Max Score</label>
          <input
            v-model="newMaxScore"
            type="number" min="1" max="100" step="1"
            class="w-full input-base"
          />
        </div>
        <div>
          <label class="text-xs text-content-muted mb-1 block">Weight <span class="text-surface-500">(optional)</span></label>
          <input
            v-model="newWeight"
            type="number" min="0" step="0.5" placeholder="—"
            class="w-full input-base"
          />
        </div>
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
          @click="showAddForm = false; newName = ''; newMaxScore = 10; newWeight = null"
          class="px-3 py-1.5 para-chip type-label border-accent text-content-muted hover:text-content-primary transition-all"
        >
          Cancel
        </button>
      </div>
    </div>

    <!-- Add button -->
    <button
      v-if="!showAddForm"
      @click="showAddForm = true"
      class="flex items-center gap-1.5 px-3 py-1.5 para-chip-sm type-label border-accent text-content-muted hover:text-accent transition-all duration-150"
    >
      <i class="pi pi-plus text-xs" />
      Add Criterion
    </button>
  </div>
</template>
