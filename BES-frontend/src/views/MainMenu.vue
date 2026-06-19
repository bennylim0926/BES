<script setup>
import { computed, inject } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/utils/auth'
import { useTierAccess } from '@/utils/useTierAccess'

const router = useRouter()
const authStore = useAuthStore()
const { battleEnabled } = useTierAccess()
const requestEvent = inject('requestEvent', null)

const role = computed(() =>
  authStore.user ? authStore.user['role'][0]['authority'] : ''
)

const activeEvent = computed(() => authStore.activeEvent)

const roleDisplay = computed(() => {
  const labels = { ROLE_ADMIN: 'Admin', ROLE_ORGANISER: 'Organiser', ROLE_JUDGE: 'Judge', ROLE_EMCEE: 'Emcee', ROLE_HELPER: 'Helper' }
  const label = labels[role.value]
  return label ? { label } : null
})

/** Navigate to a route that requires an active event — shows popup if none is set */
function goRequireEvent(to) {
  if (!activeEvent.value) {
    requestEvent?.(to)
    return
  }
  router.push(to)
}

function goAuditionEmcee() {
  if (!activeEvent.value) {
    requestEvent?.({ name: 'Audition List', query: { picker: '1' } })
    return
  }
  localStorage.removeItem('selectedCategory')
  router.push({ name: 'Audition List', query: { picker: '1' } })
}
</script>

<template>
  <div class="page-container relative">
    <!-- Color bleed -->
    <div class="color-bleed"></div>

    <div class="relative z-10">
      <!-- Page header — h1 for document outline / screen-reader navigation -->
      <div class="mb-8">
        <h1 class="type-page-title mb-1">Home</h1>
        <p class="type-label text-content-muted">{{ roleDisplay?.label ?? 'Welcome' }}</p>
      </div>

      <!-- No event selected hint -->
      <div v-if="!activeEvent && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE' || role === 'ROLE_HELPER')"
        class="mb-8 p-4 semantic-chip-warning flex items-start gap-3">
        <div class="w-2 h-2 rounded-full bg-amber-400 shadow-[0_0_6px_rgba(245,158,11,0.8)] mt-0.5 flex-shrink-0" aria-hidden="true"></div>
        <div>
          <div class="type-body text-amber-400 mb-1">No Active Event</div>
          <p class="type-label text-content-muted">
            Select an event to access audition, scoring, and battle features.
          </p>
          <router-link :to="{ name: 'EventSelector' }"
            class="para-chip-sm type-label text-accent mt-3 inline-flex items-center px-4 py-2.5">
            Select Event →
          </router-link>
        </div>
      </div>

      <!-- Quick actions section -->
      <div class="section-rule mb-6">
        <span class="section-rule-label">Quick Actions</span>
        <div class="section-rule-line"></div>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">

        <!-- Events card (Admin / Organiser) -->
        <router-link
          v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
          to="/events"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-calendar text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Events</div>
          <p class="type-prose">Manage and browse events.</p>
        </router-link>

        <!-- Event Day (Helper) -->
        <button
          v-if="role === 'ROLE_HELPER'"
          @click="goRequireEvent({ name: 'Event Details', params: { eventName: activeEvent?.name } })"
          class="card-hover p-6 relative cursor-pointer group w-full text-left"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-calendar-clock text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Event Day</div>
          <p class="type-prose">Walk-ins, check-in, audition screen.</p>
        </button>

        <!-- Event Details (Admin / Organiser) -->
        <button
          v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
          @click="goRequireEvent({ name: 'Event Details', params: { eventName: activeEvent?.name } })"
          class="card-hover p-6 relative cursor-pointer group w-full text-left"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-cog text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Event Details</div>
          <p class="type-prose">Overview, categories, Google Drive setup.</p>
        </button>

        <!-- Audition List -->
        <button
          v-if="role !== 'ROLE_HELPER' && role !== 'ROLE_EMCEE' && role !== 'ROLE_ORGANISER'"
          @click="goRequireEvent({ name: 'Audition List' })"
          class="card-hover p-6 relative cursor-pointer group w-full text-left"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-list text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Audition</div>
          <p class="type-prose">Score and timer controls.</p>
        </button>

        <!-- Audition — emcee path: clear category so the inline picker appears -->
        <button
          v-if="role === 'ROLE_EMCEE'"
          @click="goAuditionEmcee"
          class="card-hover p-6 relative cursor-pointer group w-full text-left"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-list text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Audition</div>
          <p class="type-prose">Score and timer controls.</p>
        </button>

        <!-- Participants (Admin only) -->
        <button
          v-if="role === 'ROLE_ADMIN'"
          @click="goRequireEvent({ name: 'Update Event Details' })"
          class="card-hover p-6 relative cursor-pointer group w-full text-left"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-users text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Participants</div>
          <p class="type-prose">Manage registrations and judges.</p>
        </button>

        <!-- Edit Audition Numbers (Admin only) -->
        <button
          v-if="role === 'ROLE_ADMIN'"
          @click="goRequireEvent({ name: 'Audition Adjust' })"
          class="card-hover p-6 relative cursor-pointer group w-full text-left"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-hashtag text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Edit Numbers</div>
          <p class="type-prose">Adjust audition number assignments.</p>
        </button>

        <!-- Scoreboard (Admin / Organiser / Emcee / Helper) -->
        <button
          v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_HELPER'"
          @click="goRequireEvent({ name: 'Score' })"
          class="card-hover p-6 relative cursor-pointer group w-full text-left"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-chart-bar text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Scoreboard</div>
          <p class="type-prose">Live leaderboard.</p>
        </button>

        <!-- Battle Control (Admin / Organiser) -->
        <button
          v-if="battleEnabled && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER')"
          @click="goRequireEvent({ name: 'Battle Control' })"
          class="card-hover p-6 relative cursor-pointer group w-full text-left"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-bolt text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Battle</div>
          <p class="type-prose">Bracket and match control.</p>
        </button>

        <!-- Admin (Admin only) -->
        <router-link
          v-if="role === 'ROLE_ADMIN'"
          to="/admin"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-cog text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Admin</div>
          <p class="type-prose">Categories, judges, theme config.</p>
        </router-link>

      </div>

    </div>
  </div>
</template>
