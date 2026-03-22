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
  ['Login', 'StreamOverlay', 'BattleJudge'].includes(route.name)
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

function changeEvent() {
  router.push({ name: 'EventSelector' })
}

// ── Watchers ───────────────────────────────────────────────────────────────
watch(route, () => {
  isOpen.value = false
  activeEvent.value = getActiveEvent()
})

// ── Lifecycle ──────────────────────────────────────────────────────────────
onMounted(async () => {
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
           bg-white/85 backdrop-blur-lg
           border-b border-surface-200/80
           shadow-[0_1px_12px_rgba(0,0,0,0.06)]
           transition-all duration-300"
  >
    <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div class="flex justify-between items-center h-16">

        <!-- Logo -->
        <router-link to="/" class="flex items-center gap-2 group flex-shrink-0">
          <div
            class="w-8 h-8 rounded-lg bg-primary-500 text-white
                   flex items-center justify-center font-anton text-xl
                   shadow-md group-hover:scale-105 transition-transform duration-200"
          >B</div>
          <span class="font-anton text-2xl text-surface-900 tracking-wide translate-y-[2px]">
            BES
          </span>
        </router-link>

        <!-- Desktop Nav Links -->
        <div class="hidden md:flex md:items-center md:gap-1 lg:gap-1.5">

          <router-link to="/" v-slot="{ isActive }">
            <span
              class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium
                     transition-all duration-200 cursor-pointer"
              :class="isActive
                ? 'bg-primary-600 text-white shadow-sm'
                : 'text-surface-600 hover:text-surface-900 hover:bg-surface-100'"
            >
              <i class="pi pi-home text-xs"></i> Home
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
            to="/events"
            v-slot="{ isActive }"
          >
            <span
              class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium
                     transition-all duration-200 cursor-pointer"
              :class="isActive
                ? 'bg-primary-600 text-white shadow-sm'
                : 'text-surface-600 hover:text-surface-900 hover:bg-surface-100'"
            >
              <i class="pi pi-calendar text-xs"></i> Events
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
            to="/event/update-event-details"
            v-slot="{ isActive }"
          >
            <span
              class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium
                     transition-all duration-200 cursor-pointer"
              :class="isActive
                ? 'bg-primary-600 text-white shadow-sm'
                : 'text-surface-600 hover:text-surface-900 hover:bg-surface-100'"
            >
              <i class="pi pi-users text-xs"></i> Participants
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE'"
            to="/event/audition-list"
            v-slot="{ isActive }"
          >
            <span
              class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium
                     transition-all duration-200 cursor-pointer"
              :class="isActive
                ? 'bg-primary-600 text-white shadow-sm'
                : 'text-surface-600 hover:text-surface-900 hover:bg-surface-100'"
            >
              <i class="pi pi-list text-xs"></i> Audition
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN' || role === 'ROLE_EMCEE' || role === 'ROLE_ORGANISER'"
            to="/event/score"
            v-slot="{ isActive }"
          >
            <span
              class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium
                     transition-all duration-200 cursor-pointer"
              :class="isActive
                ? 'bg-primary-600 text-white shadow-sm'
                : 'text-surface-600 hover:text-surface-900 hover:bg-surface-100'"
            >
              <i class="pi pi-chart-bar text-xs"></i> Scoreboard
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
            to="/battle/control"
            v-slot="{ isActive }"
          >
            <span
              class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium
                     transition-all duration-200 cursor-pointer"
              :class="isActive
                ? 'bg-primary-600 text-white shadow-sm'
                : 'text-surface-600 hover:text-surface-900 hover:bg-surface-100'"
            >
              <i class="pi pi-bolt text-xs"></i> Battle
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN'"
            to="/admin"
            v-slot="{ isActive }"
          >
            <span
              class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium
                     transition-all duration-200 cursor-pointer"
              :class="isActive
                ? 'bg-primary-600 text-white shadow-sm'
                : 'text-surface-600 hover:text-surface-900 hover:bg-surface-100'"
            >
              <i class="pi pi-cog text-xs"></i> Admin
            </span>
          </router-link>

        </div>

        <!-- Desktop Right: Event Chip + Role Badge + Auth -->
        <div class="hidden md:flex items-center gap-3">

          <!-- Active event chip -->
          <button
            v-if="isAuthenticated && activeEvent"
            @click="changeEvent"
            class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full
                   bg-surface-100 border border-surface-200 text-surface-700
                   text-xs font-medium hover:bg-primary-50 hover:border-primary-200
                   hover:text-primary-700 transition-all duration-200 max-w-[160px]"
            title="Click to change event"
          >
            <i class="pi pi-calendar-clock text-xs flex-shrink-0"></i>
            <span class="truncate">{{ activeEvent.name }}</span>
            <i class="pi pi-chevron-down text-xs flex-shrink-0 opacity-60"></i>
          </button>

          <!-- Role badge — single neutral style for all roles; label is the differentiator -->
          <span
            v-if="isAuthenticated && roleDisplay"
            class="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold
                   bg-surface-100 text-surface-600 border border-surface-200"
          >
            {{ roleDisplay.label }}
          </span>

          <router-link v-if="!isAuthenticated" to="/login">
            <span
              class="px-4 py-2 rounded-lg text-sm font-semibold text-white
                     bg-primary-500 hover:bg-primary-600 transition-colors shadow-sm cursor-pointer"
            >
              Login
            </span>
          </router-link>

          <button
            v-if="isAuthenticated"
            @click="openModal('Logout Confirmation', 'Are you sure you want to securely log out?')"
            class="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-sm font-medium
                   text-surface-500 hover:text-red-600 hover:bg-red-50 transition-all duration-200"
          >
            <i class="pi pi-sign-out text-xs"></i>
            <span>Logout</span>
          </button>

        </div>

        <!-- Mobile Hamburger -->
        <button
          @click="isOpen = !isOpen"
          class="md:hidden inline-flex items-center justify-center w-9 h-9 rounded-lg
                 text-surface-500 hover:text-surface-900 hover:bg-surface-100
                 transition-colors focus:outline-none"
        >
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
        class="md:hidden border-t border-surface-200
               bg-white/95 backdrop-blur-md shadow-lg"
      >
        <div class="px-3 py-3 space-y-0.5">

          <router-link to="/" v-slot="{ isActive }">
            <span
              class="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium
                     transition-colors duration-150 cursor-pointer"
              :class="isActive
                ? 'bg-primary-500 text-white'
                : 'text-surface-700 hover:bg-surface-100'"
            >
              <i class="pi pi-home w-4" :class="isActive ? 'text-white' : 'text-surface-400'"></i>
              Home
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
            to="/events"
            v-slot="{ isActive }"
          >
            <span
              class="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium
                     transition-colors duration-150 cursor-pointer"
              :class="isActive
                ? 'bg-primary-500 text-white'
                : 'text-surface-700 hover:bg-surface-100'"
            >
              <i class="pi pi-calendar w-4" :class="isActive ? 'text-white' : 'text-surface-400'"></i>
              Events
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
            to="/event/update-event-details"
            v-slot="{ isActive }"
          >
            <span
              class="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium
                     transition-colors duration-150 cursor-pointer"
              :class="isActive
                ? 'bg-primary-500 text-white'
                : 'text-surface-700 hover:bg-surface-100'"
            >
              <i class="pi pi-users w-4" :class="isActive ? 'text-white' : 'text-surface-400'"></i>
              Participants
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER' || role === 'ROLE_EMCEE' || role === 'ROLE_JUDGE'"
            to="/event/audition-list"
            v-slot="{ isActive }"
          >
            <span
              class="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium
                     transition-colors duration-150 cursor-pointer"
              :class="isActive
                ? 'bg-primary-500 text-white'
                : 'text-surface-700 hover:bg-surface-100'"
            >
              <i class="pi pi-list w-4" :class="isActive ? 'text-white' : 'text-surface-400'"></i>
              Audition
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN' || role === 'ROLE_EMCEE' || role === 'ROLE_ORGANISER'"
            to="/event/score"
            v-slot="{ isActive }"
          >
            <span
              class="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium
                     transition-colors duration-150 cursor-pointer"
              :class="isActive
                ? 'bg-primary-500 text-white'
                : 'text-surface-700 hover:bg-surface-100'"
            >
              <i class="pi pi-chart-bar w-4" :class="isActive ? 'text-white' : 'text-surface-400'"></i>
              Scoreboard
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'"
            to="/battle/control"
            v-slot="{ isActive }"
          >
            <span
              class="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium
                     transition-colors duration-150 cursor-pointer"
              :class="isActive
                ? 'bg-primary-500 text-white'
                : 'text-surface-700 hover:bg-surface-100'"
            >
              <i class="pi pi-bolt w-4" :class="isActive ? 'text-white' : 'text-surface-400'"></i>
              Battle
            </span>
          </router-link>

          <router-link
            v-if="role === 'ROLE_ADMIN'"
            to="/admin"
            v-slot="{ isActive }"
          >
            <span
              class="flex items-center gap-3 px-4 py-3 rounded-xl text-sm font-medium
                     transition-colors duration-150 cursor-pointer"
              :class="isActive
                ? 'bg-primary-500 text-white'
                : 'text-surface-700 hover:bg-surface-100'"
            >
              <i class="pi pi-cog w-4" :class="isActive ? 'text-white' : 'text-surface-400'"></i>
              Admin
            </span>
          </router-link>

        </div>

        <!-- Mobile auth row -->
        <div class="px-3 py-3 border-t border-surface-100">
          <!-- Active event chip (mobile) -->
          <button
            v-if="isAuthenticated && activeEvent"
            @click="changeEvent"
            class="w-full flex items-center gap-2 px-4 py-2.5 mb-2 rounded-xl
                   bg-surface-50 border border-surface-200 text-surface-700
                   text-sm font-medium hover:bg-primary-50 hover:border-primary-200
                   hover:text-primary-700 transition-all duration-200"
            title="Click to change event"
          >
            <i class="pi pi-calendar-clock text-sm flex-shrink-0"></i>
            <span class="truncate flex-1 text-left">{{ activeEvent.name }}</span>
            <i class="pi pi-chevron-down text-xs flex-shrink-0 opacity-60"></i>
          </button>
          <div v-if="isAuthenticated" class="flex items-center justify-between">
            <span
              v-if="roleDisplay"
              class="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-semibold
                     bg-surface-100 text-surface-600 border border-surface-200"
            >
              {{ roleDisplay.label }}
            </span>
            <button
              @click="openModal('Logout Confirmation', 'Are you sure you want to securely log out?')"
              class="flex items-center gap-2 px-4 py-2.5 rounded-xl text-sm font-medium
                     text-red-600 bg-red-50 hover:bg-red-100 transition-colors cursor-pointer"
            >
              <i class="pi pi-sign-out"></i> Logout
            </button>
          </div>
          <router-link v-else to="/login">
            <span
              class="flex items-center justify-center px-4 py-3 rounded-xl text-sm font-semibold
                     text-white bg-primary-500 hover:bg-primary-600 transition-colors w-full"
            >
              Login
            </span>
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

  <!-- Main Content -->
  <main>
    <router-view />
  </main>

  <!-- Global Logout Confirmation Modal -->
  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    variant="warning"
    @accept="handleAccept"
    @close="showModal = false"
  >
    <p class="text-surface-600 leading-relaxed">{{ modalMessage }}</p>
  </ActionDoneModal>
</template>
