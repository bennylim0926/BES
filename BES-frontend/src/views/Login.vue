<script setup>
import ReusableButton from '@/components/ReusableButton.vue'
import { login } from '@/utils/api'
import { ref } from 'vue'
import ActionDoneModal from './ActionDoneModal.vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/utils/auth'

const router    = useRouter()
const authStore = useAuthStore()

const username     = ref('')
const password     = ref('')
const isLoading    = ref(false)
const showPassword = ref(false)

const modalTitle   = ref('')
const modalMessage = ref('')
const showModal    = ref(false)
const modalVariant = ref('error')

const openModal = (title, message, variant = 'error') => {
  modalTitle.value   = title
  modalMessage.value = message
  modalVariant.value = variant
  showModal.value    = true
}

const handleAccept = () => { showModal.value = false }

const submitLogin = async () => {
  if (!username.value || !password.value) {
    openModal('Missing Fields', 'Please enter both username and password.')
    return
  }
  try {
    isLoading.value = true
    const res = await login(username.value, password.value)
    if (res.status !== 200) {
      openModal('Authentication Failed', 'Invalid username or password. Please try again.')
    } else {
      const data = await res.json()
      authStore.login(data)
      router.push({ name: 'Main' })
    }
  } catch {
    openModal('Network Error', 'Unable to reach the server. Please try again.')
  } finally {
    isLoading.value = false
  }
}
</script>

<template>
  <div class="min-h-screen flex overflow-hidden">

    <!-- ── Left panel: Brand ───────────────────────────────────────────── -->
    <div class="hidden lg:flex lg:w-[52%] relative flex-col justify-between p-14 overflow-hidden login-panel">

      <!-- CSS-only dot-grid pattern -->
      <div class="absolute inset-0 login-dots pointer-events-none"></div>

      <!-- Top: Wordmark -->
      <div class="relative z-10 flex items-center gap-3">
        <div class="w-10 h-10 rounded-xl bg-primary-500/20 border border-primary-500/30 flex items-center justify-center">
          <span class="font-anton text-primary-400 text-xl leading-none">B</span>
        </div>
        <span class="font-anton text-white text-2xl tracking-widest">BES</span>
      </div>

      <!-- Centre: Hero text -->
      <div class="relative z-10 max-w-lg">
        <h1 class="font-anton text-[clamp(4.5rem,8vw,7rem)] text-white leading-none tracking-wide mb-8">
          BES
        </h1>
        <div class="border-l-4 border-primary-500 pl-5">
          <p class="font-sans text-lg font-light text-surface-300 leading-relaxed">
            The all-in-one platform for street dance battle events — from registration to the final round.
          </p>
        </div>

        <!-- Feature pills -->
        <div class="flex flex-wrap gap-2 mt-8">
          <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/8 text-white/75 text-xs font-medium border border-white/10">
            <i class="pi pi-calendar text-primary-400 text-xs"></i> Event Management
          </span>
          <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/8 text-white/75 text-xs font-medium border border-white/10">
            <i class="pi pi-list text-primary-400 text-xs"></i> Audition Control
          </span>
          <span class="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full bg-white/8 text-white/75 text-xs font-medium border border-white/10">
            <i class="pi pi-bolt text-primary-400 text-xs"></i> Battle System
          </span>
        </div>
      </div>

      <!-- Bottom: Copyright -->
      <div class="relative z-10 text-surface-600 text-sm">
        &copy; {{ new Date().getFullYear() }} BES Platform. All rights reserved.
      </div>
    </div>

    <!-- ── Right panel: Form ───────────────────────────────────────────── -->
    <div class="w-full lg:w-[48%] flex items-center justify-center bg-white px-8 sm:px-14 lg:px-20 py-12">
      <div class="w-full max-w-sm animate-fade-in">

        <!-- Mobile brand mark -->
        <div class="lg:hidden flex items-center gap-2 mb-10">
          <div class="w-9 h-9 rounded-lg bg-primary-600 flex items-center justify-center">
            <span class="font-anton text-white text-xl leading-none">B</span>
          </div>
          <span class="font-anton text-surface-900 text-2xl tracking-widest">BES</span>
        </div>

        <!-- Heading -->
        <div class="mb-8">
          <h2 class="font-heading font-extrabold text-3xl text-surface-900 tracking-tight">
            Welcome back
          </h2>
          <p class="text-surface-500 text-sm mt-1.5">
            Sign in to access your event dashboard
          </p>
        </div>

        <!-- Form -->
        <form @submit.prevent="submitLogin" class="space-y-5">

          <!-- Username -->
          <div>
            <label for="username" class="block text-sm font-semibold text-surface-700 mb-1.5">
              Username
            </label>
            <div class="relative">
              <span class="absolute inset-y-0 left-0 flex items-center pl-3.5 text-surface-400 pointer-events-none">
                <i class="pi pi-user text-sm"></i>
              </span>
              <input
                id="username"
                type="text"
                placeholder="Enter your username"
                v-model="username"
                autocomplete="username"
                class="w-full pl-10 pr-4 py-3 text-sm rounded-xl border border-surface-200 bg-surface-50
                       text-surface-900 placeholder:text-surface-400
                       focus:bg-white focus:outline-none focus:ring-2 focus:ring-primary-600/30 focus:border-primary-600
                       transition-all duration-200"
              />
            </div>
          </div>

          <!-- Password -->
          <div>
            <div class="flex justify-between items-center mb-1.5">
              <label for="password" class="text-sm font-semibold text-surface-700">Password</label>
              <a href="#" class="text-xs font-medium text-primary-600 hover:text-primary-700 hover:underline transition-colors">
                Forgot password?
              </a>
            </div>
            <div class="relative">
              <span class="absolute inset-y-0 left-0 flex items-center pl-3.5 text-surface-400 pointer-events-none">
                <i class="pi pi-lock text-sm"></i>
              </span>
              <input
                id="password"
                :type="showPassword ? 'text' : 'password'"
                placeholder="Enter your password"
                v-model="password"
                autocomplete="current-password"
                class="w-full pl-10 pr-11 py-3 text-sm rounded-xl border border-surface-200 bg-surface-50
                       text-surface-900 placeholder:text-surface-400
                       focus:bg-white focus:outline-none focus:ring-2 focus:ring-primary-600/30 focus:border-primary-600
                       transition-all duration-200"
              />
              <button
                type="button"
                @click="showPassword = !showPassword"
                class="absolute inset-y-0 right-0 flex items-center pr-3.5 text-surface-400 hover:text-surface-600 transition-colors"
              >
                <i class="pi text-sm" :class="showPassword ? 'pi-eye-slash' : 'pi-eye'"></i>
              </button>
            </div>
          </div>

          <!-- Submit -->
          <div class="pt-2">
            <button
              type="submit"
              :disabled="isLoading"
              class="w-full py-3 px-6 rounded-xl font-semibold text-sm text-white
                     bg-primary-600 hover:bg-primary-700 active:bg-primary-700
                     shadow-sm hover:shadow-md
                     disabled:opacity-60 disabled:cursor-not-allowed
                     focus:outline-none focus:ring-2 focus:ring-primary-600/40 focus:ring-offset-1
                     transition-all duration-200 active:scale-[0.99]
                     flex items-center justify-center gap-2"
            >
              <i v-if="isLoading" class="pi pi-spinner pi-spin text-sm"></i>
              <span>{{ isLoading ? 'Signing in…' : 'Sign In' }}</span>
            </button>
          </div>

        </form>
      </div>
    </div>

  </div>

  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    :variant="modalVariant"
    @accept="handleAccept"
    @close="handleAccept"
  >
    <p class="text-surface-600 leading-relaxed">{{ modalMessage }}</p>
  </ActionDoneModal>
</template>

<style scoped>
/* Dark navy brand panel — bg-surface-900 = #0f172a */
.login-panel {
  background-color: #0f172a;
}

/* Subtle cyan dot grid — pure CSS, no animations */
.login-dots {
  background-image: radial-gradient(circle, rgba(6, 182, 212, 0.15) 1px, transparent 1px);
  background-size: 28px 28px;
}
</style>
