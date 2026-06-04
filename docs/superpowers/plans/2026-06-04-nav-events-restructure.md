# Nav + Events Restructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the navbar event dropdown with a slide-over panel that is role-filtered, mobile-friendly, and removes "Events" from the primary nav entirely.

**Architecture:** Extract a new `EventPanel.vue` component that handles all event navigation (section tiles + Manage Events zone). `App.vue` owns panel open/close state and wires the component's emits to existing navigation functions. No backend changes needed.

**Tech Stack:** Vue 3 (Composition API), Vue Router, Pinia (auth store), Vitest + Vue Test Utils, Tailwind CSS

---

## File Map

| File | Action | Responsibility |
|------|--------|---------------|
| `BES-frontend/src/components/EventPanel.vue` | **Create** | Panel UI: role-filtered section tiles, Manage Events zone (Admin/Organiser), Change Event link (Emcee/Judge) |
| `BES-frontend/src/utils/__tests__/EventPanel.test.js` | **Create** | Component tests: tile visibility per role, zone visibility, emits |
| `BES-frontend/src/App.vue` | **Modify** | Wire EventPanel, replace dropdown markup, remove Events nav item, slim mobile hamburger, add "Select Event →" chip, add Escape key handler |

---

## Task 1: Create EventPanel.vue — section tiles with role filtering

**Files:**
- Create: `BES-frontend/src/components/EventPanel.vue`
- Create: `BES-frontend/src/utils/__tests__/EventPanel.test.js`

- [ ] **Step 1: Write failing tests for tile visibility**

Create `BES-frontend/src/utils/__tests__/EventPanel.test.js`:

```js
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import EventPanel from '@/components/EventPanel.vue'

vi.mock('@/utils/api', () => ({
  fetchAllEvents: vi.fn().mockResolvedValue([
    { id: 1, name: 'Dance Battle 2025' },
    { id: 2, name: 'Street Jam Vol.3' },
  ]),
}))

vi.mock('@/utils/auth', () => ({
  setActiveEvent: vi.fn(),
}))

const activeEvent = { id: 1, name: 'Dance Battle 2025', folderID: null }

function mountPanel(role) {
  return mount(EventPanel, {
    props: { role, activeEvent },
  })
}

describe('EventPanel.vue — section tiles', () => {
  describe('Admin role', () => {
    it('shows all 6 tiles', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      const tiles = w.findAll('[data-tile]')
      expect(tiles).toHaveLength(6)
    })

    it('shows Details, Audition, Participants, Score, Battle, Numbers tiles', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      const labels = w.findAll('[data-tile]').map(t => t.text())
      expect(labels).toEqual(
        expect.arrayContaining(['DETAILS', 'AUDITION', 'PARTICIPANTS', 'SCORE', 'BATTLE', 'NUMBERS'])
      )
    })
  })

  describe('Organiser role', () => {
    it('shows all 6 tiles', async () => {
      const w = mountPanel('ROLE_ORGANISER')
      await w.vm.$nextTick()
      expect(w.findAll('[data-tile]')).toHaveLength(6)
    })
  })

  describe('Emcee role', () => {
    it('shows only Audition and Score tiles', async () => {
      const w = mountPanel('ROLE_EMCEE')
      await w.vm.$nextTick()
      const labels = w.findAll('[data-tile]').map(t => t.text())
      expect(labels).toEqual(expect.arrayContaining(['AUDITION', 'SCORE']))
      expect(labels).not.toContain('DETAILS')
      expect(labels).not.toContain('BATTLE')
      expect(labels).not.toContain('PARTICIPANTS')
      expect(labels).not.toContain('NUMBERS')
    })
  })

  describe('Judge role', () => {
    it('shows only Audition and Battle tiles', async () => {
      const w = mountPanel('ROLE_JUDGE')
      await w.vm.$nextTick()
      const labels = w.findAll('[data-tile]').map(t => t.text())
      expect(labels).toEqual(expect.arrayContaining(['AUDITION', 'BATTLE']))
      expect(labels).not.toContain('SCORE')
      expect(labels).not.toContain('DETAILS')
    })
  })

  describe('tile interactions', () => {
    it('emits navigate with correct route when Audition tile clicked', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      const tile = w.findAll('[data-tile]').find(t => t.text().includes('AUDITION'))
      await tile.trigger('click')
      expect(w.emitted('navigate')).toEqual([['Audition List']])
      expect(w.emitted('close')).toBeTruthy()
    })

    it('emits navigate with Score route when Score tile clicked', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      const tile = w.findAll('[data-tile]').find(t => t.text().includes('SCORE'))
      await tile.trigger('click')
      expect(w.emitted('navigate')).toEqual([['Score']])
    })

    it('emits goToEventDetails (not navigate) when Details tile clicked', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      const tile = w.findAll('[data-tile]').find(t => t.text().includes('DETAILS'))
      await tile.trigger('click')
      expect(w.emitted('goToEventDetails')).toBeTruthy()
      expect(w.emitted('navigate')).toBeFalsy()
      expect(w.emitted('close')).toBeTruthy()
    })

    it('emits close when header close button clicked', async () => {
      const w = mountPanel('ROLE_ADMIN')
      await w.vm.$nextTick()
      await w.find('[data-close]').trigger('click')
      expect(w.emitted('close')).toBeTruthy()
    })
  })
})
```

- [ ] **Step 2: Run tests — confirm they fail**

```bash
cd BES-frontend && npm test -- --run EventPanel
```

Expected: FAIL — `Cannot find module '@/components/EventPanel.vue'`

- [ ] **Step 3: Create EventPanel.vue with section tiles**

Create `BES-frontend/src/components/EventPanel.vue`:

```vue
<script setup>
import { ref, computed, onMounted } from 'vue'
import { fetchAllEvents } from '@/utils/api'
import { setActiveEvent } from '@/utils/auth'

const props = defineProps({
  role:        { type: String, required: true },
  activeEvent: { type: Object, default: null },
})

const emit = defineEmits([
  'close',
  'navigate',
  'goToEventDetails',
  'goToAllEvents',
  'changeEvent',
])

const ALL_TILES = [
  { key: 'details',      icon: 'pi-cog',      label: 'Details',      roles: ['ROLE_ADMIN', 'ROLE_ORGANISER'] },
  { key: 'audition',     icon: 'pi-list',      label: 'Audition',     roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE', 'ROLE_JUDGE'] },
  { key: 'participants', icon: 'pi-users',     label: 'Participants', roles: ['ROLE_ADMIN', 'ROLE_ORGANISER'] },
  { key: 'score',        icon: 'pi-chart-bar', label: 'Score',        roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE'] },
  { key: 'battle',       icon: 'pi-bolt',      label: 'Battle',       roles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_JUDGE'] },
  { key: 'numbers',      icon: 'pi-hashtag',   label: 'Numbers',      roles: ['ROLE_ADMIN', 'ROLE_ORGANISER'] },
]

const TILE_ROUTES = {
  audition:     'Audition List',
  participants: 'Update Event Details',
  score:        'Score',
  battle:       'Battle Control',
  numbers:      'Audition Adjust',
}

const isAdminOrOrganiser = computed(() =>
  props.role === 'ROLE_ADMIN' || props.role === 'ROLE_ORGANISER'
)

const visibleTiles = computed(() =>
  ALL_TILES.filter(t => t.roles.includes(props.role))
)

function handleTile(tile) {
  if (tile.key === 'details') {
    emit('goToEventDetails')
  } else {
    emit('navigate', TILE_ROUTES[tile.key])
  }
  emit('close')
}

// ── Manage Events zone ────────────────────────────────────────────────────────
const allEvents    = ref([])
const search       = ref('')

const filteredEvents = computed(() =>
  allEvents.value.filter(e =>
    e.name.toLowerCase().includes(search.value.toLowerCase())
  )
)

onMounted(async () => {
  if (!isAdminOrOrganiser.value) return
  allEvents.value = await fetchAllEvents() ?? []
})

function handleSwitchEvent(event) {
  setActiveEvent(event.id, event.name)
  // panel stays open — user picks a section next
}
</script>

<template>
  <div class="flex flex-col h-full">

    <!-- Header -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-[rgba(255,255,255,0.07)] flex-shrink-0">
      <div class="corner-bar-tl"></div>
      <span class="type-body truncate pr-4">
        {{ activeEvent ? activeEvent.name : 'No Event Selected' }}
      </span>
      <button data-close @click="emit('close')"
        class="flex-shrink-0 text-content-muted hover:text-accent transition-colors">
        <i class="pi pi-times text-sm"></i>
      </button>
    </div>

    <!-- Section tiles -->
    <div class="p-3 grid grid-cols-2 gap-2 flex-shrink-0">
      <button
        v-for="tile in visibleTiles"
        :key="tile.key"
        data-tile
        @click="handleTile(tile)"
        class="card-hover flex flex-col items-center gap-2 py-4 transition-all duration-150 hover:bg-[rgba(255,255,255,0.06)]"
        style="min-height:56px"
      >
        <i class="pi text-lg text-accent" :class="tile.icon"></i>
        <span class="type-label">{{ tile.label }}</span>
      </button>
    </div>

    <!-- Manage Events zone (Admin + Organiser) -->
    <template v-if="isAdminOrOrganiser">
      <div class="flex-1 overflow-y-auto border-t border-[rgba(255,255,255,0.07)] p-3">
        <div class="section-rule mb-3">
          <span class="section-rule-label">Manage Events</span>
          <div class="section-rule-line"></div>
        </div>
        <input
          v-model="search"
          type="text"
          placeholder="Search events..."
          class="input-base w-full mb-2"
        />
        <div class="space-y-0.5 mb-2">
          <button
            v-for="event in filteredEvents"
            :key="event.id"
            @click="handleSwitchEvent(event)"
            class="w-full flex items-center gap-2 px-2 py-2 type-label text-left transition-colors hover:bg-[rgba(255,255,255,0.04)]"
            :class="event.id === activeEvent?.id ? 'text-accent' : 'text-content-secondary'"
          >
            <span class="w-1.5 h-1.5 rounded-full flex-shrink-0 transition-colors"
              :class="event.id === activeEvent?.id ? 'bg-accent' : 'bg-transparent border border-surface-600'">
            </span>
            <span class="truncate">{{ event.name }}</span>
          </button>
        </div>
        <button
          @click="emit('goToAllEvents'); emit('close')"
          class="w-full text-left type-label text-content-muted hover:text-content-primary transition-colors px-2 py-2"
        >
          All Events →
        </button>
      </div>
    </template>

    <!-- Change Event (Emcee + Judge) -->
    <template v-else>
      <div class="mt-auto border-t border-[rgba(255,255,255,0.07)] px-4 py-3">
        <button
          @click="emit('changeEvent'); emit('close')"
          class="w-full type-label text-content-muted hover:text-content-primary transition-colors text-center py-1"
        >
          ↻ Change Event
        </button>
      </div>
    </template>

  </div>
</template>
```

- [ ] **Step 4: Run tests — confirm they pass**

```bash
cd BES-frontend && npm test -- --run EventPanel
```

Expected: all tests PASS

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/components/EventPanel.vue BES-frontend/src/utils/__tests__/EventPanel.test.js
git commit -m "feat: add EventPanel component with role-filtered section tiles"
```

---

## Task 2: EventPanel — Manage Events zone + Change Event tests

**Files:**
- Modify: `BES-frontend/src/utils/__tests__/EventPanel.test.js`

- [ ] **Step 1: Add failing tests for zone visibility and event switching**

Append to `BES-frontend/src/utils/__tests__/EventPanel.test.js`:

```js
describe('EventPanel.vue — zones', () => {
  it('Admin sees Manage Events zone', async () => {
    const w = mountPanel('ROLE_ADMIN')
    await w.vm.$nextTick()
    expect(w.text()).toContain('MANAGE EVENTS')
    expect(w.text()).not.toContain('Change Event')
  })

  it('Organiser sees Manage Events zone', async () => {
    const w = mountPanel('ROLE_ORGANISER')
    await w.vm.$nextTick()
    expect(w.text()).toContain('MANAGE EVENTS')
  })

  it('Emcee sees Change Event link, not Manage Events zone', async () => {
    const w = mountPanel('ROLE_EMCEE')
    await w.vm.$nextTick()
    expect(w.text()).toContain('Change Event')
    expect(w.text()).not.toContain('MANAGE EVENTS')
  })

  it('Judge sees Change Event link, not Manage Events zone', async () => {
    const w = mountPanel('ROLE_JUDGE')
    await w.vm.$nextTick()
    expect(w.text()).toContain('Change Event')
    expect(w.text()).not.toContain('MANAGE EVENTS')
  })

  it('Admin panel lists fetched events', async () => {
    const w = mountPanel('ROLE_ADMIN')
    await new Promise(r => setTimeout(r, 0)) // flush onMounted async
    await w.vm.$nextTick()
    expect(w.text()).toContain('Dance Battle 2025')
    expect(w.text()).toContain('Street Jam Vol.3')
  })

  it('active event is marked with accent class', async () => {
    const w = mountPanel('ROLE_ADMIN')
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()
    const activeRow = w.findAll('button').find(b => b.text().includes('Dance Battle 2025'))
    expect(activeRow?.classes()).toContain('text-accent')
  })

  it('emits changeEvent when Change Event clicked (Emcee)', async () => {
    const w = mountPanel('ROLE_EMCEE')
    await w.vm.$nextTick()
    const btn = w.findAll('button').find(b => b.text().includes('Change Event'))
    await btn.trigger('click')
    expect(w.emitted('changeEvent')).toBeTruthy()
    expect(w.emitted('close')).toBeTruthy()
  })

  it('emits goToAllEvents and close when All Events clicked', async () => {
    const w = mountPanel('ROLE_ADMIN')
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()
    const btn = w.findAll('button').find(b => b.text().includes('All Events'))
    await btn.trigger('click')
    expect(w.emitted('goToAllEvents')).toBeTruthy()
    expect(w.emitted('close')).toBeTruthy()
  })

  it('calls setActiveEvent when an event in the list is clicked', async () => {
    const { setActiveEvent } = await import('@/utils/auth')
    const w = mountPanel('ROLE_ADMIN')
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()
    const row = w.findAll('button').find(b => b.text().includes('Street Jam Vol.3'))
    await row.trigger('click')
    expect(setActiveEvent).toHaveBeenCalledWith(2, 'Street Jam Vol.3')
  })

  it('panel stays open after switching event (no close emit)', async () => {
    const w = mountPanel('ROLE_ADMIN')
    await new Promise(r => setTimeout(r, 0))
    await w.vm.$nextTick()
    const row = w.findAll('button').find(b => b.text().includes('Street Jam Vol.3'))
    await row.trigger('click')
    expect(w.emitted('close')).toBeFalsy()
  })
})
```

- [ ] **Step 2: Run tests — confirm new ones fail**

```bash
cd BES-frontend && npm test -- --run EventPanel
```

Expected: the new zone tests FAIL (zones not found / wrong text)

- [ ] **Step 3: Verify existing implementation already covers these cases**

The EventPanel.vue created in Task 1 already includes the Manage Events zone and Change Event link. Run:

```bash
cd BES-frontend && npm test -- --run EventPanel
```

Expected: ALL tests PASS (implementation was complete in Task 1)

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/utils/__tests__/EventPanel.test.js
git commit -m "test: add zone visibility and event-switching tests for EventPanel"
```

---

## Task 3: App.vue — wire EventPanel, replace dropdown, add Escape handler

**Files:**
- Modify: `BES-frontend/src/App.vue`

- [ ] **Step 1: Add imports and replace dropdown state**

In the `<script setup>` of `BES-frontend/src/App.vue`, add the EventPanel import after the existing imports:

```js
import EventPanel from './components/EventPanel.vue'
```

Rename the `eventMenuOpen` ref to `panelOpen` (search and replace all 4 occurrences):

```js
// was: const eventMenuOpen = ref(false)
const panelOpen = ref(false)
```

Update the `watch(route, ...)` block (it references `eventMenuOpen`):

```js
watch(route, () => {
  isOpen.value = false
  panelOpen.value = false
})
```

Update `changeEvent()`:

```js
function changeEvent() {
  panelOpen.value = false
  router.push({ name: 'EventSelector', query: { redirect: router.currentRoute.value.fullPath } })
}
```

Update `goToSection()`:

```js
function goToSection(routeName) {
  panelOpen.value = false
  router.push({ name: routeName })
}
```

Update `goToEventDetails()`:

```js
function goToEventDetails() {
  panelOpen.value = false
  if (activeEvent.value) router.push({
    name: 'Event Details',
    params: { eventName: activeEvent.value.name },
    query: activeEvent.value.folderID ? { folderID: activeEvent.value.folderID } : {}
  })
}
```

Add `goToAllEvents()` function:

```js
function goToAllEvents() {
  panelOpen.value = false
  router.push({ name: 'Event' })
}
```

Add Escape key handler. Define the function at the **top level of `<script setup>`** (not inside `onMounted`) so `onUnmounted` can reference it:

```js
function handleKeydown(e) {
  if (e.key === 'Escape') panelOpen.value = false
}
```

Then in the existing `onMounted` block, add after existing setup:

```js
document.addEventListener('keydown', handleKeydown)
```

In the existing `onUnmounted` block, add:

```js
document.removeEventListener('keydown', handleKeydown)
```

- [ ] **Step 2: Remove the old dropdown markup from the template**

In the `<template>`, locate the old chip button and its entire dropdown. The chip button currently reads `@click="eventMenuOpen = !eventMenuOpen"` — the rename in Step 1 already updated it to `panelOpen`, so the button itself is correct and stays. What you need to remove is:

1. The `<Transition>` block containing the dropdown div (`v-if="eventMenuOpen"` → now `v-if="panelOpen"`) — delete it entirely
2. The backdrop div immediately after: `<div v-if="eventMenuOpen" class="fixed inset-0 z-40 bg-black/20" ...>` — delete it

Also update the chip button's chevron class to reflect the new panel direction (right arrow that rotates down):

```html
<i class="pi pi-chevron-right text-[10px] flex-shrink-0 opacity-50 transition-transform duration-200"
   :class="panelOpen ? 'rotate-90' : ''"></i>
```

- [ ] **Step 3: Add slide-over panel + backdrop to template**

Add the following just before the closing `</nav>` tag:

```html
<!-- Backdrop -->
<Transition
  enter-active-class="transition-opacity duration-200"
  enter-from-class="opacity-0"
  enter-to-class="opacity-100"
  leave-active-class="transition-opacity duration-150"
  leave-from-class="opacity-100"
  leave-to-class="opacity-0"
>
  <div
    v-if="panelOpen"
    class="fixed inset-0 z-40 bg-black/50"
    @click="panelOpen = false"
  ></div>
</Transition>

<!-- Slide-over panel -->
<Transition
  enter-active-class="transition-transform duration-200 ease-out"
  enter-from-class="translate-x-full"
  enter-to-class="translate-x-0"
  leave-active-class="transition-transform duration-150 ease-in"
  leave-from-class="translate-x-0"
  leave-to-class="translate-x-full"
>
  <div
    v-if="panelOpen"
    class="fixed top-0 right-0 h-full w-full md:w-[300px] z-50 bg-surface-800 border-l border-[rgba(255,255,255,0.07)] overflow-hidden flex flex-col"
  >
    <EventPanel
      :role="role"
      :activeEvent="activeEvent"
      @close="panelOpen = false"
      @navigate="goToSection"
      @goToEventDetails="goToEventDetails"
      @goToAllEvents="goToAllEvents"
      @changeEvent="changeEvent"
    />
  </div>
</Transition>
```

- [ ] **Step 4: Start dev server and verify panel opens/closes**

```bash
cd BES-frontend && npm run dev
```

- Open http://localhost:5173, log in
- Click the event chip → panel should slide in from the right
- Click the backdrop → panel closes
- Press Escape → panel closes
- Navigate via a section tile → panel closes and page changes

- [ ] **Step 5: Commit**

```bash
git add BES-frontend/src/App.vue
git commit -m "feat: wire EventPanel into App.vue, replace dropdown with slide-over panel"
```

---

## Task 4: App.vue — remove Events from primary nav, slim mobile hamburger, show event chip on mobile

**Files:**
- Modify: `BES-frontend/src/App.vue`

- [ ] **Step 1: Remove "Events" from desktop primary nav**

In the desktop center nav section (`<!-- Center: Primary nav -->`), remove the entire `<router-link>` block for `/events`:

```html
<!-- DELETE THIS ENTIRE BLOCK -->
<router-link v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" to="/events" v-slot="{ isActive }">
  <span
    class="inline-flex items-center gap-1.5 px-3.5 py-1.5 type-label cursor-pointer transition-all duration-200"
    :class="isActive
      ? 'text-accent border-b-2 border-[color:var(--accent-color)]'
      : 'text-content-muted hover:text-content-primary'"
  >Events</span>
</router-link>
```

- [ ] **Step 2: Make event chip visible on mobile**

The current right section is `class="hidden md:flex items-center gap-2"`. The mobile hamburger button sits outside this div. We need the event chip visible on mobile.

Replace the entire right column (from `<!-- Right: event chip -->` to just before the mobile hamburger button `<!-- Mobile Hamburger -->`) with:

```html
<!-- Right: always-visible chip + desktop utilities + mobile hamburger -->
<div class="flex items-center gap-2">

  <!-- Event chip — visible on all screen sizes -->
  <div v-if="isAuthenticated" class="relative">
    <button
      v-if="activeEvent"
      @click="panelOpen = !panelOpen"
      class="inline-flex items-center gap-1.5 px-3 py-1.5 type-label para-chip-sm text-content-secondary hover:text-content-primary transition-all duration-200 max-w-[140px] md:max-w-[200px]"
    >
      <span class="truncate">{{ activeEvent.name }}</span>
      <i class="pi pi-chevron-right text-[10px] flex-shrink-0 opacity-50 transition-transform duration-200"
         :class="panelOpen ? 'rotate-90' : ''"></i>
    </button>
    <button
      v-else
      @click="router.push({ name: 'EventSelector' })"
      class="inline-flex items-center gap-1.5 px-3 py-1.5 type-label para-chip-sm text-content-muted hover:text-content-primary border border-[color:var(--accent-muted)] transition-all duration-200"
    >
      Select Event →
    </button>
  </div>

  <!-- Desktop-only utilities -->
  <div class="hidden md:flex items-center gap-2">
    <div v-if="isAuthenticated" class="h-4 w-px bg-[rgba(255,255,255,0.12)]"></div>

    <span v-if="isAuthenticated && roleDisplay"
      class="badge-neutral type-label px-2 py-0.5">
      {{ roleDisplay.label }}
    </span>

    <button @click="toggleTheme"
      class="inline-flex items-center justify-center w-8 h-8 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.06)] transition-all duration-200">
      <i class="pi text-sm" :class="theme === 'dark' ? 'pi-sun' : 'pi-moon'"></i>
    </button>

    <router-link v-if="!isAuthenticated" to="/login">
      <span class="px-4 py-1.5 para-chip type-label text-surface-900 bg-accent cursor-pointer">Login</span>
    </router-link>

    <button v-if="isAuthenticated"
      @click="openModal('Logout Confirmation', 'Are you sure you want to securely log out?')"
      class="inline-flex items-center justify-center w-8 h-8 type-label text-content-muted hover:text-red-400 hover:bg-red-950 transition-all duration-200">
      <i class="pi pi-sign-out text-sm"></i>
    </button>
  </div>

  <!-- Mobile hamburger -->
  <button @click="isOpen = !isOpen"
    class="md:hidden inline-flex items-center justify-center w-9 h-9 type-label text-content-muted hover:text-content-primary hover:bg-[rgba(255,255,255,0.06)] transition-colors focus:outline-none">
    <i class="pi text-lg" :class="isOpen ? 'pi-times' : 'pi-bars'"></i>
  </button>

</div>
```

Note: Task 3 kept the original chip button (only removing the dropdown below it). In this step you're restructuring the entire right column — the chip button from Task 3 is now re-written as part of this combined block.

- [ ] **Step 3: Slim the mobile dropdown — remove Events link and all event section links**

In the mobile dropdown (`v-show="isOpen"`), remove:

1. The entire Events `<router-link>` block:
```html
<!-- DELETE -->
<router-link v-if="role === 'ROLE_ADMIN' || role === 'ROLE_ORGANISER'" to="/events" v-slot="{ isActive }">
  <span class="flex items-center gap-3 px-4 py-3 type-label cursor-pointer transition-colors duration-150"
    :class="isActive ? 'text-accent' : 'text-content-secondary hover:text-content-primary'">
    Events
  </span>
</router-link>
```

2. The entire `<template v-if="isAuthenticated && activeEvent">` block (section rule + all section buttons + Change Event button):
```html
<!-- DELETE the entire block from here... -->
<template v-if="isAuthenticated && activeEvent">
  <div class="px-4 pt-3 pb-1 section-rule">...</div>
  <button ...>Event Details</button>
  <button ...>Audition</button>
  <button ...>Participants</button>
  <button ...>Scoreboard</button>
  <button ...>Battle</button>
  <button ...>Adjust Numbers</button>
  <button ...>Change Event</button>
</template>
<!-- ...to here -->
```

- [ ] **Step 4: Add theme toggle + logout to mobile dropdown footer**

The mobile dropdown footer already has these. Verify the bottom of the mobile dropdown contains role badge, theme toggle, and logout button — these should be unchanged.

- [ ] **Step 5: Verify in browser**

```bash
cd BES-frontend && npm run dev
```

**Desktop checks:**
- Primary nav center shows only `Home` and `Admin` (Admin role) — no `Events`
- Event chip visible in top-right, opens panel

**Mobile checks (resize browser to < 768px):**
- Event chip visible in navbar (not hidden)
- Hamburger opens a slim menu: only Home + Admin links + role/theme/logout footer
- Tapping event chip opens full-width panel with large tile targets

**All roles:**
- No active event → chip shows `Select Event →`, clicking goes to EventSelector
- Active event → chip shows event name, clicking opens panel

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/App.vue
git commit -m "feat: remove Events from nav, show event chip on mobile, slim hamburger"
```

---

## Task 5: Run full test suite and verify acceptance criteria

**Files:** none

- [ ] **Step 1: Run full frontend test suite**

```bash
cd BES-frontend && npm test -- --run
```

Expected: all existing tests pass, EventPanel tests pass

- [ ] **Step 2: Verify each acceptance criterion in the browser**

Log in as each role and check:

| Criterion | How to verify |
|-----------|--------------|
| "Events" not in nav | Check primary nav and hamburger for any role |
| Chip opens panel on desktop | Click chip |
| Chip opens panel on mobile | Resize to mobile, tap chip |
| Tiles role-filtered | Log in as Emcee → see only Audition + Score; as Judge → Audition + Battle |
| Tile height ≥ 56px | DevTools → inspect tile element, check computed height |
| Admin panel has searchable event list | Log in as Admin, open panel, type in search box |
| Switching event keeps panel open | Click a different event in the list → panel stays, header updates |
| Emcee/Judge see "Change Event" | Log in as Emcee, open panel |
| No active event shows "Select Event →" | Clear localStorage `activeEvent`, reload → chip shows prompt |
| Backdrop closes panel | Click outside the panel |
| Escape closes panel | Press Escape while panel open |
| Route change closes panel | Open panel, click Home link |
| Events page via "All Events →" | Open panel as Admin → click All Events → lands on /events |
| Mobile hamburger: Home + Admin only | Resize to mobile, open hamburger |

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "feat: nav + events restructure complete (issue #60)"
```
