<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { fetchAllFolderEvents } from '@/utils/api'
import { checkAuthStatus } from '@/utils/auth'
import EventCard from '@/components/EventCard.vue'
import { useDelay } from '@/utils/utils'

const events = ref([])
const search  = ref('')
const router  = useRouter()

const filtered = computed(() =>
  events.value.filter(e =>
    e.folderName.toLowerCase().includes(search.value.toLowerCase())
  )
)

async function goToEventDetails(eventName, folderID) {
  await useDelay().wait(200)
  router.push({ name: 'Event Details', params: { eventName }, query: { folderID } })
}

onMounted(async () => {
  const ok = await checkAuthStatus(['admin', 'organiser'])
  if (!ok) return
  events.value = await fetchAllFolderEvents()
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
        @onClick="goToEventDetails(event.folderName, event.folderID)"
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
