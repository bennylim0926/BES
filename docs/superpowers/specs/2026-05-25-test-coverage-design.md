# Test Coverage Design — BES

**Date:** 2026-05-25
**Goal:** Confidence before deployments by covering backend business logic with meaningful tests.

---

## Context

Current state:
- **Backend:** 39 integration tests across 6 controller test classes. 0 service unit tests. `ResultsController` has no integration tests.
- **Frontend:** 6 tests in 1 file (`api.test.js`). No coverage tooling installed.

Primary concern: backend business logic breaking before a deployment goes out.

---

## Approach: Option A — Backend-first, systematic

1. JaCoCo for backend coverage measurement + 70% line coverage gate on services package
2. Mockito unit tests for all 21 services
3. `ResultsController` integration test to close the controller gap
4. Frontend: install `@vitest/coverage-v8`, expand `api.test.js`, add `auth.test.js` and `adminApi.test.js`

---

## Backend — Service Unit Tests

**Location:** `BES/src/test/java/com/example/BES/services/`

**Pattern:** `@ExtendWith(MockitoExtension.class)` — no Spring context. All repository and service dependencies mocked with `@Mock` + `@InjectMocks`.

**Each test class covers:**
- Happy path for each public method
- Not-found / null input edge cases
- Key business logic branches (e.g., score calculation, phase transitions, release toggle)

**Priority grouping:**

| Group | Services |
|---|---|
| Core event | `EventService`, `EventGenreService`, `EventGenreParticipantService`, `EventParticipantService` |
| Scoring & results | `ScoreService`, `ScoringCriteriaService`, `ResultsService`, `AuditionFeedbackService` |
| Battle | `BattleService` |
| Registration & comms | `RegistrationService`, `MailSenderService`, `EmailTemplateService` |
| Supporting | `GenreService`, `JudgeService`, `ParticipantService`, `PickupCrewService`, `QrCodeService`, `GoogleDriveFolderService`, `GoogleDriveFileService`, `GoogleDriveManager`, `GoogleSheetService` |

---

## Backend — ResultsController Integration Test

**Location:** `BES/src/test/java/com/example/BES/controllers/ResultsControllerIntegrationTest.java`

**Pattern:** Same as existing integration tests — `@SpringBootTest`, `@ActiveProfiles("test")`, MockMvc, H2 in-memory DB.

**Cases to cover:**
- Fetch results by valid reference code (results released)
- Fetch results by valid reference code (results not yet released)
- Fetch results by invalid/unknown reference code
- QR access endpoint (`/results-qr`)

---

## Backend — JaCoCo Coverage Gate

**Plugin:** `jacoco-maven-plugin` added to `BES/pom.xml`

**Executions:**
- `prepare-agent` — instruments bytecode before tests run
- `report` — generates HTML + XML to `target/site/jacoco/` after tests
- `check` — enforces 70% line coverage on `com.example.BES.services` package; build fails if below threshold

**Viewing report:** Open `target/site/jacoco/index.html` after `mvn test`

---

## Frontend — Coverage Tooling + Test Expansion

**Install:** `@vitest/coverage-v8` as devDependency — enables `npm run test:coverage`

**New test files:**

| File | What it covers |
|---|---|
| `src/utils/__tests__/api.test.js` | Expand to cover remaining fetch functions in `api.js` |
| `src/utils/__tests__/auth.test.js` | Pinia auth store (`useAuthStore`), `setActiveEvent`, `getActiveEvent`, `clearVerifiedEvents` |
| `src/utils/__tests__/adminApi.test.js` | All 14 functions in `adminApi.js` |

**No frontend coverage gate** — too few tests currently to set a meaningful threshold without noise.

---

## Out of Scope

- Frontend component tests (Vue Test Utils) — deferred; backend logic is the priority
- Service integration tests (hitting H2 DB) — Mockito unit tests are sufficient for the service layer
- Load / performance testing
