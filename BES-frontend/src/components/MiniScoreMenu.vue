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
        class="flex items-center justify-between p-3 rounded-xl border border-surface-600/50
               bg-surface-700/50 text-left hover:bg-surface-600/60 hover:border-primary-500/40
               active:bg-surface-600 transition-all duration-150"
      >
        <div>
          <div class="text-sm font-semibold text-content-primary">
            #{{ card.auditionNumber }} · {{ card.participantName }}
          </div>
        </div>
        <span
          class="text-sm font-bold ml-2 flex-shrink-0"
          :class="card.score === 0 ? 'text-red-400' : 'text-primary-400'"
        >
          {{ card.score === 0 ? '—' : card.score }}
        </span>
      </button>
    </div>
  </ActionDoneModal>
</template>
