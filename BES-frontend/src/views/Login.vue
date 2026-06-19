<script setup>
import { login, startDemo, getAppConfig } from '@/utils/api'
import { ref, onMounted, nextTick } from 'vue'
import ActionDoneModal from './ActionDoneModal.vue'
import { useRouter } from 'vue-router'
import { useAuthStore, setActiveEvent } from '@/utils/auth'
import { APP_NAME, APP_TAGLINE } from '../utils/branding.js'

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

/* Demo mode */
const demoEnabled = ref(false)
const showPasscodeModal = ref(false)
const passcode = ref('')
const passcodeError = ref('')

onMounted(async () => {
  try {
    const config = await getAppConfig()
    demoEnabled.value = config.demoEnabled === true
  } catch (_e) {
    // fallback: hide demo button if config fetch fails
    demoEnabled.value = false
  }
})

const passcodeLoading = ref(false)

async function startDemoWithPasscode(role) {
  if (!passcode.value.trim() || passcodeLoading.value) return
  passcodeLoading.value = true
  passcodeError.value = ''
  try {
    const result = await startDemo(passcode.value, role)
    // Populate auth store via setActiveEvent (writes to sessionStorage + Pinia)
    authStore.isAuthenticated = true
    authStore.judgeId = result.judgeId ?? null
    authStore.judgeName = result.judgeName ?? null
    authStore.user = { authenticated: true, role: [{ authority: 'ROLE_' + role }] }
    setActiveEvent(result.eventId, result.eventName)
    // Wait for Pinia reactivity to flush before navigating
    await nextTick()
    // Route to the appropriate session view
    const roleRoutes = {
      EMCEE: '/emcee/session',
      JUDGE: '/judge/session',
      HELPER: '/helper/session'
    }
    router.push(roleRoutes[role] || '/')
  } catch (e) {
    passcodeError.value = e.message || 'Failed to start demo'
  } finally {
    passcodeLoading.value = false
  }
}

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

    <!-- ── Left panel: Kyrove hero ────────────────────────────── -->
    <div class="hidden lg:flex lg:w-[52%] relative flex-col justify-between p-14 overflow-hidden">
      <div class="absolute inset-0 bg-surface-900"></div>
      <div class="corner-bar-tl" style="height: 40%"></div>
      <div class="corner-bar-bl" style="width: 40%"></div>

      <!-- Spacer — balances the footer so hero stays vertically centered -->
      <div class="relative z-10"></div>

      <!-- Hero -->
      <div class="relative z-10">
        <div class="flex items-center gap-5 mb-6">
          <img src="/kyrove-icon-512.png" alt="Kyrove" class="w-20 h-20 flex-shrink-0">
          <div class="type-display">{{ APP_NAME }}</div>
        </div>
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
      <div class="relative z-10 type-label text-content-muted pt-8">
        &copy; {{ new Date().getFullYear() }} {{ APP_NAME }} Platform
      </div>
    </div>

    <!-- ── Right panel: Login form ───────────────────────────── -->
    <div class="flex-1 flex items-center justify-center p-8 relative z-10">
      <div class="w-full max-w-sm">
        <!-- Mobile-only wordmark + tagline (hero panel is hidden < lg). Plain centered text —
             corner bars + glow dot live with the hero panel; on mobile a clean
             wordmark reads better than a fragment of that chrome. -->
        <div class="lg:hidden mb-8 flex items-center justify-center gap-4">
          <img src="/kyrove-icon-512.png" alt="Kyrove" class="w-14 h-14 flex-shrink-0">
          <span class="type-display" style="font-size: clamp(32px, 9vw, 44px)">{{ APP_NAME }}</span>
        </div>
        <p class="lg:hidden type-label text-content-muted text-center -mt-4 mb-8">{{ APP_TAGLINE }}</p>

        <div class="relative">
          <!-- Corner bars belong to the form panel, not the wordmark -->
          <div class="corner-bar-tl"></div>
          <div class="corner-bar-bl"></div>

          <div class="p-8 bg-surface-900 border border-[rgba(255,255,255,0.07)]"
            style="clip-path: polygon(8px 0%,100% 0%,calc(100% - 8px) 100%,0% 100%)">

          <div class="mb-8">
            <div class="type-page-title leading-none">Sign In</div>
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

          <!-- Demo section -->
          <div class="demo-section">
            <div class="demo-section-rule">
              <span class="demo-section-line"></span>
              <span class="demo-section-label">or</span>
              <span class="demo-section-line"></span>
            </div>

            <button
              v-if="demoEnabled"
              class="btn-demo"
              @click="showPasscodeModal = true"
            >
              Try Demo
            </button>

            <p v-if="demoEnabled" class="type-prose-sm demo-hint">
              Experience Kyrove as an Emcee, Judge, or Helper
            </p>
          </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Passcode modal -->
    <Teleport to="body">
      <div v-if="showPasscodeModal" class="modal-backdrop" @click="showPasscodeModal = false">
        <div class="modal-content passcode-modal" @click.stop>
          <h2 class="modal-title">Enter Demo Passcode</h2>
          <input
            v-model="passcode"
            type="text"
            class="input-passcode"
            placeholder="Enter passcode"
            autocomplete="off"
            :disabled="passcodeLoading"
          />
          <p v-if="passcodeError" class="error-text">{{ passcodeError }}</p>
          <p v-if="!passcodeError" class="type-prose-sm modal-hint" style="margin-bottom:0.75rem;">Choose a role to start the demo</p>
          <div class="role-buttons">
            <button
              class="role-btn"
              :disabled="!passcode.trim() || passcodeLoading"
              @click="startDemoWithPasscode('EMCEE')"
            >
              <span class="role-btn-icon">🎤</span>
              <span class="role-btn-label">Emcee</span>
              <span class="role-btn-desc">Run audition rounds, view scoreboard</span>
            </button>
            <button
              class="role-btn"
              :disabled="!passcode.trim() || passcodeLoading"
              @click="startDemoWithPasscode('JUDGE')"
            >
              <span class="role-btn-icon">⚖️</span>
              <span class="role-btn-label">Judge</span>
              <span class="role-btn-desc">Score participants, submit feedback</span>
            </button>
            <button
              class="role-btn"
              :disabled="!passcode.trim() || passcodeLoading"
              @click="startDemoWithPasscode('HELPER')"
            >
              <span class="role-btn-icon">🛎️</span>
              <span class="role-btn-label">Helper</span>
              <span class="role-btn-desc">Check-in participants, verify details</span>
            </button>
          </div>
          <p class="type-prose-sm modal-hint">Tap outside to close</p>
        </div>
      </div>
    </Teleport>

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

<style scoped>
.demo-section {
  margin-top: 2rem;
  text-align: center;
}

.demo-section-rule {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.demo-section-label {
  font-family: var(--font-sans);
  font-size: 10px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.4);
  flex-shrink: 0;
}

.demo-section-line {
  flex: 1;
  height: 1px;
  background: var(--surface-600);
}

.demo-section-line {
  flex: 1;
  height: 1px;
  background: var(--surface-600);
}

.btn-demo {
  font-family: var(--font-sans);
  font-size: 13px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.07);
  color: var(--accent-color);
  padding: 0.75rem 2rem;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  cursor: pointer;
  transition: background 0.2s;
}

.btn-demo:hover {
  background: rgba(255,255,255,0.08);
}

.demo-hint {
  margin-top: 0.75rem;
  color: rgba(255,255,255,0.35);
}

.modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  padding: 1rem;
}

.modal-content {
  background: #1a1a1a;
  border: 1px solid rgba(255, 255, 255, 0.12);
  clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);
  padding: 2rem;
  width: 100%;
}

.passcode-modal {
  max-width: 480px;
}

.role-buttons {
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  margin-bottom: 0.5rem;
}

.role-btn {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  width: 100%;
  padding: 0.875rem 1rem;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.07);
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  color: #fff;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
  text-align: left;
}

.role-btn:hover:not(:disabled) {
  background: rgba(255,255,255,0.08);
  border-color: var(--accent-muted);
}

.role-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.role-btn-icon {
  font-size: 28px;
  flex-shrink: 0;
  width: 2.5rem;
  text-align: center;
}

.role-btn-label {
  font-family: var(--font-sans);
  font-size: 14px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--accent-color);
  flex-shrink: 0;
  min-width: 4rem;
}

.role-btn-desc {
  font-family: var(--font-body);
  font-size: 11px;
  color: rgba(255,255,255,0.4);
  line-height: 1.3;
}

.modal-title {
  font-family: var(--font-sans);
  font-size: 20px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #fff;
  text-align: center;
}

.input-passcode {
  width: 100%;
  padding: 0.75rem 1rem;
  background: #0a0a0a;
  border: 1px solid rgba(255,255,255,0.2);
  color: #fff;
  font-family: var(--font-sans);
  font-size: 18px;
  letter-spacing: 0.3em;
  text-align: center;
  margin: 1rem 0;
}

.input-passcode::placeholder {
  color: rgba(255,255,255,0.25);
}

.input-passcode:focus {
  outline: none;
  border-color: var(--accent-color);
  background: #000;
}

.error-text {
  color: var(--primary-500, #ef4444);
  font-family: var(--font-body);
  font-size: 12px;
  margin-bottom: 0.5rem;
}

.btn-primary {
  width: 100%;
  padding: 0.75rem;
  background: var(--accent-color);
  color: var(--color-surface-900, #111);
  font-family: var(--font-sans);
  font-size: 13px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  border: none;
  cursor: pointer;
  transition: filter 0.2s;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-primary:hover:not(:disabled) {
  filter: brightness(0.88);
}

.modal-hint {
  margin-top: 0.75rem;
  color: rgba(255,255,255,0.3);
  text-align: center;
}
</style>
