# Mobile & Tablet Responsiveness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix touch drag-and-drop in BattleControl, add tablet layout to BattleJudge, enable desktop swipe in EmceeRoundView, and prevent EventSelector grid overflow on narrow phones — closing issue #59.

**Architecture:** All changes are frontend-only. BattleControl and EmceeRoundView replace HTML5/touch-only event handlers with the Pointer Events API (works on touch + mouse + trackpad). BattleJudge adds CSS `@media` breakpoints. EventSelector adds overflow protection and a narrow-phone grid fallback. No backend changes, no new dependencies.

**Tech Stack:** Vue 3 (Composition API), Tailwind CSS, Vitest + Vue Test Utils

---

## File Map

| File | Change |
|------|--------|
| `BES-frontend/src/views/BattleControl.vue` | Extract `parseDropKey` util; replace HTML5 drag with pointer events; add `data-drop-key` on drop targets; add `@pointerdown` on drag sources |
| `BES-frontend/src/utils/pointerDnd.js` | New file — `parseDropKey(key)` pure helper, tested in isolation |
| `BES-frontend/src/utils/__tests__/pointerDnd.test.js` | New file — unit tests for `parseDropKey` |
| `BES-frontend/src/components/EmceeRoundView.vue` | Replace `@touchstart/touchmove/touchend` with `@pointerdown/pointermove/pointerup`; add `pointercancel`; fix `touch-action` |
| `BES-frontend/src/views/BattleJudge.vue` | Add tablet `@media` breakpoints; fix tap targets on ✕ button and SELECT JUDGE button |
| `BES-frontend/src/views/EventSelector.vue` | Fix event name overflow in 2-col grid; add `max-w-[360px]` fallback to 1-col; reduce top margin on small screens |

---

## Task 1: Extract `parseDropKey` and write tests

This is the only testable unit of pure logic in the DnD refactor — everything else is DOM interaction. Extract it first so the test exists before Task 2 wires it in.

**Files:**
- Create: `BES-frontend/src/utils/pointerDnd.js`
- Create: `BES-frontend/src/utils/__tests__/pointerDnd.test.js`

- [ ] **Step 1.1: Create the utility file**

Create `BES-frontend/src/utils/pointerDnd.js`:

```js
/**
 * Parse a data-drop-key attribute value into a typed drop target descriptor.
 *
 * Bracket keys: "bracket-Top8-0-1"  → { type: 'bracket', roundKey: 'Top8', matchIdx: 0, slotIdx: 1 }
 * Smoke keys:   "smoke-3"           → { type: 'smoke', idx: 3 }
 * Anything else: null
 */
export function parseDropKey(key) {
  if (!key) return null

  if (key.startsWith('smoke-')) {
    const idx = parseInt(key.slice(6), 10)
    if (isNaN(idx)) return null
    return { type: 'smoke', idx }
  }

  if (key.startsWith('bracket-')) {
    const inner = key.slice(8)           // e.g. "Top8-0-1"
    const parts = inner.split('-')       // ["Top8", "0", "1"]
    if (parts.length < 3) return null
    const slotIdx  = parseInt(parts[parts.length - 1], 10)
    const matchIdx = parseInt(parts[parts.length - 2], 10)
    const roundKey = parts.slice(0, parts.length - 2).join('-')  // "Top8"
    if (isNaN(slotIdx) || isNaN(matchIdx) || !roundKey) return null
    return { type: 'bracket', roundKey, matchIdx, slotIdx }
  }

  return null
}
```

- [ ] **Step 1.2: Write the tests**

Create `BES-frontend/src/utils/__tests__/pointerDnd.test.js`:

```js
import { describe, it, expect } from 'vitest'
import { parseDropKey } from '../pointerDnd'

describe('parseDropKey', () => {
  describe('smoke keys', () => {
    it('parses smoke-0', () => {
      expect(parseDropKey('smoke-0')).toEqual({ type: 'smoke', idx: 0 })
    })
    it('parses smoke-7', () => {
      expect(parseDropKey('smoke-7')).toEqual({ type: 'smoke', idx: 7 })
    })
    it('returns null for malformed smoke key', () => {
      expect(parseDropKey('smoke-')).toBeNull()
      expect(parseDropKey('smoke-abc')).toBeNull()
    })
  })

  describe('bracket keys', () => {
    it('parses Top8 bracket key slot 0', () => {
      expect(parseDropKey('bracket-Top8-0-0')).toEqual({
        type: 'bracket', roundKey: 'Top8', matchIdx: 0, slotIdx: 0,
      })
    })
    it('parses Top8 bracket key slot 1', () => {
      expect(parseDropKey('bracket-Top8-2-1')).toEqual({
        type: 'bracket', roundKey: 'Top8', matchIdx: 2, slotIdx: 1,
      })
    })
    it('parses Top16 bracket key', () => {
      expect(parseDropKey('bracket-Top16-3-0')).toEqual({
        type: 'bracket', roundKey: 'Top16', matchIdx: 3, slotIdx: 0,
      })
    })
    it('parses Top32 bracket key', () => {
      expect(parseDropKey('bracket-Top32-15-1')).toEqual({
        type: 'bracket', roundKey: 'Top32', matchIdx: 15, slotIdx: 1,
      })
    })
    it('returns null for too-short bracket key', () => {
      expect(parseDropKey('bracket-Top8-0')).toBeNull()
    })
  })

  describe('edge cases', () => {
    it('returns null for null input', () => {
      expect(parseDropKey(null)).toBeNull()
    })
    it('returns null for empty string', () => {
      expect(parseDropKey('')).toBeNull()
    })
    it('returns null for unknown prefix', () => {
      expect(parseDropKey('pool-Alice')).toBeNull()
    })
  })
})
```

- [ ] **Step 1.3: Run tests to verify they pass**

```bash
cd BES-frontend && npm test -- pointerDnd --run
```

Expected: all 11 tests pass.

- [ ] **Step 1.4: Commit**

```bash
git add BES-frontend/src/utils/pointerDnd.js BES-frontend/src/utils/__tests__/pointerDnd.test.js
git commit -m "feat: add parseDropKey utility for pointer-events DnD (issue #59)"
```

---

## Task 2: BattleControl — Replace HTML5 DnD with Pointer Events

**Files:**
- Modify: `BES-frontend/src/views/BattleControl.vue`

This task has two parts: (A) wire up the pointer-event drag engine in `<script setup>`, and (B) update the template to remove HTML5 drag attributes and add pointer event bindings + `data-drop-key` attributes.

### Part A — Script changes

- [ ] **Step 2.1: Import `parseDropKey` and add the pointer drag handler**

In `BES-frontend/src/views/BattleControl.vue`, add the import at the top of `<script setup>` (after the existing imports):

```js
import { parseDropKey } from '@/utils/pointerDnd'
```

Then add this block after the existing `onSmokeDrop` function (around line 824):

```js
// ── Pointer-events DnD (replaces HTML5 drag API — works on touch + desktop) ──

let _ghostEl = null
let _ptrMoveHandler = null
let _ptrUpHandler = null

const _removePtrListeners = () => {
  if (_ptrMoveHandler) { document.removeEventListener('pointermove', _ptrMoveHandler); _ptrMoveHandler = null }
  if (_ptrUpHandler)   { document.removeEventListener('pointerup',   _ptrUpHandler);   _ptrUpHandler   = null }
  document.removeEventListener('pointercancel', _ptrUpHandler ?? (() => {}))
}

const _cleanupDrag = () => {
  if (_ghostEl) { document.body.removeChild(_ghostEl); _ghostEl = null }
  _removePtrListeners()
  dragSource.value  = null
  poolDragName.value = null
  dragOverKey.value  = null
}

const onPointerDragStart = (type, payload, e) => {
  if (setupLocked.value) return
  e.preventDefault()

  // Set existing drag-state refs (drives existing highlight CSS — no template class changes)
  if (type === 'pool') {
    poolDragName.value = payload          // payload = name string
    dragSource.value   = null
  } else if (type === 'bracket') {
    dragSource.value   = payload          // payload = { roundKey, matchIdx, slotIdx }
    poolDragName.value = null
  } else if (type === 'smoke') {
    dragSource.value   = { smokeIdx: payload }   // payload = mIdx (number)
    poolDragName.value = null
  }

  // Resolve display name for ghost label
  let ghostName = ''
  if (type === 'pool') {
    ghostName = payload
  } else if (type === 'bracket') {
    ghostName = rounds.value[payload.roundKey]?.[payload.matchIdx]?.[payload.slotIdx] ?? ''
  } else if (type === 'smoke') {
    ghostName = rounds.value[payload]?.name ?? ''
  }

  // Create ghost element
  _ghostEl = document.createElement('div')
  _ghostEl.textContent = ghostName
  Object.assign(_ghostEl.style, {
    position:      'fixed',
    left:          `${e.clientX + 12}px`,
    top:           `${e.clientY + 12}px`,
    padding:       '5px 14px',
    background:    '#1a1a1a',
    border:        `1.5px solid ${type === 'pool' ? 'rgba(255,255,255,0.25)' : 'rgba(248,113,113,0.65)'}`,
    borderRadius:  '8px',
    fontSize:      '12px',
    fontWeight:    '600',
    color:         '#f0f0f0',
    boxShadow:     '0 10px 28px rgba(0,0,0,0.7)',
    whiteSpace:    'nowrap',
    pointerEvents: 'none',
    zIndex:        '9999',
  })
  document.body.appendChild(_ghostEl)

  _ptrMoveHandler = (ev) => {
    _ghostEl.style.left = `${ev.clientX + 12}px`
    _ghostEl.style.top  = `${ev.clientY + 12}px`

    // Find drop target under pointer (hide ghost first so it doesn't intercept)
    _ghostEl.style.display = 'none'
    const el = document.elementFromPoint(ev.clientX, ev.clientY)
    _ghostEl.style.display = ''
    dragOverKey.value = el?.closest('[data-drop-key]')?.dataset.dropKey ?? null
  }

  _ptrUpHandler = (ev) => {
    _removePtrListeners()

    // Hide ghost before elementFromPoint so it doesn't intercept the hit test
    if (_ghostEl) _ghostEl.style.display = 'none'
    const el = document.elementFromPoint(ev.clientX, ev.clientY)
    if (_ghostEl) { document.body.removeChild(_ghostEl); _ghostEl = null }

    const dropKeyEl = el?.closest('[data-drop-key]')
    const parsed = dropKeyEl ? parseDropKey(dropKeyEl.dataset.dropKey) : null

    if (parsed && !setupLocked.value) {
      if (parsed.type === 'bracket') {
        onDrop(parsed.roundKey, parsed.matchIdx, parsed.slotIdx)
      } else if (parsed.type === 'smoke') {
        onSmokeDrop(parsed.idx)
      }
    } else {
      // No valid drop — clear state manually (onDrop/onSmokeDrop would have done this)
      dragSource.value   = null
      poolDragName.value = null
      dragOverKey.value  = null
    }
  }

  document.addEventListener('pointermove',  _ptrMoveHandler)
  document.addEventListener('pointerup',    _ptrUpHandler)
  document.addEventListener('pointercancel', _ptrUpHandler)
}
```

Also add cleanup to the existing `onUnmounted` hook (find it in the file and add `_cleanupDrag()` before the existing body):

```js
onUnmounted(() => {
  _cleanupDrag()   // ← add this line
  // ... existing cleanup ...
})
```

### Part B — Template changes

- [ ] **Step 2.2: Update pool chips**

Find the pool chip `<span>` (around line 2252–2263). Replace:

```html
<span
  v-for="p in poolParticipants" :key="p.name"
  draggable="true"
  @dragstart="(e) => onPoolDragStart(p.name, e)"
  @dragend="onPoolDragEnd"
  class="para-chip-sm px-2.5 py-1 type-label text-content-primary cursor-grab active:cursor-grabbing select-none inline-flex items-center gap-1.5"
  :class="poolDragName === p.name ? 'opacity-40' : ''"
  :title="p.name"
>
```

With:

```html
<span
  v-for="p in poolParticipants" :key="p.name"
  @pointerdown="(e) => onPointerDragStart('pool', p.name, e)"
  class="para-chip-sm px-2.5 py-1 type-label text-content-primary cursor-grab active:cursor-grabbing select-none inline-flex items-center gap-1.5"
  :class="poolDragName === p.name ? 'opacity-40' : ''"
  :title="p.name"
  style="touch-action: none; user-select: none;"
>
```

- [ ] **Step 2.3: Update bracket slot 0 containers and name divs**

For each bracket slot 0 drop zone (the `<div class="flex-1 min-w-0 flex items-center gap-1.5 px-2 py-1.5 ...">` for slot 0), replace the drag-over/drop attrs and add `data-drop-key`:

```html
<div
  class="flex-1 min-w-0 flex items-center gap-1.5 px-2 py-1.5 transition-all duration-150"
  :data-drop-key="`bracket-Top${size}-${mIdx}-0`"
  :class="[
    match[2] === match[0] && match[0] ? 'bg-emerald-500/10' : '',
    dragSource?.roundKey === `Top${size}` && dragSource?.matchIdx === mIdx && dragSource?.slotIdx === 0
      ? 'ring-2 ring-primary-400/80 bg-primary-400/12 shadow-inner'
      : dragOverKey === `Top${size}-${mIdx}-0` ? 'bg-primary-500/15 ring-2 ring-inset ring-primary-500/70' : ''
  ]"
>
```

(Remove `@dragover.prevent`, `@dragleave`, `@drop.prevent` from this div.)

For the inner name `<div v-if="match[0]" ...>` inside slot 0, replace:

```html
<div v-if="match[0]"
  :draggable="!setupLocked"
  @dragstart="!setupLocked && onDragStart(`Top${size}`, mIdx, 0, $event)"
  @dragend="!setupLocked && onDragEnd()"
  class="flex-1 min-w-0 select-none flex items-center gap-3"
  :class="[!setupLocked ? 'cursor-grab active:cursor-grabbing' : '', match[2] === match[0] && match[0] ? 'text-emerald-400' : 'text-content-primary']"
>
```

With:

```html
<div v-if="match[0]"
  @pointerdown="(e) => onPointerDragStart('bracket', { roundKey: `Top${size}`, matchIdx: mIdx, slotIdx: 0 }, e)"
  class="flex-1 min-w-0 select-none flex items-center gap-3"
  :class="[!setupLocked ? 'cursor-grab active:cursor-grabbing' : '', match[2] === match[0] && match[0] ? 'text-emerald-400' : 'text-content-primary']"
  style="touch-action: none;"
>
```

- [ ] **Step 2.4: Update bracket slot 1 containers and name divs**

For the slot 1 drop zone (`<div class="flex-1 min-w-0 flex items-center gap-1.5 px-2 py-1.5 ...">` for slot 1), replace:

```html
<div
  class="flex-1 min-w-0 flex items-center gap-1.5 px-2 py-1.5 transition-all duration-150"
  :class="[
    match[2] === match[1] && match[1] ? 'bg-emerald-500/10' : '',
    dragSource?.roundKey === `Top${size}` && dragSource?.matchIdx === mIdx && dragSource?.slotIdx === 1
      ? 'ring-2 ring-primary-400/80 bg-primary-400/12 shadow-inner'
      : dragOverKey === `Top${size}-${mIdx}-1` ? 'bg-primary-500/15 ring-2 ring-inset ring-primary-500/70' : ''
  ]"
  @dragover.prevent="!setupLocked && onDragOver(`Top${size}`, mIdx, 1)"
  @dragleave="dragOverKey = null"
  @drop.prevent="!setupLocked && onDrop(`Top${size}`, mIdx, 1)"
>
```

With:

```html
<div
  class="flex-1 min-w-0 flex items-center gap-1.5 px-2 py-1.5 transition-all duration-150"
  :data-drop-key="`bracket-Top${size}-${mIdx}-1`"
  :class="[
    match[2] === match[1] && match[1] ? 'bg-emerald-500/10' : '',
    dragSource?.roundKey === `Top${size}` && dragSource?.matchIdx === mIdx && dragSource?.slotIdx === 1
      ? 'ring-2 ring-primary-400/80 bg-primary-400/12 shadow-inner'
      : dragOverKey === `Top${size}-${mIdx}-1` ? 'bg-primary-500/15 ring-2 ring-inset ring-primary-500/70' : ''
  ]"
>
```

For the inner name `<div v-if="match[1]" ...>` inside slot 1, replace:

```html
<div v-if="match[1]"
  :draggable="!setupLocked"
  @dragstart="!setupLocked && onDragStart(`Top${size}`, mIdx, 1, $event)"
  @dragend="!setupLocked && onDragEnd()"
  class="flex-1 min-w-0 select-none flex items-center gap-3"
  :class="[!setupLocked ? 'cursor-grab active:cursor-grabbing' : '', match[2] === match[1] && match[1] ? 'text-emerald-400' : 'text-content-primary']"
>
```

With:

```html
<div v-if="match[1]"
  @pointerdown="(e) => onPointerDragStart('bracket', { roundKey: `Top${size}`, matchIdx: mIdx, slotIdx: 1 }, e)"
  class="flex-1 min-w-0 select-none flex items-center gap-3"
  :class="[!setupLocked ? 'cursor-grab active:cursor-grabbing' : '', match[2] === match[1] && match[1] ? 'text-emerald-400' : 'text-content-primary']"
  style="touch-action: none;"
>
```

- [ ] **Step 2.5: Fix Win and Start button tap targets**

The Win button is `w-9` (36px wide) and the Start button is `w-8` (32px wide) — both below 44px. Height is fine (they stretch to `min-h-[44px]` via the flex container). Widen both.

Find the two Win buttons inside the match card (one per slot, near the `'Win'` text). Both have `class="flex-shrink-0 w-9 text-center rounded ..."`. Change `w-9` → `w-11` on both:

```html
<!-- Win button for slot 0 — change w-9 to w-11 -->
<button
  :disabled="!match[0]"
  @click="match[2] === match[0] && match[0] ? clearWinner(`Top${size}`, mIdx) : requestWin(`Top${size}`, mIdx, 0, match[0])"
  class="flex-shrink-0 w-11 text-center rounded text-[11px] font-bold transition-all disabled:opacity-20 disabled:cursor-not-allowed leading-5"
  :class="match[2] === match[0] && match[0] ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 hover:bg-red-500/20 hover:text-red-400 hover:border-red-500/40' : 'bg-surface-700 text-surface-400 border border-surface-600/50 hover:border-surface-500'"
>{{ match[2] === match[0] && match[0] ? '✓' : 'Win' }}</button>
```

```html
<!-- Win button for slot 1 — change w-9 to w-11 -->
<button
  :disabled="!match[1]"
  @click="match[2] === match[1] && match[1] ? clearWinner(`Top${size}`, mIdx) : requestWin(`Top${size}`, mIdx, 1, match[1])"
  class="flex-shrink-0 w-11 text-center rounded text-[11px] font-bold transition-all disabled:opacity-20 disabled:cursor-not-allowed leading-5"
  :class="match[2] === match[1] && match[1] ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40 hover:bg-red-500/20 hover:text-red-400 hover:border-red-500/40' : 'bg-surface-700 text-surface-400 border border-surface-600/50 hover:border-surface-500'"
>{{ match[2] === match[1] && match[1] ? '✓' : 'Win' }}</button>
```

Find the Start button (has `w-8 ml-1.5`). Change `w-8` → `w-10`:

```html
<button
  v-if="match[0] && match[1] && !match[2] && isActiveRoundFilled && effectivePhase === 'IDLE'"
  @click="requestStartAt(`Top${size}`, rounds[`Top${size}`], mIdx)"
  class="flex-shrink-0 flex items-center justify-center w-10 ml-1.5 self-stretch rounded text-accent border border-[color:var(--accent-muted)] bg-[color:var(--accent-subtle)] hover:bg-[color:var(--accent-muted)] transition-colors"
  title="Start round from this match"
><i class="pi pi-play text-[10px]"></i></button>
```

- [ ] **Step 2.7: Update smoke queue slots**

Find the smoke slot `<div v-for="(match, mIdx) in rounds" ...>` (around line 2441–2454). Replace:

```html
<div
  v-for="(match, mIdx) in rounds"
  :key="mIdx"
  :draggable="!!match.name && !setupLocked"
  @dragstart="!setupLocked && onSmokeDragStart(mIdx, $event)"
  @dragend="onDragEnd"
  @dragover.prevent="!setupLocked && onSmokeDragOver(mIdx)"
  @dragleave="dragOverKey = null"
  @drop.prevent="!setupLocked && onSmokeDrop(mIdx)"
  class="card-hover relative flex items-stretch overflow-hidden transition-all duration-150"
  :class="dragOverKey === `smoke-${mIdx}` ? 'ring-2 ring-inset ring-primary-500/70 bg-primary-500/10' :
          (dragSource?.smokeIdx === mIdx ? 'ring-2 ring-primary-400/80 bg-primary-400/12' : '')"
  style="padding:0"
>
```

With:

```html
<div
  v-for="(match, mIdx) in rounds"
  :key="mIdx"
  :data-drop-key="`smoke-${mIdx}`"
  @pointerdown="(e) => !!match.name && onPointerDragStart('smoke', mIdx, e)"
  class="card-hover relative flex items-stretch overflow-hidden transition-all duration-150"
  :class="dragOverKey === `smoke-${mIdx}` ? 'ring-2 ring-inset ring-primary-500/70 bg-primary-500/10' :
          (dragSource?.smokeIdx === mIdx ? 'ring-2 ring-primary-400/80 bg-primary-400/12' : '')"
  style="padding:0; touch-action: none;"
>
```

- [ ] **Step 2.8: Manual test — drag-and-drop in browser**

Run the dev server:
```bash
cd BES-frontend && npm run dev
```

Open Chrome DevTools → Toggle device toolbar → Select "iPad Air" (portrait).

Navigate to `/battle/control`. Verify:
1. Pool chips can be dragged to bracket slots with touch simulation
2. Bracket slot names can be swapped by dragging one onto another
3. Smoke queue slots can be reordered by drag
4. Hover highlight (`dragOverKey`) appears on the target slot during drag
5. No drag ghost lingers after drop or cancel
6. Desktop mouse drag still works (disable device emulation and try again)

- [ ] **Step 2.9: Commit**

```bash
git add BES-frontend/src/views/BattleControl.vue
git commit -m "feat: replace HTML5 DnD with pointer events in BattleControl (issue #59)"
```

---

## Task 3: EmceeRoundView — Desktop Swipe via Pointer Events

**Files:**
- Modify: `BES-frontend/src/components/EmceeRoundView.vue`

- [ ] **Step 3.1: Replace touch handlers in `<script setup>`**

In `BES-frontend/src/components/EmceeRoundView.vue`, find the three touch handler functions (lines 51–58):

```js
const onTouchStart = (e) => { touchStartX.value = e.touches[0].clientX; isDragging.value = true; dragOffset.value = 0 }
const onTouchMove  = (e) => { if (!isDragging.value) return; dragOffset.value = e.touches[0].clientX - touchStartX.value }
const onTouchEnd   = () => {
  if (!isDragging.value) return
  isDragging.value = false
  if      (dragOffset.value < -60) goNext()
  else if (dragOffset.value >  60) goPrev()
  dragOffset.value = 0
}
```

Replace with:

```js
const onPointerDown = (e) => {
  e.currentTarget.setPointerCapture(e.pointerId)
  touchStartX.value = e.clientX
  isDragging.value  = true
  dragOffset.value  = 0
}

const onPointerMove = (e) => {
  if (!isDragging.value) return
  dragOffset.value = e.clientX - touchStartX.value
}

const onPointerUp = () => {
  if (!isDragging.value) return
  isDragging.value = false
  if      (dragOffset.value < -60) goNext()
  else if (dragOffset.value >  60) goPrev()
  dragOffset.value = 0
}
```

- [ ] **Step 3.2: Update the template bindings**

Find the card `<div>` with the touch event listeners (around line 148–153):

```html
<div
  :key="currentRound"
  :style="cardStyle"
  @touchstart.passive="onTouchStart"
  @touchmove.passive="onTouchMove"
  @touchend="onTouchEnd"
  class="card-hover p-0 relative select-none touch-pan-y"
  style="box-shadow: 0 0 0 1px rgba(255,255,255,0.06), 0 8px 32px rgba(0,0,0,0.6);"
>
```

Replace with:

```html
<div
  :key="currentRound"
  :style="cardStyle"
  @pointerdown="onPointerDown"
  @pointermove="onPointerMove"
  @pointerup="onPointerUp"
  @pointercancel="onPointerUp"
  class="card-hover p-0 relative select-none"
  style="box-shadow: 0 0 0 1px rgba(255,255,255,0.06), 0 8px 32px rgba(0,0,0,0.6); touch-action: none;"
>
```

Note: `touch-pan-y` class removed; `touch-action: none` set inline so the browser doesn't intercept touch scroll before our handler. `setPointerCapture` handles delivery of move/up events when the pointer leaves the element bounds.

- [ ] **Step 3.3: Manual test in browser**

Navigate to `/event/audition-list`, select Emcee role and a genre with participants.

Verify:
1. **Touch (device emulation):** swipe left → next round, swipe right → previous round. Threshold is 60px offset.
2. **Mouse (desktop):** click-hold and drag left/right on the card → same navigation.
3. **Trackpad:** two-finger horizontal swipe → same navigation (Chrome/Mac trackpad gesture).
4. Swipe hint arrows (← / →) appear during drag as before.
5. Round counter updates correctly.

- [ ] **Step 3.4: Commit**

```bash
git add BES-frontend/src/components/EmceeRoundView.vue
git commit -m "feat: replace touch-only swipe with pointer events in EmceeRoundView (issue #59)"
```

---

## Task 4: BattleJudge — Tablet Layout + Tap Target Fixes

**Files:**
- Modify: `BES-frontend/src/views/BattleJudge.vue`

- [ ] **Step 4.1: Update the portrait tablet breakpoint**

In `BES-frontend/src/views/BattleJudge.vue`, find the existing `@media (min-width: 600px)` block (around line 791–798):

```css
@media (min-width: 600px) {
  .judge-root {
    max-width: 480px;
    margin: 0 auto;
    border-left:  1px solid rgba(255,255,255,0.05);
    border-right: 1px solid rgba(255,255,255,0.05);
  }
}
```

Replace with:

```css
/* Tablet portrait — wider layout */
@media (min-width: 600px) {
  .judge-root {
    max-width: 640px;
    margin: 0 auto;
    border-left:  1px solid rgba(255,255,255,0.05);
    border-right: 1px solid rgba(255,255,255,0.05);
  }
}

/* Tablet landscape — shorter header, taller tie row for thumb reach */
@media (min-width: 600px) and (orientation: landscape) {
  .judge-header {
    height: 44px;
  }
  .tie-row {
    height: 26%;
    min-height: 72px;
  }
}
```

- [ ] **Step 4.2: Fix the judge chip ✕ tap target**

Find `.judge-chip-clear` (around line 506–511):

```css
.judge-chip-clear {
  background: none; border: none; cursor: pointer;
  font-size: 10px; color: rgba(255,255,255,0.3);
  padding: 0 2px; line-height: 1; transition: color 0.15s;
}
.judge-chip-clear:hover { color: rgba(255,255,255,0.8); }
```

Replace with:

```css
.judge-chip-clear {
  background: none; border: none; cursor: pointer;
  font-size: 10px; color: rgba(255,255,255,0.3);
  padding: 10px 8px;
  margin: -10px -8px;
  min-width: 44px; min-height: 44px;
  display: flex; align-items: center; justify-content: center;
  line-height: 1; transition: color 0.15s;
  -webkit-tap-highlight-color: transparent;
}
.judge-chip-clear:hover { color: rgba(255,255,255,0.8); }
```

- [ ] **Step 4.3: Fix the SELECT JUDGE button tap target**

Find `.pick-judge-btn` (around line 513–524):

```css
.pick-judge-btn {
  font-family: 'Inter', sans-serif;
  font-size: 9px; font-weight: 800;
  letter-spacing: 0.2em; text-transform: uppercase;
  padding: 5px 12px; border-radius: 999px;
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.2);
  color: rgba(255,255,255,0.7);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
```

Replace `padding: 5px 12px` with `padding: 8px 16px`:

```css
.pick-judge-btn {
  font-family: 'Inter', sans-serif;
  font-size: 9px; font-weight: 800;
  letter-spacing: 0.2em; text-transform: uppercase;
  padding: 8px 16px; border-radius: 999px;
  background: rgba(255,255,255,0.08);
  border: 1px solid rgba(255,255,255,0.2);
  color: rgba(255,255,255,0.7);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
```

- [ ] **Step 4.4: Manual test in browser**

Open `/battle/judge` in Chrome DevTools with device toolbar:

1. **Phone portrait (375×667 "iPhone SE"):** Layout unchanged. Vote panels fill screen. ✕ button tap area is larger.
2. **Tablet portrait (768×1024 "iPad Air"):** Page centers at max 640px. Vote panels use ~640px width instead of 480px. 
3. **Tablet landscape (1024×768 "iPad Air" landscape):** Header is 44px height. Tie row is taller (~26% height). LR panels fill remaining height. Everything reachable with thumbs.
4. Click the ✕ on the judge chip — confirm it triggers on first tap (no mis-tap).

- [ ] **Step 4.5: Commit**

```bash
git add BES-frontend/src/views/BattleJudge.vue
git commit -m "feat: tablet layout and tap target fixes for BattleJudge (issue #59)"
```

---

## Task 5: EventSelector — Narrow-Phone Grid Overflow Fix

**Files:**
- Modify: `BES-frontend/src/views/EventSelector.vue`

- [ ] **Step 5.1: Add a `<style scoped>` block and narrow-phone override**

`EventSelector.vue` currently has no `<style>` block. Add one at the end of the file:

```html
<style scoped>
/* Prevent long event names from clipping in the 2-col grid */
.event-card-name {
  overflow-wrap: break-word;
  word-break: break-word;
}

/* On very narrow phones fall back to 1-col to avoid squashed cards */
@media (max-width: 360px) {
  .event-grid {
    grid-template-columns: 1fr !important;
  }
}
</style>
```

- [ ] **Step 5.2: Apply the classes in the template**

Find the event grid `<div>` (around line 84):

```html
<div class="grid gap-2" :class="events.length > 4 ? 'grid-cols-2' : 'grid-cols-1'">
```

Add `event-grid` class:

```html
<div class="grid gap-2 event-grid" :class="events.length > 4 ? 'grid-cols-2' : 'grid-cols-1'">
```

Find the event name `<div class="type-body mb-1">{{ event.name }}</div>` (around line 96) and add `event-card-name`:

```html
<div class="type-body mb-1 event-card-name">{{ event.name }}</div>
```

- [ ] **Step 5.3: Reduce top margin on small screens**

Find (around line 67):

```html
<div class="relative z-10 w-full max-w-md mt-10">
```

Replace `mt-10` with `mt-4 sm:mt-10`:

```html
<div class="relative z-10 w-full max-w-md mt-4 sm:mt-10">
```

- [ ] **Step 5.4: Reduce card padding in 2-col mode on small phones**

Find the event button (around line 85–103):

```html
<button
  ...
  :class="[
    'card-hover p-5 text-left relative group w-full',
    selectedEventId === event.id ? 'border-[color:var(--accent-muted)]' : ''
  ]"
>
```

Change `p-5` to `p-4 sm:p-5`:

```html
<button
  ...
  :class="[
    'card-hover p-4 sm:p-5 text-left relative group w-full',
    selectedEventId === event.id ? 'border-[color:var(--accent-muted)]' : ''
  ]"
>
```

- [ ] **Step 5.5: Manual test in browser**

Open `/event/select` in Chrome DevTools device toolbar:

1. **iPhone SE (375px)** with 5+ events: 2-col grid renders. Long event names wrap instead of clipping. Cards have comfortable padding.
2. **Galaxy S5 (360px)**: Confirm 1-col fallback applies (each event takes full width).
3. **iPhone SE**: Confirm form sits higher (`mt-4` instead of `mt-10`).
4. **Desktop (1280px)**: No change from before — form is centered at `max-w-md`.

- [ ] **Step 5.6: Commit**

```bash
git add BES-frontend/src/views/EventSelector.vue
git commit -m "feat: fix EventSelector 2-col grid overflow on narrow phones (issue #59)"
```

---

## Task 6: Full regression check and PR

- [ ] **Step 6.1: Run full test suite**

```bash
cd BES-frontend && npm test -- --run
```

Expected: all tests pass (including the new `pointerDnd` tests from Task 1).

- [ ] **Step 6.2: Run the dev server and verify all four views**

```bash
cd BES-frontend && npm run dev
```

Checklist:
- [ ] `/battle/control` — bracket drag works on simulated touch + desktop mouse
- [ ] `/battle/control` — 7-to-smoke queue drag works on simulated touch + desktop mouse
- [ ] `/battle/judge` — tablet portrait (768px): wider layout
- [ ] `/battle/judge` — tablet landscape (1024×768): shorter header, taller tie row
- [ ] `/battle/judge` — ✕ chip button tappable on first touch
- [ ] `/event/audition-list` (Emcee) — mouse drag on "Now" card navigates rounds
- [ ] `/event/audition-list` (Emcee) — touch swipe still works (device emulation)
- [ ] `/event/select` — long event names wrap on 375px
- [ ] `/event/select` — 1-col fallback at 360px
- [ ] Desktop layouts unchanged on all four views

- [ ] **Step 6.3: Push and open PR**

```bash
git push -u origin feat/mobile-touch-responsiveness
gh pr create \
  --title "feat: mobile & tablet responsiveness improvements (issue #59)" \
  --body "$(cat <<'EOF'
## Summary
- **BattleControl**: Replace HTML5 DnD with Pointer Events API — bracket seeding and 7-to-smoke queue now work on touch devices
- **BattleJudge**: Tablet portrait expands to 640px max-width; landscape gets shorter header + taller tie row; ✕ and SELECT JUDGE buttons meet 44px tap target
- **EmceeRoundView**: Replace touch-only swipe handlers with Pointer Events — desktop mouse and trackpad can now navigate rounds
- **EventSelector**: Long event names wrap in 2-col grid; fallback to 1-col at ≤360px; top margin reduced on small screens

## Test plan
- [ ] BattleControl bracket drag-and-drop works on iOS Safari / Android Chrome (use DevTools device emulation)
- [ ] BattleControl 7-to-smoke queue reorder works on touch
- [ ] Desktop drag-and-drop in BattleControl still works with mouse
- [ ] BattleJudge portrait layout is wider on tablet viewport (768px+)
- [ ] BattleJudge landscape layout on tablet (1024×768): header shorter, tie row taller
- [ ] EmceeRoundView swipe works with mouse click-drag on desktop
- [ ] EmceeRoundView swipe still works on touch (device emulation)
- [ ] EventSelector long names don't overflow at 375px
- [ ] All Vitest tests pass (`npm test -- --run`)

Closes #59

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```
