---
name: local-docker-verify
description: >
  Use this skill whenever code changes have been made to the codebase —
  including feature additions, bug fixes, refactors, config changes, dependency
  updates, or any edit to source files — to verify the changes locally first,
  then rebuild and verify the affected Docker containers.

  ALWAYS trigger this skill automatically (without waiting for the user to ask)
  when you detect any of these signals:
    - You just wrote, edited, or scaffolded code files
    - The user says "done", "try it", "test this", "does it work", "deploy",
      "rebuild", or similar after a code change
    - A compile/lint/type error was just fixed
    - A dependency or config file (package.json, requirements.txt, Dockerfile,
      docker-compose.yml, .env, etc.) was modified

  This skill: (1) runs a local build/compile check, (2) identifies which of
  the three containers (frontend, backend, database) are affected, (3) rebuilds
  only those containers with --no-cache, (4) verifies all containers are healthy,
  and (5) reports a clear status summary.
---

# Local → Docker Verify Skill

Verify code changes locally first, then rebuild only affected Docker containers
and confirm everything is running.

---

## Stack

| Layer | Technology |
|---|---|
| Frontend | Vue.js (Vite) |
| Backend | Java Spring Boot (Maven) |
| Database | PostgreSQL |

---

## Container Map

| Container | Affected by changes to |
|---|---|
| `frontend` | `frontend/src/`, `.vue` files, `vite.config.*`, `package.json`, `package-lock.json`, frontend `.env` |
| `backend` | `backend/src/`, `*.java`, `pom.xml`, `application.properties` / `application.yml`, backend `.env` |
| `database` | `db/`, Flyway/Liquibase migration files (`V*.sql`), `docker-compose.yml` DB service definition, DB init scripts |

> If unsure which containers are affected, rebuild all three. When in doubt,
> over-rebuild rather than miss an affected service.

---

## Step 1 — Identify Affected Containers

Based on the files changed in this session, determine which containers need
to be rebuilt. State your reasoning explicitly, e.g.:

> "Changes were made to `backend/routes/auth.js` and `package.json` →
> rebuilding **backend** only."

If the change touches a shared layer (e.g. environment variables, shared types,
`docker-compose.yml` itself) → rebuild **all three**.

---

## Step 2 — Local Build / Compile Check

Before touching Docker, verify the code compiles locally. Run only the checks
for affected layers.

**Frontend — Vue.js / Vite**
```bash
cd frontend
npm install          # re-sync deps if package.json changed
npm run build        # Vite production build — catches template/import/TS errors
npm run lint         # optional but recommended
```
Success: Vite outputs `dist/` with no errors. Any Vue template compile error,
missing import, or type error surfaces here before Docker.

**Backend — Java Spring Boot / Maven**
```bash
cd backend
./mvnw clean compile           # fast compile — no tests, no fat jar
# OR run tests too:
./mvnw clean test
# OR full package without tests (for speed):
./mvnw clean package -DskipTests
```
Watch for: Java compilation errors, missing Spring beans, bad `@Value`
property bindings, and Flyway migration conflicts Spring reports at startup.

**Database — PostgreSQL migrations**
```bash
# If Flyway is configured, validate without running:
./mvnw flyway:validate \
  -Dflyway.url=jdbc:postgresql://localhost:5432/<db> \
  -Dflyway.user=<user> -Dflyway.password=<pass>
# If no local PostgreSQL available, skip here — Spring Boot will
# validate Flyway migrations on startup and errors appear in Step 4 logs.
```

If the local build **fails**: stop here, report the compiler output, fix it,
and restart from Step 1. Do not proceed to Docker with broken code.

If the local build **passes**: proceed to Step 3.

---

## Step 3 — Rebuild Affected Docker Containers

**Targeted rebuild (preferred — faster)**
```bash
# frontend and/or backend: always --no-cache (code changes must not be stale)
docker compose stop <service>
docker compose rm -f <service>
docker compose build --no-cache <service>
docker compose up -d <service>
```

**Database container — use cache unless DB files changed**
```bash
# DB image rarely changes; only use --no-cache if migration files,
# docker-compose.yml DB section, or init scripts were modified.
docker compose stop db
docker compose rm -f db
docker compose build db          # no --no-cache (PostgreSQL image is large)
docker compose up -d db
```

**Full rebuild (shared layer changed — e.g. docker-compose.yml, root .env)**
```bash
docker compose down
docker compose build --no-cache frontend backend   # skip --no-cache for db
docker compose build db
docker compose up -d
```

Wait for all commands to complete before Step 4.

---

## Step 4 — Verify All Containers Are Running

```bash
docker compose ps
```

Check every container shows `running` / `Up`. Then health-check each:

**Frontend (Vue.js served by Nginx or Vite preview)**
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:<FRONTEND_PORT>
# Expect: 200
```

**Backend (Spring Boot — use Actuator health endpoint)**
```bash
curl -s http://localhost:<BACKEND_PORT>/actuator/health
# Expect: {"status":"UP"}
# If Actuator not enabled, try the root or any known endpoint:
curl -s -o /dev/null -w "%{http_code}" http://localhost:<BACKEND_PORT>/
```

**Database (PostgreSQL)**
```bash
docker compose exec db pg_isready -U <db_user>
# Expect: /var/run/postgresql:5432 - accepting connections
```

If any container is unhealthy, pull its logs immediately:
```bash
docker compose logs --tail=80 <service>
```

For Spring Boot startup failures, look for:
- `APPLICATION FAILED TO START` — bean/config error
- `FlywayException` — migration conflict
- `Connection refused` to DB — DB container not ready yet (add `depends_on` healthcheck or retry)


---

## Step 5 — Report Status

Always output a status block in this format:

```
## Build & Verification Report

**Trigger:** <what changed>
**Affected containers rebuilt:** frontend | backend | db | all

### Local Build
- [ ] Frontend build: ✅ passed / ❌ failed — <error summary if failed>
- [ ] Backend build:  ✅ passed / ❌ failed
- [ ] DB migrations:  ✅ N/A / ✅ valid / ❌ failed

### Docker Status
| Container  | Status     | Health Check       |
|------------|------------|--------------------|
| frontend   | ✅ running  | HTTP 200            |
| backend    | ✅ running  | HTTP 200 /health    |
| db         | ✅ running  | pg_isready: OK      |

### Result
✅ All good — changes verified locally and in Docker.
— OR —
❌ Issue detected: <summary and next step>
```

---

## Auto-Trigger Checklist (for the agent)

Before responding after any code change, silently run through this:

- [ ] Did I write, edit, or delete any source file this turn?
- [ ] Did I modify a config or dependency file?
- [ ] Did the user just approve or accept a code change?

If **any** box is checked → run this skill without being asked. Do not skip it
to save time; a broken Docker build is worse than a slower response.

---

## Edge Cases

| Situation | Action |
|---|---|
| Port numbers unknown | Check `docker-compose.yml` for port mappings before running curl |
| No `docker compose` (v2) | Fall back to `docker-compose` (hyphen, v1) |
| Spring Boot fails in Docker but passed locally | Check: missing `COPY target/*.jar` in Dockerfile, wrong `SPRING_PROFILES_ACTIVE`, DB URL env var not set |
| Vue build passes locally but blank page in Docker | Check: Nginx config missing `try_files` for Vue Router history mode; `VITE_*` env vars not passed at build time |
| Spring Boot can't reach DB on startup | DB container may still be initialising. Check `depends_on` with healthcheck in `docker-compose.yml`, or wait and `docker compose restart backend` |
| `pom.xml` changed | Always rebuild backend — dependency tree may have changed |
| Flyway migration version conflict | Spring Boot will log `FlywayException`. Fix migration filename versioning, then rebuild backend only |
| User says "skip docker, just check local" | Run Steps 1–2 only, report local build status |
| `mvnw` not executable | Run `chmod +x ./mvnw` first |