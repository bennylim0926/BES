# Judge Assignment Mode — Frontend UI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire judge assignment mode through all frontend surfaces: a per-category toggle in EventDetails, judge-grouped list in UpdateEventDetails, judge field visibility in the walk-in form, per-judge tabs in AuditionAdjust, a concurrent grid view for the emcee in AuditionList, and a multi-panel OBS audition display.

**Architecture:** `GetEventCategoryDto.judgeAssignmentMode` (from Backend Plan Task 2) is the single source of truth. Each view reads it from the categories list already fetched on load. New API calls: `toggleJudgeAssignmentMode` and `getParticipantsByJudge` added to `api.js`. New component `EmceeJudgeGridView.vue` replaces `EmceeRoundView` for the emcee when mode is active. `AuditionDisplay.vue` renders a judge-lane grid when `state.judgeMode` is true.

**Tech Stack:** Vue 3 Composition API, Vitest + Vue Test Utils, Tailwind CSS, design system from `base.css` (Oswald / Inter, parallelogram chips, section rules)

**Depends on:** Backend Plan (`2026-06-28-judge-assignment-mode-backend.md`) must be deployed first.

## Global Constraints

- Follow the design system in `docs/superpowers/specs/2026-05-30-ui-overhaul-design.md` and `CLAUDE.md`
- Parallelogram chips (`.badge-*`, `.card`), section rules (`.section-rule-label`), Oswald chrome, Inter prose
- No `rounded-2xl` cards, no `rounded-full` badges
- User-typed names read as-typed (`.type-name`); chrome labels uppercase
- Run `npm test` from `BES-frontend/` to verify each task; run `npm run dev` to smoke-test in browser
- All new API calls in `BES-frontend/src/utils/api.js`
- Design system accent color via `var(--accent-color)` — never hardcode hex for interactive chrome

---

### Task 1: New API calls in `api.js`

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

**Interfaces:**
- Produces:
  - `toggleJudgeAssignmentMode(categoryId, enabled)` → `fetch POST /api/v1/event/categories/{categoryId}/judge-assignment-mode`
  - `getParticipantsByJudge(eventName, categoryId)` → `fetch GET /api/v1/event/{eventName}/categories/{categoryId}/participants-by-judge` → `Map<String, Array<{auditionNumber, participantName, memberNames, participantId}>>`

- [ ] **Step 1: Write the failing test**

Create `BES-frontend/src/utils/__tests__/judgeAssignmentApi.test.js`:

```js
import { describe, it, expect, vi, beforeEach } from 'vitest'

// Inline the functions under test so tests are self-contained
const domain = ''

const toggleJudgeAssignmentMode = async (categoryId, enabled) => {
  return await fetch(`${domain}/api/v1/event/categories/${categoryId}/judge-assignment-mode`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled })
  })
}

const getParticipantsByJudge = async (eventName, categoryId) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/categories/${categoryId}/participants-by-judge`, {
      credentials: 'include'
    })
    if (!res.ok) return {}
    return await res.json()
  } catch { return {} }
}

describe('toggleJudgeAssignmentMode', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn()) })

  it('POSTs to the correct URL with enabled:true', async () => {
    fetch.mockResolvedValueOnce({ ok: true })
    await toggleJudgeAssignmentMode(42, true)
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/event/categories/42/judge-assignment-mode',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ enabled: true }) })
    )
  })
})

describe('getParticipantsByJudge', () => {
  beforeEach(() => { vi.stubGlobal('fetch', vi.fn()) })

  it('returns parsed JSON on success', async () => {
    fetch.mockResolvedValueOnce({ ok: true, json: async () => ({ Jay: [{ auditionNumber: 1, participantName: 'Alice' }] }) })
    const result = await getParticipantsByJudge('Showcase', 5)
    expect(result).toEqual({ Jay: [{ auditionNumber: 1, participantName: 'Alice' }] })
  })

  it('returns empty object on fetch failure', async () => {
    fetch.mockRejectedValueOnce(new Error('network'))
    const result = await getParticipantsByJudge('Showcase', 5)
    expect(result).toEqual({})
  })
})
```

- [ ] **Step 2: Run test to confirm it passes (self-contained)**

```bash
cd BES-frontend && npm test -- judgeAssignmentApi --run 2>&1 | tail -15
```
Expected: all 3 tests pass (the functions are inlined, so this tests the logic patterns)

- [ ] **Step 3: Add functions to api.js**

At the end of `BES-frontend/src/utils/api.js`, add:

```js
export const toggleJudgeAssignmentMode = async (categoryId, enabled) => {
  return await fetch(`${domain}/api/v1/event/categories/${categoryId}/judge-assignment-mode`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled })
  })
}

export const getParticipantsByJudge = async (eventName, categoryId) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/categories/${categoryId}/participants-by-judge`, {
      credentials: 'include'
    })
    if (!res.ok) return {}
    return await res.json()
  } catch { return {} }
}
```

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/utils/api.js BES-frontend/src/utils/__tests__/judgeAssignmentApi.test.js
git commit -m "feat: add toggleJudgeAssignmentMode and getParticipantsByJudge API calls"
```

---

### Task 2: EventDetails — judge assignment mode toggle per category

**Files:**
- Modify: `BES-frontend/src/views/EventDetails.vue`

**Context:** `EventDetails.vue` has a category accordion. Each category already shows its judges list (from `event_category_judge`). The toggle should appear in the category settings area only when the category has ≥ 2 judges assigned. It reads `category.judgeAssignmentMode` from `GetEventCategoryDto` and calls `toggleJudgeAssignmentMode` on change.

**Interfaces:**
- Consumes: `toggleJudgeAssignmentMode(categoryId, enabled)` (Task 1); `category.judgeAssignmentMode` on category objects loaded via `getCategoriesByEvent`; `category.judges` list (already in the accordion)
- Produces: visible toggle in category settings; calls backend on toggle; updates local state

- [ ] **Step 1: Read the category section of EventDetails.vue**

Open `BES-frontend/src/views/EventDetails.vue` and locate the category accordion section. Find where the judge list for each category is rendered and where category settings (roundLabel, numberColor, etc.) appear. Note the exact variable name for the categories array (likely `divisions` or `categories`).

- [ ] **Step 2: Add judgeAssignmentMode to the import and local toggle handler**

Find the `<script setup>` block. Add the import:

```js
import { toggleJudgeAssignmentMode } from '@/utils/api'
```

Add a handler (place near other category mutation handlers):

```js
const setJudgeAssignmentMode = async (category, enabled) => {
  const res = await toggleJudgeAssignmentMode(category.eventCategoryId, enabled)
  if (res && res.ok) {
    category.judgeAssignmentMode = enabled
  }
}
```

- [ ] **Step 3: Add the toggle UI inside the category accordion**

Locate the section inside the category accordion where judge names are listed. After the judge list (or in the category settings block), add the following — visible only when the category has ≥ 2 judges:

```html
<!-- Judge assignment mode toggle — only shown when ≥2 judges are assigned -->
<div v-if="category.judges && category.judges.length >= 2" class="flex items-center gap-3 mt-3">
  <div class="flex-1">
    <span class="section-rule-label">JUDGE ASSIGNMENT MODE</span>
    <p class="type-prose text-content-muted mt-0.5">Each judge runs their own audition pool concurrently</p>
  </div>
  <button
    class="badge-sm px-3 py-1.5 transition-colors"
    :class="category.judgeAssignmentMode
      ? 'bg-green-500/15 border border-green-500/30 text-green-400'
      : 'bg-white/5 border border-white/10 text-content-muted'"
    @click="setJudgeAssignmentMode(category, !category.judgeAssignmentMode)"
  >
    <span class="type-label">{{ category.judgeAssignmentMode ? 'ON' : 'OFF' }}</span>
  </button>
</div>
```

Note: `category.judges` is populated from the `event_category_judge` join. Verify the exact property name by reading how the accordion renders judges in EventDetails — adjust `category.judges` to match the actual property name used (e.g., it may be `cat.judges` or via a separate `divisionJudges` computed).

- [ ] **Step 4: Smoke test in browser**

```bash
cd BES-frontend && npm run dev
```
Navigate to an event's EventDetails page. Open a category that has ≥2 judges assigned. Confirm the toggle appears. Click it — verify the button state changes and no console errors. Reload — confirm the state persisted (mode reads from backend on reload).

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "feat: add judge assignment mode toggle to EventDetails category accordion"
```

---

### Task 3: UpdateEventDetails — judge-grouped participant list

**Files:**
- Modify: `BES-frontend/src/views/UpdateEventDetails.vue`

**Context:** `UpdateEventDetails.vue` shows a table of participants per category. When `judgeAssignmentMode=true` for the selected category, participants should be visually grouped under their judge's name. Participants with no judge assigned show in an "Unassigned" group with an amber warning chip. Check-in errors for `no_judge_assigned` should be surfaced with a clear message.

**Interfaces:**
- Consumes: `category.judgeAssignmentMode` from categories list; participant rows have `.judgeName` (already in `GetEventCategoryParticipantDto` via the `judgeName` field)
- Produces: judge-grouped display; amber warning for unassigned participants when mode is on; `no_judge_assigned` check-in error message

- [ ] **Step 1: Locate the participant table and check-in error handling in UpdateEventDetails.vue**

Read `UpdateEventDetails.vue`. Find:
1. Where the categories list is stored (likely `eventDivisions` or `categories`)
2. How participants are rendered per category
3. Where check-in errors are currently shown (look for `already_checked_in` handling)
4. The variable that holds the currently selected category

- [ ] **Step 2: Add computed — grouped participants**

In the `<script setup>`, find the `selectedCategory` ref. Add:

```js
const selectedCategoryData = computed(() =>
  (eventDivisions.value || []).find(d => d.name === selectedCategory.value)
)

const participantsGroupedByJudge = computed(() => {
  if (!selectedCategoryData.value?.judgeAssignmentMode) return null
  const rows = groupedParticipants.value  // adjust to actual variable name for current category's participants
  const groups = {}
  for (const p of rows) {
    const key = p.judgeName || '__unassigned__'
    if (!groups[key]) groups[key] = []
    groups[key].push(p)
  }
  return groups
})
```

Note: adjust `groupedParticipants.value` to the actual variable name holding participant rows for the selected category in this view.

- [ ] **Step 3: Add judge group headers to the participant list template**

Find the `v-for` loop that renders participants. Wrap it with a conditional: if `participantsGroupedByJudge` is non-null, render grouped; otherwise render the flat list as before.

```html
<!-- Judge assignment mode: grouped view -->
<template v-if="participantsGroupedByJudge">
  <template v-for="(groupParticipants, judgeName) in participantsGroupedByJudge" :key="judgeName">
    <tr>
      <td :colspan="/* match your table column count */" class="pt-4 pb-1 px-2">
        <div class="flex items-center gap-2">
          <span v-if="judgeName === '__unassigned__'"
            class="badge-sm bg-amber-500/15 border border-amber-500/30 text-amber-400 px-2 py-0.5 type-label">
            UNASSIGNED
          </span>
          <span v-else class="section-rule-label type-name">{{ judgeName }}</span>
        </div>
      </td>
    </tr>
    <tr v-for="p in groupParticipants" :key="p.participantId">
      <!-- same row template as the flat list — copy it here -->
    </tr>
  </template>
</template>

<!-- Default: flat list (mode off) -->
<template v-else>
  <!-- existing v-for rows unchanged -->
</template>
```

- [ ] **Step 4: Handle no_judge_assigned check-in error**

Find where `already_checked_in` is handled (typically in the QR check-in handler). Add:

```js
if (res === 'no_judge_assigned') {
  openToast('Assign a judge to this participant before checking in', 'warning')
  return
}
```

Adjust `openToast` call to match the existing toast/modal pattern used in this file.

- [ ] **Step 5: Smoke test in browser**

Start dev server. Go to `UpdateEventDetails` for an event where one category has judge assignment mode ON and participants with mixed judge/unassigned status. Confirm:
- Groups render with judge name headers
- Unassigned group shows amber badge
- Categories with mode OFF still show flat list

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/UpdateEventDetails.vue
git commit -m "feat: group participants by judge in UpdateEventDetails when mode is enabled"
```

---

### Task 4: Walk-in form — show judge field prominently when mode enabled

**Files:**
- Modify: `BES-frontend/src/components/CreateParticipantForm.vue`

**Context:** `CreateParticipantForm` already has `selectedJudge` ref and `allJudges` array, and passes `selectedJudge` to `addWalkinToSystem`. The gap: the judge dropdown has no context about whether mode is enabled for the selected category, and it may always show or always hide. When the selected category has `judgeAssignmentMode=true`, the judge field should show with an "(optional)" label. When mode is off, hide it.

**Interfaces:**
- Consumes: `props.categories` (array of `GetEventCategoryDto` including `judgeAssignmentMode`); `selectedCategory` ref inside the form; `selectedJudge` ref (already exists)
- Produces: judge dropdown shown/hidden based on mode; label changes to show "ASSIGN JUDGE (OPTIONAL)"

- [ ] **Step 1: Verify props passed to CreateParticipantForm**

Read where `CreateParticipantForm` is used in `UpdateEventDetails.vue` (or `EventDetails.vue`). Check whether `categories` (the full DTO list with `judgeAssignmentMode`) is already passed as a prop. If not, pass it:

In the parent (`UpdateEventDetails.vue` or wherever the form is opened):
```html
<CreateParticipantForm
  ...existing props...
  :categories="eventDivisions"
/>
```

- [ ] **Step 2: Add computed for mode-active flag**

In `CreateParticipantForm.vue` `<script setup>`, after the existing `selectedCategory` ref, add:

```js
const props = defineProps({
  // ...existing props...
  categories: { type: Array, default: () => [] }
})

const selectedCategoryHasJudgeMode = computed(() => {
  if (!selectedCategory.value) return false
  const cat = props.categories.find(c => c.name === selectedCategory.value)
  return cat?.judgeAssignmentMode ?? false
})
```

If `props` is already defined in this file, just add `categories` to the existing `defineProps`.

- [ ] **Step 3: Update template — show judge dropdown only when mode is active**

Find the existing judge-related element in the form template. Replace it with:

```html
<div v-if="selectedCategoryHasJudgeMode" class="flex flex-col gap-1">
  <span class="type-label text-content-muted">ASSIGN JUDGE <span class="text-white/30">(OPTIONAL)</span></span>
  <select
    v-model="selectedJudge"
    class="bg-surface-800 border border-white/10 text-content px-3 py-2 type-name w-full"
    style="clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%)"
  >
    <option value="">No preference</option>
    <option v-for="j in allJudges.filter(j => j !== '')" :key="j" :value="j">{{ j }}</option>
  </select>
</div>
```

Make sure `selectedJudge` is reset to `""` when the selected category changes (it already is on form reset — verify the `resetForm()` or category-change handler clears it).

- [ ] **Step 4: Smoke test**

Open the walk-in form on a category with judge assignment mode ON. Confirm the judge dropdown appears. Switch to a category with mode OFF — confirm it disappears. Submit a walk-in with a judge selected — verify the assignment shows in UpdateEventDetails.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/components/CreateParticipantForm.vue \
        BES-frontend/src/views/UpdateEventDetails.vue
git commit -m "feat: show judge assignment dropdown in walk-in form when category mode is enabled"
```

---

### Task 5: AuditionAdjust — per-judge tabs when mode enabled

**Files:**
- Modify: `BES-frontend/src/views/AuditionAdjust.vue`

**Context:** `AuditionAdjust.vue` shows a number-assignment UI for all participants in a category. When `judgeAssignmentMode=true`, the participants are divided into per-judge pools (each with their own #1–N sequence). The fix: show a tab bar (one tab per judge) and filter the participant list + available numbers to the selected judge's pool.

**Interfaces:**
- Consumes: `getParticipantsByJudge(eventName, categoryId)` (Task 1); `getCategoriesByEvent` result (already fetched — add `judgeAssignmentMode` awareness); existing `divisionStats`, `assignBuffer`, `getAvailableNumbers` logic
- Produces: per-judge tab bar when mode active; each tab shows only that judge's participants and their available numbers (1 to judge's pool size)

- [ ] **Step 1: Read AuditionAdjust.vue in full**

Read `BES-frontend/src/views/AuditionAdjust.vue` to understand: how categories are loaded, how `divisionStats` is built, how `assignBuffer` works, how available number ranges are computed. Note the exact variable names used.

- [ ] **Step 2: Add judge mode state**

In `<script setup>`, add:

```js
import { getParticipantsByJudge } from '@/utils/api'

const selectedJudgeTab = ref('')       // name of the currently active judge tab
const judgePoolsByCategory = ref({})   // { [eventCategoryId]: Map<judgeName, [{auditionNumber, participantName, participantId}]> }

// Call this after loading categories to fetch judge pools for mode-enabled categories
const loadJudgePools = async () => {
  const event = selectedEvent.value
  if (!event) return
  for (const div of eventDivisions.value) {
    if (div.judgeAssignmentMode) {
      judgePoolsByCategory.value[div.eventCategoryId] = await getParticipantsByJudge(event, div.eventCategoryId)
    }
  }
}
```

Call `loadJudgePools()` after the existing categories/divisions fetch completes.

- [ ] **Step 3: Add computed for current category's mode and judge list**

```js
const currentCategoryData = computed(() =>
  (eventDivisions.value || []).find(d => d.eventCategoryId === selectedCategory.value?.eventCategoryId)
)

const currentCategoryJudgeMode = computed(() => currentCategoryData.value?.judgeAssignmentMode ?? false)

const currentCategoryJudgeNames = computed(() => {
  if (!currentCategoryJudgeMode.value) return []
  const pool = judgePoolsByCategory.value[currentCategoryData.value?.eventCategoryId] ?? {}
  return Object.keys(pool).filter(k => k !== '__unassigned__')
})

const currentJudgePool = computed(() => {
  if (!currentCategoryJudgeMode.value || !selectedJudgeTab.value) return []
  const pool = judgePoolsByCategory.value[currentCategoryData.value?.eventCategoryId] ?? {}
  return pool[selectedJudgeTab.value] ?? []
})

// When mode is on, available numbers are 1..judgePoolSize. When mode is off, use existing getAvailableNumbers().
const judgeAvailableNumbers = computed(() => {
  if (!currentCategoryJudgeMode.value || !selectedJudgeTab.value) return []
  const pool = currentJudgePool.value
  const taken = pool.filter(p => p.auditionNumber !== null).map(p => p.auditionNumber)
  const total = pool.length
  return Array.from({ length: total }, (_, i) => i + 1).filter(n => !taken.includes(n))
})
```

Auto-select first judge tab when category changes to a mode-enabled category:
```js
watch(currentCategoryJudgeNames, (names) => {
  if (names.length > 0 && !names.includes(selectedJudgeTab.value)) {
    selectedJudgeTab.value = names[0]
  }
})
```

- [ ] **Step 4: Add tab bar and per-judge filtered participant list to template**

Find the participant assignment section in the template. Wrap it:

```html
<!-- Judge mode: tab bar -->
<div v-if="currentCategoryJudgeMode" class="flex gap-2 mb-3">
  <button
    v-for="j in currentCategoryJudgeNames"
    :key="j"
    class="badge-sm px-3 py-1.5 type-label transition-colors"
    :class="selectedJudgeTab === j
      ? 'bg-accent/15 border border-accent/40 text-accent'
      : 'bg-white/5 border border-white/10 text-content-muted'"
    @click="selectedJudgeTab = j"
  >{{ j }}</button>
</div>

<!-- Judge mode: show only selected judge's participants with their own available numbers -->
<template v-if="currentCategoryJudgeMode">
  <div v-for="p in currentJudgePool" :key="p.participantId" class="card mb-2 p-3 flex items-center justify-between">
    <span class="type-name">{{ p.participantName }}</span>
    <div class="flex items-center gap-2">
      <span v-if="p.auditionNumber" class="type-label text-accent">#{{ p.auditionNumber }}</span>
      <span v-else class="type-label text-amber-400/70">unassigned</span>
    </div>
  </div>
  <p v-if="currentJudgePool.length === 0" class="type-prose text-content-muted">No participants assigned to this judge yet.</p>
</template>

<!-- Default (mode off): existing participant assignment UI unchanged -->
<template v-else>
  <!-- existing assignment rows — leave untouched -->
</template>
```

Note: The number-assignment input (dropdown/stepper for picking a number) for judge mode should use `judgeAvailableNumbers` instead of `getAvailableNumbers(categoryId)`. Wire up the existing assign logic to pass the judge-scoped number. The backend `assignAuditionNumber` already handles per-judge scoping correctly — no backend change needed.

- [ ] **Step 5: Smoke test**

Open AuditionAdjust for an event with a judge-assignment-mode category. Confirm:
- Tab bar appears with one tab per judge
- Each tab shows only that judge's participants
- Available numbers are 1..N (pool size for that judge)
- Categories with mode OFF show the existing flat UI unchanged

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/AuditionAdjust.vue
git commit -m "feat: add per-judge tab view to AuditionAdjust when judge assignment mode is on"
```

---

### Task 6: EmceeJudgeGridView — concurrent multi-judge grid

**Files:**
- Create: `BES-frontend/src/components/EmceeJudgeGridView.vue`

**Context:** This is the new emcee view for judge assignment mode. It replaces `EmceeRoundView` when mode is active. Layout: a horizontal grid — one column per judge, rows = audition round number (1 to max pool size). The emcee has one "current round" counter that advances globally. At each round, the participant in that judge's pool at that slot is shown; if a judge has no participant at the current round, their cell shows "—". Navigation: PREV / NEXT buttons advance the global round.

**Interfaces:**
- Consumes (props):
  - `judgeSlots: Object` — result of `getParticipantsByJudge`, shape `{ [judgeName]: [{auditionNumber, participantName, memberNames}] }`
  - `currentRound: Number` — controlled externally (by AuditionList)
  - `maxRound: Number` — max pool size across all judges
- Emits:
  - `advance` — emcee pressed NEXT
  - `retreat` — emcee pressed PREV

- [ ] **Step 1: Write the failing test**

Create `BES-frontend/src/components/__tests__/EmceeJudgeGridView.test.js`:

```js
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EmceeJudgeGridView from '../EmceeJudgeGridView.vue'

const judgeSlots = {
  Jay: [
    { auditionNumber: 1, participantName: 'Alice', memberNames: [] },
    { auditionNumber: 2, participantName: 'Dave',  memberNames: [] },
  ],
  Kay: [
    { auditionNumber: 1, participantName: 'Bob',   memberNames: [] },
  ]
}

describe('EmceeJudgeGridView', () => {
  it('renders one column header per judge', () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 1, maxRound: 2 }
    })
    expect(wrapper.text()).toContain('Jay')
    expect(wrapper.text()).toContain('Kay')
  })

  it('shows participant at current round for each judge', () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 1, maxRound: 2 }
    })
    expect(wrapper.text()).toContain('Alice')
    expect(wrapper.text()).toContain('Bob')
  })

  it('shows em-dash for judge with no participant at current round', () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 2, maxRound: 2 }
    })
    expect(wrapper.text()).toContain('Dave')
    expect(wrapper.text()).toContain('—')  // Kay has no round 2
  })

  it('emits advance on NEXT click', async () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 1, maxRound: 2 }
    })
    await wrapper.find('[data-testid="next-round"]').trigger('click')
    expect(wrapper.emitted('advance')).toBeTruthy()
  })

  it('emits retreat on PREV click', async () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 2, maxRound: 2 }
    })
    await wrapper.find('[data-testid="prev-round"]').trigger('click')
    expect(wrapper.emitted('retreat')).toBeTruthy()
  })

  it('PREV is disabled at round 1', async () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 1, maxRound: 2 }
    })
    const prevBtn = wrapper.find('[data-testid="prev-round"]')
    expect(prevBtn.attributes('disabled')).toBeDefined()
  })

  it('NEXT is disabled at maxRound', async () => {
    const wrapper = mount(EmceeJudgeGridView, {
      props: { judgeSlots, currentRound: 2, maxRound: 2 }
    })
    const nextBtn = wrapper.find('[data-testid="next-round"]')
    expect(nextBtn.attributes('disabled')).toBeDefined()
  })
})
```

- [ ] **Step 2: Run to confirm failure**

```bash
cd BES-frontend && npm test -- EmceeJudgeGridView --run 2>&1 | tail -15
```
Expected: FAIL (component doesn't exist)

- [ ] **Step 3: Create the component**

Create `BES-frontend/src/components/EmceeJudgeGridView.vue`:

```vue
<script setup>
import { computed } from 'vue'

const props = defineProps({
  judgeSlots: { type: Object, required: true },
  currentRound: { type: Number, required: true },
  maxRound: { type: Number, required: true }
})

const emit = defineEmits(['advance', 'retreat'])

const judgeNames = computed(() => Object.keys(props.judgeSlots).filter(k => k !== '__unassigned__'))

// For a given judge and round number, find the participant at that audition slot.
// judgeSlots[judge] is sorted by auditionNumber — slot at index (round-1).
const slotAt = (judgeName, round) => {
  const pool = props.judgeSlots[judgeName] ?? []
  return pool.find(p => p.auditionNumber === round) ?? null
}
</script>

<template>
  <div class="flex flex-col h-full select-none" style="background:#111111;">

    <!-- Round counter -->
    <div class="flex items-center justify-between px-4 pt-3 pb-2">
      <span class="type-label text-content-muted">ROUND</span>
      <span class="text-accent font-oswald text-2xl font-medium tracking-wide">
        {{ currentRound }} / {{ maxRound }}
      </span>
    </div>

    <!-- Judge column headers -->
    <div
      class="grid px-3 gap-2 mb-2"
      :style="{ gridTemplateColumns: `repeat(${judgeNames.length}, 1fr)` }"
    >
      <div
        v-for="j in judgeNames"
        :key="j"
        class="card p-2 text-center"
      >
        <span class="section-rule-label">{{ j.toUpperCase() }}</span>
      </div>
    </div>

    <!-- Participant slots for current round -->
    <div
      class="grid px-3 gap-2 flex-1"
      :style="{ gridTemplateColumns: `repeat(${judgeNames.length}, 1fr)` }"
    >
      <div
        v-for="j in judgeNames"
        :key="j"
        class="card p-3 flex flex-col items-center justify-center gap-1 min-h-[120px]"
        :class="slotAt(j, currentRound) ? '' : 'opacity-30'"
      >
        <template v-if="slotAt(j, currentRound)">
          <span class="text-accent font-oswald text-3xl font-medium tabular-nums">
            #{{ slotAt(j, currentRound).auditionNumber }}
          </span>
          <span class="type-name text-center text-sm">
            {{ slotAt(j, currentRound).participantName }}
          </span>
          <div v-if="slotAt(j, currentRound).memberNames?.length" class="flex flex-wrap gap-1 justify-center mt-1">
            <span
              v-for="m in slotAt(j, currentRound).memberNames"
              :key="m"
              class="badge-sm type-label px-1.5 py-0.5 bg-white/5 border border-white/10"
            >{{ m }}</span>
          </div>
        </template>
        <template v-else>
          <span class="text-content-muted font-oswald text-3xl">—</span>
          <span class="type-label text-content-muted text-xs">WAITING</span>
        </template>
      </div>
    </div>

    <!-- Navigation -->
    <div class="flex gap-3 px-4 py-3">
      <button
        data-testid="prev-round"
        class="flex-1 card py-3 type-label transition-opacity"
        :disabled="currentRound <= 1"
        :class="currentRound <= 1 ? 'opacity-30' : ''"
        @click="emit('retreat')"
      >
        ‹ PREV
      </button>
      <button
        data-testid="next-round"
        class="flex-1 card py-3 type-label transition-opacity text-accent"
        :disabled="currentRound >= maxRound"
        :class="currentRound >= maxRound ? 'opacity-30' : ''"
        @click="emit('advance')"
      >
        NEXT ›
      </button>
    </div>

  </div>
</template>
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd BES-frontend && npm test -- EmceeJudgeGridView --run 2>&1 | tail -15
```
Expected: all 7 tests pass

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/components/EmceeJudgeGridView.vue \
        BES-frontend/src/components/__tests__/EmceeJudgeGridView.test.js
git commit -m "feat: add EmceeJudgeGridView concurrent multi-judge grid component"
```

---

### Task 7: AuditionList — switch to grid view when mode is on (emcee role)

**Files:**
- Modify: `BES-frontend/src/views/AuditionList.vue`

**Context:** `AuditionList.vue` uses `EmceeRoundView` for the emcee role. When the selected category has `judgeAssignmentMode=true` and the user is in Emcee mode, replace `EmceeRoundView` with `EmceeJudgeGridView`. The current round state is managed here (`judgeCurrentRound` ref). `getParticipantsByJudge` is called when mode is active and the category changes.

**Interfaces:**
- Consumes: `EmceeJudgeGridView` (Task 6); `getParticipantsByJudge` (Task 1); `selectedCategory` ref; `isEmcee` ref; `selectedCategoryMode` computed (from category DTO)
- Produces: `EmceeJudgeGridView` shown instead of `EmceeRoundView` when `isEmcee && selectedCategoryJudgeMode`; `judgeCurrentRound` ref increments/decrements on advance/retreat events

- [ ] **Step 1: Add import and state**

In `AuditionList.vue` `<script setup>`:

```js
import EmceeJudgeGridView from '@/components/EmceeJudgeGridView.vue'
import { getParticipantsByJudge } from '@/utils/api'

const judgeCurrentRound = ref(1)
const judgeSlots = ref({})       // populated by getParticipantsByJudge
const judgeMaxRound = computed(() =>
  Math.max(0, ...Object.values(judgeSlots.value)
    .filter(arr => Array.isArray(arr))
    .map(arr => arr.length))
)

const selectedCategoryJudgeMode = computed(() => {
  const cat = eventDivisions.value?.find(d => d.name === selectedCategory.value)
  return cat?.judgeAssignmentMode ?? false
})

const loadJudgeSlotsForCategory = async () => {
  if (!selectedCategoryJudgeMode.value || !selectedEvent.value) {
    judgeSlots.value = {}
    return
  }
  const div = eventDivisions.value?.find(d => d.name === selectedCategory.value)
  if (!div) return
  judgeSlots.value = await getParticipantsByJudge(selectedEvent.value, div.eventCategoryId)
  judgeCurrentRound.value = 1
}
```

In the existing `watch(selectedCategory, ...)`, call `loadJudgeSlotsForCategory()` after categories load.

Also watch judge-update WebSocket messages to refresh slots:
```js
// Inside the existing /topic/judge-update/ subscription handler, after updating participants.value, add:
if (selectedCategoryJudgeMode.value) loadJudgeSlotsForCategory()
```

- [ ] **Step 2: Update template — conditional view**

In the emcee section of the template (where `EmceeRoundView` is rendered), wrap it:

```html
<!-- Judge assignment mode: concurrent grid -->
<EmceeJudgeGridView
  v-if="isEmcee && selectedCategoryJudgeMode && Object.keys(judgeSlots).length > 0"
  :judgeSlots="judgeSlots"
  :currentRound="judgeCurrentRound"
  :maxRound="judgeMaxRound"
  @advance="judgeCurrentRound = Math.min(judgeCurrentRound + 1, judgeMaxRound)"
  @retreat="judgeCurrentRound = Math.max(judgeCurrentRound - 1, 1)"
/>

<!-- Standard linear view -->
<EmceeRoundView
  v-else-if="isEmcee"
  ...existing props...
/>
```

- [ ] **Step 3: Smoke test in browser**

Log in as emcee (or organiser in Emcee mode). Navigate to a category with judge assignment mode ON. Confirm the grid view appears with judge columns and round navigation. Switch to a category with mode OFF — confirm the standard linear view is still shown. Advance rounds — confirm all judge cells update.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/AuditionList.vue
git commit -m "feat: switch emcee to concurrent judge grid view when assignment mode is on"
```

---

### Task 8: AuditionDisplay — multi-panel OBS view for judge mode

**Files:**
- Modify: `BES-frontend/src/views/AuditionDisplay.vue`

**Context:** The emcee's `advance`/`retreat` in Task 7 needs to broadcast the current round so the OBS display shows the right participants for each judge. The flow: emcee advances round → frontend posts to `POST /api/v1/event/audition-display` with `judgeMode=true`, `judgeCurrentRound=N`, and `judgeSlots` (current participant per judge at round N) → `EventAuditionDisplayService` saves and broadcasts → `AuditionDisplay.vue` reads `state.judgeMode` and renders a multi-panel grid.

**Interfaces:**
- Consumes: `AuditionDisplayStateDto.judgeMode`, `.judgeCurrentRound`, `.judgeSlots` (Backend Plan Task 6); `updateDisplayState` existing POST endpoint
- Produces: multi-panel grid in OBS display when `state.judgeMode === true`

- [ ] **Step 1: Broadcast judge round state from AuditionList on advance/retreat**

In `AuditionList.vue`, update the advance/retreat handlers to also POST display state:

```js
import { updateAuditionDisplay } from '@/utils/api'  // check if this already exists; if not see Step 2

const broadcastJudgeRound = async (newRound) => {
  if (!selectedEvent.value || !selectedCategory.value) return
  const slots = {}
  for (const [judgeName, pool] of Object.entries(judgeSlots.value)) {
    if (judgeName === '__unassigned__') continue
    const slot = pool.find(p => p.auditionNumber === newRound) ?? null
    slots[judgeName] = slot ? {
      auditionNumber: slot.auditionNumber,
      participantName: slot.participantName,
      memberNames: slot.memberNames ?? []
    } : null
  }
  await updateAuditionDisplay(selectedEvent.value, {
    eventName: selectedEvent.value,
    categoryName: selectedCategory.value,
    judgeMode: true,
    judgeCurrentRound: newRound,
    judgeSlots: slots
  })
}
```

Replace the advance/retreat handlers:
```js
// @advance
judgeCurrentRound.value = Math.min(judgeCurrentRound.value + 1, judgeMaxRound.value)
broadcastJudgeRound(judgeCurrentRound.value)

// @retreat  
judgeCurrentRound.value = Math.max(judgeCurrentRound.value - 1, 1)
broadcastJudgeRound(judgeCurrentRound.value)
```

Check `api.js` for an existing `updateAuditionDisplay` function (search for `/api/v1/event/audition-display`). If it exists, use it. If not, add to `api.js`:

```js
export const updateAuditionDisplay = async (eventName, payload) => {
  try {
    await fetch(`${domain}/api/v1/event/audition-display`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
  } catch { /* silent — display is best-effort */ }
}
```

- [ ] **Step 2: Add multi-panel grid to AuditionDisplay.vue**

In `AuditionDisplay.vue`, `state` is already reactive (updated via WebSocket from `/topic/audition-display/{event}/{category}`). Add the judge mode template section.

Find the main participant display block (where `currentSlots` is rendered). Add a conditional wrapper:

```html
<!-- Judge mode: multi-panel per judge -->
<div
  v-if="state?.judgeMode && state?.judgeSlots"
  class="judge-grid"
  :style="{ gridTemplateColumns: `repeat(${Object.keys(state.judgeSlots).length}, 1fr)` }"
>
  <div
    v-for="(slot, judgeName) in state.judgeSlots"
    :key="judgeName"
    class="judge-panel flex flex-col items-center justify-center gap-3"
  >
    <span class="judge-label">{{ judgeName.toUpperCase() }}</span>
    <template v-if="slot">
      <span
        class="audition-number"
        :style="state.numberColor ? { color: state.numberColor } : {}"
      >#{{ slot.auditionNumber }}</span>
      <span class="participant-name">{{ slot.participantName }}</span>
    </template>
    <template v-else>
      <span class="audition-number" style="opacity:0.2">—</span>
      <span class="participant-name" style="opacity:0.2">WAITING</span>
    </template>
  </div>
</div>

<!-- Default: existing single-participant display (unchanged) -->
<template v-else>
  <!-- existing display markup — leave untouched -->
</template>
```

Add styles (within the component's `<style>` block):

```css
.judge-grid {
  display: grid;
  gap: 2px;
  width: 100%;
  height: 100%;
}

.judge-panel {
  background: rgba(255,255,255,0.03);
  border-right: 1px solid rgba(255,255,255,0.06);
  padding: 2rem 1rem;
}

.judge-label {
  font-family: 'Oswald', sans-serif;
  font-size: 13px;
  letter-spacing: 0.18em;
  color: rgba(255,255,255,0.4);
  margin-bottom: 0.5rem;
}
```

(Reuse `.audition-number` and `.participant-name` classes already defined in the file.)

- [ ] **Step 3: Smoke test in browser (two tabs)**

Open `/audition/display?event=YourEvent&category=YourCategory` in one tab (the OBS view). Open the emcee's AuditionList in another tab with the same event/category in judge assignment mode. Advance rounds — confirm the OBS display updates to show all judges' current participants. Confirm that when a judge has no participant at a round, their panel shows "—".

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/AuditionDisplay.vue \
        BES-frontend/src/views/AuditionList.vue \
        BES-frontend/src/utils/api.js
git commit -m "feat: broadcast judge round state and render multi-panel OBS display for judge mode"
```

---

### Task 9: Final check — full frontend test suite

- [ ] **Step 1: Run all frontend tests**

```bash
cd BES-frontend && npm test --run 2>&1 | tail -20
```
Expected: all tests pass, no regressions

- [ ] **Step 2: Build production bundle**

```bash
cd BES-frontend && npm run build 2>&1 | tail -10
```
Expected: `built in Xs`, no errors

- [ ] **Step 3: End-to-end smoke test checklist**

With both backend and frontend running (`mvn spring-boot:run` + `npm run dev`):

- [ ] EventDetails: toggle judge assignment mode ON for a category with ≥2 judges → button shows green ON state → reload → state persists
- [ ] EventDetails: category with 1 judge → toggle does NOT appear
- [ ] UpdateEventDetails: with mode ON → participants grouped by judge, unassigned shows amber UNASSIGNED chip
- [ ] Walk-in form: select a mode-ON category → judge dropdown appears; select mode-OFF → dropdown hidden
- [ ] AuditionAdjust: mode-ON category → tab bar with judge names; each tab shows per-judge pool
- [ ] Check-in (QR scan): participant with mode-ON and no judge → returns `no_judge_assigned` error message
- [ ] AuditionList (emcee): mode-ON category → grid view with judge columns; PREV/NEXT advances round globally; mode-OFF → normal EmceeRoundView
- [ ] AuditionDisplay (OBS): open in browser; advance emcee round → OBS updates all judge panels simultaneously

- [ ] **Step 4: Final commit**

```bash
git commit -m "chore: frontend judge assignment mode complete — all smoke tests passed" --allow-empty
```
