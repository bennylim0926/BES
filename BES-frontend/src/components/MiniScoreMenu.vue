<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';

const props = defineProps({
  cards: { type: Array, required: true },
  show:  { type: Boolean, default: false },
  title: { type: String, default: 'Jump to Participant' },
})

const emit = defineEmits(['moveTo', 'close'])

const moveTo = (index) => {
  const cards = document.querySelectorAll('[data-card]');
  const el = cards[index];
  if (el) {
    el.scrollIntoView({ behavior: 'smooth', inline: 'center' });
  }
  emit('close')
}
</script>

<template>
  <ActionDoneModal
    :show="props.show"
    :title="props.title"
    variant="info"
    @accept="$emit('close')"
    @close="$emit('close')"
  >
    <div class="grid grid-cols-2 gap-2 mt-1">
      <button
        v-for="(card, idx) in props.cards"
        :key="idx"
        @click="moveTo(idx)"
        class="flex items-center justify-between p-3 rounded-xl border border-surface-200
               text-left hover:bg-primary-50 hover:border-primary-200
               active:bg-primary-100 transition-all duration-150"
      >
        <div>
          <div class="text-sm font-semibold text-surface-800">
            #{{ card.auditionNumber }} · {{ card.participantName }}
          </div>
        </div>
        <span
          class="text-sm font-bold ml-2 flex-shrink-0"
          :class="card.score === 0 ? 'text-red-400' : 'text-primary-600'"
        >
          {{ card.score === 0 ? '—' : card.score }}
        </span>
      </button>
    </div>
  </ActionDoneModal>
</template>
