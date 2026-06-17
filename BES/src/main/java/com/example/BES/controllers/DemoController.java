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

        // 4. Check for existing demo session — reuse sandbox if one exists
        HttpSession existingSession = httpRequest.getSession(false);
        String existingEventName = null;
        Long existingEventId = null;
        if (existingSession != null) {
            try {
                existingEventName = (String) existingSession.getAttribute("eventName");
                existingEventId = (Long) existingSession.getAttribute("eventId");
            } catch (IllegalStateException e) {
                // session already invalidated
            }
        }

        SessionToken token;
        Long eventId;
        String eventName;

        if (existingEventName != null && existingEventName.startsWith("Kyrove Demo-") && existingEventId != null) {
            // Reuse existing sandbox — just create a new token for the new role
            log.info("Reusing existing demo sandbox {} for role {}", existingEventName, role);
            token = demoService.createTokenForExistingSandbox(existingEventId, role);
            eventId = existingEventId;
            eventName = existingEventName;
        } else {
            // No existing demo session — clone a fresh sandbox
            String clientIp = httpRequest.getRemoteAddr();
            DemoService.CloneResult result;
            try {
                result = demoService.cloneTemplate(role, clientIp);
            } catch (DemoService.DemoRateLimitException e) {
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                        .body(Map.of("error", e.getMessage()));
            }
            token = result.token;
            eventId = result.event.getEventId();
            eventName = result.event.getEventName();
        }

        // 5. Create session and authenticate (same pattern as AuthController.token)
        String username = "token:" + token.getTokenId();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                username, null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_" + role)));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute("eventId", eventId);
        session.setAttribute("eventName", eventName);

        if (token.getJudge() != null) {
            session.setAttribute("judgeId", token.getJudge().getJudgeId());
            session.setAttribute("judgeName", token.getJudge().getName());
        }

        // 6. Build response
        DemoStartResponseDto response = new DemoStartResponseDto();
        response.setAuthenticated(true);
        response.setRole(role);
        response.setEventId(eventId);
        response.setEventName(eventName);
        if (token.getJudge() != null) {
            response.setJudgeId(token.getJudge().getJudgeId());
            response.setJudgeName(token.getJudge().getName());
        }

        log.info("Demo session started: {} as {}", eventName, role);
        return ResponseEntity.ok(response);
    }
}
