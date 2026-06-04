<script setup>
import { computed } from 'vue'
import { useAuthStore } from '@/utils/auth'

const authStore = useAuthStore()

const role = computed(() =>
  authStore.user ? authStore.user['role'][0]['authority'] : ''
)

const activeEvent = computed(() => authStore.activeEvent)

const roleDisplay = computed(() => {
  const labels = { ROLE_ADMIN: 'Admin', ROLE_ORGANISER: 'Organiser', ROLE_JUDGE: 'Judge', ROLE_EMCEE: 'Emcee', ROLE_HELPER: 'Helper' }
  const label = labels[role.value]
  return label ? { label } : null
})
</script>

<template>
  <div class="page-container relative">
    <!-- Color bleed -->
    <div class="color-bleed"></div>

    <div class="relative z-10">
      <!-- Page header -->
      <div class="mb-8">
        <div class="type-page-title mb-1">Home</div>
        <p class="type-label text-content-muted">{{ roleDisplay?.label ?? 'Welcome' }}</p>
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
          <p class="type-label text-content-muted">Manage and browse events</p>
        </router-link>

        <!-- Audition List -->
        <router-link
          v-if="activeEvent"
          :to="{ name: 'Audition List' }"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-list text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Audition</div>
          <p class="type-label text-content-muted">Score and timer controls</p>
        </router-link>

        <!-- Participants (Admin / Organiser) -->
        <router-link
          v-if="activeEvent && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER')"
          :to="{ name: 'Update Event Details' }"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-users text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Participants</div>
          <p class="type-label text-content-muted">Manage registrations and judges</p>
        </router-link>

        <!-- Scoreboard (Admin / Organiser / Emcee) -->
        <router-link
          v-if="activeEvent && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE')"
          :to="{ name: 'Score' }"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-chart-bar text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Scoreboard</div>
          <p class="type-label text-content-muted">Live leaderboard</p>
        </router-link>

        <!-- Battle Control (Admin / Organiser) -->
        <router-link
          v-if="activeEvent && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER')"
          :to="{ name: 'Battle Control' }"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-bolt text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Battle</div>
          <p class="type-label text-content-muted">Bracket and match control</p>
        </router-link>

        <!-- Admin (Admin only) -->
        <router-link
          v-if="role === 'ROLE_ADMIN'"
          to="/admin"
          class="card-hover p-6 relative cursor-pointer group"
        >
          <div class="corner-bar-tl"></div>
          <i class="pi pi-cog text-2xl text-accent mb-3 block"></i>
          <div class="type-body mb-1">Admin</div>
          <p class="type-label text-content-muted">Genres, judges, theme config</p>
        </router-link>

      </div>

      <!-- No event selected hint -->
      <div v-if="!activeEvent && (role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE')"
        class="mt-8 p-4 semantic-chip-warning flex items-start gap-3">
        <div class="w-2 h-2 rounded-full bg-amber-400 box-shadow-[0_0_6px_rgba(245,158,11,0.8)] mt-0.5 flex-shrink-0"></div>
        <div>
          <div class="type-body text-amber-400 mb-1">No Active Event</div>
          <p class="type-label text-content-muted">
            Select an event to access audition, scoring, and battle features.
          </p>
          <router-link :to="{ name: 'EventSelector' }" class="type-label text-accent underline mt-2 inline-block">
            Select Event →
          </router-link>
        </div>
      </div>

    </div>
  </div>
</template>
