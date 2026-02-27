---
description: Add a full-stack feature spanning database, backend, and frontend
---
// turbo-all

# Add Full-Stack Feature

Follow these steps **in order** to avoid cross-layer dependency issues.

## 1. Database Layer (if new tables/columns needed)

Create a new Flyway migration file:
```
BES/src/main/resources/db/migration/V{N}__description.sql
```
- Use the next version number (check existing migrations in that directory)
- Write idempotent SQL (`CREATE TABLE IF NOT EXISTS`, `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`)

## 2. Backend Layer

1. **Entity** — Update or create JPA entity in `BES/src/main/java/com/example/BES/models/`
2. **Repository** — Add/update repository in `respositories/`
3. **DTO** — Create request/response DTOs in `dtos/`
4. **Service** — Implement business logic in `services/`
5. **Controller** — Add endpoint in `controllers/`, annotate with OpenAPI: `@Operation`, `@ApiResponse`

## 3. Frontend Layer

1. **API function** — Add fetch call to `BES-frontend/src/utils/api.js` (or `adminApi.js` for admin endpoints) following existing patterns
2. **Component/View** — Update or create Vue component in `components/` or `views/`
3. **Router** — If new page, add route to `src/router/index.js`

## 4. Tests

1. **Backend** — Write `@SpringBootTest` test with `@ActiveProfiles("test")` using H2
2. **Frontend** — Write Vitest test in `src/utils/__tests__/` for the new API function

## 5. Verify

1. Run backend tests: `cd BES && ./mvnw test`
2. Run frontend tests: `cd BES-frontend && npm test`
3. Docker build: `docker compose build`
