<script setup>
import { addGenre, deleteGenre, deleteImage, deleteScore, getAllImages, updateGenre, getFeedbackGroups, addFeedbackGroup, deleteFeedbackGroup, addFeedbackTag, deleteFeedbackTag, getOrganisers, assignOrganiserToEvent, removeOrganiserFromEvent } from '@/utils/adminApi';
import { checkInputNull } from '@/utils/utils';
import { onMounted, ref } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import { fetchAllEvents, fetchAllGenres, getAppConfig, postAppConfig } from '@/utils/api';
import UpdateFieldForm from '@/components/UpdateFieldForm.vue';

const addGenreInput = ref('');

const modalTitle = ref("")
const modalMessage = ref("")
const modalVariant = ref("warning")
const showModal = ref(false)

const updateModalTitle = ref("Update")
const updateModalMessage = ref("")
const showUpdateModal = ref(false)
const updateType = ref("text")
const updateField = ref("")
const selectedId = ref(0)

const openModal = (title, message, variant = 'info') => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = variant
  showModal.value = true
  dynamicHandler.value = () => { showModal.value = false }
}

const selectId = (id, field) => {
  selectedId.value = id
  showUpdateModal.value = true
  updateField.value = field
}

const submitUpdate = (value) => {
  if (updateField.value === "genre") {
    submitUpdateGenre(selectedId.value, value)
    genres.value = genres.value.map(x =>
      x.id === selectedId.value ? { ...x, genreName: value } : x
    )
  }
  showUpdateModal.value = false
}

const genres = ref([])
const events = ref([])
const images = ref([])
const feedbackGroups = ref([])
const addGroupInput = ref('')
const addTagInputs = ref({})  // { [groupId]: string }
const dynamicHandler = ref(() => {})

const organisers = ref([])
const newOrganiserUsername = ref('')
const newOrganiserPassword = ref('')

const submitCreateOrganiser = async () => {
  if (checkInputNull(newOrganiserUsername.value) || checkInputNull(newOrganiserPassword.value)) {
    openModal("Validation Error", "Username and password cannot be empty.", "error")
    return
  }
  const res = await createOrganiser(newOrganiserUsername.value, newOrganiserPassword.value)
  if (res?.ok) {
    organisers.value = await getOrganisers() ?? []
    newOrganiserUsername.value = ''
    newOrganiserPassword.value = ''
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
const activeTab = ref('genres')
const tabs = ref(['genres', 'scores', 'feedback', 'images', 'theme', 'organisers'])

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

const confirmRemoveGenre = (id, title, message) => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = 'warning'
  showModal.value = true
  dynamicHandler.value = async () => {
    await submitDeleteGenre(id)
    showModal.value = false
  }
}

const submitAddGenre = async () => {
  if (checkInputNull(addGenreInput.value)) {
    openModal("Validation Error", "Genre name cannot be empty.", "error")
  } else {
    const res = await addGenre(addGenreInput.value)
    genres.value = await res.json()
    addGenreInput.value = ''
  }
}

const submitUpdateGenre = async (id, value) => { await updateGenre(id, value) }

const submitDeleteGenre = async (id) => {
  const res = await deleteGenre(id)
  if (res.ok) genres.value = genres.value.filter(g => g.id !== id)
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

onMounted(async () => {
  genres.value = await fetchAllGenres() ?? []
  events.value = await fetchAllEvents() ?? []
  images.value = await getAllImages() ?? []
  feedbackGroups.value = await getFeedbackGroups() ?? []
  organisers.value = await getOrganisers() ?? []
  const cfg = await getAppConfig()
  accentInput.value = cfg?.accentColor ?? '#ffffff'
})
</script>

<template>
  <div class="page-container relative">
    <div class="color-bleed"></div>
    <div class="relative z-10 space-y-8">

      <!-- Page header -->
      <div>
        <div class="type-page-title">Admin</div>
      </div>

      <!-- Section tabs -->
      <div class="flex flex-wrap gap-2">
        <button
          v-for="tab in tabs"
          :key="tab"
          @click="activeTab = tab"
          class="para-chip-sm type-label px-3 py-1.5 transition-all duration-150"
          :class="activeTab === tab ? 'text-accent border-[color:var(--accent-muted)]' : 'text-content-muted hover:text-content-primary'"
        >{{ tab }}</button>
      </div>

      <!-- ── Genres ──────────────────────────────────────────── -->
      <div v-if="activeTab === 'genres'">
        <div class="section-rule mb-4">
          <span class="section-rule-label">Genres</span>
          <span class="badge-neutral type-label px-2 py-0.5">{{ genres.length }}</span>
          <div class="section-rule-line"></div>
        </div>

        <p class="type-label text-content-muted mb-4">Genres — used to group divisions when setting up events.</p>

        <div class="flex gap-3 mb-5">
          <input
            v-model="addGenreInput"
            type="text"
            placeholder="Genre name…"
            class="input-base flex-1 max-w-xs"
            @keyup.enter="submitAddGenre"
          />
          <button @click="submitAddGenre" class="bg-accent para-chip-sm type-label text-surface-900 px-4 py-2">Add Genre</button>
        </div>

        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
          <div
            v-for="g in genres"
            :key="g.id"
            class="card-hover p-3 relative flex flex-col gap-1.5"
          >
            <div class="corner-bar-tl"></div>
            <!-- Name row -->
            <div class="flex items-center justify-between">
              <button
                @click="selectId(g.id, 'genre')"
                class="type-body text-content-secondary hover:text-accent text-left truncate flex-1 transition-colors"
              >
                {{ g.genreName }}
              </button>
              <div class="flex gap-0.5 ml-1 flex-shrink-0">
                <button
                  @click="confirmRemoveGenre(g.id, 'Remove Genre?', `Are you sure you want to remove ${g.genreName}?`)"
                  class="w-6 h-6 flex items-center justify-center text-content-muted hover:text-red-400 hover:bg-red-950 transition-all"
                >
                  <i class="pi pi-times text-xs"></i>
                </button>
              </div>
            </div>
          </div>
          <div v-if="genres.length === 0" class="col-span-full type-label text-content-muted py-4">
            No genres added yet
          </div>
        </div>
      </div>

      <!-- ── Reset Scores ───────────────────────────────────── -->
      <div v-if="activeTab === 'scores'">
        <div class="section-rule mb-4">
          <span class="section-rule-label">Reset Scores</span>
          <div class="section-rule-line"></div>
        </div>
        <p class="type-label text-content-muted mb-4">Permanently removes all scores for an event.</p>

        <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
          <div
            v-for="e in events"
            :key="e.id"
            class="card-hover p-3 relative flex items-center justify-between"
          >
            <div class="corner-bar-tl"></div>
            <span class="type-body text-content-secondary truncate flex-1">{{ e.name }}</span>
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
            class="input-base flex-1 max-w-xs"
            @keyup.enter="submitAddGroup"
          />
          <button @click="submitAddGroup" class="bg-accent para-chip-sm type-label text-surface-900 px-4 py-2">Add Group</button>
        </div>

        <div class="space-y-5">
          <div
            v-for="group in feedbackGroups"
            :key="group.id"
            class="card-hover p-4 relative"
          >
            <div class="corner-bar-tl"></div>
            <div class="flex items-center justify-between mb-3">
              <span class="type-body text-content-primary">{{ group.name }}</span>
              <button
                @click="submitDeleteGroup(group.id)"
                class="w-6 h-6 flex items-center justify-center text-content-muted hover:text-red-400 hover:bg-red-950 transition-all"
                title="Delete group"
              >
                <i class="pi pi-times text-xs"></i>
              </button>
            </div>

            <div class="flex flex-wrap gap-2 mb-3">
              <div
                v-for="tag in group.tags"
                :key="tag.id"
                class="para-chip-sm px-3 py-1 type-label text-content-secondary flex items-center gap-1.5"
              >
                {{ tag.label }}
                <button
                  @click="submitDeleteTag(tag.id)"
                  class="text-content-muted hover:text-red-400 transition-colors leading-none"
                >
                  <i class="pi pi-times" style="font-size: 0.6rem"></i>
                </button>
              </div>
              <p v-if="!group.tags?.length" class="type-label text-content-muted py-1">No tags yet</p>
            </div>

            <div class="flex gap-2">
              <input
                v-model="addTagInputs[group.id]"
                type="text"
                :placeholder="`Add tag to ${group.name}…`"
                class="input-base flex-1 text-sm py-1.5"
                @keyup.enter="submitAddTag(group.id)"
              />
              <button
                @click="submitAddTag(group.id)"
                class="para-chip-sm type-label px-3 py-1.5 text-content-secondary hover:text-content-primary transition-colors"
              >
                <i class="pi pi-plus text-xs"></i>
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
              class="ml-2 flex-shrink-0 w-6 h-6 flex items-center justify-center text-content-muted hover:text-red-400 hover:bg-red-950 transition-all"
            >
              <i class="pi pi-times text-xs"></i>
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

        <div class="flex gap-3 mb-5">
          <input
            v-model="newOrganiserUsername"
            type="text"
            placeholder="Username…"
            class="input-base flex-1 max-w-xs"
            @keyup.enter="submitCreateOrganiser"
          />
          <input
            v-model="newOrganiserPassword"
            type="password"
            placeholder="Password…"
            class="input-base flex-1 max-w-xs"
            @keyup.enter="submitCreateOrganiser"
          />
          <button @click="submitCreateOrganiser" class="bg-accent para-chip-sm type-label text-surface-900 px-4 py-2">Create Account</button>
        </div>

        <p class="type-label text-content-muted mb-4">Assign or remove events for each organiser.</p>

        <div class="space-y-3">
          <div
            v-for="org in organisers"
            :key="org.id"
            class="card-hover p-4 relative"
          >
            <div class="corner-bar-tl"></div>
            <div class="flex items-center justify-between mb-3">
              <span class="type-body text-content-primary">{{ org.username }}</span>
              <button
                @click="confirmDeleteOrganiser(org.id, org.username)"
                class="w-6 h-6 flex items-center justify-center text-content-muted hover:text-red-400 hover:bg-red-950 transition-all"
                title="Delete organiser"
              >
                <i class="pi pi-times text-xs"></i>
              </button>
            </div>

            <div class="flex flex-wrap gap-2">
              <button
                v-for="e in events"
                :key="e.id"
                @click="toggleOrganiserEvent(org.id, e.id, isEventAssigned(org, e.id))"
                class="para-chip-sm type-label px-3 py-1 transition-all duration-150"
                :class="isEventAssigned(org, e.id) ? 'bg-accent text-surface-900' : 'text-content-muted hover:text-content-primary hover:border-[color:var(--accent-muted)]'"
              >{{ e.name }}</button>
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
          <p class="type-label text-content-muted mb-4">Sets the global accent color for all connected clients in real-time.</p>
          <div class="flex items-center gap-4">
            <input type="color" v-model="accentInput" class="w-12 h-10 cursor-pointer bg-transparent border-0" />
            <span class="type-body text-accent">{{ accentInput }}</span>
            <button @click="saveAccent" class="bg-accent para-chip type-label text-surface-900 px-4 py-2">Apply</button>
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

    <UpdateFieldForm
      :show="showUpdateModal"
      :title="updateModalTitle"
      :type="updateType"
      @close="showUpdateModal = false"
      @submitUpdate="submitUpdate"
    >
      {{ updateModalMessage }}
    </UpdateFieldForm>
  </div>
</template>
