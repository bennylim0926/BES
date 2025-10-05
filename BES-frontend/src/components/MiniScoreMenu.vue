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
        <div class="flex flex-wrap justify-start gap-2 p-4">
            <div
                v-for="(card, idx) in props.cards"
                :key="idx"
                @click="moveTo(idx)"
                class="p-2 m-1 border border-orange-400 text-gray-900 dark:text-gray-100 cursor-pointer min-w-[150px] text-left rounded hover:bg-orange-500 transition"
            >
                <div>{{ card.auditionNumber }}. <span class="font-bold">{{ card.participantName }}</span></div>
                <div v-if="card.score===0" class="text-red-500 flex justify-center items-center">UNSCORED</div>
                <div v-else class="text-gray-900 dark:text-gray-100 flex justify-center items-center">{{card.score}}</div>
            </div>
            </div>
    </ActionDoneModal>
</template>