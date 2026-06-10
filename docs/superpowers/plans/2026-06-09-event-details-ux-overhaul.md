# EventDetails UX Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign EventDetails.vue for clarity — judge pool cards, categories rename, session link auto-generation, sheet suggestion chips, and genre tab structure.

**Architecture:** All changes are in two frontend files only. No backend changes. Logic changes (session auto-gen, matching fix, no-solo default) are frontend-only and explicitly user-requested.

**Tech Stack:** Vue 3 (Composition API), Tailwind CSS utility classes, project design system (parallelogram chips via `para-chip`, section rules via `section-rule`, Anton SC typography via `type-*`)

---

## Files Modified

| File | What changes |
|------|-------------|
| `BES-frontend/src/views/EventDetails.vue` | Tasks 1–8 |
| `BES-frontend/src/components/ScoringCriteriaModal.vue` | Task 9 |

---

## Task 1: Fix matching bug — exact case-insensitive equality

**Files:** Modify `BES-frontend/src/views/EventDetails.vue:564-595`

The current substring match (`cat.includes(n)`) causes false positives: a category named `"1v1"` matches every sheet value containing "1v1". Fix to exact equality. Both sides are already lowercased before comparison.

- [ ] **Open** `BES-frontend/src/views/EventDetails.vue`

- [ ] **Replace** the `matchCounts` computed (lines ~564–579):

```js
const matchCounts = computed(() => {
  const counts = {}
  const cats = sheetCategories.value.map(s => s.toLowerCase())
  for (const div of eventGenres.value) {
    const names = [div.name.toLowerCase()]
    if (div.sheetAliases) {
      names.push(...div.sheetAliases.split(',').map(a => a.trim().toLowerCase()).filter(Boolean))
    }
    let count = 0
    for (const cat of cats) {
      if (names.some(n => cat === n)) count++
    }
    counts[div.eventGenreId] = count
  }
  return counts
})
```

- [ ] **Replace** the `unmatchedSheetValues` computed (lines ~581–595):

```js
const unmatchedSheetValues = computed(() => {
  if (!sheetCategories.value.length) return []
  const matched = new Set()
  for (const div of eventGenres.value) {
    const names = [div.name.toLowerCase()]
    if (div.sheetAliases) {
      names.push(...div.sheetAliases.split(',').map(a => a.trim().toLowerCase()).filter(Boolean))
    }
    for (const cat of sheetCategories.value) {
      if (names.some(n => cat.toLowerCase() === n)) matched.add(cat.toLowerCase())
    }
  }
  const unique = [...new Set(sheetCategories.value.map(s => s.toLowerCase()))]
  return unique.filter(v => !matched.has(v))
})
```

- [ ] **Verify** in browser: connect a sheet, set a category name to exactly match one sheet value. Only that one sheet row should count as matched. A category named `"1v1"` should NOT match `"Hip Hop 1v1"`.

- [ ] **Commit:**
```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "fix: exact case-insensitive category matching (was substring, caused false positives)"
```

---

## Task 2: No-solo default when team format selected

**Files:** Modify `BES-frontend/src/views/EventDetails.vue:609-612`

When a team format (2v2, 3v3, 4v4, 5v5) is selected, immediately set soloAllowed = false. The Solo toggle stays visible for manual override.

- [ ] **Replace** `saveDivisionFormat` (lines ~609–612):

```js
const saveDivisionFormat = async (div, format) => {
  await updateEventGenreFormat(props.eventName, div.eventGenreId, format || null)
  div.format = format || null
  // Team formats default to no solo
  if (format && /^\d+v\d+$/i.test(format) && format.toLowerCase() !== '1v1') {
    if (div.soloAllowed !== false) {
      await updateDivisionSoloAllowed(props.eventName, div.eventGenreId, false)
      div.soloAllowed = false
    }
  }
}
```

- [ ] **Verify** in browser: set a category to `2v2` → solo toggle should immediately switch to "NO SOLO" without needing a page refresh.

- [ ] **Commit:**
```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "feat: team format categories default to no-solo on format select"
```

---

## Task 3: Rename "Divisions" → "Categories" in all UI text

**Files:** Modify `BES-frontend/src/views/EventDetails.vue`

This is UI text only. Do NOT rename JS variables (`divisionsByGenre`, `divRenameActive`, `divFormatOptions`, etc.) — only change the strings that users see.

- [ ] **Find and replace** these exact strings (case-sensitive, in template and JS string literals only):

| Find | Replace |
|------|---------|
| `'Remove Division?'` | `'Remove Category?'` |
| `will be permanently deleted. Participants already enrolled will block this action — remove them first.` | `will be permanently removed. Participants already enrolled will block this action — remove them first.` |
| `Participants registering for "${div.name}" will no longer be able to select Solo (pickup crew).` | `Participants in "${div.name}" will no longer be able to register as Solo (pickup crew).` |
| `Solo entries will be permitted for "${div.name}".` | `Solo entries will be permitted for "${div.name}".` *(no change needed)* |
| `<span class="section-rule-label">Divisions</span>` | `<span class="section-rule-label">Categories</span>` |
| `` + Add {{ group.label }} division`` | `` + Add {{ group.label }} category`` |
| `title="Remove division"` | `title="Remove category"` |
| `Remove all participants from the division before deleting it.` | `Remove all participants from the category before deleting it.` |
| `Remove ${j.judgeName} from ${g.name}?` | `Remove ${j.judgeName} from ${g.name}?` *(no change — judge confirm message is fine)* |

- [ ] **Add help text** immediately after the `<div class="section-rule mb-4">` that wraps the "Categories" label (the outer card for the Divisions section, around line 1398):

```html
<div class="section-rule mb-3">
  <span class="section-rule-label">Categories</span>
  <div class="section-rule-line"></div>
</div>
<p class="type-label text-content-muted mb-4" style="font-size:10px;text-transform:none;letter-spacing:0.03em;">
  Categories are competition formats within each genre (e.g. Popping 1v1, Popping 7 to Smoke).
  Names must match your Google Sheet column values exactly.
</p>
```

- [ ] **Verify** in browser: section heading says "Categories", add button says "+ Add X category", confirm dialog says "Remove Category?", help text visible.

- [ ] **Commit:**
```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "ui: rename Divisions to Categories throughout EventDetails"
```

---

## Task 4: Sheet suggestions strip

**Files:** Modify `BES-frontend/src/views/EventDetails.vue`

Add a panel showing all unique sheet category values above the per-genre groups. Covered = already has a matching category (greyed out). Uncovered = clickable chips that open a genre picker.

- [ ] **Add computed** `allSheetSuggestions` and `suggestionCoveredSet` after `unmatchedSheetValues` (~line 595):

```js
const allSheetSuggestions = computed(() => {
  return [...new Set(sheetCategories.value)]
})

const suggestionCoveredSet = computed(() => {
  const covered = new Set()
  for (const div of eventGenres.value) {
    const names = [div.name.toLowerCase()]
    if (div.sheetAliases) {
      names.push(...div.sheetAliases.split(',').map(a => a.trim().toLowerCase()).filter(Boolean))
    }
    for (const cat of sheetCategories.value) {
      if (names.some(n => cat.toLowerCase() === n)) covered.add(cat)
    }
  }
  return covered
})
```

- [ ] **Add state** for the genre picker popover (after the existing `divAliasExpanded` ref, ~line 57):

```js
const pendingSuggestionCat = ref(null) // sheet value waiting for genre assignment
```

- [ ] **Add handler** `addSuggestionToGenre` after `addDivisionToGroup` (~line 652):

```js
const addSuggestionToGenre = async (genreId, genreLabel) => {
  if (!pendingSuggestionCat.value) return
  const name = pendingSuggestionCat.value
  pendingSuggestionCat.value = null
  // Reuse existing addDivisionToGroup but pass the sheet value as the desired name.
  // addDivisionToGroup auto-deduplicates; since this name comes from the sheet it
  // should not collide, but the guard still runs.
  const existingNames = eventGenres.value.map(d => d.name.toLowerCase())
  let finalName = name
  let i = 2
  while (existingNames.includes(finalName.toLowerCase())) {
    finalName = `${name} ${i++}`
  }
  const resp = await addDivision(props.eventName, finalName, null, genreId === 'custom' ? null : genreId)
  if (resp && resp.ok) {
    eventGenres.value = await getGenresByEvent(props.eventName)
  }
}
```

- [ ] **Add suggestions strip** to the template, inside the Categories card, immediately before `<div class="space-y-4">` (the per-genre groups loop, ~line 1403):

```html
<!-- Sheet suggestions strip — only when sheet is connected -->
<div v-if="allSheetSuggestions.length > 0" class="mb-4 p-3 para-chip">
  <p class="type-label text-content-muted mb-2" style="font-size:10px;">FROM YOUR SHEET — click to add as a category</p>
  <div class="flex flex-wrap gap-2">
    <span
      v-for="cat in allSheetSuggestions"
      :key="cat"
    >
      <!-- Covered: greyed, not clickable -->
      <span
        v-if="suggestionCoveredSet.has(cat)"
        class="type-label text-content-muted line-through opacity-40"
        style="font-size:10px;"
      >{{ cat }}</span>
      <!-- Uncovered: clickable, shows genre picker -->
      <span
        v-else
        class="relative"
      >
        <button
          @click="pendingSuggestionCat = pendingSuggestionCat === cat ? null : cat"
          class="para-chip-sm px-2.5 py-1 type-label text-content-secondary hover:text-accent transition-colors"
          style="font-size:10px;border-style:dashed;"
        >+ {{ cat }}</button>
        <!-- Inline genre picker -->
        <div
          v-if="pendingSuggestionCat === cat"
          class="absolute top-full left-0 mt-1 z-50 bg-surface-800 border border-surface-600 para-chip p-2 min-w-[160px]"
        >
          <p class="type-label text-content-muted mb-1.5" style="font-size:9px;">ADD TO GENRE:</p>
          <button
            v-for="group in divisionsByGenre"
            :key="group.genreId"
            @click="addSuggestionToGenre(group.genreId, group.label)"
            class="block w-full text-left px-2 py-1.5 type-body text-content-secondary hover:text-accent transition-colors"
            style="font-size:11px;"
          >{{ group.label }}</button>
        </div>
      </span>
    </span>
  </div>
</div>
```

- [ ] **Close picker on outside click** — add to the existing `@click.outside` pattern or a simple document click handler. Simplest: add `@click.self` to the backdrop or use `v-click-outside` if already available. The easiest path: add to the existing confirm/modal close patterns. Since the picker auto-closes when the user selects a genre (`pendingSuggestionCat` is cleared), just ensure clicking another chip closes the previous one (already handled since `pendingSuggestionCat` holds only one value).

- [ ] **Verify** in browser: suggestions strip appears when sheet is connected. Covered chips appear greyed out. Clicking an uncovered chip shows the genre picker. Picking a genre creates the category and the chip becomes greyed.

- [ ] **Commit:**
```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "feat: sheet suggestions strip in Categories section with inline genre picker"
```

---

## Task 5: Alias — collapse behind text link

**Files:** Modify `BES-frontend/src/views/EventDetails.vue:1483-1531`

The alias button (`pi-tags` icon) is replaced by a small "add alias" text link shown only when the alias section is expanded or aliases already exist on the row.

- [ ] **Replace** the alias toggle button (currently `<button @click="divAliasExpanded = ..."` with the `pi-tags` icon, ~line 1484):

```html
<!-- Alias toggle — text link, shown after the remove button -->
<button
  v-if="divAliasExpanded !== div.eventGenreId && !(div.sheetAliases && div.sheetAliases.split(',').filter(Boolean).length)"
  @click="divAliasExpanded = div.eventGenreId; divAliasInput = ''"
  class="type-label text-content-muted hover:text-accent transition-colors"
  style="font-size:9px;letter-spacing:0.1em;text-decoration:underline;text-underline-offset:2px;"
  title="Add a sheet alias manually if the category name doesn't match your sheet exactly"
>aliases</button>
```

- [ ] **Verify** alias chips already on a category still render (the existing chips template at ~line 1509 is unchanged — keep it). Existing aliases not removed.

- [ ] **Verify** in browser: no alias button visible by default. Alias chips appear inline if the category already has aliases. "aliases" text link appears to open the add-alias input.

- [ ] **Commit:**
```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "ui: collapse alias button behind text link in category rows"
```

---

## Task 6: Genre tab — three labelled sections

**Files:** Modify `BES-frontend/src/views/EventDetails.vue:1626-1731`

Add section-rule labels (Roster Status / Scoring Criteria / Judges) inside each genre tab. Update badge copy.

- [ ] **Replace** the genre tab content block (inside `<template v-for="g in eventGenres"`, from the `<div v-if="activeGenreTab === g.name"` through its closing tag, ~lines 1626–1731) with:

```html
<template v-for="g in eventGenres" :key="g.name + '-content'">
  <div v-if="activeGenreTab === g.name" class="p-5 space-y-4">

    <!-- ROSTER STATUS -->
    <template v-if="completeBreakdown.find(b => b.genre === normalizeGenreName(g.name))">
      <div>
        <div class="section-rule mb-3">
          <span class="section-rule-label" style="font-size:9px;">Roster Status</span>
          <div class="section-rule-line"></div>
        </div>
        <div class="flex items-center gap-2 flex-wrap">
          <span class="badge-neutral text-xs">{{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).total }} total</span>
          <span class="badge-success">{{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).registered }} registered</span>
          <span
            v-if="completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).unregistered > 0"
            class="badge-danger"
          >{{ completeBreakdown.find(b => b.genre === normalizeGenreName(g.name)).unregistered }} unregistered</span>
        </div>

        <!-- Unregistered list -->
        <div v-if="getUnregistered(normalizeGenreName(g.name)).unregistered.length > 0" class="mt-2">
          <div class="flex flex-wrap gap-2">
            <span
              v-for="p in getUnregistered(normalizeGenreName(g.name)).unregistered"
              :key="p.participantName"
              class="badge-danger font-source"
            >{{ p.participantName }}</span>
          </div>
        </div>
        <div v-else class="flex items-center gap-2 type-body text-emerald-400 mt-2">
          <i class="pi pi-check-circle"></i>
          <span>All participants registered</span>
        </div>
      </div>
      <div class="h-px bg-surface-700/40"></div>
    </template>

    <!-- SCORING CRITERIA -->
    <div>
      <div class="flex items-center gap-2 mb-2">
        <div class="section-rule mb-0 flex-1">
          <span class="section-rule-label" style="font-size:9px;">Scoring Criteria</span>
          <div class="section-rule-line"></div>
        </div>
        <button
          @click="showCriteriaModal = true"
          class="para-chip-sm px-2.5 py-1 type-label shrink-0"
        ><i class="pi pi-sliders-h" style="font-size:0.65rem"></i> Configure</button>
      </div>
      <template v-if="criteriaByGenre[g.name]?.length">
        <div class="flex flex-wrap gap-1.5">
          <span
            v-for="c in criteriaByGenre[g.name]"
            :key="c.id"
            class="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-surface-700 border border-surface-600/50 text-xs text-content-secondary"
          >
            {{ c.name }}
            <span v-if="c.weight != null" class="font-source text-primary-400">×{{ c.weight }}</span>
          </span>
        </div>
      </template>
      <span v-else class="text-xs text-content-muted">Default — single 0–10 score</span>
    </div>

    <div class="h-px bg-surface-700/40"></div>

    <!-- JUDGES (read-only) -->
    <div>
      <div class="section-rule mb-2">
        <span class="section-rule-label" style="font-size:9px;">Judges</span>
        <div class="section-rule-line"></div>
        <span class="type-label text-content-muted" style="font-size:9px;white-space:nowrap;">manage in judge pool above</span>
      </div>
      <div v-if="(divisionJudges[g.name] || []).length > 0" class="flex flex-wrap gap-2">
        <span
          v-for="j in (divisionJudges[g.name] || [])"
          :key="j.judgeId"
          class="flex items-center gap-1.5 para-chip px-2.5 py-1"
        >
          <span class="inline-block w-1.5 h-1.5 rounded-full bg-emerald-400 shrink-0" style="box-shadow:0 0 5px rgba(52,211,153,0.5)"></span>
          <span class="type-body text-content-secondary text-xs">{{ j.judgeName }}</span>
        </span>
      </div>
      <span v-else class="text-xs text-content-muted">None assigned — add in Judge Pool above</span>
    </div>

  </div>
</template>
```

- [ ] **Verify** in browser: three clear labelled sections inside each genre tab. Badge text now says "24 total / 22 registered / 2 unregistered". Judges row is read-only chips.

- [ ] **Commit:**
```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "ui: genre tab three-section layout — Roster Status, Scoring Criteria, Judges (read-only)"
```

---

## Task 7: Judge Pool → mini-profile cards

**Files:** Modify `BES-frontend/src/views/EventDetails.vue:1570-1604`

Replace the flat "Event Judges" chip list with a card-per-judge grid. Each card shows the judge's name, their assigned categories, an assign dropdown, and a remove button.

- [ ] **Add state** for the judge pool card assign dropdown (near the existing `openAssignDropdown` ref, ~line 674):

```js
const openJudgeCardDropdown = ref(null) // judgeId of the card with open assign dropdown
```

- [ ] **Add computed** `categoriesForJudge` that returns all categories assigned to a given judge (uses existing `divisionJudges` data):

```js
const categoriesAssignedToJudge = (judgeId) => {
  const result = []
  for (const [genreName, judges] of Object.entries(divisionJudges.value)) {
    if (judges.some(j => j.judgeId === judgeId)) {
      const genre = eventGenres.value.find(g => g.name === genreName)
      if (genre) result.push(genre)
    }
  }
  return result
}

const categoriesUnassignedToJudge = (judgeId) => {
  const assigned = new Set(categoriesAssignedToJudge(judgeId).map(g => g.eventGenreId))
  return eventGenres.value.filter(g => !assigned.has(g.eventGenreId))
}
```

- [ ] **Replace** the Global Judges block (lines ~1570–1604):

```html
<!-- Judge Pool -->
<div class="mb-5">
  <div class="section-rule mb-1">
    <span class="section-rule-label">Judge Pool</span>
    <div class="section-rule-line"></div>
  </div>
  <p class="type-label text-content-muted mb-4" style="font-size:10px;text-transform:none;letter-spacing:0.03em;">
    Add your judges here first — then assign them to categories below.
  </p>

  <div class="flex flex-wrap gap-3">
    <!-- Per-judge card -->
    <div
      v-for="j in allEventJudges"
      :key="j.judgeId"
      class="para-chip p-3 flex flex-col gap-2 min-w-[160px] flex-1"
      :class="categoriesAssignedToJudge(j.judgeId).length === 0
        ? 'border-l-[3px] border-l-amber-500 bg-amber-950/10'
        : 'border-l-[3px] border-l-emerald-500/40'"
    >
      <!-- Judge name + remove -->
      <div class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-1.5">
          <span
            class="inline-block w-1.5 h-1.5 rounded-full shrink-0"
            :class="categoriesAssignedToJudge(j.judgeId).length === 0
              ? 'bg-amber-400' : 'bg-emerald-400'"
            :style="categoriesAssignedToJudge(j.judgeId).length === 0
              ? 'box-shadow:0 0 5px rgba(245,158,11,0.5)'
              : 'box-shadow:0 0 5px rgba(52,211,153,0.5)'"
          ></span>
          <span class="type-body text-content-secondary text-xs">{{ j.judgeName }}</span>
        </div>
        <button
          @click="askRemoveJudgeGlobal(j)"
          class="type-label text-content-muted hover:text-red-400 transition-colors"
          title="Remove from event"
        ><i class="pi pi-times text-xs"></i></button>
      </div>

      <!-- Assigned categories -->
      <div>
        <p class="type-label text-content-muted mb-1" style="font-size:9px;">CATEGORIES</p>
        <div v-if="categoriesAssignedToJudge(j.judgeId).length > 0" class="flex flex-wrap gap-1 mb-1.5">
          <span
            v-for="cat in categoriesAssignedToJudge(j.judgeId)"
            :key="cat.eventGenreId"
            class="inline-flex items-center gap-1 para-chip-sm px-1.5 py-0.5 type-label text-content-muted"
            style="font-size:9px;"
          >
            {{ cat.name }}
            <button
              @click="submitRemoveJudge(cat.eventGenreId, j.judgeId)"
              class="hover:text-red-400 transition-colors leading-none"
            ><i class="pi pi-times" style="font-size:0.45rem"></i></button>
          </span>
        </div>
        <p v-else class="type-label text-amber-400 mb-1.5" style="font-size:9px;">No categories assigned yet</p>
      </div>

      <!-- Assign dropdown -->
      <div v-if="categoriesUnassignedToJudge(j.judgeId).length > 0" class="relative">
        <button
          @click="openJudgeCardDropdown = openJudgeCardDropdown === j.judgeId ? null : j.judgeId"
          class="para-chip-sm px-2 py-1 type-label text-accent hover:bg-[var(--accent-subtle)] transition-all w-full text-left"
          style="font-size:9px;"
        ><i class="pi pi-plus text-xs mr-1"></i> Assign category</button>
        <div
          v-if="openJudgeCardDropdown === j.judgeId"
          class="absolute top-full left-0 mt-1 bg-surface-800 border border-surface-600 para-chip p-1.5 z-50 min-w-[160px] max-h-48 overflow-y-auto"
        >
          <button
            v-for="cat in categoriesUnassignedToJudge(j.judgeId)"
            :key="cat.eventGenreId"
            @click="submitAssignJudge(cat.eventGenreId, j.judgeId); openJudgeCardDropdown = null"
            class="block w-full text-left px-3 py-1.5 type-body text-content-secondary hover:text-content-primary hover:bg-surface-700 transition-colors"
            style="font-size:11px;"
          >+ {{ cat.name }}</button>
        </div>
      </div>
    </div>

    <!-- Add judge card -->
    <div class="para-chip p-3 flex flex-col items-center justify-center gap-2 min-w-[140px] flex-1" style="border-style:dashed;border-color:rgba(255,255,255,0.1);">
      <div class="flex items-center gap-1.5 w-full">
        <input
          v-model="globalJudgeInput"
          type="text"
          placeholder="Judge name…"
          autocomplete="off"
          class="bg-transparent type-body placeholder:text-content-muted focus:outline-none flex-1 min-w-0 text-xs"
          @keyup.enter="submitAddJudgeGlobal()"
        />
        <button
          @click="submitAddJudgeGlobal()"
          class="type-label text-accent hover:opacity-80 transition-opacity shrink-0"
          title="Add judge"
        ><i class="pi pi-plus text-xs"></i></button>
      </div>
      <span v-if="allEventJudges.length === 0" class="type-label text-content-muted" style="font-size:9px;">No judges yet</span>
    </div>
  </div>
</div>
```

- [ ] **Verify** in browser: judges appear as cards. Unassigned judge has amber left border + amber dot. Assign dropdown lists only unassigned categories. Removing a category chip via × updates the card immediately.

- [ ] **Commit:**
```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "ui: judge pool mini-profile cards with inline category assignment"
```

---

## Task 8: Session Links — auto-generated, refresh-to-extend

**Files:** Modify `BES-frontend/src/views/EventDetails.vue`

Remove manual generate buttons and expand toggle. Auto-generate EMCEE/HELPER on load, auto-generate JUDGE on judge add, auto-revoke on judge remove. Add Refresh action and expiry colour coding.

- [ ] **Remove** `sessionLinksExpanded` ref (line ~735) and `selectedJudgeId` ref (line ~738) and `generatingRole` ref (line ~739) — these become unused.

- [ ] **Add** copy feedback ref near the existing `auditionLinkCopied` ref (~line 813):

```js
const copiedTokenId = ref(null)
```

- [ ] **Replace** `loadSessionTokens` (~lines 768–773) with an auto-generating version:

```js
const loadSessionTokens = async () => {
  if (!dbEventId.value) return
  sessionTokensLoading.value = true
  sessionTokens.value = await getSessionTokens(dbEventId.value)
  // Auto-generate permanent roles if missing
  const hasEmcee = sessionTokens.value.some(t => t.role === 'EMCEE')
  const hasHelper = sessionTokens.value.some(t => t.role === 'HELPER')
  if (!hasEmcee) await generateToken('EMCEE', dbEventId.value, null)
  if (!hasHelper) await generateToken('HELPER', dbEventId.value, null)
  if (!hasEmcee || !hasHelper) {
    sessionTokens.value = await getSessionTokens(dbEventId.value)
  }
  sessionTokensLoading.value = false
}
```

- [ ] **Update** `submitAddJudgeGlobal` (~lines 686–693) to auto-generate a judge token:

```js
const submitAddJudgeGlobal = async () => {
  if (!globalJudgeInput.value.trim()) return
  const res = await addJudgeToEvent(props.eventName, globalJudgeInput.value.trim())
  if (res?.ok) {
    const judges = await res.json()
    allEventJudges.value = judges
    // Auto-generate session token for the new judge
    const newJudge = judges[judges.length - 1]
    if (newJudge && dbEventId.value) {
      await generateToken('JUDGE', dbEventId.value, newJudge.judgeId)
      await loadSessionTokens()
    }
  }
  globalJudgeInput.value = ''
}
```

- [ ] **Update** `submitRemoveJudgeGlobal` (~lines 695–705) to revoke the judge's token first:

```js
const submitRemoveJudgeGlobal = async (judgeId) => {
  // Revoke session token for this judge before removing them
  const judgeToken = sessionTokens.value.find(t => t.role === 'JUDGE' && t.judgeId === judgeId)
  if (judgeToken) await revokeSessionToken(judgeToken.tokenId)

  const res = await removeEventJudge(props.eventName, judgeId)
  if (res?.ok) {
    allEventJudges.value = await res.json()
    for (const genre of eventGenres.value) {
      if (divisionJudges.value[genre.name]) {
        divisionJudges.value[genre.name] = await getJudgesByDivision(props.eventName, genre.eventGenreId)
      }
    }
    await loadSessionTokens()
  }
}
```

> **Note:** `judgeToken.judgeId` assumes the token DTO includes `judgeId`. Check `getSessionTokens` response shape. If the field is named differently (e.g. `judgeRefId`), use that name. If it's not present, match by `judgeName` against `allEventJudges`.

- [ ] **Add** `handleRefreshToken` after `handleRevokeToken` (~line 792):

```js
const handleRefreshToken = async (token) => {
  await revokeSessionToken(token.tokenId)
  await generateToken(token.role, dbEventId.value, token.judgeId ?? null)
  await loadSessionTokens()
}
```

- [ ] **Update** `copyTokenLink` to accept tokenId for feedback (~line 809):

```js
const copyTokenLink = (url, tokenId) => {
  copyToClipboard(window.location.origin + url)
  copiedTokenId.value = tokenId
  setTimeout(() => { copiedTokenId.value = null }, 2000)
}
```

- [ ] **Add** expiry helper after `formatExpiry` (~line 820):

```js
const isExpiryWarning = (expiresAt) => {
  return (new Date(expiresAt) - Date.now()) < 3 * 24 * 60 * 60 * 1000
}
```

- [ ] **Remove** the `watch(sessionLinksExpanded, ...)` block (~lines 825–827) — no longer needed since section is always visible.

- [ ] **Replace** the entire Session Links template block (currently ~lines 1735–1822) with:

```html
<!-- Session Links — always visible, auto-generated -->
<div v-if="isAdminOrOrganiser && tableExist" class="card-hover p-4 relative mt-6">
  <div class="corner-bar-tl"></div>

  <div class="section-rule mb-4">
    <span class="section-rule-label">Session Links</span>
    <div class="section-rule-line"></div>
    <span class="type-label text-content-muted" style="font-size:9px;white-space:nowrap;">auto-generated · refresh to extend</span>
  </div>

  <div v-if="sessionTokensLoading" class="flex items-center gap-2 type-label text-content-muted py-4">
    <i class="pi pi-spinner pi-spin text-xs"></i> Loading…
  </div>
  <div v-else class="space-y-2">
    <div
      v-for="t in sessionTokens"
      :key="t.tokenId"
      class="para-chip px-3 py-2 flex items-center gap-3"
    >
      <span
        class="badge-neutral text-xs shrink-0"
        :class="t.role === 'JUDGE' ? 'badge-accent' : 'badge-neutral'"
      >{{ t.role }}</span>
      <span v-if="t.judgeName" class="type-body text-content-secondary text-xs truncate">{{ t.judgeName }}</span>
      <span class="flex-1"></span>
      <span
        class="type-label text-xs shrink-0"
        :class="isExpiryWarning(t.expiresAt) ? 'text-amber-400' : 'text-content-muted'"
      >
        {{ formatExpiry(t.expiresAt) }}
        <span v-if="isExpiryWarning(t.expiresAt)"> ⚠</span>
      </span>
      <button
        @click="handleRefreshToken(t)"
        class="para-chip-sm px-2 py-1 type-label text-content-muted hover:text-accent transition-colors shrink-0"
        title="Refresh link (extends expiry)"
      ><i class="pi pi-refresh text-xs"></i></button>
      <button
        @click="copyTokenLink(t.url, t.tokenId)"
        class="para-chip-sm px-2 py-1 type-label transition-colors shrink-0"
        :class="copiedTokenId === t.tokenId ? 'text-emerald-400' : 'text-content-muted hover:text-accent'"
        title="Copy link"
      ><i class="pi text-xs" :class="copiedTokenId === t.tokenId ? 'pi-check' : 'pi-copy'"></i></button>
    </div>
    <p class="type-label text-content-muted mt-3" style="font-size:9px;">Judge links are removed automatically when a judge is deleted.</p>
  </div>
</div>
```

- [ ] **Verify** in browser:
  - On page load, EMCEE and HELPER tokens appear automatically (generated if missing)
  - Add a judge → their token appears in the list
  - Refresh button → link refreshes, copy button reflects updated URL
  - Delete a judge → their token disappears
  - Expiry < 3 days → amber colour + ⚠

- [ ] **Commit:**
```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "feat: session links auto-generated, refresh-to-extend, judge tokens tied to judge lifecycle"
```

---

## Task 9: Scoring Criteria Modal — "Copy to all genres" rename

**Files:** Modify `BES-frontend/src/components/ScoringCriteriaModal.vue`

- [ ] **Add** `appliedAll` ref after `applyingAll` (~line 113):

```js
const appliedAll = ref(false)
```

- [ ] **Update** `applyToAllGenres` to set `appliedAll` on success (~line 115):

```js
const applyToAllGenres = async () => {
  if (!activeCriteria.value.length) return
  applyingAll.value = true
  const source = [...activeCriteria.value]
  const targets = allTabs.value.filter(t => t !== activeTab.value)

  await Promise.all(targets.map(async (tab) => {
    const genre = genreNameForTab(tab)
    await deleteAllCriteriaForGenre(props.eventName, genre)
    const added = []
    for (let i = 0; i < source.length; i++) {
      const r = await addScoringCriteria(props.eventName, {
        genreName:    genre,
        name:         source[i].name,
        weight:       source[i].weight,
        displayOrder: i,
      })
      if (r) added.push(r)
    }
    criteriaMap.value[tab] = added
  }))

  applyingAll.value = false
  appliedAll.value = true
  setTimeout(() => { appliedAll.value = false }, 1500)
}
```

- [ ] **Replace** the "Apply to all genres" button (~lines 335–346) with:

```html
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
```

- [ ] **Verify** in browser: button says "Copy to all genres". After clicking, shows "Copied!" in green for 1.5s, then reverts.

- [ ] **Commit:**
```bash
git add BES-frontend/src/components/ScoringCriteriaModal.vue
git commit -m "ui: rename Apply to all genres → Copy to all genres with Copied! confirmation"
```

---

## Task 10: Assign dropdown direction fix

**Files:** Modify `BES-frontend/src/views/EventDetails.vue:1718`

The assign dropdown in the genre tab (now read-only, but the old dropdown code can be removed since Task 6 and Task 7 replaced the interactive judge assignment with the pool cards). Verify this dropdown no longer exists in the template after Task 7. If it was removed by Task 7, this task is a no-op. If it still exists anywhere, change `bottom-full` → `top-full` and `mb-1` → `mt-1`.

- [ ] **Search** for `bottom-full` in `EventDetails.vue`:
```bash
grep -n "bottom-full" BES-frontend/src/views/EventDetails.vue
```
If any results remain, replace each:
```
class="absolute bottom-full left-0 mb-1 ...
```
→
```
class="absolute top-full left-0 mt-1 ...
```

- [ ] **Commit** only if a change was needed:
```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "ui: fix judge assign dropdown direction — opens downward not upward"
```

---

## Self-Review Checklist

| Spec requirement | Task |
|-----------------|------|
| Matching bug fix (exact equality) | Task 1 |
| No-solo default on team format | Task 2 |
| Divisions → Categories rename + help text | Task 3 |
| Sheet suggestions strip + genre picker | Task 4 |
| Alias collapsed behind text link | Task 5 |
| Genre tab section rules (Roster Status / Scoring Criteria / Judges) | Task 6 |
| Badge copy: Total → N total, Reg → N registered | Task 6 |
| Judge Pool mini-profile cards | Task 7 |
| Per-genre Judges read-only | Task 6 |
| Session links auto-generate EMCEE/HELPER on load | Task 8 |
| Session links auto-generate JUDGE on judge add | Task 8 |
| Session links auto-revoke JUDGE on judge remove | Task 8 |
| Refresh = revoke + regenerate | Task 8 |
| Expiry colour coding (amber < 3 days) | Task 8 |
| Copy feedback (✓ for 2s) | Task 8 |
| "Copy to all genres" rename + Copied! | Task 9 |
| Assign dropdown direction fix | Task 10 |
