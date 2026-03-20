# BES Frontend Revamp Plan

## Overview

Full redesign of the BES (Battle Event System) frontend targeting three primary user groups:

- **Event Organisers** — need clear data overview, efficient workflows
- **Judges** — need minimal cognitive load, fast scoring, intuitive touch controls
- **Emcees** — need clear participant lists, quick lookup, real-time updates

### Design Principles
- **Audience-first:** Large scannable text for judges reading at a distance; clear status indicators for emcees; organised data hierarchy for organisers
- **Low cognitive load:** Hide complexity behind collapsible panels; one primary action visible at a time; progressive disclosure
- **Professional, not generic:** Deep navy + cyan brand identity; refined typography; purposeful animations only (no decorative blobs)
- **Responsive:** Mobile-first, optimised for tablet (judges likely on iPad) and desktop (organiser workflows)

### Design System
| Token | Value |
|-------|-------|
| Primary | Cyan `#06b6d4` |
| Primary Dark | `#0891b2 / #0e7490` |
| Accent | Orange `#f97316` (medals/rankings ONLY) |
| Surface Dark | Navy `#0f172a` |
| Surface Light | Off-white `#f8fafc` |
| Content | White `#ffffff` |
| Body Font | Inter (300–700) |
| Heading Font | Outfit (400–800) |
| Mono/Numbers | Source Code Pro |

> Design system is also codified in `CLAUDE.md` and `base.css @theme` block.

---

## Phase 1 — Foundation ✅ COMPLETE

> Reviewed. All decisions below are locked in for subsequent phases.

### 1.1 `assets/base.css` — Design System Tokens ✅
- [x] CSS custom properties: color palette, typography, surface scale
- [x] Refined color palette: navy `#0f172a` surface, white content, cyan primary, orange accent
- [x] Utility classes: `.card`, `.card-hover`, `.badge` (+ variants), `.input-base`, `.stat-card`, `.page-title`, `.text-muted`, `.page-container`
- [x] Keyframe animations: `fadeIn`, `slideDown`, `slideUp`, `scaleIn`, `pulse-soft`

### 1.2 `ReusableButton.vue` ✅
- [x] 5 variants: `primary` (cyan-600), `secondary` (navy), `ghost`, `danger`, `outline`
- [x] 3 sizes: `sm`, `md`, `lg`
- [x] Slot-based content + `buttonName` prop fallback

### 1.3 `ReusableDropdown.vue` ✅
- [x] Headless UI Listbox, chevron rotates on open, checkmark on selected
- [x] Cyan hover, `placeholder` prop

### 1.4 `ActionDoneModal.vue` ✅
- [x] `variant` prop: `info` / `success` / `error` / `warning`
- [x] `backdrop-blur-sm`, `animate-scale-in`, click-outside closes

### 1.5 `LoadingOverlay.vue` ✅
- [x] SVG spinner, floating white card, slot for message

### 1.6 `App.vue` ✅
- [x] Active nav: filled cyan pill
- [x] Role badge: single neutral style
- [x] Mobile: slide-down dropdown, click-outside backdrop
- [x] Admin link for ROLE_ADMIN

#### Colour Theory Correction (locked for all phases)
| Rule | Value |
|------|-------|
| 60% Neutral | `surface-*` — backgrounds, borders, text hierarchy |
| 30% Structure | `surface-800/900` + white — nav, table headers |
| 10% Brand | `primary-*` cyan — ALL interactive elements only |
| Orange | Accent/medals ONLY — never buttons/nav/badges |
| Role badges | All neutral `bg-surface-100 text-surface-600` |
| Button secondary | `bg-surface-800` dark navy |
| Page bg | `bg-surface-50` |
| Cards | `.card` utility (white, rounded-2xl, border-surface-200/80) |

---

## Phase 2 — Core Pages ✅ COMPLETE

### 2.1 `Login.vue` ✅
- [x] Split layout: dark navy left panel (dot-grid pattern) + white right form
- [x] Password show/hide toggle
- [x] `ActionDoneModal` for errors, `modalVariant` for error/success

### 2.2 `MainMenu.vue` ✅
- [x] Role badge + welcome heading
- [x] Role-filtered quick-action cards (icon + title + desc + hover arrow)
- [x] Collapsible `<details>` How-to-Use accordion with original content preserved

### 2.3 `EventCard.vue` ✅
- [x] White card, `border-l-[4px] border-l-primary-500`
- [x] Chevron in rounded circle, `font-heading font-bold`
- [x] Removed old orange triangle `::before` animation

### 2.4 `Events.vue` ✅
- [x] Page header with event count badge
- [x] Search input with `input-base`, computed `filtered()`
- [x] 4-col responsive grid, empty state

### 2.5 `EventDetails.vue` ✅
- [x] 3-col stat cards: Total / Verification / Registered
- [x] Genre accordion (click to expand unregistered participants)
- [x] AuditionNumber embedded in card (restyled — no orange)
- [x] Setup section (when no DB table) with checkbox genre picker
- [x] Refresh button top-right

### 2.6 `UpdateEventDetails.vue` ✅
- [x] Page header + "Add Participant" button top-right
- [x] Filter card: Event + Genre dropdowns + participant count
- [x] DynamicTable below, "Save Judge Assignments" bottom-right
- [x] Empty state when no participants

### 2.7 `DynamicTable.vue` ✅
- [x] Header: `bg-surface-800 text-white` (replaced orange)
- [x] Rows: white + `even:bg-surface-50` + `hover:bg-primary-50`
- [x] Toggle switch for boolean, `input-base` for editable cells
- [x] Cyan link cells, empty state row

---

## Phase 3 — Event-Day Tools ✅ COMPLETE

### 3.1 `AuditionList.vue` ✅
- [x] Page header + action toolbar (Filters / Jump / Submit / Reset)
- [x] Filter card: Role / Genre / Event / Judge dropdowns, collapsible
- [x] Judge role: "You are judging as" selector
- [x] Emcee view: Timer (sticky) + DynamicTable
- [x] Judge view: SwipeableCardsV2
- [x] Empty states for both no-role and no-participants cases

### 3.2 `SwipeableCardsV2.vue` ✅
- [x] Active card white/border-primary, inactive 70% opacity
- [x] Whole number grid (1–9) + decimal grid (.1–.9)
- [x] "10 — Full Score" button (outline cyan, fills on hover)
- [x] ±0.1 fine-tune buttons
- [x] Dot progress indicator below cards

### 3.3 `Timer.vue` ✅
- [x] Compact card, progress bar, inline preset buttons (30/45/60/90s)
- [x] Color shifts red near end, "Done" state in primary cyan

### 3.4 `MiniScoreMenu.vue` ✅
- [x] Grid layout inside ActionDoneModal
- [x] Cyan hover per row, score preview (— if unscored, value if scored)

### 3.5 `Score.vue` ✅
- [x] Filter card: Event + Genre + Group By
- [x] By Total: top-3 podium cards + full rankings table
- [x] By Judge: judge avatar + name header per table, participant count badge

---

## Phase 4 — Battle System & Admin ✅ COMPLETE

### 4.1 `BattleControl.vue` ✅
- [x] Config card: event/genre/format dropdowns + judge badge management
- [x] Bracket section: standard rounds or 7-to-Smoke layout
- [x] Live Match card: status badge, previous/current/next pair, action buttons
- [x] File upload as hidden input with label button

### 4.2 `BattleJudge.vue` ✅
- [x] Full-screen tri-section (blue-left / gray-tie / red-right) retained as semantic UI
- [x] Judge selector bar at top
- [x] Confirmation hint text appears on selected panel

### 4.3 `AuditionNumber.vue` ✅
- [x] Cyan `#` number, no orange
- [x] Slot animation with muted pulsing numbers
- [x] Clean typography hierarchy: genre label / name / number

### 4.4 `AdminPage.vue` ✅
- [x] 4 section cards: Judges / Genres / Reset Scores / Images
- [x] Inline add input + button per section
- [x] Pill items with edit (click name) + remove (× button) per row

### 4.5 Form Modals ✅
- [x] `CreateParticipantForm` — name input + genre checkboxes with cyan selected state
- [x] `UpdateScoreForm` — participant badge + score input + judge dropdown
- [x] `UpdateFieldForm` — single input, Enter key support, inline error modal

---

## Phase 5 — Polish ✅ COMPLETE

### 5.1 `ForbiddenPage.vue` ✅
- [x] Lock icon + "403" + "Access Denied" message
- [x] "Back to Home" primary button

---

## Implementation Status

| Phase | Status | Files |
|-------|--------|-------|
| 1 — Foundation | ✅ Done | base.css, Button, Dropdown, Modal, Loading, Navbar |
| 2 — Core pages | ✅ Done | Login, MainMenu, EventCard, Events, EventDetails, UpdateEventDetails, DynamicTable |
| 3 — Event-day tools | ✅ Done | AuditionList, SwipeableCards, Timer, MiniScoreMenu, Score |
| 4 — Battle + Admin | ✅ Done | BattleControl, BattleJudge, AuditionNumber, AdminPage, Form modals |
| 5 — Polish | ✅ Done | ForbiddenPage |

**Total files revamped: 27** (14 views + 12 components + 1 CSS file)
