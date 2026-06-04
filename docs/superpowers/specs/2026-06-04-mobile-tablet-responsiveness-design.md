# Mobile & Tablet Responsiveness — Design Spec

**Issue:** #59
**Branch:** `feat/mobile-touch-responsiveness`
**Date:** 2026-06-04

## Summary

Four targeted fixes to make BES usable on phones and tablets:

1. **BattleControl** — replace HTML5 drag-and-drop (touch-incompatible) with pointer events for bracket seeding and 7-to-smoke queue reordering
2. **BattleJudge** — responsive tablet layout (portrait + landscape) with larger tap targets
3. **EmceeRoundView** — replace touch-only swipe handlers with pointer events so desktop mouse/trackpad swipe works
4. **EventSelector** — prevent long event names from overflowing in the 2-column grid on narrow phones

---

## 1 — BattleControl: Touch-Compatible Drag-and-Drop

### Problem

BattleControl uses the HTML5 `draggable` / `dragstart` / `dragover` / `drop` API. This API does not fire on touch screens (iOS Safari, Android Chrome). The bracket seeding drag-and-drop and the 7-to-smoke queue reordering are both broken on mobile.

### Approach

Replace HTML5 drag handlers with a pointer events implementation. No new library dependency.

### Implementation

**Shared drag state (new refs):**
```js
const pointerDragType   = ref(null)   // 'pool' | 'bracket' | 'smoke' | null
const pointerDragName   = ref(null)   // string | null (for pool drags)
const pointerDragSource = ref(null)   // { roundKey, matchIdx, slotIdx } | { smokeIdx } | null
const pointerGhost      = ref(null)   // DOM element | null
```

**`data-drop-key` attributes on drop targets (template):**
- Bracket slots: `data-drop-key="bracket-{roundKey}-{matchIdx}-{slotIdx}"` (e.g. `bracket-Top8-0-1`)
- Smoke slots: `data-drop-key="smoke-{idx}"` (e.g. `smoke-3`)

**`data-drag-name` attribute on pool name chips:**
- Pool chips: `data-drag-name="{name}"` — read on pointerdown to identify what is being dragged

**Drag lifecycle (document-level pointermove/pointerup):**

1. `pointerdown` on a draggable element:
   - Create ghost `<div>` (same styling as existing HTML5 ghost: dark chip, red border for bracket slots, white border for pool chips)
   - Append ghost to `document.body` at pointer position
   - Set `touch-action: none` on the dragged element during drag to prevent scroll interference
   - Set `pointerDragType`, `pointerDragName` / `pointerDragSource`
   - Register `pointermove` and `pointerup` listeners on `document`

2. `pointermove` on document:
   - Move ghost to `(e.clientX + 12, e.clientY + 12)` (offset so finger doesn't cover the ghost)
   - Call `document.elementFromPoint(e.clientX, e.clientY)`, walk up via `.closest('[data-drop-key]')` to find the current drop target
   - Set `dragOverKey` from the found key (drives existing hover highlight CSS — no template changes needed)

3. `pointerup` on document:
   - Remove ghost from DOM
   - Resolve drop target via `elementFromPoint` + `closest('[data-drop-key]')`
   - Parse key and invoke existing `onDrop(roundKey, matchIdx, slotIdx)` or `onSmokeDrop(idx)` — zero logic changes
   - Clear all pointer drag state
   - Remove document-level `pointermove` / `pointerup` listeners

4. `pointercancel` on document: same cleanup as `pointerup`, no drop

**Removing HTML5 attributes:**
- Remove `draggable="true"` from all bracket slot elements, smoke slot elements, and pool name chips
- Remove all `@dragstart`, `@dragover`, `@dragleave`, `@drop`, `@dragend` bindings
- Replace with `@pointerdown` on each draggable element

**Touch scroll guard:**
- Add `touch-action: none` to draggable elements only while a drag is active (set/remove dynamically)
- Non-draggable areas of the page retain normal scroll

**Smoke queue (7-to-Smoke):**
- Same pointer events pattern; `data-drop-key="smoke-{idx}"` on each queue slot
- Pool → smoke slot and smoke slot → smoke slot swap both handled via existing `onSmokeDrop`

### No logic changes

`onDrop`, `onSmokeDrop`, `dragOverKey`, `dragSource`, `poolDragName` — all existing swap logic stays exactly as-is. Only the event plumbing changes.

---

## 2 — BattleJudge: Tablet Layout (Portrait + Landscape)

### Problem

BattleJudge caps at `max-width: 480px` on viewports ≥600px, wasting screen space on tablets. The judge chip "clear" (✕) button is ~10px — too small for reliable touch. No landscape adaptation exists.

### Approach

Expand max-width to 640px for tablet portrait. Add a landscape media query that makes the tie row taller for thumb reach. Fix tap target sizes.

### Implementation

**Portrait — wider layout (≥600px viewport width):**
```css
@media (min-width: 600px) {
  .judge-root {
    max-width: 640px;   /* was 480px */
    margin: 0 auto;
    border-left:  1px solid rgba(255,255,255,0.05);
    border-right: 1px solid rgba(255,255,255,0.05);
  }
}
```

**Landscape — tablet-optimised (≥600px width + landscape orientation):**
```css
@media (min-width: 600px) and (orientation: landscape) {
  .judge-header {
    height: 44px;         /* shorter header frees space for panels */
  }
  .tie-row {
    height: 26%;          /* taller tie row — reachable with thumbs at bottom */
    min-height: 72px;
  }
}
```

The LR panels (`.lr-row`) remain `flex: 1` and fill remaining height naturally. No structural template changes required.

**Tap target fixes:**

Judge chip clear button — minimum 44×44px tap area via padding:
```css
.judge-chip-clear {
  padding: 10px 8px;      /* was: 0 2px */
  margin: -10px -8px;     /* negative margin keeps visual size unchanged */
  min-width: 44px;
  min-height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
}
```

SELECT JUDGE button — increase padding for easier tapping:
```css
.pick-judge-btn {
  padding: 8px 16px;      /* was: 5px 12px */
}
```

Sheet name chips (judge picker bottom sheet) — already `padding: 14px 8px`, no change needed.

---

## 3 — EmceeRoundView: Desktop Swipe (Mouse + Trackpad)

### Problem

The "Now on Stage" card in `EmceeRoundView.vue` only listens to `touchstart` / `touchmove` / `touchend`. Desktop users (emcees at a laptop, or an emcee using a USB trackpad) cannot swipe through rounds.

### Approach

Replace the three touch-only event handlers with pointer event equivalents. Pointer events unify touch, mouse, and trackpad input.

### Implementation

**Handler renames:**

| Remove | Add |
|--------|-----|
| `@touchstart.passive="onTouchStart"` | `@pointerdown="onPointerDown"` |
| `@touchmove.passive="onTouchMove"` | `@pointermove="onPointerMove"` |
| `@touchend="onTouchEnd"` | `@pointerup="onPointerUp"` |

Add `@pointercancel="onPointerUp"` to handle mid-gesture cancellation (e.g. incoming call on mobile).

**Handler changes:**

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

const onPointerUp = (e) => {
  if (!isDragging.value) return
  isDragging.value = false
  if      (dragOffset.value < -60) goNext()
  else if (dragOffset.value >  60) goPrev()
  dragOffset.value = 0
}
```

`setPointerCapture` ensures `pointermove` and `pointerup` are delivered to the card even when the pointer moves outside its bounds mid-swipe.

**CSS change on the card element:**
```
touch-pan-y  →  touch-action: none
```
`touch-pan-y` was used to allow vertical scroll while capturing horizontal swipe. With pointer capture, the browser no longer needs the hint — `touch-action: none` prevents the browser from intercepting horizontal swipes before our handler fires.

**Variable names unchanged:** `touchStartX`, `dragOffset`, `isDragging`, `swipeHint`, `cardStyle` — no other code needs to change.

---

## 4 — EventSelector: Narrow-Phone Grid Fix

### Problem

When 5+ events exist, EventSelector shows a `grid-cols-2` layout. On narrow phones (320–360px), each card is ~160px wide. Long event names (e.g. "KL Bboy Open 2026") can overflow or truncate unreadably inside the `p-5` card.

### Approach

Add `text-wrap: balance` and `min-h` so cards handle long names gracefully. On the narrowest phones (≤360px) fall back to a single column.

### Implementation

**Event card text:** add `break-words` / `overflow-wrap: break-word` to the event name element so long names wrap instead of clipping.

**Narrow phone breakpoint:** switch from 2-col to 1-col on very narrow screens:
```css
@media (max-width: 360px) {
  .event-grid-2col {
    grid-template-columns: 1fr;
  }
}
```
Applied via a conditional class or scoped style on the grid `<div>`. The `:class` binding already targets `events.length > 4` — add a `max-w-[360px]` Tailwind responsive override or use a scoped `<style>` block since EventSelector has no existing `<style scoped>`.

**Reduced card padding on small screens:** change `p-5` → `p-4` inside the 2-col grid only (via a Tailwind responsive modifier or scoped style targeting the 2-col case).

**`mt-10` top margin:** change to `mt-4 sm:mt-10` so the form doesn't sit too low on small phones.

---

## Acceptance Criteria (mirrors issue #59 + bundled fix)

- [ ] BattleControl bracket seeding drag-and-drop works on touch devices (iOS Safari, Android Chrome) — pool→slot and slot↔slot
- [ ] BattleControl 7-to-smoke queue reordering works on touch devices
- [ ] All interactive controls on BattleControl meet 48px minimum touch target (existing buttons reviewed — none added)
- [ ] BattleJudge is comfortable on tablet portrait (≥600px) with wider layout
- [ ] BattleJudge is comfortable on tablet landscape — tie row reachable with thumbs
- [ ] BattleJudge judge chip ✕ and SELECT JUDGE button meet 44px minimum tap target
- [ ] EmceeRoundView swipe works with mouse drag and trackpad swipe on desktop
- [ ] EmceeRoundView swipe still works on touch (pointer events cover both)
- [ ] EventSelector 2-col grid handles long event names without overflow on 320px phones
- [ ] No regressions on existing desktop layouts for any of the four views

## Files to Change

| File | Change |
|------|--------|
| `BES-frontend/src/views/BattleControl.vue` | Replace HTML5 DnD with pointer events; add `data-drop-key` attrs; add `data-drag-name` to pool chips |
| `BES-frontend/src/views/BattleJudge.vue` | Update `@media` breakpoints; fix tap targets |
| `BES-frontend/src/components/EmceeRoundView.vue` | Replace touch handlers with pointer event handlers |
| `BES-frontend/src/views/EventSelector.vue` | Fix 2-col grid overflow; add narrow-phone fallback |
