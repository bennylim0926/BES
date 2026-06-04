<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { fetchAllEvents, verifyEventAccessCode } from '@/utils/api'
import { whoami } from '@/utils/api'
import { isEventVerified, markEventVerified, setActiveEvent } from '@/utils/auth'

const router = useRouter()
const route = useRoute()

const events = ref([])
const selectedEventId = ref(null)
const accessCode = ref('')
const isLoading = ref(false)
const error = ref('')
const userRole = ref('')

const selectedEvent = computed(() => events.value.find(e => e.id === selectedEventId.value) ?? null)
const alreadyVerified = computed(() => selectedEventId.value !== null && isEventVerified(selectedEventId.value))
const isAdmin = computed(() => userRole.value === 'ROLE_ADMIN')

onMounted(async () => {
  const user = await whoami()
  userRole.value = user?.role?.[0]?.authority ?? ''
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
    if (isAdmin.value || alreadyVerified.value) {
      setActiveEvent(selectedEventId.value, selectedEvent.value.name)
      markEventVerified(selectedEventId.value)
      const redirect = String(route.query.redirect || '/')
      // If coming from an EventDetails page, go to new event's details instead
      router.push(redirect.startsWith('/events/') ? `/events/${selectedEvent.value.name}` : redirect)
      return
    }
    if (!accessCode.value || accessCode.value.length !== 4) {
      error.value = 'Please enter the 4-digit access code.'
      return
    }
    const res = await verifyEventAccessCode(selectedEventId.value, accessCode.value)
    if (res?.valid) {
      setActiveEvent(selectedEventId.value, selectedEvent.value.name)
      markEventVerified(selectedEventId.value)
      const redirect = String(route.query.redirect || '/')
      router.push(redirect.startsWith('/events/') ? `/events/${selectedEvent.value.name}` : redirect)
    } else {
      error.value = 'Incorrect access code. Please try again.'
      accessCode.value = ''
    }
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

        <!-- Header -->
        <div class="mb-8">
          <div class="type-page-title mb-1">Select Event</div>
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
              <button
                v-for="event in events"
                :key="event.id"
                type="button"
                @click="selectedEventId = event.id; accessCode = ''; error = ''"
                :class="[
                  'card-hover p-4 sm:p-5 text-left relative group w-full',
                  selectedEventId === event.id ? 'border-[color:var(--accent-muted)]' : ''
                ]"
              >
                <div class="corner-bar-tl"></div>
                <div class="type-body mb-1 event-card-name">{{ event.name }}</div>
                <div
                  v-if="isAdmin && event.accessCode"
                  class="type-label text-content-muted"
                >
                  {{ event.accessCode }}
                </div>
              </button>
            </div>
            <p v-if="events.length === 0" class="type-label text-content-muted mt-2">No events found.</p>
          </div>

          <!-- Already verified chip -->
          <div
            v-if="alreadyVerified && !isAdmin"
            class="semantic-chip-success flex items-center gap-2 px-3 py-2"
          >
            <div class="w-2 h-2 rounded-full bg-emerald-400 flex-shrink-0" style="box-shadow: 0 0 6px rgba(52,211,153,0.8)"></div>
            <span class="type-label text-emerald-400">Already verified — you can proceed directly</span>
          </div>

          <!-- Access code input (non-admin, not yet verified) -->
          <div v-if="selectedEventId !== null && !isAdmin && !alreadyVerified">
            <label class="type-label text-content-muted block mb-2">Access Code</label>
            <input
              v-model="accessCode"
              type="text"
              inputmode="numeric"
              maxlength="4"
              placeholder="0000"
              class="input-base text-2xl tracking-widest text-center"
              autocomplete="off"
            />
          </div>

          <!-- Error -->
          <p v-if="error" class="type-label text-red-400">{{ error }}</p>

          <!-- Submit -->
          <button
            type="submit"
            :disabled="isLoading || !selectedEventId"
            class="w-full py-3 type-body bg-accent text-surface-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-200 flex items-center justify-center gap-2"
            style="clip-path: polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)"
          >
            <span v-if="isLoading">
              <i class="pi pi-spin pi-spinner mr-2"></i>Verifying…
            </span>
            <span v-else>
              {{ alreadyVerified || isAdmin ? 'Enter Event' : 'Verify & Enter' }}
            </span>
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
