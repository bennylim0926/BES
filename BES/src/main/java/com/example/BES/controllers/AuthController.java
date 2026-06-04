package com.example.BES.controllers;
import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.config.SecurityConfig;
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
        response.put("username", auth.getName());
        response.put("role", auth.getAuthorities());

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

    @Operation(summary = "Generate Token", description = "Generates a session token for a given role, event, and optional judge")
    @PostMapping("/generate-token")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> generateToken(
            @RequestParam String role,
            @RequestParam Long eventId,
            @RequestParam(required = false) Long judgeId,
            @RequestParam(required = false, defaultValue = "7") int expiresInDays) {
        String tokenId = sessionTokenService.generateToken(role, eventId, judgeId, expiresInDays);
        return ResponseEntity.ok(Map.of(
            "tokenId", tokenId,
            "url", "/auth/token?t=" + tokenId));
    }
}
