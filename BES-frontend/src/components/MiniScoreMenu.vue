<script setup>
const props = defineProps({
  cards: { type: Array, required: true },
  show:  { type: Boolean, default: false },
  title: { type: String, default: 'Find Participant' },
})

const emit = defineEmits(['moveTo', 'close', 'select'])

// Snap stride = card width + gap. Cards are `width:100%` inside a
// `px-2 gap-2` flex container, so each card is `clientWidth - 16` wide
// and the per-card stride is `(clientWidth - 16) + 8 = clientWidth - 8`.
// Using `clientWidth + 8` (the original bug) overshoots by 16px per card;
// at higher indices the cumulative drift crosses half-a-card and snap
// pulls to the next slide (#14 → #15 on a ~440px phone, etc.).
const scrollTo = (container, elIndex) => {
  const target = elIndex * (container.clientWidth - 8)
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
  // Tell the parent which specific card was picked. In pair mode this
  // lets PairScoreCards select the right slot (#54 instead of falling
  // back to #53 — the default "first non-placeholder in pair").
  emit('select', card)
  emit('close')
}
</script>

<template>
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
      class="fixed inset-0 z-50 flex items-center justify-center p-4"
    >
      <div class="absolute inset-0 bg-black/80 backdrop-blur-sm" @click="$emit('close')"></div>

      <div
        class="card-hover relative flex flex-col mx-4 w-full"
        style="max-width: 520px; max-height: 80vh;"
      >
        <div class="corner-bar-tl"></div>
        <div class="corner-bar-bl"></div>

        <!-- Header -->
        <div class="flex items-center justify-between px-6 pt-6 pb-3 flex-shrink-0">
          <p class="type-page-title">{{ props.title }}</p>
          <button
            @click="$emit('close')"
            class="para-chip-sm px-2 py-1 type-label text-content-muted hover:text-content-primary transition-colors"
          >
            <i class="pi pi-times text-xs"></i>
          </button>
        </div>

        <!-- Scrollable grid -->
        <div class="overflow-y-auto flex-1 px-6 pb-6" style="scrollbar-width: thin; scrollbar-color: rgba(255,255,255,0.12) transparent;">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-2">
            <button
              v-for="(card, idx) in props.cards"
              :key="idx"
              @click="moveTo(idx)"
              class="para-chip-sm p-2.5 type-label text-content-muted hover:text-accent hover:border-[color:var(--accent-muted)] transition-all duration-150 text-left w-full min-w-0"
            >
              <div class="flex items-center gap-2 w-full min-w-0">
                <div class="flex-1 min-w-0">
                  <div class="flex items-baseline gap-1.5 truncate">
                    <span class="type-stat leading-none flex-shrink-0 text-accent" style="font-size: 1.4rem">#{{ card.auditionNumber }}</span>
                    <span class="type-name text-content-primary truncate" style="font-size: 1.1rem">{{ card.participantName }}</span>
                  </div>
                  <div v-if="card.memberNames?.length" class="type-prose text-content-muted truncate mt-0.5" style="font-size: 12px;">{{ card.memberNames.join(' · ') }}</div>
                </div>
                <span
                  class="type-stat text-[16px] flex-shrink-0"
                  :class="card.score === 0 ? 'text-content-muted' : 'text-accent'"
                >
                  {{ card.score === 0 ? '—' : card.score }}
                </span>
              </div>
            </button>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>
