# BES (Battle Event System) — Architecture Guide

> **For AI agents and developers.** Read this before making any code changes.

## Project Structure

```
BES/                          ← Root
├── BES/                      ← Spring Boot backend (Java 17)
│   ├── pom.xml
│   └── src/main/java/com/example/BES/
│       ├── controllers/      ← REST endpoints (/api/v1/*)
│       ├── services/         ← Business logic
│       ├── respositories/    ← Spring Data JPA repos
│       ├── models/           ← JPA entities (→ PostgreSQL tables)
│       ├── dtos/             ← Request/response data transfer objects
│       ├── config/           ← Security, CORS, WebSocket, Google config
│       ├── enums/            ← Enumerations
│       ├── mapper/           ← Entity ↔ DTO mappers
│       ├── parsers/          ← Google Sheets parsing
│       └── clients/          ← External API clients (Google Sheets)
├── BES-frontend/             ← Vue 3 + Vite frontend
│   ├── src/
│   │   ├── views/            ← Page-level components
│   │   ├── components/       ← Reusable UI components
│   │   ├── utils/            ← API calls (api.js, adminApi.js), helpers
│   │   ├── router/           ← Vue Router config
│   │   └── assets/           ← CSS, fonts, images
│   ├── nginx/                ← Nginx reverse proxy config + SSL certs
│   └── Dockerfile            ← Multi-stage: npm build → nginx
├── docker-compose.yml        ← Orchestrates backend + frontend + postgres
├── Dockerfile                ← Backend Docker image (Java JAR)
└── .agents/workflows/        ← AI-specific workflow templates
```

## Layer Dependency Chain

```
Database (PostgreSQL) → Backend (Spring Boot) → Frontend (Vue 3) → Docker
```

**Always modify bottom-up.** Never change the frontend API contract without first updating the backend.

## Key Files by Layer

| Layer | Key Files | What They Control |
|-------|-----------|------------------|
| **DB** | `db/migration/V*.sql` | Schema (Flyway) |
| **DB** | `models/*.java` | JPA entity ↔ table mapping |
| **API** | `controllers/*.java` | REST endpoints (7 controllers) |
| **API** | `dtos/*.java` | Request/response shapes (~40 DTOs) |
| **API** | `application.properties` | DB connection, mail, Flyway, server port |
| **Frontend** | `utils/api.js` | All frontend→backend fetch calls (~40 functions) |
| **Frontend** | `utils/adminApi.js` | Admin-specific API calls (~10 functions) |
| **Frontend** | `router/index.js` | Client-side routes |
| **Docker** | `docker-compose.yml` | Service orchestration |
| **Docker** | `nginx/default.conf` | Reverse proxy: `/api/*` → backend:5050 |

## Backend Controllers

| Controller | Base Path | Purpose |
|-----------|-----------|---------|
| `AuthController` | `/api/v1/auth` | Login, logout, session check |
| `EventController` | `/api/v1/event` | CRUD events, participants, scores, genres |
| `BattleController` | `/api/v1/battle` | Battle mode, voting, judges, images |
| `AdminController` | `/api/v1/admin` | Admin CRUD for genres, judges, scores |
| `GoogleDriveFileController` | `/api/v1/files` | Google Drive file listing |
| `GoogleDriveFolderController` | `/api/v1/folders` | Google Drive folder listing |
| `GoogleSheetsController` | `/api/v1/sheets` | Google Sheets participant data |

## Authentication

- **Session-based** (not JWT) — uses Spring Security with HTTP session cookies
- Frontend sends `credentials: 'include'` on every fetch call
- Session timeout: 1440 minutes (24 hours)

## Database

- **PostgreSQL 15.2** in Docker (port 5433 external, 5432 internal)
- Migrations managed by **Flyway** (`db/migration/V*.sql`)
- `ddl-auto=validate` — Hibernate checks schema but never modifies it

## Tests

- **Backend:** JUnit 5 + Spring Boot Test + MockMvc, uses H2 (`@ActiveProfiles("test")`)
- **Frontend:** Vitest + Vue Test Utils, tests in `src/utils/__tests__/`
