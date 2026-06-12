<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { logout, whoami, getAppConfig, sendHeartbeat } from './utils/api'
import { createClient, deactivateClient, subscribeToChannel } from './utils/websocket'
import { useAuthStore } from './utils/auth'
import ActionDoneModal from './views/ActionDoneModal.vue'
import EventPanel from './components/EventPanel.vue'
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

const isJudgeRole = computed(() => role.value === 'ROLE_JUDGE')
const isEmceeRole = computed(() => role.value === 'ROLE_EMCEE')
const isHelperRole = computed(() => role.value === 'ROLE_HELPER')
const isJudgeSession = computed(() => !!authStore.judgeName && !!authStore.judgeId)
const isEmceeSession = computed(() => isEmceeRole.value && !!authStore.activeEvent)
const isHelperSession = computed(() => isHelperRole.value && !!authStore.activeEvent)

/** Hide the navbar on full-screen / immersive routes */
const hideNav = computed(() =>
  ['Login', 'StreamOverlay', 'Battle Judge', 'BracketVisualization', 'AuditionDisplay'].includes(route.name)
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
  isOpen.value = false
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
      <div class="grid grid-cols-[auto_1fr_auto] items-center h-16 gap-4">

        <!-- Left: BES wordmark + glowing dot -->
        <router-link :to="isJudgeRole ? '/judge/session' : isEmceeRole ? '/emcee/session' : isHelperRole ? '/helper/session' : '/'" class="flex items-center gap-2.5 group flex-shrink-0">
          <div class="glow-dot"></div>
          <span class="type-body text-[18px] tracking-[0.12em] text-content-primary">BES</span>
        </router-link>

        <!-- Center: Primary nav as parallelogram chips -->
        <div class="hidden md:flex items-center justify-center gap-2">
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

        <!-- Right: always-visible chip + desktop utilities + mobile hamburger -->
        <div class="flex items-center gap-2">

          <!-- Event chip — visible on all screen sizes -->
          <div v-if="isAuthenticated" class="relative">
            <!-- Session roles (judge/emcee/helper): event name is locked, displayed as static chip -->
            <span
              v-if="activeEvent && (isJudgeSession || isEmceeSession || isHelperSession)"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 type-label para-chip-sm text-content-secondary max-w-[200px] md:max-w-[240px]"
            >
              <span class="truncate">{{ activeEvent.name }}</span>
            </span>
            <!-- Normal user: interactive event chip -->
            <button
              v-else-if="activeEvent && !isEmceeSession && !isHelperSession"
              @click="panelOpen = !panelOpen"
              :aria-expanded="panelOpen"
              aria-haspopup="dialog"
              aria-label="Open event menu"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 type-label para-chip-sm text-content-secondary hover:text-content-primary transition-all duration-200 max-w-[200px] md:max-w-[240px]"
            >
              <span class="truncate">{{ activeEvent.name }}</span>
              <i class="pi pi-chevron-right text-[10px] flex-shrink-0 opacity-50 transition-transform duration-200"
                 :class="panelOpen ? 'rotate-90' : ''"></i>
            </button>
            <button
              v-else
              @click="router.push({ name: 'EventSelector' })"
              class="inline-flex items-center gap-1.5 px-3 py-1.5 type-label para-chip-sm text-content-muted hover:text-content-primary border border-[color:var(--accent-muted)] transition-all duration-200"
            >
              Select Event →
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
              <span class="px-4 py-1.5 para-chip type-label text-surface-900 bg-accent cursor-pointer">Login</span>
            </router-link>

            <!-- aria-label: icon-only logout button needs an accessible name -->
            <button v-if="isAuthenticated"
              @click="openModal('Logout Confirmation', 'Are you sure you want to securely log out?')"
              aria-label="Log out"
              class="inline-flex items-center justify-center w-8 h-8 type-label text-content-muted hover:text-red-400 hover:bg-red-950 transition-all duration-200">
              <i class="pi pi-sign-out text-sm" aria-hidden="true"></i>
            </button>
          </div>

          <!-- Mobile hamburger — aria-expanded announces menu state; removed focus:outline-none so the global focus ring shows -->
          <button @click="isOpen = !isOpen"
            :aria-expanded="isOpen"
            aria-label="Toggle navigation menu"
            class="md:hidden inline-flex items-center justify-center w-11 h-11 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.06)] transition-colors">
            <i class="pi text-lg" :class="isOpen ? 'pi-times' : 'pi-bars'" aria-hidden="true"></i>
          </button>

        </div>

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
          <router-link v-if="!isJudgeRole && !isEmceeRole && !isHelperRole" to="/" v-slot="{ isActive }">
            <span class="flex items-center gap-3 px-4 py-3 type-label cursor-pointer transition-colors duration-150"
              :class="isActive ? 'text-accent' : 'text-content-secondary hover:text-content-primary'">
              Home
            </span>
          </router-link>
          <router-link v-if="role === 'ROLE_ADMIN'" to="/admin" v-slot="{ isActive }">
            <span class="flex items-center gap-3 px-4 py-3 type-label cursor-pointer transition-colors duration-150"
              :class="isActive ? 'text-accent' : 'text-content-secondary hover:text-content-primary'">
              Admin
            </span>
          </router-link>

        </div>

        <div class="px-3 py-3 border-t border-[rgba(255,255,255,0.07)]">
          <div v-if="isAuthenticated" class="flex items-center justify-between">
            <div class="flex items-center gap-2">
              <span v-if="roleDisplay" class="badge-neutral type-label inline-flex items-center gap-1.5">
                <span class="opacity-50">{{ roleDisplay.label }}</span>
                <span v-if="roleDisplay.name" class="text-content-primary">{{ roleDisplay.name }}</span>
              </span>
              <!-- aria-label + 44px tap target for mobile theme toggle -->
              <button @click="toggleTheme"
                :aria-label="theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'"
                class="inline-flex items-center justify-center w-11 h-11 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.06)] transition-all duration-200">
                <i class="pi text-sm" :class="theme === 'dark' ? 'pi-sun' : 'pi-moon'" aria-hidden="true"></i>
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
          @close="panelOpen = false"
          @navigate="goToSection"
          @goToEventDetails="goToEventDetails"
          @goToAllEvents="goToAllEvents"
          @changeEvent="changeEvent"
        />
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
