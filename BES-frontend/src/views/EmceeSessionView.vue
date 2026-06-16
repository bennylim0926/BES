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

const showCategoryPicker = ref(false)
const categories = ref([])

onMounted(async () => {
  // Clear any previously selected category so emcee always picks fresh
  localStorage.removeItem('selectedCategory')

  if (!authStore.user) {
    try {
      const user = await whoami()
      if (user?.authenticated) authStore.login(user)
    } catch { /* not authenticated */ }
  }
  loading.value = false
  sessionReady.value = !!authStore.user && !!authStore.activeEvent
  if (authStore.activeEvent?.name) {
    authStore.fetchEventBattleEnabled(authStore.activeEvent.name)
    categories.value = await getCategoriesByEvent(authStore.activeEvent.name) ?? []
  }
})

const ALL_LINKS = [
  { key: 'audition', label: 'Audition List', route: 'Audition List', icon: 'pi-list' },
  { key: 'score',    label: 'Score',         route: 'Score',         icon: 'pi-chart-bar' },
  { key: 'battle',   label: 'Battle Control', route: 'Battle Control', icon: 'pi-bolt' },
]

const LINKS = computed(() =>
  ALL_LINKS.filter(l => l.key !== 'battle' || authStore.activeEventBattleEnabled)
)

function handleNavClick(link) {
  if (link.key === 'audition') {
    showCategoryPicker.value = true
  } else {
    router.push({ name: link.route })
  }
}

function selectCategory(categoryName) {
  localStorage.setItem('selectedCategory', categoryName)
  localStorage.setItem('selectedRole', 'Emcee')
  router.push({ name: 'Audition List' })
}
</script>

<template>
  <div class="session-root">
    <div class="color-bleed"></div>

    <!-- Not authenticated / session lost -->
    <div v-if="!loading && !sessionReady" class="session-card">
      <h1 class="session-title">SESSION NOT FOUND</h1>
      <p class="empty-text">Your session could not be restored. Please use your invitation link again, or contact the event organiser for a new link.</p>
      <router-link to="/login" class="session-link">Go to login</router-link>
    </div>

    <!-- Main hub -->
    <div v-else class="session-card session-card--wide">

      <!-- Title -->
      <h1 class="session-title">EMCEE SESSION</h1>

      <!-- Identity block -->
      <div class="info-grid">
        <div class="info-block">
          <span class="info-label">EVENT</span>
          <span class="info-value">{{ eventName }}</span>
        </div>
        <div class="info-block">
          <span class="info-label">ROLE</span>
          <span class="info-value">Emcee</span>
        </div>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="loading-text" role="status">LOADING…</div>

      <!-- Category picker (shown after clicking Audition List) -->
      <template v-else-if="showCategoryPicker">
        <div class="section-header">
          <button class="back-btn" @click="showCategoryPicker = false">‹ BACK</button>
          <span class="section-label">SELECT CATEGORY</span>
          <span class="section-rule"></span>
        </div>
        <div class="category-list">
          <button
            v-for="cat in categories"
            :key="cat.eventCategoryId"
            @click="selectCategory(cat.name)"
            class="category-btn"
          >
            <span class="category-btn-name">{{ cat.name }}</span>
            <i class="pi pi-chevron-right category-btn-arrow" aria-hidden="true"></i>
          </button>
          <div v-if="categories.length === 0" class="empty-text">No categories found for this event.</div>
        </div>
      </template>

      <!-- Navigation grid -->
      <template v-else>
        <div class="section-header">
          <span class="section-label">NAVIGATION</span>
          <span class="section-rule"></span>
        </div>
        <div class="nav-grid">
          <button
            v-for="link in LINKS"
            :key="link.key"
            @click="handleNavClick(link)"
            class="nav-btn"
          >
            <i class="pi nav-btn-icon" :class="link.icon" aria-hidden="true"></i>
            <span class="nav-btn-label">{{ link.label }}</span>
          </button>
        </div>
      </template>

    </div>
  </div>
</template>

<style scoped>
.session-root {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #111111;
  font-family: 'Oswald', sans-serif;
  text-transform: uppercase;
  padding: 16px;
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

.session-card--wide {
  max-width: 640px;
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

.back-btn {
  background: transparent;
  border: none;
  color: rgba(255,255,255,0.4);
  font-family: 'Oswald', sans-serif;
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  cursor: pointer;
  padding: 0;
  white-space: nowrap;
  transition: color 0.15s ease;
}
.back-btn:hover { color: rgba(255,255,255,0.85); }

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
  min-height: 44px;
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

/* Category picker */
.category-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.category-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: transparent;
  border: 1px solid rgba(255,255,255,0.07);
  color: rgba(255,255,255,0.7);
  font-family: 'Oswald', sans-serif;
  font-size: 13px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  transition: all 0.15s ease;
  min-height: 48px;
  -webkit-tap-highlight-color: transparent;
}
.category-btn:hover,
.category-btn:active {
  color: var(--accent-color, #fff);
  border-color: var(--accent-muted, rgba(255,255,255,0.25));
  background: var(--accent-subtle, rgba(255,255,255,0.04));
}
.category-btn-name { flex: 1; text-align: left; }
.category-btn-arrow { font-size: 10px; opacity: 0.4; }

/* Navigation grid */
.nav-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
}

.nav-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 24px 12px;
  background: transparent;
  border: 1px solid rgba(255,255,255,0.07);
  color: rgba(255,255,255,0.6);
  font-family: 'Oswald', sans-serif;
  text-transform: uppercase;
  cursor: pointer;
  transition: all 0.15s ease;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  min-height: 88px;
  -webkit-tap-highlight-color: transparent;
}

.nav-btn:hover,
.nav-btn:active {
  color: var(--accent-color, #fff);
  border-color: var(--accent-muted, rgba(255,255,255,0.25));
  background: var(--accent-subtle, rgba(255,255,255,0.04));
}

.nav-btn-icon {
  font-size: 1.4rem;
}

.nav-btn-label {
  font-size: 11px;
  letter-spacing: 0.12em;
  text-align: center;
  line-height: 1.3;
}

/* Mobile */
@media (max-width: 480px) {
  .session-root {
    padding: 12px;
    align-items: flex-start;
    padding-top: 32px;
  }

  .session-card {
    padding: 28px 20px;
    gap: 20px;
    min-width: 0;
    width: 100%;
  }

  .session-card--wide {
    max-width: 100%;
  }

  .session-title {
    font-size: 24px;
  }

  .info-value {
    font-size: 15px;
  }

  .nav-grid {
    grid-template-columns: 1fr 1fr;
    gap: 10px;
  }

  .nav-btn {
    flex-direction: column;
    align-items: center;
    gap: 12px;
    padding: 22px 12px;
    min-height: 96px;
  }

  .nav-btn-icon {
    font-size: 1.7rem;
  }

  .nav-btn-label {
    font-size: 12px;
    letter-spacing: 0.1em;
  }
}
</style>
