<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { logout, whoami, getAppConfig, sendHeartbeat } from './utils/api'
import { createClient, deactivateClient, subscribeToChannel } from './utils/websocket'
import { useAuthStore } from './utils/auth'
import ActionDoneModal from './views/ActionDoneModal.vue'
import EventPanel from './components/EventPanel.vue'
import { useRoute, useRouter } from 'vue-router'
import { APP_NAME } from './utils/branding.js'
import { useTierAccess } from './utils/useTierAccess'

const router = useRouter()
const route  = useRoute()

const modalTitle   = ref('')
const modalMessage = ref('')
const showModal    = ref(false)

const accentServer = ref('#ffffff')
const wsAccentClient = ref(null)
const demoEnabled = ref(false)

const authStore = useAuthStore()
const { battleEnabled } = useTierAccess()

// ── Computed ───────────────────────────────────────────────────────────────
const role = computed(() =>
  authStore.user ? authStore.user['role'][0]['authority'] : ''
)

const isAuthenticated = computed(() => authStore.isAuthenticated)

const isJudgeRole = computed(() => role.value === 'ROLE_JUDGE')
const isEmceeRole = computed(() => role.value === 'ROLE_EMCEE')
const isHelperRole = computed(() => role.value === 'ROLE_HELPER')

/** Hide the navbar on full-screen / immersive routes */
const hideNav = computed(() =>
  ['Login', 'StreamOverlay', 'Battle Judge', 'BracketVisualization', 'AuditionDisplay', 'EmceeSession', 'JudgeSession', 'HelperSession'].includes(route.name)
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
    ROLE_HELPER:    'Helper',
  }
  const label = labels[role.value]
  if (!label) return null
  const name = (role.value === 'ROLE_JUDGE' && authStore.judgeName) ? authStore.judgeName
    : (role.value === 'ROLE_ORGANISER' && authStore.user?.username) ? authStore.user.username
    : null
  return { label, name }
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
  await logout()
  authStore.logout()
}

const activeEvent = computed(() => authStore.activeEvent)
const panelOpen = ref(false)

function handleKeydown(e) {
  if (e.key === 'Escape') panelOpen.value = false
}

function changeEvent() {
  panelOpen.value = false
  router.push({ name: 'EventSelector', query: { redirect: router.currentRoute.value.fullPath } })
}

function goToSection(routeName) {
  panelOpen.value = false
  if (routeName === 'Audition List' && isEmceeRole.value) {
    localStorage.removeItem('selectedCategory')
    router.push({ name: routeName, query: { picker: '1' } })
    return
  }
  router.push({ name: routeName })
}

function goToEventDetails() {
  panelOpen.value = false
  if (activeEvent.value) router.push({
    name: 'Event Details',
    params: { eventName: activeEvent.value.name },
    query: activeEvent.value.folderID ? { folderID: activeEvent.value.folderID } : {}
  })
}

function goToAllEvents() {
  panelOpen.value = false
  router.push({ name: 'Event' })
}

function goToHome() {
  panelOpen.value = false
  router.push('/')
}

function goToAdmin() {
  panelOpen.value = false
  router.push('/admin')
}

function handlePanelLogout() {
  panelOpen.value = false
  openModal('Logout Confirmation', 'Are you sure you want to securely log out?')
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
  panelOpen.value = false
})

// ── Lifecycle ──────────────────────────────────────────────────────────────
let heartbeatTimer = null

function startHeartbeat() {
  if (heartbeatTimer) return
  sendHeartbeat()
  heartbeatTimer = setInterval(sendHeartbeat, 30_000)
}

function stopHeartbeat() {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
}

watch(isAuthenticated, (val) => { val ? startHeartbeat() : stopHeartbeat() })

onMounted(async () => {
  applyTheme(theme.value)
  try {
    const res = await whoami()
    // Only apply the whoami result if the user IS authenticated, or if the
    // store doesn't already have an authenticated session. This prevents a
    // race where App.vue's whoami() resolves AFTER a token-auth page has
    // already logged the user in — without this guard, whoami's
    // { authenticated: false } would overwrite the token-auth session.
    if (res?.authenticated || !authStore.isAuthenticated) {
      authStore.login(res)
    }
    if (authStore.activeEvent) {
      authStore.fetchEventBattleEnabled(authStore.activeEvent.name)
    }
  } catch {
    // not authenticated — ok
  }
  try {
    const cfg = await getAppConfig()
    if (cfg?.accentColor) applyAccent(cfg.accentColor)
    if (cfg?.demoEnabled !== undefined) demoEnabled.value = cfg.demoEnabled
  } catch {
    // server not ready — keep default
  }
  const c = createClient()
  wsAccentClient.value = c
  subscribeToChannel(c, '/topic/app-config', (msg) => {
    if (msg?.accentColor) applyAccent(msg.accentColor)
    if (msg?.demoEnabled !== undefined) demoEnabled.value = msg.demoEnabled
  })
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  stopHeartbeat()
  deactivateClient(wsAccentClient.value)
  document.removeEventListener('keydown', handleKeydown)
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
    class="fixed top-0 left-0 w-full z-50 bg-surface-900/95 border-b border-[rgba(255,255,255,0.07)] transition-all duration-300"
  >
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex items-center h-16 gap-4">

        <!-- Left: Kyrove wordmark + glowing dot -->
        <router-link :to="isJudgeRole ? '/judge/session' : isEmceeRole ? '/emcee/session' : isHelperRole ? '/helper/session' : '/'" class="flex items-center gap-2.5 group flex-shrink-0">
          <div class="glow-dot"></div>
          <span class="type-body text-[18px] tracking-[0.12em] text-content-primary">{{ APP_NAME }}</span>
        </router-link>

        <!-- Center: Primary nav as parallelogram chips -->
        <div class="hidden md:flex flex-1 items-center justify-center gap-2">
          <router-link v-if="!isJudgeRole && !isEmceeRole && !isHelperRole" to="/" v-slot="{ isActive }">
            <span
              class="inline-flex items-center gap-1.5 px-3.5 py-1.5 type-label cursor-pointer transition-all duration-200"
              :class="isActive
                ? 'text-accent border-b-2 border-[color:var(--accent-color)]'
                : 'text-content-muted hover:text-content-primary'"
            >Home</span>
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

        <!-- Right: event chip (mobile menu trigger) + desktop utilities -->
        <div class="flex items-center justify-end gap-2 ml-auto md:ml-0">

          <!-- Single mobile entry point: event chip / menu button (always opens EventPanel) -->
          <div v-if="isAuthenticated" class="relative">
            <button
              @click="panelOpen = !panelOpen"
              :aria-expanded="panelOpen"
              aria-haspopup="dialog"
              :aria-label="activeEvent ? 'Open event menu' : 'Open menu'"
              class="inline-flex items-center gap-2 px-4 py-2 type-name para-chip-sm text-content-secondary hover:text-content-primary transition-all duration-200 max-w-[260px] md:max-w-[320px]"
            >
              <i v-if="!activeEvent" class="pi pi-bars text-base flex-shrink-0" aria-hidden="true"></i>
              <span v-if="activeEvent" class="truncate">{{ activeEvent.name }}</span>
              <span v-else class="type-label">Menu</span>
              <i class="pi pi-chevron-right text-xs flex-shrink-0 opacity-50 transition-transform duration-200"
                 :class="panelOpen ? 'rotate-90' : ''"></i>
            </button>
          </div>

          <!-- Desktop-only utilities -->
          <div class="hidden md:flex items-center gap-2">
            <div v-if="isAuthenticated" class="h-4 w-px bg-[rgba(255,255,255,0.12)]"></div>

            <span v-if="isAuthenticated && roleDisplay"
              class="badge-neutral type-label px-2 py-0.5 inline-flex items-center gap-1.5">
              <span class="opacity-50">{{ roleDisplay.label }}</span>
              <span v-if="roleDisplay.name" class="text-content-primary">{{ roleDisplay.name }}</span>
            </span>

            <!-- aria-label: icon-only button needs an accessible name -->
            <button @click="toggleTheme"
              :aria-label="theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'"
              class="inline-flex items-center justify-center w-8 h-8 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.06)] transition-all duration-200">
              <i class="pi text-sm" :class="theme === 'dark' ? 'pi-sun' : 'pi-moon'" aria-hidden="true"></i>
            </button>

            <router-link v-if="!isAuthenticated" to="/login">
              <span
                class="px-4 py-1.5 type-label text-surface-900 bg-accent cursor-pointer"
                style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%)"
              >Login</span>
            </router-link>

            <!-- aria-label: icon-only logout button needs an accessible name -->
            <button v-if="isAuthenticated"
              @click="openModal('Logout Confirmation', 'Are you sure you want to securely log out?')"
              aria-label="Log out"
              class="inline-flex items-center justify-center w-8 h-8 type-label text-content-muted hover:text-red-400 hover:bg-red-950 transition-all duration-200">
              <i class="pi pi-sign-out text-sm" aria-hidden="true"></i>
            </button>
          </div>

          <!-- Login for unauthenticated users on mobile -->
          <router-link v-if="!isAuthenticated" to="/login" class="md:hidden">
            <span
              class="px-4 py-1.5 type-label text-surface-900 bg-accent cursor-pointer"
              style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%)"
            >Login</span>
          </router-link>

        </div>

      </div>
    </div>

    <!-- Backdrop -->
    <Transition
      enter-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-opacity duration-150"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="panelOpen"
        class="fixed inset-0 z-40 bg-black/50"
        @click="panelOpen = false"
      ></div>
    </Transition>

    <!-- Slide-over panel -->
    <Transition
      enter-active-class="transition-transform duration-200 ease-out"
      enter-from-class="translate-x-full"
      enter-to-class="translate-x-0"
      leave-active-class="transition-transform duration-150 ease-in"
      leave-from-class="translate-x-0"
      leave-to-class="translate-x-full"
    >
      <!-- role=dialog: slide-over is a modal surface; screen readers announce it as such -->
      <div
        v-if="panelOpen"
        role="dialog"
        aria-label="Event navigation"
        class="fixed top-0 right-0 h-full w-full md:w-[300px] z-50 bg-surface-800 border-l border-[rgba(255,255,255,0.07)] overflow-hidden flex flex-col"
      >
        <EventPanel
          :role="role"
          :activeEvent="activeEvent"
          :theme="theme"
          :battleEnabled="battleEnabled"
          @close="panelOpen = false"
          @navigate="goToSection"
          @goToEventDetails="goToEventDetails"
          @goToAllEvents="goToAllEvents"
          @changeEvent="changeEvent"
          @goHome="goToHome"
          @goAdmin="goToAdmin"
          @toggleTheme="toggleTheme"
          @logout="handlePanelLogout"
        />
      </div>
    </Transition>

  </nav>

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
