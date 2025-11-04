<script setup>
import ActionDoneModal from '@/views/ActionDoneModal.vue';

const props = defineProps({
    cards: {
    type: Array,
    required: true,
    },
    show: { type: Boolean, default: false },
    title: { type: String, default: "Modal Title" },
})

const emit = defineEmits(['moveTo', 'close'])

const moveTo = async (index) => {
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
        @accept="$emit('close')"
        @close="$emit('close')">
        <div class="flex flex-wrap justify-center md:justify-start lg:justify-start gap-2">
            <div
                v-for="(card, idx) in props.cards"
                :key="idx"
                @click="moveTo(idx)"
                class="p-2 m-1 text-black cursor-pointer min-w-30 text-left rounded hover:bg-orange-400 active:bg-orange-200 transition shadow-lg"
            >
                <div>{{ card.auditionNumber }}. <span class="font-semibold">{{ card.participantName }}</span></div>
                <div v-if="card.score===0" class="text-red-500 flex justify-center items-center">UNSCORED</div>
                <div v-else class="text-black  flex justify-center items-center">{{card.score}}</div>
            </div>
            </div>
    </ActionDoneModal>
</template>