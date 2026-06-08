<script setup>
import { onMounted, ref, computed } from 'vue'
import { useAuthStore } from '@/utils/auth'
import CreateParticipantForm from '@/components/CreateParticipantForm.vue'
import ConfirmModal from '@/components/ConfirmModal.vue'
import ActionDoneModal from './ActionDoneModal.vue'
import {
  getRegisteredParticipantsByEvent,
  deleteParticipantFromEvent,
  removeParticipantGenre,
  updateParticipant,
  updateParticipantsJudge
} from '@/utils/api'

const authStore   = useAuthStore()
const selectedEvent = computed(() => authStore.activeEvent?.name || localStorage.getItem('selectedEvent') || '')

const allJudges   = ref([])
const rawRows     = ref([])
const participants = computed(() => groupParticipants(rawRows.value))

const search         = ref('')
const activeGenre    = ref('All')

const genreList = computed(() => {
  const names = [...new Set(rawRows.value.map(r => r.genreName))].sort()
  return ['All', ...names]
})

const genreCount = (genre) =>
  genre === 'All'
    ? participants.value.length
    : participants.value.filter(p => p.genres.some(g => g.genreName === genre)).length

const filtered = computed(() => {
  let list = participants.value
  if (activeGenre.value !== 'All')
    list = list.filter(p => p.genres.some(g => g.genreName === activeGenre.value))
  if (search.value.trim())
    list = list.filter(p => p.name.toLowerCase().includes(search.value.trim().toLowerCase()))
  return list
})

const expanded = ref(new Set())
const toggle   = (id) => expanded.value.has(id) ? expanded.value.delete(id) : expanded.value.add(id)

const editTarget  = ref(null)
const editName    = ref('')
const editMembers = ref([])
const editError   = ref('')
const showEdit    = ref(false)

const openEdit = (p) => {
  const slotCount = parseFormatSize(p.format)
  const isTeam    = slotCount >= 2
  editTarget.value  = { ...p, isTeam, slotCount }
  editName.value    = p.name
  editMembers.value = isTeam
    ? Array.from({ length: slotCount }, (_, i) => (p.memberNames[i] ?? ''))
    : []
  editError.value   = ''
  showEdit.value    = true
}

const validateEdit = () => {
  if (!editName.value.trim()) return 'Name is required'
  if (editTarget.value.isTeam) {
    for (let i = 0; i < editMembers.value.length; i++) {
      if (!editMembers.value[i].trim()) return `Member ${i + 1} is required`
    }
  }
  return ''
}

const submitEdit = async () => {
  const err = validateEdit()
  if (err) { editError.value = err; return }

  const res = await updateParticipant(
    editTarget.value.participantId,
    editTarget.value.eventId,
    { name: editName.value.trim(), memberNames: editTarget.value.isTeam ? editMembers.value.map(m => m.trim()) : [] }
  )
  if (!res) { editError.value = 'Network error'; return }
  if (res.status === 422) {
    const body = await res.json().catch(() => null)
    editError.value = body || 'Duplicate name'
    return
  }
  if (!res.ok) { editError.value = 'Save failed'; return }
  showEdit.value = false
  await reload()
  openToast('Participant updated')
}

const confirmState = ref({ show: false, title: '', message: '', onConfirm: null })

const confirmDeleteParticipant = (p) => {
  const genreList = p.genres.map(g => g.genreName).join(', ')
  confirmState.value = {
    show: true,
    title: 'Delete Participant',
    message: `Remove "${p.name}" from ${selectedEvent.value}? This will remove them from all genres (${genreList}) and release all audition numbers. This cannot be undone.`,
    onConfirm: async () => {
      await deleteParticipantFromEvent(p.participantId, p.eventId)
      confirmState.value.show = false
      await reload()
      openToast('Participant removed')
    }
  }
}

const confirmRemoveGenre = (p, genre) => {
  confirmState.value = {
    show: true,
    title: 'Remove from Genre',
    message: `Remove "${p.name}" from ${genre.genreName}? Their audition number will be released.`,
    onConfirm: async () => {
      await removeParticipantGenre(p.participantId, p.eventId, genre.eventGenreId)
      confirmState.value.show = false
      await reload()
      openToast('Genre removed')
    }
  }
}

const assignJudge = async (p, genre, judgeName) => {
  await updateParticipantsJudge(p.eventId, [
    { eventName: selectedEvent.value, participantName: p.name, genreName: genre.genreName, judgeName }
  ])
  await reload()
}

const toast        = ref('')
const showToast    = ref(false)
let   toastTimer   = null
const openToast = (msg) => {
  toast.value = msg; showToast.value = true
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => { showToast.value = false }, 2500)
}

const showCreate = ref(false)

function isTeamFormat(fmt) {
  if (!fmt) return false
  return /^\d+v\d+$/i.test(fmt) && fmt.toLowerCase() !== '1v1'
}

function parseFormatSize(fmt) {
  if (!fmt) return 0
  const m = fmt.match(/^(\d+)v\d+$/i)
  return m ? parseInt(m[1]) : 0
}

function groupParticipants(rows) {
  const map = new Map()
  for (const row of rows) {
    if (!map.has(row.participantId)) {
      map.set(row.participantId, {
        participantId: row.participantId,
        eventId:       row.eventId,
        name:          row.participantName,
        format:        row.format,
        memberNames:   row.memberNames || [],
        genres: []
      })
    }
    map.get(row.participantId).genres.push({
      genreName:    row.genreName,
      eventGenreId: row.eventGenreId,
      judgeName:    row.judgeName || '',
      auditionNumber: row.auditionNumber
    })
  }
  return Array.from(map.values())
}

const reload = async () => {
  if (!selectedEvent.value) return
  const res = await getRegisteredParticipantsByEvent(selectedEvent.value)
  rawRows.value = Array.isArray(res) ? res : []
}

const fetchJudges = async () => {
  try {
    const res = await fetch('/api/v1/event/judges', { credentials: 'include' })
    if (!res.ok) return
    const data = await res.json()
    allJudges.value = Object.values(data).map(j => j.judgeName)
  } catch { /* silent */ }
}

onMounted(async () => {
  await reload()
  await fetchJudges()
})
</script>

<template>
  <div class="page-container relative">
    <div class="color-bleed"></div>
    <div class="relative z-10">

      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-8">
        <div>
          <div class="type-page-title mb-1">Participants</div>
          <p class="type-label text-content-muted">Manage entries, edit names, assign judges</p>
        </div>
        <button
          @click="showCreate = true"
          class="bg-accent para-chip type-label text-surface-900 px-4 py-2 flex items-center gap-2"
        >
          <i class="pi pi-plus text-xs"></i> Add Participant
        </button>
      </div>

      <div class="para-chip p-4 mb-6">
        <input
          v-model="search"
          class="search-input"
          placeholder="Search name..."
        />
        <div class="genre-chips">
          <button
            v-for="genre in genreList"
            :key="genre"
            :class="['genre-chip', activeGenre === genre && 'active']"
            @click="activeGenre = genre"
          >
            {{ genre }}
            <span class="count-badge">{{ genreCount(genre) }}</span>
          </button>
        </div>
        <div class="flex items-center gap-2 mt-3 pt-3 border-t border-surface-600/30">
          <i class="pi pi-users text-content-muted text-sm"></i>
          <span class="type-label text-content-muted">
            Showing <span class="text-accent">{{ filtered.length }}</span>
            of <span class="text-accent">{{ participants.length }}</span> participants
          </span>
        </div>
      </div>

      <div class="section-rule mb-4">
        <span class="section-rule-label">Participants</span>
        <div class="section-rule-line"></div>
      </div>

      <div v-if="filtered.length === 0" class="empty-state">
        <i class="pi pi-users text-2xl text-content-muted mb-2"></i>
        <p class="type-body text-content-secondary">No participants found</p>
      </div>

      <div v-else class="participant-table">
        <div class="pt-header">
          <div class="pt-col-expand"></div>
          <div class="pt-col-name">Name</div>
          <div class="pt-col-format">Format</div>
          <div class="pt-col-genres">Genres</div>
          <div class="pt-col-actions"></div>
        </div>

        <template v-for="p in filtered" :key="p.participantId">
          <div class="pt-row">
            <button class="expand-btn" @click="toggle(p.participantId)">
              {{ expanded.has(p.participantId) ? '▾' : '▸' }}
            </button>
            <div class="pt-col-name">
              <span class="participant-name">{{ p.name }}</span>
            </div>
            <div class="pt-col-format">
              <span :class="['format-badge', isTeamFormat(p.format) ? 'team' : 'solo']">
                {{ p.format || 'Solo' }}
              </span>
            </div>
            <div class="pt-col-genres">
              <span
                v-for="g in p.genres"
                :key="g.genreName"
                class="genre-pill"
              >{{ g.genreName }}</span>
            </div>
            <div class="pt-col-actions">
              <button class="btn-action" @click="openEdit(p)">
                <i class="pi pi-pencil"></i> Edit
              </button>
              <button class="btn-action danger" @click="confirmDeleteParticipant(p)">
                <i class="pi pi-trash"></i> Delete
              </button>
            </div>
          </div>

          <template v-if="expanded.has(p.participantId)">
            <div
              v-for="genre in p.genres"
              :key="genre.genreName"
              class="pt-subrow"
            >
              <span class="subrow-indent">↳</span>
              <span class="genre-pill">{{ genre.genreName }}</span>
              <span class="subrow-label">Judge:</span>
              <select
                class="judge-select"
                :value="genre.judgeName"
                @change="assignJudge(p, genre, $event.target.value)"
              >
                <option value="">—</option>
                <option v-for="j in allJudges" :key="j" :value="j">{{ j }}</option>
              </select>
              <span v-if="genre.auditionNumber" class="audition-num">#{{ genre.auditionNumber }}</span>
              <button
                class="btn-remove-genre"
                @click="confirmRemoveGenre(p, genre)"
              >Remove Genre</button>
            </div>
          </template>
        </template>
      </div>

    </div>
  </div>

  <Teleport to="body">
    <div v-if="showEdit" class="modal-backdrop" @click.self="showEdit = false">
      <div class="edit-modal">
        <div class="modal-header">
          <span class="type-label">{{ editTarget?.isTeam ? 'Edit Team' : 'Edit Participant' }}</span>
          <button class="modal-close" @click="showEdit = false">✕</button>
        </div>

        <div class="modal-body">
          <div class="field">
            <label class="field-label">
              {{ editTarget?.isTeam ? 'Team Name' : 'Name' }}
              <span class="required">*</span>
            </label>
            <input
              v-model="editName"
              class="field-input"
              :placeholder="editTarget?.isTeam ? 'Team name' : 'Participant name'"
            />
          </div>

          <template v-if="editTarget?.isTeam">
            <div class="members-label">Members</div>
            <div
              v-for="(_, i) in editMembers"
              :key="i"
              class="field"
            >
              <label class="field-label">
                Member {{ i + 1 }} <span class="required">*</span>
              </label>
              <input
                v-model="editMembers[i]"
                class="field-input"
                :placeholder="`Member ${i + 1}`"
              />
            </div>
          </template>

          <p v-if="editError" class="field-error">{{ editError }}</p>
        </div>

        <div class="modal-footer">
          <button class="btn-ghost" @click="showEdit = false">Cancel</button>
          <button class="btn-primary" @click="submitEdit">Save</button>
        </div>
      </div>
    </div>
  </Teleport>

  <ConfirmModal
    :show="confirmState.show"
    :title="confirmState.title"
    :message="confirmState.message"
    confirm-label="Confirm"
    variant="danger"
    @confirm="confirmState.onConfirm && confirmState.onConfirm()"
    @cancel="confirmState.show = false"
  />

  <CreateParticipantForm
    :event="selectedEvent"
    :show="showCreate"
    title="New participant entry"
    @createNewEntry="showCreate = false; reload()"
    @close="showCreate = false"
  />

  <Transition name="toast">
    <div v-if="showToast" class="toast">{{ toast }}</div>
  </Transition>
</template>

<style scoped>
.search-input {
  width: 100%; max-width: 280px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.8);
  padding: 7px 12px; font-size: 12px;
  margin-bottom: 12px;
}
.search-input::placeholder { color: rgba(255,255,255,0.25); }
.genre-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.genre-chip {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 4px 12px; font-size: 10px; letter-spacing: 0.1em; text-transform: uppercase;
  border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.04);
  color: rgba(255,255,255,0.5); cursor: pointer;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  transition: all 0.15s;
}
.genre-chip.active {
  background: rgba(255,255,255,0.12); border-color: rgba(255,255,255,0.35);
  color: rgba(255,255,255,0.9);
}
.count-badge {
  background: rgba(255,255,255,0.1); color: rgba(255,255,255,0.5);
  font-size: 9px; padding: 0 4px; border-radius: 2px;
}

.participant-table { display: flex; flex-direction: column; gap: 2px; }
.pt-header {
  display: grid;
  grid-template-columns: 32px 1fr auto auto auto;
  gap: 8px; align-items: center;
  padding: 6px 12px;
  font-size: 9px; letter-spacing: 0.15em; text-transform: uppercase;
  color: rgba(255,255,255,0.3);
  border-bottom: 1px solid rgba(255,255,255,0.07);
}
.pt-row {
  display: grid;
  grid-template-columns: 32px 1fr auto auto auto;
  gap: 8px; align-items: center;
  padding: 10px 12px;
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  transition: background 0.15s;
}
.pt-row:hover { background: rgba(255,255,255,0.05); }
.pt-subrow {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  padding: 8px 12px 8px 44px;
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.04);
  border-top: none; font-size: 12px;
}
.expand-btn {
  background: none; border: 1px solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.45); font-size: 10px;
  width: 24px; height: 24px; cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.participant-name {
  font-size: 13px; letter-spacing: 0.06em; text-transform: uppercase;
  color: rgba(255,255,255,0.85);
}
.format-badge {
  font-size: 9px; letter-spacing: 0.12em; text-transform: uppercase;
  padding: 2px 8px;
}
.format-badge.solo {
  background: rgba(99,102,241,0.12); border: 1px solid rgba(99,102,241,0.3);
  color: #a5b4fc;
}
.format-badge.team {
  background: rgba(20,184,166,0.12); border: 1px solid rgba(20,184,166,0.3);
  color: #5eead4;
}
.genre-pill {
  display: inline-block; padding: 1px 8px;
  background: rgba(255,255,255,0.06); border: 1px solid rgba(255,255,255,0.1);
  font-size: 9px; text-transform: uppercase; letter-spacing: 0.1em;
  clip-path: polygon(3px 0%, 100% 0%, calc(100% - 3px) 100%, 0% 100%);
}
.pt-col-actions { display: flex; gap: 4px; flex-shrink: 0; }
.btn-action {
  background: none; border: 1px solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.5); font-size: 10px; letter-spacing: 0.08em;
  padding: 4px 10px; cursor: pointer; display: flex; align-items: center; gap: 4px;
  text-transform: uppercase;
}
.btn-action:hover { border-color: rgba(255,255,255,0.25); color: rgba(255,255,255,0.8); }
.btn-action.danger { border-color: rgba(239,68,68,0.2); color: rgba(248,113,113,0.6); }
.btn-action.danger:hover { border-color: rgba(239,68,68,0.5); color: #f87171; }
.subrow-indent { color: rgba(255,255,255,0.25); }
.subrow-label { font-size: 11px; color: rgba(255,255,255,0.3); }
.judge-select {
  background: rgba(255,255,255,0.04); border: 1px solid rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.7); font-size: 11px; padding: 3px 6px;
}
.audition-num { font-size: 10px; color: rgba(255,255,255,0.3); letter-spacing: 0.08em; }
.btn-remove-genre {
  margin-left: auto; background: none;
  border: 1px solid rgba(239,68,68,0.2); color: rgba(248,113,113,0.55);
  font-size: 10px; letter-spacing: 0.08em; text-transform: uppercase;
  padding: 3px 8px; cursor: pointer;
}
.btn-remove-genre:hover { border-color: rgba(239,68,68,0.45); color: #f87171; }

.empty-state {
  display: flex; flex-direction: column; align-items: center;
  justify-content: center; padding: 60px 0; text-align: center;
}

.modal-backdrop {
  position: fixed; inset: 0; z-index: 200;
  background: rgba(0,0,0,0.6);
  display: flex; align-items: center; justify-content: center; padding: 16px;
}
.edit-modal {
  background: var(--color-surface-800, #1a1a1a);
  border: 1px solid rgba(255,255,255,0.1);
  clip-path: polygon(8px 0%, 100% 0%, calc(100% - 8px) 100%, 0% 100%);
  width: 100%; max-width: 480px;
  display: flex; flex-direction: column;
}
.modal-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 16px 20px; border-bottom: 1px solid rgba(255,255,255,0.07);
}
.modal-close {
  background: none; border: none; color: rgba(255,255,255,0.4);
  font-size: 13px; cursor: pointer;
}
.modal-body { padding: 20px; display: flex; flex-direction: column; gap: 12px; }
.modal-footer {
  display: flex; gap: 8px; justify-content: flex-end;
  padding: 12px 20px; border-top: 1px solid rgba(255,255,255,0.07);
}
.field { display: flex; flex-direction: column; gap: 4px; }
.field-label {
  font-size: 10px; letter-spacing: 0.15em; text-transform: uppercase;
  color: rgba(255,255,255,0.4);
}
.required { color: #f87171; margin-left: 2px; }
.field-input {
  background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.12);
  color: rgba(255,255,255,0.85); padding: 8px 10px; font-size: 13px;
}
.field-input:focus { outline: none; border-color: rgba(255,255,255,0.3); }
.members-label {
  font-size: 10px; letter-spacing: 0.18em; text-transform: uppercase;
  color: rgba(255,255,255,0.25); padding-top: 4px;
  border-top: 1px solid rgba(255,255,255,0.06);
}
.field-error { font-size: 11px; color: #f87171; margin-top: -4px; }
.btn-ghost {
  background: none; border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.55); padding: 7px 18px; font-size: 11px;
  letter-spacing: 0.1em; text-transform: uppercase; cursor: pointer;
}
.btn-primary {
  background: rgba(255,255,255,0.9); color: #111; border: none;
  padding: 7px 18px; font-size: 11px; letter-spacing: 0.1em;
  text-transform: uppercase; cursor: pointer;
  clip-path: polygon(4px 0%, 100% 0%, calc(100% - 4px) 100%, 0% 100%);
}

.toast {
  position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
  background: rgba(255,255,255,0.1); border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.85); padding: 10px 20px; font-size: 12px;
  letter-spacing: 0.1em; text-transform: uppercase; z-index: 300;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
}
.toast-enter-active, .toast-leave-active { transition: opacity 0.2s, transform 0.2s; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(8px); }

@media (max-width: 640px) {
  .pt-header { display: none; }
  .pt-row {
    grid-template-columns: 32px 1fr;
    grid-template-rows: auto auto;
  }
  .pt-col-format, .pt-col-genres { grid-column: 2; }
  .pt-col-actions { grid-column: 1 / -1; justify-content: flex-start; }
  .pt-subrow { padding-left: 12px; }
}
</style>
