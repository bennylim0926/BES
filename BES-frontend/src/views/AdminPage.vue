<script setup>
import { deleteImage, deleteScore, getAllImages, getFeedbackGroups, addFeedbackGroup, deleteFeedbackGroup, addFeedbackTag, deleteFeedbackTag, getFeedbackTagOverrides, getOrganisers, assignOrganiserToEvent, removeOrganiserFromEvent, createOrganiser, deleteOrganiser, setOrganiserTier, resetOrganiserPassword, renameOrganiser } from '@/utils/adminApi';
import { checkInputNull } from '@/utils/utils';
import { computed, onMounted, ref } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import LoadingOverlay from '@/components/LoadingOverlay.vue';
import { fetchAllEvents, deleteEvent, getAppConfig, postAppConfig, saveSheetConfig, getDemoConfig, updateDemoConfig } from '@/utils/api';

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

const loading = ref(true)
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

// Per-organiser inline edit state: { [id]: { mode: 'rename'|'password'|null, value: '' } }
const orgEdit = ref({})

const openRename = (org) => {
  orgEdit.value = { ...orgEdit.value, [org.id]: { mode: 'rename', value: org.username } }
}
const openResetPw = (org) => {
  orgEdit.value = { ...orgEdit.value, [org.id]: { mode: 'password', value: '' } }
}
const cancelEdit = (id) => {
  const next = { ...orgEdit.value }
  delete next[id]
  orgEdit.value = next
}

const submitRename = async (org) => {
  const state = orgEdit.value[org.id]
  const newName = (state?.value ?? '').trim()
  if (!newName) {
    openModal('Validation Error', 'Username cannot be empty.', 'error')
    return
  }
  if (newName === org.username) { cancelEdit(org.id); return }
  const res = await renameOrganiser(org.id, newName)
  if (res.ok) {
    organisers.value = await getOrganisers() ?? []
    cancelEdit(org.id)
    openModal('Username Updated', `Renamed to "${newName}".`, 'info')
  } else {
    openModal('Error', res.error || 'Failed to rename organiser.', 'warning')
  }
}

const submitResetPw = async (org) => {
  const state = orgEdit.value[org.id]
  const newPw = state?.value ?? ''
  if (newPw.length < 6) {
    openModal('Validation Error', 'Password must be at least 6 characters.', 'error')
    return
  }
  const res = await resetOrganiserPassword(org.id, newPw)
  if (res.ok) {
    cancelEdit(org.id)
    openModal('Password Reset', `New password set for "${org.username}".`, 'info')
  } else {
    openModal('Error', res.error || 'Failed to reset password.', 'warning')
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

const showDeleteModal = ref(false)
const eventToDelete = ref(null)
const deleteConfirmName = ref('')
const deleteError = ref('')
const deleting = ref(false)

// ── Drag-and-drop state for organiser event assignment ──
const dragEvent = ref(null)
const dragSource = ref(null)  // 'pool' or organiser id
const dragOverOrgId = ref(null)
const dragOverPool = ref(false)
let ghostEl = null

const unassignedEvents = computed(() =>
  events.value.filter(e => !organisers.value.some(o => o.assignedEventIds?.includes(e.id)))
)

const assignedEvents = (org) =>
  (org.assignedEventIds ?? []).map(id => events.value.find(e => e.id === id)).filter(Boolean)

// ── Pointer DnD handlers ──
function onDragStart(event, e) {
  e.preventDefault()
  const el = e.currentTarget
  dragEvent.value = event
  dragSource.value = el.dataset.dragSource === 'pool' ? 'pool' : Number(el.dataset.dragSource)

  // Create ghost
  ghostEl = el.cloneNode(true)
  ghostEl.style.position = 'fixed'
  ghostEl.style.zIndex = '9999'
  ghostEl.style.pointerEvents = 'none'
  ghostEl.style.opacity = '0.85'
  ghostEl.style.width = el.offsetWidth + 'px'
  ghostEl.style.left = (e.clientX - el.offsetWidth / 2) + 'px'
  ghostEl.style.top = (e.clientY - el.offsetHeight / 2) + 'px'
  document.body.appendChild(ghostEl)

  // Make source invisible
  el.style.opacity = '0'

  document.addEventListener('pointermove', onDragMove)
  document.addEventListener('pointerup', onDragUp)
  document.addEventListener('pointercancel', onDragUp)
}

function onDragMove(e) {
  if (!ghostEl) return
  ghostEl.style.left = (e.clientX - ghostEl.offsetWidth / 2) + 'px'
  ghostEl.style.top = (e.clientY - ghostEl.offsetHeight / 2) + 'px'

  // Probe drop target
  ghostEl.style.display = 'none'
  const target = document.elementFromPoint(e.clientX, e.clientY)
  ghostEl.style.display = ''

  const orgEl = target?.closest('[data-organiser-id]')
  const poolEl = target?.closest('[data-pool-zone]')

  dragOverOrgId.value = orgEl ? Number(orgEl.dataset.organiserId) : null
  dragOverPool.value = !!poolEl && dragSource.value !== 'pool'
}

async function onDragUp(e) {
  document.removeEventListener('pointermove', onDragMove)
  document.removeEventListener('pointerup', onDragUp)
  document.removeEventListener('pointercancel', onDragUp)

  // Restore source visibility
  if (dragEvent.value) {
    const sourceEl = document.querySelector(`[data-event-id="${dragEvent.value.id}"][data-drag-source]`)
    if (sourceEl) sourceEl.style.opacity = ''
  }

  // Remove ghost
  if (ghostEl) { ghostEl.remove(); ghostEl = null }

  // Probe final drop target
  const target = document.elementFromPoint(e.clientX, e.clientY)
  const orgEl = target?.closest('[data-organiser-id]')
  const poolEl = target?.closest('[data-pool-zone]')

  const targetOrgId = orgEl ? Number(orgEl.dataset.organiserId) : null
  const targetPool = !!poolEl

  const ev = dragEvent.value
  const src = dragSource.value
  dragEvent.value = null
  dragSource.value = null
  dragOverOrgId.value = null
  dragOverPool.value = false

  if (!ev) return

  // Determine action
  if (targetOrgId !== null && targetOrgId !== src) {
    // Drop on an organiser (different from source)
    await assignOrganiserToEvent(targetOrgId, ev.id)
    organisers.value = await getOrganisers() ?? []
  } else if (targetPool && src !== 'pool') {
    // Drop back to pool from an organiser
    await removeOrganiserFromEvent(src, ev.id)
    organisers.value = await getOrganisers() ?? []
  }
  // else: no-op (dropped on same organiser, or pool→pool)
}

const accentInput = ref('#ffffff')
const activeTab = ref('scores')
const tabs = ref(['scores', 'feedback', 'images', 'theme', 'organisers', 'demo', 'sheets'])

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

// ── Sheet Config ───────────────────────────────────────────
const sheetConfig = ref({
  nameKeyword: '',
  stageNameKeyword: '',
  teamNameKeywords: '',
  memberNameKeywords: '',
  categoryKeywords: '',
  entryTypeKeyword: '',
  emailKeyword: '',
  paymentKeyword: '',
  screenshotKeywords: ''
})

const sheetSaved = ref(false)

async function saveSheet() {
  try {
    await saveSheetConfig(sheetConfig.value)
    sheetSaved.value = true
    setTimeout(() => { sheetSaved.value = false }, 2000)
  } catch (e) {
    console.error('Failed to save sheet config', e)
  }
}

const coreFields = [
  { key: 'nameKeyword',        label: 'Name keyword',           help: 'Column is a candidate for participant name.',            default: 'name' },
  { key: 'stageNameKeyword',   label: 'Stage name keyword',     help: 'Column is a stage/display name.',                        default: 'stage name' },
  { key: 'teamNameKeywords',   label: 'Team name keywords',     help: 'Comma-separated — column signals a team/crew entry.',    default: 'team,duo,battler,crew,group' },
  { key: 'memberNameKeywords', label: 'Member name keywords',   help: 'Comma-separated — column signals a team member/dancer.', default: 'member,dancer' },
  { key: 'categoryKeywords',   label: 'Category keywords',      help: 'Comma-separated — column signals a battle category.',   default: 'categor' },
]

const otherFields = [
  { key: 'entryTypeKeyword',   label: 'Entry type keyword',     help: 'Column is explicit entry type (solo/team).',            default: 'entry type' },
  { key: 'emailKeyword',       label: 'Email keyword',          help: 'Column is the email address.',                          default: 'email' },
  { key: 'paymentKeyword',     label: 'Payment keyword',        help: 'Column is payment status.',                             default: 'payment status' },
  { key: 'screenshotKeywords', label: 'Screenshot keywords',    help: 'Comma-separated — column is a payment screenshot.',     default: 'screenshot,receipt,proof,prove,payment' },
]

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

async function resetTemplateData() {
  if (!confirm('Reset demo template event? This deletes and re-seeds the template with fresh data. Existing sandboxes are unaffected.')) return
  await fetch('/api/v1/admin/demo/reset-template', { method: 'POST', credentials: 'include' })
  await loadDemoConfig()
}

function openDeleteModal(event) {
  eventToDelete.value = event
  deleteConfirmName.value = ''
  deleteError.value = ''
  showDeleteModal.value = true
}

function closeDeleteModal() {
  showDeleteModal.value = false
  eventToDelete.value = null
  deleteConfirmName.value = ''
  deleteError.value = ''
}

async function confirmDelete() {
  if (!eventToDelete.value || deleteConfirmName.value !== eventToDelete.value.name) return
  deleting.value = true
  deleteError.value = ''
  try {
    await deleteEvent(eventToDelete.value.name)
    events.value = events.value.filter(e => e.id !== eventToDelete.value.id)
    closeDeleteModal()
  } catch (err) {
    deleteError.value = err.message || 'Failed to delete event'
  } finally {
    deleting.value = false
  }
}

onMounted(async () => {
  try {
    events.value = await fetchAllEvents() ?? []
    images.value = await getAllImages() ?? []
    feedbackGroups.value = await getFeedbackGroups() ?? []
    feedbackOverrides.value = await getFeedbackTagOverrides() ?? []
    organisers.value = await getOrganisers() ?? []
    const cfg = await getAppConfig()
    accentInput.value = cfg?.accentColor ?? '#ffffff'
    sheetConfig.value = cfg?.sheetConfig ?? sheetConfig.value
    loadDemoConfig()
    loadSandboxes()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page-container relative">
    <div class="color-bleed"></div>

    <LoadingOverlay v-if="loading">Loading admin panel…</LoadingOverlay>

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

        <!-- ── Delete Events ──────────────────────────────────── -->
        <div class="section-rule mb-4 mt-10">
          <span class="section-rule-label">Delete Events</span>
          <div class="section-rule-line"></div>
        </div>
        <p class="type-prose mb-4">Permanently deletes an event and ALL associated data — participants, categories, scores, feedback, battle state, session tokens. This cannot be undone.</p>

        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
          <div
            v-for="e in events"
            :key="e.id"
            class="card-hover p-3 relative flex items-center justify-between"
          >
            <div class="corner-bar-tl"></div>
            <span class="type-name text-content-secondary truncate flex-1">{{ e.name }}</span>
            <button
              @click="openDeleteModal(e)"
              class="ml-2 flex-shrink-0 para-chip-sm type-label px-2 py-1 text-red-400 hover:bg-red-950 transition-all"
            >
              Delete
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

        <!-- ── Event Pool ────────────────────────────────────── -->
        <div class="section-rule mb-4 mt-4">
          <span class="section-rule-label">Event Pool</span>
          <span class="badge-neutral type-label px-2 py-0.5">{{ unassignedEvents.length }}</span>
          <div class="section-rule-line"></div>
        </div>

        <div
          data-pool-zone
          class="card-hover p-4 relative mb-6 min-h-[56px] transition-all duration-150"
          :class="dragOverPool ? 'border-[color:var(--accent-muted)] bg-[rgba(255,255,255,0.03)]' : ''"
        >
          <div class="corner-bar-tl"></div>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="e in unassignedEvents"
              :key="e.id"
              :data-event-id="e.id"
              data-drag-source="pool"
              @pointerdown="onDragStart(e, $event)"
              class="para-chip-sm type-name-sm px-3 py-1.5 inline-flex items-center gap-1.5 transition-all duration-150 text-content-secondary hover:text-accent hover:border-[color:var(--accent-muted)] cursor-grab active:cursor-grabbing select-none"
              style="touch-action: none"
              :aria-label="`Drag ${e.name} to an organiser`"
            >
              {{ e.name }}
            </button>
            <p v-if="unassignedEvents.length === 0" class="type-label text-content-muted py-2 w-full text-center">
              All events are assigned
            </p>
          </div>
        </div>

        <!-- ── Organiser Drop Zones ──────────────────────────── -->
        <div class="section-rule mb-4">
          <span class="section-rule-label">Organisers</span>
          <span class="badge-neutral type-label px-2 py-0.5">{{ filteredOrganisers.length }}</span>
          <div class="section-rule-line"></div>
        </div>

        <div class="space-y-3">
          <div
            v-for="org in filteredOrganisers"
            :key="org.id"
            :data-organiser-id="org.id"
            class="card-hover p-4 relative transition-all duration-150"
            :class="dragOverOrgId === org.id ? 'border-[color:var(--accent-muted)] bg-[rgba(255,255,255,0.03)]' : ''"
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
                  @click="openRename(org)"
                  class="w-8 h-8 flex items-center justify-center text-content-muted hover:text-accent hover:bg-[rgba(255,255,255,0.04)] transition-all"
                  title="Rename username"
                  :aria-label="`Rename ${org.username}`"
                >
                  <i class="pi pi-pencil text-xs" aria-hidden="true"></i>
                </button>
                <button
                  @click="openResetPw(org)"
                  class="w-8 h-8 flex items-center justify-center text-content-muted hover:text-accent hover:bg-[rgba(255,255,255,0.04)] transition-all"
                  title="Reset password"
                  :aria-label="`Reset password for ${org.username}`"
                >
                  <i class="pi pi-key text-xs" aria-hidden="true"></i>
                </button>
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

            <!-- Inline edit row (rename or reset password) -->
            <div v-if="orgEdit[org.id]" class="mb-3 flex items-center gap-2">
              <input
                :type="orgEdit[org.id].mode === 'password' ? 'password' : 'text'"
                v-model="orgEdit[org.id].value"
                :placeholder="orgEdit[org.id].mode === 'password' ? 'New password (min 6 chars)' : 'New username'"
                class="flex-1 type-name-sm px-3 py-2 para-chip-sm bg-transparent text-content-primary placeholder:text-content-muted focus:outline-none focus:border-[color:var(--accent-muted)]"
                @keyup.enter="orgEdit[org.id].mode === 'password' ? submitResetPw(org) : submitRename(org)"
                @keyup.escape="cancelEdit(org.id)"
              />
              <button
                @click="orgEdit[org.id].mode === 'password' ? submitResetPw(org) : submitRename(org)"
                class="type-label px-3 py-2 para-chip-sm text-accent border-[color:var(--accent-muted)] hover:bg-[rgba(255,255,255,0.04)] transition-all"
              >
                SAVE
              </button>
              <button
                @click="cancelEdit(org.id)"
                class="type-label px-3 py-2 para-chip-sm text-content-muted hover:text-content-primary transition-all"
              >
                CANCEL
              </button>
            </div>

            <!-- Drop zone -->
            <div class="flex flex-wrap gap-2 min-h-[36px] items-start">
              <button
                v-for="e in assignedEvents(org)"
                :key="e.id"
                :data-event-id="e.id"
                :data-drag-source="org.id"
                @pointerdown="onDragStart(e, $event)"
                class="para-chip-sm type-name-sm px-3 py-1.5 inline-flex items-center gap-1.5 transition-all duration-150 text-accent border-[color:var(--accent-muted)] cursor-grab active:cursor-grabbing select-none"
                style="touch-action: none"
                :aria-label="`Drag ${e.name} to pool or another organiser`"
              >
                {{ e.name }}
              </button>
              <p v-if="assignedEvents(org).length === 0" class="type-label text-content-muted py-2">
                Drop events here
              </p>
            </div>
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
              <button @click="resetTemplateData" class="para-chip-sm type-label px-3 py-1.5 min-h-[44px] text-amber-400 border-amber-400/30">
                Reset Template
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

      <!-- ── Sheet Import Config ───────────────────────────────── -->
      <div v-if="activeTab === 'sheets'">
        <div class="section-rule mb-6">
          <span class="section-rule-label">Sheet Column Keywords</span>
          <div class="section-rule-line"></div>
        </div>
        <p class="type-prose mb-6">Configure which words in Google Sheets column headers map to each field. Changes apply to all future imports; no sheet reformatting needed.</p>

        <!-- Core fields -->
        <div class="section-rule mb-4">
          <span class="section-rule-label">Core Fields</span>
          <span class="badge-neutral type-label px-2 py-0.5">often customised</span>
          <div class="section-rule-line"></div>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
          <div class="card-hover p-4 relative" v-for="f in coreFields" :key="f.key">
            <div class="corner-bar-tl"></div>
            <label class="type-label block mb-1">{{ f.label }}</label>
            <p class="type-prose-sm text-content-secondary mb-2">{{ f.help }}</p>
            <input
              v-model="sheetConfig[f.key]"
              type="text"
              :placeholder="f.default"
              class="w-full bg-surface-900 border border-white/10 px-3 py-2 type-body text-content-secondary focus:border-accent outline-none"
            />
          </div>
        </div>

        <!-- Other fields -->
        <div class="section-rule mb-4">
          <span class="section-rule-label">Other Fields</span>
          <span class="badge-neutral type-label px-2 py-0.5">rarely changed</span>
          <div class="section-rule-line"></div>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-6">
          <div class="card-hover p-4 relative" v-for="f in otherFields" :key="f.key">
            <div class="corner-bar-tl"></div>
            <label class="type-label block mb-1">{{ f.label }}</label>
            <p class="type-prose-sm text-content-secondary mb-2">{{ f.help }}</p>
            <input
              v-model="sheetConfig[f.key]"
              type="text"
              :placeholder="f.default"
              class="w-full bg-surface-900 border border-white/10 px-3 py-2 type-body text-content-secondary focus:border-accent outline-none"
            />
          </div>
        </div>

        <button @click="saveSheet" class="para-chip type-label border-accent flex items-center gap-2 px-4 py-2 min-h-[44px]">
          <i class="pi pi-check text-sm"></i>
          {{ sheetSaved ? 'Saved' : 'Save Sheet Config' }}
        </button>
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

    <!-- Delete Event Confirmation Modal -->
    <Teleport to="body">
      <div
        v-if="showDeleteModal"
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        style="background: rgba(0,0,0,0.8)"
        @click.self="closeDeleteModal"
      >
        <div
          class="w-full max-w-md p-6 flex flex-col gap-5"
          style="background: #1a1a1a; border: 1px solid rgba(239,68,68,0.3); clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%)"
        >
          <div class="flex items-center gap-3">
            <div class="w-2.5 h-2.5 rounded-full bg-red-500 flex-shrink-0" style="box-shadow: 0 0 10px rgba(239,68,68,0.7)"></div>
            <h2 class="type-page-title text-red-400" style="font-size: 20px">DELETE EVENT</h2>
          </div>

          <div class="px-4 py-3" style="border-left: 3px solid rgba(239,68,68,0.6); background: rgba(239,68,68,0.07)">
            <p class="type-label text-red-300/90 mb-1">This will permanently delete:</p>
            <p class="type-prose text-red-200/70">
              <strong class="type-name text-red-300">{{ eventToDelete?.name }}</strong>
              and ALL associated data — participants, categories, scores, feedback, battle state, and session tokens.
              This cannot be undone.
            </p>
          </div>

          <div>
            <p class="type-prose text-content-muted mb-3">
              <template v-if="deleteConfirmName === ''">Type the event name to enable deletion.</template>
              <template v-else-if="deleteConfirmName === eventToDelete?.name">✓ Name confirmed. Ready to delete.</template>
              <template v-else>Keep typing — name must match exactly.</template>
            </p>
            <input
              v-model="deleteConfirmName"
              type="text"
              :placeholder="eventToDelete?.name"
              class="input-base w-full"
              autofocus
            />
          </div>

          <div
            v-if="deleteError"
            class="px-3 py-2 type-label text-red-300"
            style="border-left: 3px solid rgba(239,68,68,0.5); background: rgba(239,68,68,0.08)"
          >
            {{ deleteError }}
          </div>

          <div class="flex gap-3">
            <button
              @click="closeDeleteModal"
              class="flex-1 py-2.5 type-label border border-surface-600 text-content-muted hover:text-content-primary transition-colors"
              style="clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%)"
              :disabled="deleting"
            >CANCEL</button>
            <button
              @click="confirmDelete"
              :disabled="deleteConfirmName !== eventToDelete?.name || deleting"
              class="flex-1 py-2.5 type-label font-bold transition-all"
              :class="deleteConfirmName === eventToDelete?.name && !deleting
                ? 'bg-red-600 text-white hover:bg-red-500'
                : 'bg-surface-700 text-content-muted cursor-not-allowed'"
              style="clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%)"
            >
              <span v-if="deleting">DELETING…</span>
              <span v-else>DELETE EVENT</span>
            </button>
          </div>

          <p class="type-prose-sm text-content-muted/50 text-center">Tap outside to cancel</p>
        </div>
      </div>
    </Teleport>
  </div>
</template>
