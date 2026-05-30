# BES UI Overhaul — Design Spec
**Date:** 2026-05-30  
**Status:** Approved  

---

## Goal

Unify the entire BES webapp under a single cinematic design language inspired by the existing `BattleOverlay.vue` and `BracketVisualization.vue` views. Every screen — from Login to AdminPage — should feel like it belongs to the same broadcast production system. The three already-redesigned views (`BattleOverlay`, `BracketVisualization`, `AuditionList`) serve as the reference point; all other views are brought into alignment with them.

---

## Design Decisions Summary

| Decision | Choice | Rationale |
|---|---|---|
| Design direction | Cinematic Battle | Extend battle views' aesthetic across all screens |
| Font strategy | Full Anton SC everywhere | All text tiers use Anton SC; letter-spacing + opacity replace font-weight |
| Background base | Neutral black `#111111` | Easier to read on long admin sessions vs navy `#060818` |
| Chrome intensity | Full broadcast | Scanlines, parallelogram chips, corner bars, color bleeds, section rules |
| Semantic contrast | Left border + glowing dot | Strong enough to command attention without overpowering |
| Primary accent | White/silver default, admin-configurable | Red conflicts with error states; accent is a shared runtime config |
| Light mode | Kept | Accent auto-inverts to `rgba(0,0,0,0.78)` in light mode |

---

## 1. Color & Token System

### Accent token (new)
- **CSS custom property:** `--accent-color` — set at `<html>` level at runtime
- **Dark mode default:** `rgba(255, 255, 255, 0.88)` (white/silver)
- **Light mode override:** `rgba(0, 0, 0, 0.78)` (set via `[data-theme="light"]` CSS, ignores server config)
- **Accent is stored as an opaque hex value** (e.g. `#ffffff`). Alpha variants are computed in CSS via `color-mix`, not JS. The default white/silver is `#ffffff`.
- **Derived tokens** (CSS custom properties, computed from `--accent-color`):
  - `--accent-muted: color-mix(in srgb, var(--accent-color) 25%, transparent)`
  - `--accent-subtle: color-mix(in srgb, var(--accent-color) 7%, transparent)`
  - Both are set once in `base.css` as default values; they update automatically when `--accent-color` changes at the `<html>` level.
- **New utility classes** added to `base.css`:
  - `.text-accent` → `color: var(--accent-color)`
  - `.bg-accent` → `background: var(--accent-color)`
  - `.border-accent` → `border-color: var(--accent-color)`
  - `.glow-accent` → `box-shadow: 0 0 14px var(--accent-muted)`

### Red (`primary-*`) — role change
- **Was:** Brand/interactive color (buttons, links, active states, focus rings)
- **Now:** Reserved exclusively for error, danger, and destructive action states
- All `bg-primary-500`, `text-primary-400`, `border-primary-500` usage on interactive/brand elements is replaced with `var(--accent-color)` equivalents
- Semantic error/danger usage of red is unchanged and intentional

### Semantic state styling (left border + glowing dot)
Applied to all alert banners, warning chips, status callouts:

```css
/* Error */
border-left: 3px solid #ef4444;
border: 1px solid rgba(239,68,68,0.2); /* top/right/bottom */
background: rgba(239,68,68,0.10);
/* dot: background #ef4444; box-shadow: 0 0 6px rgba(239,68,68,0.8) */

/* Warning */
border-left: 3px solid #f59e0b;
border: 1px solid rgba(245,158,11,0.2);
background: rgba(245,158,11,0.08);
/* dot: background #f59e0b; box-shadow: 0 0 6px rgba(245,158,11,0.8) */

/* Success */
border-left: 3px solid #34d399;
border: 1px solid rgba(52,211,153,0.2);
background: rgba(52,211,153,0.08);
/* dot: background #34d399; box-shadow: 0 0 6px rgba(52,211,153,0.8) */
```

### Surface scale (unchanged values)
| Token | Value | Use |
|---|---|---|
| `surface-950` | `#080808` | Body background |
| `surface-900` | `#111111` | Page base |
| `surface-800` | `#1a1a1a` | Cards, panels |
| `surface-700` | `#222222` | Elevated surfaces |
| `surface-600` | `#2c2c2c` | Borders, dividers |
| Parallelogram fill | `rgba(255,255,255,0.04)` | Chip background |
| Parallelogram border | `rgba(255,255,255,0.07)` | Chip border |

---

## 2. Typography System

All text uses **Anton SC**. Letter-spacing and opacity provide hierarchy — font-weight is not available (Anton SC is single-weight only). All text is `text-transform: uppercase`.

`--font-sans` in `base.css` is changed from `'Inter'` to `'Anton SC'`. Inter is retained as `--font-body` for any future contexts requiring it but is no longer the default.

**Important:** The `body` rule in `base.css` currently applies `leading-relaxed` (line-height 1.625). Anton SC is a display font with tightly packed glyphs — `leading-relaxed` creates excessive gaps. Change `body` line-height to `leading-tight` (1.25) and let individual tiers set their own `line-height: 1` or `line-height: 1.1` where needed.

### Type scale (6 tiers)

| Tier | Size | Letter-spacing | Opacity | Use |
|---|---|---|---|---|
| Display | `clamp(36px, 5vw, 56px)` | `0.06em` | 100% | Page heroes, login screen |
| Page title | `28px` | `0.06em` | 94% (`#f0f0f0`) | View `h1` headings |
| Section header | `13px` | `0.18em` | 55% | Group labels with rule line |
| Body / row | `13px` | `0.05em` | 75% | Table rows, cards, form values |
| Label | `10px` | `0.22em` | 35% | Field labels, badges, breadcrumbs |
| Number / stat | `42px` | `0.02em` | 100% | Scores, counts, audition numbers |

**Rule:** Wider letter-spacing = lower hierarchy. Size amplifies at Display and Page title only.

**Text shadows:**
- Display: `text-shadow: 2px 2px 0 var(--accent-muted)` (subtle lift)
- Page title: `text-shadow: 1px 1px 0 var(--accent-muted)`
- All others: none

**Light mode:** Same scale, text colors flip to dark (`#0d0d0d` / `#404040` / `#757575`).

---

## 3. Cinematic Chrome System

Six decorative elements with defined placement rules. Battle views (`BattleOverlay`, `BracketVisualization`, `Chart`) are **excluded** — they already have their own fully bespoke chrome and must not be touched.

### 1 — Scanlines
- **Where:** Global overlay on `App.vue` root layout div
- **CSS:** `repeating-linear-gradient(to bottom, transparent 0px, transparent 3px, rgba(0,0,0,0.045) 3px, rgba(0,0,0,0.045) 4px)`
- **Position:** `fixed; inset: 0; pointer-events: none; z-index: 9999`
- **Light mode:** Opacity reduced to `0.5×` (use `opacity: 0.5` on the element)

### 2 — Parallelogram chips
- **Where:** All cards, list rows, stat boxes, inline badges
- **CSS:** `clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%)`
- **Background:** `rgba(255,255,255,0.04)` | **Border:** `1px solid rgba(255,255,255,0.07)`
- **Replaces:** `.card` (rounded-2xl) and `.badge-*` (rounded-full) in **all modes** (dark and light) — the parallelogram shape is the universal card language of this design system, not a dark-mode-only feature
- **Light mode:** Background `rgba(0,0,0,0.03)` | Border `rgba(0,0,0,0.08)`

### 3 — Corner accent bars
- **Where:** Panels, modals, large section cards (not individual row chips)
- **CSS:**
  ```css
  /* top-left vertical */   position:absolute; top:0; left:0; width:2px; height:28%; background: linear-gradient(to bottom, var(--accent-color), transparent);
  /* bottom-left horizontal */ position:absolute; bottom:0; left:0; height:2px; width:30%; background: linear-gradient(to right, var(--accent-color), transparent);
  ```
- Use only top-left + bottom-left bars (not all four corners — too busy)

### 4 — Section rules
- **Where:** Every section header throughout all views
- **Pattern:** `[label text] ────────────────`
- **CSS:** Label at `10px / 0.22em / opacity 28%` + `flex:1; height:1px; background: rgba(255,255,255,0.07)`
- **Replaces:** `border-b border-surface-700` section dividers

### 5 — Color bleed
- **Where:** Page root background only (not individual cards)
- **CSS:**
  ```css
  /* bottom-left */ radial-gradient(ellipse 50% 45% at 0% 100%, color-mix(in srgb, var(--accent-color) 7%, transparent), transparent 70%)
  /* bottom-right */ radial-gradient(ellipse 40% 40% at 100% 100%, color-mix(in srgb, var(--accent-color) 4%, transparent), transparent 70%)
  ```
- **Light mode:** Hidden (`display: none` or `opacity: 0`)

### 6 — Navbar / Topbar
- **Where:** `App.vue` navbar
- **Layout:** BES wordmark + glowing dot (left) · Nav links as parallelogram chips (center) · Active event + role (right)
- **Active nav link:** bottom `2px` border using `var(--accent-color)` + subtle glow
- **Font:** Full Anton SC (Label tier for nav items, Body tier for wordmark)
- **Glowing dot:** `width:6px; height:6px; border-radius:50%; background: var(--accent-color); box-shadow: 0 0 8px var(--accent-muted)`

---

## 4. Accent Color Config System

### Backend (new)

**Entity:** `AppConfig`
- Table: `app_config`
- Fields: `id` (PK), `key` (varchar, unique), `value` (text)
- Seed row: `('accentColor', 'rgba(255,255,255,0.88)')`

**Migration:** `V19__add_app_config.sql`

**Controller:** `AppConfigController` at `/api/v1/config/app`
- `GET /api/v1/config/app` — public (no auth), returns `{ accentColor: String }`
- `POST /api/v1/config/app` — admin only, body `{ accentColor: String }`, saves to DB then broadcasts via WebSocket

**WebSocket:** broadcasts to `/topic/app-config` with payload `{ accentColor: String }` on every POST

### Frontend

**`App.vue` on mount:**
```js
const cfg = await getAppConfig()               // GET /api/v1/config/app
applyAccent(cfg.accentColor)
subscribeToChannel(client, '/topic/app-config', (msg) => applyAccent(msg.accentColor))

function applyAccent(color) {
  // color is always a hex string (e.g. '#ffffff') — alpha variants are computed by CSS color-mix
  document.documentElement.style.setProperty('--accent-color', color)
  // --accent-muted and --accent-subtle are defined in base.css using color-mix(in srgb, var(--accent-color) ...)
  // so they update automatically — no JS derivation needed
}
```
Light mode CSS overrides `--accent-color` via `[data-theme="light"]` rule — no JS needed.

**`AdminPage.vue` — color picker section:**
- Section header: "Theme Config"
- Current color swatch (large parallelogram chip)
- 6 preset swatches: white/silver, gold, cyan, purple, orange, emerald
- Native `<input type="color">` for custom hex
- "Apply to All Clients" button → `POST /api/v1/config/app`
- Live preview panel showing the accent applied to a sample view

---

## 5. Light Mode Adaptations

Light mode is toggled via `[data-theme="light"]` on `<html>` (existing behavior).

| Element | Dark | Light |
|---|---|---|
| `--accent-color` | `rgba(255,255,255,0.88)` | `rgba(0,0,0,0.78)` — CSS override |
| `--accent-muted` | `rgba(255,255,255,0.25)` | `rgba(0,0,0,0.22)` |
| `--accent-subtle` | `rgba(255,255,255,0.07)` | `rgba(0,0,0,0.06)` |
| Scanlines | `opacity: 1` | `opacity: 0.5` |
| Color bleed | Visible | `opacity: 0` |
| Parallelogram fill | `rgba(255,255,255,0.04)` | `rgba(0,0,0,0.03)` |
| Anton SC text | `#f0f0f0` / opacity-based | `#0d0d0d` / opacity-based |
| Corner bars | White gradients | Dark gradients |

---

## 6. Scope — Files to Update

### `base.css` (design token layer — do first)
- Change `--font-sans` to `'Anton SC'`
- Add `--accent-color`, `--accent-muted`, `--accent-subtle` defaults
- Add `.text-accent`, `.bg-accent`, `.border-accent`, `.glow-accent` utilities
- Update `.card`, `.card-hover` to use parallelogram clip-path
- Update `.badge-*` to parallelogram + new accent/semantic styles
- Update `.input-base` to Anton SC
- Add `.section-rule`, `.para-chip`, `.corner-bar`, `.semantic-chip` utilities
- Update `[data-theme="light"]` block for new accent overrides + scanline/bleed adjustments

### `App.vue`
- New cinematic navbar (Anton SC, parallelogram nav chips, glowing dot)
- Global scanlines overlay div
- Fetch + subscribe to `/topic/app-config` on mount, apply `--accent-color`

### Views to update (all follow token system once `base.css` is done)
| View | Key changes |
|---|---|
| `Login.vue` | Display-tier "BES" hero, parallelogram form fields, accent border-left on form panel |
| `MainMenu.vue` | Quick-action cards → parallelogram chips, section rules, color bleed on page |
| `Events.vue` | Event list rows → parallelogram chips, section rules |
| `EventCard.vue` | Card shape → parallelogram, corner bars, accent glow on hover |
| `EventDetails.vue` | Section headers → section rules, stat boxes → parallelogram |
| `UpdateEventDetails.vue` | Table rows → parallelogram chips, semantic state badges → left-border style |
| `Score.vue` | Podium numbers → Number/stat tier, leaderboard rows → parallelogram |
| `BattleControl.vue` | Section rules, bracket seed slots → parallelogram |
| `AdminPage.vue` | Add Theme Config section with color picker |
| `EventSelector.vue` | Event grid cards → parallelogram with corner bars |
| `Results.vue` / `ResultsQR.vue` | Parallelogram result rows, Display-tier for scores |
| `AuditionList.vue` | Context bar → parallelogram, filter chips → parallelogram |
| `ActionDoneModal.vue` | Modal → corner bars, semantic chip styling |
| Components: `ReusableDropdown`, `SwipeableCardsV2`, `PairScoreCards`, `EmceeRoundView`, `FeedbackPopout`, `Timer`, `MiniScoreMenu` | Parallelogram shapes, Anton SC labels, section rules |

### Do NOT change
- `BattleOverlay.vue` — already correct, has its own bespoke CSS system
- `BracketVisualization.vue` — already correct, has its own bespoke CSS system
- `Chart.vue` — already correct
- `BattleJudge.vue` — battle screen, review separately
- All WebSocket / API / backend logic files (this is purely a UI overhaul)

---

## 7. Migration Order

1. **`base.css`** — tokens, utilities, light-mode overrides (everything inherits from here)
2. **`App.vue`** — navbar + scanlines + accent config fetch/subscribe
3. **Backend** — `AppConfig` entity, migration, controller, WebSocket broadcast
4. **High-visibility views** — `Login`, `MainMenu`, `EventSelector`, `Events`
5. **Event management views** — `EventDetails`, `UpdateEventDetails`, `AuditionList`, `Score`
6. **Battle management** — `BattleControl`
7. **Admin + results** — `AdminPage` (with color picker), `Results`, `ResultsQR`
8. **Components** — shared components updated last (they inherit most styles from tokens)
