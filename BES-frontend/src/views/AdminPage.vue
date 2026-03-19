<script setup>
import ReusableButton from '@/components/ReusableButton.vue';
import { addGenre, addJudge, deleteGenre, deleteImage, deleteJudge, deleteScore, getAllImages, updateGenre, updateJudge } from '@/utils/adminApi';
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

onMounted(async () => {
  genres.value = await fetchAllGenres() ?? []
  judges.value = await getAllJudges() ?? []
  events.value = await fetchAllEvents() ?? []
  images.value = await getAllImages() ?? []
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
        <div class="w-8 h-8 rounded-xl bg-primary-50 flex items-center justify-center">
          <i class="pi pi-user text-primary-600 text-sm"></i>
        </div>
        <h2 class="font-heading font-bold text-surface-800 text-lg">Judges</h2>
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
          class="flex items-center justify-between px-3 py-2.5 rounded-xl border border-surface-200 bg-white hover:border-surface-300 transition-all"
        >
          <button
            @click="selectId(j.judgeId, 'judge')"
            class="text-sm font-medium text-surface-800 hover:text-primary-600 text-left truncate flex-1 transition-colors"
          >
            {{ j.judgeName }}
          </button>
          <button
            @click="confirmRemoveJudge(j.judgeId, 'Remove Judge?', `Are you sure you want to remove ${j.judgeName}?`)"
            class="ml-2 flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center
                   text-surface-400 hover:text-red-500 hover:bg-red-50 transition-all"
          >
            <i class="pi pi-times text-xs"></i>
          </button>
        </div>
        <div v-if="judges.length === 0" class="col-span-full text-sm text-surface-400 py-4">
          No judges added yet
        </div>
      </div>
    </section>

    <!-- Genres section -->
    <section class="card p-6">
      <div class="flex items-center gap-3 mb-6">
        <div class="w-8 h-8 rounded-xl bg-primary-50 flex items-center justify-center">
          <i class="pi pi-tag text-primary-600 text-sm"></i>
        </div>
        <h2 class="font-heading font-bold text-surface-800 text-lg">Genres</h2>
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
          class="flex items-center justify-between px-3 py-2.5 rounded-xl border border-surface-200 bg-white hover:border-surface-300 transition-all"
        >
          <button
            @click="selectId(g.id, 'genre')"
            class="text-sm font-medium text-surface-800 hover:text-primary-600 text-left truncate flex-1 transition-colors"
          >
            {{ g.genreName }}
          </button>
          <button
            @click="confirmRemoveGenre(g.id, 'Remove Genre?', `Are you sure you want to remove ${g.genreName}?`)"
            class="ml-2 flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center
                   text-surface-400 hover:text-red-500 hover:bg-red-50 transition-all"
          >
            <i class="pi pi-times text-xs"></i>
          </button>
        </div>
        <div v-if="genres.length === 0" class="col-span-full text-sm text-surface-400 py-4">
          No genres added yet
        </div>
      </div>
    </section>

    <!-- Reset Scores section -->
    <section class="card p-6">
      <div class="flex items-center gap-3 mb-6">
        <div class="w-8 h-8 rounded-xl bg-red-50 flex items-center justify-center">
          <i class="pi pi-trash text-red-500 text-sm"></i>
        </div>
        <div>
          <h2 class="font-heading font-bold text-surface-800 text-lg">Reset Scores</h2>
          <p class="text-xs text-surface-400">Permanently removes all scores for an event</p>
        </div>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
        <div
          v-for="e in events"
          :key="e.id"
          class="flex items-center justify-between px-3 py-2.5 rounded-xl border border-surface-200 bg-white"
        >
          <span class="text-sm font-medium text-surface-700 truncate flex-1">{{ e.name }}</span>
          <button
            @click="confirmResetScore(e.id, 'Reset Scores?', `This will permanently delete all scores for ${e.name}.`)"
            class="ml-2 flex-shrink-0 px-2 py-1 rounded-lg text-xs font-semibold text-red-600
                   hover:bg-red-50 transition-all"
          >
            Reset
          </button>
        </div>
      </div>
    </section>

    <!-- Images section -->
    <section class="card p-6">
      <div class="flex items-center gap-3 mb-6">
        <div class="w-8 h-8 rounded-xl bg-surface-100 flex items-center justify-center">
          <i class="pi pi-image text-surface-600 text-sm"></i>
        </div>
        <h2 class="font-heading font-bold text-surface-800 text-lg">Uploaded Images</h2>
        <span class="badge-neutral text-xs">{{ images.length }}</span>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2">
        <div
          v-for="img in images"
          :key="img"
          class="flex items-center justify-between px-3 py-2.5 rounded-xl border border-surface-200 bg-white"
        >
          <span class="text-sm text-surface-700 truncate flex-1">{{ img }}</span>
          <button
            @click="confirmRemoveImage(img, `Delete ${img}?`, 'Are you sure you want to delete this image?')"
            class="ml-2 flex-shrink-0 w-6 h-6 rounded-full flex items-center justify-center
                   text-surface-400 hover:text-red-500 hover:bg-red-50 transition-all"
          >
            <i class="pi pi-times text-xs"></i>
          </button>
        </div>
        <div v-if="images.length === 0" class="col-span-full text-sm text-surface-400 py-4">
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
    <p class="text-surface-600 leading-relaxed">{{ modalMessage }}</p>
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
