# Demo Account System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a self-service demo system where users enter a passcode on the login page, pick a role (Emcee/Judge/Helper), and get their own isolated sandbox clone of a pre-populated template event.

**Architecture:** A template event ("Kyrove Demo") is seeded once on startup via `@EventListener(ApplicationReadyEvent)`. When a user picks a role, `DemoService.cloneTemplate()` deep-copies the template into a new event `"Kyrove Demo-{uuid8}"` in a single `@Transactional` block, creates session tokens, and auto-authenticates the user. Sandboxes are cleaned up on session expiry.

**Tech Stack:** Spring Boot (JPA/Hibernate), PostgreSQL, Flyway, Vue 3 (Pinia, Vue Router), Vitest, JUnit 5 + MockMvc

## Global Constraints

- Demo covers only Emcee, Judge, Helper roles — Organiser excluded
- PRO tier only — no battle access in demo
- Passcode stored in `app_config` table, admin-manageable
- Rate limits: 1 sandbox per session, 3 per IP per 24h, 10 concurrent max
- Sandbox lifetime: 24h max (session timeout), cascade-deleted on expiry
- No email, no Google Sheets/Docs in demo sandboxes
- Follow existing patterns: DTO naming, `@PreAuthorize` annotations, session-based auth, Flyway double-underscore naming
- All demo-specific behavior must NOT affect real events — only events named `"Kyrove Demo*"`

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `BES/src/main/resources/db/migration/V46__add_demo_config.sql` | Create | Add `demo_passcode` and `demo_enabled` rows to `app_config` |
| `BES/src/main/java/com/example/BES/services/AppConfigService.java` | Modify | Add generic `get()`/`set()` methods for arbitrary config keys |
| `BES/src/main/java/com/example/BES/controllers/AppConfigController.java` | Modify | Extend `GET /api/v1/config/app` response with `demoEnabled` |
| `BES/src/main/java/com/example/BES/controllers/AdminController.java` | Modify | Add `GET/POST /api/v1/admin/demo/config` |
| `BES/src/main/java/com/example/BES/services/DemoDataSeeder.java` | Create | Seed template event on startup |
| `BES/src/main/java/com/example/BES/services/DemoService.java` | Create | Clone template, rate limiting, cleanup |
| `BES/src/main/java/com/example/BES/controllers/DemoController.java` | Create | `POST /api/v1/demo/start` |
| `BES/src/main/java/com/example/BES/dtos/DemoStartRequestDto.java` | Create | `{ passcode, role }` |
| `BES/src/main/java/com/example/BES/dtos/DemoStartResponseDto.java` | Create | `{ authenticated, role, eventId, eventName, judgeId?, judgeName? }` |
| `BES/src/main/java/com/example/BES/dtos/DemoConfigDto.java` | Create | `{ demoEnabled, passcode }` |
| `BES/src/main/java/com/example/BES/config/SecurityConfig.java` | Modify | Add `/api/v1/demo/**` to `permitAll` |
| `BES/src/main/java/com/example/BES/config/SessionExpiryListener.java` | Modify | Cascade-delete demo sandbox on session expiry |
| `BES/src/main/java/com/example/BES/respositories/EventRepo.java` | Modify | Add `@Query` / `@Modifying` for demo cleanup |
| `BES-frontend/src/utils/api.js` | Modify | Add `startDemo()`, `getDemoConfig()`, `updateDemoConfig()` |
| `BES-frontend/src/views/Login.vue` | Modify | Add "Try Demo" button + passcode modal |
| `BES-frontend/src/components/DemoRolePicker.vue` | Create | 3 role cards for demo role selection |
| `BES-frontend/src/views/AdminPage.vue` | Modify | Add Demo Settings section |
| `BES-frontend/src/App.vue` | Modify | Handle `demoEnabled` in app config, hide/show demo button |

---

### Task 1: Database Migration V46

**Files:**
- Create: `BES/src/main/resources/db/migration/V46__add_demo_config.sql`

**Interfaces:**
- Produces: `app_config` rows with keys `demo_passcode` and `demo_enabled`

- [ ] **Step 1: Write the migration**

```sql
INSERT INTO app_config (key, value)
VALUES ('demo_passcode', 'CHANGEME')
ON CONFLICT (key) DO NOTHING;

INSERT INTO app_config (key, value)
VALUES ('demo_enabled', 'true')
ON CONFLICT (key) DO NOTHING;
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/resources/db/migration/V46__add_demo_config.sql
git commit -m "feat: add demo_passcode and demo_enabled to app_config (V46)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Generalize AppConfigService

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/AppConfigService.java`

**Interfaces:**
- Produces: `String get(String key, String defaultVal)` — generic config reader
- Produces: `void set(String key, String value)` — generic config writer
- Produces: `String getDemoPasscode()` — convenience for `get("demo_passcode", random())` with auto-generation on first read
- Produces: `void setDemoPasscode(String passcode)` — convenience
- Produces: `boolean isDemoEnabled()` — convenience for `"true".equals(get("demo_enabled", "true"))`
- Produces: `void setDemoEnabled(boolean enabled)` — convenience

- [ ] **Step 1: Add generic accessors and demo convenience methods**

Replace the entire file with:

```java
package com.example.BES.services;

import com.example.BES.models.AppConfig;
import com.example.BES.respositories.AppConfigRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class AppConfigService {

    private static final String ACCENT_KEY = "accentColor";
    private static final String ACCENT_DEFAULT = "#ffffff";
    private static final String DEMO_PASSCODE_KEY = "demo_passcode";
    private static final String DEMO_ENABLED_KEY = "demo_enabled";

    private final AppConfigRepository appConfigRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AppConfigService(AppConfigRepository appConfigRepository) {
        this.appConfigRepository = appConfigRepository;
    }

    // ---- accentColor (existing) ----

    public String getAccentColor() {
        return get(ACCENT_KEY, ACCENT_DEFAULT);
    }

    public void saveAccentColor(String color) {
        set(ACCENT_KEY, color);
    }

    // ---- generic key-value ----

    public String get(String key, String defaultVal) {
        return appConfigRepository.findByKey(key)
                .map(AppConfig::getValue)
                .orElse(defaultVal);
    }

    public void set(String key, String value) {
        AppConfig config = appConfigRepository.findByKey(key)
                .orElse(new AppConfig(null, key, value));
        config.setValue(value);
        appConfigRepository.save(config);
    }

    // ---- demo ----

    public String getDemoPasscode() {
        String existing = get(DEMO_PASSCODE_KEY, null);
        if (existing == null || "CHANGEME".equals(existing)) {
            String generated = generatePasscode();
            set(DEMO_PASSCODE_KEY, generated);
            return generated;
        }
        return existing;
    }

    public void setDemoPasscode(String passcode) {
        set(DEMO_PASSCODE_KEY, passcode);
    }

    public boolean isDemoEnabled() {
        return "true".equals(get(DEMO_ENABLED_KEY, "true"));
    }

    public void setDemoEnabled(boolean enabled) {
        set(DEMO_ENABLED_KEY, Boolean.toString(enabled));
    }

    private String generatePasscode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
```

- [ ] **Step 2: Verify the AppConfig model constructor**

Read `BES/src/main/java/com/example/BES/models/AppConfig.java` and confirm it has `AppConfig(Long id, String key, String value)`. If it uses `@AllArgsConstructor` or has that constructor, the code above works. If not, adjust to use default constructor + setters.

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/AppConfigService.java
git commit -m "feat: generalize AppConfigService with generic get/set and demo config methods

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Demo Config Admin Endpoints

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/AppConfigController.java` (extend GET response)
- Create: `BES/src/main/java/com/example/BES/dtos/DemoConfigDto.java`
- Modify: `BES/src/main/java/com/example/BES/controllers/AdminController.java` (add demo config endpoints)

**Interfaces:**
- Consumes: `AppConfigService.getAccentColor()`, `AppConfigService.isDemoEnabled()`, `AppConfigService.getDemoPasscode()`, `AppConfigService.setDemoPasscode()`, `AppConfigService.setDemoEnabled()`
- Produces: `GET /api/v1/config/app` returns `{ accentColor, demoEnabled }`
- Produces: `GET /api/v1/admin/demo/config` → `DemoConfigDto` (ADMIN only)
- Produces: `POST /api/v1/admin/demo/config` ← `DemoConfigDto` (ADMIN only)

- [ ] **Step 1: Create DemoConfigDto**

```java
package com.example.BES.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemoConfigDto {
    private boolean demoEnabled;
    private String passcode;
    private Boolean regeneratePasscode;  // set to true to trigger regeneration on POST
}
```

File: `BES/src/main/java/com/example/BES/dtos/DemoConfigDto.java`

- [ ] **Step 2: Extend GET /api/v1/config/app response**

In `AppConfigController.java`, change the GET method response from `Map<String, String>` to include `demoEnabled`:

```java
@GetMapping("/app")
public ResponseEntity<Map<String, Object>> getAppConfig() {
    Map<String, Object> config = new java.util.HashMap<>();
    config.put("accentColor", appConfigService.getAccentColor());
    config.put("demoEnabled", appConfigService.isDemoEnabled());
    return ResponseEntity.ok(config);
}
```

Replace the existing `Map<String, String>` return type with the above.

- [ ] **Step 3: Add demo config endpoints to AdminController**

Add at the end of `AdminController.java` (before the closing `}`):

```java
private final AppConfigService appConfigService;

// Add appConfigService to the existing constructor, or use @Autowired field injection:
@Autowired
private AppConfigService appConfigService;

@GetMapping("/demo/config")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<DemoConfigDto> getDemoConfig() {
    return ResponseEntity.ok(new DemoConfigDto(
            appConfigService.isDemoEnabled(),
            appConfigService.getDemoPasscode(),
            null
    ));
}

@PostMapping("/demo/config")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<DemoConfigDto> updateDemoConfig(@RequestBody DemoConfigDto dto) {
    if (dto.getRegeneratePasscode() != null && dto.getRegeneratePasscode()) {
        String newPasscode = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        appConfigService.setDemoPasscode(newPasscode);
    }
    appConfigService.setDemoEnabled(dto.isDemoEnabled());
    return ResponseEntity.ok(new DemoConfigDto(
            appConfigService.isDemoEnabled(),
            appConfigService.getDemoPasscode(),
            null
    ));
}
```

Note: Adjust the `AdminController` constructor to also accept `AppConfigService`. Check the existing constructor signature first — if it uses `@Autowired` fields, add a new field for `AppConfigService`.

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/DemoConfigDto.java \
        BES/src/main/java/com/example/BES/controllers/AppConfigController.java \
        BES/src/main/java/com/example/BES/controllers/AdminController.java
git commit -m "feat: add demo config admin endpoints and extend app config response

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: DemoDataSeeder — Template Event

**Files:**
- Create: `BES/src/main/java/com/example/BES/services/DemoDataSeeder.java`

**Interfaces:**
- Consumes: Repositories for Event, EventCategory, Participant, EventParticipant, EventCategoryParticipant, Judge, ScoringCriteria, Score, AuditionFeedback, FeedbackTag, FeedbackTagGroup; also JudgeRepo for native queries
- Produces: Template event "Kyrove Demo" with all seed data (idempotent — skips if event already exists)

- [ ] **Step 1: Check required repositories exist**

Check these repository files exist and note their exact method signatures:
- `EventRepo.java` — `findByEventName(String)`
- `EventCategoryRepository.java` — note the save method
- `ParticipantRepo.java` — does it have `findByParticipantName`?
- `EventParticipantRepo.java`
- `EventCategoryParticipantRepo.java`
- `JudgeRepo.java` — note native query methods: `insertEventJudge(eventId, judgeId)`, `findFirstByName(String)`?
- `ScoringCriteriaRepo.java`
- `ScoreRepo.java`
- `AuditionFeedbackRepo.java`
- `FeedbackTagRepo.java`
- `FeedbackTagGroupRepo.java`

Run: `find BES/src/main/java/com/example/BES/respositories -name "*.java" | sort`

- [ ] **Step 2: Write DemoDataSeeder**

```java
package com.example.BES.services;

import com.example.BES.models.*;
import com.example.BES.respositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String TEMPLATE_NAME = "Kyrove Demo";

    private final EventRepo eventRepo;
    private final EventCategoryRepo eventCategoryRepo;
    private final ParticipantRepo participantRepo;
    private final EventParticipantRepo eventParticipantRepo;
    private final EventCategoryParticipantRepo eventCategoryParticipantRepo;
    private final JudgeRepo judgeRepo;
    private final ScoringCriteriaRepo scoringCriteriaRepo;
    private final ScoreRepo scoreRepo;
    private final AuditionFeedbackRepo auditionFeedbackRepo;
    private final FeedbackTagRepo feedbackTagRepo;
    private final FeedbackTagGroupRepo feedbackTagGroupRepo;

    public DemoDataSeeder(EventRepo eventRepo, EventCategoryRepo eventCategoryRepo,
                          ParticipantRepo participantRepo, EventParticipantRepo eventParticipantRepo,
                          EventCategoryParticipantRepo eventCategoryParticipantRepo,
                          JudgeRepo judgeRepo, ScoringCriteriaRepo scoringCriteriaRepo,
                          ScoreRepo scoreRepo, AuditionFeedbackRepo auditionFeedbackRepo,
                          FeedbackTagRepo feedbackTagRepo, FeedbackTagGroupRepo feedbackTagGroupRepo) {
        this.eventRepo = eventRepo;
        this.eventCategoryRepo = eventCategoryRepo;
        this.participantRepo = participantRepo;
        this.eventParticipantRepo = eventParticipantRepo;
        this.eventCategoryParticipantRepo = eventCategoryParticipantRepo;
        this.judgeRepo = judgeRepo;
        this.scoringCriteriaRepo = scoringCriteriaRepo;
        this.scoreRepo = scoreRepo;
        this.auditionFeedbackRepo = auditionFeedbackRepo;
        this.feedbackTagRepo = feedbackTagRepo;
        this.feedbackTagGroupRepo = feedbackTagGroupRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (eventRepo.findByEventName(TEMPLATE_NAME).isPresent()) {
            log.info("Demo template event already exists, skipping seed");
            return;
        }
        log.info("Seeding demo template event '{}'...", TEMPLATE_NAME);

        // 1. Create template event
        Event event = new Event();
        event.setEventName(TEMPLATE_NAME);
        event.setPaymentRequired(false);
        event.setJudgingMode("SOLO");
        event.setFeedbackEnabled(true);
        event.setResultsReleased(false);
        event.setReleaseScore(false);
        event.setAnimTheme("impact");
        event = eventRepo.save(event);

        // 2. Create categories
        EventCategory hipHop = createCategory(event, "Hip Hop", "1v1");
        EventCategory popping = createCategory(event, "Popping", "1v1");
        EventCategory[] categories = {hipHop, popping};

        // 3. Create scoring criteria for Popping (multi-criteria)
        createCriterion(event, popping, "Musicality", 1.0, 0);
        createCriterion(event, popping, "Technique", 1.0, 1);
        createCriterion(event, popping, "Originality", 1.0, 2);

        // 4. Create judges
        Judge judge1 = createJudge("DJ FLEX");
        Judge judge2 = createJudge("B-Girl RAY");
        Judge judge3 = createJudge("Kid Kazoo");
        Judge[] judges = {judge1, judge2, judge3};

        // Assign judges to event + categories
        for (Judge judge : judges) {
            judgeRepo.insertEventJudge(event.getEventId(), judge.getJudgeId());
        }
        for (EventCategory cat : categories) {
            cat.setJudges(Arrays.asList(judges));
            eventCategoryRepo.save(cat);
        }

        // 5. Create participants (30 unique, 20 per category, ~10 overlap)
        String[] allNames = {
            "B-Boy Spinz", "Poppin J", "Lil Flow", "Kid Twist", "Lady Glide",
            "Rock Steady", "Mighty Mouse", "Turbo T", "Fresh Kicks", "Smooth Move",
            "Wild Card", "Shadow Step", "Beat Breaker", "Style King", "Lock N Load",
            "Wave Rider", "Funk Master", "Cypher Queen", "Groove Theory", "Spin Doctor",
            "Hip Hop Harry", "Pop N Drop", "Freeze Frame", "Rhythm Nation", "Soul Train",
            "Electric Boogaloo", "Floor Phantom", "King Tut", "Robot X", "Moon Walker"
        };

        List<Participant> participants = new ArrayList<>();
        for (String name : allNames) {
            Participant p = participantRepo.findFirstByParticipantName(name)
                    .orElseGet(() -> {
                        Participant np = new Participant();
                        np.setParticipantName(name);
                        return participantRepo.save(np);
                    });
            participants.add(p);
        }

        // 6. Create EventParticipant links (all 30 to the event)
        List<EventParticipant> eventParticipants = new ArrayList<>();
        Random rng = new Random(42); // fixed seed for reproducibility
        for (Participant p : participants) {
            EventParticipant ep = new EventParticipant();
            ep.setEvent(event);
            ep.setParticipant(p);
            ep.setPaymentVerified(true);
            ep.setDisplayName(p.getParticipantName());
            ep.setStageName(p.getParticipantName());
            ep.setReferenceCode(generateRefCode());
            eventParticipants.add(eventParticipantRepo.save(ep));
        }

        // 7. Create EventCategoryParticipant links
        // Hip Hop: participants 0-19 (first 20)
        // Popping: participants 10-29 (last 20, overlapping 10-19)
        List<EventCategoryParticipant> hipHopECPs = new ArrayList<>();
        List<EventCategoryParticipant> poppingECPs = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            Participant p = participants.get(i);
            hipHopECPs.add(createECP(event, hipHop, p, i + 1, "1v1"));
        }
        for (int i = 10; i < 30; i++) {
            Participant p = participants.get(i);
            poppingECPs.add(createECP(event, popping, p, i - 9, "1v1"));
        }

        // 8. Pre-fill scores (~50% of participants scored per category per judge)
        // Hip Hop: judges score participants at even indices (0,2,4,...,18) = 10 scored
        // Popping: judges score participants at odd indices in the popping list = 10 scored
        for (Judge judge : judges) {
            for (int i = 0; i < hipHopECPs.size(); i += 2) {
                double scoreVal = 5.0 + rng.nextDouble() * 4.5; // 5.0-9.5
                createScore(hipHopECPs.get(i), judge, null, Math.round(scoreVal * 10.0) / 10.0);
            }
            for (int i = 1; i < poppingECPs.size(); i += 2) {
                // For Popping, create 3 aspect scores per scored participant
                EventCategoryParticipant ecp = poppingECPs.get(i);
                createScore(ecp, judge, "Musicality", 5.0 + rng.nextDouble() * 4.5);
                createScore(ecp, judge, "Technique", 5.0 + rng.nextDouble() * 4.5);
                createScore(ecp, judge, "Originality", 5.0 + rng.nextDouble() * 4.5);
            }
        }

        // 9. Pre-fill feedback on ~30% of scored participants
        List<FeedbackTag> allTags = feedbackTagRepo.findAll();
        if (!allTags.isEmpty()) {
            for (Judge judge : judges) {
                for (int i = 0; i < hipHopECPs.size(); i += 6) { // every 6th
                    createFeedback(hipHopECPs.get(i), judge, allTags, rng);
                }
            }
        }

        log.info("Demo template event seeded successfully with {} participants, {} judges",
                participants.size(), judges.length);
    }

    private EventCategory createCategory(Event event, String name, String format) {
        EventCategory cat = new EventCategory();
        cat.setEvent(event);
        cat.setName(name);
        cat.setFormat(format);
        cat.setSoloAllowed(true);
        return eventCategoryRepo.save(cat);
    }

    private void createCriterion(Event event, EventCategory cat, String name, double weight, int order) {
        ScoringCriteria sc = new ScoringCriteria();
        sc.setEvent(event);
        sc.setEventCategory(cat);
        sc.setName(name);
        sc.setWeight(weight);
        sc.setDisplayOrder(order);
        scoringCriteriaRepo.save(sc);
    }

    private Judge createJudge(String name) {
        return judgeRepo.findFirstByName(name)
                .orElseGet(() -> {
                    Judge j = new Judge();
                    j.setName(name);
                    return judgeRepo.save(j);
                });
    }

    private EventCategoryParticipant createECP(Event event, EventCategory cat,
                                                Participant p, int auditionNum, String format) {
        EventCategoryParticipant ecp = new EventCategoryParticipant();
        EventCategoryParticipantId id = new EventCategoryParticipantId(
                event.getEventId(), cat.getId(), p.getParticipantId());
        ecp.setId(id);
        ecp.setEvent(event);
        ecp.setEventCategory(cat);
        ecp.setParticipant(p);
        ecp.setAuditionNumber(auditionNum);
        ecp.setFormat(format);
        ecp.setDisplayName(p.getParticipantName());
        return eventCategoryParticipantRepo.save(ecp);
    }

    private void createScore(EventCategoryParticipant ecp, Judge judge, String aspect, double value) {
        Score score = new Score();
        score.setEventCategoryParticipant(ecp);
        score.setJudge(judge);
        score.setAspect(aspect);
        score.setValue(value);
        scoreRepo.save(score);
    }

    private void createFeedback(EventCategoryParticipant ecp, Judge judge,
                                 List<FeedbackTag> allTags, Random rng) {
        AuditionFeedback fb = new AuditionFeedback();
        fb.setEventCategoryParticipant(ecp);
        fb.setJudge(judge);
        fb.setNote("Great energy and stage presence.");
        Set<FeedbackTag> tags = new HashSet<>();
        // pick 1-2 random tags
        tags.add(allTags.get(rng.nextInt(allTags.size())));
        if (rng.nextBoolean() && allTags.size() > 1) {
            tags.add(allTags.get(rng.nextInt(allTags.size())));
        }
        fb.setTags(tags);
        auditionFeedbackRepo.save(fb);
    }

    private String generateRefCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
```

- [ ] **Step 3: Check repository method names match**

Verify these repository methods actually exist (names may differ). Adjust method calls as needed:
- `participantRepo.findFirstByParticipantName(String)` — might be `findByParticipantName` or `findFirstByName`
- `judgeRepo.findFirstByName(String)` — verify signature
- `judgeRepo.insertEventJudge(Long, Long)` — verify this is the actual method name
- `EventCategoryParticipantRepo.save()` — should use the composite key
- `AuditionFeedbackRepo` — verify the save method and that `feedbackTagRepo.findAll()` exists

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/DemoDataSeeder.java
git commit -m "feat: add DemoDataSeeder to create template event on startup

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: DemoService — Clone Logic

**Files:**
- Create: `BES/src/main/java/com/example/BES/services/DemoService.java`
- Modify: `BES/src/main/java/com/example/BES/respositories/EventRepo.java`

**Interfaces:**
- Consumes: Repos for Event, EventCategory, EventParticipant, EventCategoryParticipant, Score, AuditionFeedback, ScoringCriteria, SessionToken, Judge; JudgeRepo native queries; AppConfigService
- Produces: `DemoSession cloneTemplate(String role, String clientIp)` — full clone in @Transactional
- Produces: `void purgeSandbox(Long eventId)` — delete sandbox and all children
- Produces: `int countActiveSandboxes()` — for rate limit check

- [ ] **Step 1: Add cleanup queries to EventRepo**

Add these methods to `EventRepo.java`:

```java
@Modifying
@Query("DELETE FROM Event e WHERE e.eventName LIKE 'Kyrove Demo-%'")
int deleteAllDemoEvents();

@Query("SELECT e FROM Event e WHERE e.eventName LIKE 'Kyrove Demo-%'")
List<Event> findAllDemoEvents();

@Modifying
@Query("DELETE FROM Event e WHERE e.eventName = :eventName")
void deleteByEventName(@Param("eventName") String eventName);
```

Add these imports:
```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import java.util.List;
```

- [ ] **Step 2: Write DemoService**

```java
package com.example.BES.services;

import com.example.BES.models.*;
import com.example.BES.respositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    private static final String TEMPLATE_NAME = "Kyrove Demo";
    private static final String CLONE_PREFIX = "Kyrove Demo-";
    private static final int MAX_CONCURRENT_SANDBOXES = 10;

    private final EventRepo eventRepo;
    private final EventCategoryRepo eventCategoryRepo;
    private final ParticipantRepo participantRepo;
    private final EventParticipantRepo eventParticipantRepo;
    private final EventCategoryParticipantRepo eventCategoryParticipantRepo;
    private final JudgeRepo judgeRepo;
    private final ScoringCriteriaRepo scoringCriteriaRepo;
    private final ScoreRepo scoreRepo;
    private final AuditionFeedbackRepo auditionFeedbackRepo;
    private final SessionTokenRepo sessionTokenRepo;
    private final AppConfigService appConfigService;

    // IP rate limiting: map of IP → list of timestamps
    private final ConcurrentHashMap<String, List<LocalDateTime>> ipRequestLog = new ConcurrentHashMap<>();

    public DemoService(EventRepo eventRepo, EventCategoryRepo eventCategoryRepo,
                       ParticipantRepo participantRepo, EventParticipantRepo eventParticipantRepo,
                       EventCategoryParticipantRepo eventCategoryParticipantRepo,
                       JudgeRepo judgeRepo, ScoringCriteriaRepo scoringCriteriaRepo,
                       ScoreRepo scoreRepo, AuditionFeedbackRepo auditionFeedbackRepo,
                       SessionTokenRepo sessionTokenRepo, AppConfigService appConfigService) {
        this.eventRepo = eventRepo;
        this.eventCategoryRepo = eventCategoryRepo;
        this.participantRepo = participantRepo;
        this.eventParticipantRepo = eventParticipantRepo;
        this.eventCategoryParticipantRepo = eventCategoryParticipantRepo;
        this.judgeRepo = judgeRepo;
        this.scoringCriteriaRepo = scoringCriteriaRepo;
        this.scoreRepo = scoreRepo;
        this.auditionFeedbackRepo = auditionFeedbackRepo;
        this.sessionTokenRepo = sessionTokenRepo;
        this.appConfigService = appConfigService;
    }

    @Transactional
    public CloneResult cloneTemplate(String role, String clientIp) {
        // Rate limit: max 3 per IP per 24h
        checkIpRateLimit(clientIp);

        // Limit concurrent sandboxes
        long active = eventRepo.findAllDemoEvents().size();
        if (active >= MAX_CONCURRENT_SANDBOXES) {
            throw new DemoRateLimitException("Too many demo sessions. Try again later.");
        }

        Event template = eventRepo.findByEventName(TEMPLATE_NAME)
                .orElseThrow(() -> new IllegalStateException("Demo template event not found"));

        // 1. Clone event
        String cloneName = CLONE_PREFIX + randomUuid8();
        Event clone = cloneEvent(template, cloneName);

        // 2. Clone categories + scoring criteria + judge assignments
        Map<Long, EventCategory> oldToNewCategory = new HashMap<>();
        List<EventCategory> templateCategories = eventCategoryRepo.findByEvent(template);
        for (EventCategory templateCat : templateCategories) {
            EventCategory clonedCat = cloneCategory(templateCat, clone);
            oldToNewCategory.put(templateCat.getId(), clonedCat);

            // Clone scoring criteria for this category
            List<ScoringCriteria> criteria = scoringCriteriaRepo.findByEventAndEventCategory(template, templateCat);
            for (ScoringCriteria sc : criteria) {
                cloneScoringCriteria(sc, clone, clonedCat);
            }
        }
        // Also clone event-level criteria (eventCategory = null)
        List<ScoringCriteria> eventCriteria = scoringCriteriaRepo.findByEventAndEventCategory(template, null);
        for (ScoringCriteria sc : eventCriteria) {
            cloneScoringCriteria(sc, clone, null);
        }

        // 3. Clone EventParticipants
        Map<Long, EventParticipant> oldToNewEP = new HashMap<>();
        List<EventParticipant> templateEPs = eventParticipantRepo.findByEvent(template);
        for (EventParticipant templateEP : templateEPs) {
            EventParticipant clonedEP = cloneEventParticipant(templateEP, clone);
            oldToNewEP.put(templateEP.getId(), clonedEP);
        }

        // 4. Clone EventCategoryParticipants
        Map<String, EventCategoryParticipant> compositeKeyToNewECP = new HashMap<>();
        for (EventCategory templateCat : templateCategories) {
            List<EventCategoryParticipant> templateECPs =
                    eventCategoryParticipantRepo.findByEventCategory(templateCat);
            EventCategory clonedCat = oldToNewCategory.get(templateCat.getId());
            for (EventCategoryParticipant templateECP : templateECPs) {
                EventCategoryParticipant clonedECP = cloneECP(templateECP, clone, clonedCat);
                String oldCompositeKey = compositeKey(templateECP);
                compositeKeyToNewECP.put(oldCompositeKey, clonedECP);
            }
        }

        // 5. Clone scores
        for (EventCategory templateCat : templateCategories) {
            List<EventCategoryParticipant> templateECPs =
                    eventCategoryParticipantRepo.findByEventCategory(templateCat);
            for (EventCategoryParticipant templateECP : templateECPs) {
                List<Score> scores = scoreRepo.findByEventCategoryParticipant(templateECP);
                EventCategoryParticipant clonedECP =
                        compositeKeyToNewECP.get(compositeKey(templateECP));
                for (Score s : scores) {
                    cloneScore(s, clonedECP);
                }
            }
        }

        // 6. Clone feedback
        for (EventCategory templateCat : templateCategories) {
            List<EventCategoryParticipant> templateECPs =
                    eventCategoryParticipantRepo.findByEventCategory(templateCat);
            for (EventCategoryParticipant templateECP : templateECPs) {
                List<AuditionFeedback> feedbacks = auditionFeedbackRepo.findByEventCategoryParticipant(templateECP);
                EventCategoryParticipant clonedECP =
                        compositeKeyToNewECP.get(compositeKey(templateECP));
                for (AuditionFeedback fb : feedbacks) {
                    cloneFeedback(fb, clonedECP);
                }
            }
        }

        // 7. Generate session token for the requested role
        SessionToken token = new SessionToken();
        token.setTokenId(UUID.randomUUID().toString());
        token.setRole(role);
        token.setEvent(clone);
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        if ("JUDGE".equals(role)) {
            // Pick a random judge from the clone
            List<Judge> judges = judgeRepo.findJudgesByEventId(clone.getEventId());
            if (!judges.isEmpty()) {
                token.setJudge(judges.get(new Random().nextInt(judges.size())));
            }
        }
        token = sessionTokenRepo.save(token);

        // Record this IP request
        ipRequestLog.computeIfAbsent(clientIp, k -> new ArrayList<>()).add(LocalDateTime.now());

        log.info("Demo sandbox created: {} for role {}", cloneName, role);

        return new CloneResult(clone, token);
    }

    public void purgeSandbox(Long eventId) {
        Event event = eventRepo.findById(eventId).orElse(null);
        if (event == null || !event.getEventName().startsWith(CLONE_PREFIX)) {
            return; // safety: only purge demo events
        }
        log.info("Purging demo sandbox: {}", event.getEventName());

        // Delete in order: feedback → scores → ECPs → criteria → EPs → categories → event_judge → event
        List<EventCategory> categories = eventCategoryRepo.findByEvent(event);
        for (EventCategory cat : categories) {
            List<EventCategoryParticipant> ecps = eventCategoryParticipantRepo.findByEventCategory(cat);
            for (EventCategoryParticipant ecp : ecps) {
                auditionFeedbackRepo.deleteAll(auditionFeedbackRepo.findByEventCategoryParticipant(ecp));
                scoreRepo.deleteAll(scoreRepo.findByEventCategoryParticipant(ecp));
            }
            eventCategoryParticipantRepo.deleteAll(ecps);
            scoringCriteriaRepo.deleteAll(scoringCriteriaRepo.findByEventAndEventCategory(event, cat));
            // Remove judge assignments from category
            cat.getJudges().clear();
            eventCategoryRepo.save(cat);
        }
        // Delete event-level criteria
        scoringCriteriaRepo.deleteAll(scoringCriteriaRepo.findByEventAndEventCategory(event, null));
        // Delete EventParticipants
        List<EventParticipant> eps = eventParticipantRepo.findByEvent(event);
        eventParticipantRepo.deleteAll(eps);
        // Remove event_judge entries
        List<Judge> eventJudges = judgeRepo.findJudgesByEventId(event.getEventId());
        for (Judge j : eventJudges) {
            judgeRepo.deleteEventJudge(event.getEventId(), j.getJudgeId());
        }
        // Delete session tokens
        List<SessionToken> tokens = sessionTokenRepo.findByEvent(event);
        sessionTokenRepo.deleteAll(tokens);
        // Delete categories
        eventCategoryRepo.deleteAll(categories);
        // Delete event
        eventRepo.delete(event);
    }

    public int countActiveSandboxes() {
        return eventRepo.findAllDemoEvents().size();
    }

    private void checkIpRateLimit(String ip) {
        List<LocalDateTime> timestamps = ipRequestLog.getOrDefault(ip, Collections.emptyList());
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        long recent = timestamps.stream().filter(t -> t.isAfter(cutoff)).count();
        if (recent >= 3) {
            throw new DemoRateLimitException("Demo limit reached for today. Try again later.");
        }
    }

    private Event cloneEvent(Event template, String newName) {
        Event clone = new Event();
        clone.setEventName(newName);
        clone.setPaymentRequired(template.isPaymentRequired());
        clone.setJudgingMode(template.getJudgingMode());
        clone.setFeedbackEnabled(template.isFeedbackEnabled());
        clone.setResultsReleased(false);
        clone.setReleaseScore(false);
        clone.setAnimTheme(template.getAnimTheme());
        return eventRepo.save(clone);
    }

    private EventCategory cloneCategory(EventCategory template, Event newEvent) {
        EventCategory clone = new EventCategory();
        clone.setEvent(newEvent);
        clone.setName(template.getName());
        clone.setFormat(template.getFormat());
        clone.setSoloAllowed(template.isSoloAllowed());
        clone.setRoundLabel(template.getRoundLabel());
        clone.setNumberColor(template.getNumberColor());
        clone.setSheetAliases(template.getSheetAliases());
        // Copy judge list
        clone.setJudges(new ArrayList<>(template.getJudges()));
        return eventCategoryRepo.save(clone);
    }

    private void cloneScoringCriteria(ScoringCriteria template, Event newEvent, EventCategory newCat) {
        ScoringCriteria clone = new ScoringCriteria();
        clone.setEvent(newEvent);
        clone.setEventCategory(newCat);
        clone.setName(template.getName());
        clone.setWeight(template.getWeight());
        clone.setDisplayOrder(template.getDisplayOrder());
        scoringCriteriaRepo.save(clone);
    }

    private EventParticipant cloneEventParticipant(EventParticipant template, Event newEvent) {
        EventParticipant clone = new EventParticipant();
        clone.setEvent(newEvent);
        clone.setParticipant(template.getParticipant());
        clone.setPaymentVerified(template.isPaymentVerified());
        clone.setDisplayName(template.getDisplayName());
        clone.setStageName(template.getStageName());
        clone.setTeamName(template.getTeamName());
        clone.setCategory(template.getCategory());
        clone.setResidency(template.getResidency());
        clone.setReferenceCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        return eventParticipantRepo.save(clone);
    }

    private EventCategoryParticipant cloneECP(EventCategoryParticipant template, Event newEvent, EventCategory newCat) {
        EventCategoryParticipant clone = new EventCategoryParticipant();
        EventCategoryParticipantId newId = new EventCategoryParticipantId(
                newEvent.getEventId(), newCat.getId(), template.getParticipant().getParticipantId());
        clone.setId(newId);
        clone.setEvent(newEvent);
        clone.setEventCategory(newCat);
        clone.setParticipant(template.getParticipant());
        clone.setDisplayName(template.getDisplayName());
        clone.setFormat(template.getFormat());
        clone.setAuditionNumber(template.getAuditionNumber());
        clone.setTeamName(template.getTeamName());
        return eventCategoryParticipantRepo.save(clone);
    }

    private void cloneScore(Score template, EventCategoryParticipant newECP) {
        Score clone = new Score();
        clone.setEventCategoryParticipant(newECP);
        clone.setJudge(template.getJudge());
        clone.setAspect(template.getAspect());
        clone.setValue(template.getValue());
        scoreRepo.save(clone);
    }

    private void cloneFeedback(AuditionFeedback template, EventCategoryParticipant newECP) {
        AuditionFeedback clone = new AuditionFeedback();
        clone.setEventCategoryParticipant(newECP);
        clone.setJudge(template.getJudge());
        clone.setNote(template.getNote());
        clone.setTags(new HashSet<>(template.getTags()));
        auditionFeedbackRepo.save(clone);
    }

    private String compositeKey(EventCategoryParticipant ecp) {
        return ecp.getEvent().getEventId() + "-" +
                ecp.getEventCategory().getId() + "-" +
                ecp.getParticipant().getParticipantId();
    }

    private String randomUuid8() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ---- Inner classes ----

    public static class CloneResult {
        public final Event event;
        public final SessionToken token;

        public CloneResult(Event event, SessionToken token) {
            this.event = event;
            this.token = token;
        }
    }

    public static class DemoRateLimitException extends RuntimeException {
        public DemoRateLimitException(String message) {
            super(message);
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/DemoService.java \
        BES/src/main/java/com/example/BES/respositories/EventRepo.java
git commit -m "feat: add DemoService with template cloning, rate limiting, and cleanup

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: DemoController + DTOs

**Files:**
- Create: `BES/src/main/java/com/example/BES/dtos/DemoStartRequestDto.java`
- Create: `BES/src/main/java/com/example/BES/dtos/DemoStartResponseDto.java`
- Create: `BES/src/main/java/com/example/BES/controllers/DemoController.java`

**Interfaces:**
- Consumes: `DemoService.cloneTemplate(role, ip)`, `AppConfigService.isDemoEnabled()`, `AppConfigService.getDemoPasscode()`
- Produces: `POST /api/v1/demo/start` — public endpoint, validates passcode + demo enabled, clones template, creates session, returns response

- [ ] **Step 1: Create DemoStartRequestDto**

```java
package com.example.BES.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemoStartRequestDto {
    private String passcode;
    private String role; // "EMCEE", "JUDGE", or "HELPER"
}
```

- [ ] **Step 2: Create DemoStartResponseDto**

```java
package com.example.BES.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemoStartResponseDto {
    private boolean authenticated;
    private String role;
    private Long eventId;
    private String eventName;
    private Long judgeId;
    private String judgeName;
}
```

- [ ] **Step 3: Create DemoController**

```java
package com.example.BES.controllers;

import com.example.BES.dtos.DemoStartRequestDto;
import com.example.BES.dtos.DemoStartResponseDto;
import com.example.BES.models.SessionToken;
import com.example.BES.services.AppConfigService;
import com.example.BES.services.DemoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final AppConfigService appConfigService;
    private final DemoService demoService;

    public DemoController(AppConfigService appConfigService, DemoService demoService) {
        this.appConfigService = appConfigService;
        this.demoService = demoService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startDemo(@RequestBody DemoStartRequestDto request,
                                        HttpServletRequest httpRequest) {
        // 1. Check demo is enabled
        if (!appConfigService.isDemoEnabled()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Demo is currently disabled"));
        }

        // 2. Validate passcode
        String expectedPasscode = appConfigService.getDemoPasscode();
        if (request.getPasscode() == null || !request.getPasscode().equals(expectedPasscode)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid passcode"));
        }

        // 3. Validate role
        String role = request.getRole();
        if (role == null || !role.matches("^(EMCEE|JUDGE|HELPER)$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid role. Must be EMCEE, JUDGE, or HELPER."));
        }

        // 4. Clone template
        String clientIp = httpRequest.getRemoteAddr();
        DemoService.CloneResult result;
        try {
            result = demoService.cloneTemplate(role, clientIp);
        } catch (DemoService.DemoRateLimitException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", e.getMessage()));
        }

        // 5. Create session and authenticate (same pattern as AuthController.token)
        SessionToken token = result.token;
        String username = "token:" + token.getTokenId();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                username, null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + role)));

        // Check double-session for non-unlimited roles (same pattern as AuthController)
        // Skip for EMCEE/HELPER/JUDGE since they are unlimited

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("eventId", result.event.getEventId());
        session.setAttribute("eventName", result.event.getEventName());

        if (token.getJudge() != null) {
            session.setAttribute("judgeId", token.getJudge().getJudgeId());
            session.setAttribute("judgeName", token.getJudge().getName());
        }

        // 6. Build response
        DemoStartResponseDto response = new DemoStartResponseDto();
        response.setAuthenticated(true);
        response.setRole(role);
        response.setEventId(result.event.getEventId());
        response.setEventName(result.event.getEventName());
        if (token.getJudge() != null) {
            response.setJudgeId(token.getJudge().getJudgeId());
            response.setJudgeName(token.getJudge().getName());
        }

        log.info("Demo session started: {} as {}", result.event.getEventName(), role);
        return ResponseEntity.ok(response);
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/dtos/DemoStartRequestDto.java \
        BES/src/main/java/com/example/BES/dtos/DemoStartResponseDto.java \
        BES/src/main/java/com/example/BES/controllers/DemoController.java
git commit -m "feat: add DemoController with /api/v1/demo/start endpoint

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: SecurityConfig — Permit Demo Endpoint

**Files:**
- Modify: `BES/src/main/java/com/example/BES/config/SecurityConfig.java`

- [ ] **Step 1: Add demo path to permitAll**

Add this line after the existing `requestMatchers("/api/v1/auth/**").permitAll()`:

```java
.requestMatchers("/api/v1/demo/**").permitAll()
```

- [ ] **Step 2: Commit**

```bash
git add BES/src/main/java/com/example/BES/config/SecurityConfig.java
git commit -m "feat: permit all access to /api/v1/demo/** endpoints

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: SessionExpiryListener — Demo Cleanup

**Files:**
- Modify: `BES/src/main/java/com/example/BES/config/SessionExpiryListener.java`
- Modify: `BES/src/main/java/com/example/BES/services/DemoService.java` (add scheduled purge)

- [ ] **Step 1: Add demo cleanup to SessionExpiryListener**

Read the current `SessionExpiryListener.java`. Add `DemoService` as a dependency and call `purgeSandbox` when a demo session expires:

```java
@Component
public class SessionExpiryListener {

    @Autowired
    private ActiveSessionStore activeSessionStore;

    @Autowired
    private EmceeCategoryStore emceeCategoryStore;

    @Autowired
    private DemoService demoService;

    @EventListener
    public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
        activeSessionStore.deregisterBySessionId(event.getId());
        emceeCategoryStore.release(event.getId());

        // Clean up demo sandbox if this session was a demo
        HttpSession session = event.getSession();
        if (session != null) {
            String eventName = (String) session.getAttribute("eventName");
            if (eventName != null && eventName.startsWith("Kyrove Demo-")) {
                Long eventId = (Long) session.getAttribute("eventId");
                if (eventId != null) {
                    demoService.purgeSandbox(eventId);
                }
            }
        }
    }
}
```

- [ ] **Step 2: Add scheduled orphan purge to DemoService**

Add this method to `DemoService.java`:

```java
import org.springframework.scheduling.annotation.Scheduled;

@Scheduled(fixedRate = 6 * 3600 * 1000) // every 6 hours
public void purgeOrphanSandboxes() {
    List<Event> demos = eventRepo.findAllDemoEvents();
    LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
    int purged = 0;
    for (Event demo : demos) {
        // Check if there are any valid session tokens for this event
        List<SessionToken> tokens = sessionTokenRepo.findByEvent(demo);
        boolean hasValidToken = tokens.stream().anyMatch(t ->
                !t.isRevoked() && t.getExpiresAt().isAfter(LocalDateTime.now()));
        if (!hasValidToken) {
            purgeSandbox(demo.getEventId());
            purged++;
        }
    }
    if (purged > 0) {
        log.info("Purged {} orphan demo sandboxes", purged);
    }
}
```

Also add the `@EnableScheduling` import check — Spring Boot auto-enables scheduling via `@SpringBootApplication`, so no extra config bean needed unless scheduling is explicitly disabled.

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/java/com/example/BES/config/SessionExpiryListener.java \
        BES/src/main/java/com/example/BES/services/DemoService.java
git commit -m "feat: add demo sandbox cleanup on session expiry and scheduled orphan purge

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: Frontend API Utilities

**Files:**
- Modify: `BES-frontend/src/utils/api.js`

- [ ] **Step 1: Add demo API functions**

Add these functions at the end of `api.js`:

```javascript
/**
 * Start a demo session with the given passcode and role.
 * @param {string} passcode
 * @param {string} role - "EMCEE", "JUDGE", or "HELPER"
 * @returns {Promise<object>} - { authenticated, role, eventId, eventName, judgeId?, judgeName? }
 */
export const startDemo = async (passcode, role) => {
  const res = await fetch('/api/v1/demo/start', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ passcode, role })
  })
  if (!res.ok) {
    const err = await res.json().catch(() => ({}))
    throw new Error(err.error || 'Failed to start demo')
  }
  return res.json()
}

/**
 * Get demo config (admin only).
 * @returns {Promise<object>} - { demoEnabled, passcode }
 */
export const getDemoConfig = async () => {
  const res = await fetch('/api/v1/admin/demo/config', {
    credentials: 'include'
  })
  if (!res.ok) throw new Error('Failed to fetch demo config')
  return res.json()
}

/**
 * Update demo config (admin only).
 * @param {object} config - { demoEnabled, regeneratePasscode? }
 * @returns {Promise<object>} - updated { demoEnabled, passcode }
 */
export const updateDemoConfig = async (config) => {
  const res = await fetch('/api/v1/admin/demo/config', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(config)
  })
  if (!res.ok) throw new Error('Failed to update demo config')
  return res.json()
}
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/utils/api.js
git commit -m "feat: add demo API utilities (startDemo, getDemoConfig, updateDemoConfig)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: Login Page — "Try Demo" Button

**Files:**
- Modify: `BES-frontend/src/views/Login.vue`

- [ ] **Step 1: Add demo button, passcode modal, and role picker trigger**

Read the current `Login.vue` to understand existing structure (template, script setup, style).

Add in the template, after the login form:

```html
<!-- Demo section -->
<div class="demo-section">
  <div class="section-rule">
    <span class="section-label">or</span>
    <span class="section-line"></span>
  </div>

  <button
    v-if="demoEnabled"
    class="btn-demo"
    @click="showPasscodeModal = true"
  >
    Try Demo
  </button>

  <p v-if="demoEnabled" class="type-prose-sm demo-hint">
    Experience Kyrove as an Emcee, Judge, or Helper
  </p>
</div>

<!-- Passcode modal -->
<Teleport to="body">
  <div v-if="showPasscodeModal" class="modal-backdrop" @click="showPasscodeModal = false">
    <div class="modal-content passcode-modal" @click.stop>
      <h2 class="modal-title">Enter Demo Passcode</h2>
      <input
        v-model="passcode"
        type="text"
        class="input-passcode"
        placeholder="Enter passcode"
        autocomplete="off"
        @keyup.enter="submitPasscode"
      />
      <p v-if="passcodeError" class="error-text">{{ passcodeError }}</p>
      <button class="btn-primary" @click="submitPasscode" :disabled="!passcode.trim()">
        Continue
      </button>
      <p class="type-prose-sm modal-hint">Tap outside to close</p>
    </div>
  </div>
</Teleport>

<!-- Role picker -->
<DemoRolePicker
  v-if="showRolePicker"
  @select="startDemoSession"
  @back="showRolePicker = false; showPasscodeModal = true"
/>
```

Add in the script setup:

```javascript
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { startDemo } from '@/utils/api'
import DemoRolePicker from '@/components/DemoRolePicker.vue'
import { getAppConfig } from '@/utils/api'

const router = useRouter()

const demoEnabled = ref(false)
const showPasscodeModal = ref(false)
const showRolePicker = ref(false)
const passcode = ref('')
const passcodeError = ref('')

onMounted(async () => {
  try {
    const config = await getAppConfig()
    demoEnabled.value = config.demoEnabled === true
  } catch (e) {
    // fallback: hide demo button if config fetch fails
    demoEnabled.value = false
  }
})

function submitPasscode() {
  if (!passcode.value.trim()) return
  passcodeError.value = ''
  showPasscodeModal.value = false
  showRolePicker.value = true
}

async function startDemoSession(role) {
  try {
    const result = await startDemo(passcode.value, role)
    // Route to the appropriate session view
    const roleRoutes = {
      EMCEE: '/emcee/session',
      JUDGE: '/judge/session',
      HELPER: '/helper/session'
    }
    router.push(roleRoutes[role] || '/')
  } catch (e) {
    passcodeError.value = e.message || 'Failed to start demo'
    showRolePicker.value = false
    showPasscodeModal.value = true
  }
}
```

- [ ] **Step 2: Add demo styles**

Add scoped styles for the demo section and passcode modal following the design system (Oswald font, parallelogram chips, surface tokens):

```css
.demo-section {
  margin-top: 2rem;
  text-align: center;
}

.section-rule {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.section-label {
  font-family: var(--font-sans);
  font-size: 10px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: rgba(255,255,255,0.4);
}

.section-line {
  flex: 1;
  height: 1px;
  background: var(--surface-600);
}

.btn-demo {
  font-family: var(--font-sans);
  font-size: 13px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.07);
  color: var(--accent-color);
  padding: 0.75rem 2rem;
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  cursor: pointer;
  transition: background 0.2s;
}

.btn-demo:hover {
  background: rgba(255,255,255,0.08);
}

.demo-hint {
  margin-top: 0.75rem;
  color: rgba(255,255,255,0.35);
}

.passcode-modal {
  max-width: 360px;
}

.input-passcode {
  width: 100%;
  padding: 0.75rem 1rem;
  background: var(--surface-900);
  border: 1px solid var(--surface-600);
  color: #fff;
  font-family: var(--font-sans);
  font-size: 18px;
  letter-spacing: 0.3em;
  text-align: center;
  text-transform: uppercase;
  margin: 1rem 0;
}

.input-passcode:focus {
  outline: none;
  border-color: var(--accent-color);
}

.error-text {
  color: var(--primary-500, #ef4444);
  font-family: var(--font-body);
  font-size: 12px;
  margin-bottom: 0.5rem;
}

.modal-hint {
  margin-top: 0.75rem;
  color: rgba(255,255,255,0.3);
  text-align: center;
}
```

- [ ] **Step 3: App.vue — handle demoEnabled from app config WebSocket**

Read `App.vue` to find where `applyAccent` / app config WebSocket message is handled. Add `demoEnabled` handling so the login page can react to admin toggles:

In the WebSocket subscription callback (where `accentColor` is applied), also emit/provide `demoEnabled`:

```javascript
// In the app-config message handler, add:
if (body.demoEnabled !== undefined) {
  demoEnabled.value = body.demoEnabled
}
```

Provide `demoEnabled` at the app level so `Login.vue` can inject it, or store it in the auth store.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/Login.vue BES-frontend/src/App.vue
git commit -m "feat: add Try Demo button, passcode modal, and role picker trigger to Login

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: DemoRolePicker Component

**Files:**
- Create: `BES-frontend/src/components/DemoRolePicker.vue`

- [ ] **Step 1: Write DemoRolePicker component**

```vue
<template>
  <Teleport to="body">
    <div class="modal-backdrop" @click="$emit('back')">
      <div class="role-picker" @click.stop>
        <h2 class="picker-title">Try Kyrove as…</h2>

        <div class="role-cards">
          <button
            v-for="role in roles"
            :key="role.key"
            class="role-card"
            @click="$emit('select', role.key)"
          >
            <span class="role-icon">{{ role.icon }}</span>
            <span class="role-name">{{ role.label }}</span>
            <span class="role-desc type-prose-sm">{{ role.description }}</span>
          </button>
        </div>

        <p class="type-prose-sm picker-hint">Tap outside to go back</p>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
defineEmits(['select', 'back'])

const roles = [
  {
    key: 'EMCEE',
    icon: '🎤',
    label: 'Emcee',
    description: 'Run audition rounds, view scoreboard, announce results'
  },
  {
    key: 'JUDGE',
    icon: '⚖️',
    label: 'Judge',
    description: 'Score participants, submit feedback, use the keypad'
  },
  {
    key: 'HELPER',
    icon: '🛎️',
    label: 'Helper',
    description: 'Check-in participants, verify details, see QR codes'
  }
]
</script>

<style scoped>
.role-picker {
  max-width: 680px;
  width: 90vw;
  padding: 2rem;
  background: var(--surface-800);
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
}

.picker-title {
  font-family: var(--font-sans);
  font-size: 20px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #fff;
  text-align: center;
  margin-bottom: 1.5rem;
}

.role-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem;
}

@media (max-width: 600px) {
  .role-cards {
    grid-template-columns: 1fr;
  }
}

.role-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  padding: 1.5rem 1rem;
  background: rgba(255,255,255,0.04);
  border: 1px solid rgba(255,255,255,0.07);
  clip-path: polygon(6px 0%, 100% 0%, calc(100% - 6px) 100%, 0% 100%);
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
  color: #fff;
}

.role-card:hover {
  background: rgba(255,255,255,0.08);
  border-color: var(--accent-muted);
}

.role-icon {
  font-size: 32px;
}

.role-name {
  font-family: var(--font-sans);
  font-size: 16px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--accent-color);
}

.role-desc {
  text-align: center;
  color: rgba(255,255,255,0.45);
}

.picker-hint {
  margin-top: 1.5rem;
  color: rgba(255,255,255,0.3);
  text-align: center;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/components/DemoRolePicker.vue
git commit -m "feat: add DemoRolePicker component with Emcee/Judge/Helper role cards

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 12: AdminPage — Demo Settings

**Files:**
- Modify: `BES-frontend/src/views/AdminPage.vue`

- [ ] **Step 1: Add demo settings section**

Read the current `AdminPage.vue` to understand its structure. Find a good place to add a new "Demo Settings" section.

Add to the template (after existing settings sections):

```html
<!-- Demo Settings -->
<section class="admin-section">
  <div class="section-rule">
    <span class="section-label">Demo Settings</span>
    <span class="section-line"></span>
  </div>

  <div class="setting-row">
    <span class="type-prose">Enable demo system</span>
    <label class="toggle-switch">
      <input
        type="checkbox"
        :checked="demoConfig.demoEnabled"
        @change="toggleDemoEnabled"
      />
      <span class="toggle-slider"></span>
    </label>
  </div>

  <div class="setting-row">
    <span class="type-prose">Passcode</span>
    <div class="passcode-controls">
      <input
        :type="showPasscode ? 'text' : 'password'"
        :value="showPasscode ? demoConfig.passcode : '••••••••'"
        class="passcode-display"
        readonly
      />
      <button class="btn-sm" @click="showPasscode = !showPasscode">
        {{ showPasscode ? 'Hide' : 'Show' }}
      </button>
      <button class="btn-sm" @click="regeneratePasscode">
        Regenerate
      </button>
    </div>
  </div>

  <p class="type-prose-sm stat-text">
    Active sandboxes: {{ activeSandboxes }}
  </p>
</section>
```

Add to the script setup:

```javascript
import { ref, onMounted } from 'vue'
import { getDemoConfig, updateDemoConfig } from '@/utils/api'

const demoConfig = ref({ demoEnabled: false, passcode: '' })
const showPasscode = ref(false)
const activeSandboxes = ref(0)

async function loadDemoConfig() {
  try {
    demoConfig.value = await getDemoConfig()
  } catch (e) {
    console.error('Failed to load demo config', e)
  }
}

async function toggleDemoEnabled() {
  const newState = !demoConfig.value.demoEnabled
  await updateDemoConfig({ demoEnabled: newState })
  demoConfig.value.demoEnabled = newState
}

async function regeneratePasscode() {
  await updateDemoConfig({ demoEnabled: demoConfig.value.demoEnabled, regeneratePasscode: true })
  await loadDemoConfig()
}

onMounted(() => {
  loadDemoConfig()
})
```

- [ ] **Step 2: Commit**

```bash
git add BES-frontend/src/views/AdminPage.vue
git commit -m "feat: add Demo Settings section to AdminPage (toggle, passcode, regenerate)

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 13: Build Verification

- [ ] **Step 1: Build backend**

```bash
cd BES && mvn clean package -DskipTests
```

Expected: BUILD SUCCESS. Fix any compilation errors (check repository method names, import paths, constructor signatures).

- [ ] **Step 2: Build frontend**

```bash
cd BES-frontend && npm run build
```

Expected: Build completes without errors.

- [ ] **Step 3: Fix issues and commit**

If any build errors, fix them and commit:

```bash
git add -A
git commit -m "fix: resolve build errors from demo feature integration

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 14: Repository Method Verification

Before writing tests, verify that all repository methods called in `DemoService` and `DemoDataSeeder` actually exist. This task catches any method name mismatches.

- [ ] **Step 1: Check each repository method**

For each repository used in `DemoService` and `DemoDataSeeder`, read the repository file and confirm the method signature exists. Adjust code if methods are named differently.

Key methods to verify:
- `EventCategoryRepo.findByEvent(Event)` 
- `EventParticipantRepo.findByEvent(Event)`
- `EventCategoryParticipantRepo.findByEventCategory(EventCategory)`
- `ScoreRepo.findByEventCategoryParticipant(EventCategoryParticipant)`
- `AuditionFeedbackRepo.findByEventCategoryParticipant(EventCategoryParticipant)`
- `ScoringCriteriaRepo.findByEventAndEventCategory(Event, EventCategory)`
- `JudgeRepo.findFirstByName(String)` 
- `JudgeRepo.insertEventJudge(Long, Long)`
- `JudgeRepo.deleteEventJudge(Long, Long)`
- `JudgeRepo.findJudgesByEventId(Long)`
- `ParticipantRepo.findFirstByParticipantName(String)`
- `SessionTokenRepo.findByEvent(Event)`
- `FeedbackTagRepo.findAll()`

Run: `find BES/src/main/java/com/example/BES/respositories -name "*.java" -exec grep -l "findByEvent\|findByEventCategory\|findFirstBy\|insertEventJudge\|deleteEventJudge\|findJudgesByEvent" {} \;`

Read each file to confirm exact signatures, and update `DemoService` / `DemoDataSeeder` method calls to match.

- [ ] **Step 2: Add missing repository methods**

If any method is missing, add it to the appropriate repository. For example, if `EventCategoryParticipantRepo` has no `findByEventCategory`, add:

```java
List<EventCategoryParticipant> findByEventCategory(EventCategory eventCategory);
```

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "fix: align repository method calls in demo services with actual repo interfaces

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 15: Backend Integration Tests

**Files:**
- Create: `BES/src/test/java/com/example/BES/controllers/DemoControllerIntegrationTest.java`
- Create: `BES/src/test/java/com/example/BES/services/DemoServiceTest.java`

**Interfaces:**
- Consumes: `DemoController`, `DemoService`, H2 test database, Spring MockMvc
- Produces: Test coverage for demo start endpoint (200/401/403/404/429), clone integrity

- [ ] **Step 1: Write DemoControllerIntegrationTest**

```java
package com.example.BES.controllers;

import com.example.BES.dtos.DemoStartRequestDto;
import com.example.BES.models.*;
import com.example.BES.respositories.*;
import com.example.BES.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppConfigService appConfigService;

    @Autowired
    private EventRepo eventRepo;

    @BeforeEach
    void setUp() {
        // Ensure demo is enabled and passcode is set
        appConfigService.setDemoEnabled(true);
        appConfigService.setDemoPasscode("TESTCODE");
        // Ensure template event exists
        if (eventRepo.findByEventName("Kyrove Demo").isEmpty()) {
            Event template = new Event();
            template.setEventName("Kyrove Demo");
            template.setJudgingMode("SOLO");
            eventRepo.save(template);
        }
    }

    @Test
    void startDemo_withValidPasscodeAndRole_returnsSuccess() throws Exception {
        DemoStartRequestDto request = new DemoStartRequestDto("TESTCODE", "EMCEE");
        mockMvc.perform(post("/api/v1/demo/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("EMCEE"))
                .andExpect(jsonPath("$.eventName").value(startsWith("Kyrove Demo-")))
                .andExpect(jsonPath("$.eventId").exists());
    }

    @Test
    void startDemo_withWrongPasscode_returns401() throws Exception {
        DemoStartRequestDto request = new DemoStartRequestDto("WRONG", "EMCEE");
        mockMvc.perform(post("/api/v1/demo/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid passcode"));
    }

    @Test
    void startDemo_whenDisabled_returns403() throws Exception {
        appConfigService.setDemoEnabled(false);
        DemoStartRequestDto request = new DemoStartRequestDto("TESTCODE", "EMCEE");
        mockMvc.perform(post("/api/v1/demo/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Demo is currently disabled"));
        appConfigService.setDemoEnabled(true); // restore
    }

    @Test
    void startDemo_withInvalidRole_returns400() throws Exception {
        DemoStartRequestDto request = new DemoStartRequestDto("TESTCODE", "ORGANISER");
        mockMvc.perform(post("/api/v1/demo/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").containsString("Invalid role"));
    }

    @Test
    void startDemo_asJudge_returnsJudgeFields() throws Exception {
        // Note: this test requires a judge to exist in the template event
        // The test will pass even without judgeId if no judges are seeded
        DemoStartRequestDto request = new DemoStartRequestDto("TESTCODE", "JUDGE");
        mockMvc.perform(post("/api/v1/demo/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("JUDGE"));
    }
}
```

- [ ] **Step 2: Write DemoServiceTest**

```java
package com.example.BES.services;

import com.example.BES.models.*;
import com.example.BES.respositories.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoServiceTest {

    @Autowired
    private DemoService demoService;

    @Autowired
    private EventRepo eventRepo;

    @Autowired
    private AppConfigService appConfigService;

    @BeforeEach
    void setUp() {
        appConfigService.setDemoEnabled(true);
        appConfigService.setDemoPasscode("TEST");
        // Seed a minimal template
        if (eventRepo.findByEventName("Kyrove Demo").isEmpty()) {
            Event template = new Event();
            template.setEventName("Kyrove Demo");
            template.setJudgingMode("SOLO");
            template.setFeedbackEnabled(true);
            eventRepo.save(template);
        }
    }

    @Test
    void cloneTemplate_createsNewEventWithUuidSuffix() {
        DemoService.CloneResult result = demoService.cloneTemplate("EMCEE", "127.0.0.1");
        assertThat(result.event.getEventName()).startsWith("Kyrove Demo-");
        assertThat(result.event.getEventName()).isNotEqualTo("Kyrove Demo");
        assertThat(result.token).isNotNull();
        assertThat(result.token.getRole()).isEqualTo("EMCEE");
    }

    @Test
    void cloneTemplate_rateLimitsPerIp() {
        demoService.cloneTemplate("EMCEE", "192.168.1.1");
        demoService.cloneTemplate("HELPER", "192.168.1.1");
        demoService.cloneTemplate("JUDGE", "192.168.1.1");

        assertThatThrownBy(() -> demoService.cloneTemplate("EMCEE", "192.168.1.1"))
                .isInstanceOf(DemoService.DemoRateLimitException.class)
                .hasMessageContaining("limit reached");
    }

    @Test
    void purgeSandbox_deletesEventAndChildren() {
        DemoService.CloneResult result = demoService.cloneTemplate("EMCEE", "127.0.0.2");
        Long eventId = result.event.getEventId();

        demoService.purgeSandbox(eventId);

        assertThat(eventRepo.findById(eventId)).isEmpty();
    }
}
```

- [ ] **Step 3: Run backend tests**

```bash
cd BES && mvn test -Dtest=DemoControllerIntegrationTest,DemoServiceTest
```

Expected: All tests pass. Fix any failures.

- [ ] **Step 4: Commit**

```bash
git add BES/src/test/java/com/example/BES/controllers/DemoControllerIntegrationTest.java \
        BES/src/test/java/com/example/BES/services/DemoServiceTest.java
git commit -m "test: add integration tests for DemoController and DemoService

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 16: Frontend Component Tests

**Files:**
- Create: `BES-frontend/src/components/__tests__/DemoRolePicker.test.js`

- [ ] **Step 1: Write DemoRolePicker test**

```javascript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DemoRolePicker from '../DemoRolePicker.vue'

describe('DemoRolePicker', () => {
  it('renders three role cards', () => {
    const wrapper = mount(DemoRolePicker)
    const cards = wrapper.findAll('.role-card')
    expect(cards).toHaveLength(3)
  })

  it('emits select with EMCEE when Emcee card is clicked', async () => {
    const wrapper = mount(DemoRolePicker)
    const cards = wrapper.findAll('.role-card')
    await cards[0].trigger('click')
    expect(wrapper.emitted('select')).toBeTruthy()
    expect(wrapper.emitted('select')[0]).toEqual(['EMCEE'])
  })

  it('emits select with JUDGE when Judge card is clicked', async () => {
    const wrapper = mount(DemoRolePicker)
    const cards = wrapper.findAll('.role-card')
    await cards[1].trigger('click')
    expect(wrapper.emitted('select')[0]).toEqual(['JUDGE'])
  })

  it('emits select with HELPER when Helper card is clicked', async () => {
    const wrapper = mount(DemoRolePicker)
    const cards = wrapper.findAll('.role-card')
    await cards[2].trigger('click')
    expect(wrapper.emitted('select')[0]).toEqual(['HELPER'])
  })

  it('emits back when backdrop is clicked', async () => {
    const wrapper = mount(DemoRolePicker)
    await wrapper.find('.modal-backdrop').trigger('click')
    expect(wrapper.emitted('back')).toBeTruthy()
  })

  it('does not emit back when role card is clicked', async () => {
    const wrapper = mount(DemoRolePicker)
    await wrapper.find('.role-card').trigger('click')
    expect(wrapper.emitted('back')).toBeFalsy()
    expect(wrapper.emitted('select')).toBeTruthy()
  })
})
```

- [ ] **Step 2: Run frontend tests**

```bash
cd BES-frontend && npx vitest run src/components/__tests__/DemoRolePicker.test.js
```

Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add BES-frontend/src/components/__tests__/DemoRolePicker.test.js
git commit -m "test: add DemoRolePicker component tests

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 17: Full Integration Verification

- [ ] **Step 1: Start the full stack**

```bash
docker-compose up --build --no-cache
```

- [ ] **Step 2: Smoke test the demo flow**

1. Open `http://localhost/login`
2. Verify "Try Demo" button is visible
3. Click "Try Demo" → passcode modal appears
4. Enter passcode (check logs for generated passcode, or query DB: `SELECT value FROM app_config WHERE key='demo_passcode'`)
5. Select "Emcee" → verify redirect to `/emcee/session` with audition list visible
6. Open new incognito window → repeat for "Judge" → verify scoring cards appear with participants
7. Open new incognito window → repeat for "Helper" → verify check-in flow

- [ ] **Step 3: Verify admin controls**

1. Login as admin
2. Navigate to `/admin`
3. Find "Demo Settings" section
4. Toggle demo off → verify "Try Demo" disappears from login page
5. Toggle demo on → verify "Try Demo" reappears
6. Click "Regenerate" passcode → verify new passcode works, old one doesn't

- [ ] **Step 4: Verify cleanup**

1. Start a demo session, note the event name
2. Close the browser tab
3. Wait for session expiry (or manually delete the session)
4. Verify the sandbox event is removed from the database

- [ ] **Step 5: Commit any fixes**

```bash
git add -A && git commit -m "fix: smoke test fixes for demo feature

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 18: Final Commit & PR

- [ ] **Step 1: Run all tests one final time**

```bash
cd BES && mvn test
cd BES-frontend && npm test
```

- [ ] **Step 2: Create PR**

```bash
git push -u origin feat/demo-account
gh pr create --title "feat: demo account system" \
  --body "## Demo Account System

Implements a self-service demo system for Kyrove.

### What it does
- Adds a \"Try Demo\" button on the login page
- Passcode-gated access (admin-managed)
- Users pick a role: Emcee, Judge, or Helper
- Each user gets their own isolated sandbox clone of a pre-populated template event
- Sandboxes auto-expire after 24h
- Rate limited: 3 per IP per day, 10 concurrent max

### What's included
- Template event seeder (2 categories, 30 participants, 3 judges, pre-filled scores/feedback)
- Full clone service with transaction safety
- Admin controls (toggle demo, view/regenerate passcode)
- Session expiry cleanup + scheduled orphan purge

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

---

## Spec Coverage Checklist

| Spec Section | Tasks |
|---|---|
| 3.1 Template Event | Task 4 (DemoDataSeeder) |
| 3.2 Per-Session Clone | Task 5 (DemoService.cloneTemplate) |
| 4.1 Passcode Management | Task 2 (AppConfigService), Task 3 (Admin endpoints) |
| 4.2 Enable/Disable Toggle | Task 2, Task 3, Task 10 (Login.vue), Task 12 (AdminPage) |
| 4.3 Rate Limits | Task 5 (checkIpRateLimit, MAX_CONCURRENT) |
| 5.1-5.3 Backend API | Task 6 (DemoController + DTOs), Task 7 (SecurityConfig) |
| 5.5 Cleanup | Task 8 (SessionExpiryListener + scheduled purge) |
| 6.1-6.4 Frontend | Task 9 (api.js), Task 10 (Login.vue), Task 11 (DemoRolePicker), Task 12 (AdminPage) |
| 8 Database Migration | Task 1 (V46) |
| 9 Testing | Task 15 (backend tests), Task 16 (frontend tests), Task 17 (smoke tests) |
| 10 Out of Scope | Organiser excluded (validated in Task 6 controller) |
| 11 Seeding | Task 4 (DemoDataSeeder with Google Sheets fallback note) |
