package com.example.BES.respositories;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.SessionToken;

@Repository
public interface SessionTokenRepository extends JpaRepository<SessionToken, String> {
    List<SessionToken> findByEvent_EventIdAndRoleAndRevokedFalseAndExpiresAtAfter(
        Long eventId, String role, LocalDateTime now);
}
