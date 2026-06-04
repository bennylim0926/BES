<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { redeemToken } from '@/utils/api'
import { useAuthStore, setActiveEvent } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const loading = ref(true)
const error = ref(null)

onMounted(async () => {
  const token = route.query.t
  if (!token) {
    error.value = 'No token provided.'
    loading.value = false
    return
  }
  const data = await redeemToken(token)
  if (!data || !data.authenticated) {
    error.value = data?.error || 'Invalid or expired link.'
    loading.value = false
    return
  }
  authStore.login(data)
  if (data.eventId && data.eventName) {
    setActiveEvent(data.eventId, data.eventName)
  }
  const authority = data.role?.[0]?.authority
  if (authority === 'ROLE_JUDGE') {
    router.replace('/battle/judge')
  } else if (authority === 'ROLE_EMCEE') {
    router.replace('/event/audition-list')
  } else if (authority === 'ROLE_HELPER') {
    router.replace(`/events/${encodeURIComponent(data.eventName)}`)
  } else {
    router.replace('/')
  }
})
</script>

<template>
  <div class="token-root">
    <div class="color-bleed"></div>
    <div class="token-card">
      <template v-if="loading">
        <div class="spinner"></div>
        <p class="token-text">SIGNING IN&#8230;</p>
      </template>
      <template v-else-if="error">
        <p class="token-title">LINK INVALID</p>
        <p class="token-sub">{{ error }}</p>
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
  font-family: 'Anton SC', sans-serif;
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

.token-sub {
  font-size: 13px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.45);
  text-align: center;
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
