<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { redeemToken, whoami } from '@/utils/api'
import { useAuthStore, setActiveEvent } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(true)
const error = ref(null)

function redirectByRole(user) {
  const authority = user.role?.[0]?.authority
  if (authority === 'ROLE_JUDGE') router.replace('/judge/session')
  else if (authority === 'ROLE_EMCEE') router.replace('/emcee/session')
  else if (authority === 'ROLE_HELPER') router.replace('/helper/session')
  else router.replace('/')
}

const roleLabel = ref('')
const judgeLabel = ref('')

onMounted(async () => {
  // Read descriptive path params for display
  const roleParam = route.params.role
  const judgeParam = route.params.judgeName
  if (roleParam) {
    roleLabel.value = roleParam.charAt(0).toUpperCase() + roleParam.slice(1)
    if (judgeParam) judgeLabel.value = decodeURIComponent(judgeParam)
  }

  // Short-circuit on in-memory auth (e.g. Pinia rehydrate within same tab).
  // No network call needed.
  if (authStore.user?.authenticated) {
    redirectByRole(authStore.user)
    return
  }

  const token = route.query.t

  // Fast path: token present → go straight to redeem. Skipping the preflight
  // whoami() here saves one round-trip on every session-link login. The
  // backend's sameSession check already handles the "same browser, second
  // tab" case correctly, so no auth is lost.
  if (token) {
    const data = await redeemToken(token)
    if (!data?.authenticated) {
      error.value = data?.error || 'Invalid or expired link.'
      loading.value = false
      return
    }
    authStore.login(data)
    if (data.eventId && data.eventName) {
      setActiveEvent(data.eventId, data.eventName)
    }
    redirectByRole(data)
    return
  }

  // No token in URL: maybe a refresh on an already-authenticated session.
  // Only now do we pay for a whoami round-trip.
  const existing = await whoami().catch(() => null)
  if (existing?.authenticated) {
    authStore.login(existing)
    if (existing.eventId && existing.eventName) {
      setActiveEvent(existing.eventId, existing.eventName)
    }
    redirectByRole(existing)
    return
  }

  error.value = 'No token provided.'
  loading.value = false
})
</script>

<template>
  <div class="token-root">
    <div class="color-bleed"></div>
    <!-- aria-live: dynamic sign-in status is announced to screen readers -->
    <div class="token-card" role="status" aria-live="polite">
      <template v-if="loading">
        <div class="spinner" aria-hidden="true"></div>
        <p class="token-text">SIGNING IN&#8230;</p>
        <p v-if="roleLabel" class="token-role">{{ roleLabel }}<template v-if="judgeLabel"> · {{ judgeLabel }}</template></p>
      </template>
      <template v-else-if="error">
        <p class="token-title">LINK INVALID</p>
        <p class="token-sub">{{ error }}</p>
        <!-- Escape route: error state must answer "how do I go back?" -->
        <router-link to="/login" class="token-link">Go to login</router-link>
      </template>
    </div>
  </div>
</template>

<style scoped>
.token-root {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #111111;
  font-family: 'Oswald', sans-serif;
}

.color-bleed {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(ellipse 60% 35% at 0% 100%, var(--accent-subtle, rgba(255,255,255,0.04)) 0%, transparent 70%),
    radial-gradient(ellipse 60% 35% at 100% 100%, var(--accent-subtle, rgba(255,255,255,0.04)) 0%, transparent 70%);
}

.token-card {
  clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);
  background: #1a1a1a;
  border: 1px solid rgba(255,255,255,0.07);
  padding: 48px 36px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  min-width: 260px;
}

.token-title {
  font-size: 28px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.9);
  text-shadow: 1px 1px 0 var(--accent-muted, rgba(255,255,255,0.08));
}

.token-text {
  font-size: 18px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.6);
}

.token-role {
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--accent-color, rgba(255,255,255,0.7));
  margin-top: 6px;
}

.token-sub {
  font-size: 13px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.45);
  text-align: center;
}

.token-link {
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.12);
  padding: 12px 24px;
  min-height: 44px; /* mobile tap target */
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.85);
  text-decoration: none;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.token-link:hover {
  background: rgba(255,255,255,0.12);
  border-color: rgba(255,255,255,0.25);
}

.spinner {
  width: 32px;
  height: 32px;
  border: 2px solid rgba(255,255,255,0.1);
  border-top-color: rgba(255,255,255,0.7);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
