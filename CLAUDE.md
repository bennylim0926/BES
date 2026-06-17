# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

**Kyrove (formerly BES — Battle Event System)** — a full-stack web app for managing dance battle events (registration, judging, scoring, real-time battle control, results portal). The product is called Kyrove; internal code references (package names, directory names, env vars) still use `BES`.

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
| `controllers/` | 13 REST controllers under `/api/v1/` (+ STOMP `BattleTimerController`) |
| `services/` | Business logic services (tier access, session tokens, emcee category presence, audition display, etc.) |
| `respositories/` | Spring Data JPA repos |
| `models/` | 24 JPA entities → PostgreSQL tables |
| `dtos/` | Request/response DTOs |
| `config/` | Security, CORS, WebSocket, Google API config |
| `mapper/` | Entity ↔ DTO converters |

**Controllers:**

| Controller | Base Path | Purpose |
|-----------|-----------|---------|
| `AuthController` | `/api/v1/auth` | Login, logout, session check, token-link login (`/auth/me` returns `tier`) |
| `EventController` | `/api/v1/event` | CRUD events, participants, scores, categories, scoring criteria, feedback toggle, pickup crews, session tokens |
| `BattleController` | `/api/v1/battle` | Battle mode, voting, judges, images, battle phase state machine (most endpoints gated by `TierAccessService`) |
| `BattleTimerController` | STOMP `/app/battle/timer/...` | WebSocket timer relay for battle overlay |
| `EmceeCategoryController` | `/api/v1/emcee/active-categories` | Heartbeat-based registry of categories actively being run by emcees |
| `EventAuditionDisplayController` | `/api/v1/event/audition-display` | Live round/timer state for the OBS audition display |
| `AdminController` | `/api/v1/admin` | Admin CRUD for categories (genre groups), judges, scores, feedback groups/tags, organiser tier |
| `AppConfigController` | `/api/v1/app-config` | Global runtime config (accent color) broadcast to all clients |
| `ResultsController` | `/api/v1/results` | Public results by reference code, QR access |
| `SecurityController` | `/api/v1/security` | CSRF token endpoint |
| `GoogleDriveFileController` | `/api/v1/files` | Google Drive file listing |
| `GoogleDriveFolderController` | `/api/v1/folders` | Google Drive folder listing |
| `GoogleSheetsController` | `/api/v1/sheets` | Google Sheets participant import |

**Key Models:**

| Model | Purpose |
|-------|---------|
| `Event` | Event with payment flag, judging mode, results release toggle, `feedbackEnabled`, `animTheme` |
| `EventParticipant` / `EventParticipantId` | Participant linked to event; supports walk-ins, stage name, team format, display name |
| `EventParticipantTeamMember` | Individual member rows for team entries |
| `EventCategory` | Category (formerly "genre") linked to event with format, display fields, scoring config |
| `EventCategoryParticipant` / `EventCategoryParticipantId` | Participant-category junction with audition number |
| `EventCategoryParticipantMember` | Per-category team member roster |
| `EventCategoryBattleGuest` | Battle-only guest participants attached to a category |
| `BattleCategoryState` | Persisted bracket / smoke / phase state per (event, category) |
| `BattleActiveCategory` | Tracks which category each event is currently running |
| `Score` | Single overall score per judge/participant/category |
| `ScoringCriteria` | Named weighted criteria per event/category (custom scoring mode) |
| `AuditionFeedback` | Judge feedback per participant/category with tags and note |
| `FeedbackTag` / `FeedbackTagGroup` | Configurable tag taxonomy for feedback |
| `PickupCrew` / `PickupCrewMember` | Ad-hoc crews formed from individual participants |
| `EventEmailTemplate` | Customisable per-event email subject/body |
| `Account` | User account with role + `tier` (PRO/MAX) for organisers |
| `SessionToken` | Permanent/long-lived tokens for direct session links (Emcee, Helper, per-judge) |
| `AppConfig` | Global runtime config row (e.g. accent color) |
| `Judge` / `Participant` | Global judge / participant directories reused across events |

### Frontend Layout (`BES-frontend/src/`)

| Path | Purpose |
|------|---------|
| `views/` | 25 page-level components (see Routes section) |
| `components/` | Reusable UI components |
| `utils/api.js` | Frontend→backend fetch functions |
| `utils/adminApi.js` | Admin-specific API functions |
| `utils/auth.js` | Pinia auth store + active event helpers; persists `tier`, holds `activeEventBattleEnabled` |
| `utils/battleLogic.js` | Battle game logic |
| `utils/websocket.js` | WebSocket/STOMP setup |
| `utils/eventUtils.js` | Shared event/category helpers |
| `utils/dropdown.js` | Dropdown state utilities |
| `utils/useScrollReveal.js` | Scroll-reveal animation composable |
| `utils/useTierAccess.js` | Composable + `resolveBattleEnabled` helper for PRO/MAX gating (router guard reuses it) |
| `utils/branding.js` | Central `APP_NAME` / `APP_TAGLINE` / `APP_DESCRIPTION` constants (Kyrove) |
| `router/index.js` | Client-side routes with RBAC + tier guards |

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
- Latest migration: `V43__drop_access_code_column.sql` → next is `V44__...`
- Recent schema shifts to be aware of: `V39` added `event_genre_link` junction; `V40` added organiser `tier`; `V41`/`V42` renamed `event_genre` → `event_category` and `event_participant.genre` → `category`; `V43` dropped `access_code`

### Layer Modification Order

**Always modify bottom-up**: DB migration → JPA entity → service → controller → DTO → frontend API call. Never change the frontend API contract without updating the backend first.

## Routes & Access by Role

Public routes (no authentication required):

| Route | View | Purpose |
|-------|------|---------|
| `/login` | `Login` | Login page |
| `/403` | — | Forbidden landing for tier-gated redirects |
| `/results` | `Results` | Public results portal (enter reference code) |
| `/results-qr` | `ResultsQR` | Results via QR scan |
| `/battle/overlay` | `BattleOverlay` | Stream overlay (OBS/broadcast display) |
| `/battle/judge` | `BattleJudge` | Battle judge voting screen |
| `/battle/chart` | `Chart` | 7-to-Smoke live chart |
| `/battle/bracket` | `BracketVisualization` | Live bracket with winner animation + LED ticker |
| `/audition/display` | `AuditionDisplay` | Live audition round + timer OBS source |
| `/event/select` | `EventSelector` | Select active event (redirected here if no event set) |
| `/auth/token` | `TokenAuth` | Token-based login landing (consumes EMCEE/HELPER/JUDGE session-token links) |

Authenticated routes (role-gated):

| Route | Roles | View | Purpose |
|-------|-------|------|---------|
| `/` | all | `MainMenu` | Role-filtered quick-action home |
| `/events` | Admin, Organiser | `Events` | Event list with search |
| `/events/:eventName` | Admin, Organiser | `EventDetails` | Event overview, category accordion, Google Drive setup, session-link generation |
| `/event/audition-number` | Admin, Organiser | `AuditionNumber` | Display audition number on screen |
| `/event/audition-adjust` | Admin, Organiser | `AuditionAdjust` | Tune/adjust audition number assignments |
| `/event/update-event-details` | Admin, Organiser | `UpdateEventDetails` | Participant table, judge assignment, verification, payment |
| `/event/audition-list` | Admin, Organiser, Emcee, Judge | `AuditionList` | Role-split view: Judge scores / Emcee timer+status |
| `/event/score` | Admin, Organiser, Emcee | `Score` | Top N qualifier tool: Control vs Broadcast modes, live WS updates, tie-pool resolver |
| `/event/crew-formation` | Admin, Organiser | `CrewFormation` | Form pickup crews from individual participants |
| `/battle/control` | Admin, Organiser (**MAX tier only**) | `BattleControl` | Bracket seeding, battle phases, live match control, overlay/animation theme config |
| `/admin` | Admin only | `AdminPage` | Category/judge/image/feedback-tag CRUD + organiser PRO/MAX tier control + global accent color |
| `/judge/session` | Judge (token) | `JudgeSessionView` | Token-link landing for a per-judge session |
| `/emcee/session` | Emcee (token) | `EmceeSessionView` | Token-link landing for an emcee — includes Display URLs for OBS |
| `/helper/session` | Helper (token) | `HelperSessionView` | Token-link landing for the Helper check-in flow |

## Workflow by Role

### Admin
Full access to everything (always treated as MAX tier). Also manages global resources on `/admin`: categories (genre groups), judges, images, feedback tag groups/tags, **organiser PRO/MAX tier**, and the global accent color used across all clients.

### Organiser (PRO or MAX)
**Tier matters** — PRO organisers do not see any battle UI; battle endpoints return 403. MAX organisers get the full lifecycle. Tier is set by Admin on `/admin`.

Event lifecycle end-to-end:
1. Create event on `/events` (link Google Drive folder, add categories)
2. Import participants from Google Sheets or add walk-ins on `EventDetails`
3. Manage participants on `/event/update-event-details` — assign judges, verify & email, track payment, set stage names/team format
4. Customise per-event email template
5. Configure scoring criteria per category (default single score or weighted multi-criteria), toggle `feedbackEnabled`
6. Generate session links (Emcee / Helper / per-judge) from EventDetails to hand to staff
7. Run audition day: monitor `/event/audition-list`, check `/event/score` (Top N qualifier) scoreboard
8. Form pickup crews from individuals on `/event/crew-formation`
9. **MAX only** — Battle day: seed bracket on `/battle/control`, manage battle phases (IDLE → LOCKED → VOTING → REVEALED), choose animation theme (IMPACT / HYPE), watch `/battle/bracket` and `/battle/overlay` for stream
10. Release results on `/event/score` → results become visible on public `/results` portal via reference code/QR

### Emcee
Event-day read/announce role. Multiple emcees can run **different categories concurrently** — heartbeat-based active-category tracker shows an "ACTIVE" badge on categories already being run; second emcee gets a confirm prompt before overriding.
- `/emcee/session` — token-link landing with Display URLs (one OBS URL per category)
- `/event/audition-list` — timer, participant status (scored/unscored), swipe / `‹ PREV` / `NEXT ›` buttons through rounds with `EmceeRoundView`
- `/event/score` — live Broadcast-mode scoreboard to announce standings

### Judge
Scoring only:
- `/judge/session` — token-link landing (per-judge token; refresh extends; tied to judge lifecycle)
- `/event/audition-list` — `SwipeableCardsV2` score entry (whole + decimal keypads), per-aspect scoring if custom criteria are set, submit audition feedback tags + note via `FeedbackPopout` (only shown when event `feedbackEnabled` is true)
- `/battle/judge` — vote for a battler during VOTING phase; result revealed on REVEALED phase

### Helper
Check-in role (new):
- `/helper/session` — token-link landing
- Runs the check-in flow on participants; sees the results QR in the post-check-in dialog

### Public (no login)
- `/results` or `/results-qr` — participants look up their own results using a reference code (released by organiser)
- `/battle/overlay` — OBS browser source for stream
- `/battle/bracket` — projector/audience bracket display with live winner animation and LED ticker
- `/battle/chart` — 7-to-Smoke cumulative battle chart
- `/audition/display` — OBS source showing current round, participant name(s), countdown timer (live via WebSocket; survives refresh)

## Feature Overview

| Feature | Location | Notes |
|---------|----------|-------|
| Google Sheets import | `EventDetails`, `GoogleSheetsController` | Imports stage name, team format, member roster; comma-separated category column values are split |
| Walk-in registration | `EventDetails` → `addWalkinToSystem` | Supports team members, team name |
| Participant verification + email | `UpdateEventDetails` | Verify individually or batch; uses `EventEmailTemplate` |
| Payment tracking | `UpdateEventDetails` | Per-participant payment flag |
| Custom scoring criteria | `ScoringCriteriaModal` / `ScoringCriteriaPanel` | Weighted criteria per event/category; judge scores per aspect |
| Audition feedback | `FeedbackPopout` | Tags (grouped taxonomy) + free-text note per judge/participant; hidden when event `feedbackEnabled` is false |
| Feedback system toggle | `EventDetails` Event Settings | Per-event `feedbackEnabled` boolean; live WS broadcast to judge UIs |
| Results portal | `/results`, `/results-qr` | Reference code or QR; organiser controls release toggle |
| Top N qualifier | `Score.vue` (Control mode) | Top 8 / 16 / 32 / ALL picker; tie-pool stepper; black-box rule hides expanded pool from Broadcast/public |
| Score broadcast mode | `Score.vue` (Broadcast mode) | Uniform-row leaderboard, 2-col packing at Top 16/32, glowing Top N separator; live WS updates on score/feedback save |
| Battle phase state machine | `BattleControl` (MAX tier only) | IDLE → LOCKED → VOTING → REVEALED via WebSocket |
| Battle tier gating | `TierAccessService` (backend) + `useTierAccess` (frontend) | PRO organisers blocked; UI hidden (no lock icons); 20+ battle endpoints + `GET /battle/state` gated |
| Bracket seeding | `BattleControl` | By Rank (asc/desc), High↔Low pairing, Random; slot-picker UI |
| Tie-breaker persistence | `Score` + localStorage | Tie-breaker selections survive page refresh, scoped per event/category |
| Live bracket visualization | `/battle/bracket` | Winner animations, LED ticker |
| 7-to-Smoke format | `/battle/chart` | Cumulative smoke chart |
| Animation theme | `BattleControl` Overlay Settings → `event.animTheme` | IMPACT (default) or HYPE; broadcasts via `/topic/battle/{event}/overlay-config`; `BattleOverlay`/`BracketVisualization`/`BattleJudge` switch via `data-anim-theme` |
| Pickup crew formation | `/event/crew-formation` | Form named crews from individual participants |
| Concurrent emcee sessions | `EmceeCategoryController` + `/topic/emcee/active-categories/{event}` | Heartbeat tracker (~90s release); ACTIVE badge + confirm prompt before joining a category an emcee is already running |
| Audition Display (OBS) | `/audition/display?event=X` | Public route; subscribes to display state; timer reconstructs from `timerStartedAt + timerDuration`; survives refresh |
| Session-token links | EventDetails → Session Links + `TokenAuth` view | EMCEE/HELPER permanent tokens + per-judge tokens; refresh-to-extend; consumed via `/auth/token?token=...` |
| Judging mode | `EventDetails` | Default (single score) or custom criteria mode |
| Judge scoring card | `SwipeableCardsV2` | `py-5` keypad buttons, `py-3` "10 — Full Score" button, `w-[97%]` card width, `p-2` card padding. Reset/GoTo live in the AuditionList context bar (not in the card). |
| Event selector grid | `EventSelector` | ≤4 events → 1 col, 5+ events → 2-col grid; no scroll. Always passes `?redirect=` back to originating page |
| Helper check-in | `HelperSessionView` + check-in dialog | Helper role can see results QR in post-check-in dialog (GET `/api/v1/results/qr` allows HELPER) |
| Global runtime config | `AppConfigController` + `/topic/app-config` | Admin sets accent color on `/admin` → broadcast to all clients |

## Battle Endpoint Permission Matrix

**When debugging battle issues, check this first.** Most "data not persisting" or "feature not working" bugs are either (a) missing `EMCEE` on a `@PreAuthorize` annotation, or (b) the organiser is on PRO tier — `TierAccessService.requireBattleAccess()` returns 403 for non-MAX before authorisation even runs.

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
| `/battle/active-category` | POST | ✅ | ✅ | ✅ | Switch active event+category |

**Every time you add a new battle endpoint, explicitly decide whether Emcee needs it.** The default should be to include `'EMCEE'` unless the operation is setup/admin-only. Also wire it through `TierAccessService.requireBattleAccess()` so PRO organisers get 403 instead of partial access.

## Battle Persistence Architecture

### Two Data Stores (both in the same `battle_category_state` DB row)

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

- **Frontend:** `isSmoke` = `topSize === 7`. Set by category watcher when category name contains "7 to smoke".
- **Backend:** `categoryFormat` loaded from `EventCategory.format` field. Included in `/battle/state` response. *(Note: the JSON key was renamed from `genreFormat` → `categoryFormat` in #149. Any old frontend code still reading `genreFormat` will silently see undefined.)*
- **`hydrateFromState`:** Uses `applySmoke`/`applyStd` hard gates to prevent cross-format data corruption.

## Common Pitfalls & Debugging Checklist

### 🔴 BEFORE diving into code — ask these 6 questions

When someone reports a bug or requests a feature in the battle system, **ask clarifying questions first**. Do not open a file until you can answer all of these:

| # | Question | Why It Matters |
|---|----------|---------------|
| 1 | **Which format?** 7-to-Smoke, regular bracket, or both? | Entirely different data structures (`[{name,score}]` vs `{Top16:[[L,R,W],...]}`), different state machines, different UI |
| 2 | **Which role?** Admin, Organiser (PRO/MAX), or Emcee? | Most bugs are role-specific. PRO organisers are blocked at `TierAccessService`; Emcee has restricted endpoints and a separate UI code path in `LiveMatchPanel` |
| 3 | **One event with both formats?** Do they switch categories? | The save-then-load cycle on category switch is the #1 source of state leaks (see Pitfall #0) |
| 4 | **Single tab or multiple?** Any other clients connected? | `activeCategoryName` is a global server field — multiple tabs race on it. Emcees can now run different categories concurrently, so cross-tab activity is the norm |
| 5 | **What phase is the battle in?** IDLE / LOCKED / VOTING / REVEALED / DECIDED? | Different phases take different code paths in `jumpToRecoveredPair`, `hydrateFromState`, and the template |
| 6 | **Page refresh or server restart?** | Page refresh = frontend re-hydrates from REST. Server restart = `@PostConstruct` loads last active category from DB |

**If the answer to any of these is unclear or "both," investigate each path separately.** The formats diverge in `broadcastBracket`, `setWinner` vs `update7toSmokeMatch`, `hydrateFromState` format gates, and the template sections.

### ⚠️ 0. One event = both formats coexisting (read this first)

**An event can have both 7-to-Smoke and regular battle categories simultaneously.** The user switches between them via the category selector in `LiveMatchPanel`. This is the single most important architectural fact — every other pitfall flows from it.

**The core problem:** `battlePhase`, `champion`, `bracketState`, and `battlers` in `BattleService.java` are **single in-memory fields shared across ALL categories**. They are NOT per-category data structures. When the frontend switches categories:

```
1. persistActiveState()       → saves current in-memory state to OUTGOING category's DB row
2. activeCategoryName = new
3. loadCategoryStateIntoMemory() → overwrites in-memory state from INCOMING category's DB row
```

This save-then-load cycle is the critical path. If anything goes wrong (race condition, missing permission, stale WS message, format mismatch), data from one format **leaks into the other's DB row**. Always ask:

- "What happens to this state when the user switches categories?"
- "Is this field scoped to the current category or global?"
- "Does this mutation include a format guard (`isSmoke`/`applySmoke`)?"

### 1. "Data not persisting on refresh" → Check permissions first
```
→ Is the user an Emcee?
→ Check Network tab for 403 responses on POST /bracket, POST /champion-reveal
→ Verify @PreAuthorize on the relevant BattleController endpoint includes 'EMCEE'
```

### 2. Silent failures in API calls
Most API functions in `api.js` catch errors with `console.error(e)` but **don't surface them to the UI** and **don't check HTTP status codes**. A 403 Forbidden returns a Response object that `res.ok` would reject, but callers rarely check. When debugging, always check the browser's Network tab.

### 3. Global backend state shared across categories
`battlePhase`, `champion`, `bracketState`, and `battlers` in `BattleService.java` are single in-memory fields — NOT per-category. They get overwritten by `loadCategoryStateIntoMemory()` on category switch. If two browser tabs have different categories active, the last tab to call `setActiveCategory()` wins the `activeCategoryName` race. The DB rows are correctly separated per `(event_name, category_name)`, but in-memory state is a single slot.

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

### Typography — Oswald (chrome) + Inter (prose) + Oswald name tier

**Anton SC is no longer the default.** As of #141 the rollout is:

- **`--font-sans` = `'Oswald'`** (self-hosted latin 500) — default body / chrome font
- **`--font-body` = `'Inter'`** — used for prose, descriptions, helper subcopy
- **Anton SC** stays loaded as fallback only on the 3 untouched broadcast files (`BattleOverlay.vue`, `BracketVisualization.vue`, `Chart.vue`) — do not reintroduce it elsewhere
- **Inputs no longer force uppercase.** User-typed data (participant names, team names, judge names, event names, criterion labels, etc.) reads **as typed** everywhere — EventDetails, UpdateEventDetails, AuditionAdjust, AuditionList, AuditionNumber, Score, BattleControl, LiveMatchPanel, AdminPage, EventCard, EventPanel, EventSelector, ScoringCriteriaModal, FeedbackPopout, scoring cards, broadcast displays, BattleJudge.
- **Chrome labels stay capitalised** (section headers, button labels, status badges, round counters, "VS", "UP NEXT"). When in doubt: if it's a label, uppercase; if it's user data or descriptive prose, leave the case alone.

**New tiers** added in `base.css`:

| Tier | Font | Use |
|------|------|-----|
| `.type-prose` / `.type-prose-sm` | Inter 12px sentence case | Every helper / description / explanatory subcopy |
| `.type-name` / `.type-name-sm` | Oswald sentence case | Every user-typed name (participant, team, member, judge, division, event, criterion) |
| `.section-rule-lg` | — | Larger section subheaders |
| `.tab-item-data` | — | Tab item with attached data badge |

Legacy chrome tiers (now Oswald, not Anton SC):

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

### Modal Standardisation (post-#126 / #130)

All modals (`FeedbackPopout`, `ScoringCriteriaModal`, `CreateParticipantForm`, EventDetails Category Entries / Add Division) share a slide-up shell:

- `bg-black/80` backdrop (no bleed-through)
- `pb-6` mobile bottom padding (clears browser chrome)
- **No header X button** — backdrop click closes; show "Tap outside to close" hint
- Header is icon + badge; rows use `.para-chip-sm` parallelogram tag chips
- Use `<Teleport>` for portal rendering
- Save state shown as centered status text (`Saving…` / `Feedback saved`) instead of a Done button

### Six Cinematic Chrome Elements

| Element | Where | Notes |
|---------|-------|-------|
| **Scanlines** | `App.vue` global overlay | Always on; 50% opacity in light mode |
| **Parallelogram chips** | All cards, rows, badges | Core shape of every UI element |
| **Corner accent bars** | Panels, modals only | Top-left + bottom-left bars only (not all 4 corners) |
| **Section rules** | Every section header | Label + `flex:1` hairline — replaces `border-b` dividers |
| **Color bleed** | Page root only | Radial gradients from bottom corners using `--accent-subtle`; hidden in light mode |
| **Topbar** | `App.vue` navbar | Oswald, parallelogram nav chips, glowing dot |

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
