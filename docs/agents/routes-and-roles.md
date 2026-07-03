# Routes, Roles & Feature Map

> Loaded on demand via CLAUDE.md routing. Read when the task involves **roles/permissions, adding or changing routes, "where does X happen", or finding which component/controller owns a feature**. Last verified: 2026-07-03. Counts drift — derive with `ls` rather than trusting numbers here.

## Backend map (`BES/src/main/java/com/example/BES/`)

| Package | Purpose |
|---------|---------|
| `controllers/` | REST controllers under `/api/v1/` (+ STOMP `BattleTimerController`) |
| `services/` | Business logic (tier access, session tokens, emcee category presence, audition display, …) |
| `respositories/` | Spring Data JPA repos — **directory name is intentionally misspelled, keep it** |
| `models/` | JPA entities → PostgreSQL tables |
| `dtos/` | Request/response DTOs |
| `config/` | Security, CORS, WebSocket, Google API config |
| `mapper/` | Entity ↔ DTO converters |
| `clients/`, `enums/`, `parsers/`, `request/`, `utils/` | Google API clients, shared enums, import parsers, request helpers, utilities |

**Controllers:**

| Controller | Base Path | Purpose |
|-----------|-----------|---------|
| `AuthController` | `/api/v1/auth` | Login, logout, session check, token-link login (`/auth/me` returns `tier`) |
| `EventController` | `/api/v1/event` | CRUD events, participants, scores, categories, scoring criteria, feedback toggle, pickup crews, session tokens |
| `BattleController` | `/api/v1/battle` | Battle mode, voting, judges, images, phase state machine (most endpoints gated by `TierAccessService`) |
| `BattleTimerController` | STOMP `/app/battle/timer/...` | WebSocket timer relay for battle overlay |
| `EmceeCategoryController` | `/api/v1/emcee` (`/active-category` POST/DELETE, `/active-categories` GET) | Heartbeat-based registry of categories actively run by emcees |
| `EventAuditionDisplayController` | `/api/v1/event/audition-display` | Live round/timer state for the OBS audition display |
| `AdminController` | `/api/v1/admin` | Admin CRUD for categories, judges, scores, feedback groups/tags, organiser tier |
| `AppConfigController` | `/api/v1/app-config` | Global runtime config (accent color) broadcast to all clients |
| `ResultsController` | `/api/v1/results` | Public results by reference code, QR access |
| `SecurityController` | `/api/v1/security` | CSRF token endpoint |
| `GoogleDriveFileController` | `/api/v1/files` | Google Drive file listing |
| `GoogleDriveFolderController` | `/api/v1/folders` | Google Drive folder listing |
| `GoogleSheetsController` | `/api/v1/sheets` | Google Sheets participant import |

**Key models:**

| Model | Purpose |
|-------|---------|
| `Event` | Event with payment flag, judging mode, results release toggle, `feedbackEnabled`, `animTheme` |
| `EventParticipant` / `EventParticipantId` | Participant linked to event; walk-ins, stage name, team format, display name |
| `EventParticipantTeamMember` | Individual member rows for team entries |
| `EventCategory` | Category (formerly "genre") linked to event with format, display fields, scoring config |
| `EventCategoryParticipant` / `EventCategoryParticipantId` | Participant-category junction with audition number |
| `EventCategoryParticipantMember` | Per-category team member roster |
| `EventCategoryBattleGuest` | Battle-only guest participants attached to a category |
| `BattleCategoryState` | Persisted bracket / smoke / phase state per (event, category) |
| `BattleActiveCategory` | Which category each event is currently running |
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

## Frontend map (`BES-frontend/src/`)

| Path | Purpose |
|------|---------|
| `views/` | Page-level components (see Routes below) |
| `components/` | Reusable UI components |
| `utils/api.js` | Frontend→backend fetch functions |
| `utils/adminApi.js` | Admin-specific API functions |
| `utils/auth.js` | Pinia auth store + active event helpers; persists `tier`, holds `activeEventBattleEnabled` |
| `utils/battleLogic.js` | Battle game logic |
| `utils/websocket.js` | WebSocket/STOMP setup |
| `utils/eventUtils.js` | Shared event/category helpers |
| `utils/dropdown.js`, `utils/useScrollReveal.js` | Dropdown state utilities; scroll-reveal animation composable |
| `utils/useTierAccess.js` | Composable + `resolveBattleEnabled` helper for PRO/MAX gating (router guard reuses it) |
| `utils/branding.js` | Central `APP_NAME` / `APP_TAGLINE` / `APP_DESCRIPTION` constants (Kyrove) |
| `router/index.js` | Client-side routes with RBAC + tier guards |

## Routes

**Role columns below mirror `meta.allowedRoles` in `BES-frontend/src/router/index.js` (re-verified 2026-07-03). When in doubt, re-derive — this is the truth source:**
```bash
grep -n -E "path:|allowedRoles|PUBLIC_ROUTES" BES-frontend/src/router/index.js
```

Guard-exempt routes (`PUBLIC_ROUTES` constant — the router guard skips auth entirely; the session/token landings authenticate via session-token internally):

| Route | View | Purpose |
|-------|------|---------|
| `/login`, `/403` | `Login`, — | Login page; forbidden landing |
| `/results`, `/results-qr` | `Results`, `ResultsQR` | Public results portal (reference code / QR) |
| `/battle/overlay` | `BattleOverlay` | Stream overlay (OBS/broadcast display) |
| `/battle/chart` | `Chart` | 7-to-Smoke live chart |
| `/battle/bracket` | `BracketVisualization` | Live bracket with winner animation + LED ticker |
| `/audition/display` | `AuditionDisplay` | Live audition round + timer OBS source |
| `/auth/token/...` | `TokenAuth` / `TokenAuthRole` | Token-link landings (consume EMCEE/HELPER/JUDGE session-token links) |
| `/judge/session`, `/emcee/session`, `/helper/session` | `JudgeSessionView` / `EmceeSessionView` / `HelperSessionView` | Token-session landings — guard-exempt, content requires a consumed token |

Authenticated routes (guard checks `allowedRoles`; unauthenticated → `/login`):

| Route | `allowedRoles` (router truth) | View | Purpose |
|-------|-------|------|---------|
| `/` | any authenticated | `MainMenu` | Role-filtered quick-action home |
| `/event/select` | any authenticated | `EventSelector` | Select active event (redirect target when no event set — **not** public) |
| `/events` | ADMIN, ORGANISER | `Events` | Event list with search |
| `/events/:eventName` | ADMIN, ORGANISER, HELPER | `EventDetails` | Event overview, category accordion, Drive setup, session links, participant management |
| `/event/audition-number` | ADMIN, ORGANISER, HELPER | `AuditionNumber` | Display audition number on screen |
| `/event/audition-adjust` | ADMIN | `AuditionAdjust` | Tune/adjust audition number assignments |
| `/event/update-event-details` | ADMIN | `UpdateEventDetails` | Participant table, judge assignment, verification, payment |
| `/event/audition-list` | ADMIN, EMCEE, JUDGE | `AuditionList` | Role-split view: Judge scores / Emcee timer+status |
| `/event/score` | ADMIN, EMCEE, ORGANISER, HELPER | `Score` | Top N qualifier: Control vs Broadcast modes, live WS updates, tie-pool resolver |
| `/event/crew-formation` | ADMIN, ORGANISER | `CrewFormation` | Form pickup crews from individual participants |
| `/battle/control` | ADMIN, ORGANISER, EMCEE (+ MAX-tier check in guard & backend) | `BattleControl` | Bracket seeding, phases, live match control; Emcee gets the readonly path (battle-system.md pitfall #7) |
| `/battle/judge` | JUDGE, ADMIN | `BattleJudge` | Battle judge voting screen (**not** public) |
| `/admin` | ADMIN | `AdminPage` | Category/judge/image/feedback-tag CRUD + organiser tier + global accent color |

## Workflow by role

### Admin
Full access (always MAX tier). Manages global resources on `/admin`: categories, judges, images, feedback tag groups/tags, **organiser PRO/MAX tier**, global accent color.

### Organiser (PRO or MAX)
**Tier matters** — PRO organisers see no battle UI; battle endpoints return 403. MAX gets the full lifecycle. Tier set by Admin on `/admin`.

Event lifecycle:
1. Create event on `/events` (link Google Drive folder, add categories)
2. Import participants from Google Sheets or add walk-ins on `EventDetails`
3. Manage participants — assign judges, verify & email, track payment, stage names/team format (participant management lives in `EventDetails`; the standalone `/event/update-event-details` view is ADMIN-only per the router)
4. Customise per-event email template
5. Configure scoring criteria per category (single score or weighted multi-criteria), toggle `feedbackEnabled`
6. Generate session links (Emcee / Helper / per-judge) from EventDetails
7. Audition day: check `/event/score` (Top N qualifier) — note `/event/audition-list` is ADMIN/EMCEE/JUDGE only per the router
8. Form pickup crews on `/event/crew-formation`
9. **MAX only** — Battle day: seed bracket on `/battle/control`, run phases (IDLE → LOCKED → VOTING → REVEALED), pick animation theme (IMPACT / HYPE), stream `/battle/bracket` + `/battle/overlay`
10. Release results on `/event/score` → visible on public `/results` via reference code/QR

### Emcee
Event-day read/announce role. Multiple emcees run **different categories concurrently** — heartbeat-based tracker shows an "ACTIVE" badge; second emcee gets a confirm prompt before overriding.
- `/emcee/session` — token landing with Display URLs (one OBS URL per category)
- `/event/audition-list` — timer, participant status, swipe / `‹ PREV` / `NEXT ›` rounds via `EmceeRoundView`
- `/event/score` — live Broadcast-mode scoreboard

### Judge
Scoring only:
- `/judge/session` — token landing (refresh extends; tied to judge lifecycle)
- `/event/audition-list` — `SwipeableCardsV2` score entry, per-aspect scoring if custom criteria set, feedback tags + note via `FeedbackPopout` (only when event `feedbackEnabled`)
- `/battle/judge` — vote during VOTING phase; result on REVEALED

### Helper
Check-in role: `/helper/session` token landing; runs check-in flow; sees results QR in post-check-in dialog (GET `/api/v1/results/qr` allows HELPER). Router also grants HELPER: `/events/:eventName`, `/event/audition-number`, `/event/score`.

### Public (no login)
`/results`, `/results-qr` (reference code lookup), `/battle/overlay` (OBS), `/battle/bracket` (projector), `/battle/chart` (7-to-Smoke), `/audition/display` (OBS round/timer; live via WebSocket; survives refresh).

## Feature inventory

| Feature | Location | Notes |
|---------|----------|-------|
| Google Sheets import | `EventDetails`, `GoogleSheetsController` | Imports stage name, team format, member roster; comma-separated category values split |
| Walk-in registration | `EventDetails` → `addWalkinToSystem` | Team members, team name |
| Participant verification + email | `UpdateEventDetails` | Individual or batch; uses `EventEmailTemplate` |
| Payment tracking | `UpdateEventDetails` | Per-participant payment flag |
| Custom scoring criteria | `ScoringCriteriaModal` / `ScoringCriteriaPanel` | Weighted criteria per event/category; judge scores per aspect |
| Audition feedback | `FeedbackPopout` | Tags + free-text note per judge/participant; hidden when `feedbackEnabled` false |
| Feedback system toggle | `EventDetails` Event Settings | Per-event boolean; live WS broadcast to judge UIs |
| Results portal | `/results`, `/results-qr` | Reference code or QR; organiser release toggle |
| Top N qualifier | `Score.vue` (Control mode) | Top 8/16/32/ALL picker; tie-pool stepper; black-box rule hides expanded pool from Broadcast/public |
| Score broadcast mode | `Score.vue` (Broadcast mode) | Uniform-row leaderboard, 2-col packing at Top 16/32, glowing Top N separator; live WS updates |
| Battle phase state machine | `BattleControl` (MAX only) | IDLE → LOCKED → VOTING → REVEALED via WebSocket |
| Battle tier gating | `TierAccessService` (BE) + `useTierAccess` (FE) | PRO blocked; UI hidden (no lock icons); 20+ endpoints + `GET /battle/state` gated |
| Bracket seeding | `BattleControl` | By Rank (asc/desc), High↔Low pairing, Random; slot-picker UI |
| Tie-breaker persistence | `Score` + localStorage | Survives refresh, scoped per event/category |
| Live bracket visualization | `/battle/bracket` | Winner animations, LED ticker |
| 7-to-Smoke format | `/battle/chart` | Cumulative smoke chart |
| Animation theme | `BattleControl` Overlay Settings → `event.animTheme` | IMPACT (default) or HYPE; broadcast via `/topic/battle/{event}/overlay-config`; displays switch via `data-anim-theme` |
| Pickup crew formation | `/event/crew-formation` | Named crews from individual participants |
| Concurrent emcee sessions | `EmceeCategoryController` + `/topic/emcee/active-categories/{event}` | Heartbeat (~90s release); ACTIVE badge + confirm prompt |
| Audition Display (OBS) | `/audition/display?event=X` | Public; timer reconstructs from `timerStartedAt + timerDuration`; survives refresh |
| Session-token links | EventDetails → Session Links + `TokenAuth` | EMCEE/HELPER permanent + per-judge tokens; refresh-to-extend; consumed via `/auth/token?token=...` |
| Judging mode | `EventDetails` | Default (single score) or custom criteria |
| Judge scoring card | `SwipeableCardsV2` | `py-5` keypad, `py-3` full-score button, `w-[97%]` card, `p-2` padding; Reset/GoTo live in AuditionList context bar |
| Event selector grid | `EventSelector` | ≤4 events → 1 col, 5+ → 2-col; no scroll; always passes `?redirect=` back |
| Helper check-in | `HelperSessionView` + dialog | Results QR in post-check-in dialog |
| Global runtime config | `AppConfigController` + `/topic/app-config` | Admin sets accent color → broadcast to all clients |
| Judge assignment mode | `EventDetails` category toggle; per-judge pools everywhere (shipped PR #187, 2026-07-03) | Per-judge audition numbers + Top-N score pools; OBS multi-panel; plans: `docs/superpowers/plans/2026-06-28-judge-assignment-mode-{backend,frontend}.md`; V50 migration |
