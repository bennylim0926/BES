<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/utils/auth'
import { useScrollReveal } from '@/utils/useScrollReveal'

const router    = useRouter()
const authStore = useAuthStore()
const { revealRef } = useScrollReveal()

const role = computed(() =>
  authStore.user ? authStore.user['role'][0]['authority'] : ''
)

const roleLabel = computed(() => {
  const map = {
    ROLE_ADMIN:     'Admin',
    ROLE_ORGANISER: 'Organiser',
    ROLE_JUDGE:     'Judge',
    ROLE_EMCEE:     'Emcee',
  }
  return map[role.value] ?? 'User'
})

// Quick action cards per role
const quickActions = computed(() => {
  const r = role.value
  const all = [
    {
      icon: 'pi-calendar',
      title: 'Events',
      desc: 'Browse and manage dance battle events',
      route: '/events',
      roles: ['ROLE_ADMIN', 'ROLE_ORGANISER'],
    },
    {
      icon: 'pi-users',
      title: 'Participants',
      desc: 'Assign judges and manage participant entries',
      route: '/event/update-event-details',
      roles: ['ROLE_ADMIN', 'ROLE_ORGANISER'],
    },
    {
      icon: 'pi-chart-bar',
      title: 'Scoreboard',
      desc: 'View and compare scores across genres',
      route: '/event/score',
      roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE'],
    },
    {
      icon: 'pi-list',
      title: 'Audition List',
      desc: 'Manage auditions, score participants, and run the timer',
      route: '/event/audition-list',
      roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE', 'ROLE_JUDGE'],
    },
    {
      icon: 'pi-bolt',
      title: 'Battle Control',
      desc: 'Run the bracket, manage rounds and voting',
      route: '/battle/control',
      roles: ['ROLE_ADMIN', 'ROLE_ORGANISER'],
    },
    {
      icon: 'pi-thumbs-up',
      title: 'Battle Judge',
      desc: 'Cast your vote during live battle rounds',
      route: '/battle/judge',
      roles: ['ROLE_JUDGE'],
    },
    {
      icon: 'pi-cog',
      title: 'Admin',
      desc: 'Manage judges, genres, and system settings',
      route: '/admin',
      roles: ['ROLE_ADMIN'],
    },
  ]
  return all.filter(a => a.roles.includes(r))
})
</script>

<template>
  <div class="page-container">

    <!-- Welcome header -->
    <div class="mb-10">
      <div class="flex items-center gap-2 mb-2">
        <span class="badge-neutral text-xs px-2.5 py-1">{{ roleLabel }}</span>
      </div>
      <h1 class="page-title">Welcome to BES</h1>
      <p class="text-muted mt-1">Battle Event System — your quick access dashboard</p>
    </div>

    <!-- Quick action cards with scroll reveal -->
    <div
      :ref="revealRef"
      class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 mb-12"
    >
      <button
        v-for="(action, i) in quickActions"
        :key="action.title"
        @click="$router.push(action.route)"
        class="group card-hover text-left p-6 focus:outline-none
               focus:ring-2 focus:ring-primary-500/30 reveal"
        :class="`reveal-delay-${Math.min(i + 1, 4)}`"
      >
        <!-- Icon -->
        <div class="w-11 h-11 rounded-xl bg-primary-100 group-hover:bg-primary-200
                    flex items-center justify-center mb-4 transition-colors duration-200">
          <i class="pi text-primary-400 text-xl" :class="action.icon"></i>
        </div>
        <!-- Text -->
        <h3 class="font-heading font-bold text-content-primary text-base mb-1">
          {{ action.title }}
        </h3>
        <p class="text-content-muted text-sm leading-relaxed">{{ action.desc }}</p>
        <!-- Arrow -->
        <div class="flex items-center gap-1 mt-4 text-primary-400 text-xs font-semibold
                    opacity-0 group-hover:opacity-100 transition-opacity duration-200">
          Open <i class="pi pi-arrow-right text-xs"></i>
        </div>
      </button>
    </div>

    <!-- How to use — collapsible -->
    <div class="card overflow-hidden">
      <details class="group">
        <summary
          class="flex items-center justify-between px-6 py-4 cursor-pointer
                 list-none select-none hover:bg-surface-700/40 transition-colors"
        >
          <div class="flex items-center gap-2.5">
            <i class="pi pi-book text-content-muted text-sm"></i>
            <span class="font-heading font-semibold text-content-secondary text-sm">How to Use</span>
          </div>
          <i class="pi pi-chevron-down text-content-muted text-xs
                    group-open:rotate-180 transition-transform duration-200"></i>
        </summary>

        <div class="px-6 pb-6 pt-2 border-t border-surface-600/30">
          <div class="prose prose-sm max-w-none text-content-secondary space-y-6">

            <!-- Before Event Day -->
            <section>
              <h2 class="font-heading font-bold text-content-secondary text-base mb-3">Before Event Day</h2>
              <ol class="list-decimal list-inside space-y-1.5 text-sm text-content-muted">
                <li>Each folder should contain one response form.</li>
                <li>Each response should contain at least: <strong class="text-content-secondary">Name, Category/Categories, Email</strong>.</li>
                <li>Go to <em>Events</em> and choose one.</li>
                <li>The first table shows participant breakdown for each genre.</li>
              </ol>
              <div class="mt-4 pl-4 border-l-2 border-surface-600/30 space-y-3 text-sm">
                <div>
                  <p class="font-semibold text-content-secondary mb-1">If there is a record of this event:</p>
                  <p class="text-content-muted">A table will be shown with participants and categories joined.</p>
                </div>
                <div>
                  <p class="font-semibold text-content-secondary mb-1">Else:</p>
                  <ol class="list-decimal list-inside space-y-1 text-content-muted">
                    <li>Choose categories.</li>
                    <li>Name the judges.</li>
                    <li>Insert a record in the database.</li>
                    <li>Refresh the database once you verify payment from Google response.</li>
                  </ol>
                </div>
                <div>
                  <p class="font-semibold text-content-secondary mb-1">If different judges per category:</p>
                  <ol class="list-decimal list-inside space-y-1 text-content-muted">
                    <li>Go to <em>Participants</em>.</li>
                    <li>Assign judges to each participant and press the update button.</li>
                  </ol>
                </div>
              </div>
            </section>

            <!-- On Event Day -->
            <section>
              <h2 class="font-heading font-bold text-content-secondary text-base mb-3">On the Event Day</h2>
              <div class="grid sm:grid-cols-2 gap-4 text-sm">
                <div class="bg-surface-700/50 rounded-xl p-4">
                  <p class="font-semibold text-content-secondary mb-2">Event Organiser</p>
                  <ol class="list-decimal list-inside space-y-1 text-content-muted">
                    <li>Display <em>Audition Number</em> screen.</li>
                    <li>Scan QR → audition number + category shown.</li>
                    <li>Give wrist tag with number.</li>
                  </ol>
                </div>
                <div class="bg-surface-700/50 rounded-xl p-4">
                  <p class="font-semibold text-content-secondary mb-2">Emcee</p>
                  <ol class="list-decimal list-inside space-y-1 text-content-muted">
                    <li>Go to <em>Audition List</em>, update the filter.</li>
                    <li>Use the timer for countdown.</li>
                  </ol>
                </div>
                <div class="bg-surface-700/50 rounded-xl p-4">
                  <p class="font-semibold text-content-secondary mb-2">Judge</p>
                  <ol class="list-decimal list-inside space-y-1 text-content-muted">
                    <li>Go to <em>Audition List</em>, update the filter.</li>
                    <li>Ensure <em>Current Judge</em> is selected.</li>
                    <li>Give score and submit.</li>
                  </ol>
                </div>
                <div class="bg-surface-700/50 rounded-xl p-4">
                  <p class="font-semibold text-content-secondary mb-2">When Audition Ends</p>
                  <ol class="list-decimal list-inside space-y-1 text-content-muted">
                    <li>Go to <em>Scoreboard</em> to get top-n participants.</li>
                    <li>If judges were assigned, choose <em>By Judge</em>.</li>
                  </ol>
                </div>
              </div>
            </section>

          </div>
        </div>
      </details>
    </div>

  </div>
</template>
