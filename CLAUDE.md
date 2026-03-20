# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Project Is

**BES (Battle Event System)** — a full-stack web app for managing dance battle events (registration, judging, scoring, real-time battle control).

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
docker-compose up --build   # Build and start all services
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
| `controllers/` | 7 REST controllers under `/api/v1/` |
| `services/` | 16 business logic services |
| `respositories/` | Spring Data JPA repos |
| `models/` | 11 JPA entities → PostgreSQL tables |
| `dtos/` | ~40 request/response DTOs |
| `config/` | Security, CORS, WebSocket, Google API config |
| `mapper/` | Entity ↔ DTO converters |

**Controllers:**

| Controller | Base Path | Purpose |
|-----------|-----------|---------|
| `AuthController` | `/api/v1/auth` | Login, logout, session check |
| `EventController` | `/api/v1/event` | CRUD events, participants, scores, genres |
| `BattleController` | `/api/v1/battle` | Battle mode, voting, judges, images |
| `AdminController` | `/api/v1/admin` | Admin CRUD for genres, judges, scores |
| `GoogleDriveFileController` | `/api/v1/files` | Google Drive file listing |
| `GoogleDriveFolderController` | `/api/v1/folders` | Google Drive folder listing |
| `GoogleSheetsController` | `/api/v1/sheets` | Google Sheets participant import |

### Frontend Layout (`BES-frontend/src/`)

| Path | Purpose |
|------|---------|
| `views/` | 14 page-level components (Login, MainMenu, BattleControl, etc.) |
| `components/` | 18 reusable UI components |
| `utils/api.js` | ~40 frontend→backend fetch functions |
| `utils/adminApi.js` | ~10 admin-specific API functions |
| `utils/websocket.js` | WebSocket/STOMP setup |
| `utils/battleLogic.js` | Battle game logic |
| `router/index.js` | Client-side routes |

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
- Next version after `V1__baseline.sql` would be `V2__...`

### Layer Modification Order

**Always modify bottom-up**: DB migration → JPA entity → service → controller → DTO → frontend API call. Never change the frontend API contract without updating the backend first.

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
