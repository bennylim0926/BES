# Two-Tier Auth Model Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace shared in-memory accounts with DB-backed persistent accounts for Admin/Organiser and signed session link tokens for Judge/Emcee/Helper — eliminating the shared judge picker in BattleJudge and making /battle/judge auth-gated.

**Architecture:** Spring Security's `InMemoryUserDetailsManager` is replaced by a JPA-backed `AccountUserDetailsService` for Admin/Organiser. Judge/Emcee/Helper are not DB users — they authenticate via a one-time session link (`/auth/token?t=<uuid>`). The backend validates the token, writes a Spring Security session, and stores judgeId/eventId as session attributes returned by `/me`. Organiser event access is scoped to an `organiser_event` join table.

**Tech Stack:** Spring Boot 3, Spring Security 6 (session-based), Spring Data JPA, Flyway, H2 (tests), PostgreSQL (prod), Vue 3, Pinia, Vue Router

> **Split note:** This plan is intentionally one document because every task is a prerequisite for the next. DB-backed auth (Tasks 1–7) must be shipped and verified before session links (Tasks 8–14) are attempted. Frontend changes (Tasks 15–18) can only be done after the backend is complete.

---

## File Map

**New backend files:**
- `BES/src/main/java/com/example/BES/models/Account.java`
- `BES/src/main/java/com/example/BES/models/SessionToken.java`
- `BES/src/main/java/com/example/BES/respositories/AccountRepository.java`
- `BES/src/main/java/com/example/BES/respositories/SessionTokenRepository.java`
- `BES/src/main/java/com/example/BES/services/AccountUserDetailsService.java`
- `BES/src/main/java/com/example/BES/services/InitialAccountSeeder.java`
- `BES/src/main/java/com/example/BES/services/SessionTokenService.java`
- `BES/src/main/resources/db/migration/V31__add_account_and_session_token.sql`

**Modified backend files:**
- `BES/src/main/java/com/example/BES/config/SecurityConfig.java` — swap UserDetailsService
- `BES/src/main/java/com/example/BES/controllers/AuthController.java` — /me judgeId, /token, /generate-token endpoints
- `BES/src/main/java/com/example/BES/services/EventService.java` — Organiser event scoping
- `BES/src/main/java/com/example/BES/controllers/EventController.java` — pass role+username to service

**New frontend files:**
- `BES-frontend/src/views/TokenAuth.vue` — session link redemption page

**Modified frontend files:**
- `BES-frontend/src/router/index.js` — HELPER role, /battle/judge auth-gated
- `BES-frontend/src/utils/auth.js` — store judgeId/judgeName/eventId in Pinia
- `BES-frontend/src/utils/api.js` — redeemToken(), generateToken() functions
- `BES-frontend/src/views/BattleJudge.vue` — remove picker, auto-identify from session
- `BES-frontend/src/views/AuditionList.vue` — JUDGE role skips identity picker

---

### Task 1: DB Migration V31

**Files:**
- Create: `BES/src/main/resources/db/migration/V31__add_account_and_session_token.sql`

- [ ] **Step 1: Write the migration**

```sql
-- Persistent accounts for Admin and Organiser
CREATE TABLE account (
    account_id   BIGSERIAL    PRIMARY KEY,
    username     VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role         VARCHAR(20)  NOT NULL,
    event_credits INT          NOT NULL DEFAULT 0,
    referral_code VARCHAR(20)  UNIQUE NOT NULL,
    referred_by  BIGINT       REFERENCES account(account_id),
    created_at   TIMESTAMP    NOT NULL DEFAULT now()
);

-- Organiser ↔ Event assignment (Admin assigns organisers to events)
CREATE TABLE organiser_event (
    account_id BIGINT NOT NULL REFERENCES account(account_id) ON DELETE CASCADE,
    event_id   BIGINT NOT NULL REFERENCES event(event_id)   ON DELETE CASCADE,
    PRIMARY KEY (account_id, event_id)
);

-- Session link tokens for Judge / Emcee / Helper (no password, expires after event)
CREATE TABLE session_token (
    token_id   VARCHAR(64)  PRIMARY KEY,
    role       VARCHAR(20)  NOT NULL,
    event_id   BIGINT       NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    judge_id   BIGINT       REFERENCES judge(judge_id) ON DELETE SET NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);
```

- [ ] **Step 2: Verify migration applies cleanly**

```bash
cd BES && mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5433/bes \
  -Dflyway.user=bes -Dflyway.password=bes
```
Expected: `Successfully applied 1 migration to schema "public"` (V31).

- [ ] **Step 3: Commit**

```bash
git add BES/src/main/resources/db/migration/V31__add_account_and_session_token.sql
git commit -m "feat: V31 migration — account, organiser_event, session_token tables"
```

---

### Task 2: Account JPA Entity + Repository

**Files:**
- Create: `BES/src/main/java/com/example/BES/models/Account.java`
- Create: `BES/src/main/java/com/example/BES/respositories/AccountRepository.java`

- [ ] **Step 1: Write the Account entity**

```java
package com.example.BES.models;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accountId;

    @Column(unique = true, nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 20)
    private String role; // ADMIN or ORGANISER

    @Column(nullable = false)
    private int eventCredits = 0;

    @Column(unique = true, nullable = false, length = 20)
    private String referralCode;

    @ManyToOne
    @JoinColumn(name = "referred_by")
    private Account referredBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @ManyToMany
    @JoinTable(
        name = "organiser_event",
        joinColumns = @JoinColumn(name = "account_id"),
        inverseJoinColumns = @JoinColumn(name = "event_id")
    )
    private List<Event> assignedEvents;
}
```

- [ ] **Step 2: Write the AccountRepository**

```java
package com.example.BES.respositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.example.BES.models.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUsername(String username);
    Optional<Account> findByReferralCode(String referralCode);
}
```

- [ ] **Step 3: Run tests to verify the new entities compile and the H2 schema validates**

```bash
cd BES && mvn test -Dtest=AuthControllerIntegrationTest -q
```
Expected: build compiles, tests pass (no entity mapping errors).

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/Account.java \
        BES/src/main/java/com/example/BES/respositories/AccountRepository.java
git commit -m "feat: Account entity and repository"
```

---

### Task 3: SessionToken JPA Entity + Repository

**Files:**
- Create: `BES/src/main/java/com/example/BES/models/SessionToken.java`
- Create: `BES/src/main/java/com/example/BES/respositories/SessionTokenRepository.java`

- [ ] **Step 1: Write the SessionToken entity**

```java
package com.example.BES.models;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "session_token")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SessionToken {

    @Id
    private String tokenId;

    @Column(nullable = false, length = 20)
    private String role; // EMCEE, JUDGE, or HELPER

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne
    @JoinColumn(name = "judge_id")
    private Judge judge; // null for EMCEE and HELPER

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

- [ ] **Step 2: Write the SessionTokenRepository**

```java
package com.example.BES.respositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.BES.models.SessionToken;

public interface SessionTokenRepository extends JpaRepository<SessionToken, String> {
    List<SessionToken> findByEvent_EventIdAndRoleAndRevokedFalseAndExpiresAtAfter(
        Long eventId, String role, LocalDateTime now);
}
```

- [ ] **Step 3: Compile check**

```bash
cd BES && mvn compile -q
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/models/SessionToken.java \
        BES/src/main/java/com/example/BES/respositories/SessionTokenRepository.java
git commit -m "feat: SessionToken entity and repository"
```

---

### Task 4: AccountUserDetailsService + InitialAccountSeeder

**Files:**
- Create: `BES/src/main/java/com/example/BES/services/AccountUserDetailsService.java`
- Create: `BES/src/main/java/com/example/BES/services/InitialAccountSeeder.java`

- [ ] **Step 1: Write AccountUserDetailsService**

```java
package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.BES.models.Account;
import com.example.BES.respositories.AccountRepository;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    @Autowired
    private AccountRepository accountRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Account not found: " + username));
        return User.builder()
            .username(account.getUsername())
            .password(account.getPasswordHash())
            .roles(account.getRole())
            .build();
    }
}
```

- [ ] **Step 2: Write InitialAccountSeeder**

```java
package com.example.BES.services;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.example.BES.models.Account;
import com.example.BES.respositories.AccountRepository;

@Component
public class InitialAccountSeeder {

    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${BES_ADMIN_PASSWORD}")    private String adminPassword;
    @Value("${BES_ORGANISER_PASSWORD}") private String organiserPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        createIfAbsent("admin",     adminPassword,     "ADMIN");
        createIfAbsent("organiser", organiserPassword, "ORGANISER");
    }

    private void createIfAbsent(String username, String rawPassword, String role) {
        if (accountRepository.findByUsername(username).isPresent()) return;
        Account a = new Account();
        a.setUsername(username);
        a.setPasswordHash(passwordEncoder.encode(rawPassword));
        a.setRole(role);
        a.setReferralCode(shortUuid());
        accountRepository.save(a);
    }

    private String shortUuid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
```

- [ ] **Step 3: Compile check**

```bash
cd BES && mvn compile -q
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 4: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/AccountUserDetailsService.java \
        BES/src/main/java/com/example/BES/services/InitialAccountSeeder.java
git commit -m "feat: DB-backed UserDetailsService and initial account seeder"
```

---

### Task 5: Swap SecurityConfig to DB-backed Auth

**Files:**
- Modify: `BES/src/main/java/com/example/BES/config/SecurityConfig.java`

- [ ] **Step 1: Write the test first**

Create `BES/src/test/java/com/example/BES/auth/DbAuthIntegrationTest.java`:

```java
package com.example.BES.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DbAuthIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void adminLogin_validCredentials_returns200() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"testpass\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.role[0].authority").value("ROLE_ADMIN"));
    }

    @Test
    void adminLogin_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run the test — verify it fails (InMemory still in use)**

```bash
cd BES && mvn test -Dtest=DbAuthIntegrationTest -q
```
Expected: FAIL — `admin` credentials don't match yet because InMemory is still wired.

- [ ] **Step 3: Update `application-test.properties` to set predictable passwords for tests**

File: `BES/src/main/resources/application-test.properties` — add:
```properties
BES_ADMIN_PASSWORD=testpass
BES_ORGANISER_PASSWORD=testpass
BES_EMCEE_PASSWORD=testpass
BES_JUDGE_PASSWORD=testpass
```

- [ ] **Step 4: Rewrite SecurityConfig to use AccountUserDetailsService**

Replace the entire `SecurityConfig.java` with:

```java
package com.example.BES.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import com.example.BES.services.AccountUserDetailsService;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private AccountUserDetailsService accountUserDetailsService;

    @Value("${BES_COOKIE_DOMAIN:localhost}")
    private String cookieDomain;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/api/v1/battle/**").permitAll()
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/results").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/config/app").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .securityContext(Customizer.withDefaults())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authenticationProvider(daoAuthProvider())
            .build();
    }

    @Bean
    public DaoAuthenticationProvider daoAuthProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(accountUserDetailsService);
        provider.setPasswordEncoder(pwdEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder pwdEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setSameSite("None");
        serializer.setUseSecureCookie(true);
        serializer.setDomainName(cookieDomain);
        return serializer;
    }
}
```

- [ ] **Step 5: Run the test — expect PASS**

```bash
cd BES && mvn test -Dtest=DbAuthIntegrationTest -q
```
Expected: both tests PASS.

- [ ] **Step 6: Run the full test suite to check for regressions**

```bash
cd BES && mvn test -q
```
Expected: `BUILD SUCCESS` — all existing tests pass.

- [ ] **Step 7: Commit**

```bash
git add BES/src/main/java/com/example/BES/config/SecurityConfig.java \
        BES/src/main/resources/application-test.properties \
        BES/src/test/java/com/example/BES/auth/DbAuthIntegrationTest.java
git commit -m "feat: replace InMemoryUserDetailsManager with DB-backed auth"
```

---

### Task 6: Organiser Event Scoping

**Files:**
- Modify: `BES/src/main/java/com/example/BES/services/EventService.java`
- Modify: `BES/src/main/java/com/example/BES/controllers/EventController.java`

- [ ] **Step 1: Write the failing test**

Create `BES/src/test/java/com/example/BES/auth/OrganiserScopeIntegrationTest.java`:

```java
package com.example.BES.auth;

import com.example.BES.models.Account;
import com.example.BES.models.Event;
import com.example.BES.respositories.AccountRepository;
import com.example.BES.respositories.EventRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrganiserScopeIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accountRepository;
    @Autowired EventRepo eventRepo;
    @Autowired PasswordEncoder passwordEncoder;

    private String sessionCookie;

    @BeforeEach
    void setup() throws Exception {
        // Create organiser2 with an assigned event
        if (accountRepository.findByUsername("organiser2").isEmpty()) {
            Event event = new Event();
            event.setEventName("Org2Event");
            event = eventRepo.save(event);

            Account org2 = new Account();
            org2.setUsername("organiser2");
            org2.setPasswordHash(passwordEncoder.encode("pass2"));
            org2.setRole("ORGANISER");
            org2.setReferralCode("ORG2CODE");
            org2.setAssignedEvents(List.of(event));
            accountRepository.save(org2);
        }
        // Log in as organiser2
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"organiser2\",\"password\":\"pass2\"}"))
            .andExpect(status().isOk())
            .andReturn();
        sessionCookie = result.getResponse().getHeader("Set-Cookie");
    }

    @Test
    void organiser_seesOnlyAssignedEvents() throws Exception {
        mockMvc.perform(get("/api/v1/event")
                .header("Cookie", sessionCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.eventName == 'Org2Event')]").exists())
            .andExpect(jsonPath("$.length()").value(1));
    }
}
```

- [ ] **Step 2: Run the test — expect FAIL**

```bash
cd BES && mvn test -Dtest=OrganiserScopeIntegrationTest -q
```
Expected: FAIL — `getEvents` returns all events regardless of role.

- [ ] **Step 3: Find the EventService.getEvents method**

```bash
grep -n "getEvents\|getAllEvent\|findAll" \
  BES/src/main/java/com/example/BES/services/EventService.java | head -20
```

- [ ] **Step 4: Add organiser-scoped query to EventService**

In `EventService.java`, find the method that fetches all events (typically calls `eventRepo.findAll()`) and replace it with:

```java
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.example.BES.respositories.AccountRepository;

// inject at field level:
@Autowired private AccountRepository accountRepository;

public List<Event> getEvents() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean isAdmin = auth.getAuthorities().stream()
        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    if (isAdmin) return eventRepo.findAll();

    // ORGANISER: return only assigned events
    return accountRepository.findByUsername(auth.getName())
        .map(Account::getAssignedEvents)
        .orElse(List.of());
}
```

- [ ] **Step 5: Run the test — expect PASS**

```bash
cd BES && mvn test -Dtest=OrganiserScopeIntegrationTest -q
```
Expected: PASS.

- [ ] **Step 6: Run full test suite**

```bash
cd BES && mvn test -q
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/EventService.java \
        BES/src/test/java/com/example/BES/auth/OrganiserScopeIntegrationTest.java
git commit -m "feat: scope event list to organiser's assigned events"
```

---

### Task 7: SessionTokenService

**Files:**
- Create: `BES/src/main/java/com/example/BES/services/SessionTokenService.java`

- [ ] **Step 1: Write the failing test**

Create `BES/src/test/java/com/example/BES/auth/SessionTokenServiceTest.java`:

```java
package com.example.BES.auth;

import com.example.BES.models.Event;
import com.example.BES.models.Judge;
import com.example.BES.models.SessionToken;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.SessionTokenRepository;
import com.example.BES.services.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SessionTokenServiceTest {

    @Autowired SessionTokenService tokenService;
    @Autowired SessionTokenRepository tokenRepo;
    @Autowired EventRepo eventRepo;
    @Autowired JudgeRepo judgeRepo;

    @Test
    void generateJudgeToken_createsValidToken() {
        Event event = eventRepo.save(new Event(null, "TokenTestEvent", false, "0000", "SOLO", false, null, null));
        Judge judge = judgeRepo.save(new Judge(null, "Test Judge", null));

        String tokenId = tokenService.generateToken("JUDGE", event.getEventId(), judge.getJudgeId(), 7);

        SessionToken stored = tokenRepo.findById(tokenId).orElseThrow();
        assertThat(stored.getRole()).isEqualTo("JUDGE");
        assertThat(stored.getEvent().getEventId()).isEqualTo(event.getEventId());
        assertThat(stored.getJudge().getJudgeId()).isEqualTo(judge.getJudgeId());
        assertThat(stored.isRevoked()).isFalse();
        assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void validateToken_expired_throwsException() {
        Event event = eventRepo.save(new Event(null, "ExpiredEvent", false, "0000", "SOLO", false, null, null));

        SessionToken expired = new SessionToken();
        expired.setTokenId(java.util.UUID.randomUUID().toString());
        expired.setRole("EMCEE");
        expired.setEvent(event);
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        expired.setRevoked(false);
        tokenRepo.save(expired);

        assertThatThrownBy(() -> tokenService.validate(expired.getTokenId()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("expired");
    }

    @Test
    void revokeToken_preventsValidation() {
        Event event = eventRepo.save(new Event(null, "RevokeEvent", false, "0000", "SOLO", false, null, null));
        String tokenId = tokenService.generateToken("HELPER", event.getEventId(), null, 7);
        tokenService.revoke(tokenId);

        assertThatThrownBy(() -> tokenService.validate(tokenId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("revoked");
    }
}
```

- [ ] **Step 2: Run the test — expect FAIL (service doesn't exist yet)**

```bash
cd BES && mvn test -Dtest=SessionTokenServiceTest -q
```
Expected: compile error — `SessionTokenService` not found.

- [ ] **Step 3: Implement SessionTokenService**

```java
package com.example.BES.services;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.models.SessionToken;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.SessionTokenRepository;

@Service
public class SessionTokenService {

    @Autowired private SessionTokenRepository tokenRepo;
    @Autowired private EventRepo eventRepo;
    @Autowired private JudgeRepo judgeRepo;

    public String generateToken(String role, Long eventId, Long judgeId, int expiresInDays) {
        SessionToken token = new SessionToken();
        token.setTokenId(UUID.randomUUID().toString());
        token.setRole(role);
        token.setEvent(eventRepo.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId)));
        if (judgeId != null) {
            token.setJudge(judgeRepo.findById(judgeId)
                .orElseThrow(() -> new IllegalArgumentException("Judge not found: " + judgeId)));
        }
        token.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        tokenRepo.save(token);
        return token.getTokenId();
    }

    public SessionToken validate(String tokenId) {
        SessionToken token = tokenRepo.findById(tokenId)
            .orElseThrow(() -> new IllegalArgumentException("Token not found"));
        if (token.isRevoked()) throw new IllegalArgumentException("Token has been revoked");
        if (token.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("Token has expired");
        return token;
    }

    public void revoke(String tokenId) {
        SessionToken token = tokenRepo.findById(tokenId)
            .orElseThrow(() -> new IllegalArgumentException("Token not found: " + tokenId));
        token.setRevoked(true);
        tokenRepo.save(token);
    }
}
```

- [ ] **Step 4: Run the test — expect PASS**

```bash
cd BES && mvn test -Dtest=SessionTokenServiceTest -q
```
Expected: all 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add BES/src/main/java/com/example/BES/services/SessionTokenService.java \
        BES/src/test/java/com/example/BES/auth/SessionTokenServiceTest.java
git commit -m "feat: SessionTokenService — generate, validate, revoke"
```

---

### Task 8: AuthController — /me judgeId, /token, /generate-token Endpoints

**Files:**
- Modify: `BES/src/main/java/com/example/BES/controllers/AuthController.java`

- [ ] **Step 1: Write the failing test**

Add to `BES/src/test/java/com/example/BES/auth/TokenAuthIntegrationTest.java`:

```java
package com.example.BES.auth;

import com.example.BES.models.Event;
import com.example.BES.models.Judge;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.services.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenAuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired SessionTokenService tokenService;
    @Autowired EventRepo eventRepo;
    @Autowired JudgeRepo judgeRepo;

    @Test
    void redeemJudgeToken_authenticatesWithJudgeRole_andReturnsJudgeId() throws Exception {
        Event event = eventRepo.save(new Event(null, "TokenAuthEvent", false, "0000", "SOLO", false, null, null));
        Judge judge = judgeRepo.save(new Judge(null, "Judge One", null));
        String tokenId = tokenService.generateToken("JUDGE", event.getEventId(), judge.getJudgeId(), 7);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenId\":\"" + tokenId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.role[0].authority").value("ROLE_JUDGE"))
            .andExpect(jsonPath("$.judgeId").isNumber())
            .andReturn();

        // /me should also return judgeId via the session cookie
        String cookie = result.getResponse().getHeader("Set-Cookie");
        mockMvc.perform(get("/api/v1/auth/me").header("Cookie", cookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.judgeId").isNumber());
    }

    @Test
    void redeemInvalidToken_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tokenId\":\"not-a-real-token\"}"))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run the test — expect FAIL**

```bash
cd BES && mvn test -Dtest=TokenAuthIntegrationTest -q
```
Expected: FAIL — `/api/v1/auth/token` endpoint doesn't exist.

- [ ] **Step 3: Add a `RedeemTokenDto`**

Create `BES/src/main/java/com/example/BES/dtos/RedeemTokenDto.java`:

```java
package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RedeemTokenDto {
    @NotBlank
    private String tokenId;
}
```

- [ ] **Step 4: Update AuthController with three new additions**

Replace `AuthController.java` with:

```java
package com.example.BES.controllers;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import com.example.BES.config.SecurityConfig;
import com.example.BES.dtos.LoginDto;
import com.example.BES.dtos.RedeemTokenDto;
import com.example.BES.models.SessionToken;
import com.example.BES.services.SessionTokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin
public class AuthController {

    @Autowired private AuthenticationManager authenticationManager;
    @Autowired private SecurityConfig config;
    @Autowired private SessionTokenService sessionTokenService;

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null
            && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken);

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", isAuthenticated);
        response.put("username", auth != null ? auth.getName() : null);
        response.put("role", auth != null ? auth.getAuthorities() : List.of());

        HttpSession session = request.getSession(false);
        if (session != null) {
            if (session.getAttribute("judgeId")   != null) response.put("judgeId",   session.getAttribute("judgeId"));
            if (session.getAttribute("judgeName") != null) response.put("judgeName", session.getAttribute("judgeName"));
            if (session.getAttribute("eventId")   != null) response.put("eventId",   session.getAttribute("eventId"));
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDto dto, HttpServletRequest request) {
        try {
            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword());
            Authentication authentication = authenticationManager.authenticate(authToken);

            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(authentication);
            SecurityContextHolder.setContext(ctx);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);

            return ResponseEntity.ok(Map.of(
                "message", "Login Successfully",
                "authenticated", true,
                "username", authentication.getName(),
                "role", authentication.getAuthorities()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            return ResponseEntity.status(401).body(Map.of(
                "message", "Invalid credentials",
                "authenticated", false));
        }
    }

    @PostMapping("/token")
    public ResponseEntity<?> redeemToken(@Valid @RequestBody RedeemTokenDto dto, HttpServletRequest request) {
        try {
            SessionToken token = sessionTokenService.validate(dto.getTokenId());

            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + token.getRole()));
            UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("token:" + dto.getTokenId(), null, authorities);

            SecurityContext ctx = SecurityContextHolder.createEmptyContext();
            ctx.setAuthentication(auth);
            SecurityContextHolder.setContext(ctx);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
            session.setAttribute("eventId", token.getEvent().getEventId());
            if (token.getJudge() != null) {
                session.setAttribute("judgeId",   token.getJudge().getJudgeId());
                session.setAttribute("judgeName", token.getJudge().getName());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("role", authorities);
            if (token.getJudge() != null) {
                response.put("judgeId",   token.getJudge().getJudgeId());
                response.put("judgeName", token.getJudge().getName());
            }
            response.put("eventId", token.getEvent().getEventId());
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/generate-token")
    @PreAuthorize("hasAnyRole('ADMIN','ORGANISER')")
    public ResponseEntity<?> generateToken(
            @RequestParam String role,
            @RequestParam Long eventId,
            @RequestParam(required = false) Long judgeId,
            @RequestParam(defaultValue = "7") int expiresInDays) {
        String tokenId = sessionTokenService.generateToken(role.toUpperCase(), eventId, judgeId, expiresInDays);
        return ResponseEntity.ok(Map.of(
            "tokenId", tokenId,
            "url", "/auth/token?t=" + tokenId));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged Out"));
    }
}
```

- [ ] **Step 5: Run the token test — expect PASS**

```bash
cd BES && mvn test -Dtest=TokenAuthIntegrationTest -q
```
Expected: both tests PASS.

- [ ] **Step 6: Run full test suite**

```bash
cd BES && mvn test -q
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```bash
git add BES/src/main/java/com/example/BES/controllers/AuthController.java \
        BES/src/main/java/com/example/BES/dtos/RedeemTokenDto.java \
        BES/src/test/java/com/example/BES/auth/TokenAuthIntegrationTest.java
git commit -m "feat: /token and /generate-token endpoints, /me returns judgeId from session"
```

---

### Task 9: Frontend — Token Redemption View

**Files:**
- Create: `BES-frontend/src/views/TokenAuth.vue`
- Modify: `BES-frontend/src/router/index.js`
- Modify: `BES-frontend/src/utils/api.js`
- Modify: `BES-frontend/src/utils/auth.js`

- [ ] **Step 1: Add `redeemToken` to api.js**

Append to `BES-frontend/src/utils/api.js`:

```js
export const redeemToken = async (tokenId) => {
  try {
    const res = await fetch(`${domain}/api/v1/auth/token`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tokenId })
    })
    return res.ok ? await res.json() : null
  } catch (err) {
    console.error(err)
    return null
  }
}

export const generateToken = async (role, eventId, judgeId, expiresInDays = 7) => {
  try {
    const params = new URLSearchParams({ role, eventId, expiresInDays })
    if (judgeId) params.append('judgeId', judgeId)
    const res = await fetch(`${domain}/api/v1/auth/generate-token?${params}`, {
      method: 'POST',
      credentials: 'include'
    })
    return res.ok ? await res.json() : null
  } catch (err) {
    console.error(err)
    return null
  }
}
```

- [ ] **Step 2: Update auth.js Pinia store to hold judgeId/judgeName**

In `BES-frontend/src/utils/auth.js`, update the `useAuthStore` state and `login` action:

```js
export const useAuthStore = defineStore('auth', {
    state: () => ({
        user: null,
        isAuthenticated: false,
        activeEvent: getActiveEvent(),
        judgeId: null,
        judgeName: null,
    }),
    actions: {
        login(userData) {
            this.user = userData
            this.isAuthenticated = userData['authenticated']
            this.judgeId = userData.judgeId ?? null
            this.judgeName = userData.judgeName ?? null
        },
        logout() {
            this.user = null
            this.isAuthenticated = false
            this.activeEvent = null
            this.judgeId = null
            this.judgeName = null
            clearVerifiedEvents()
            localStorage.removeItem('selectedEvent')
            localStorage.removeItem('selectedRole')
            localStorage.removeItem('selectedGenre')
            localStorage.removeItem('currentJudge')
        },
        setActive(id, name, folderID = null) {
            const event = { id: Number(id), name, folderID: folderID ?? null }
            sessionStorage.setItem(ACTIVE_KEY, JSON.stringify(event))
            this.activeEvent = event
        }
    },
    getters: {
        isLoggedIn: (state) => state.isAuthenticated,
        currentUser: (state) => state.user
    }
})
```

- [ ] **Step 3: Create the TokenAuth view**

```vue
<!-- BES-frontend/src/views/TokenAuth.vue -->
<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { redeemToken } from '@/utils/api'
import { useAuthStore } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const status = ref('loading') // 'loading' | 'error'
const errorMsg = ref('')

onMounted(async () => {
  const tokenId = route.query.t
  if (!tokenId) { status.value = 'error'; errorMsg.value = 'No token provided.'; return }

  const data = await redeemToken(tokenId)
  if (!data?.authenticated) {
    status.value = 'error'
    errorMsg.value = 'This link is invalid or has expired.'
    return
  }

  authStore.login(data)

  const role = data.role?.[0]?.authority
  if (role === 'ROLE_JUDGE')  { router.replace('/battle/judge'); return }
  if (role === 'ROLE_EMCEE')  { router.replace('/event/audition-list'); return }
  if (role === 'ROLE_HELPER') { router.replace('/event/update-event-details'); return }
  router.replace('/')
})
</script>

<template>
  <div class="page-container flex items-center justify-center min-h-screen">
    <div class="card p-8 text-center max-w-sm w-full">
      <template v-if="status === 'loading'">
        <i class="pi pi-spin pi-spinner text-accent text-3xl mb-4"></i>
        <p class="type-body text-content-secondary">Signing you in…</p>
      </template>
      <template v-else>
        <i class="pi pi-times-circle text-red-400 text-3xl mb-4"></i>
        <p class="type-body text-content-primary mb-2">Link Invalid</p>
        <p class="type-label text-content-muted">{{ errorMsg }}</p>
      </template>
    </div>
  </div>
</template>
```

- [ ] **Step 4: Register the route and update router guards**

In `BES-frontend/src/router/index.js`, make these four changes:

**a) Import TokenAuth:**
```js
import TokenAuth from '@/views/TokenAuth.vue'
```

**b) Add the token route (public — no allowedRoles):**
```js
{
    path: '/auth/token',
    name: 'TokenAuth',
    component: TokenAuth
},
```

**c) Add `'ROLE_HELPER'` to the UpdateEventDetails allowed roles:**
```js
// Change:
meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_ORGANISER'], requiresEvent: true }
// To (for /event/update-event-details):
meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_HELPER'], requiresEvent: true }
```

**d) Add `'ROLE_HELPER'` to Emcee-accessible Score route, EMCEE to Score:**
```js
// /event/score — add ROLE_EMCEE if not already present (it should be)
// /battle/judge — add auth requirement:
{
    path: '/battle/judge',
    name: "Battle Judge",
    component: BattleJudge,
    meta: { allowedRoles: ['ROLE_JUDGE', 'ROLE_ADMIN'] }
},
```

**e) Add `'TokenAuth'` to PUBLIC_ROUTES:**
```js
const PUBLIC_ROUTES = ['Login', 'Forbidden', 'StreamOverlay', 'Smoke', 'Results', 'ResultsQR', 'BracketVisualization', 'TokenAuth']
```
Note: `'Battle Judge'` is removed from PUBLIC_ROUTES because it now requires auth.

**f) Add `'ROLE_EMCEE'` to BattleControl:**
```js
{
    path: '/battle/control',
    name: "Battle Control",
    component: BattleControl,
    meta: { allowedRoles: ['ROLE_ADMIN', 'ROLE_ORGANISER', 'ROLE_EMCEE'], requiresEvent: true }
},
```

- [ ] **Step 5: Run frontend tests**

```bash
cd BES-frontend && npm test -- --run
```
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add BES-frontend/src/views/TokenAuth.vue \
        BES-frontend/src/router/index.js \
        BES-frontend/src/utils/api.js \
        BES-frontend/src/utils/auth.js
git commit -m "feat: token redemption view, router guards for HELPER/JUDGE roles"
```

---

### Task 10: BattleJudge.vue — Remove Picker, Auto-Identify from Session

**Files:**
- Modify: `BES-frontend/src/views/BattleJudge.vue`

- [ ] **Step 1: Update BattleJudge.vue onMounted and identity logic**

The current `resolveJudgeIdentity()` reads from localStorage and shows the "WHO ARE YOU?" picker. Replace the entire judge identity section (lines 16–62) with a session-based approach:

Remove these refs and functions entirely:
- `showJudgePicker`, `LS_JUDGE_ID`, `LS_JUDGE_NAME`
- `resolveJudgeIdentity()`, `selectJudge()`, `clearJudge()` (keep a simplified clearJudge that clears vote only)

Replace them with:

```js
// ── Judge identity (from session, no picker) ────────────────────────────────
const judgeId   = ref(null)
const judgeName = ref('')
const notAssigned = ref(false)

async function loadJudgeIdentity(judges) {
  const authStore = useAuthStore()
  const sid = authStore.judgeId
  if (!sid) { notAssigned.value = true; return }
  const match = judges.find(j => j.id === Number(sid))
  if (!match) { notAssigned.value = true; return }
  judgeId.value   = match.id
  judgeName.value = match.name
  setupVoteSubscription()
}

function clearJudge() {
  clearVote()
  if (voteClient) {
    deactivateClient(voteClient)
    const idx = wsClients.indexOf(voteClient)
    if (idx !== -1) wsClients.splice(idx, 1)
    voteClient = null
  }
  judgeId.value   = null
  judgeName.value = ''
}
```

Add this import at the top of `<script setup>`:
```js
import { useAuthStore } from '@/utils/auth'
```

In `onMounted`, replace `resolveJudgeIdentity(battleJudges.value?.judges ?? [])` with `loadJudgeIdentity(battleJudges.value?.judges ?? [])`.

- [ ] **Step 2: Update the template to replace the picker with a "not assigned" state**

Remove the entire `<!-- ── Judge picker bottom sheet ───────────────────────────── -->` Transition block (lines 401–421).

Replace it with:

```vue
<!-- ── Not assigned state ─────────────────────────────────── -->
<Transition name="phase-fade">
  <div
    v-if="notAssigned"
    class="panels-blocker"
    aria-live="polite"
  >
    <span class="blocker-icon">🚫</span>
    <span class="blocker-text">NOT ASSIGNED TO THIS BATTLE</span>
  </div>
</Transition>
```

Remove the old judge chip clear button reference to `clearJudge` from the header — the judge chip should just display the name without a clear button (judges shouldn't switch identity mid-session):

In the header, simplify `.judge-chip` to:
```vue
<div v-if="judgeName" class="judge-chip">
  <span class="judge-chip-label">AS</span>
  <span class="judge-chip-name">{{ judgeName }}</span>
</div>
```

- [ ] **Step 3: Run frontend tests**

```bash
cd BES-frontend && npm test -- --run
```
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/BattleJudge.vue
git commit -m "feat: BattleJudge auto-identifies judge from session, removes picker"
```

---

### Task 11: AuditionList.vue — Judge Identity from Session

**Files:**
- Modify: `BES-frontend/src/views/AuditionList.vue`

- [ ] **Step 1: Update dynamicRole() to set judge identity from session for ROLE_JUDGE**

In `AuditionList.vue`, the `dynamicRole()` function (lines 42–59) currently sets `selectedRole` based on role. Update the ROLE_JUDGE branch to also set `currentJudge` from the auth store instead of from localStorage:

```js
const dynamicRole = async () => {
  const res = await whoami()
  const authority = res.role?.[0]?.authority
  if (authority === 'ROLE_EMCEE') {
    roles.value = ['Emcee']
    selectedRole.value = 'Emcee'
  } else if (authority === 'ROLE_JUDGE') {
    roles.value = ['Judge']
    selectedRole.value = 'Judge'
    // Identity comes from session — set currentJudge to the judge's name
    // so the existing score-loading logic works unchanged
    const jName = authStore.judgeName
    if (jName) {
      currentJudge.value = jName
      localStorage.setItem('currentJudge', jName)
    }
  } else if (authority === 'ROLE_ORGANISER') {
    roles.value = ['Emcee']
    selectedRole.value = 'Emcee'
  } else if (authority === 'ROLE_ADMIN') {
    roles.value = ['Emcee', 'Judge']
    selectedRole.value = localStorage.getItem('selectedRole') || ''
    isAdmin.value = true
  }
}
```

Add `authStore` access at the top (it's already imported — just add the const if missing):
```js
const authStore = useAuthStore()
```

- [ ] **Step 2: Hide the "Who are you?" picker for ROLE_JUDGE**

In the template, find the `<!-- Judge: no identity selected -->` block (around line 806). Wrap it so it only shows for Admin (not for session-authenticated judges):

```vue
<!-- Judge: no identity selected — only shown for Admin who can pick any judge -->
<div
  v-else-if="selectedRole === 'Judge' && !currentJudge && isAdmin"
  ...
>
```

This means ROLE_JUDGE with a session identity skips straight to the score cards, and only Admin can switch judge identity.

- [ ] **Step 3: Run frontend tests**

```bash
cd BES-frontend && npm test -- --run
```
Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add BES-frontend/src/views/AuditionList.vue
git commit -m "feat: AuditionList JUDGE role auto-sets identity from session, hides picker"
```

---

### Task 12: Final Verification

- [ ] **Step 1: Run full backend test suite**

```bash
cd BES && mvn test -q
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run full frontend test suite**

```bash
cd BES-frontend && npm test -- --run
```
Expected: all tests pass.

- [ ] **Step 3: Build the frontend**

```bash
cd BES-frontend && npm run build
```
Expected: `dist/` generated with no type errors.

- [ ] **Step 4: Smoke-test the auth flows manually**

Start the backend: `cd BES && mvn spring-boot:run`
Start the frontend: `cd BES-frontend && npm run dev`

Verify:
1. `http://localhost:5173/login` → admin/testpass → lands on `/` with ROLE_ADMIN
2. `http://localhost:5173/login` → organiser/testpass → lands on `/` with ROLE_ORGANISER, sees only assigned events
3. Generate a JUDGE token via `POST /api/v1/auth/generate-token?role=JUDGE&eventId=1&judgeId=1` (use curl or Postman, logged in as admin)
4. Open `http://localhost:5173/auth/token?t=<tokenId>` → redirects to `/battle/judge` with judge auto-identified (no picker shown)
5. Open `http://localhost:5173/battle/judge` without a session → redirected to `/login`

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "feat: complete two-tier auth model — DB accounts, session links, judge auto-id"
```

---

## Deployment Notes

- V31 migration runs automatically on `docker-compose up`. No manual steps needed.
- `BES_ADMIN_PASSWORD` and `BES_ORGANISER_PASSWORD` env vars are still required in `.env` — they seed the initial DB accounts.
- `BES_EMCEE_PASSWORD` and `BES_JUDGE_PASSWORD` env vars are no longer used after this change. They can be removed from `.env` and `docker-compose.yml` after the release is confirmed stable.
- Token links for Judge/Emcee/Helper are generated by Admin/Organiser via `POST /api/v1/auth/generate-token`. No UI for this yet — use the API directly or via Postman until a token management UI is built.

## Out of Scope (follow-up)

- Token management UI in EventDetails (generate + revoke links per judge/emcee/helper) → #63 follow-up
- Account billing fields (eventCredits, referralCode bonus trigger) → separate plan per #40 decision
- BattleControl Emcee live operator mode (phase advance, read-only bracket) → Issue #61
- Admin UI for assigning Organisers to Events → #63 follow-up
