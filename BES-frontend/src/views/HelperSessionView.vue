<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/utils/auth'
import { whoami, getCategoriesByEvent } from '@/utils/api'

const router = useRouter()
const authStore = useAuthStore()

const eventName = computed(() => authStore.activeEvent?.name || '')
const sessionReady = ref(false)
const loading = ref(true)
const linkCopied = ref(false)
const categories = ref([])
const copiedCategoryId = ref(null)

onMounted(async () => {
  if (!authStore.user) {
    try {
      const user = await whoami()
      if (user?.authenticated) authStore.login(user)
    } catch { /* not authenticated */ }
  }
  loading.value = false
  sessionReady.value = !!authStore.user && !!authStore.activeEvent
  if (sessionReady.value && eventName.value) {
    categories.value = await getCategoriesByEvent(eventName.value) ?? []
  }
})

function displayUrlFor(categoryName) {
  return `${window.location.origin}/audition/display?event=${encodeURIComponent(eventName.value)}&category=${encodeURIComponent(categoryName)}`
}

function copyDisplayUrl(category) {
  copyToClipboard(displayUrlFor(category.name))
  copiedCategoryId.value = category.eventCategoryId
  setTimeout(() => { copiedCategoryId.value = null }, 2000)
}

function copyToClipboard(text) {
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.left = '-9999px'
  ta.style.top = '-9999px'
  ta.setAttribute('readonly', '')
  document.body.appendChild(ta)
  ta.select()
  ta.setSelectionRange(0, text.length)
  document.execCommand('copy')
  document.body.removeChild(ta)
}

function copyAuditionScreenLink() {
  if (!authStore.activeEvent?.name) return
  copyToClipboard(window.location.origin + '/event/audition-number?event=' + encodeURIComponent(authStore.activeEvent.name))
  linkCopied.value = true
  setTimeout(() => { linkCopied.value = false }, 2000)
}

function goToEventDetails() {
  if (authStore.activeEvent) {
    router.push({
      name: 'Event Details',
      params: { eventName: authStore.activeEvent.name },
      query: authStore.activeEvent.folderID ? { folderID: authStore.activeEvent.folderID } : {}
    })
  }
}
</script>

<template>
  <div class="session-root">
    <div class="color-bleed"></div>

    <!-- Not authenticated / session lost -->
    <div v-if="!loading && !sessionReady" class="session-card">
      <h1 class="session-title">SESSION NOT FOUND</h1>
      <p class="empty-text">Your session could not be restored. Please use your invitation link again, or contact the event organiser for a new link.</p>
      <!-- Escape route: dead-end error state must offer a way forward -->
      <router-link to="/login" class="session-link">Go to login</router-link>
    </div>

    <!-- Main hub -->
    <div v-else class="session-card">

      <!-- Title -->
      <h1 class="session-title">HELPER SESSION</h1>

      <!-- Identity block -->
      <div class="info-grid">
        <div class="info-block">
          <span class="info-label">EVENT</span>
          <span class="info-value">{{ eventName }}</span>
        </div>
        <div class="info-block">
          <span class="info-label">ROLE</span>
          <span class="info-value">Helper</span>
        </div>
      </div>

      <!-- Role guidance -->
      <div class="px-4 py-3 type-label" style="font-size:10px;letter-spacing:0.12em;border-left:3px solid var(--accent-muted);background:var(--accent-subtle)">
        You are logged in as a Helper. Use the Event Day tab to register walk-ins and manage check-ins.
      </div>

      <!-- Section header -->
      <div class="section-header">
        <span class="section-label">NAVIGATION</span>
        <span class="section-rule"></span>
      </div>

      <!-- Loading — role=status announces the async state change -->
      <div v-if="loading" class="loading-text" role="status">LOADING…</div>

      <!-- Navigation buttons -->
      <div v-else class="nav-list">
        <button
          @click="goToEventDetails"
          class="nav-btn"
        >
          <i class="pi pi-calendar nav-btn-icon" aria-hidden="true"></i>
          <span class="nav-btn-label">Event Details</span>
        </button>

        <!-- aria-live: copy confirmation is announced, success reads via icon + label, not color alone -->
        <button
          @click="copyAuditionScreenLink"
          class="nav-btn"
          :class="linkCopied ? 'nav-btn--copied' : ''"
          :disabled="!authStore.activeEvent?.name"
          :style="!authStore.activeEvent?.name ? 'opacity:0.35;cursor:not-allowed' : ''"
          aria-live="polite"
        >
          <i class="pi nav-btn-icon" :class="linkCopied ? 'pi-check' : 'pi-hashtag'" aria-hidden="true"></i>
          <span class="nav-btn-label">{{ linkCopied ? 'Link Copied!' : 'Audition Screen' }}</span>
        </button>
      </div>

      <!-- Display URLs per category — for OBS / projector setup -->
      <div v-if="!loading && categories.length > 0" class="section-header">
        <span class="section-label">DISPLAY URLS</span>
        <span class="section-rule"></span>
      </div>
      <p v-if="!loading && categories.length > 0" class="display-hint">
        Paste these into OBS browser sources or open in a projector browser. No login required.
      </p>
      <div v-if="!loading && categories.length > 0" class="display-list">
        <button
          v-for="cat in categories"
          :key="cat.eventCategoryId"
          @click="copyDisplayUrl(cat)"
          class="display-btn"
          :class="copiedCategoryId === cat.eventCategoryId ? 'display-btn--copied' : ''"
          aria-live="polite"
        >
          <span class="display-btn-name">{{ cat.name }}</span>
          <span class="display-btn-action">
            <i class="pi" :class="copiedCategoryId === cat.eventCategoryId ? 'pi-check' : 'pi-copy'" aria-hidden="true"></i>
            {{ copiedCategoryId === cat.eventCategoryId ? 'Copied' : 'Copy URL' }}
          </span>
        </button>
      </div>

    </div>
  </div>
</template>

<style scoped>
.session-root {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  background: #111111;
  font-family: 'Oswald', sans-serif;
  text-transform: uppercase;
  padding: 32px 16px;
  overflow-y: auto;
}

.color-bleed {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(ellipse 60% 35% at 0% 100%, var(--accent-subtle, rgba(255,255,255,0.04)) 0%, transparent 70%),
    radial-gradient(ellipse 60% 35% at 100% 100%, var(--accent-subtle, rgba(255,255,255,0.04)) 0%, transparent 70%);
}

.session-card {
  clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);
  background: #1a1a1a;
  border: 1px solid rgba(255,255,255,0.07);
  padding: 48px 36px;
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 380px;
  max-width: 520px;
  width: 90%;
  position: relative;
  z-index: 1;
}

.session-title {
  font-size: 28px;
  letter-spacing: 0.06em;
  color: rgba(255,255,255,0.9);
  text-shadow: 1px 1px 0 var(--accent-muted, rgba(255,255,255,0.08));
  text-align: center;
  margin: 0;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.info-block {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 10px;
  letter-spacing: 0.22em;
  color: rgba(255,255,255,0.4);
}

.info-value {
  font-size: 13px;
  letter-spacing: 0.05em;
  color: rgba(255,255,255,0.85);
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.section-label {
  font-size: 10px;
  letter-spacing: 0.22em;
  color: rgba(255,255,255,0.4);
  white-space: nowrap;
}

.section-rule {
  flex: 1;
  height: 1px;
  background: rgba(255,255,255,0.07);
}

.loading-text,
.empty-text {
  font-size: 13px;
  letter-spacing: 0.05em;
  color: rgba(255,255,255,0.35);
  text-align: center;
}

.session-link {
  align-self: center;
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
  background: rgba(255,255,255,0.06);
  border: 1px solid rgba(255,255,255,0.12);
  padding: 12px 24px;
  min-height: 44px; /* mobile tap target */
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  letter-spacing: 0.18em;
  color: rgba(255,255,255,0.85);
  text-decoration: none;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.session-link:hover {
  background: rgba(255,255,255,0.12);
  border-color: rgba(255,255,255,0.25);
}

.nav-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.nav-btn {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px 24px;
  background: transparent;
  border: 1px solid rgba(255,255,255,0.07);
  color: rgba(255,255,255,0.6);
  font-family: 'Oswald', sans-serif;
  text-transform: uppercase;
  cursor: pointer;
  transition: all 0.15s ease;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  min-height: 56px;
  -webkit-tap-highlight-color: transparent;
  width: 100%;
}

.nav-btn:hover,
.nav-btn:active {
  color: var(--accent-color, #fff);
  border-color: var(--accent-muted, rgba(255,255,255,0.25));
  background: var(--accent-subtle, rgba(255,255,255,0.04));
}

.nav-btn--copied {
  color: #4ade80;
  border-color: rgba(74,222,128,0.3);
  background: rgba(74,222,128,0.06);
}

.nav-btn-icon {
  font-size: 1.2rem;
  width: 1.5rem;
  text-align: center;
  flex-shrink: 0;
}

.nav-btn-label {
  font-size: 13px;
  letter-spacing: 0.05em;
}

.display-hint {
  font-size: 11px;
  letter-spacing: 0.06em;
  color: rgba(255,255,255,0.35);
  text-transform: none;
  margin: -8px 0 4px 0;
}

.display-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.display-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  background: transparent;
  border: 1px solid rgba(255,255,255,0.07);
  color: rgba(255,255,255,0.7);
  font-family: 'Oswald', sans-serif;
  text-transform: uppercase;
  cursor: pointer;
  transition: all 0.15s ease;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  min-height: 48px;
  -webkit-tap-highlight-color: transparent;
  width: 100%;
}

.display-btn:hover,
.display-btn:active {
  color: var(--accent-color, #fff);
  border-color: var(--accent-muted, rgba(255,255,255,0.25));
  background: var(--accent-subtle, rgba(255,255,255,0.04));
}

.display-btn--copied {
  color: #4ade80;
  border-color: rgba(74,222,128,0.3);
  background: rgba(74,222,128,0.06);
}

.display-btn-name {
  font-size: 13px;
  letter-spacing: 0.08em;
  text-align: left;
  flex: 1;
}

.display-btn-action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  letter-spacing: 0.18em;
  opacity: 0.7;
  white-space: nowrap;
}

/* Mobile */
@media (max-width: 480px) {
  .session-card {
    padding: 32px 20px;
    gap: 20px;
    min-width: 0;
    width: 100%;
  }

  .nav-btn {
    padding: 16px 20px;
  }
}
</style>
