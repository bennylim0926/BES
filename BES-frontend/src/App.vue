<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { logout, whoami, getAppConfig } from './utils/api'
import { createClient, deactivateClient, subscribeToChannel } from './utils/websocket'
import { useAuthStore } from './utils/auth'
import ActionDoneModal from './views/ActionDoneModal.vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route  = useRoute()

const modalTitle   = ref('')
const modalMessage = ref('')
const showModal    = ref(false)
const isOpen       = ref(false)

const accentServer = ref('#ffffff')
const wsAccentClient = ref(null)

const authStore = useAuthStore()

// ── Computed ───────────────────────────────────────────────────────────────
const role = computed(() =>
  authStore.user ? authStore.user['role'][0]['authority'] : ''
)

const isAuthenticated = computed(() => authStore.isAuthenticated)

/** Hide the navbar on full-screen / immersive routes */
const hideNav = computed(() =>
  ['Login', 'StreamOverlay', 'Battle Judge', 'BracketVisualization'].includes(route.name)
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

const activeEvent = computed(() => authStore.activeEvent)
const eventMenuOpen = ref(false)

function changeEvent() {
  eventMenuOpen.value = false
  router.push({ name: 'EventSelector', query: { redirect: router.currentRoute.value.fullPath } })
}

function goToSection(routeName) {
  eventMenuOpen.value = false
  router.push({ name: routeName })
}

function goToEventDetails() {
  eventMenuOpen.value = false
  if (activeEvent.value) router.push({
    name: 'Event Details',
    params: { eventName: activeEvent.value.name },
    query: activeEvent.value.folderID ? { folderID: activeEvent.value.folderID } : {}
  })
}

// ── Theme ───────────────────────────────────────────────────────────────────
const theme = ref(localStorage.getItem('bes-theme') || 'dark')

function applyTheme(t) {
  document.documentElement.setAttribute('data-theme', t)
  localStorage.setItem('bes-theme', t)
}

function applyAccent(color) {
  accentServer.value = color
  document.documentElement.style.setProperty('--accent-server', color)
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
  try {
    const cfg = await getAppConfig()
    if (cfg?.accentColor) applyAccent(cfg.accentColor)
  } catch {
    // server not ready — keep default
  }
  const c = createClient()
  wsAccentClient.value = c
  subscribeToChannel(c, '/topic/app-config', (msg) => {
    if (msg?.accentColor) applyAccent(msg.accentColor)
  })
})

onUnmounted(() => {
  deactivateClient(wsAccentClient.value)
})
</script>

<template>
  <!-- Global scanlines overlay -->
  <div
    class="scanlines fixed inset-0 z-[9999] pointer-events-none"
    style="background: repeating-linear-gradient(to bottom, transparent 0px, transparent 3px, rgba(0,0,0,0.045) 3px, rgba(0,0,0,0.045) 4px);"
    aria-hidden="true"
  ></div>

  <nav
    v-if="!hideNav"
    class="fixed top-0 left-0 w-full z-40 bg-surface-900/95 border-b border-[rgba(255,255,255,0.07)] transition-all duration-300"
  >
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="grid grid-cols-[auto_1fr_auto] items-center h-16 gap-4">

        <!-- Left: BES wordmark + glowing dot -->
        <router-link to="/" class="flex items-center gap-2.5 group flex-shrink-0">
          <div class="glow-dot"></div>
          <span class="type-body text-[18px] tracking-[0.12em] text-content-primary">BES</span>
        </router-link>

        <!-- Center: Primary nav as parallelogram chips -->
        <div class="hidden md:flex items-center justify-center gap-2">
          <router-link to="/" v-slot="{ isActive }">
            <span
              class="inline-flex items-center gap-1.5 px-3.5 py-1.5 type-label cursor-pointer transition-all duration-200"
              :class="isActive
                ? 'text-accent border-b-2 border-[color:var(--accent-color)]'
                : 'text-content-muted hover:text-content-primary'"
            >Home</span>
          </router-link>
          <router-link v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" to="/events" v-slot="{ isActive }">
            <span
              class="inline-flex items-center gap-1.5 px-3.5 py-1.5 type-label cursor-pointer transition-all duration-200"
              :class="isActive
                ? 'text-accent border-b-2 border-[color:var(--accent-color)]'
                : 'text-content-muted hover:text-content-primary'"
            >Events</span>
          </router-link>
          <router-link v-if="role === 'ROLE_ADMIN'" to="/admin" v-slot="{ isActive }">
            <span
              class="inline-flex items-center gap-1.5 px-3.5 py-1.5 type-label cursor-pointer transition-all duration-200"
              :class="isActive
                ? 'text-accent border-b-2 border-[color:var(--accent-color)]'
                : 'text-content-muted hover:text-content-primary'"
            >Admin</span>
          </router-link>
        </div>

        <!-- Right: event chip + role + theme + logout -->
        <div class="hidden md:flex items-center gap-2">

          <!-- Active event dropdown -->
          <div v-if="isAuthenticated && activeEvent" class="relative">
            <button
              @click="eventMenuOpen = !eventMenuOpen"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 type-label para-chip-sm text-content-secondary hover:text-content-primary transition-all duration-200 max-w-[200px]"
            >
              <span class="truncate">{{ activeEvent.name }}</span>
              <i class="pi pi-chevron-down text-[10px] flex-shrink-0 opacity-50 transition-transform duration-200"
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
                class="absolute right-0 top-full mt-2 w-52 z-50 bg-surface-800 border border-[rgba(255,255,255,0.07)] shadow-[0_8px_32px_rgba(0,0,0,0.5)] overflow-hidden relative"
                style="clip-path: polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)">
                <div class="corner-bar-tl"></div>
                <div class="px-3 py-2.5 border-b border-[rgba(255,255,255,0.07)]">
                  <p class="type-label text-content-muted mb-0.5">Current Event</p>
                  <p class="type-body text-content-primary truncate">{{ activeEvent.name }}</p>
                </div>
                <div class="py-1">
                  <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
                    @click="goToEventDetails"
                    class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-secondary hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                    Event Details
                  </button>
                  <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE'"
                    @click="goToSection('Audition List')"
                    class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-secondary hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                    Audition
                  </button>
                  <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
                    @click="goToSection('Update Event Details')"
                    class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-secondary hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                    Participants
                  </button>
                  <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE'"
                    @click="goToSection('Score')"
                    class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-secondary hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                    Scoreboard
                  </button>
                  <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
                    @click="goToSection('Battle Control')"
                    class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-secondary hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                    Battle
                  </button>
                </div>
                <div class="border-t border-[rgba(255,255,255,0.07)] py-1">
                  <button @click="changeEvent"
                    class="w-full flex items-center gap-2.5 px-3 py-2 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.04)] transition-colors">
                    Change Event
                  </button>
                </div>
              </div>
            </Transition>
            <div v-if="eventMenuOpen" class="fixed inset-0 z-40" @click="eventMenuOpen = false"></div>
          </div>

          <div v-if="isAuthenticated" class="h-4 w-px bg-[rgba(255,255,255,0.12)]"></div>

          <!-- Role badge -->
          <span v-if="isAuthenticated && roleDisplay"
            class="badge-neutral type-label px-2 py-0.5">
            {{ roleDisplay.label }}
          </span>

          <!-- Theme toggle -->
          <button @click="toggleTheme"
            class="inline-flex items-center justify-center w-8 h-8 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.06)] transition-all duration-200">
            <i class="pi text-sm" :class="theme === 'dark' ? 'pi-sun' : 'pi-moon'"></i>
          </button>

          <router-link v-if="!isAuthenticated" to="/login">
            <span class="px-4 py-1.5 para-chip type-label text-surface-900 bg-accent cursor-pointer">Login</span>
          </router-link>

          <button v-if="isAuthenticated"
            @click="openModal('Logout Confirmation', 'Are you sure you want to securely log out?')"
            class="inline-flex items-center justify-center w-8 h-8 type-label text-content-muted hover:text-red-400 hover:bg-red-950 transition-all duration-200">
            <i class="pi pi-sign-out text-sm"></i>
          </button>
        </div>

        <!-- Mobile Hamburger -->
        <button @click="isOpen = !isOpen"
          class="md:hidden inline-flex items-center justify-center w-9 h-9 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.06)] transition-colors focus:outline-none">
          <i class="pi text-lg" :class="isOpen ? 'pi-times' : 'pi-bars'"></i>
        </button>

      </div>
    </div>

    <!-- Mobile Dropdown -->
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 -translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 -translate-y-2"
    >
      <div v-show="isOpen" class="md:hidden border-t border-[rgba(255,255,255,0.07)] bg-surface-900/98">
        <div class="px-3 py-3 space-y-0.5">
          <router-link to="/" v-slot="{ isActive }">
            <span class="flex items-center gap-3 px-4 py-3 type-label cursor-pointer transition-colors duration-150"
              :class="isActive ? 'text-accent' : 'text-content-secondary hover:text-content-primary'">
              Home
            </span>
          </router-link>
          <router-link v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" to="/events" v-slot="{ isActive }">
            <span class="flex items-center gap-3 px-4 py-3 type-label cursor-pointer transition-colors duration-150"
              :class="isActive ? 'text-accent' : 'text-content-secondary hover:text-content-primary'">
              Events
            </span>
          </router-link>
          <router-link v-if="role === 'ROLE_ADMIN'" to="/admin" v-slot="{ isActive }">
            <span class="flex items-center gap-3 px-4 py-3 type-label cursor-pointer transition-colors duration-150"
              :class="isActive ? 'text-accent' : 'text-content-secondary hover:text-content-primary'">
              Admin
            </span>
          </router-link>

          <template v-if="isAuthenticated && activeEvent">
            <div class="px-4 pt-3 pb-1 section-rule">
              <span class="section-rule-label">Current Event — {{ activeEvent.name }}</span>
              <div class="section-rule-line"></div>
            </div>
            <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" @click="goToEventDetails"
              class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-secondary hover:text-content-primary transition-colors">
              Event Details
            </button>
            <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE'" @click="goToSection('Audition List')"
              class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-secondary hover:text-content-primary transition-colors">
              Audition
            </button>
            <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" @click="goToSection('Update Event Details')"
              class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-secondary hover:text-content-primary transition-colors">
              Participants
            </button>
            <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE'" @click="goToSection('Score')"
              class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-secondary hover:text-content-primary transition-colors">
              Scoreboard
            </button>
            <button v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" @click="goToSection('Battle Control')"
              class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-secondary hover:text-content-primary transition-colors">
              Battle
            </button>
            <button @click="changeEvent"
              class="w-full flex items-center gap-3 px-4 py-2.5 type-label text-content-muted hover:text-content-primary transition-colors">
              Change Event
            </button>
          </template>
        </div>

        <div class="px-3 py-3 border-t border-[rgba(255,255,255,0.07)]">
          <div v-if="isAuthenticated" class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span v-if="roleDisplay" class="badge-neutral type-label">{{ roleDisplay.label }}</span>
              <button @click="toggleTheme"
                class="inline-flex items-center justify-center w-8 h-8 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.06)] transition-all duration-200">
                <i class="pi text-sm" :class="theme === 'dark' ? 'pi-sun' : 'pi-moon'"></i>
              </button>
            </div>
            <button @click="openModal('Logout Confirmation', 'Are you sure you want to securely log out?')"
              class="flex items-center gap-2 px-4 py-2 type-label text-red-400 bg-red-950 hover:bg-red-900 transition-colors cursor-pointer"
              style="clip-path: polygon(4px 0%,100% 0%,calc(100% - 4px) 100%,0% 100%)">
              Logout
            </button>
          </div>
          <router-link v-else to="/login">
            <span class="flex items-center justify-center px-4 py-2.5 type-label para-chip text-surface-900 bg-accent w-full cursor-pointer">Login</span>
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
