---
name: local-docker-verify
description: >
  Use this skill to verify code changes locally and rebuild affected Docker
  containers. Invoke ONLY when the user explicitly asks to test, rebuild, or
  deploy — e.g. "does it work", "try it", "rebuild", "deploy", "test this".
  Do NOT auto-trigger after individual file edits; wait for the user to signal
  they are ready to verify.

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

## Step 2b — Verify no unmerged sibling branches will be dropped

Before rebuilding, confirm the current branch includes all in-progress sibling branches. If any sibling branch has commits ahead of the default and no merged PR, a rebuild from the current branch will silently exclude that work.

```bash
DEFAULT=$(git symbolic-ref refs/remotes/origin/HEAD 2>/dev/null | sed 's@^refs/remotes/origin/@@' || echo "master")
CURRENT=$(git branch --show-current)

git for-each-ref --format='%(refname:short)' refs/heads/ \
  | grep -vE "^(master|main|${CURRENT})$" \
  | while read branch; do
      AHEAD=$(git log origin/${DEFAULT}.."$branch" --oneline 2>/dev/null | wc -l | tr -d ' ')
      if [ "$AHEAD" -gt 0 ]; then
        MERGED=$(gh pr list --head "$branch" --state merged --limit 1 2>/dev/null | wc -l | tr -d ' ')
        NOT_IN_CURRENT=$(git log HEAD.."$branch" --oneline 2>/dev/null | wc -l | tr -d ' ')
        if [ "$MERGED" -eq 0 ] && [ "$NOT_IN_CURRENT" -gt 0 ]; then
          echo "  ⚠️  $branch — $AHEAD commits ahead of ${DEFAULT}, not merged into ${CURRENT}"
        fi
      fi
    done
```

If any are found, **stop and warn before rebuilding**:
```
⚠️ Docker rebuild blocked — unmerged sibling branches detected:
  feat/some-feature   (12 commits ahead of master, not in current branch)

Rebuilding now will drop their changes from the running containers.

Recommended: get each sibling branch merged to {DEFAULT} via PR first, then:
  git fetch origin && git rebase origin/{DEFAULT}

Do not rebuild until sibling branches are merged to {DEFAULT} and this branch
has been rebased onto the updated origin/{DEFAULT}.
```

Do NOT use `git merge {sibling-branch}` — always keep history linear via rebase.

Do not proceed to Step 3 until all sibling branches are resolved or the user explicitly confirms they want to rebuild without them.

If no unmerged siblings are found: proceed to Step 3 silently.

---

## Step 3 — Rebuild Affected Docker Containers

### When to use `--no-cache`

**Default: plain `--build`.** The multi-stage Dockerfiles already invalidate dependency layers when their inputs (`pom.xml`, `package.json`) change. Docker's content-hash caching is reliable — trust it.

Only add `--no-cache` when ONE of these is true:
- `pom.xml` was modified this session
- `package.json` or `package-lock.json` was modified this session
- Any `Dockerfile` was modified this session
- A previous build produced unexpected behavior and you suspect a stale layer (rare)

Reflex `--no-cache` writes ~1 GB of new BuildKit cache every rebuild that will never be reused — disk climbs from ~5 GB steady-state to 40+ GB within weeks.

### Targeted rebuild (preferred — faster)

```bash
# Default — fast rebuild, reuses dependency layers
docker compose up -d --build <service>
```

If a `--no-cache` trigger applies (see above):
```bash
docker compose build --no-cache <service>
docker compose up -d <service>
```

### Database container

```bash
# DB image rarely changes — let cache do its job.
# Only rebuild when migration files, docker-compose.yml DB section, or init scripts were modified.
docker compose up -d --build db
```

### Full rebuild (shared layer changed — e.g. docker-compose.yml, root .env)

```bash
# Teardown with --rmi local so old images don't pile up as <none> dangling tags.
docker compose down --rmi local --remove-orphans
docker compose up -d --build
```

If `--no-cache` is also warranted (pom.xml / package.json / Dockerfile changed):
```bash
docker compose down --rmi local --remove-orphans
docker compose build --no-cache frontend backend
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

## Step 6 — Disk Hygiene (run after successful verify if disk is heavy)

After Step 5 reports green, check Docker's disk usage:

```bash
docker system df
```

If **reclaimable** exceeds ~5 GB for images or ~10 GB for build cache, clean up:

```bash
docker container prune -f
docker image prune -f
docker builder prune -f --filter "until=72h"   # keep last 3 days of cache
```

Or, if the user has the `dclean` alias configured, just suggest running it:

```bash
dclean
```

Do **not** run `docker system prune --volumes` from this skill — that wipes the Postgres data volume. Volume cleanup must be an explicit user request.

When to volunteer cleanup vs stay quiet:
- ✅ Volunteer when `docker system df` shows reclaimable > 10 GB
- ✅ Volunteer when the build was slow due to disk pressure (extraction errors, "no space" warnings in logs)
- ❌ Stay quiet on routine rebuilds with healthy disk usage

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
| `pom.xml` changed | Rebuild backend with `--no-cache` (dependency layer must re-resolve) |
| `package.json` / `package-lock.json` changed | Rebuild frontend with `--no-cache` (npm install layer must re-run) |
| `Dockerfile` (any) changed | Rebuild affected service with `--no-cache` |
| Disk full / "no space left on device" during build | Run `docker system df`, then `docker builder prune -af && docker image prune -af`, retry |
| Flyway migration version conflict | Spring Boot will log `FlywayException`. Fix migration filename versioning, then rebuild backend only |
| User says "skip docker, just check local" | Run Steps 1–2 only, report local build status |
| `mvnw` not executable | Run `chmod +x ./mvnw` first |