<script setup>
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

/* Inline validation state — surface missing-field errors at the field, not only after submit */
const touched = ref({ username: false, password: false })

const openModal = (title, message, variant = 'error') => {
  modalTitle.value   = title
  modalMessage.value = message
  modalVariant.value = variant
  showModal.value    = true
}

const handleAccept = () => { showModal.value = false }

const submitLogin = async () => {
  if (!username.value || !password.value) {
    touched.value = { username: true, password: true }
    return
  }
  try {
    isLoading.value = true
    const res = await login(username.value, password.value)
    if (res.status === 409) {
      openModal('Account Already Active', 'This account is already logged in on another device or browser. Please log out there first.')
    } else if (res.status !== 200) {
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
  <div class="min-h-screen flex overflow-hidden bg-surface-950 relative">

    <!-- Color bleed -->
    <div class="color-bleed"></div>

    <!-- ── Left panel: BES hero ───────────────────────────────── -->
    <div class="hidden lg:flex lg:w-[52%] relative flex-col justify-between p-14 overflow-hidden">
      <div class="absolute inset-0 bg-surface-900"></div>
      <div class="corner-bar-tl" style="height: 40%"></div>
      <div class="corner-bar-bl" style="width: 40%"></div>

      <!-- Wordmark -->
      <div class="relative z-10 flex items-center gap-2.5">
        <div class="glow-dot"></div>
        <span class="type-body tracking-[0.12em]">BES</span>
      </div>

      <!-- Hero -->
      <div class="relative z-10">
        <div class="type-display mb-6">BES</div>
        <div class="section-rule mb-6">
          <div class="section-rule-line"></div>
        </div>
        <p class="type-body text-content-secondary leading-relaxed max-w-md">
          The all-in-one platform for street dance battle events — registration, judging, real-time battles.
        </p>
        <div class="flex flex-wrap gap-2 mt-8">
          <span class="badge-neutral px-3 py-1.5">Event Management</span>
          <span class="badge-neutral px-3 py-1.5">Audition Control</span>
          <span class="badge-neutral px-3 py-1.5">Battle System</span>
        </div>
      </div>

      <!-- Footer -->
      <div class="relative z-10 type-label text-content-muted">
        &copy; {{ new Date().getFullYear() }} BES Platform
      </div>
    </div>

    <!-- ── Right panel: Login form ───────────────────────────── -->
    <div class="flex-1 flex items-center justify-center p-8 relative z-10">
      <div class="w-full max-w-sm relative">
        <!-- Corner bars on form panel -->
        <div class="corner-bar-tl"></div>
        <div class="corner-bar-bl"></div>

        <div class="p-8 bg-surface-900 border border-[rgba(255,255,255,0.07)]"
          style="clip-path: polygon(8px 0%,100% 0%,calc(100% - 8px) 100%,0% 100%)">

          <div class="mb-8">
            <div class="type-page-title mb-1">Sign In</div>
            <p class="type-label text-content-muted">Battle Event System</p>
          </div>

          <form @submit.prevent="submitLogin" class="space-y-4">
            <div>
              <!-- for/id association so the label is programmatically linked to its input -->
              <label for="login-username" class="type-label text-content-muted block mb-2">Username</label>
              <input
                id="login-username"
                v-model="username"
                type="text"
                class="input-base w-full"
                :class="touched.username && !username ? 'border-red-500/60' : ''"
                style="text-transform: none; font-family: var(--font-body, 'Inter'), sans-serif; letter-spacing: normal;"
                autocomplete="username"
                :aria-invalid="touched.username && !username ? 'true' : undefined"
                aria-describedby="login-username-error"
                @blur="touched.username = true"
              />
              <!-- Inline validation: error appears at the field instead of waiting for submit -->
              <p v-if="touched.username && !username" id="login-username-error" role="alert"
                class="type-label text-red-400 mt-1.5">Username is required</p>
            </div>
            <div>
              <label for="login-password" class="type-label text-content-muted block mb-2">Password</label>
              <div class="relative">
                <input
                  id="login-password"
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  class="input-base w-full pr-12"
                  :class="touched.password && !password ? 'border-red-500/60' : ''"
                  style="text-transform: none; font-family: var(--font-body, 'Inter'), sans-serif; letter-spacing: normal;"
                  autocomplete="current-password"
                  :aria-invalid="touched.password && !password ? 'true' : undefined"
                  aria-describedby="login-password-error"
                  @blur="touched.password = true"
                />
                <!-- aria-label + aria-pressed: icon-only toggle gets an accessible name and state; 44px tap target -->
                <button type="button" @click="showPassword = !showPassword"
                  :aria-label="showPassword ? 'Hide password' : 'Show password'"
                  :aria-pressed="showPassword"
                  class="absolute right-1 top-1/2 -translate-y-1/2 w-11 h-11 inline-flex items-center justify-center type-label text-content-muted hover:text-content-primary transition-colors">
                  <i class="pi" :class="showPassword ? 'pi-eye-slash' : 'pi-eye'" aria-hidden="true"></i>
                </button>
              </div>
              <p v-if="touched.password && !password" id="login-password-error" role="alert"
                class="type-label text-red-400 mt-1.5">Password is required</p>
            </div>

            <!-- aria-busy + spinner: visible loading state, never leave the user guessing -->
            <button
              type="submit"
              :disabled="isLoading"
              :aria-busy="isLoading"
              class="w-full py-3 type-body bg-accent text-surface-900 transition-all duration-200 disabled:opacity-50 mt-2 inline-flex items-center justify-center gap-2"
              style="clip-path: polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)">
              <span v-if="isLoading" class="inline-block w-4 h-4 border-2 border-surface-900/30 border-t-surface-900 rounded-full animate-spin" aria-hidden="true"></span>
              {{ isLoading ? 'Signing in...' : 'Sign In' }}
            </button>
          </form>
        </div>
      </div>
    </div>

    <!-- Error modal -->
    <ActionDoneModal
      :show="showModal"
      :title="modalTitle"
      :variant="modalVariant"
      @accept="handleAccept"
      @close="showModal = false"
    >
      <p class="type-body text-content-secondary">{{ modalMessage }}</p>
    </ActionDoneModal>
  </div>
</template>
