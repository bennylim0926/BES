<script setup>
import ReusableButton from '@/components/ReusableButton.vue';
import { addGenre, addJudge, deleteGenre, deleteImage, deleteJudge, deleteScore, getAllImages, updateGenre, updateJudge, getFeedbackGroups, addFeedbackGroup, deleteFeedbackGroup, addFeedbackTag, deleteFeedbackTag } from '@/utils/adminApi';
import { checkInputNull } from '@/utils/utils';
import { onMounted, ref } from 'vue';
import ActionDoneModal from './ActionDoneModal.vue';
import { fetchAllEvents, fetchAllGenres, getAllJudges } from '@/utils/api';
import UpdateFieldForm from '@/components/UpdateFieldForm.vue';

const addJudgeInput = ref('');
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
  if (updateField.value === "judge") {
    submitUpdateJudge(selectedId.value, value)
    judges.value = judges.value.map(x =>
      x.judgeId === selectedId.value ? { ...x, judgeName: value } : x
    )
  } else if (updateField.value === "genre") {
    submitUpdateGenre(selectedId.value, value)
    genres.value = genres.value.map(x =>
      x.id === selectedId.value ? { ...x, genreName: value } : x
    )
  }
  showUpdateModal.value = false
}

const judges = ref([])
const genres = ref([])
const events = ref([])
const images = ref([])
const feedbackGroups = ref([])
const addGroupInput = ref('')
const addTagInputs = ref({})  // { [groupId]: string }
const dynamicHandler = ref(() => {})

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

const confirmRemoveJudge = (id, title, message) => {
  modalTitle.value = title
  modalMessage.value = message
  modalVariant.value = 'warning'
  showModal.value = true
  dynamicHandler.value = async () => {
    await submitDeleteJudge(id)
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

const submitAddJudge = async () => {
  if (checkInputNull(addJudgeInput.value)) {
    openModal("Validation Error", "Judge name cannot be empty.", "error")
  } else {
    const res = await addJudge(addJudgeInput.value)
    judges.value = await res.json()
    addJudgeInput.value = ''
  }
}

const submitUpdateGenre = async (id, value) => { await updateGenre(id, value) }
const submitUpdateJudge = async (id, value) => { await updateJudge(id, value) }

const submitDeleteJudge = async (id) => {
  const res = await deleteJudge(id)
  if (res.ok) judges.value = judges.value.filter(j => j.judgeId !== id)
}

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

onMounted(async () => {
  genres.value = await fetchAllGenres() ?? []
  judges.value = await getAllJudges() ?? []
  events.value = await fetchAllEvents() ?? []
  images.value = await getAllImages() ?? []
  feedbackGroups.value = await getFeedbackGroups() ?? []
})
</script>

<template>
  <div class="page-container space-y-8">

    <!-- Page header -->
    <div>
      <h1 class="page-title">Admin</h1>
      <p class="text-muted mt-1">Manage judges, genres, events, and system settings</p>
    </div>

    <!-- Judges section -->
    <section class="card p-6">
      <div class="flex items-center gap-3 mb-6">
        <div class="w-8 h-8 rounded-xl bg-primary-100 flex items-center justify-center">
          <i class="pi pi-user text-primary-400 text-sm"></i>
        </div>
        <h2 class="font-heading font-bold text-content-secondary text-lg">Judges</h2>
        <span class="badge-neutral text-xs">{{ judges.length }}</span>
      </div>

      <!-- Add judge -->
      <div class="flex gap-3 mb-5">
        <input
          v-model="addJudgeInput"
          type="text"
          placeholder="Judge name…"
          class="input-base flex-1 max-w-xs"
          @keyup.enter="submitAddJudge"
        />
        <button
          @click="submitAddJudge"
          class="flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-primary-600 text-white text-sm
                 font-semibold hover:bg-primary-700 transition-all duration-200 shadow-sm"
        >
          <i class="pi pi-plus text-xs"></i>
          Add Judge
        </button>
      </div>

      <!-- Judge list -->
      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-2">
        <div
          v-for="j in judges"
          :key="j.judgeId"
          class="flex items-center justify-between px-3 py-2.5 rounded-xl border border-surface-600 bg-surface-800 hover:border-surface-500 transition-all"
        >
          <button
            @click="selectId(j.judgeId, 'judge')"
            class="text-sm font-medium text-content-secondary hover:text-primary-400 text-left truncate flex-1 transition-colors"
          >
            {{ j.judgeName }}
          </button>
          <button
            @click="confirmRemoveJudge(j.judgeId, 'Remove Judge?', `Are you sure you want to remove ${j.judgeName}?`)"
            class="ml-2 flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center
                   text-content-muted hover:text-red-400 hover:bg-red-950 transition-all"
          >
            <i class="pi pi-times text-xs"></i>
          </button>
        </div>
        <div v-if="judges.length === 0" class="col-span-full text-sm text-content-muted py-4">
          No judges added yet
        </div>
      </div>
    </section>

    <!-- Genres section -->
    <section class="card p-6">
      <div class="flex items-center gap-3 mb-6">
        <div class="w-8 h-8 rounded-xl bg-primary-100 flex items-center justify-center">
          <i class="pi pi-tag text-primary-400 text-sm"></i>
        </div>
        <h2 class="font-heading font-bold text-content-secondary text-lg">Genres</h2>
        <span class="badge-neutral text-xs">{{ genres.length }}</span>
      </div>

      <!-- Add genre -->
      <div class="flex gap-3 mb-5">
        <input
          v-model="addGenreInput"
          type="text"
          placeholder="Genre name…"
          class="input-base flex-1 max-w-xs"
          @keyup.enter="submitAddGenre"
        />
        <button
          @click="submitAddGenre"
          class="flex items-center gap-1.5 px-4 py-2.5 rounded-xl bg-primary-600 text-white text-sm
                 font-semibold hover:bg-primary-700 transition-all duration-200 shadow-sm"
        >
          <i class="pi pi-plus text-xs"></i>
          Add Genre
        </button>
      </div>

      <!-- Genre list -->
      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-2">
        <div
          v-for="g in genres"
          :key="g.id"
          class="flex items-center justify-between px-3 py-2.5 rounded-xl border border-surface-600 bg-surface-800 hover:border-surface-500 transition-all"
        >
          <button
            @click="selectId(g.id, 'genre')"
            class="text-sm font-medium text-content-secondary hover:text-primary-400 text-left truncate flex-1 transition-colors"
          >
            {{ g.genreName }}
          </button>
          <button
            @click="confirmRemoveGenre(g.id, 'Remove Genre?', `Are you sure you want to remove ${g.genreName}?`)"
            class="ml-2 flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center
                   text-content-muted hover:text-red-400 hover:bg-red-950 transition-all"
          >
            <i class="pi pi-times text-xs"></i>
          </button>
        </div>
        <div v-if="genres.length === 0" class="col-span-full text-sm text-content-muted py-4">
          No genres added yet
        </div>
      </div>
    </section>

    <!-- Reset Scores section -->
    <section class="card p-6">
      <div class="flex items-center gap-3 mb-6">
        <div class="w-8 h-8 rounded-xl bg-red-950 flex items-center justify-center">
          <i class="pi pi-trash text-red-400 text-sm"></i>
        </div>
        <div>
          <h2 class="font-heading font-bold text-content-secondary text-lg">Reset Scores</h2>
          <p class="text-xs text-content-muted">Permanently removes all scores for an event</p>
        </div>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
        <div
          v-for="e in events"
          :key="e.id"
          class="flex items-center justify-between px-3 py-2.5 rounded-xl border border-surface-600 bg-surface-800"
        >
          <span class="text-sm font-medium text-content-secondary truncate flex-1">{{ e.name }}</span>
          <button
            @click="confirmResetScore(e.id, 'Reset Scores?', `This will permanently delete all scores for ${e.name}.`)"
            class="ml-2 flex-shrink-0 px-2 py-1 rounded-lg text-xs font-semibold text-red-400
                   hover:bg-red-950 transition-all"
          >
            Reset
          </button>
        </div>
      </div>
    </section>

    <!-- Feedback Tags section -->
    <section class="card p-6">
      <div class="flex items-center gap-3 mb-6">
        <div class="w-8 h-8 rounded-xl bg-primary-100 flex items-center justify-center">
          <i class="pi pi-comment text-primary-400 text-sm"></i>
        </div>
        <h2 class="font-heading font-bold text-content-secondary text-lg">Feedback Tags</h2>
        <span class="badge-neutral text-xs">{{ feedbackGroups.length }} groups</span>
      </div>

      <!-- Add group -->
      <div class="flex gap-3 mb-6">
        <input
          v-model="addGroupInput"
          type="text"
          placeholder="New group name…"
          class="input-base flex-1 max-w-xs"
          @keyup.enter="submitAddGroup"
        />
        <button
          @click="submitAddGroup"
          class="flex items-center gap-1.5 px-3 py-2 rounded-xl text-sm font-medium
                 bg-primary-600 text-white hover:bg-primary-700 transition-colors"
        >
          <i class="pi pi-plus text-xs"></i>
          Add Group
        </button>
      </div>

      <!-- Groups list -->
      <div class="space-y-5">
        <div
          v-for="group in feedbackGroups"
          :key="group.id"
          class="border border-surface-600/50 rounded-xl p-4 bg-surface-700/20"
        >
          <!-- Group header -->
          <div class="flex items-center justify-between mb-3">
            <h3 class="text-sm font-semibold text-content-primary">{{ group.name }}</h3>
            <button
              @click="submitDeleteGroup(group.id)"
              class="w-6 h-6 rounded-full flex items-center justify-center
                     text-content-muted hover:text-red-400 hover:bg-red-950 transition-all"
              title="Delete group"
            >
              <i class="pi pi-times text-xs"></i>
            </button>
          </div>

          <!-- Tags -->
          <div class="flex flex-wrap gap-2 mb-3">
            <div
              v-for="tag in group.tags"
              :key="tag.id"
              class="flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-medium
                     bg-surface-600/60 border border-surface-500/50 text-content-secondary"
            >
              {{ tag.label }}
              <button
                @click="submitDeleteTag(tag.id)"
                class="text-surface-400 hover:text-red-400 transition-colors leading-none"
              >
                <i class="pi pi-times" style="font-size: 0.6rem"></i>
              </button>
            </div>
            <p v-if="!group.tags?.length" class="text-xs text-content-muted py-1">No tags yet</p>
          </div>

          <!-- Add tag input -->
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
              class="px-3 py-1.5 rounded-xl text-xs font-medium
                     bg-surface-600 text-content-secondary hover:bg-surface-500 transition-colors"
            >
              <i class="pi pi-plus text-xs"></i>
            </button>
          </div>
        </div>

        <p v-if="feedbackGroups.length === 0" class="text-sm text-content-muted py-2">
          No groups configured. Create groups above, then add tags to each.
        </p>
      </div>
    </section>

    <!-- Images section -->
    <section class="card p-6">
      <div class="flex items-center gap-3 mb-6">
        <div class="icon-wrap w-8 h-8 rounded-xl bg-surface-700 flex items-center justify-center">
          <i class="pi pi-image text-content-muted text-sm"></i>
        </div>
        <h2 class="font-heading font-bold text-content-secondary text-lg">Uploaded Images</h2>
        <span class="badge-neutral text-xs">{{ images.length }}</span>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
        <div
          v-for="img in images"
          :key="img"
          class="flex items-center justify-between px-3 py-2.5 rounded-xl border border-surface-600 bg-surface-800"
        >
          <span class="text-sm text-content-secondary truncate flex-1">{{ img }}</span>
          <button
            @click="confirmRemoveImage(img, `Delete ${img}?`, 'Are you sure you want to delete this image?')"
            class="ml-2 flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center
                   text-content-muted hover:text-red-400 hover:bg-red-950 transition-all"
          >
            <i class="pi pi-times text-xs"></i>
          </button>
        </div>
        <div v-if="images.length === 0" class="col-span-full text-sm text-content-muted py-4">
          No images uploaded
        </div>
      </div>
    </section>

  </div>

  <ActionDoneModal
    :show="showModal"
    :title="modalTitle"
    :variant="modalVariant"
    @accept="() => { dynamicHandler() }"
    @close="() => { showModal = false }"
  >
    <p class="text-content-secondary leading-relaxed">{{ modalMessage }}</p>
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
</template>
