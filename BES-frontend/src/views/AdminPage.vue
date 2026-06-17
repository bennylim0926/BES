<script setup>
import { deleteImage, deleteScore, getAllImages, getFeedbackGroups, addFeedbackGroup, deleteFeedbackGroup, addFeedbackTag, deleteFeedbackTag, getFeedbackTagOverrides, getOrganisers, assignOrganiserToEvent, removeOrganiserFromEvent, createOrganiser, deleteOrganiser, setOrganiserTier } from '@/utils/adminApi';
import { checkInputNull } from '@/utils/utils';
import { computed, onMounted, ref } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import { fetchAllEvents, getAppConfig, postAppConfig, getDemoConfig, updateDemoConfig } from '@/utils/api';

const modalTitle = ref("")
const modalMessage = ref("")
const modalVariant = ref("warning")
const showModal = ref(false)

const openModal = (title, message, variant = 'info') => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = variant
  showModal.value = true
  dynamicHandler.value = () => { showModal.value = false }
}

const events = ref([])
const images = ref([])
const feedbackGroups = ref([])
const feedbackOverrides = ref([]) // [{ globalGroupId, groupName, overridingEventNames[] }]
const overridingEventsForGroup = (id) => {
  const match = feedbackOverrides.value.find(o => o.globalGroupId === id)
  return match?.overridingEventNames ?? []
}
const addGroupInput = ref('')
const addTagInputs = ref({})  // { [groupId]: string }
const dynamicHandler = ref(() => {})

const organisers = ref([])
const newOrganiserUsername = ref('')
const newOrganiserPassword = ref('')
const tierFilter = ref('All')

const filteredOrganisers = computed(() => {
  if (tierFilter.value === 'All') return organisers.value
  return organisers.value.filter(o => o.tier === tierFilter.value.toUpperCase())
})

const onTierChange = async (org, newTier) => {
  if (newTier === org.tier) return
  const prev = org.tier
  org.tier = newTier
  const result = await setOrganiserTier(org.id, newTier)
  if (result?.ok === true) {
    openModal('Tier Updated', `Set "${org.username}" to ${newTier}.`, 'info')
  } else {
    org.tier = prev
    organisers.value = await getOrganisers() ?? []
    openModal('Error', result?.error || 'Failed to update tier.', 'warning')
  }
}

const submitCreateOrganiser = async () => {
  if (checkInputNull(newOrganiserUsername.value) || checkInputNull(newOrganiserPassword.value)) {
    openModal("Validation Error", "Username and password cannot be empty.", "error")
    return
  }
  if (newOrganiserPassword.value.length < 6) {
    openModal("Validation Error", "Password must be at least 6 characters.", "error")
    return
  }
  const username = newOrganiserUsername.value
  const res = await createOrganiser(username, newOrganiserPassword.value)
  if (res?.ok) {
    organisers.value = await getOrganisers() ?? []
    newOrganiserUsername.value = ''
    newOrganiserPassword.value = ''
    openModal("Account Created", `Organiser "${username}" created successfully.`, "info")
  } else {
    openModal("Error", res?.error || "Failed to create organiser.", "warning")
  }
}

const confirmDeleteOrganiser = (id, username) => {
  modalTitle.value = 'Delete Organiser?'
  modalMessage.value = `Are you sure you want to delete ${username}?`
  modalVariant.value = 'warning'
  showModal.value = true
  dynamicHandler.value = async () => {
    const res = await deleteOrganiser(id)
    if (res?.ok) organisers.value = organisers.value.filter(o => o.id !== id)
    showModal.value = false
  }
}

const toggleOrganiserEvent = async (accountId, eventId, isAssigned) => {
  if (isAssigned) {
    await removeOrganiserFromEvent(accountId, eventId)
  } else {
    await assignOrganiserToEvent(accountId, eventId)
  }
  organisers.value = await getOrganisers() ?? []
}

const isEventAssigned = (organiser, eventId) => {
  return organiser.assignedEventIds?.includes(eventId) ?? false
}

const accentInput = ref('#ffffff')
const activeTab = ref('scores')
const tabs = ref(['scores', 'feedback', 'images', 'theme', 'organisers', 'demo'])

const confirmResetScore = (id, title, message) => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = 'warning'
  showModal.value = true
  dynamicHandler.value = async () => {
    await deleteScore(id)
    showModal.value = false
  }
}

const confirmRemoveImage = (name, title, message) => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = 'warning'
  showModal.value = true
  dynamicHandler.value = async () => {
    await submitDeleteImage(name)
    showModal.value = false
  }
}

const submitDeleteImage = async (name) => {
  const res = await deleteImage(name)
  if (res.ok) images.value = images.value.filter(i => i != name)
}

const submitAddGroup = async () => {
  if (checkInputNull(addGroupInput.value)) {
    openModal("Validation Error", "Group name cannot be empty.", "error")
    return
  }
  const res = await addFeedbackGroup(addGroupInput.value)
  if (res?.ok) feedbackGroups.value = await res.json()
  addGroupInput.value = ''
}

const submitDeleteGroup = async (id) => {
  const res = await deleteFeedbackGroup(id)
  if (res?.ok) feedbackGroups.value = feedbackGroups.value.filter(g => g.id !== id)
}

const submitAddTag = async (groupId) => {
  const label = addTagInputs.value[groupId] ?? ''
  if (checkInputNull(label)) return
  const res = await addFeedbackTag(groupId, label)
  if (res?.ok) feedbackGroups.value = await res.json()
  addTagInputs.value[groupId] = ''
}

const submitDeleteTag = async (tagId) => {
  const res = await deleteFeedbackTag(tagId)
  if (res?.ok) {
    feedbackGroups.value = feedbackGroups.value.map(g => ({
      ...g,
      tags: g.tags.filter(t => t.id !== tagId)
    }))
  }
}

const saveAccent = async () => {
  await postAppConfig(accentInput.value)
}

// ── Demo Settings ──────────────────────────────────────────
const demoConfig = ref({ demoEnabled: false, passcode: '', activeSandboxes: 0 })
const showPasscode = ref(false)

async function loadDemoConfig() {
  try {
    const cfg = await getDemoConfig()
    demoConfig.value = cfg
  } catch (e) {
    console.error('Failed to load demo config', e)
  }
}

async function toggleDemoEnabled() {
  const newState = !demoConfig.value.demoEnabled
  await updateDemoConfig({ demoEnabled: newState })
  demoConfig.value.demoEnabled = newState
}

async function regeneratePasscode() {
  await updateDemoConfig({ demoEnabled: demoConfig.value.demoEnabled, regeneratePasscode: true })
  await loadDemoConfig()
}

// ── Demo Sandbox Management ─────────────────────────────────
const sandboxes = ref([])

async function loadSandboxes() {
  try {
    const res = await fetch('/api/v1/admin/demo/sandboxes', { credentials: 'include' })
    sandboxes.value = res.ok ? await res.json() : []
  } catch { sandboxes.value = [] }
}

async function purgeSandbox(eventId) {
  await fetch(`/api/v1/admin/demo/sandboxes/${eventId}`, { method: 'DELETE', credentials: 'include' })
  await loadSandboxes()
  await loadDemoConfig()
}

async function purgeAllSandboxes() {
  if (!confirm('Delete all demo sandboxes?')) return
  await fetch('/api/v1/admin/demo/sandboxes', { method: 'DELETE', credentials: 'include' })
  await loadSandboxes()
  await loadDemoConfig()
}

onMounted(async () => {
  events.value = await fetchAllEvents() ?? []
  images.value = await getAllImages() ?? []
  feedbackGroups.value = await getFeedbackGroups() ?? []
  feedbackOverrides.value = await getFeedbackTagOverrides() ?? []
  organisers.value = await getOrganisers() ?? []
  const cfg = await getAppConfig()
  accentInput.value = cfg?.accentColor ?? '#ffffff'
  loadDemoConfig()
  loadSandboxes()
})
</script>

<template>
  <div class="page-container relative">
    <div class="color-bleed"></div>
    <div class="relative z-10 space-y-8">

      <!-- Page header — h1 for document outline -->
      <div>
        <h1 class="type-page-title">Admin</h1>
      </div>

      <!-- Section tabs — tablist semantics expose the navigation pattern -->
      <div class="tab-bar mb-6" role="tablist" aria-label="Admin sections">
        <button
          v-for="tab in tabs"
          :key="tab"
          role="tab"
          :aria-selected="activeTab === tab"
          @click="activeTab = tab"
          class="tab-item"
          :class="{ 'is-active': activeTab === tab }"
        >{{ tab }}</button>
      </div>


      <!-- ── Reset Scores ───────────────────────────────────── -->
      <div v-if="activeTab === 'scores'">
        <div class="section-rule mb-4">
          <span class="section-rule-label">Reset Scores</span>
          <div class="section-rule-line"></div>
        </div>
        <p class="type-prose mb-4">Permanently removes all scores for an event.</p>

        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
          <div
            v-for="e in events"
            :key="e.id"
            class="card-hover p-3 relative flex items-center justify-between"
          >
            <div class="corner-bar-tl"></div>
            <span class="type-name text-content-secondary truncate flex-1">{{ e.name }}</span>
            <button
              @click="confirmResetScore(e.id, 'Reset Scores?', `This will permanently delete all scores for ${e.name}.`)"
              class="ml-2 flex-shrink-0 para-chip-sm type-label px-2 py-1 text-red-400 hover:bg-red-950 transition-all"
            >
              Reset
            </button>
          </div>
        </div>
      </div>

      <!-- ── Feedback Tags ──────────────────────────────────── -->
      <div v-if="activeTab === 'feedback'">
        <div class="section-rule mb-4">
          <span class="section-rule-label">Feedback Tags</span>
          <span class="badge-neutral type-label px-2 py-0.5">{{ feedbackGroups.length }} groups</span>
          <div class="section-rule-line"></div>
        </div>

        <div class="flex gap-3 mb-6">
          <input
            v-model="addGroupInput"
            type="text"
            placeholder="New group name…"
            aria-label="New feedback group name"
            class="input-base flex-1 max-w-xs"
            @keyup.enter="submitAddGroup"
          />
          <button @click="submitAddGroup" class="para-chip type-label border-accent flex items-center gap-2 px-4 py-2 min-h-[44px]">
            <i class="pi pi-plus text-sm"></i>
            Add Group
          </button>
        </div>

        <div class="space-y-5">
          <div
            v-for="group in feedbackGroups"
            :key="group.id"
            class="card-hover p-4 relative"
          >
            <div class="corner-bar-tl"></div>
            <div class="flex items-center justify-between mb-3">
              <div class="flex items-center gap-2 min-w-0">
                <span class="type-name text-content-primary truncate">{{ group.name }}</span>
                <span
                  v-if="overridingEventsForGroup(group.id).length"
                  class="para-chip-sm type-label text-accent shrink-0 px-2 py-0.5"
                  :title="`Overridden in: ${overridingEventsForGroup(group.id).join(', ')}`"
                  style="font-size: 9px; letter-spacing: 0.18em;"
                >
                  OVERRIDDEN IN {{ overridingEventsForGroup(group.id).length }} EVENT{{ overridingEventsForGroup(group.id).length === 1 ? '' : 'S' }}
                </span>
              </div>
              <button
                @click="submitDeleteGroup(group.id)"
                class="w-8 h-8 flex items-center justify-center text-content-muted hover:text-red-400 hover:bg-red-950 transition-all"
                title="Delete group"
                :aria-label="`Delete group ${group.name}`"
              >
                <i class="pi pi-times text-xs" aria-hidden="true"></i>
              </button>
            </div>

            <div class="flex flex-wrap gap-2 mb-3">
              <div
                v-for="tag in group.tags"
                :key="tag.id"
                class="para-chip-sm px-3 py-1 type-name-sm text-content-secondary flex items-center gap-1.5"
              >
                {{ tag.label }}
                <!-- aria-label + padding: tiny × target gets an accessible name and a usable hit area -->
                <button
                  @click="submitDeleteTag(tag.id)"
                  :aria-label="`Remove tag ${tag.label}`"
                  class="text-content-muted hover:text-red-400 transition-colors leading-none p-1.5 -m-1"
                >
                  <i class="pi pi-times" style="font-size: 0.6rem" aria-hidden="true"></i>
                </button>
              </div>
              <p v-if="!group.tags?.length" class="type-label text-content-muted py-1">No tags yet</p>
            </div>

            <div class="flex gap-2">
              <input
                v-model="addTagInputs[group.id]"
                type="text"
                :placeholder="`Add tag to ${group.name}…`"
                :aria-label="`New tag for ${group.name}`"
                class="input-base flex-1 text-sm py-1.5"
                @keyup.enter="submitAddTag(group.id)"
              />
              <button
                @click="submitAddTag(group.id)"
                :aria-label="`Add tag to ${group.name}`"
                class="para-chip-sm type-label px-3 py-1.5 min-h-[44px] text-content-secondary hover:text-content-primary transition-colors"
              >
                <i class="pi pi-plus text-xs" aria-hidden="true"></i>
              </button>
            </div>
          </div>

          <p v-if="feedbackGroups.length === 0" class="type-label text-content-muted py-2">
            No groups configured. Create groups above, then add tags to each.
          </p>
        </div>
      </div>

      <!-- ── Uploaded Images ────────────────────────────────── -->
      <div v-if="activeTab === 'images'">
        <div class="section-rule mb-4">
          <span class="section-rule-label">Uploaded Images</span>
          <span class="badge-neutral type-label px-2 py-0.5">{{ images.length }}</span>
          <div class="section-rule-line"></div>
        </div>

        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
          <div
            v-for="img in images"
            :key="img"
            class="card-hover p-3 relative flex items-center justify-between"
          >
            <div class="corner-bar-tl"></div>
            <span class="type-body text-content-secondary truncate flex-1">{{ img }}</span>
            <button
              @click="confirmRemoveImage(img, `Delete ${img}?`, 'Are you sure you want to delete this image?')"
              :aria-label="`Delete image ${img}`"
              class="ml-2 flex-shrink-0 w-8 h-8 flex items-center justify-center text-content-muted hover:text-red-400 hover:bg-red-950 transition-all"
            >
              <i class="pi pi-times text-xs" aria-hidden="true"></i>
            </button>
          </div>
          <div v-if="images.length === 0" class="col-span-full type-label text-content-muted py-4">
            No images uploaded
          </div>
        </div>
      </div>

      <!-- ── Organisers ────────────────────────────────────── -->
      <div v-if="activeTab === 'organisers'">
        <div class="section-rule mb-4">
          <span class="section-rule-label">Organisers</span>
          <span class="badge-neutral type-label px-2 py-0.5">{{ organisers.length }}</span>
          <div class="section-rule-line"></div>
        </div>

        <!-- stacks on mobile so inputs don't get squashed -->
        <div class="flex flex-col sm:flex-row gap-3 mb-5">
          <input
            v-model="newOrganiserUsername"
            type="text"
            placeholder="Username…"
            aria-label="New organiser username"
            autocomplete="off"
            class="input-base flex-1 sm:max-w-xs"
            @keyup.enter="submitCreateOrganiser"
          />
          <input
            v-model="newOrganiserPassword"
            type="password"
            placeholder="Password…"
            aria-label="New organiser password"
            autocomplete="new-password"
            class="input-base flex-1 sm:max-w-xs"
            @keyup.enter="submitCreateOrganiser"
          />
          <button @click="submitCreateOrganiser" class="para-chip type-label border-accent flex items-center gap-2 px-4 py-2 min-h-[44px]">
            <i class="pi pi-user-plus text-sm"></i>
            Create Account
          </button>
        </div>

        <div v-if="organisers.length > 0" class="flex flex-wrap gap-2 mb-4">
          <button
            v-for="f in ['All', 'Pro', 'Max']"
            :key="f"
            @click="tierFilter = f"
            :aria-pressed="tierFilter === f"
            class="para-chip-sm px-4 py-2 type-label transition-all duration-150"
            :class="tierFilter === f ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
          >{{ f }}</button>
        </div>

        <p class="type-prose mb-4">Assign or remove events for each organiser.</p>

        <div class="space-y-3">
          <div
            v-for="org in filteredOrganisers"
            :key="org.id"
            class="card-hover p-4 relative"
          >
            <div class="corner-bar-tl"></div>
            <div class="flex items-center justify-between mb-3">
              <span class="type-name text-content-primary">{{ org.username }}</span>
              <div class="flex items-center gap-2">
                <select
                  :value="org.tier"
                  @change="(e) => onTierChange(org, e.target.value)"
                  class="type-name-sm px-2.5 py-1.5 para-chip-sm bg-transparent text-content-secondary"
                >
                  <option value="PRO">PRO</option>
                  <option value="MAX">MAX</option>
                </select>
                <button
                  @click="confirmDeleteOrganiser(org.id, org.username)"
                  class="w-8 h-8 flex items-center justify-center text-content-muted hover:text-red-400 hover:bg-red-950 transition-all"
                  title="Delete organiser"
                  :aria-label="`Delete organiser ${org.username}`"
                >
                  <i class="pi pi-times text-xs" aria-hidden="true"></i>
                </button>
              </div>
            </div>

            <div class="flex flex-wrap gap-2">
              <!-- aria-pressed + check icon: assigned state reads via icon + semantics, not green alone -->
              <button
                v-for="e in events"
                :key="e.id"
                @click="toggleOrganiserEvent(org.id, e.id, isEventAssigned(org, e.id))"
                :aria-pressed="isEventAssigned(org, e.id)"
                class="para-chip-sm type-name-sm px-3 py-1.5 transition-all duration-150 inline-flex items-center gap-1.5"
                :class="isEventAssigned(org, e.id) ? 'text-green-300 border-green-500/50 bg-green-500/15' : 'text-content-muted hover:text-content-primary hover:border-[color:var(--accent-muted)]'"
              >
                <i v-if="isEventAssigned(org, e.id)" class="pi pi-check text-xs" aria-hidden="true"></i>
                {{ e.name }}
              </button>
            </div>

            <p v-if="events.length === 0" class="type-label text-content-muted py-1">No events available</p>
          </div>

          <p v-if="organisers.length === 0" class="type-label text-content-muted py-4">
            No organiser accounts yet. Use the form above to create one.
          </p>
        </div>
      </div>

      <!-- ── Theme Config ───────────────────────────────────── -->
      <div v-if="activeTab === 'theme'">
        <div class="section-rule mb-6">
          <span class="section-rule-label">Accent Color</span>
          <div class="section-rule-line"></div>
        </div>
        <div class="card-hover p-6 relative">
          <div class="corner-bar-tl"></div>
          <p class="type-prose mb-4">Sets the global accent color for all connected clients in real-time.</p>
          <div class="flex items-center gap-4">
            <input type="color" v-model="accentInput" aria-label="Accent color picker" class="w-12 h-11 cursor-pointer bg-transparent border-0" />
            <span class="type-body text-accent">{{ accentInput }}</span>
            <button @click="saveAccent" class="para-chip type-label border-accent flex items-center gap-2 px-4 py-2 min-h-[44px]">
              <i class="pi pi-check text-sm"></i>
              Apply Accent Color
            </button>
          </div>
        </div>
      </div>

      <!-- ── Demo Settings ──────────────────────────────────── -->
      <div v-if="activeTab === 'demo'">
        <div class="section-rule mb-4">
          <span class="section-rule-label">Demo Settings</span>
          <div class="section-rule-line"></div>
        </div>

        <div class="card-hover p-4 relative">
          <div class="corner-bar-tl"></div>

          <div class="setting-row mb-4">
            <span class="type-prose">Enable demo system</span>
            <label class="toggle-switch">
              <input
                type="checkbox"
                :checked="demoConfig.demoEnabled"
                @change="toggleDemoEnabled"
              />
              <span class="toggle-slider"></span>
            </label>
          </div>

          <div class="setting-row mb-4">
            <span class="type-prose">Passcode</span>
            <div class="passcode-controls flex items-center gap-2">
              <input
                :type="showPasscode ? 'text' : 'password'"
                :value="showPasscode ? demoConfig.passcode : '••••••••'"
                class="input-base flex-1 max-w-[180px] type-body"
                readonly
              />
              <button @click="showPasscode = !showPasscode" class="para-chip-sm type-label px-3 py-1.5 min-h-[44px]">
                {{ showPasscode ? 'Hide' : 'Show' }}
              </button>
              <button @click="regeneratePasscode" class="para-chip-sm type-label px-3 py-1.5 min-h-[44px]">
                Regenerate
              </button>
            </div>
          </div>

          <p class="type-prose-sm text-content-muted mb-3">
            Active sandboxes: {{ demoConfig.activeSandboxes }}
          </p>

          <!-- Sandbox list -->
          <div v-if="sandboxes.length" class="border-t border-surface-600 pt-3 mt-3">
            <div class="flex items-center justify-between mb-3">
              <span class="type-label text-content-muted">Sandboxes</span>
              <button @click="purgeAllSandboxes" class="para-chip-sm px-3 py-1 min-h-[36px] text-red-400 border-red-400/30">
                Purge All
              </button>
            </div>
            <div v-for="s in sandboxes" :key="s.eventId" class="flex items-center justify-between py-2 border-b border-surface-600/50 last:border-b-0">
              <div>
                <span class="type-body block">{{ s.eventName }}</span>
                <span class="type-prose-sm text-content-muted">{{ s.activeTokens }} active token(s)</span>
              </div>
              <button @click="purgeSandbox(s.eventId)" class="para-chip-sm px-3 py-1 min-h-[36px] text-red-400 border-red-400/30">
                Purge
              </button>
            </div>
          </div>
        </div>
      </div>

    </div>

    <ActionDoneModal
      :show="showModal"
      :title="modalTitle"
      :variant="modalVariant"
      @accept="() => { dynamicHandler() }"
      @close="() => { showModal = false }"
    >
      <p class="type-body text-content-secondary">{{ modalMessage }}</p>
    </ActionDoneModal>
  </div>
</template>
