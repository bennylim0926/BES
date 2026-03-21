<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAllFolderEvents, fetchAllEvents, whoami, updateEventAccessCode } from '@/utils/api'
import { checkAuthStatus } from '@/utils/auth'
import EventCard from '@/components/EventCard.vue'
import { useDelay } from '@/utils/utils'

const events = ref([])
const dbEvents = ref([])
const search  = ref('')
const router  = useRouter()
const isAdmin = ref(false)

const filtered = computed(() =>
  events.value.filter(e =>
    e.folderName.toLowerCase().includes(search.value.toLowerCase())
  )
)

function getAccessCode(folderName) {
  if (!isAdmin.value) return null
  const match = dbEvents.value.find(e => e.name === folderName)
  return match?.accessCode ?? null
}

function getDbEventId(folderName) {
  const match = dbEvents.value.find(e => e.name === folderName)
  return match?.id ?? null
}

async function handleUpdateCode(folderName, newCode) {
  const id = getDbEventId(folderName)
  if (!id) return
  await updateEventAccessCode(id, newCode)
  // update local cache
  const idx = dbEvents.value.findIndex(e => e.name === folderName)
  if (idx !== -1) dbEvents.value[idx] = { ...dbEvents.value[idx], accessCode: newCode }
}

async function goToEventDetails(eventName, folderID) {
  await useDelay().wait(200)
  router.push({ name: 'Event Details', params: { eventName }, query: { folderID } })
}

onMounted(async () => {
  const ok = await checkAuthStatus(['ROLE_ADMIN', 'ROLE_ORGANISER'])
  if (!ok) return
  const user = await whoami()
  isAdmin.value = user?.role?.[0]?.authority === 'ROLE_ADMIN'
  events.value = await fetchAllFolderEvents()
  if (isAdmin.value) {
    dbEvents.value = await fetchAllEvents() ?? []
  }
})
</script>

<template>
  <div class="page-container">

    <!-- Page header -->
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-8">
      <div>
        <h1 class="page-title">Events</h1>
        <p class="text-muted mt-1">Select an event to manage participants and scores</p>
      </div>
      <span class="badge-neutral self-start sm:self-auto text-sm px-3 py-1">
        {{ events.length }} event{{ events.length !== 1 ? 's' : '' }}
      </span>
    </div>

    <!-- Search -->
    <div class="relative max-w-xs mb-8">
      <span class="absolute inset-y-0 left-0 flex items-center pl-3.5 text-surface-400 pointer-events-none">
        <i class="pi pi-search text-sm"></i>
      </span>
      <input
        v-model="search"
        type="text"
        placeholder="Search events…"
        class="input-base pl-10"
      />
    </div>

    <!-- Events grid -->
    <div
      v-if="filtered.length"
      class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4"
    >
      <EventCard
        v-for="event in filtered"
        :key="event.folderID"
        :buttonName="event.folderName"
        :accessCode="getAccessCode(event.folderName)"
        @onClick="goToEventDetails(event.folderName, event.folderID)"
        @updateCode="(code) => handleUpdateCode(event.folderName, code)"
      />
    </div>

    <!-- Empty state -->
    <div v-else class="flex flex-col items-center justify-center py-24 text-center">
      <div class="w-16 h-16 rounded-2xl bg-surface-100 flex items-center justify-center mb-4">
        <i class="pi pi-calendar text-surface-400 text-2xl"></i>
      </div>
      <p class="font-heading font-semibold text-surface-700 text-lg">No events found</p>
      <p class="text-muted mt-1">
        {{ search ? 'Try a different search term' : 'No events are available yet' }}
      </p>
    </div>

  </div>
</template>
