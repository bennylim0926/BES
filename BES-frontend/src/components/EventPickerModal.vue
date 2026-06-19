<script setup>
import { ref, onMounted } from 'vue'
import { fetchAllEvents } from '@/utils/api'
import { setActiveEvent } from '@/utils/auth'

const emit = defineEmits(['close', 'selected'])

const events = ref([])
const loading = ref(true)

onMounted(async () => {
  events.value = await fetchAllEvents() ?? []
  loading.value = false
})

function select(event) {
  setActiveEvent(event.id, event.name)
  emit('selected', event)
}
</script>

<template>
  <Teleport to="body">
    <div
      class="fixed inset-0 z-[100] flex items-center justify-center p-4"
      style="background: rgba(0,0,0,0.8)"
      @click.self="emit('close')"
    >
      <div
        class="w-full max-w-sm p-6 flex flex-col gap-4"
        style="background: #1a1a1a; border: 1px solid rgba(255,255,255,0.1); clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%)"
      >
        <!-- Header -->
        <div class="flex items-center gap-3">
          <div class="w-2 h-2 rounded-full bg-amber-400 flex-shrink-0" style="box-shadow: 0 0 8px rgba(245,158,11,0.7)"></div>
          <h2 class="type-page-title" style="font-size: 18px">Select Event</h2>
        </div>

        <p class="type-prose text-content-muted">
          Choose an event to continue.
        </p>

        <!-- Event list -->
        <div v-if="loading" class="type-label text-content-muted text-center py-6">
          Loading…
        </div>
        <div v-else-if="events.length === 0" class="type-label text-content-muted text-center py-6">
          No events found.
        </div>
        <div v-else class="grid gap-2 max-h-64 overflow-y-auto">
          <button
            v-for="event in events"
            :key="event.id"
            @click="select(event)"
            class="card-hover p-3 text-left w-full"
          >
            <div class="corner-bar-tl"></div>
            <span class="type-name">{{ event.name }}</span>
          </button>
        </div>

        <!-- Hint -->
        <p class="type-prose-sm text-content-muted/40 text-center">Tap outside to dismiss</p>
      </div>
    </div>
  </Teleport>
</template>
