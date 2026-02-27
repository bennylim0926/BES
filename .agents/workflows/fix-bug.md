---
description: Debug and fix a bug with structured investigation
---

# Fix Bug

## 1. Reproduce & Locate

Determine which layer the bug is in:
- **Frontend error?** → Check browser console, inspect network tab for failed requests
- **Backend error?** → Check `docker compose logs backend` or Spring Boot console
- **Database?** → Connect to PostgreSQL and verify data: `docker compose exec postgres psql -U bes_admin bes_db`

## 2. Identify the Layer Chain

Trace the data flow:
```
Frontend (api.js function) → Backend Controller → Service → Repository → Database
```

Identify which layer breaks. **Only fix that specific layer** unless the root cause spans layers.

## 3. Fix

- Apply the fix to the affected layer
- If fixing a backend bug, check if the DTO shape or endpoint behavior changed — if so, update the frontend

## 4. Write Regression Test

Add a test that specifically covers the bug scenario so it doesn't regress:
- Backend: `@SpringBootTest` test with `@ActiveProfiles("test")`
- Frontend: Vitest test mocking the specific failure scenario

## 5. Verify

```bash
# Backend
cd BES && ./mvnw test

# Frontend
cd BES-frontend && npm test

# Full stack smoke test
docker compose build && docker compose up -d
docker compose logs backend
```
