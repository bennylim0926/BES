package com.example.BES.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.config.SecurityConfig;
import com.example.BES.dtos.LoginDto;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin
public class AuthController {
    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    SecurityConfig config;

    @GetMapping("/debug-session")
    public Map<String, Object> debugSession(HttpSession session) {
        return Map.of(
            "id", session.getId(),
            "auth", SecurityContextHolder.getContext().getAuthentication()
        );
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    boolean isAuthenticated = auth != null 
        && auth.isAuthenticated() 
        && !(auth instanceof AnonymousAuthenticationToken);

    return ResponseEntity.ok(Map.of(
            "authenticated", isAuthenticated,
            "username", auth.getName(),
            "role", auth.getAuthorities()
    ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDto dto, HttpServletRequest request){
        UsernamePasswordAuthenticationToken authToken = 
            new UsernamePasswordAuthenticationToken(dto.getUsername(), dto.getPassword());
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
            "role", authentication.getAuthorities()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request){
        HttpSession session = request.getSession(false);
        if(session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Logged Out "));
    }
}
