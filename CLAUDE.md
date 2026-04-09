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

### API (see `API_CONVENTIONS.md` for full details)

- URLs: `/api/v1/{resource}` (collection), `/api/v1/{resource}/{id}` (single)
- HTTP methods: GET (read), POST (create/action), DELETE (remove)
- DTO naming: `Add{Entity}Dto`, `Get{Entity}Dto`, `Update{Entity}Dto`, `Delete{Entity}Dto`
- Error handling: 404 → empty `[]`, 403 → Spring Security redirect, 500 → log + generic error

### Database Migrations

- File naming: `V{version}__{description}.sql` (double underscore, snake_case description)
- Always add new migrations as new files — never edit existing ones
- Latest migration: `V16__add_pickup_crew.sql` → next is `V17__...`

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
| Judge scoring card | `SwipeableCardsV2` | Minimal padding for mobile; `py-4` keypad buttons, `w-[97%]` card width, `p-2` card padding — do not increase these |
| Event selector grid | `EventSelector` | ≤4 events → 1 col, 5+ events → 2-col grid; no scroll. Always passes `?redirect=` back to originating page |

## Frontend Design System

All frontend work must follow this design system consistently across every component and view.

### Color Tokens (defined in `base.css` `@theme`)
| Role | Token | Hex |
|------|-------|-----|
| Primary | `primary-500` | `#06b6d4` (cyan) |
| Primary Dark | `primary-600/700` | `#0891b2 / #0e7490` |
| Accent | `secondary-500` | `#f97316` (orange) |
| Surface Dark | `surface-900` | `#0f172a` (deep navy) |
| Surface Mid | `surface-700/800` | `#334155 / #1e293b` |
| Surface Light | `surface-50/100` | `#f8fafc / #f1f5f9` |
| Content | white | `#ffffff` |

### Typography
| Use | Font | Class |
|-----|------|-------|
| Body / UI | Inter | `font-sans` |
| Headings | Outfit | `font-heading` |
| Numbers / Code | Source Code Pro | `font-source` |
| Display titles | Anton SC | `font-anton` |

### Spacing & Shape
- Border radius: `rounded-xl` (inputs, cards) · `rounded-2xl` (panels, modals) · `rounded-full` (badges, pills)
- Card style: `bg-white rounded-2xl border border-surface-200/80 shadow-sm`
- Page container: `max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8`
- Navbar height offset: `h-16` (64px)

### Colour Usage Rules (60-30-10)
- **60% Neutral** — `surface-*` for backgrounds, borders, text hierarchy
- **30% Structure** — `surface-800/900` + white for nav, headers, dark surfaces
- **10% Brand** — `primary-*` (cyan) for ALL interactive elements only
- **Semantic** — emerald=success, amber=warning, red=error — functional use only, never decorative
- **Orange `accent-*`** — medals/rankings ONLY; never buttons, nav, or role badges
- **Button secondary** → `bg-surface-800 text-white` (dark navy, not orange)
- **Role badges** → all use `bg-surface-100 text-surface-600 border-surface-200` (label is differentiator)

### Interaction States
- Active nav item: `bg-primary-500 text-white` (filled cyan pill)
- Button active: `active:scale-95`
- Focus ring: `focus:ring-2 focus:ring-primary-500/30 focus:border-primary-500`
- Row hover: `hover:bg-primary-50` or `hover:bg-surface-50`

### Target Audience UX Rules
- **Judges**: Large touch targets (min 48px), very large font for scores/numbers, minimal steps to complete a score
- **Emcees**: Clear status badges (scored/unscored), prominent search, always-visible participant count
- **Organisers**: Scannable stat cards, collapsible complexity, clear workflow steps
