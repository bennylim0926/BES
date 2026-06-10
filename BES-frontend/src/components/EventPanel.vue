<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { fetchAllEvents } from '@/utils/api'
import { setActiveEvent } from '@/utils/auth'

const props = defineProps({
  role:        { type: String, required: true },
  activeEvent: { type: Object, default: null },
})

const emit = defineEmits([
  'close',
  'navigate',
  'goToEventDetails',
  'goToAllEvents',
  'changeEvent',
])

const ALL_TILES = [
  { key: 'details',      icon: 'pi-cog',      label: 'Details',      roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_HELPER'] },
  { key: 'audition',     icon: 'pi-list',      label: 'Audition',     roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE', 'ROLE_JUDGE'] },
  { key: 'participants', icon: 'pi-users',     label: 'Participants', roles: ['ROLE_ADMIN', 'ROLE_ORGANISER'] },
  { key: 'score',        icon: 'pi-chart-bar', label: 'Score',        roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE'] },
  { key: 'battle',       icon: 'pi-bolt',      label: 'Battle',       roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_JUDGE'] },
  { key: 'numbers',      icon: 'pi-hashtag',   label: 'Numbers',      roles: ['ROLE_ADMIN', 'ROLE_ORGANISER'] },
]

const TILE_ROUTES = {
  audition:     'Audition List',
  participants: 'Update Event Details',
  score:        'Score',
  battle:       'Battle Control',
  numbers:      'Audition Adjust',
}

const isAdminOrOrganiser = computed(() =>
  props.role === 'ROLE_ADMIN' || props.role === 'ROLE_ORGANISER'
)

const isSessionRole = computed(() =>
  ['ROLE_JUDGE', 'ROLE_EMCEE', 'ROLE_HELPER'].includes(props.role)
)

const visibleTiles = computed(() =>
  ALL_TILES.filter(t => t.roles.includes(props.role))
)

function handleTile(tile) {
  if (tile.key === 'details') {
    emit('goToEventDetails')
  } else if (tile.key === 'battle' && props.role === 'ROLE_JUDGE') {
    emit('navigate', 'Battle Judge')
  } else {
    emit('navigate', TILE_ROUTES[tile.key])
  }
  emit('close')
}

const router = useRouter()
const route  = useRoute()

const allEvents = ref([])
const search    = ref('')

const filteredEvents = computed(() =>
  allEvents.value.filter(e =>
    e.name.toLowerCase().includes(search.value.toLowerCase())
  )
)

onMounted(async () => {
  if (!isAdminOrOrganiser.value) return
  allEvents.value = await fetchAllEvents() ?? []
})

function handleSwitchEvent(event) {
  setActiveEvent(event.id, event.name)
  emit('close')
  if (route?.name === 'Event Details') {
    router.push({ name: 'Event Details', params: { eventName: event.name } })
  }
}
</script>

<template>
  <div class="flex flex-col h-full">

    <div class="flex items-center justify-between px-4 py-3 border-b border-[rgba(255,255,255,0.07)] flex-shrink-0">
      <div class="corner-bar-tl"></div>
      <span class="type-body truncate pr-4">
        {{ activeEvent ? activeEvent.name : 'No Event Selected' }}
      </span>
      <button data-close @click="emit('close')"
        class="flex-shrink-0 text-content-muted hover:text-accent transition-colors">
        <i class="pi pi-times text-sm"></i>
      </button>
    </div>

    <div class="p-3 grid grid-cols-2 gap-2 flex-shrink-0">
      <button
        v-for="tile in visibleTiles"
        :key="tile.key"
        data-tile
        @click="handleTile(tile)"
        class="card-hover flex flex-col items-center gap-2 py-4 transition-all duration-150 hover:bg-[rgba(255,255,255,0.06)]"
        style="min-height:56px"
      >
        <i class="pi text-lg text-accent" :class="tile.icon"></i>
        <span class="type-label">{{ tile.label }}</span>
      </button>
    </div>

    <template v-if="isAdminOrOrganiser">
      <div class="flex-1 overflow-y-auto border-t border-[rgba(255,255,255,0.07)] p-3">
        <div class="section-rule mb-3">
          <span class="section-rule-label">Manage Events</span>
          <div class="section-rule-line"></div>
        </div>
        <input
          v-model="search"
          type="text"
          placeholder="Search events..."
          class="input-base w-full mb-2"
        />
        <div class="space-y-0.5 mb-2">
          <button
            v-for="event in filteredEvents"
            :key="event.id"
            @click="handleSwitchEvent(event)"
            class="w-full flex items-center gap-2 px-2 py-2 type-label text-left transition-colors hover:bg-[rgba(255,255,255,0.04)]"
            :class="event.id === activeEvent?.id ? 'text-accent' : 'text-content-secondary'"
          >
            <span class="w-1.5 h-1.5 rounded-full flex-shrink-0 transition-colors"
              :class="event.id === activeEvent?.id ? 'bg-accent' : 'bg-transparent border border-surface-600'">
            </span>
            <span class="truncate">{{ event.name }}</span>
          </button>
        </div>
        <button
          @click="emit('goToAllEvents'); emit('close')"
          class="w-full text-left type-label text-content-muted hover:text-content-primary transition-colors px-2 py-2"
        >
          All Events →
        </button>
      </div>
    </template>

    <template v-else>
      <div v-if="!isSessionRole" class="mt-auto border-t border-[rgba(255,255,255,0.07)] px-4 py-3">
        <button
          @click="emit('changeEvent'); emit('close')"
          class="w-full type-label text-content-muted hover:text-content-primary transition-colors text-center py-1"
        >
          ↻ Change Event
        </button>
      </div>
    </template>

  </div>
</template>
