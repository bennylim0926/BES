<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';
import ReusableDropdown from './ReusableDropdown.vue';
import { onMounted, ref } from 'vue';
import { getAllJudges, submitParticipantScore } from '@/utils/api';

const props = defineProps({
  show:  { type: Boolean, default: false },
  title: { type: String, default: 'Update Score' },
  event: { type: String, default: '' },
  genre: { type: String, default: '' },
  name:  { type: String, default: '' },
  score: { type: Number, default: 0 }
})

const emit = defineEmits(['updateScore', 'close'])

const selectedJudge = ref("")
const scoreInput = ref(props.score)
const allJudges = ref([])

const errorTitle = ref("")
const errorMessage = ref("")
const showError = ref(false)

const updateScore = async () => {
  if (selectedJudge.value === "") {
    errorTitle.value = "Missing Judge"
    errorMessage.value = "Please select a judge before submitting."
    showError.value = true
    return
  }
  if (scoreInput.value > 10 || scoreInput.value <= 0) {
    errorTitle.value = "Invalid Score"
    errorMessage.value = "Score must be between 0.1 and 10."
    showError.value = true
    return
  }
  await submitParticipantScore(props.event, props.genre, selectedJudge.value, [{ participantName: props.name, score: scoreInput.value }])
  emit("updateScore")
}

onMounted(async () => {
  const res = await getAllJudges()
  allJudges.value = ["", ...Object.values(res).map(item => item.judgeName)]
})
</script>

<template>
  <ActionDoneModal
    :show="props.show"
    :title="props.title"
    variant="info"
    @accept="updateScore"
    @close="$emit('close')"
  >
    <div class="space-y-4 mt-1">
      <!-- Participant name -->
      <div class="flex items-center gap-3 p-3 rounded-xl bg-surface-900 border border-surface-600">
        <div class="w-8 h-8 rounded-full bg-primary-100 flex items-center justify-center">
          <i class="pi pi-user text-primary-400 text-xs"></i>
        </div>
        <div>
          <div class="text-xs text-content-muted">Participant</div>
          <div class="font-heading font-bold text-content-primary text-sm">{{ props.name }}</div>
        </div>
      </div>

      <!-- Score input -->
      <div>
        <label class="block text-xs font-semibold text-surface-600 uppercase tracking-wider mb-1.5">
          Score (0.1 – 10)
        </label>
        <input
          type="number"
          min="0"
          max="10"
          step="0.1"
          placeholder="Enter score…"
          v-model="scoreInput"
          class="input-base"
        />
      </div>

      <!-- Judge selector -->
      <div>
        <ReusableDropdown v-model="selectedJudge" labelId="Judge" :options="allJudges" />
      </div>
    </div>
  </ActionDoneModal>

  <ActionDoneModal
    :show="showError"
    :title="errorTitle"
    variant="error"
    @accept="showError = false"
    @close="showError = false"
  >
    <p class="text-surface-600">{{ errorMessage }}</p>
  </ActionDoneModal>
</template>
