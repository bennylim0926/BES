# EmceeRoundView — Navigation Footer Redesign

**Issue:** #122
**Branch:** `ui/emcee-round-view-ux`
**File:** `BES-frontend/src/components/EmceeRoundView.vue`

## Problem

The card footer currently shows three non-interactive spans:

```html
<span>← Prev</span>
<span>swipe</span>
<span>Next →</span>
```

The arrow directions are misleading: `← Prev` implies swipe-left for previous, but swiping right goes to previous. `Next →` implies swipe-right for next, but swiping left advances. There are also no tap-to-navigate buttons, making single-handed phone use awkward for an emcee.

## Design

Replace the three spans with two `<button>` elements occupying the full card bottom.

### Buttons

| Button | Label | Action | Disabled when |
|--------|-------|--------|---------------|
| Left | `‹ PREV` | `goPrev()` | `currentRound <= 1` |
| Right | `NEXT ›` | `goNext()` | `currentRound >= totalRounds` |

### Layout

```
[ ‹ PREV          ] [ NEXT ›          ]
```

- Both buttons use `flex: 1` — equal width, filling the card bottom edge
- `min-height: 48px` — thumb-friendly tap target (matches UX rule for emcee/judge touch targets)
- `gap: 6px` between buttons, `padding: 6px 10px 10px` on container

### Styling

- Parallelogram clip-path: `polygon(5px 0%, 100% 0%, calc(100% - 5px) 100%, 0% 100%)`
- Background: `rgba(255,255,255,0.05)` — chip fill from design system
- Border: `rgba(255,255,255,0.08)` — chip border from design system
- Text: `type-label` tier (10px, Anton SC, 0.18em tracking, uppercase), `rgba(255,255,255,0.65)`
- Disabled: `opacity: 0.2`, `pointer-events: none`, `cursor: not-allowed`
- Active press: `background: var(--accent-muted)`, `color: var(--accent-color)`

### Pointer Event Isolation

Each button gets `@pointerdown.stop` to prevent the card's drag gesture (`onPointerDown` → `setPointerCapture`) from firing when the user taps a button. Swipe gesture on the card body remains fully functional.

## What Does Not Change

- Swipe gesture (pointer events on card) — unchanged
- Animated chevron overlays during drag — unchanged
- Card header (`Now on Stage` · `Rd X / Y`) — unchanged
- Queue section — unchanged
- Timer section — unchanged
- All other component logic and props — unchanged

## Acceptance Criteria

1. Tapping `PREV` navigates to the previous round; button is visually disabled on round 1
2. Tapping `NEXT` navigates to the next round; button is visually disabled on the last round
3. Swiping left/right still navigates (gesture unchanged)
4. No accidental navigation triggers when tapping buttons (pointer isolation works)
5. Buttons meet 48px minimum touch target height
