# Nav + Events Restructure Design

**Issue:** #60  
**Date:** 2026-06-04  
**Status:** Approved for implementation

## Problem

Users need multiple clicks to reach any event section from anywhere in the app. The Events page and the navbar event chip duplicate navigation concerns. On mobile the hamburger is heavy and slow for Emcees/Judges who only need 1–2 sections. The event chip disappears entirely when no event is active, leaving users with no clear next step.

## Solution: Slide-Over Event Panel

Replace the existing dropdown with a right-side slide-over panel opened by the event chip. The panel is role-filtered, has large touch targets for mobile, and includes an inline event switcher for Admin/Organiser. Remove "Events" from the primary nav entirely.

## Approach

**Chosen: Slide-over panel (Option B)**  
Rejected: enhanced dropdown (good UX for desktop but poor mobile touch targets), persistent sub-bar (eats screen real estate on every page).

## Design

### 1. Navbar chip — revised behaviour

**Active event:** chip shows truncated event name + chevron. Clicking toggles the panel.

**No active event:** chip shows `Select Event →` in accent-outlined style (not solid). Clicking navigates to EventSelector. This replaces the current behaviour where the chip disappears entirely.

**Primary nav center:** `Home · Admin` (Admin only) — "Events" removed for all roles. The panel is the new entry point for event navigation.

**Mobile hamburger:** now only contains `Home · Admin`. Much lighter than today — the event panel is opened separately via the chip, not via the hamburger.

### 2. Slide-over panel

**Layout:** slides in from the right, overlaying page content. A semi-transparent dark backdrop covers the rest of the screen.

**Width:** 280px on desktop; 100% viewport width on mobile.

**Close triggers:** clicking any section tile (navigates then closes), clicking the backdrop, pressing Escape, or route change (already wired in `App.vue`).

**Panel header:** current event name (prominent, Anton SC) + close button (✕) top-right.

**Section tiles:** 2-column parallelogram grid with icon + label. Clicking a tile calls `router.push` to the relevant route and closes the panel.

**Role-filtered tile sets:**

| Tile | Admin | Organiser | Emcee | Judge |
|------|-------|-----------|-------|-------|
| Details (⚙) | ✓ | ✓ | — | — |
| Audition (≡) | ✓ | ✓ | ✓ | ✓ |
| Participants (👥) | ✓ | ✓ | — | — |
| Score (📊) | ✓ | ✓ | ✓ | — |
| Battle (⚡) | ✓ | ✓ | — | ✓ |
| Numbers (#) | ✓ | ✓ | — | — |

Tile height: minimum 56px to meet mobile touch target requirements.

**Manage Events zone (Admin + Organiser only):** rendered below a section divider at the bottom of the panel. Contains:
- Section label: `MANAGE EVENTS`
- Search input (filters the list client-side by event name)
- Scrollable list of all events — clicking any entry calls `setActiveEvent()`, updates the panel header to show the new event name, and **keeps the panel open** so the user can immediately tap a section tile. Panel does not close or navigate.
- Active event is visually marked (accent dot or weight)
- "All Events →" link below the list navigates to `/events` and closes the panel

Emcee/Judge panels show a plain `Change Event` text link at the bottom instead of this zone (routes to EventSelector).

### 3. Events page (`/events`)

The page stays. It remains the best way to browse the full event grid and access card actions (create, edit access code).

Access path changes:
- **Previously:** "Events" top-level nav item
- **Now:** "All Events →" link at the bottom of the Manage Events zone in the panel (Admin + Organiser only) — navigates to `/events` and closes the panel

No changes to `Events.vue` or `EventCard.vue` are required by this spec.

### 4. Animation

Panel enter: slide-in from right, `transform: translateX(100%) → translateX(0)`, 200ms `ease-out`.  
Panel leave: reverse, 150ms `ease-in`.  
Backdrop enter: `opacity: 0 → 0.5`, 200ms. Leave: reverse, 150ms.

## Files to Change

| File | Change |
|------|--------|
| `App.vue` | Replace dropdown with slide-over panel component/section; update chip to show "Select Event →" when no event active; remove "Events" from primary nav and mobile hamburger; wire backdrop + Escape close |
| `EventCard.vue` | No changes |
| `Events.vue` | No changes |
| `router/index.js` | No changes |

The panel markup can live inline in `App.vue` (it is already a large component) or be extracted to `components/EventPanel.vue` — either is acceptable. Prefer extraction if the panel template exceeds ~80 lines.

## Acceptance Criteria

- [ ] "Events" no longer appears in the primary nav or mobile hamburger for any role
- [ ] Event chip opens a slide-over panel on both desktop and mobile
- [ ] Panel tiles are role-filtered per the table above
- [ ] Panel tile height is ≥ 56px (mobile touch target)
- [ ] Admin/Organiser panel shows a searchable event list that switches the active event
- [ ] Emcee/Judge panel shows "Change Event" link at the bottom
- [ ] No active event → chip shows "Select Event →" and routes to EventSelector
- [ ] Backdrop click, Escape key, and route change all close the panel
- [ ] Panel slides in/out with the specified animation
- [ ] Events page remains accessible via the panel's "All Events" link (Admin/Organiser)
- [ ] Mobile hamburger only contains Home + Admin links

## Out of Scope

- Account management / per-organiser event scoping (tracked in issues #39, #40)
- Any changes to the Events page card layout or EventCard actions
- Emcee/Judge session-link access model (future work)
