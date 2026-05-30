<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';

const props = defineProps({
  cards: { type: Array, required: true },
  show:  { type: Boolean, default: false },
  title: { type: String, default: 'Jump to Participant' },
})

const emit = defineEmits(['moveTo', 'close'])

const scrollTo = (container, elIndex) => {
  const target = elIndex * (container.clientWidth + 8)
  container.scrollTo({ left: target, behavior: 'smooth' })
}

const moveTo = (index) => {
  const card = props.cards[index]
  if (!card) { emit('close'); return }

  const soloCards = document.querySelectorAll('[data-card]')
  const pairSlides = document.querySelectorAll('[data-pair]')

  if (soloCards.length) {
    const el = soloCards[index]
    if (el) scrollTo(el.parentElement, index)
  } else if (pairSlides.length) {
    const pairIndex = Math.floor((card.auditionNumber - 1) / 2)
    const el = pairSlides[pairIndex]
    if (el) scrollTo(el.parentElement, pairIndex)
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
        class="para-chip-sm p-2 type-label text-content-muted hover:text-accent hover:border-[color:var(--accent-muted)] transition-all duration-150 text-left"
      >
        <div class="flex items-center gap-2 w-full">
          <span class="type-body text-content-primary truncate flex-1">#{{ card.auditionNumber }} · {{ card.participantName }}</span>
          <span
            class="type-stat text-[16px] flex-shrink-0"
            :class="card.score === 0 ? 'text-content-muted' : 'text-accent'"
          >
            {{ card.score === 0 ? '—' : card.score }}
          </span>
        </div>
      </button>
    </div>
  </ActionDoneModal>
</template>
