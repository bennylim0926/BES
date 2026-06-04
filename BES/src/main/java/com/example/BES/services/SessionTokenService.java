package com.example.BES.services;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.models.Event;
import com.example.BES.models.Judge;
import com.example.BES.models.SessionToken;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.respositories.SessionTokenRepository;

@Service
public class SessionTokenService {

    @Autowired
    private SessionTokenRepository sessionTokenRepository;

    @Autowired
    private EventRepo eventRepo;

    @Autowired
    private JudgeRepo judgeRepo;

    public String generateToken(String role, Long eventId, Long judgeId, int expiresInDays) {
        Event event = eventRepo.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        Judge judge = null;
        if (judgeId != null) {
            judge = judgeRepo.findById(judgeId)
                .orElseThrow(() -> new IllegalArgumentException("Judge not found: " + judgeId));
        }

        SessionToken token = new SessionToken();
        token.setTokenId(UUID.randomUUID().toString());
        token.setRole(role);
        token.setEvent(event);
        token.setJudge(judge);
        token.setExpiresAt(LocalDateTime.now().plusDays(expiresInDays));
        token.setRevoked(false);
        token.setCreatedAt(LocalDateTime.now());

        sessionTokenRepository.save(token);
        return token.getTokenId();
    }

    public SessionToken validate(String tokenId) {
        SessionToken token = sessionTokenRepository.findById(tokenId)
            .orElseThrow(() -> new IllegalArgumentException("Token not found: " + tokenId));

        if (token.isRevoked()) {
            throw new IllegalArgumentException("Token has been revoked");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Token has expired");
        }

        return token;
    }

    public void revoke(String tokenId) {
        SessionToken token = sessionTokenRepository.findById(tokenId)
            .orElseThrow(() -> new IllegalArgumentException("Token not found: " + tokenId));
        token.setRevoked(true);
        sessionTokenRepository.save(token);
    }
}
