# Genre & Division UX Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the genre/division system so event init is a simple genre picker, division names/aliases are managed post-init with live sheet match feedback, and the walk-in form shows grouped divisions.

**Architecture:** Eight tasks: four backend (service methods, controller endpoints, sheet categories endpoint, DTO), four frontend (api.js, admin page cleanup, init panel simplification, new Divisions section + walk-in grouping). No DB migrations needed — all columns exist from V23.

**Tech Stack:** Spring Boot (Maven), Vue 3 (Vite), PostgreSQL, Google Sheets API (existing `sheetClient`)

---

## File Map

| File | Change |
|---|---|
| `BES/src/main/java/com/example/BES/services/EventGenreService.java` | Add `renameDivision`, `updateAliases`, `deleteDivision` |
| `BES/src/main/java/com/example/BES/dtos/AddDivisionDto.java` | **Create** — DTO for adding a single division |
| `BES/src/main/java/com/example/BES/controllers/EventController.java` | Add 4 division management endpoints |
| `BES/src/main/java/com/example/BES/services/GoogleSheetService.java` | Add `getAllCategoryValues` |
| `BES/src/main/java/com/example/BES/controllers/GoogleSheetsController.java` | Add `GET /categories/{fileId}` endpoint |
| `BES/src/test/java/com/example/BES/services/EventGenreServiceTest.java` | Add 6 new tests |
| `BES/src/test/java/com/example/BES/controllers/EventControllerIntegrationTest.java` | Add 4 new integration tests |
| `BES-frontend/src/utils/api.js` | Add 5 new functions |
| `BES-frontend/src/views/AdminPage.vue` | Remove alias UI from genres panel |
| `BES-frontend/src/views/EventDetails.vue` | Simplify init panel; add Divisions section |
| `BES-frontend/src/components/CreateParticipantForm.vue` | Group divisions by genre |

---

## Task 1: EventGenreService — rename, aliases, delete

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventGenreService.java`
- Test: `BES/src/test/java/com/example/BES/services/EventGenreServiceTest.java`

- [ ] **Step 1: Write failing tests**

Open `EventGenreServiceTest.java` and add after the existing `updateFormat_savesFormat` test:

```java
@Test
void renameDivision_savesNewName() {
    EventGenre eg = new EventGenre();
    when(eventGenreRepo.findById(1L)).thenReturn(Optional.of(eg));
    service.renameDivision(1L, "Open Breaking");
    assertThat(eg.getName()).isEqualTo("Open Breaking");
    verify(eventGenreRepo).save(eg);
}

@Test
void renameDivision_throwsWhenNotFound() {
    when(eventGenreRepo.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.renameDivision(99L, "X"))
        .isInstanceOf(RuntimeException.class);
}

@Test
void updateAliases_savesAliases() {
    EventGenre eg = new EventGenre();
    when(eventGenreRepo.findById(1L)).thenReturn(Optional.of(eg));
    service.updateAliases(1L, "b-boy, bboy");
    assertThat(eg.getSheetAliases()).isEqualTo("b-boy, bboy");
    verify(eventGenreRepo).save(eg);
}

@Test
void updateAliases_clearsOnBlank() {
    EventGenre eg = new EventGenre();
    eg.setSheetAliases("old");
    when(eventGenreRepo.findById(1L)).thenReturn(Optional.of(eg));
    service.updateAliases(1L, "  ");
    assertThat(eg.getSheetAliases()).isNull();
    verify(eventGenreRepo).save(eg);
}

@Test
void deleteDivision_throwsWhenNotFound() {
    when(eventGenreRepo.findById(99L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.deleteDivision(99L))
        .isInstanceOf(RuntimeException.class);
}

@Test
void deleteDivision_deletesById() {
    when(eventGenreRepo.findById(1L)).thenReturn(Optional.of(new EventGenre()));
    service.deleteDivision(1L);
    verify(eventGenreRepo).deleteById(1L);
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
cd BES && mvn test -Dtest=EventGenreServiceTest -q
```

Expected: compilation failure (methods don't exist yet).

- [ ] **Step 3: Implement the three methods in EventGenreService.java**

Add after the existing `updateEventGenreFormat` method:

```java
public void renameDivision(Long id, String name) {
    EventGenre eg = eventGenreRepo.findById(id)
        .orElseThrow(() -> new RuntimeException("Division not found: " + id));
    if (name == null || name.isBlank()) throw new IllegalArgumentException("Name cannot be blank");
    eg.setName(name.trim());
    eventGenreRepo.save(eg);
}

public void updateAliases(Long id, String aliases) {
    EventGenre eg = eventGenreRepo.findById(id)
        .orElseThrow(() -> new RuntimeException("Division not found: " + id));
    eg.setSheetAliases(aliases == null || aliases.isBlank() ? null : aliases.trim());
    eventGenreRepo.save(eg);
}

public void deleteDivision(Long id) {
    eventGenreRepo.findById(id)
        .orElseThrow(() -> new RuntimeException("Division not found: " + id));
    eventGenreRepo.deleteById(id);
}
```

- [ ] **Step 4: Run tests — expect all pass**

```bash
cd BES && mvn test -Dtest=EventGenreServiceTest -q
```

Expected: BUILD SUCCESS, 11 tests passing.

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventGenreService.java \
        BES/src/test/java/com/example/BES/services/EventGenreServiceTest.java
git commit -m "feat: add renameDivision, updateAliases, deleteDivision to EventGenreService"
```

---

## Task 2: EventController — division management endpoints

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/AddDivisionDto.java`
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`
- Test: `BES/src/test/java/com/example/BES/controllers/EventControllerIntegrationTest.java`

- [ ] **Step 1: Create AddDivisionDto.java**

```java
package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddDivisionDto {
    @NotBlank @Size(max = 255)
    public String name;
    public String format;
    public Long genreId;
}
```

- [ ] **Step 2: Write failing integration tests**

In `EventControllerIntegrationTest.java`, add these four tests after `testAssignGenreToEvent`:

```java
@Test
@WithMockUser(roles = "ADMIN")
public void testAddDivision() throws Exception {
    String json = objectMapper.writeValueAsString(Map.of("name", "Junior Breaking", "format", "2v2"));
    mockMvc.perform(post("/api/v1/event/TestEvent/divisions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$").value("Division added"));
}

@Test
@WithMockUser(roles = "ADMIN")
public void testRenameDivision() throws Exception {
    String json = objectMapper.writeValueAsString(Map.of("name", "Open Breaking"));
    mockMvc.perform(patch("/api/v1/event/TestEvent/divisions/1/name")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value("Division renamed"));
}

@Test
@WithMockUser(roles = "ADMIN")
public void testUpdateDivisionAliases() throws Exception {
    String json = objectMapper.writeValueAsString(Map.of("aliases", "breaking seven"));
    mockMvc.perform(patch("/api/v1/event/TestEvent/divisions/1/aliases")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value("Aliases updated"));
}

@Test
@WithMockUser(roles = "ADMIN")
public void testDeleteDivision() throws Exception {
    mockMvc.perform(delete("/api/v1/event/TestEvent/divisions/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").value("Division deleted"));
}
```

Also add `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;` and `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;` to the imports block.

- [ ] **Step 3: Run tests to confirm they fail**

```bash
cd BES && mvn test -Dtest=EventControllerIntegrationTest -q
```

Expected: compilation failure (import missing, endpoints don't exist).

- [ ] **Step 4: Add PatchMapping import and four endpoints to EventController.java**

Add to the imports block (after the existing `DeleteMapping` import):
```java
import org.springframework.web.bind.annotation.PatchMapping;
```

Also add:
```java
import com.example.BES.dtos.AddDivisionDto;
```

Add these four methods anywhere after the existing `updateEventGenreFormat` method:

```java
@PostMapping("/{eventName}/divisions")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<String> addDivision(
        @PathVariable String eventName,
        @Valid @RequestBody AddDivisionDto dto) {
    AddGenreToEventDto addDto = new AddGenreToEventDto();
    addDto.eventName = eventName;
    AddGenreToEventDto.Division div = new AddGenreToEventDto.Division();
    div.name = dto.name;
    div.format = dto.format;
    div.genreId = dto.genreId;
    addDto.divisions = java.util.List.of(div);
    eventGenreService.addGenreToEventService(addDto);
    return ResponseEntity.status(HttpStatus.CREATED).body(gson.toJson("Division added"));
}

@PatchMapping("/{eventName}/divisions/{id}/name")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<String> renameDivision(
        @PathVariable String eventName,
        @PathVariable Long id,
        @RequestBody java.util.Map<String, String> body) {
    eventGenreService.renameDivision(id, body.get("name"));
    return ResponseEntity.ok(gson.toJson("Division renamed"));
}

@PatchMapping("/{eventName}/divisions/{id}/aliases")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<String> updateDivisionAliases(
        @PathVariable String eventName,
        @PathVariable Long id,
        @RequestBody java.util.Map<String, String> body) {
    eventGenreService.updateAliases(id, body.get("aliases"));
    return ResponseEntity.ok(gson.toJson("Aliases updated"));
}

@DeleteMapping("/{eventName}/divisions/{id}")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<String> deleteDivision(
        @PathVariable String eventName,
        @PathVariable Long id) {
    eventGenreService.deleteDivision(id);
    return ResponseEntity.ok(gson.toJson("Division deleted"));
}
```

- [ ] **Step 5: Run all tests**

```bash
cd BES && mvn test -q
```

Expected: BUILD SUCCESS, all tests passing.

- [ ] **Step 6: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/AddDivisionDto.java \
        BES/src/main/java/com/example/BES/controllers/EventController.java \
        BES/src/test/java/com/example/BES/controllers/EventControllerIntegrationTest.java
git commit -m "feat: add division CRUD endpoints to EventController"
```

---

## Task 3: Sheet categories endpoint

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/GoogleSheetService.java`
- Modify: `BES/src/main/java/com/example/BES/controllers/GoogleSheetsController.java`

No new unit tests — `GoogleSheetService` depends on `sheetClient` which requires a live API. The integration test for this endpoint is a manual curl after docker rebuild.

- [ ] **Step 1: Add `getAllCategoryValues` to GoogleSheetService.java**

Add this method after `getSheetSizeService`:

```java
public List<String> getAllCategoryValues(String fileId) throws IOException {
    List<Integer> categoryColumns = getCategoriesColumns(fileId);
    if (categoryColumns.isEmpty()) return new ArrayList<>();

    List<String> ranges = new ArrayList<>();
    for (Integer index : categoryColumns) {
        String col = colIndexToLetter(index + 1);
        ranges.add(col + ":" + col);
    }

    BatchGetValuesResponse response = sheetClient.batchGet(fileId, ranges);
    List<String> result = new ArrayList<>();
    for (ValueRange vr : response.getValueRanges()) {
        List<List<Object>> vals = vr.getValues();
        if (vals == null) continue;
        for (int i = 1; i < vals.size(); i++) {
            List<Object> cell = vals.get(i);
            if (cell != null && !cell.isEmpty()) {
                String val = cell.get(0).toString().trim();
                if (!val.isBlank()) result.add(val);
            }
        }
    }
    return result;
}
```

Also add `import java.util.ArrayList;` if not already present (check imports at the top of the file).

- [ ] **Step 2: Add endpoint to GoogleSheetsController.java**

Add after the existing `getSheetSize` method:

```java
@GetMapping("/categories/{fileId}")
@PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
public ResponseEntity<Map<String, Object>> getCategoryValues(@PathVariable String fileId) {
    try {
        List<String> values = service.getAllCategoryValues(fileId);
        return ResponseEntity.ok(Map.of("values", values));
    } catch (Exception e) {
        return ResponseEntity.ok(Map.of("values", java.util.List.of()));
    }
}
```

Add `import java.util.List;` to the imports if not already present.

- [ ] **Step 3: Compile check**

```bash
cd BES && mvn clean compile -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Run all tests**

```bash
cd BES && mvn test -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/GoogleSheetService.java \
        BES/src/main/java/com/example/BES/controllers/GoogleSheetsController.java
git commit -m "feat: add sheet categories endpoint GET /api/v1/sheets/categories/{fileId}"
```

---

## Task 4: Frontend — api.js new functions

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Add five functions at the end of api.js**

```js
export const addDivision = async (eventName, name, format, genreId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, format: format || null, genreId: genreId || null })
    })
  } catch (e) { console.log(e); return null }
}

export const renameDivision = async (eventName, divisionId, name) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/name`, {
      method: 'PATCH',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ name })
    })
  } catch (e) { console.log(e); return null }
}

export const updateDivisionAliases = async (eventName, divisionId, aliases) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/aliases`, {
      method: 'PATCH',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ aliases })
    })
  } catch (e) { console.log(e); return null }
}

export const deleteDivision = async (eventName, divisionId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}`, {
      method: 'DELETE',
      credentials: 'include',
      headers: { 'Accept': 'application/json' }
    })
  } catch (e) { console.log(e); return null }
}

export const getSheetCategories = async (fileId) => {
  try {
    const res = await fetch(`${domain}/api/v1/sheets/categories/${encodeURIComponent(fileId)}`, {
      credentials: 'include',
      headers: { 'Accept': 'application/json' }
    })
    if (!res.ok) return []
    const data = await res.json()
    return data.values ?? []
  } catch (e) { console.log(e); return [] }
}
```

- [ ] **Step 2: Verify no lint errors**

```bash
cd BES-frontend && npm run build 2>&1 | grep -E "error|Error" | head -20
```

Expected: no errors (warnings about chunk size are OK).

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: add division management and sheet categories API functions"
```

---

## Task 5: AdminPage — remove aliases UI from genres panel

**Files:**
- Modify: `BES-frontend/src/views/AdminPage.vue`

- [ ] **Step 1: Remove alias-related script state and functions**

In the `<script setup>` block, remove these lines:

```js
// Remove these:
const expandedAliasId = ref(null)
const aliasInputs = reactive({})

const toggleAliasEdit = (id, aliases) => { ... }  // entire function
const saveAliases = async (id) => { ... }           // entire function
```

- [ ] **Step 2: Remove alias UI from the genres panel template**

Find the genre card in the template (around the `v-for="g in genres"` loop). Remove:

1. The alias edit button:
```html
<button
  @click="toggleAliasEdit(g.id, g.aliases)"
  class="w-6 h-6 flex items-center justify-center text-content-muted hover:text-accent transition-all"
  title="Edit sheet aliases"
>
  <i class="pi pi-tags text-xs"></i>
</button>
```

2. The alias chips display block:
```html
<div v-if="expandedAliasId !== g.id && g.aliases && g.aliases.length" class="flex flex-wrap gap-1">
  <span v-for="a in g.aliases" :key="a" ...>{{ a }}</span>
</div>
```

3. The alias editor block:
```html
<div v-if="expandedAliasId === g.id" class="flex flex-col gap-1.5 ...">
  <label ...>Sheet aliases (comma-separated)</label>
  <input v-model="aliasInputs[g.id]" ... />
  <div class="flex gap-1.5">
    <button @click="saveAliases(g.id)" ...>Save</button>
    <button @click="expandedAliasId = null" ...>Cancel</button>
  </div>
</div>
```

- [ ] **Step 3: Build to verify no errors**

```bash
cd BES-frontend && npm run build 2>&1 | grep -E "error|Error" | head -20
```

Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/AdminPage.vue
git commit -m "feat: remove alias UI from admin genres panel — aliases now managed per event division"
```

---

## Task 6: EventDetails — simplify event init panel

**Files:**
- Modify: `BES-frontend/src/views/EventDetails.vue`

The "Event Setup Required" panel currently has: genre checkboxes → division config section → payment toggle → initialise. Remove the division config step; keep only genre checkboxes and payment toggle.

- [ ] **Step 1: Update the import line in the script**

In the `import` statement at the top of `<script setup>`, add `addDivision` and remove nothing (we'll clean up the unused division functions):

```js
import { checkTableExist, getFileId, getResponseDetails, fetchAllGenres, getGenresByEvent,
  getVerifiedParticipantsByEvent, addJudges, insertEventInTable, linkGenreToEvent,
  addParticipantToSystem, getSheetSize, getRegisteredParticipantsByEvent, removeParticipantGenre,
  addGenreToParticipant, getUnverifiedParticipantsDB, verifyPayment, verifyPaymentBatch,
  updateEventGenreFormat, getEventJudges, addEventJudge, removeEventJudge, getScoringCriteria,
  fetchAllFolderEvents, fetchAllEvents, getCheckinList, checkInParticipant,
  addDivision, renameDivision, updateDivisionAliases, deleteDivision, getSheetCategories
} from '@/utils/api'
```

- [ ] **Step 2: Replace the createTable state and related functions**

Find and **replace** the `createTable` block and all division-related functions with this simplified version:

Remove these entirely:
```js
const createTable = reactive({ divisions: [] })
let _divUid = 0
const isGenreChecked = (g) => ...
const toggleGenre = (g, checked) => ...
const addAnotherDivision = (g) => ...
const removeDivision = (uid) => ...
const addCustomDivision = () => ...
const selectedGenreOptions = computed(() => ...)
const formatOptions = [...]
```

Replace with:
```js
const selectedInitGenres = ref([])  // array of genre objects { id, genreName }
```

- [ ] **Step 3: Update `onSubmit` to use selectedInitGenres**

Find the existing `onSubmit` function and replace it:

```js
const onSubmit = async () => {
  if (selectedInitGenres.value.length === 0) {
    openModal('Missing Genres', 'Please select at least one genre.', 'warning')
    return
  }
  loading.value = true
  try {
    const divisions = selectedInitGenres.value.map(g => ({
      name: g.genreName,
      format: null,
      genreId: g.id
    }))
    const resp = await linkGenreToEvent(props.eventName, divisions)
    if (!resp || !resp.ok) {
      openModal('Error', 'Failed to initialise event.', 'error')
      return
    }
    tableExist.value = true
    eventGenres.value = await getGenresByEvent(props.eventName)
  } finally {
    loading.value = false
  }
}
```

- [ ] **Step 4: Replace the Event Setup Required template section**

Find the genre checkboxes + divisions block in the template (starts with `<div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2 mb-4">`). Replace the entire genre + divisions markup with:

```html
<div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-2 mb-4">
  <div
    v-for="g in genreOptions"
    :key="g.genreName"
    class="para-chip-sm p-3 transition-all duration-150"
    :class="selectedInitGenres.some(s => s.id === g.id) ? 'border-accent' : ''"
  >
    <label class="flex items-center gap-2.5 cursor-pointer">
      <input
        type="checkbox"
        :checked="selectedInitGenres.some(s => s.id === g.id)"
        @change="e => {
          if (e.target.checked) selectedInitGenres.push(g)
          else selectedInitGenres.splice(selectedInitGenres.findIndex(s => s.id === g.id), 1)
        }"
        class="w-4 h-4"
      />
      <span class="type-body capitalize">{{ g.genreName }}</span>
    </label>
  </div>
</div>
```

Remove the entire divisions config section that follows (from `<div v-if="createTable.divisions.length > 0" class="mb-4">` through its closing `</div>`), including the custom division button.

- [ ] **Step 5: Build check**

```bash
cd BES-frontend && npm run build 2>&1 | grep -E "error|Error" | head -20
```

Expected: no errors.

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "feat: simplify event init to genre picker only — division config moved to post-init"
```

---

## Task 7: EventDetails — Divisions section (post-init)

**Files:**
- Modify: `BES-frontend/src/views/EventDetails.vue`

Add a new "Divisions" card above the existing "Genre Configuration" accordion. This section shows all event divisions grouped by genre, with inline editing of name/format/aliases and live sheet match counts.

- [ ] **Step 1: Add reactive state for the Divisions section**

In the `<script setup>` block, after `const eventGenres = ref([])`, add:

```js
// Divisions section state
const sheetCategories = ref([])        // raw category strings from the linked sheet
const divAliasExpanded = ref(null)     // eventGenreId of the division whose alias row is open
const divAliasInput = ref('')          // text being typed into alias add input
const divRenameActive = ref(null)      // eventGenreId of division being renamed inline
const divRenameInput = ref('')

const divisionsByGenre = computed(() => {
  const groups = new Map()
  for (const div of eventGenres.value) {
    const key = div.genreId ?? '__custom__'
    if (!groups.has(key)) {
      const label = key === '__custom__'
        ? 'Custom'
        : genreOptions.value?.find(g => g.id === div.genreId)?.genreName ?? 'Genre'
      groups.set(key, { label, genreId: div.genreId, divisions: [] })
    }
    groups.get(key).divisions.push(div)
  }
  return [...groups.values()]
})

const matchCounts = computed(() => {
  const counts = {}
  for (const div of eventGenres.value) {
    const matchStrings = [div.name.toLowerCase()]
    if (div.sheetAliases) {
      div.sheetAliases.split(',').map(a => a.trim().toLowerCase()).filter(Boolean).forEach(a => matchStrings.push(a))
    }
    counts[div.eventGenreId] = sheetCategories.value.filter(val =>
      matchStrings.some(m => val.toLowerCase().includes(m))
    ).length
  }
  return counts
})

const unmatchedSheetValues = computed(() => {
  const matched = new Set()
  for (const val of sheetCategories.value) {
    for (const div of eventGenres.value) {
      const matchStrings = [div.name.toLowerCase()]
      if (div.sheetAliases) {
        div.sheetAliases.split(',').map(a => a.trim().toLowerCase()).filter(Boolean).forEach(a => matchStrings.push(a))
      }
      if (matchStrings.some(m => val.toLowerCase().includes(m))) {
        matched.add(val)
        break
      }
    }
  }
  // Deduplicate unmatched
  return [...new Set(sheetCategories.value.filter(v => !matched.has(v)))]
})

const loadSheetCategories = async () => {
  if (!fileId.value) return
  sheetCategories.value = await getSheetCategories(fileId.value)
}

const saveDivisionName = async (div) => {
  const name = divRenameInput.value.trim()
  if (!name) return
  await renameDivision(props.eventName, div.eventGenreId, name)
  div.name = name
  divRenameActive.value = null
}

const saveDivisionFormat = async (div, format) => {
  await updateEventGenreFormat(props.eventName, div.eventGenreId, format)
  div.format = format || null
}

const addAlias = async (div) => {
  const alias = divAliasInput.value.trim()
  if (!alias) return
  const current = div.sheetAliases ? div.sheetAliases.split(',').map(a => a.trim()).filter(Boolean) : []
  if (current.includes(alias)) return
  current.push(alias)
  const joined = current.join(', ')
  await updateDivisionAliases(props.eventName, div.eventGenreId, joined)
  div.sheetAliases = joined
  divAliasInput.value = ''
}

const removeAlias = async (div, alias) => {
  const current = div.sheetAliases.split(',').map(a => a.trim()).filter(a => a !== alias)
  const joined = current.join(', ')
  await updateDivisionAliases(props.eventName, div.eventGenreId, joined || null)
  div.sheetAliases = joined || null
}

const addDivisionToGroup = async (genreId, genreLabel) => {
  const resp = await addDivision(props.eventName, genreLabel, null, genreId === '__custom__' ? null : genreId)
  if (resp && resp.ok) {
    eventGenres.value = await getGenresByEvent(props.eventName)
  }
}

const removeDivisionFromSection = async (divId) => {
  await deleteDivision(props.eventName, divId)
  eventGenres.value = eventGenres.value.filter(d => d.eventGenreId !== divId)
}

const divFormatOptions = ['', '1v1', '2v2', '3v3', '4v4', '5v5', '7 to smoke', 'solo']
```

- [ ] **Step 2: Call loadSheetCategories when sheet is available**

Find the existing `watch([fileId, ...])` or the `onMounted` block where `fileId.value` is set. After `fileId.value = await getFileId(resolvedFolderID)`, add:

```js
loadSheetCategories()
```

- [ ] **Step 3: Add the Divisions section template**

Insert this block directly **before** the `<!-- Genre Configuration -->` comment (the post-init accordion, around line 887):

```html
<!-- Divisions Management -->
<div v-if="tableExist && eventGenres.length > 0" class="card-hover p-4 relative mt-6">
  <div class="corner-bar-tl"></div>

  <div class="section-rule mb-4">
    <span class="section-rule-label">Divisions</span>
    <div class="section-rule-line"></div>
    <button
      v-if="fileId"
      @click="loadSheetCategories"
      class="para-chip-sm px-3 py-1 type-label text-content-muted hover:text-content-primary transition-all ml-2"
      title="Refresh sheet match counts"
    >
      <i class="pi pi-refresh text-xs"></i>
    </button>
  </div>

  <div v-for="group in divisionsByGenre" :key="group.genreId ?? '__custom__'" class="mb-6">
    <!-- Genre group header -->
    <div class="flex items-center gap-2 mb-3">
      <div class="w-1.5 h-1.5 rounded-full bg-accent opacity-60"></div>
      <span class="type-label text-content-muted tracking-widest uppercase">{{ group.label }}</span>
    </div>

    <!-- Division rows -->
    <div
      v-for="div in group.divisions"
      :key="div.eventGenreId"
      class="ml-4 mb-2 para-chip p-0 overflow-hidden transition-all"
      :class="sheetCategories.length > 0 && matchCounts[div.eventGenreId] > 0 ? 'border-emerald-500/25' : ''"
    >
      <!-- Main row: name · format · match count · remove -->
      <div class="flex items-center gap-2 px-3 py-2">
        <!-- Name: display or inline edit -->
        <span
          v-if="divRenameActive !== div.eventGenreId"
          class="type-body flex-1 min-w-0 truncate cursor-pointer hover:text-accent transition-colors"
          @click="divRenameActive = div.eventGenreId; divRenameInput = div.name"
        >{{ div.name }}</span>
        <input
          v-else
          v-model="divRenameInput"
          class="input-base flex-1 min-w-0 py-1 text-xs"
          @keyup.enter="saveDivisionName(div)"
          @blur="saveDivisionName(div)"
          @keyup.escape="divRenameActive = null"
          autofocus
        />

        <!-- Format dropdown -->
        <select
          :value="div.format || ''"
          @change="saveDivisionFormat(div, $event.target.value)"
          class="shrink-0 type-label text-content-muted"
          style="background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.1);color:rgba(255,255,255,0.6);padding:3px 6px;font-family:'Anton SC',sans-serif;letter-spacing:0.05em;text-transform:uppercase;clip-path:polygon(3px 0%,100% 0%,calc(100% - 3px) 100%,0% 100%);font-size:9px;width:80px"
        >
          <option v-for="opt in divFormatOptions" :key="opt" :value="opt">
            {{ opt || 'No format' }}
          </option>
        </select>

        <!-- Match count badge (only if sheet loaded) -->
        <div v-if="sheetCategories.length > 0" class="shrink-0 flex items-center gap-1.5">
          <div
            class="w-1.5 h-1.5 rounded-full"
            :class="matchCounts[div.eventGenreId] > 0 ? 'bg-emerald-400 shadow-[0_0_4px_rgba(52,211,153,0.8)]' : 'bg-white/20'"
          ></div>
          <span
            class="type-label text-xs"
            :class="matchCounts[div.eventGenreId] > 0 ? 'text-emerald-400' : 'text-content-muted'"
          >{{ matchCounts[div.eventGenreId] }}</span>
        </div>

        <!-- Alias toggle -->
        <button
          @click="divAliasExpanded = divAliasExpanded === div.eventGenreId ? null : div.eventGenreId; divAliasInput = ''"
          class="shrink-0 type-label text-content-muted hover:text-accent transition-colors px-1"
          title="Aliases"
        ><i class="pi pi-tags text-xs"></i></button>

        <!-- Remove -->
        <button
          @click="removeDivisionFromSection(div.eventGenreId)"
          class="shrink-0 type-label text-content-muted hover:text-red-400 transition-colors px-1"
        >✕</button>
      </div>

      <!-- Aliases sub-row -->
      <div
        v-if="divAliasExpanded === div.eventGenreId || (div.sheetAliases && div.sheetAliases.trim())"
        class="px-3 pb-2 border-t border-white/5 pt-2 flex flex-wrap items-center gap-1.5"
      >
        <span class="type-label text-content-muted text-xs mr-1">Aliases</span>
        <span
          v-for="alias in (div.sheetAliases ? div.sheetAliases.split(',').map(a => a.trim()).filter(Boolean) : [])"
          :key="alias"
          class="font-mono text-xs text-content-muted bg-white/4 border border-white/10 px-1.5 py-0.5 para-chip cursor-pointer hover:text-red-400 transition-colors"
          @click="removeAlias(div, alias)"
          title="Click to remove"
        >{{ alias }}</span>
        <template v-if="divAliasExpanded === div.eventGenreId">
          <input
            v-model="divAliasInput"
            class="input-base py-0.5 text-xs normal-case"
            style="width: 140px; font-family: monospace; letter-spacing: 0;"
            placeholder="add alias…"
            @keyup.enter="addAlias(div)"
          />
          <button @click="addAlias(div)" class="type-label text-content-muted hover:text-accent px-1 transition-colors">+</button>
        </template>
      </div>
    </div>

    <!-- Add division under this genre -->
    <button
      @click="addDivisionToGroup(group.genreId, group.label)"
      class="ml-4 mt-1 para-chip-sm px-3 py-1 type-label text-content-muted hover:text-content-primary hover:border-accent transition-all"
    >+ add division</button>
  </div>

  <!-- Unmatched sheet values strip -->
  <div
    v-if="unmatchedSheetValues.length > 0"
    class="mt-4 p-3 border border-amber-400/25 bg-amber-400/5"
    style="clip-path:polygon(6px 0%,100% 0%,calc(100% - 6px) 100%,0% 100%)"
  >
    <div class="flex items-center gap-2 mb-3">
      <div class="w-1.5 h-1.5 rounded-full bg-amber-400 shadow-[0_0_5px_rgba(251,191,36,0.8)]"></div>
      <span class="type-label text-amber-400">{{ unmatchedSheetValues.length }} sheet value{{ unmatchedSheetValues.length > 1 ? 's' : '' }} unmatched — will be skipped on import</span>
    </div>
    <div
      v-for="val in unmatchedSheetValues"
      :key="val"
      class="flex items-center gap-3 py-1.5 border-b border-amber-400/10 last:border-0"
    >
      <span class="font-mono text-xs text-white/50 flex-1 normal-case">"{{ val }}"</span>
      <button
        @click="divAliasExpanded = eventGenres[0]?.eventGenreId; divAliasInput = val.toLowerCase().replace(/\d+v\d+/g, '').trim()"
        class="type-label text-amber-400 border border-amber-400/30 px-2 py-0.5 hover:bg-amber-400/10 transition-all"
        style="clip-path:polygon(3px 0%,100% 0%,calc(100% - 3px) 100%,0% 100%);font-size:8px"
      >Add alias</button>
    </div>
  </div>
</div>
```

- [ ] **Step 4: Build check**

```bash
cd BES-frontend && npm run build 2>&1 | grep -E "error|Error" | head -20
```

Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/views/EventDetails.vue
git commit -m "feat: add Divisions management section with live sheet match counts and alias editing"
```

---

## Task 8: CreateParticipantForm — group divisions by genre

**Files:**
- Modify: `BES-frontend/src/components/CreateParticipantForm.vue`

The walk-in form shows divisions as flat checkboxes. Group them by their parent genre (`genreId`) so organisers can see "Breaking → Junior Breaking, Open Breaking" instead of a flat undifferentiated list.

- [ ] **Step 1: Update genreOptions to include genreId**

Find the `watch` that sets `genreOptions`:

```js
watch(() => props.eventGenres, (newGenres) => {
  if (newGenres && newGenres.length > 0) {
    genreOptions.value = newGenres.map(g => ({ genreName: g.name, format: g.format || null }))
  }
}, { immediate: true })
```

Replace with:

```js
watch(() => props.eventGenres, (newGenres) => {
  if (newGenres && newGenres.length > 0) {
    genreOptions.value = newGenres.map(g => ({
      genreName: g.name,
      format: g.format || null,
      genreId: g.genreId ?? null
    }))
  }
}, { immediate: true })
```

Also remove the `onMounted` fallback that calls `fetchAllGenres()` — walk-ins should always use event divisions, not the global genre list:

Find and remove:
```js
onMounted(async () => {
  if (!props.eventGenres || props.eventGenres.length === 0) {
    const genres = await fetchAllGenres()
    genreOptions.value = genres.map(g => ({ genreName: g.genreName, format: g.format || null }))
  }
  ...
})
```

Keep only the judge fetch inside `onMounted`:
```js
onMounted(async () => {
  const res = await getAllJudges()
  allJudges.value = ["", ...Object.values(res).map(item => item.judgeName)]
})
```

- [ ] **Step 2: Add a computed to group divisions**

After `const genreOptions = ref([])`, add:

```js
const groupedDivisions = computed(() => {
  const groups = new Map()
  for (const g of genreOptions.value) {
    const key = g.genreId ?? '__custom__'
    if (!groups.has(key)) groups.set(key, { label: g.genreId ? g.genreName : 'Custom', divisions: [] })
    // label will be overwritten by division name — we need genre label from eventGenres
    groups.get(key).divisions.push(g)
  }
  // Use the genreName from the parent genre if available
  // Since we don't have genre labels here, derive from first division's genreId lookup
  return [...groups.values()]
})
```

Actually, `genreOptions` has `genreName` = division name (e.g. "Junior Breaking"), not the parent genre name. We need the parent genre label. Pass it through from EventDetails.

In `EventDetails.vue`, the `eventGenres` data from `getGenresByEvent` includes `genreId` but not the parent genre name. We can compute it from `genreOptions` (the admin genres). Add to the `:eventGenres` prop in EventDetails.vue the resolved genre label.

Simpler approach: in `CreateParticipantForm.vue`, since we don't have the genre label, group by `genreId` and use a fallback label. Pass `allAdminGenres` as an extra prop — but that's scope creep.

Simplest working approach: group by genreId, use the first division's name pattern to derive a group label by stripping the level (too fragile), OR just show the genreId as the group key with no label (shows as "Group 1", "Group 2") — ugly.

Best approach: add a `genreLabel` field to the division options by fetching from `fetchAllGenres` map in the `watch`.

In `CreateParticipantForm.vue`, add:

```js
import { addWalkinToSystem, getAllJudges, fetchAllGenres } from '@/utils/api'

const adminGenreMap = ref({})  // id → genreName

onMounted(async () => {
  const res = await getAllJudges()
  allJudges.value = ["", ...Object.values(res).map(item => item.judgeName)]
  const genres = await fetchAllGenres()
  if (genres) adminGenreMap.value = Object.fromEntries(genres.map(g => [g.id, g.genreName]))
})
```

Update the `watch`:

```js
watch(() => props.eventGenres, (newGenres) => {
  if (newGenres && newGenres.length > 0) {
    genreOptions.value = newGenres.map(g => ({
      genreName: g.name,
      format: g.format || null,
      genreId: g.genreId ?? null,
      groupLabel: g.genreId ? (adminGenreMap.value[g.genreId] ?? 'Other') : 'Custom'
    }))
  }
}, { immediate: true })
```

Add the grouped computed:

```js
const groupedDivisions = computed(() => {
  const groups = new Map()
  for (const g of genreOptions.value) {
    const key = g.genreId ?? '__custom__'
    if (!groups.has(key)) groups.set(key, { label: g.groupLabel ?? 'Custom', divisions: [] })
    groups.get(key).divisions.push(g)
  }
  return [...groups.values()]
})
```

- [ ] **Step 3: Replace the flat genre checkboxes in the template with grouped view**

Find the genre checkboxes section:

```html
<div class="grid grid-cols-2 gap-2">
  <label
    v-for="g in genreOptions"
    :key="g.genreName"
    ...
  >
    <input type="checkbox" :value="g.genreName" v-model="createTable.genres" ... />
    <span>{{ g.genreName }}</span>
    <span v-if="g.format">{{ g.format }}</span>
  </label>
</div>
```

Replace with:

```html
<div class="space-y-3">
  <div v-for="group in groupedDivisions" :key="group.label">
    <div class="type-label text-content-muted text-xs uppercase tracking-widest mb-1.5">{{ group.label }}</div>
    <div class="grid grid-cols-2 gap-2 pl-2">
      <label
        v-for="g in group.divisions"
        :key="g.genreName"
        class="flex items-center gap-2.5 px-3 py-2.5 para-chip cursor-pointer transition-all"
        :class="createTable.genres.includes(g.genreName)
          ? 'border-accent text-accent'
          : 'text-content-secondary hover:border-white/25'"
      >
        <input
          type="checkbox"
          :value="g.genreName"
          v-model="createTable.genres"
          class="w-4 h-4"
        />
        <span class="text-sm">{{ g.genreName }}</span>
        <span v-if="g.format" class="ml-auto text-xs opacity-50 font-mono">{{ g.format }}</span>
      </label>
    </div>
  </div>
</div>
```

- [ ] **Step 4: Build check**

```bash
cd BES-frontend && npm run build 2>&1 | grep -E "error|Error" | head -20
```

Expected: no errors.

- [ ] **Step 5: Run all backend tests to confirm nothing broken**

```bash
cd BES && mvn test -q
```

Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/components/CreateParticipantForm.vue
git commit -m "feat: group walk-in divisions by parent genre in CreateParticipantForm"
```

---

## Final verification

- [ ] **Rebuild backend and frontend Docker containers**

```bash
docker compose stop backend frontend
docker compose rm -f backend frontend
docker compose build --no-cache backend frontend
docker compose up -d backend frontend
```

- [ ] **Check all containers running**

```bash
docker compose ps
curl -s http://localhost:5050/actuator/health
curl -s -o /dev/null -w "%{http_code}" http://localhost:80
```

Expected: backend `{"status":"UP"}`, frontend HTTP 200.

- [ ] **Smoke test in browser**

1. Go to `/admin` → Genres tab → confirm no alias UI visible
2. Go to an event with no init → confirm genre picker only (no division rows at init)
3. Init an event → confirm one division created per selected genre
4. After init → confirm "Divisions" card appears above "Genre Configuration"
5. In Divisions card: rename a division, add alias, change format, verify match count updates after clicking refresh
6. Open walk-in form → confirm divisions grouped by genre

