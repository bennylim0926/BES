<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { fetchAllEvents } from '@/utils/api'
import { markEventVerified, setActiveEvent } from '@/utils/auth'

const router = useRouter()
const route = useRoute()

const events = ref([])
const selectedEventId = ref(null)
const isLoading = ref(false)
const error = ref('')

const selectedEvent = computed(() => events.value.find(e => e.id === selectedEventId.value) ?? null)

onMounted(async () => {
  events.value = await fetchAllEvents() ?? []
})

const handleSubmit = async () => {
  if (!selectedEventId.value) {
    error.value = 'Please select an event.'
    return
  }
  error.value = ''
  isLoading.value = true
  try {
    setActiveEvent(selectedEventId.value, selectedEvent.value.name)
    markEventVerified(selectedEventId.value)
    const redirect = String(route.query.redirect || '/')
    router.push(redirect.startsWith('/events/') ? `/events/${selectedEvent.value.name}` : redirect)
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="page-container flex items-start justify-center relative">
    <div class="color-bleed"></div>
    <div class="relative z-10 w-full max-w-md mt-4 sm:mt-10">
      <div class="card p-8">

        <!-- Header — h1 for document outline -->
        <div class="mb-8">
          <h1 class="type-page-title mb-1">Select Event</h1>
          <p class="type-label text-content-muted">{{ events.length }} available</p>
        </div>

        <form @submit.prevent="handleSubmit" class="space-y-5">

          <!-- Event grid -->
          <div>
            <div class="section-rule mb-4">
              <span class="section-rule-label">Available Events</span>
              <div class="section-rule-line"></div>
            </div>
            <div class="grid gap-2 event-grid" :class="events.length > 4 ? 'grid-cols-2' : 'grid-cols-1'">
              <!-- aria-pressed + check icon: selected state reads via shape and semantics, not border color alone -->
              <button
                v-for="event in events"
                :key="event.id"
                type="button"
                @click="selectedEventId = event.id; error = ''"
                :aria-pressed="selectedEventId === event.id"
                :class="[
                  'card-hover p-4 sm:p-5 text-left relative group w-full min-h-[44px]',
                  selectedEventId === event.id ? 'border-[color:var(--accent-muted)] glow-accent' : ''
                ]"
              >
                <div class="corner-bar-tl"></div>
                <div class="flex items-start justify-between gap-2">
                  <div class="type-body mb-1 event-card-name">{{ event.name }}</div>
                  <i v-if="selectedEventId === event.id" class="pi pi-check-circle text-accent flex-shrink-0" aria-hidden="true"></i>
                </div>
              </button>
            </div>
            <p v-if="events.length === 0" class="type-label text-content-muted mt-2">No events found.</p>
          </div>

          <!-- Error — role=alert so validation is announced when it appears -->
          <p v-if="error" role="alert" class="type-label text-red-400">{{ error }}</p>

          <!-- Submit -->
          <button
            type="submit"
            :disabled="isLoading || !selectedEventId"
            class="w-full py-3 type-body bg-accent text-surface-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 flex items-center justify-center gap-2"
            style="clip-path: polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)"
          >
            <span v-if="isLoading">
              <i class="pi pi-spin pi-spinner mr-2"></i>Loading…
            </span>
            <span v-else>Enter Event</span>
          </button>

        </form>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Prevent long event names from clipping in the 2-col grid */
.event-card-name {
  overflow-wrap: break-word;
  word-break: break-word;
}

/* On very narrow phones fall back to 1-col to avoid squashed cards */
@media (max-width: 360px) {
  .event-grid {
    grid-template-columns: 1fr !important;
  }
}
</style>
