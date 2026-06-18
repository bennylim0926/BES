<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAllFolderEvents, fetchAllEvents, deleteEvent } from '@/utils/api'
import { useAuthStore, setActiveEvent } from '@/utils/auth'
import EventCard from '@/components/EventCard.vue'
import { useDelay } from '@/utils/utils'

const events = ref([])
const showCreateModal = ref(false)
const dbEvents = ref([])
const search  = ref('')
const router  = useRouter()
const isAdmin = ref(false)
const isOrganiser = ref(false)
const showDeleteModal = ref(false)
const eventToDelete = ref(null)
const deleteConfirmName = ref('')
const deleteError = ref('')
const deleting = ref(false)

// Only one card expanded at a time (mobile)
const expandedId = ref(null)
const toggleExpanded = (id) => { expandedId.value = expandedId.value === id ? null : id }
const closeExpanded = () => { expandedId.value = null }
onMounted(()  => document.addEventListener('click', closeExpanded))
onUnmounted(() => document.removeEventListener('click', closeExpanded))

const filtered = computed(() => {
  const base = isAdmin.value
    ? events.value
    : events.value.filter(e => dbEvents.value.some(db => db.name === e.folderName))
  return base.filter(e => e.folderName.toLowerCase().includes(search.value.toLowerCase()))
})

async function goToEventDetails(eventName, folderID) {
  await useDelay().wait(200)
  const dbEvent = dbEvents.value.find(e => e.name === eventName)
  if (dbEvent) setActiveEvent(dbEvent.id, dbEvent.name, folderID)
  router.push({ name: 'Event Details', params: { eventName }, query: { folderID } })
}

function activateAndGo(folderName, routeName) {
  const dbEvent = dbEvents.value.find(e => e.name === folderName)
  if (dbEvent) setActiveEvent(dbEvent.id, dbEvent.name)
  router.push({ name: routeName })
}

function openDeleteModal(event) {
  eventToDelete.value = event
  deleteConfirmName.value = ''
  deleteError.value = ''
  showDeleteModal.value = true
  expandedId.value = null  // close the action panel
}

function closeDeleteModal() {
  showDeleteModal.value = false
  eventToDelete.value = null
  deleteConfirmName.value = ''
  deleteError.value = ''
}

async function confirmDelete() {
  if (!eventToDelete.value || deleteConfirmName.value !== eventToDelete.value.folderName) return
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteEvent(eventToDelete.value.folderName)
    // Remove from local list
    events.value = events.value.filter(e => e.folderID !== eventToDelete.value.folderID)
    dbEvents.value = dbEvents.value.filter(e => e.name !== eventToDelete.value.folderName)
    closeDeleteModal()
  } catch (err) {
    deleteError.value = err.message || 'Failed to delete event'
  } finally {
    deleting.value = false
  }
}

onMounted(async () => {
  const authStore = useAuthStore()
  const role = authStore.user?.role?.[0]?.authority
  isAdmin.value = role === 'ROLE_ADMIN'
  isOrganiser.value = role === 'ROLE_ORGANISER'
  events.value = await fetchAllFolderEvents()
  dbEvents.value = await fetchAllEvents() ?? []
})
</script>

<template>
  <div class="page-container relative">
    <div class="color-bleed"></div>

    <div class="relative z-10">

    <!-- Page header -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-8">
      <div>
        <!-- h1 for document outline -->
        <h1 class="type-page-title mb-1">Events</h1>
        <p class="type-prose">Select an event to manage participants and scores.</p>
      </div>
      <span class="badge-neutral type-label self-start sm:self-auto px-3 py-1">
        {{ filtered.length }} event{{ filtered.length !== 1 ? 's' : '' }}
      </span>
    </div>

    <!-- Search — sr-only label so the input has an accessible name beyond the placeholder -->
    <div class="relative max-w-xs mb-8">
      <label for="event-search" class="sr-only">Search events</label>
      <span class="absolute inset-y-0 left-0 flex items-center pl-3.5 text-content-muted pointer-events-none">
        <i class="pi pi-search text-sm" aria-hidden="true"></i>
      </span>
      <input
        id="event-search"
        v-model="search"
        type="search"
        placeholder="Search events"
        class="input-base !pl-10"
      />
    </div>

    <div class="section-rule mb-6">
      <span class="section-rule-label">All Events</span>
      <div class="section-rule-line"></div>
    </div>

    <!-- Events grid -->
    <div
      v-if="filtered.length"
      class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4 items-start"
    >
      <EventCard
        v-for="event in filtered"
        :key="event.folderID"
        :buttonName="event.folderName"
        :expanded="expandedId === event.folderID"
        :isAdmin="isAdmin"
        :showAudition="!isOrganiser"
        @toggle="toggleExpanded(event.folderID)"
        @onDetails="goToEventDetails(event.folderName, event.folderID)"
        @onAudition="activateAndGo(event.folderName, 'Audition List')"
        @onParticipants="activateAndGo(event.folderName, 'Update Event Details')"
        @onScoreboard="activateAndGo(event.folderName, 'Score')"
        @onBattle="activateAndGo(event.folderName, 'Battle Control')"
        @onDelete="openDeleteModal(event)"
      />
    </div>

    <!-- Empty state: no events at all -->
    <div v-else-if="events.length === 0" class="card p-8 text-center">
      <div class="type-page-title text-content-muted mb-3">NO EVENTS YET</div>
      <p class="type-body text-content-muted mb-4">Create your first event to get started.</p>
      <button @click="showCreateModal = true" class="para-chip-sm px-5 py-3 type-label text-accent">CREATE EVENT</button>
    </div>

    <!-- Empty state: search found nothing -->
    <div v-else class="flex flex-col items-center justify-center py-24 text-center">
      <div class="para-chip w-16 h-16 flex items-center justify-center mb-4">
        <i class="pi pi-calendar text-content-muted text-2xl"></i>
      </div>
      <p class="type-body text-content-secondary">No events found</p>
      <p class="type-label text-content-muted mt-1">
        {{ search ? 'Try a different search term' : 'No events are available yet' }}
      </p>
    </div>

    <!-- Delete Event Confirmation Modal -->
    <Teleport to="body">
      <Transition
        enter-active-class="transition duration-200 ease-out"
        enter-from-class="opacity-0"
        enter-to-class="opacity-100"
        leave-active-class="transition duration-150 ease-in"
        leave-from-class="opacity-100"
        leave-to-class="opacity-0"
      >
        <div
          v-if="showDeleteModal"
          class="fixed inset-0 z-50 flex items-center justify-center p-4"
          style="background: rgba(0,0,0,0.8)"
          @click.self="closeDeleteModal"
        >
          <div
            class="w-full max-w-md p-6 flex flex-col gap-5"
            style="background: #1a1a1a; border: 1px solid rgba(239,68,68,0.3); clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%)"
          >
            <!-- Header -->
            <div class="flex items-center gap-3">
              <div class="w-2.5 h-2.5 rounded-full bg-red-500 flex-shrink-0" style="box-shadow: 0 0 10px rgba(239,68,68,0.7)"></div>
              <h2 class="type-page-title text-red-400" style="font-size: 20px">DELETE EVENT</h2>
            </div>

            <!-- Warning -->
            <div class="px-4 py-3" style="border-left: 3px solid rgba(239,68,68,0.6); background: rgba(239,68,68,0.07)">
              <p class="type-label text-red-300/90 mb-1">This will permanently delete:</p>
              <p class="type-prose text-red-200/70">
                <strong class="type-name text-red-300">{{ eventToDelete?.folderName }}</strong>
                and ALL associated data — participants, categories, scores, feedback, battle state, and session tokens.
                This cannot be undone.
              </p>
            </div>

            <!-- Type-to-confirm: input always visible, hint changes -->
            <div>
              <p class="type-prose text-content-muted mb-3">
                <template v-if="deleteConfirmName === ''">Type the event name to enable deletion.</template>
                <template v-else-if="deleteConfirmName === eventToDelete?.folderName">✓ Name confirmed. Ready to delete.</template>
                <template v-else>Keep typing — name must match exactly.</template>
              </p>
              <input
                v-model="deleteConfirmName"
                type="text"
                :placeholder="eventToDelete?.folderName"
                class="input-base w-full"
                autofocus
              />
            </div>

            <!-- Error -->
            <div
              v-if="deleteError"
              class="px-3 py-2 type-label text-red-300"
              style="border-left: 3px solid rgba(239,68,68,0.5); background: rgba(239,68,68,0.08)"
            >
              {{ deleteError }}
            </div>

            <!-- Actions -->
            <div class="flex gap-3">
              <button
                @click="closeDeleteModal"
                class="flex-1 py-2.5 type-label border border-surface-600 text-content-muted hover:text-content-primary transition-colors"
                style="clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%)"
                :disabled="deleting"
              >CANCEL</button>
              <button
                @click="confirmDelete"
                :disabled="deleteConfirmName !== eventToDelete?.folderName || deleting"
                class="flex-1 py-2.5 type-label font-bold transition-all"
                :class="deleteConfirmName === eventToDelete?.folderName && !deleting
                  ? 'bg-red-600 text-white hover:bg-red-500'
                  : 'bg-surface-700 text-content-muted cursor-not-allowed'"
                style="clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%)"
              >
                <span v-if="deleting">DELETING…</span>
                <span v-else>DELETE EVENT</span>
              </button>
            </div>

            <!-- Tap outside hint -->
            <p class="type-prose-sm text-content-muted/50 text-center">Tap outside to cancel</p>
          </div>
        </div>
      </Transition>
    </Teleport>

  </div>
  </div>
</template>
