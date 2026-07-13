# Frontend Design System — Cinematic Battle

> Loaded on demand via CLAUDE.md routing. **Read before any UI work**: new components, styling, layout, modals, nav changes. Full spec: `docs/superpowers/specs/2026-05-30-ui-overhaul-design.md`. Last verified: 2026-07-03. Token values change — when in doubt, check `base.css` `@theme`, not remembered hex values.

## Design language

The entire app uses a unified cinematic aesthetic inspired by `BattleOverlay.vue` and `BracketVisualization.vue`. Every screen should feel like part of the same broadcast production system.

**Do NOT change** `BattleOverlay.vue`, `BracketVisualization.vue`, or `Chart.vue` — they have their own bespoke CSS and are the reference point, not the target.

## Accent color (runtime-configurable)

- CSS custom property `--accent-color` — set on `<html>` at runtime via `App.vue`
- Dark mode default: `#ffffff` (white/silver)
- Light mode override: `rgba(0,0,0,0.78)` — set via `[data-theme="light"]` CSS, ignores server value
- Derived tokens (auto-computed): `--accent-muted` = `color-mix(in srgb, var(--accent-color) 25%, transparent)` · `--accent-subtle` = 7% mix
- Utility classes: `.text-accent` · `.bg-accent` · `.border-accent` · `.glow-accent`
- Admin changes the color globally on `AdminPage.vue` → `AppConfig` DB table → `/topic/app-config` WebSocket → all clients update live

## Red (`primary-*`) — error/danger ONLY

Red is **not a brand color**. Reserved exclusively for error states, destructive actions, warning indicators. Never use `primary-*` for buttons, links, nav, or interactive chrome. (History: the app was red-primary Tesla-style Apr–May 2026; that palette is dead — don't resurrect it from old code or memory.)

## Semantic state pattern (left border + glowing dot)

All alert banners, warning chips, status callouts use:
- 3px solid left border in the semantic color (red / amber / green)
- Subtle tint background (10–12% opacity)
- Glowing dot indicator (same color with `box-shadow` glow)
- Never full solid fills or low-opacity tints alone — both were hard to read

## Light/dark mode — the #1 recurring UI bug

`text-white` on a `bg-surface-*` element becomes invisible in light mode (light mode remaps surfaces to whites). Rules:
- Readable text in both modes → `text-content-primary`, never `text-white`
- Modal backdrops → `bg-black/80`, never `bg-surface-*/80`
- **Check every UI change in both modes before calling it done.** Current token values live in `base.css` `@theme`.

## Typography — Oswald (chrome) + Inter (prose)

As of #141:
- `--font-sans` = `'Oswald'` (self-hosted latin 500) — default body/chrome font
- `--font-body` = `'Inter'` — prose, descriptions, helper subcopy
- Anton SC survives only as fallback in the 3 untouched broadcast files — do not reintroduce elsewhere
- **Inputs never force uppercase.** User-typed data (participant/team/judge/event names, criterion labels) reads **as typed** everywhere
- **Chrome labels stay capitalised** (section headers, buttons, badges, "VS", "UP NEXT"). Rule of thumb: label → uppercase; user data or prose → leave case alone

Tiers in `base.css`:

| Tier | Font | Use |
|------|------|-----|
| `.type-prose` / `.type-prose-sm` | Inter 12px sentence case | Every helper / description / explanatory subcopy |
| `.type-name` / `.type-name-sm` | Oswald sentence case | Every user-typed name (participant, team, member, judge, division, event, criterion) |
| `.section-rule-lg` | — | Larger section subheaders |
| `.tab-item-data` | — | Tab item with attached data badge |

Legacy chrome tiers (Oswald):

| Tier | Size | Letter-spacing | Use |
|------|------|----------------|-----|
| Display | `clamp(36px,5vw,56px)` | `0.06em` | Page heroes, login |
| Page title | `28px` | `0.06em` | View `h1` headings |
| Section header | `13px` | `0.18em` | Group labels + rule line |
| Body / row | `13px` | `0.05em` | Table rows, cards, form values |
| Label | `10px` | `0.22em` | Field labels, badges, breadcrumbs |
| Number / stat | `42px` | `0.02em` | Scores, counts, audition numbers |

Text shadows use `var(--accent-muted)`: Display `2px 2px 0`, Page title `1px 1px 0`, others none.

## Shape — parallelogram (universal)

All cards, list rows, stat boxes, badges use parallelogram clip-path in both modes:
```css
clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%)
```
No `rounded-2xl` cards or `rounded-full` badges. `.card` and `.badge-*` utilities in `base.css` use this shape.

## Modal standard (post-#126 / #130)

All modals (`FeedbackPopout`, `ScoringCriteriaModal`, `CreateParticipantForm`, EventDetails Category Entries / Add Division) share a slide-up shell:
- `bg-black/80` backdrop (no bleed-through)
- `pb-6` mobile bottom padding (clears browser chrome)
- **No header X button** — backdrop click closes; show "Tap outside to close" hint
- Header is icon + badge; rows use `.para-chip-sm` parallelogram tag chips
- `<Teleport>` for portal rendering
- Save state = centered status text (`Saving…` / `Feedback saved`), not a Done button

## Six cinematic chrome elements

| Element | Where | Notes |
|---------|-------|-------|
| Scanlines | `App.vue` global overlay | Always on; 50% opacity in light mode |
| Parallelogram chips | All cards, rows, badges | Core shape of every UI element |
| Corner accent bars | Panels, modals only | Top-left + bottom-left bars only (not all 4 corners) |
| Section rules | Every section header | Label + `flex:1` hairline — replaces `border-b` dividers |
| Color bleed | Page root only | Radial gradients from bottom corners using `--accent-subtle`; hidden in light mode |
| Topbar | `App.vue` navbar | Oswald, parallelogram nav chips, glowing dot |

## Surface scale

| Token | Value | Use |
|-------|-------|-----|
| `surface-900` | `#111111` | Page base |
| `surface-800` | `#1a1a1a` | Cards, panels |
| `surface-600` | `#2c2c2c` | Borders, dividers |
| Chip fill | `rgba(255,255,255,0.04)` | Parallelogram background |
| Chip border | `rgba(255,255,255,0.07)` | Parallelogram border |

## Navigation architecture

**Navbar** (`App.vue`): 3-column grid — Logo (left) · Primary nav (center) · Utilities (right).
- Primary nav (center): Home · Events · Admin only — kept minimal
- **Event chip** (right): active-event dropdown doubling as section nav — Audition, Participants, Scoreboard, Battle links (role-filtered) + "Change Event". **Do NOT add event-specific pages back as top-level nav items.**
- Event switching: `changeEvent()` always passes `?redirect=<currentPath>` so EventSelector returns the user to the same page

**EventCard** (`components/EventCard.vue`):
- Hover (desktop) / tap (mobile) reveals an absolute-positioned action panel that overlaps content below without affecting grid layout
- `expandedId` state is lifted to `Events.vue` — only one card expands at a time; cards emit `toggle`. **Do not move this state back into the card.**
- Action buttons: Details → `/events/:name`; Audition/Participants/Score/Battle → `setActiveEvent()` then navigate

## Target-audience UX rules

- **Judges**: large touch targets (min 48px), Number/stat tier for scores, minimal steps to submit
- **Emcees**: clear status badges, always-visible participant count
- **Organisers**: scannable stat cards, collapsible complexity, clear workflow steps

## Done-checklist for any UI change

1. Both dark AND light mode checked (screenshot or manual toggle)
2. Mobile viewport checked (judges/emcees are on phones/tablets)
3. No raw hex colors — tokens/utilities only; no red for non-error chrome
4. User-typed data not uppercased; chrome labels uppercased
5. Parallelogram shape + type tiers used, not rounded/bespoke styles
6. If it's a modal → matches the modal standard above
