<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';
import { ref } from 'vue';

const props = defineProps({
  show:  { type: Boolean, default: false },
  title: { type: String, default: 'Update' },
  type:  { type: String, default: 'text' }
})

const emit = defineEmits(['submitUpdate', 'close'])

const inputValue = ref("")
const errorTitle = ref('')
const errorMessage = ref('')
const showError = ref(false)

function isNumeric(str) {
  return !isNaN(str) && !isNaN(parseFloat(str))
}

const submitUpdate = async () => {
  if (props.type === 'text') {
    if (inputValue.value == "") {
      errorTitle.value = "Validation Error"
      errorMessage.value = "Field cannot be empty."
      showError.value = true
      return
    }
    emit("submitUpdate", inputValue.value)
  } else if (props.type === 'number') {
    if (isNumeric(inputValue.value) && Number(inputValue.value) > 0) {
      emit("submitUpdate", Number(inputValue.value))
    } else {
      errorTitle.value = "Invalid Value"
      errorMessage.value = "Please enter a numeric value greater than 0."
      showError.value = true
    }
  }
  inputValue.value = ""
}
</script>

<template>
  <ActionDoneModal
    :show="props.show"
    :title="props.title"
    variant="info"
    @accept="submitUpdate"
    @close="$emit('close')"
  >
    <div class="mt-1">
      <label class="block text-xs font-semibold text-surface-600 uppercase tracking-wider mb-1.5">
        New Value
      </label>
      <input
        v-model="inputValue"
        :type="props.type === 'number' ? 'number' : 'text'"
        placeholder="Enter new value…"
        class="input-base"
        @keyup.enter="submitUpdate"
      />
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
