# CLAUDE.md

**Kyrove (code name BES)** — full-stack app for running dance battle events: registration, judging, scoring, real-time battle control, public results portal. The product is called Kyrove; packages, directories, and env vars still say `BES`.

Stack: Vue 3 (`BES-frontend/`) → Nginx → Spring Boot (`BES/`, port 5050) → PostgreSQL. Session-based auth (Spring Security, **not JWT** — frontend always sends `credentials: 'include'`). STOMP WebSocket on `/ws`. Flyway migrations with `ddl-auto=validate` (schema is never auto-created).

## Route by task — read the matching file BEFORE starting work

This file is deliberately small. Domain depth lives in `docs/agents/` and is **mandatory reading when the trigger matches**:

| If the task involves… | Read first |
|---|---|
| Battle mode in any form — bracket, 7-to-Smoke, phases, votes, overlay, "battle data not persisting" | `docs/agents/battle-system.md` — answer its 6 questions before opening any file |
| Any UI work — components, styling, layout, modals, nav | `docs/agents/design-system.md` |
| Roles, permissions, routes, feature inventory, "where does X happen" | `docs/agents/routes-and-roles.md` |
| Spawning agents, exploring >3 files, web research, batch edits, plan execution | `docs/agents/dispatch.md` (+ `docs/agents/templates.md` for prompts) |
| Deciding: done? stuck? escalate? ask the user? change approach? | `docs/agents/judgment.md` |
| Editing CLAUDE.md, docs/agents/*, memory, or .claude config | `docs/agents/maintenance.md` |
| Starting a session — orientation, in-flight work, known traps | `docs/agents/letter.md` |
| REST/DTO/error conventions in detail | `docs/API_CONVENTIONS.md` |

Why the system is shaped this way: `docs/agents/diagnosis.md`.

## Instruction precedence (when sources conflict)

1. The user's live instruction in this conversation
2. CLAUDE.md and `docs/agents/*`
3. Auto-memory (`~/.claude/projects/.../memory/`)
4. Skill / command text

Additionally: **harness/tool-description restrictions outrank docs thresholds** — e.g. when the harness says "don't spawn agents unless the user asks", that beats dispatch.md's delegation triggers; stay on codegraph + targeted reads.

No known conflicts remain (the `/docker` command and `local-docker-verify` description were aligned to the Docker rule below on 2026-07-03, user-approved). If you find a NEW contradiction between layers, don't silently pick one: apply the precedence, then report it (see maintenance.md).

## Commands

### Backend (from `BES/`)
```bash
mvn spring-boot:run                             # Run dev server (port 5050)
mvn test                                        # All integration tests
mvn test -Dtest=AuthControllerIntegrationTest   # Single test class
JAVA_HOME=/opt/homebrew/opt/openjdk@17 mvn clean package -DskipTests   # Build JAR — from repo root add: -f BES/pom.xml. The ambient JDK is not 17 in every shell; always set JAVA_HOME
```

### Frontend (from `BES-frontend/`)
```bash
npm install / npm run dev (localhost:5173) / npm run build / npm test (+ test:watch, test:coverage)
```

### Docker (from root; backend :5050, frontend :80/443, postgres :5433)
```bash
docker compose up -d --build    # DEFAULT — multi-stage Dockerfile builds the JAR in-container and reuses dep layers
```
`--no-cache` ONLY when `pom.xml` / `package.json` / a `Dockerfile` changed, or staleness is proven — reflex use writes ~1 GB dead cache per rebuild. **If a change looks missing from the running app, in order:** ① hard-refresh the browser (Cmd+Shift+R — a cached JS bundle is the #1 false alarm), ② `docker ps` → CREATED time confirms the container was actually recreated, ③ only then `docker compose build --no-cache <service> && docker compose up -d <service>`. Teardown: `docker compose down --rmi local`. Weekly (user's alias `dclean`): `docker container prune -f && docker image prune -f && docker builder prune -f --filter "until=72h"`.

### Environment
Copy `.env.example` → `.env` (needs `EMAIL`, `EMAIL_PASSWORD`, `SPRING_DATASOURCE_*`, `DOMAIN`). Never read or loosen protections on `.env`, `credentials.json`, `beskey.pem`.

## Architecture facts (always relevant)

```
Vue 3 → Nginx (proxies /api/* and /ws) → Spring Boot :5050 → PostgreSQL
```
- Backend packages: `controllers/ services/ respositories/ models/ dtos/ config/ mapper/` plus `clients/ enums/ parsers/ request/ utils/` — the repos directory is **intentionally spelled `respositories`**; keep it
- Frontend: `views/` (pages), `components/`, `utils/api.js` (fetch layer), `utils/auth.js` (Pinia store + tier), `router/index.js` (RBAC + tier guards)
- **Layer order is bottom-up, always:** DB migration → JPA entity → service → controller → DTO → frontend API call. Never change the frontend contract before the backend
- API digest: `/api/v1/{resource}`; GET/POST/DELETE; DTOs `Add|Get|Update|Delete{Entity}Dto`; 404 → empty `[]` (full rules: `docs/API_CONVENTIONS.md`)

## Hard rules (always apply)

1. **Branch scope.** Invoke the `git-scope-guard` skill before any implementation task. Prefix contract: `feat/X` (feature X + bugs it introduced) · `fix/X` · `ui/X` · `refactor/X` (no behavior change) · `chore/X` · `docs/X`. Out-of-scope task + dirty branch → create a GitHub issue and stop. Out-of-scope + clean branch → offer issue vs new branch. "It's just a small fix" is not an exception.
2. **Migrations.** New `V{n}__{snake_case}.sql` files only — never edit an applied one. Next number = current max + 1, derived by `ls BES/src/main/resources/db/migration | sort -V | tail -1` (plain `ls | tail` lies — lexical sort puts V9 after V50). Never reference Hibernate hash-named FK constraints (prod rollback incident — use the pg_constraint lookup; memory: feedback_migration_hash_constraints). H2 tests skip Flyway — verify new migrations against real Postgres. An applied-but-uncommitted migration file is a time bomb: commit it the same session.
3. **Battle bugs: permissions before logic.** Most "not persisting / not working" reports are a 403 — missing `EMCEE` on `@PreAuthorize`, or a PRO-tier organiser blocked by `TierAccessService`. Check the Network tab and the matrix in `docs/agents/battle-system.md` before reading any business logic.
4. **Done = evidence.** Build + tests run with output read, feature actually exercised (both themes for UI), edge case touched, per `docs/agents/judgment.md` §2 and the `superpowers:verification-before-completion` skill. Never claim done on "compiles and looks right"; report what you did NOT verify.
5. **Explore cheap-first.** `codegraph_explore` (pre-indexed, one call) → targeted Read → `Explore` agent for fan-out → see `docs/agents/dispatch.md`. Don't pull whole files into the main conversation when a graph query answers it; don't spawn an agent to read one file.
6. **Git hygiene.** Commit/push only when the user asks. Never fold unrelated files into a commit; never touch in-flight WIP files that aren't part of your task (`git status` first — wrong-branch work has cost hours here; memory: common_mistakes "verify git branch first" + "root cause before patching").
7. **Skills are triggered, not ceremonial.** Invoke the skill whose description matches the task (user skill requests always win); don't invoke skills speculatively "just in case". At most one *workflow-orchestration* skill per task (brainstorming / writing-plans / executing-plans / subagent-driven-development) — the mandated gates in these rules (git-scope-guard, TDD, verification-before-completion, pre-push-verify) do NOT count toward that cap. Before any push/PR: `pre-push-verify`. For releases: the `release` skill only.
8. **Write-as-you-go.** Any conclusion that must survive the session (findings, decisions, handoffs) gets written to a file when it's reached — long contexts get summarized and unwritten conclusions die.

## Testing

- Backend: JUnit 5 + Spring Boot Test + MockMvc, H2 in-memory via `@ActiveProfiles("test")` (`application-test.properties`)
- Frontend: Vitest + Vue Test Utils in `src/utils/__tests__/`
- TDD for features/bugfixes per `superpowers:test-driven-development`: failing test first

---
Maintained per `docs/agents/maintenance.md` (backups in `docs/agents/backups/`). Last structural rewrite: 2026-07-03. Keep this file ≤ ~200 lines — extract and route instead of growing it.
