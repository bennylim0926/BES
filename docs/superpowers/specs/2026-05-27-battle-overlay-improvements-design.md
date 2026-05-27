# BattleOverlay Improvements — Design Spec
_Date: 2026-05-27_

## Overview

Three improvements to `BES-frontend/src/views/BattleOverlay.vue` for the 1v1 standard battle mode:

1. **Picture toggle** — organiser can switch between image mode and name-focus mode live
2. **Animation overhaul** — underground/street aesthetic: hard cuts, physical impacts, no smooth easing
3. **UI redesign** — customizable colors, angular design language, transparent-aware layout

---

## 1. Architecture — Unified Overlay Config (Approach A)

A single new WebSocket topic carries all overlay settings from BattleControl to the overlay.

**New WebSocket topic:** `/topic/battle/overlay-config`

**Payload:**
```json
{
  "showImages": true,
  "leftColor": "#dc2626",
  "rightColor": "#2563eb"
}
```

**Backend changes:**
- New endpoint: `POST /api/v1/battle/overlay-config` — accepts the payload, broadcasts to `/topic/battle/overlay-config`, persists to DB
- New `overlay_config` JSONB column on `Event` table (see migration V17 in §5) — config survives OBS browser source refresh
- New `GET /api/v1/battle/overlay-config` — overlay fetches current config on mount (scoped to active event)

**BattleControl changes:**
- New "Overlay" settings panel (compact, collapsible) with:
  - Left color picker + hex input
  - Right color picker + hex input
  - Show Images toggle (on/off)
- Color changes broadcast on `change` event (not on every keypress) to avoid flooding WebSocket

**BattleOverlay changes:**
- Subscribe to `/topic/battle/overlay-config` on mount
- Fetch initial config via `GET` on mount
- Apply `--left-color` and `--right-color` as CSS custom properties on the root element
- Reactively switch between picture mode and name mode based on `showImages`

---

## 2. UI Design

### Layout system
- **Both panels** use `left`-only positioning internally (`left:0; width:46%` and `left:54%; width:46%`) — not `right:0` — so winner centering animations work correctly from either side
- **CSS custom properties** on `.overlay-root`: `--left-color` and `--right-color` drive all color treatments
- **Transparent background**: existing `transparent-page` class preserved; checkerboard only visible in dev/mockup

### Structural elements (both modes)
| Element | Description |
|---------|-------------|
| Diagonal slash divider | Thin skewed line at center, subtle white glow gradient |
| Corner accent bars | 3px vertical bars top-left and top-right, fade downward in respective team color |
| Bottom edge lines | 3px horizontal lines at bottom corners, fade inward in respective team color |
| Color bleeds | Soft diagonal color gradients from each corner, low opacity (~18%) |
| Scanlines | Repeating 4px pattern, `rgba(0,0,0,0.035)` — gritty CRT texture |

### No-picture mode
- Giant **Anton SC** name text, vertically centered in each half
- Left name: `text-align:left`, color shadow (`3px 3px 0 var(--left-color, 0.65)` + wide glow)
- Right name: `text-align:right`, color shadow using `--right-color`
- No role tag labels
- VS badge: hexagonal clip-path, centered, appears on entrance

### Picture mode
- Portrait image: waist-to-head crop (`aspect-ratio: 3/4`), bottom-anchored
- Name overlaid at bottom of image via absolute-positioned gradient overlay — name partially covers the waist
- Same large Anton SC font and color shadow as no-picture mode
- Lighter panel gradient (not the heavy bottom wash of the original)

### Judge cards
| State | Visual |
|-------|--------|
| Waiting (unvoted) | Frosted glass (`rgba(255,255,255,0.1)`), white border, gentle pulse animation |
| Voted left | Left-color glow, colored border, fill bar, lit left arrow |
| Voted right | Right-color glow, colored border, fill bar, lit right arrow |
| Voted tie | Amber glow, amber border, amber fill bar, both arrows lit |

Cards use a parallelogram clip-path (`clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%)`) for an angular shape. Cards are **hidden** (off-screen) during the voting phase and only revealed when Get Score is triggered.

---

## 3. Animation Overhaul

**Philosophy:** Hard cuts, not smooth transitions. Every moment lands like a DJ dropping a beat — no easing in, no fades, just impact.

### Entrance
1. Both name panels slam in simultaneously from their respective edges
   - Easing: `cubic-bezier(0.2, 0, 0.3, 1)` — fast attack, single tight overshoot at ~60% then settles
   - Duration: 450ms
2. VS badge rushes in from "in front of camera"
   - Scale: 6× → 0.72× (deep undershoot) → 1.10× → 1.0×
   - Duration: 520ms, easing `cubic-bezier(0.12, 0, 0.2, 1)`
   - Stage shake fires at ~340ms (when VS hits undershoot trough)

### During voting
- Judge panel remains **off-screen** (translated above viewport) — crowd sees nothing
- Subtle "Judges voting" indicator at bottom with pulsing red dot

### Score reveal (Get Score)
- Judge panel slams down from top (bounce easing, 480ms)
- All vote cards burst in simultaneously — staggered by only 55ms each so it reads as one hit
- Each card: scale punch from 1.4× with skew, settles to 1× (280ms)

### Winner announcement
1. Judge panel slides back up (300ms, hard ease-out)
2. VS scale-punches to 1.25× (brief brace) then rockets off toward **loser's side** with 55° spin, arcing slightly downward — duration 380ms
3. Loser panel hard-cuts off screen in the **opposite direction** (320ms)
4. Winner panel transitions: `left → 0`, `width → 100%`, name grows to `~6.5vw`, text-shadow deepens (420ms CSS transition)
5. WINNER tag stamps in at 300ms

### Next pair transition
1. Glitch overlay fires (380ms)
2. Winner panel hard-cuts off screen (delayed 100ms into glitch)
3. Full state reset (names, judges, VS, config)
4. New pair enters with fresh entrance sequence

---

## 4. Scope Boundary

This spec covers **standard 1v1 battle mode only** (`isSmoke = false`). Smoke mode layout is unchanged — it embeds `<Chart />` and has its own animation logic.

The BattleControl "Overlay" panel is new UI but intentionally minimal — two color pickers and one toggle. No additional BattleControl refactoring in scope.

---

## 5. DB Migration

New migration `V17__add_overlay_config.sql`:
```sql
ALTER TABLE event ADD COLUMN overlay_config JSONB DEFAULT '{"showImages": true, "leftColor": "#dc2626", "rightColor": "#2563eb"}';
```

Default value ensures backward compatibility — existing events get the original red/blue colors with images on by default.
