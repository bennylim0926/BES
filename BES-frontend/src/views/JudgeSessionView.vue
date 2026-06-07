<script setup>
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/utils/auth'
import { getJudgeDivisions, whoami } from '@/utils/api'

const router = useRouter()
const authStore = useAuthStore()

const isJudgeSession = computed(() => !!authStore.judgeName && !!authStore.judgeId)
const eventName = computed(() => authStore.activeEvent?.name || '')
const judgeName = computed(() => authStore.judgeName || '')
const divisions = ref([])
const loading = ref(true)
const error = ref(null)
const sessionReady = ref(false)

async function loadDivisions() {
  loading.value = true
  error.value = null
  try {
    const data = await getJudgeDivisions(authStore.activeEvent.name, authStore.judgeId)
    divisions.value = data ?? []
  } catch {
    error.value = 'Failed to load divisions.'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // Public route — auth store may not be populated. Restore via whoami.
  if (!isJudgeSession.value) {
    try {
      const user = await whoami()
      if (user?.authenticated && user.judgeName) {
        authStore.login(user)
      }
    } catch { /* not authenticated */ }
  }
  if (!authStore.judgeName || !authStore.activeEvent) {
    loading.value = false
    sessionReady.value = false
    return
  }
  sessionReady.value = true
  await loadDivisions()
})

function navigateToAudition(divisionName) {
  localStorage.setItem('selectedGenre', divisionName)
  if (authStore.judgeName) {
    localStorage.setItem('currentJudge', authStore.judgeName)
    localStorage.setItem('selectedRole', 'Judge')
  }
  router.push({ name: 'Audition List' })
}

function navigateToBattle() {
  router.push({ name: 'Battle Judge' })
}
</script>

<template>
  <div class="session-root">
    <div class="color-bleed"></div>

    <!-- Not authenticated / session lost -->
    <div v-if="!loading && !sessionReady" class="session-card">
      <h1 class="session-title">SESSION NOT FOUND</h1>
      <p class="empty-text">Your session could not be restored. Please use your invitation link again, or contact the event organiser for a new link.</p>
    </div>

    <!-- Main hub -->
    <div v-else class="session-card session-card--wide">

      <!-- Title -->
      <h1 class="session-title">JUDGE SESSION</h1>

      <!-- Identity block (locked display) -->
      <div class="info-grid">
        <div class="info-block">
          <span class="info-label">EVENT</span>
          <span class="info-value">{{ eventName }}</span>
        </div>
        <div class="info-block">
          <span class="info-label">JUDGE</span>
          <span class="info-value">{{ judgeName }}</span>
        </div>
      </div>

      <!-- Role guidance -->
      <div class="px-4 py-3 type-label" style="font-size:10px;letter-spacing:0.12em;border-left:3px solid var(--accent-muted);background:var(--accent-subtle)">
        You are logged in as a Judge. Swipe through audition cards to score participants.
      </div>

      <!-- Section header -->
      <div class="section-header">
        <span class="section-label">ASSIGNED DIVISIONS</span>
        <span class="section-rule"></span>
      </div>

      <!-- Loading -->
      <div v-if="loading" class="loading-text">LOADING…</div>

      <!-- Error -->
      <div v-else-if="error" class="flex flex-col items-center gap-3">
        <p class="empty-text">{{ error }}</p>
        <button
          @click="loadDivisions"
          class="para-chip-sm px-4 py-2 type-label text-content-muted hover:text-content-primary transition-colors"
        >RETRY</button>
      </div>

      <!-- Empty -->
      <div v-else-if="divisions.length === 0" class="empty-text">
        No divisions assigned. Contact an organiser to be added to this event's judging panel.
      </div>

      <!-- Division rows with role-aware action buttons -->
      <div v-else class="division-list">
        <div v-for="(d, i) in divisions" :key="i" class="division-row">
          <div class="division-info">
            <span class="division-name">{{ d.divisionName }}</span>
            <span v-if="d.format" class="division-format">{{ d.format }}</span>
          </div>
          <div class="division-actions">
            <button
              v-if="d.isAudition"
              @click="navigateToAudition(d.divisionName)"
              class="action-btn action-btn--audition"
            >AUDITION</button>
            <button
              v-if="d.isBattle"
              @click="navigateToBattle()"
              class="action-btn action-btn--battle"
            >BATTLE</button>
            <span
              v-if="!d.isAudition && !d.isBattle"
              class="empty-text"
              style="font-size:10px"
            >NO ACTIONS</span>
          </div>
        </div>
      </div>

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
  font-family: 'Anton SC', sans-serif;
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

.loading-text,
.empty-text {
  font-size: 13px;
  letter-spacing: 0.05em;
  color: rgba(255,255,255,0.35);
  text-align: center;
}

.division-list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: rgba(255,255,255,0.04);
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
}

.division-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
  background: #1a1a1a;
  flex-wrap: wrap;
}

.division-row + .division-row {
  border-top: 1px solid rgba(255,255,255,0.05);
}

.division-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.division-name {
  font-size: 13px;
  letter-spacing: 0.05em;
  color: rgba(255,255,255,0.85);
}

.division-format {
  font-size: 10px;
  letter-spacing: 0.18em;
  color: rgba(255,255,255,0.35);
  border: 1px solid rgba(255,255,255,0.07);
  padding: 3px 8px;
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
}

.division-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.action-btn {
  font-family: 'Anton SC', sans-serif;
  font-size: 10px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  padding: 8px 16px;
  cursor: pointer;
  transition: all 0.15s ease;
  background: transparent;
  border: 1px solid rgba(255,255,255,0.07);
  color: rgba(255,255,255,0.5);
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
  min-width: 44px;
  min-height: 44px;
  -webkit-tap-highlight-color: transparent;
}

.action-btn--audition:hover,
.action-btn--audition:active {
  color: var(--accent-color, #fff);
  border-color: var(--accent-muted, rgba(255,255,255,0.25));
  background: var(--accent-subtle, rgba(255,255,255,0.04));
}

.action-btn--battle:hover,
.action-btn--battle:active {
  color: rgba(255,255,255,0.85);
  border-color: rgba(255,255,255,0.2);
  background: rgba(255,255,255,0.04);
}

/* Mobile: stack division row vertically */
@media (max-width: 480px) {
  .session-card {
    padding: 32px 20px;
    gap: 20px;
    min-width: 0;
    width: 100%;
  }

  .session-card--wide {
    max-width: 100%;
  }

  .division-row {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;
    padding: 12px 14px;
  }

  .division-actions {
    align-self: flex-end;
  }
}
</style>
