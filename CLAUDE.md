# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

**BES (Battle Event System)** — a full-stack web app for managing dance battle events (registration, judging, scoring, real-time battle control, results portal).

## Commands

### Backend (from `BES/` directory)

```bash
mvn clean package -DskipTests   # Build JAR
mvn spring-boot:run              # Run with hot reload (port 5050)
mvn test                         # Run all integration tests
mvn test -Dtest=AuthControllerIntegrationTest  # Run a single test class
```

### Frontend (from `BES-frontend/` directory)

```bash
npm install           # Install dependencies
npm run dev           # Dev server (http://localhost:5173)
npm run build         # Production build → dist/
npm test              # Run Vitest tests
npm run test:watch    # Watch mode
npm run test:coverage # With coverage
```

### Docker (from root)

```bash
docker-compose up --build --no-cache   # Build and start all services (always use --no-cache)
# backend → port 5050, frontend → ports 80/443, postgres → port 5433
```

### Environment Setup

Copy `.env.example` to `.env` and fill in values before running Docker. Required vars: `EMAIL`, `EMAIL_PASSWORD`, `SPRING_DATASOURCE_*`, `DOMAIN`.

## Architecture

```
Vue 3 frontend  →  Nginx (reverse proxy)  →  Spring Boot backend (port 5050)  →  PostgreSQL
```

- **`/api/*`** and **`/ws`** requests are proxied by Nginx to the backend
- **Authentication**: Session-based (Spring Security), not JWT — frontend always sends `credentials: 'include'`
- **WebSocket**: STOMP over `/ws` for real-time battle updates
- **Database migrations**: Flyway (`BES/src/main/resources/db/migration/V*.sql`), `ddl-auto=validate` — never auto-creates schema

### Backend Layout (`BES/src/main/java/com/example/BES/`)

| Package | Purpose |
|---------|---------|
| `controllers/` | 9 REST controllers under `/api/v1/` |
| `services/` | 21 business logic services |
| `respositories/` | Spring Data JPA repos |
| `models/` | 19 JPA entities → PostgreSQL tables |
| `dtos/` | ~40 request/response DTOs |
| `config/` | Security, CORS, WebSocket, Google API config |
| `mapper/` | Entity ↔ DTO converters |

**Controllers:**

| Controller | Base Path | Purpose |
|-----------|-----------|---------|
| `AuthController` | `/api/v1/auth` | Login, logout, session check |
| `EventController` | `/api/v1/event` | CRUD events, participants, scores, genres, scoring criteria, feedback, pickup crews |
| `BattleController` | `/api/v1/battle` | Battle mode, voting, judges, images, battle phase state machine |
| `AdminController` | `/api/v1/admin` | Admin CRUD for genres, judges, scores, feedback groups/tags |
| `ResultsController` | `/api/v1/results` | Public results by reference code, QR access |
| `SecurityController` | `/api/v1/security` | CSRF token endpoint |
| `GoogleDriveFileController` | `/api/v1/files` | Google Drive file listing |
| `GoogleDriveFolderController` | `/api/v1/folders` | Google Drive folder listing |
| `GoogleSheetsController` | `/api/v1/sheets` | Google Sheets participant import |

**Key Models:**

| Model | Purpose |
|-------|---------|
| `Event` | Event with access code, payment flag, judging mode, results release toggle |
| `EventParticipant` | Participant linked to event; supports walk-ins, stage name, team format, display name |
| `EventParticipantTeamMember` | Individual member rows for team entries |
| `EventGenre` | Genre linked to event with format field |
| `EventGenreParticipant` | Participant-genre junction with audition number |
| `Score` | Single overall score per judge/participant/genre |
| `ScoringCriteria` | Named weighted criteria per event/genre (custom scoring mode) |
| `AuditionFeedback` | Judge feedback per participant/genre with tags and note |
| `FeedbackTag` / `FeedbackTagGroup` | Configurable tag taxonomy for feedback |
| `PickupCrew` / `PickupCrewMember` | Ad-hoc crews formed from individual participants |
| `EventEmailTemplate` | Customisable per-event email subject/body |

### Frontend Layout (`BES-frontend/src/`)

| Path | Purpose |
|------|---------|
| `views/` | 19 page-level components (see Routes section) |
| `components/` | 22 reusable UI components |
| `utils/api.js` | ~65 frontend→backend fetch functions |
| `utils/adminApi.js` | ~14 admin-specific API functions |
| `utils/auth.js` | Pinia auth store + active event helpers |
| `utils/battleLogic.js` | Battle game logic |
| `utils/websocket.js` | WebSocket/STOMP setup |
| `utils/eventUtils.js` | Shared event/genre helpers |
| `utils/dropdown.js` | Dropdown state utilities |
| `utils/useScrollReveal.js` | Scroll-reveal animation composable |
| `router/index.js` | Client-side routes with RBAC guards |

### Navigation Architecture

**Navbar** (`App.vue`) uses a 3-column grid: Logo (left) · Primary nav (center) · Utilities (right).

- **Primary nav** (center): Home · Events · Admin only — kept minimal
- **Event chip** (right): Active event dropdown that doubles as section nav — contains Audition, Participants, Scoreboard, Battle links (role-filtered) + "Change Event". Do NOT add event-specific pages back as top-level nav items.
- **Event switching**: `changeEvent()` always passes `?redirect=<currentPath>` so EventSelector returns the user to the same page after switching.

**EventCard** (`components/EventCard.vue`):
- Hover (desktop) or tap (mobile) reveals an absolute-positioned action panel that overlaps content below without affecting grid layout
- `expandedId` state is lifted to `Events.vue` — only one card expands at a time. Cards emit `toggle`; parent manages which is open. Do not move this state back into the card.
- Action buttons: Details → `/events/:name`, Audition/Participants/Score/Battle → calls `setActiveEvent()` then navigates directly

### Testing

- **Backend**: JUnit 5 + Spring Boot Test + MockMvc; uses H2 in-memory DB via `@ActiveProfiles("test")`
- **Frontend**: Vitest + Vue Test Utils; test files in `src/utils/__tests__/`
- Backend test profile config: `application-test.properties`

## Key Conventions

### Branch Discipline (enforced via `git-scope-guard` skill)

**Before touching any code, invoke the `git-scope-guard` skill.** It checks whether the task fits the current branch's declared purpose.

| Branch prefix | Allowed work |
|---------------|-------------|
| `feat/X` | Feature X + UI/bugs introduced by this feature |
| `fix/X` | Fix bug X only |
| `ui/X` | Visual changes to component X only |
| `refactor/X` | Restructure X, no behavior changes |
| `chore/X` | Maintenance (deps, config, tooling) |
| `docs/X` | Documentation only |

**If a task is out of scope:** do not execute. Check branch state first:
- **Branch has uncommitted WIP** → create a GitHub issue only, then stop
- **Branch is clean** → offer the user a choice: (A) create issue for later, or (B) switch to a new branch now and implement immediately

When switching to a new branch (option B): check for open PRs on the current branch, pull latest main, rebase the new branch onto it, search for related open issues to bundle, then implement. "It's just a small fix" is not an exception to the flow.

### API (see `docs/API_CONVENTIONS.md` for full details)

- URLs: `/api/v1/{resource}` (collection), `/api/v1/{resource}/{id}` (single)
- HTTP methods: GET (read), POST (create/action), DELETE (remove)
- DTO naming: `Add{Entity}Dto`, `Get{Entity}Dto`, `Update{Entity}Dto`, `Delete{Entity}Dto`
- Error handling: 404 → empty `[]`, 403 → Spring Security redirect, 500 → log + generic error

### Database Migrations

- File naming: `V{version}__{description}.sql` (double underscore, snake_case description)
- Always add new migrations as new files — never edit existing ones
- Latest migration: `V37__add_overlay_theme_config.sql` → next is `V38__...`

### Layer Modification Order

**Always modify bottom-up**: DB migration → JPA entity → service → controller → DTO → frontend API call. Never change the frontend API contract without updating the backend first.

## Routes & Access by Role

Public routes (no authentication required):

| Route | View | Purpose |
|-------|------|---------|
| `/login` | `Login` | Login page |
| `/results` | `Results` | Public results portal (enter reference code) |
| `/results-qr` | `ResultsQR` | Results via QR scan |
| `/battle/overlay` | `BattleOverlay` | Stream overlay (OBS/broadcast display) |
| `/battle/judge` | `BattleJudge` | Battle judge voting screen |
| `/battle/chart` | `Chart` | 7-to-Smoke live chart |
| `/battle/bracket` | `BracketVisualization` | Live bracket with winner animation + LED ticker |
| `/event/select` | `EventSelector` | Select active event (redirected here if no event set) |

Authenticated routes (role-gated):

| Route | Roles | View | Purpose |
|-------|-------|------|---------|
| `/` | all | `MainMenu` | Role-filtered quick-action home |
| `/events` | Admin, Organiser | `Events` | Event list with search |
| `/events/:eventName` | Admin, Organiser | `EventDetails` | Event overview, genre accordion, Google Drive setup |
| `/event/audition-number` | Admin, Organiser | `AuditionNumber` | Display audition number on screen |
| `/event/update-event-details` | Admin, Organiser | `UpdateEventDetails` | Participant table, judge assignment, verification, payment |
| `/event/audition-list` | Admin, Organiser, Emcee, Judge | `AuditionList` | Role-split view: Judge scores / Emcee timer+status |
| `/event/score` | Admin, Organiser, Emcee | `Score` | Scoreboard with podium top-3 and by-judge breakdown |
| `/event/crew-formation` | Admin, Organiser | `CrewFormation` | Form pickup crews from individual participants |
| `/battle/control` | Admin, Organiser | `BattleControl` | Bracket seeding, battle phases, live match control |
| `/admin` | Admin only | `AdminPage` | Genre/judge/image/feedback-tag CRUD |

## Workflow by Role

### Admin
Full access to everything. Also manages global resources on `/admin`: genres, judges, images, and feedback tag groups/tags used across all events.

### Organiser
Event lifecycle end-to-end:
1. Create event on `/events` (link Google Drive folder, add genres)
2. Import participants from Google Sheets or add walk-ins on `EventDetails`
3. Manage participants on `/event/update-event-details` — assign judges, verify & email, track payment, set stage names/team format
4. Customise per-event email template
5. Configure scoring criteria per genre (default single score or weighted multi-criteria)
6. Run audition day: monitor `/event/audition-list`, check `/event/score` scoreboard
7. Form pickup crews from individuals on `/event/crew-formation`
8. Battle day: seed bracket on `/battle/control`, manage battle phases (IDLE → LOCKED → VOTING → REVEALED), watch `/battle/bracket` and `/battle/overlay` for stream
9. Release results on `/event/score` → results become visible on public `/results` portal via reference code/QR

### Emcee
Event-day read/announce role:
- `/event/audition-list` — timer, participant status (scored/unscored), swipe through rounds with `EmceeRoundView`
- `/event/score` — live scoreboard to announce standings

### Judge
Scoring only:
- `/event/audition-list` — `SwipeableCardsV2` score entry (whole + decimal keypads), per-aspect scoring if custom criteria are set, submit audition feedback tags + note via `FeedbackPopout`
- `/battle/judge` — vote for a battler during VOTING phase; result revealed on REVEALED phase

### Public (no login)
- `/results` or `/results-qr` — participants look up their own results using a reference code (released by organiser)
- `/battle/overlay` — OBS browser source for stream
- `/battle/bracket` — projector/audience bracket display with live winner animation and LED ticker
- `/battle/chart` — 7-to-Smoke cumulative battle chart

## Feature Overview

| Feature | Location | Notes |
|---------|----------|-------|
| Google Sheets import | `EventDetails`, `GoogleSheetsController` | Imports stage name, team format, member roster |
| Walk-in registration | `EventDetails` → `addWalkinToSystem` | Supports team members, team name |
| Participant verification + email | `UpdateEventDetails` | Verify individually or batch; uses `EventEmailTemplate` |
| Payment tracking | `UpdateEventDetails` | Per-participant payment flag |
| Custom scoring criteria | `ScoringCriteriaModal` / `ScoringCriteriaPanel` | Weighted criteria per event/genre; judge scores per aspect |
| Audition feedback | `FeedbackPopout` | Tags (grouped taxonomy) + free-text note per judge/participant |
| Results portal | `/results`, `/results-qr` | Reference code or QR; organiser controls release toggle |
| Battle phase state machine | `BattleControl` | IDLE → LOCKED → VOTING → REVEALED via WebSocket |
| Bracket seeding | `BattleControl` | By Rank (asc/desc), High↔Low pairing, Random; slot-picker UI |
| Tie-breaker persistence | `Score` + localStorage | Tie-breaker selections survive page refresh, scoped per event/genre |
| Live bracket visualization | `/battle/bracket` | Winner animations, LED ticker |
| 7-to-Smoke format | `/battle/chart` | Cumulative smoke chart |
| Pickup crew formation | `/event/crew-formation` | Form named crews from individual participants |
| Access code | `EventDetails` | Per-event access code, visible to organiser |
| Judging mode | `EventDetails` | Default (single score) or custom criteria mode |
| Judge scoring card | `SwipeableCardsV2` | `py-5` keypad buttons, `py-3` "10 — Full Score" button, `w-[97%]` card width, `p-2` card padding. Reset/GoTo live in the AuditionList context bar (not in the card). |
| Event selector grid | `EventSelector` | ≤4 events → 1 col, 5+ events → 2-col grid; no scroll. Always passes `?redirect=` back to originating page |

## Battle Endpoint Permission Matrix

**When debugging battle issues, check this first.** Most "data not persisting" or "feature not working" bugs are missing `EMCEE` on a `@PreAuthorize` annotation.

| Endpoint | Method | Admin | Organiser | Emcee | What It Saves |
|----------|--------|:---:|:---:|:---:|---------------|
| `/battle/bracket` | POST | ✅ | ✅ | ✅ | Bracket state (rounds, winners, topSize) |
| `/battle/score` | POST | ✅ | ✅ | ✅ | Judge vote tally → winner, phase=REVEALED |
| `/battle/phase` | POST | ✅ | ✅ | ✅ | Battle phase transitions |
| `/battle/battle-pair` | POST | ✅ | ✅ | ✅ | Current battler pair |
| `/battle/battle-pair` | DELETE | ✅ | ✅ | ✅ | Clear current pair |
| `/battle/smoke` | POST | ✅ | ✅ | ✅ | 7-to-Smoke battlers list |
| `/battle/revote` | POST | ✅ | ✅ | ✅ | Reset all judge votes |
| `/battle/champion-reveal` | POST | ✅ | ✅ | ✅ | Champion reveal broadcast to overlay/bracket |
| `/battle/judge` | POST | ✅ | ✅ | ❌ | Add battle judge |
| `/battle/judge` | DELETE | ✅ | ✅ | ❌ | Remove battle judge |
| `/battle/judge/weightage` | POST | ✅ | ✅ | ❌ | Update judge weight |
| `/battle/upload` | POST | ✅ | ✅ | ❌ | Upload images |
| `/battle/overlay-config` | POST | ✅ | ✅ | ❌ | Overlay colors/visibility |
| `/battle/active-genre` | POST | ✅ | ✅ | ✅ | Switch active event+genre |

**Every time you add a new battle endpoint, explicitly decide whether Emcee needs it.** The default should be to include `'EMCEE'` unless the operation is setup/admin-only.

## Battle Persistence Architecture

### Two Data Stores (both in the same `battle_genre_state` DB row)

| Store | Backend Field | API | Updated By | Used For |
|-------|-------------|-----|------------|----------|
| `bracketState` | `bracketState` (Map) | POST `/bracket` | `broadcastBracket()` in frontend | Standard bracket rounds+winners + smoke `topSize`/initial array |
| `battlers` | `battlers` (List) | POST `/smoke` | `updateSmokeList()` in frontend, `setScoreService()` in backend | 7-to-Smoke queue order + scores |

**Critical:** Both are saved to the same DB row via `persistActiveState()`. They must stay in sync. `broadcastBracket()` writes BOTH (smoke path only). `updateSmokePair()` writes only `battlers`. `setScoreService()` writes only `battlers`.

### Refresh State Restoration

On page refresh, `BattleControl.vue` restores state through two paths:

1. **Recovery path** (`jumpToRecoveredPair`): taken when `battlePhase !== 'IDLE' && currentPair.left` is non-empty. Restores active battle position.
2. **Hydrate path** (`hydrateFromState`): taken when IDLE or no active pair. Restores bracket data only.

Both paths receive state from `GET /battle/state` (REST) and `/topic/battle/state` (WebSocket). The diff guard (`lastAppliedState`) prevents duplicate processing.

### Format Detection (7-to-Smoke vs Standard)

- **Frontend:** `isSmoke` = `topSize === 7`. Set by genre watcher when genre name contains "7 to smoke".
- **Backend:** `genreFormat` loaded from `EventGenre.format` field. Included in `/battle/state` response.
- **`hydrateFromState`:** Uses `applySmoke`/`applyStd` hard gates to prevent cross-format data corruption.

## Common Pitfalls & Debugging Checklist

### 🔴 BEFORE diving into code — ask these 6 questions

When someone reports a bug or requests a feature in the battle system, **ask clarifying questions first**. Do not open a file until you can answer all of these:

| # | Question | Why It Matters |
|---|----------|---------------|
| 1 | **Which format?** 7-to-Smoke, regular bracket, or both? | Entirely different data structures (`[{name,score}]` vs `{Top16:[[L,R,W],...]}`), different state machines, different UI |
| 2 | **Which role?** Admin, Organiser, or Emcee? | Most bugs are role-specific. Emcee has restricted endpoints and a separate UI code path in `LiveMatchPanel` |
| 3 | **One event with both formats?** Do they switch genres? | The save-then-load cycle on genre switch is the #1 source of state leaks (see Pitfall #0) |
| 4 | **Single tab or multiple?** Any other clients connected? | `activeGenreName` is a global server field — multiple tabs race on it |
| 5 | **What phase is the battle in?** IDLE / LOCKED / VOTING / REVEALED / DECIDED? | Different phases take different code paths in `jumpToRecoveredPair`, `hydrateFromState`, and the template |
| 6 | **Page refresh or server restart?** | Page refresh = frontend re-hydrates from REST. Server restart = `@PostConstruct` loads last active genre from DB |

**If the answer to any of these is unclear or "both," investigate each path separately.** The formats diverge in `broadcastBracket`, `setWinner` vs `update7toSmokeMatch`, `hydrateFromState` format gates, and the template sections.

### ⚠️ 0. One event = both formats coexisting (read this first)

**An event can have both 7-to-Smoke and regular battle genres simultaneously.** The user switches between them via the genre selector in `LiveMatchPanel`. This is the single most important architectural fact — every other pitfall flows from it.

**The core problem:** `battlePhase`, `champion`, `bracketState`, and `battlers` in `BattleService.java` are **single in-memory fields shared across ALL genres**. They are NOT per-genre data structures. When the frontend switches genres:

```
1. persistActiveState()  → saves current in-memory state to OUTGOING genre's DB row
2. activeGenreName = newGenre
3. loadGenreStateIntoMemory() → overwrites in-memory state from INCOMING genre's DB row
```

This save-then-load cycle is the critical path. If anything goes wrong (race condition, missing permission, stale WS message, format mismatch), data from one format **leaks into the other's DB row**. Always ask:

- "What happens to this state when the user switches genres?"
- "Is this field scoped to the current genre or global?"
- "Does this mutation include a format guard (`isSmoke`/`applySmoke`)?"

### 1. "Data not persisting on refresh" → Check permissions first
```
→ Is the user an Emcee?
→ Check Network tab for 403 responses on POST /bracket, POST /champion-reveal
→ Verify @PreAuthorize on the relevant BattleController endpoint includes 'EMCEE'
```

### 2. Silent failures in API calls
Most API functions in `api.js` catch errors with `console.error(e)` but **don't surface them to the UI** and **don't check HTTP status codes**. A 403 Forbidden returns a Response object that `res.ok` would reject, but callers rarely check. When debugging, always check the browser's Network tab.

### 3. Global backend state shared across genres
`battlePhase`, `champion`, `bracketState`, and `battlers` in `BattleService.java` are single in-memory fields — NOT per-genre. They get overwritten by `loadGenreStateIntoMemory()` on genre switch. If two browser tabs have different genres active, the last tab to call `setActiveGenre()` wins the `activeGenreName` race. The DB rows are correctly separated per `(event_name, genre_name)`, but in-memory state is a single slot.

### 4. `watch(topSize)` clears `currentBattle`/`currentTop`/`currentRound`
These are always reset at the end of the watcher callback. Programmatic changes set `skipSizeChangeClear=true` to suppress this, but if the watcher fires **async after hydration** (triggered by a topSize change in `hydrateFromState`), it can wipe just-restored state. Always guard these clears with the `programmatic` flag.

### 5. 7-to-Smoke `broadcastBracket` must be called after battle operations
`updateSmokePair()` was only calling `updateSmokeList()` (saving battlers), not `setBracketState()` (saving bracket). This left `bracketState.rounds` stale after queue rotation. Now `updateSmokePair` calls `broadcastBracket()` which syncs both.

### 6. `updateSmokeList` was fire-and-forget in `broadcastBracket`
The `await` was missing, causing `updateSmokeList` and `setBracketState` to race on the same DB row. Both write via `persistActiveState()`. Now properly awaited.

### 7. Emcee template parity
When adding UI controls, check both the Organiser section (`!isReadonly`) AND the Emcee section (`isReadonly`) in `LiveMatchPanel.vue`. The two sections are maintained separately and can drift — buttons present for Organiser may be missing for Emcee.

### Debugging Flow (follow this order)
1. **Network tab:** Any 403 or failed requests?
2. **Backend `@PreAuthorize`:** Does the user's role have access?
3. **Frontend console:** Any caught errors?
4. **DB state:** What does `GET /battle/state` actually return?
5. **Code logic:** Only after ruling out 1–4

## Frontend Design System

> **Full spec:** `docs/superpowers/specs/2026-05-30-ui-overhaul-design.md`
> All frontend work must follow this design system. When adding or updating any UI, read the spec first.

### Design Language — Cinematic Battle

The entire app uses a unified cinematic aesthetic inspired by `BattleOverlay.vue` and `BracketVisualization.vue`. Every screen should feel like part of the same broadcast production system.

**Do NOT change** `BattleOverlay.vue`, `BracketVisualization.vue`, or `Chart.vue` — they already have their own bespoke CSS and are the reference point, not the target.

### Accent Color (runtime-configurable)

- **CSS custom property:** `--accent-color` — set on `<html>` at runtime via `App.vue`
- **Dark mode default:** `#ffffff` (white/silver)
- **Light mode override:** `rgba(0,0,0,0.78)` — set via `[data-theme="light"]` CSS, ignores server value
- **Derived tokens** (auto-computed in CSS): `--accent-muted` = `color-mix(in srgb, var(--accent-color) 25%, transparent)` · `--accent-subtle` = `color-mix(in srgb, var(--accent-color) 7%, transparent)`
- **Utility classes:** `.text-accent` · `.bg-accent` · `.border-accent` · `.glow-accent`
- Admin can change the color globally via `AdminPage.vue` → saved to `AppConfig` DB table → broadcast via `/topic/app-config` WebSocket → all clients update live

### Red (`primary-*`) — Error/Danger ONLY

Red is **no longer a brand color**. It is reserved exclusively for error states, destructive actions, and warning indicators. Never use `primary-*` for buttons, links, nav, or interactive chrome.

### Semantic State Pattern (left border + glowing dot)

All alert banners, warning chips, and status callouts use:
- **3px solid left border** in the semantic color (red / amber / green)
- **Subtle tint background** (10–12% opacity)
- **Glowing dot indicator** (same color with `box-shadow` glow)
- Never use full solid fills or low-opacity tints alone — both were hard to read

### Typography — Full Anton SC

**Every text element uses Anton SC.** `--font-sans` is `'Anton SC'` (the default body font). Inter is available as `--font-body` but is not used by default. All text is `text-transform: uppercase`. Letter-spacing + opacity provide hierarchy (Anton SC has only one weight).

| Tier | Size | Letter-spacing | Use |
|------|------|----------------|-----|
| Display | `clamp(36px,5vw,56px)` | `0.06em` | Page heroes, login |
| Page title | `28px` | `0.06em` | View `h1` headings |
| Section header | `13px` | `0.18em` | Group labels + rule line |
| Body / row | `13px` | `0.05em` | Table rows, cards, form values |
| Label | `10px` | `0.22em` | Field labels, badges, breadcrumbs |
| Number / stat | `42px` | `0.02em` | Scores, counts, audition numbers |

Text shadows use `var(--accent-muted)`: Display `2px 2px 0`, Page title `1px 1px 0`, others none.

### Shape — Parallelogram (universal)

All cards, list rows, stat boxes, and badges use **parallelogram clip-path** in both dark and light mode:
```css
clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%)
```
**No more** `rounded-2xl` cards or `rounded-full` badges. The `.card` and `.badge-*` utilities in `base.css` use this shape.

### Six Cinematic Chrome Elements

| Element | Where | Notes |
|---------|-------|-------|
| **Scanlines** | `App.vue` global overlay | Always on; 50% opacity in light mode |
| **Parallelogram chips** | All cards, rows, badges | Core shape of every UI element |
| **Corner accent bars** | Panels, modals only | Top-left + bottom-left bars only (not all 4 corners) |
| **Section rules** | Every section header | Label + `flex:1` hairline — replaces `border-b` dividers |
| **Color bleed** | Page root only | Radial gradients from bottom corners using `--accent-subtle`; hidden in light mode |
| **Topbar** | `App.vue` navbar | Anton SC, parallelogram nav chips, glowing dot |

### Surface Scale (unchanged)
| Token | Value | Use |
|-------|-------|-----|
| `surface-900` | `#111111` | Page base |
| `surface-800` | `#1a1a1a` | Cards, panels |
| `surface-600` | `#2c2c2c` | Borders, dividers |
| Chip fill | `rgba(255,255,255,0.04)` | Parallelogram background |
| Chip border | `rgba(255,255,255,0.07)` | Parallelogram border |

### Target Audience UX Rules
- **Judges**: Large touch targets (min 48px), Number/stat tier for scores, minimal steps to submit
- **Emcees**: Clear status badges, always-visible participant count
- **Organisers**: Scannable stat cards, collapsible complexity, clear workflow steps
