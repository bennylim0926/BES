<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { logout, whoami } from './utils/api'
import { useAuthStore, getActiveEvent } from './utils/auth'
import ActionDoneModal from './views/ActionDoneModal.vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route  = useRoute()

const modalTitle   = ref('')
const modalMessage = ref('')
const showModal    = ref(false)
const isOpen       = ref(false)

const authStore = useAuthStore()

// ── Computed ───────────────────────────────────────────────────────────────
const role = computed(() =>
  authStore.user ? authStore.user['role'][0]['authority'] : ''
)

const isAuthenticated = computed(() => authStore.isAuthenticated)

/** Hide the navbar on full-screen / immersive routes */
const hideNav = computed(() =>
  ['Login', 'StreamOverlay', 'BattleJudge', 'BracketVisualization'].includes(route.name)
)

/**
 * Role label — all roles share ONE neutral badge style.
 * Color differentiation would misuse semantic colors (amber=warning, emerald=success).
 * The text label itself is the differentiator; style is consistent.
 */
const roleDisplay = computed(() => {
  const labels = {
    ROLE_ADMIN:     'Admin',
    ROLE_ORGANISER: 'Organiser',
    ROLE_JUDGE:     'Judge',
    ROLE_EMCEE:     'Emcee',
  }
  const label = labels[role.value]
  return label ? { label } : null
})

// ── Actions ────────────────────────────────────────────────────────────────
const openModal = (title, message) => {
  modalTitle.value   = title
  modalMessage.value = message
  showModal.value    = true
}

const handleAccept = () => {
  showModal.value = false
  logoutNow()
  router.push({ name: 'Login' })
}

const logoutNow = async () => {
  isOpen.value = false
  await logout()
  authStore.logout()
}

const activeEvent = ref(getActiveEvent())
const eventMenuOpen = ref(false)

function changeEvent() {
  eventMenuOpen.value = false
  router.push({ name: 'EventSelector', query: { redirect: router.currentRoute.value.fullPath } })
}

function goToSection(routeName) {
  eventMenuOpen.value = false
  router.push({ name: routeName })
}

// ── Theme ───────────────────────────────────────────────────────────────────
const theme = ref(localStorage.getItem('bes-theme') || 'dark')

function applyTheme(t) {
  document.documentElement.setAttribute('data-theme', t)
  localStorage.setItem('bes-theme', t)
}

function toggleTheme() {
  const html = document.documentElement
  html.classList.add('theme-transition')
  theme.value = theme.value === 'dark' ? 'light' : 'dark'
  applyTheme(theme.value)
  setTimeout(() => html.classList.remove('theme-transition'), 300)
}

// ── Watchers ───────────────────────────────────────────────────────────────
watch(route, () => {
  isOpen.value = false
  eventMenuOpen.value = false
  activeEvent.value = getActiveEvent()
})

// ── Lifecycle ──────────────────────────────────────────────────────────────
onMounted(async () => {
  applyTheme(theme.value)
  try {
    const res = await whoami()
    authStore.login(res)
  } catch {
    // not authenticated — ok
  }
})
</script>

<template>
  <!-- ─────────────────────────────────────────
       Navigation Bar
  ───────────────────────────────────────── -->
  <nav
    v-if="!hideNav"
    class="fixed top-0 left-0 w-full z-40
           bg-surface-900/90 backdrop-blur-lg
           border-b border-surface-600/30
           shadow-[0_1px_20px_rgba(0,0,0,0.5)]
           transition-all duration-300"
  >
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="grid grid-cols-[auto_1fr_auto] items-center h-16 gap-4">

        <!-- Left: Logo -->
        <router-link to="/" class="flex items-center gap-2 group flex-shrink-0">
          <div class="w-8 h-8 rounded-lg bg-primary-500 text-white flex items-center justify-center font-anton text-xl group-hover:scale-105 transition-transform duration-200"
            style="box-shadow: 0 0 12px rgba(6,182,212,0.4);">B</div>
          <span class="font-anton text-2xl text-content-primary tracking-wide translate-y-[2px]">BES</span>
        </router-link>

        <!-- Center: Primary nav -->
        <div class="hidden md:flex items-center justify-center gap-1">
          <router-link to="/" v-slot="{ isActive }">
            <span class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer"
              :class="isActive ? 'bg-primary-600 text-white shadow-[0_0_12px_rgba(6,182,212,0.3)]' : 'text-content-muted hover:text-content-primary hover:bg-surface-700/60'">
              <i class="pi pi-home text-xs"></i> Home
            </span>
          </router-link>
          <router-link v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" to="/events" v-slot="{ isActive }">
            <span class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer"
              :class="isActive ? 'bg-primary-600 text-white shadow-[0_0_12px_rgba(6,182,212,0.3)]' : 'text-content-muted hover:text-content-primary hover:bg-surface-700/60'">
              <i class="pi pi-calendar text-xs"></i> Events
            </span>
          </router-link>
          <router-link v-if="role === 'ROLE_ADMIN'" to="/admin" v-slot="{ isActive }">
            <span class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium transition-all duration-200 cursor-pointer"
              :class="isActive ? 'bg-primary-600 text-white shadow-[0_0_12px_rgba(6,182,212,0.3)]' : 'text-content-muted hover:text-content-primary hover:bg-surface-700/60'">
              <i class="pi pi-cog text-xs"></i> Admin
            </span>
          </router-link>
        </div>

        <!-- Right: Event chip + utilities -->
        <div class="hidden md:flex items-center gap-2">

          <!-- Active event dropdown -->
          <div v-if="isAuthenticated && activeEvent" class="relative">
            <button @click="eventMenuOpen = !eventMenuOpen"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full
                     bg-surface-700 border border-surface-600 text-content-secondary
                     text-xs font-medium hover:bg-surface-600 hover:border-primary-500/50
                     hover:text-primary-400 transition-all duration-200 max-w-[180px]">
              <i class="pi pi-calendar-clock text-xs flex-shrink-0"></i>
              <span class="truncate">{{ activeEvent.name }}</span>
              <i class="pi pi-chevron-down text-xs flex-shrink-0 opacity-60 transition-transform duration-200"
                 :class="eventMenuOpen ? 'rotate-180' : ''"></i>
            </button>

            <Transition
              enter-active-class="transition duration-150 ease-out"
              enter-from-class="opacity-0 translate-y-1"
              enter-to-class="opacity-100 translate-y-0"
              leave-active-class="transition duration-100 ease-in"
              leave-from-class="opacity-100 translate-y-0"
              leave-to-class="opacity-0 translate-y-1"
            >
              <div v-if="eventMenuOpen"
                class="absolute right-0 top-full mt-2 w-48 z-50 bg-surface-800 border border-surface-600/60 rounded-xl shadow-[0_8px_32px_rgba(0,0,0,0.5)] overflow-hidden">
                <div class="px-3 py-2.5 border-b border-surface-700/60">
                  <p class="text-[10px] font-bold uppercase tracking-widest text-content-muted mb-0.5">Current Event</p>
                  <p class="text-sm font-semibold text-content-primary truncate">{{ activeEvent.name }}</p>
                </div>
                <div class="py-1">
                  <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE'"
                    @click="goToSection('Audition List')"
                    class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-content-secondary hover:bg-surface-700/60 hover:text-primary-400 transition-colors">
                    <i class="pi pi-list text-xs w-4"></i> Audition
                  </button>
                  <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
                    @click="goToSection('Update Event Details')"
                    class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-content-secondary hover:bg-surface-700/60 hover:text-primary-400 transition-colors">
                    <i class="pi pi-users text-xs w-4"></i> Participants
                  </button>
                  <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE'"
                    @click="goToSection('Score')"
                    class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-content-secondary hover:bg-surface-700/60 hover:text-primary-400 transition-colors">
                    <i class="pi pi-chart-bar text-xs w-4"></i> Scoreboard
                  </button>
                  <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
                    @click="goToSection('Battle Control')"
                    class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-content-secondary hover:bg-surface-700/60 hover:text-primary-400 transition-colors">
                    <i class="pi pi-bolt text-xs w-4"></i> Battle
                  </button>
                </div>
                <div class="border-t border-surface-700/60 py-1">
                  <button @click="changeEvent"
                    class="w-full flex items-center gap-2.5 px-3 py-2 text-sm text-content-muted hover:bg-surface-700/60 hover:text-primary-400 transition-colors">
                    <i class="pi pi-refresh text-xs w-4"></i> Change Event
                  </button>
                </div>
              </div>
            </Transition>
            <div v-if="eventMenuOpen" class="fixed inset-0 z-40" @click="eventMenuOpen = false"></div>
          </div>

          <!-- Divider -->
          <div v-if="isAuthenticated" class="h-5 w-px bg-surface-600/60"></div>

          <!-- Role badge -->
          <span v-if="isAuthenticated && roleDisplay"
            class="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-surface-700 text-content-secondary border border-surface-600">
            {{ roleDisplay.label }}
          </span>

          <!-- Theme toggle -->
          <button @click="toggleTheme"
            class="inline-flex items-center justify-center w-8 h-8 rounded-lg text-content-muted hover:text-content-primary hover:bg-surface-700 transition-all duration-200">
            <i class="pi text-sm" :class="theme === 'dark' ? 'pi-sun' : 'pi-moon'"></i>
          </button>

          <router-link v-if="!isAuthenticated" to="/login">
            <span class="px-4 py-2 rounded-lg text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors shadow-sm cursor-pointer btn-glow">Login</span>
          </router-link>

          <button v-if="isAuthenticated"
            @click="openModal('Logout Confirmation', 'Are you sure you want to securely log out?')"
            class="inline-flex items-center justify-center w-8 h-8 rounded-lg text-content-muted hover:text-red-400 hover:bg-red-950 transition-all duration-200"
            title="Logout">
            <i class="pi pi-sign-out text-sm"></i>
          </button>
        </div>

        <!-- Mobile Hamburger -->
        <button @click="isOpen = !isOpen"
          class="md:hidden inline-flex items-center justify-center w-9 h-9 rounded-lg text-content-muted hover:text-content-primary hover:bg-surface-700 transition-colors focus:outline-none">
          <i class="pi text-lg" :class="isOpen ? 'pi-times' : 'pi-bars'"></i>
        </button>

      </div>
    </div>

    <!-- Mobile Dropdown (slide-down from navbar) -->
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 -translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 -translate-y-2"
    >
      <div
        v-show="isOpen"
        class="md:hidden border-t border-surface-600/30
               bg-surface-900/98 backdrop-blur-md shadow-lg"
      >
        <!-- Mobile nav links -->
        <div class="px-3 py-3 space-y-0.5">
          <router-link to="/" v-slot="{ isActive }">
            <span class="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-colors duration-150 cursor-pointer"
              :class="isActive ? 'bg-primary-500 text-white' : 'text-content-secondary hover:bg-surface-700/60'">
              <i class="pi pi-home w-4" :class="isActive ? 'text-white' : 'text-content-muted'"></i> Home
            </span>
          </router-link>

          <router-link v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" to="/events" v-slot="{ isActive }">
            <span class="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-colors duration-150 cursor-pointer"
              :class="isActive ? 'bg-primary-500 text-white' : 'text-content-secondary hover:bg-surface-700/60'">
              <i class="pi pi-calendar w-4" :class="isActive ? 'text-white' : 'text-content-muted'"></i> Events
            </span>
          </router-link>

          <router-link v-if="role === 'ROLE_ADMIN'" to="/admin" v-slot="{ isActive }">
            <span class="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium transition-colors duration-150 cursor-pointer"
              :class="isActive ? 'bg-primary-500 text-white' : 'text-content-secondary hover:bg-surface-700/60'">
              <i class="pi pi-cog w-4" :class="isActive ? 'text-white' : 'text-content-muted'"></i> Admin
            </span>
          </router-link>

          <!-- Current event section (mobile) -->
          <template v-if="isAuthenticated && activeEvent">
            <div class="px-4 pt-3 pb-1">
              <p class="text-[10px] font-bold uppercase tracking-widest text-content-muted">Current Event</p>
              <p class="text-sm font-semibold text-primary-400 truncate">{{ activeEvent.name }}</p>
            </div>
            <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE'"
              @click="goToSection('Audition List')"
              class="w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm text-content-secondary hover:bg-surface-700/60 hover:text-primary-400 transition-colors">
              <i class="pi pi-list w-4 text-content-muted"></i> Audition
            </button>
            <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
              @click="goToSection('Update Event Details')"
              class="w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm text-content-secondary hover:bg-surface-700/60 hover:text-primary-400 transition-colors">
              <i class="pi pi-users w-4 text-content-muted"></i> Participants
            </button>
            <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE'"
              @click="goToSection('Score')"
              class="w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm text-content-secondary hover:bg-surface-700/60 hover:text-primary-400 transition-colors">
              <i class="pi pi-chart-bar w-4 text-content-muted"></i> Scoreboard
            </button>
            <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
              @click="goToSection('Battle Control')"
              class="w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm text-content-secondary hover:bg-surface-700/60 hover:text-primary-400 transition-colors">
              <i class="pi pi-bolt w-4 text-content-muted"></i> Battle
            </button>
            <button @click="changeEvent"
              class="w-full flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm text-content-muted hover:bg-surface-700/60 hover:text-primary-400 transition-colors">
              <i class="pi pi-refresh w-4"></i> Change Event
            </button>
          </template>
        </div>

        <!-- Mobile bottom row -->
        <div class="px-3 py-3 border-t border-surface-600/30">
          <div v-if="isAuthenticated" class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span v-if="roleDisplay"
                class="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold bg-surface-700 text-content-secondary border border-surface-600">
                {{ roleDisplay.label }}
              </span>
              <button @click="toggleTheme"
                class="inline-flex items-center justify-center w-8 h-8 rounded-lg text-content-muted hover:text-content-primary hover:bg-surface-700 transition-all duration-200">
                <i class="pi text-sm" :class="theme === 'dark' ? 'pi-sun' : 'pi-moon'"></i>
              </button>
            </div>
            <button @click="openModal('Logout Confirmation', 'Are you sure you want to securely log out?')"
              class="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium text-red-400 bg-red-950 hover:bg-red-900 transition-colors cursor-pointer">
              <i class="pi pi-sign-out"></i> Logout
            </button>
          </div>
          <router-link v-else to="/login">
            <span class="flex items-center justify-center px-4 py-3 rounded-xl text-sm font-semibold text-white bg-primary-500 hover:bg-primary-600 transition-colors w-full btn-glow">Login</span>
          </router-link>
        </div>

      </div>
    </Transition>

  </nav>

  <!-- Click-outside backdrop for mobile menu -->
  <div
    v-if="isOpen && !hideNav"
    class="fixed inset-0 z-30 md:hidden"
    @click="isOpen = false"
  ></div>

  <!-- Navbar height spacer -->
  <div v-if="!hideNav" class="h-16"></div>

  <!-- Main Content with page transitions -->
  <main>
    <RouterView v-slot="{ Component, route }">
      <Transition name="page-fade">
        <component :is="Component" :key="route.path" />
      </Transition>
    </RouterView>
  </main>

  <!-- Global Logout Confirmation Modal -->
  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    variant="warning"
    @accept="handleAccept"
    @close="showModal = false"
  >
    <p class="text-content-secondary leading-relaxed">{{ modalMessage }}</p>
  </ActionDoneModal>
</template>
