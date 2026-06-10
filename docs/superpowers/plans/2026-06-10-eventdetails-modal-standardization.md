# EventDetails Modal Standardization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Standardize the Walk-in Form and Scoring Criteria modals in EventDetails to use the same shell, shape, and typography as the Genre Entries modal.

**Architecture:** Two components are modified — `CreateParticipantForm.vue` (template rewrite: remove ActionDoneModal wrapper, add standardized Teleport shell, replace checkbox genre list with chip toggles) and `ScoringCriteriaModal.vue` (visual update: restructure panel into header/body/footer sections, replace nested card-hover items with para-chip rows). No logic changes in either component. Genre Entries modal is untouched.

**Tech Stack:** Vue 3, Tailwind CSS, PrimeIcons, Vitest + Vue Test Utils

---

## File Map

| File | Change |
|------|--------|
| `BES-frontend/src/components/CreateParticipantForm.vue` | Template rewrite + add `toggleGenre` helper |
| `BES-frontend/src/components/ScoringCriteriaModal.vue` | Template restructure (visual only, no logic changes) |
| `BES-frontend/src/utils/__tests__/CreateParticipantForm.test.js` | Update tests to match new chip-based UI |

---

## Task 1: Update CreateParticipantForm tests to match new chip UI

The existing tests use `input[type="checkbox"]` to select genres and check old label text. Update them to match the new chip-button genre selection and new label text before touching the component.

**Files:**
- Modify: `BES-frontend/src/utils/__tests__/CreateParticipantForm.test.js`

- [ ] **Step 1.1: Replace all three genre-selection tests**

Replace the file content from line 19 onward with:

```js
describe('CreateParticipantForm.vue', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders stage name input and genre chips when open', async () => {
    const wrapper = mount(CreateParticipantForm, {
      props: {
        show: true,
        event: 'TestEvent',
        eventGenres: [
          { name: 'Popping', format: '3v3' },
          { name: 'Waacking', format: '1v1' },
        ],
      },
    })
    await wrapper.vm.$nextTick()
    // Stage name input
    expect(wrapper.find('input[type="text"]').exists()).toBe(true)
    // Genre chips rendered as buttons (not checkboxes)
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false)
    const genreButtons = wrapper.findAll('button').filter(b => b.text().includes('Popping'))
    expect(genreButtons.length).toBeGreaterThan(0)
  })

  it('toggleGenre adds and removes genre from createTable.genres', async () => {
    const wrapper = mount(CreateParticipantForm, {
      props: {
        show: true,
        event: 'TestEvent',
        eventGenres: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.createTable.genres).toEqual([])

    wrapper.vm.toggleGenre('Popping')
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.createTable.genres).toContain('Popping')

    wrapper.vm.toggleGenre('Popping')
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.createTable.genres).not.toContain('Popping')
  })

  it('shows inline team section when a team-format genre chip is toggled on', async () => {
    const wrapper = mount(CreateParticipantForm, {
      props: {
        show: true,
        event: 'TestEvent',
        eventGenres: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()

    wrapper.vm.toggleGenre('Popping')
    await wrapper.vm.$nextTick()

    // Section rule label for the genre
    expect(wrapper.text()).toContain('Popping')
    // Team/Solo toggle buttons appear
    const teamBtn = wrapper.findAll('button').filter(b => b.text() === 'Team')
    expect(teamBtn.length).toBeGreaterThan(0)
  })

  it('shows team name and member inputs when team mode active', async () => {
    const wrapper = mount(CreateParticipantForm, {
      props: {
        show: true,
        event: 'TestEvent',
        eventGenres: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()

    wrapper.vm.toggleGenre('Popping')
    await wrapper.vm.$nextTick()

    // entryModes defaults to 'team' for team-format genre
    expect(wrapper.vm.entryModes['Popping']).toBe('team')
    // Team name input and member inputs present
    const inputs = wrapper.findAll('input[type="text"]')
    // stage name + team name + 2 members = 4
    expect(inputs.length).toBe(4)
  })

  it('submits walk-in with correct args (solo mode)', async () => {
    const wrapper = mount(CreateParticipantForm, {
      props: {
        show: true,
        event: 'TestEvent',
        eventGenres: [{ name: 'Popping', format: '3v3' }],
      },
    })
    await wrapper.vm.$nextTick()

    wrapper.vm.name = 'Dancer1'
    wrapper.vm.toggleGenre('Popping')
    await wrapper.vm.$nextTick()

    wrapper.vm.entryModes['Popping'] = 'solo'
    await wrapper.vm.$nextTick()

    wrapper.vm.submitNewEntry()
    await wrapper.vm.$nextTick()

    expect(addWalkinToSystem).toHaveBeenCalledWith(
      'Dancer1', 'TestEvent', 'Popping', '', [], '', 'solo'
    )
  })
})
```

- [ ] **Step 1.2: Run tests — expect failures**

```bash
cd BES-frontend && npm test -- --run src/utils/__tests__/CreateParticipantForm.test.js
```

Expected: most tests fail because `toggleGenre` doesn't exist yet and the template still uses checkboxes.

---

## Task 2: Add `toggleGenre` helper to CreateParticipantForm.vue script

**Files:**
- Modify: `BES-frontend/src/components/CreateParticipantForm.vue`

- [ ] **Step 2.1: Add `toggleGenre` after the `updateMemberName` function (around line 59)**

```js
function toggleGenre(genreName) {
  const idx = createTable.genres.indexOf(genreName)
  if (idx >= 0) {
    createTable.genres.splice(idx, 1)
  } else {
    createTable.genres.push(genreName)
  }
}
```

- [ ] **Step 2.2: Run the toggleGenre test only — expect pass**

```bash
cd BES-frontend && npm test -- --run src/utils/__tests__/CreateParticipantForm.test.js -t "toggleGenre"
```

Expected: `toggleGenre adds and removes genre` → PASS

---

## Task 3: Rebuild CreateParticipantForm.vue template

Replace the entire `<template>` block. The `<script setup>` and ActionDoneModal result dialogs are kept; only the main form shell changes.

**Files:**
- Modify: `BES-frontend/src/components/CreateParticipantForm.vue`

- [ ] **Step 3.1: Replace the `<template>` block**

Replace everything from `<template>` down to (but not including) the first result `<ActionDoneModal` (the `showError` one) with:

```vue
<template>
  <!-- Main walk-in form -->
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="props.show"
        class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
      >
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="$emit('close')" />
        <div class="card-hover relative w-full sm:max-w-md flex flex-col" style="max-height: 85vh;">
          <div class="corner-bar-tl"></div>
          <div class="corner-bar-bl"></div>

          <!-- Header -->
          <div class="flex items-center justify-between px-4 py-3 border-b border-surface-600/30 shrink-0">
            <div class="flex items-center gap-2">
              <i class="pi pi-user-plus text-content-muted text-xs"></i>
              <span class="type-body text-content-primary">{{ props.title }}</span>
              <span class="badge-neutral type-label">{{ props.event }}</span>
            </div>
            <button
              @click="$emit('close')"
              class="para-chip-sm px-2 py-1 type-label text-content-muted hover:text-content-primary transition-colors"
            >
              <i class="pi pi-times text-xs"></i>
            </button>
          </div>

          <!-- Body -->
          <div class="flex-1 overflow-y-auto p-4 space-y-4 min-h-0">

            <!-- Stage name -->
            <div>
              <label class="type-label text-content-muted mb-1.5 block">Stage Name</label>
              <input
                v-model="name"
                type="text"
                placeholder="Enter stage name…"
                class="input-base"
                :class="formTouched && !name.trim() ? '!border-red-400/60' : ''"
                @input="formTouched = true"
                @keyup.enter="submitNewEntry"
              />
            </div>

            <!-- Division chips -->
            <div>
              <div class="flex items-center justify-between mb-2">
                <label class="type-label text-content-muted">Divisions</label>
                <span class="type-label text-content-muted">tap to select</span>
              </div>
              <div class="flex flex-wrap gap-1.5">
                <template v-for="group in groupedDivisions" :key="group.genreId">
                  <button
                    v-for="g in group.divisions"
                    :key="g.genreName"
                    type="button"
                    @click="toggleGenre(g.genreName)"
                    class="para-chip-sm px-3 py-1.5 type-label transition-all flex items-center gap-1.5"
                    :class="createTable.genres.includes(g.genreName)
                      ? 'text-accent border-[color:var(--accent-muted)] bg-[var(--accent-subtle)]'
                      : 'text-content-secondary hover:text-accent'"
                  >
                    <span
                      class="inline-block w-1.5 h-1.5 rounded-full flex-shrink-0"
                      :style="createTable.genres.includes(g.genreName)
                        ? 'background:var(--accent-color);box-shadow:0 0 5px var(--accent-muted)'
                        : 'background:rgba(255,255,255,0.2)'"
                    ></span>
                    {{ g.genreName }}
                    <span v-if="g.format" class="opacity-40 normal-case">{{ g.format }}</span>
                  </button>
                </template>
              </div>
            </div>

            <!-- Per-genre team details (inline expansion) -->
            <template v-for="g in createTable.genres" :key="g">
              <template v-if="isTeamFormat(g)">
                <div class="section-rule">
                  <span class="section-rule-label">{{ g }} · {{ selectedFormat(g) }}</span>
                  <div class="section-rule-line"></div>
                </div>

                <!-- Solo not allowed warning -->
                <div
                  v-if="!isSoloAllowed(g)"
                  class="flex items-center gap-2 px-3 py-2 para-chip"
                  style="border-left: 3px solid rgb(251 191 36); background: rgba(251,191,36,0.07);"
                >
                  <span class="inline-block w-1.5 h-1.5 rounded-full bg-amber-400 shrink-0" style="box-shadow:0 0 6px rgba(251,191,36,0.6)"></span>
                  <span class="type-label text-amber-400">Solo entries not allowed — team entry only.</span>
                </div>

                <!-- Team / Solo toggle -->
                <div v-if="isSoloAllowed(g)">
                  <label class="type-label text-content-muted mb-2 block">Entry Type</label>
                  <div class="flex border border-surface-600/60 overflow-hidden">
                    <button
                      type="button"
                      @click="entryModes[g] = 'team'"
                      class="flex-1 px-4 py-2 type-label transition-all"
                      :class="entryModes[g] === 'team'
                        ? 'bg-[var(--accent-subtle)] text-accent'
                        : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
                    >
                      Team
                    </button>
                    <button
                      type="button"
                      @click="entryModes[g] = 'solo'"
                      class="flex-1 px-4 py-2 type-label transition-all border-l border-surface-600/60"
                      :class="entryModes[g] === 'solo'
                        ? 'bg-[var(--accent-subtle)] text-accent'
                        : 'bg-surface-800 text-content-secondary hover:bg-surface-700'"
                    >
                      Solo
                    </button>
                  </div>
                  <p v-if="entryModes[g] === 'solo'" class="type-label text-content-muted mt-1.5">
                    Auditions individually. Can be grouped into a crew after auditions.
                  </p>
                </div>

                <!-- Team name + members -->
                <template v-if="entryModes[g] !== 'solo' && additionalMembersCountForGenre(g) > 0">
                  <div>
                    <label class="type-label text-content-muted mb-1.5 block">Team Name</label>
                    <input
                      v-model="teamNames[g]"
                      type="text"
                      placeholder="Enter team name…"
                      class="input-base"
                      :class="formTouched && !(teamNames[g] || '').trim() ? '!border-red-400/60' : ''"
                      @input="formTouched = true"
                    />
                  </div>
                  <div>
                    <label class="type-label text-content-muted mb-1.5 block">Team Members</label>
                    <p class="type-label text-content-muted mb-2 normal-case" style="font-size:0.65rem">
                      {{ name || 'Stage name' }} is Member 1. Enter the other {{ additionalMembersCountForGenre(g) }}.
                    </p>
                    <div class="space-y-2">
                      <input
                        v-for="i in additionalMembersCountForGenre(g)"
                        :key="i"
                        :value="(teamMemberNames[g] || [])[i - 1] || ''"
                        @input="updateMemberName(g, i - 1, $event.target.value); formTouched = true"
                        type="text"
                        :placeholder="`Member ${i + 1} stage name…`"
                        class="input-base"
                        :class="formTouched && !((teamMemberNames[g] || [])[i - 1] || '').trim() ? '!border-red-400/60' : ''"
                      />
                    </div>
                  </div>
                </template>
              </template>
            </template>

          </div>

          <!-- Footer -->
          <div class="flex gap-2 justify-end px-4 py-3 border-t border-surface-600/30 shrink-0">
            <button
              @click="$emit('close')"
              class="para-chip-sm px-4 py-2 type-label text-content-muted hover:text-content-primary transition-colors"
            >
              Cancel
            </button>
            <button
              @click="submitNewEntry"
              :disabled="!canSubmit"
              class="para-chip-sm px-4 py-2 type-label transition-all disabled:opacity-40 disabled:cursor-not-allowed text-accent border-[color:var(--accent-muted)] hover:bg-[var(--accent-subtle)]"
            >
              Add Participant
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
```

- [ ] **Step 3.2: Run all CreateParticipantForm tests**

```bash
cd BES-frontend && npm test -- --run src/utils/__tests__/CreateParticipantForm.test.js
```

Expected: all 5 tests pass.

- [ ] **Step 3.3: Commit**

```bash
git add BES-frontend/src/components/CreateParticipantForm.vue \
        BES-frontend/src/utils/__tests__/CreateParticipantForm.test.js
git commit -m "ui: rebuild CreateParticipantForm with standardized slide-up shell and chip genre selection"
```

---

## Task 4: Rebuild ScoringCriteriaModal.vue shell + header

Replace the outer panel structure (currently `card-hover p-6` with no sections) with the standardized header/body/footer layout. Slide-up on mobile.

**Files:**
- Modify: `BES-frontend/src/components/ScoringCriteriaModal.vue`

- [ ] **Step 4.1: Replace the outer `<div>` (panel) and header section**

In the template, find:
```vue
<!-- Panel -->
<div class="card-hover p-6 relative w-full max-w-xl flex flex-col max-h-[85vh]">
  <div class="corner-bar-tl"></div>
  <div class="corner-bar-bl"></div>

  <!-- Header -->
  <div class="flex items-center justify-between flex-shrink-0 mb-4">
    <div>
      <p class="type-page-title" style="font-size: 18px;">Scoring Criteria</p>
      <p class="type-label text-content-muted mt-0.5">Define what judges score on. Leave empty for a single 0–10 score.</p>
    </div>
    <button
      @click="close"
      class="p-1.5 para-chip-sm type-label text-content-muted hover:text-content-primary transition-colors"
    >
      <i class="pi pi-times text-sm" />
    </button>
  </div>
```

Replace with:
```vue
<!-- Panel -->
<div class="card-hover relative w-full sm:max-w-lg flex flex-col max-h-[85vh]">
  <div class="corner-bar-tl"></div>
  <div class="corner-bar-bl"></div>

  <!-- Header -->
  <div class="flex items-center justify-between px-4 py-3 border-b border-surface-600/30 flex-shrink-0">
    <div class="flex items-center gap-2">
      <i class="pi pi-sliders-h text-content-muted text-xs"></i>
      <span class="type-body text-content-primary">Scoring Criteria</span>
      <span class="badge-neutral type-label">{{ props.eventName }}</span>
    </div>
    <button
      @click="close"
      class="para-chip-sm px-2 py-1 type-label text-content-muted hover:text-content-primary transition-colors"
    >
      <i class="pi pi-times text-xs" />
    </button>
  </div>
```

- [ ] **Step 4.2: Update the outer modal positioning div to slide-up on mobile**

Find:
```vue
<div
  v-if="modelValue"
  class="fixed inset-0 z-50 flex items-center justify-center p-4"
  @click.self="close"
>
```

Replace with:
```vue
<div
  v-if="modelValue"
  class="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-0 sm:p-4"
>
```

- [ ] **Step 4.3: Update tab description text**

Find:
```vue
<div class="px-5 pt-3 pb-0 flex-shrink-0">
  <p v-if="activeTab === 'event-level'" class="text-xs text-content-muted italic">
    These criteria apply to any genre that doesn't have its own specific criteria set.
  </p>
  <p v-else class="text-xs text-content-muted italic">
    Criteria specific to <span class="text-primary-400 font-semibold">{{ activeTab }}</span>. Overrides the Event Default.
  </p>
</div>
```

Replace with:
```vue
<div class="px-4 pt-2 pb-0 flex-shrink-0">
  <p v-if="activeTab === 'event-level'" class="type-label text-content-muted">
    Applies to any genre without its own criteria.
  </p>
  <p v-else class="type-label text-content-muted">
    Criteria for <span class="text-accent">{{ activeTab }}</span> — overrides event default.
  </p>
</div>
```

- [ ] **Step 4.4: Fix criteria list container padding**

Find:
```vue
<div class="flex-1 overflow-y-auto px-5 py-4 space-y-2 min-h-0">
```

Replace with:
```vue
<div class="flex-1 overflow-y-auto px-4 py-3 space-y-2 min-h-0">
```

- [ ] **Step 4.5: Fix tab row padding**

Find:
```vue
<div class="flex gap-1 px-5 pt-4 pb-0 overflow-x-auto flex-shrink-0" style="scrollbar-width: none;">
```

Replace with:
```vue
<div class="flex gap-1 px-4 pt-3 pb-0 overflow-x-auto flex-shrink-0" style="scrollbar-width: none;">
```

- [ ] **Step 4.6: Run frontend build check**

```bash
cd BES-frontend && npm run build 2>&1 | tail -20
```

Expected: build completes without errors.

---

## Task 5: Replace criteria card-hover items with para-chip rows

**Files:**
- Modify: `BES-frontend/src/components/ScoringCriteriaModal.vue`

- [ ] **Step 5.1: Replace the criteria list items (view + edit rows)**

Find:
```vue
<template v-else>
  <div
    v-for="c in activeCriteria"
    :key="c.id"
    class="card-hover p-3 relative"
  >
    <!-- View row -->
    <div
      v-if="editingId !== c.id"
      class="flex items-center justify-between"
    >
      <div class="flex items-center gap-2 flex-1 min-w-0">
        <span class="text-sm font-semibold text-content-primary truncate">{{ c.name }}</span>
        <span v-if="c.weight != null" class="badge-neutral type-label shrink-0">
          ×{{ c.weight }}
        </span>
      </div>
      <div class="flex items-center gap-1 ml-2 shrink-0">
        <button
          @click="startEdit(c)"
          class="p-1.5 para-chip-sm type-label text-content-muted hover:text-accent transition-colors"
          title="Edit"
        >
          <i class="pi pi-pencil text-xs" />
        </button>
        <button
          @click="remove(c.id)"
          class="p-1.5 para-chip-sm type-label text-content-muted hover:text-red-400 transition-colors"
          title="Delete"
        >
          <i class="pi pi-times text-xs" />
        </button>
      </div>
    </div>

    <!-- Edit row -->
    <div v-else class="space-y-2">
      <div class="flex gap-2">
        <input
          v-model="editName"
          placeholder="Criterion name"
          class="flex-1 min-w-0 input-base"
          @keydown.enter="saveEdit"
          @keydown.escape="cancelEdit"
        />
        <input
          v-model="editWeight"
          type="number" min="0" step="0.5" placeholder="Weight"
          class="input-base"
          style="width: 6rem; flex-shrink: 0"
        />
      </div>
      <div class="flex gap-2">
        <button
          @click="saveEdit"
          :disabled="editSaving || !editName.trim()"
          class="flex-1 py-1.5 bg-accent para-chip type-label text-surface-900 disabled:opacity-40 disabled:cursor-not-allowed transition-all"
        >
          <i v-if="editSaving" class="pi pi-spin pi-spinner mr-1" />
          Save
        </button>
        <button
          @click="cancelEdit"
          class="px-3 py-1.5 para-chip type-label border-accent text-content-muted hover:text-content-primary transition-all"
        >
          Cancel
        </button>
      </div>
    </div>
  </div>
</template>
```

Replace with:
```vue
<template v-else>
  <div
    v-for="c in activeCriteria"
    :key="c.id"
    class="para-chip px-3 py-2.5 transition-all duration-150"
    :class="editingId === c.id
      ? 'border-[color:var(--accent-muted)] bg-[var(--accent-subtle)]'
      : 'border-surface-600/40'"
  >
    <!-- View row -->
    <div v-if="editingId !== c.id" class="flex items-center gap-3">
      <span
        class="inline-block w-2 h-2 rounded-full flex-shrink-0"
        :style="c.weight != null
          ? 'background:var(--accent-color);box-shadow:0 0 8px var(--accent-muted)'
          : 'background:rgba(255,255,255,0.15)'"
      ></span>
      <span class="type-body text-content-primary flex-1 truncate">{{ c.name }}</span>
      <span v-if="c.weight != null" class="badge-neutral type-label shrink-0">×{{ c.weight }}</span>
      <div class="flex items-center gap-1 shrink-0">
        <button
          @click="startEdit(c)"
          class="p-1.5 para-chip-sm type-label text-content-muted hover:text-accent transition-colors"
          title="Edit"
        >
          <i class="pi pi-pencil text-xs" />
        </button>
        <button
          @click="remove(c.id)"
          class="p-1.5 para-chip-sm type-label text-content-muted hover:text-red-400 transition-colors"
          title="Delete"
        >
          <i class="pi pi-times text-xs" />
        </button>
      </div>
    </div>

    <!-- Edit row (inline expansion) -->
    <div v-else class="space-y-2">
      <div class="flex gap-2">
        <input
          v-model="editName"
          placeholder="Criterion name"
          class="flex-1 min-w-0 input-base"
          @keydown.enter="saveEdit"
          @keydown.escape="cancelEdit"
        />
        <input
          v-model="editWeight"
          type="number" min="0" step="0.5" placeholder="Weight"
          class="input-base"
          style="width: 6rem; flex-shrink: 0"
        />
      </div>
      <div class="flex gap-2">
        <button
          @click="saveEdit"
          :disabled="editSaving || !editName.trim()"
          class="flex-1 py-1.5 bg-accent para-chip type-label text-surface-900 disabled:opacity-40 disabled:cursor-not-allowed transition-all"
        >
          <i v-if="editSaving" class="pi pi-spin pi-spinner mr-1" />
          Save
        </button>
        <button
          @click="cancelEdit"
          class="px-3 py-1.5 para-chip type-label border-accent text-content-muted hover:text-content-primary transition-all"
        >
          Cancel
        </button>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 5.2: Replace the add form container**

Find:
```vue
<!-- Add form -->
<div v-if="showAdd" class="card-hover p-3 space-y-2">
```

Replace with:
```vue
<!-- Add form -->
<div v-if="showAdd" class="para-chip px-3 py-3 space-y-2 border-surface-600/40">
```

---

## Task 6: Update ScoringCriteriaModal footer buttons

**Files:**
- Modify: `BES-frontend/src/components/ScoringCriteriaModal.vue`

- [ ] **Step 6.1: Replace footer**

Find:
```vue
<!-- Footer actions -->
<div class="flex items-center gap-2 flex-shrink-0 pt-4 border-t border-[rgba(255,255,255,0.07)]">
  <button
    v-if="!showAdd"
    @click="showAdd = true"
    class="flex items-center gap-1.5 px-3 py-1.5 para-chip-sm type-label border-accent text-content-muted hover:text-accent transition-all duration-150"
  >
    <i class="pi pi-plus text-xs" />
    Add Criterion
  </button>

  <div class="flex-1" />

  <!-- Copy to all genres (not shown on Event Default tab) -->
  <button
    v-if="activeTab !== 'event-level' && genres.length > 1"
    @click="applyToAllGenres"
    :disabled="applyingAll || activeCriteria.length === 0"
    class="flex items-center gap-1.5 px-3 py-1.5 para-chip-sm type-label border-accent
           disabled:opacity-40 disabled:cursor-not-allowed transition-all duration-150"
    :class="appliedAll ? 'text-emerald-400' : 'text-content-muted hover:text-accent'"
    title="Replaces criteria in all other genres with these"
  >
    <i v-if="applyingAll" class="pi pi-spin pi-spinner text-xs" />
    <i v-else-if="appliedAll" class="pi pi-check text-xs" />
    <i v-else class="pi pi-copy text-xs" />
    {{ appliedAll ? 'Copied!' : 'Copy to all genres' }}
  </button>
</div>
```

Replace with:
```vue
<!-- Footer actions -->
<div class="flex items-center gap-2 flex-shrink-0 px-4 py-3 border-t border-surface-600/30">
  <button
    v-if="!showAdd"
    @click="showAdd = true"
    class="flex items-center gap-1.5 px-3 py-1.5 para-chip-sm type-label text-content-muted hover:text-accent transition-all duration-150"
  >
    <i class="pi pi-plus text-xs" />
    Add Criterion
  </button>

  <div class="flex-1" />

  <button
    v-if="activeTab !== 'event-level' && genres.length > 1"
    @click="applyToAllGenres"
    :disabled="applyingAll || activeCriteria.length === 0"
    class="flex items-center gap-1.5 px-3 py-1.5 para-chip-sm type-label disabled:opacity-40 disabled:cursor-not-allowed transition-all duration-150"
    :class="appliedAll ? 'text-emerald-400' : 'text-content-muted hover:text-accent'"
    title="Replaces criteria in all other genres with these"
  >
    <i v-if="applyingAll" class="pi pi-spin pi-spinner text-xs" />
    <i v-else-if="appliedAll" class="pi pi-check text-xs" />
    <i v-else class="pi pi-copy text-xs" />
    {{ appliedAll ? 'Copied!' : 'Copy to all genres' }}
  </button>
</div>
```

- [ ] **Step 6.2: Run full test suite**

```bash
cd BES-frontend && npm test -- --run
```

Expected: all tests pass (no regressions).

- [ ] **Step 6.3: Run build check**

```bash
cd BES-frontend && npm run build 2>&1 | tail -20
```

Expected: no errors.

- [ ] **Step 6.4: Commit ScoringCriteriaModal changes**

```bash
git add BES-frontend/src/components/ScoringCriteriaModal.vue
git commit -m "ui: standardize ScoringCriteriaModal — slide-up shell, icon+badge header, para-chip criteria rows"
```

---

## Task 7: Visual verification

- [ ] **Step 7.1: Start the dev server**

```bash
cd BES-frontend && npm run dev
```

- [ ] **Step 7.2: Open EventDetails for any event and verify Walk-in Form**

Navigate to `/events/<any-event-name>`. Click "Add Walk-in".

Checklist:
- [ ] Panel slides up from bottom on mobile / centers on desktop
- [ ] Header: user-plus icon · "Add Walk-in" title · event name badge · × close
- [ ] Stage name is an `input-base` text field
- [ ] Genres are displayed as `para-chip-sm` chips with glowing dots (no checkboxes)
- [ ] Tapping a chip toggles it (accent border + dot when selected, dim when not)
- [ ] For a team-format genre: selecting it reveals section rule + Team/Solo toggle + team name + member fields inline
- [ ] Deselecting a genre hides the inline section
- [ ] "Add Participant" button is disabled until stage name is filled and at least one genre is selected

- [ ] **Step 7.3: Open Scoring Criteria modal and verify**

Click "Configure Criteria" on any genre accordion in EventDetails.

Checklist:
- [ ] Panel slides up from bottom on mobile / centers on desktop
- [ ] Header: sliders-h icon · "Scoring Criteria" title · event name badge · × close
- [ ] Genre tabs are `para-chip-sm` chips; active tab has accent fill
- [ ] Criteria rows are `para-chip` parallelogram shape (no rounded-xl card)
- [ ] Rows with weight: glowing accent dot; rows without weight: dim dot
- [ ] Edit inline expansion works (row expands, Save/Cancel appear)
- [ ] "Add Criterion" toggle in footer works
- [ ] "Copy to all genres" button present (when > 1 genre)

- [ ] **Step 7.4: Final commit if any tweaks were made**

```bash
git add -p
git commit -m "ui: visual tweaks after manual verification of standardized modals"
```
