package com.example.BES.controllers;
import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.config.SecurityConfig;
import com.example.BES.dtos.GetSessionTokenDto;
import com.example.BES.dtos.LoginDto;
import com.example.BES.dtos.RedeemTokenDto;
import com.example.BES.models.SessionToken;
import com.example.BES.services.SessionTokenService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin
@Tag(name = "Authentication", description = "Endpoints for user login, logout, and session management")
public class AuthController {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    SecurityConfig config;

    @Autowired
    private SessionTokenService sessionTokenService;

    @Operation(summary = "Debug Session", description = "Returns current session ID and authentication context for debugging")
    @GetMapping("/debug-session")
    public Map<String, Object> debugSession(HttpSession session) {
        return Map.of(
                "id", session.getId(),
                "auth", SecurityContextHolder.getContext().getAuthentication());
    }

    @Operation(summary = "Get Current User Info", description = "Returns details of the currently authenticated user")
    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", isAuthenticated);
        if (auth != null) {
            response.put("username", auth.getName());
            response.put("role", auth.getAuthorities());
        } else {
            response.put("username", null);
            response.put("role", java.util.List.of());
        }

        HttpSession session = request.getSession(false);
        if (session != null) {
            Long eventId = (Long) session.getAttribute("eventId");
            Long judgeId = (Long) session.getAttribute("judgeId");
            String judgeName = (String) session.getAttribute("judgeName");
            if (eventId != null) response.put("eventId", eventId);
            if (judgeId != null) response.put("judgeId", judgeId);
            if (judgeName != null) response.put("judgeName", judgeName);
        }

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Login", description = "Authenticates a user and establishes a session")
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginDto dto, HttpServletRequest request) {
        try {
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(dto.getUsername(),
                    dto.getPassword());
            Authentication authentication = authenticationManager.authenticate(authToken);

            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
            return ResponseEntity.ok(Map.of(
                    "message", "Login Successfully",
                    "authenticated", true,
                    "username", authentication.getName(),
                    "role", authentication.getAuthorities()));
        } catch (org.springframework.security.core.AuthenticationException e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).body(Map.of(
                    "message", "Invalid credentials",
                    "authenticated", false));
        }
    }

    @Operation(summary = "Logout", description = "Invalidates the current session and clears the security context")
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null)
            session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged Out "));
    }

    @Operation(summary = "Redeem Token", description = "Redeems a session token for authentication without password")
    @PostMapping("/token")
    public ResponseEntity<?> redeemToken(@Valid @RequestBody RedeemTokenDto dto, HttpServletRequest request) {
        try {
            SessionToken token = sessionTokenService.validate(dto.getTokenId());

            String username = "token:" + dto.getTokenId();
            String role = token.getRole();
            String roleAuthority = "ROLE_" + role;

            UsernamePasswordAuthenticationToken authToken =
                new UsernamePasswordAuthenticationToken(username, null,
                    java.util.List.of(new SimpleGrantedAuthority(roleAuthority)));

            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authToken);
            SecurityContextHolder.setContext(securityContext);

            HttpSession session = request.getSession(true);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

            Long eventId = token.getEvent().getEventId();
            String eventName = token.getEvent().getEventName();
            session.setAttribute("eventId", eventId);

            Map<String, Object> response = new HashMap<>();
            response.put("authenticated", true);
            response.put("role", authToken.getAuthorities());
            response.put("eventId", eventId);
            response.put("eventName", eventName);

            if (token.getJudge() != null) {
                Long judgeId = token.getJudge().getJudgeId();
                String judgeName = token.getJudge().getName();
                session.setAttribute("judgeId", judgeId);
                session.setAttribute("judgeName", judgeName);
                response.put("judgeId", judgeId);
                response.put("judgeName", judgeName);
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static final java.util.Set<String> ALLOWED_SESSION_ROLES =
        java.util.Set.of("JUDGE", "EMCEE", "HELPER");

    @Operation(summary = "Generate Token", description = "Generates a session token for a given role, event, and optional judge")
    @PostMapping("/generate-token")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> generateToken(
            @RequestParam String role,
            @RequestParam Long eventId,
            @RequestParam(required = false) Long judgeId,
            @RequestParam(required = false, defaultValue = "7") int expiresInDays) {
        if (!ALLOWED_SESSION_ROLES.contains(role.toUpperCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role: " + role
                + ". Allowed session roles: JUDGE, EMCEE, HELPER."));
        }
        String tokenId = sessionTokenService.generateToken(role.toUpperCase(), eventId, judgeId, expiresInDays);
        return ResponseEntity.ok(Map.of(
            "tokenId", tokenId,
            "url", "/auth/token?t=" + tokenId));
    }

    @Operation(summary = "List Tokens", description = "Returns all non-revoked, non-expired session tokens for an event")
    @GetMapping("/tokens")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<List<GetSessionTokenDto>> listTokens(@RequestParam Long eventId) {
        List<SessionToken> tokens = sessionTokenService.getActiveTokens(eventId);
        List<GetSessionTokenDto> dtos = tokens.stream().map(t -> {
            GetSessionTokenDto dto = new GetSessionTokenDto();
            dto.tokenId = t.getTokenId();
            dto.role = t.getRole();
            dto.judgeName = t.getJudge() != null ? t.getJudge().getName() : null;
            dto.expiresAt = t.getExpiresAt().toString();
            dto.url = "/auth/token?t=" + t.getTokenId();
            return dto;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @Operation(summary = "Revoke Token", description = "Revokes a session token")
    @DeleteMapping("/tokens/{tokenId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> revokeToken(@PathVariable String tokenId) {
        sessionTokenService.revoke(tokenId);
        return ResponseEntity.ok(Map.of("message", "Token revoked"));
    }
}
