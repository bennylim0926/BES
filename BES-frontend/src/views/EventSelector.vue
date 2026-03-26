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
      router.push(route.query.redirect || '/')
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
      router.push(route.query.redirect || '/')
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
  <div class="page-container flex items-start justify-center">
    <div class="w-full max-w-md mt-10">
      <div class="card p-8">

        <!-- Header -->
        <div class="mb-8">
          <h1 class="font-heading font-bold text-content-primary text-2xl mb-1">Select Event</h1>
          <p class="text-muted text-sm">Choose the event you are working on today</p>
        </div>

        <form @submit.prevent="handleSubmit" class="space-y-5">

          <!-- Event list -->
          <div>
            <label class="block text-sm font-semibold text-content-secondary mb-2">Event</label>
            <div class="space-y-2 max-h-64 overflow-y-auto pr-1">
              <button
                v-for="event in events"
                :key="event.id"
                type="button"
                @click="selectedEventId = event.id; accessCode = ''; error = ''"
                :class="[
                  'w-full text-left px-4 py-3 rounded-xl border transition-all duration-150',
                  selectedEventId === event.id
                    ? 'border-primary-500 bg-primary-100 text-primary-400 font-semibold'
                    : 'border-surface-600 bg-surface-700 hover:border-primary-500/50 hover:bg-surface-600 text-content-primary'
                ]"
              >
                <span class="font-heading text-sm">{{ event.name }}</span>
                <span
                  v-if="isAdmin && event.accessCode"
                  class="ml-2 font-source text-xs text-content-muted tracking-widest"
                >
                  ({{ event.accessCode }})
                </span>
              </button>
            </div>
            <p v-if="events.length === 0" class="text-sm text-muted mt-2">No events found.</p>
          </div>

          <!-- Already verified chip -->
          <div
            v-if="alreadyVerified && !isAdmin"
            class="flex items-center gap-2 px-3 py-2 rounded-xl bg-emerald-950 border border-emerald-800/50"
          >
            <i class="pi pi-check-circle text-emerald-400 text-sm"></i>
            <span class="text-emerald-400 text-sm font-medium">Already verified — you can proceed directly</span>
          </div>

          <!-- Access code input (non-admin, not yet verified) -->
          <div v-if="selectedEventId !== null && !isAdmin && !alreadyVerified">
            <label class="block text-sm font-semibold text-content-secondary mb-2">Access Code</label>
            <input
              v-model="accessCode"
              type="text"
              inputmode="numeric"
              maxlength="4"
              placeholder="0000"
              class="input-base font-source text-2xl tracking-widest text-center"
              autocomplete="off"
            />
          </div>

          <!-- Error -->
          <p v-if="error" class="text-sm text-red-400 font-medium">{{ error }}</p>

          <!-- Submit -->
          <button
            type="submit"
            :disabled="isLoading || !selectedEventId"
            class="w-full py-3 px-6 rounded-xl font-semibold text-sm text-white
                   bg-primary-600 hover:bg-primary-700 active:scale-95
                   disabled:opacity-50 disabled:cursor-not-allowed
                   transition-all duration-200 btn-glow
                   flex items-center justify-center gap-2"
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
