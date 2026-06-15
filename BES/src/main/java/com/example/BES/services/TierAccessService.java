package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.BES.models.Account;

/**
 * Single source of truth for tier-based access to battle features.
 *
 * <p>Rules:
 * <ul>
 *   <li>Admin — always has access (tier ignored).</li>
 *   <li>Organiser — has access iff their own tier is {@code MAX}.</li>
 *   <li>Everyone else (Emcee/Judge/Helper) — has access iff at least one organiser
 *       assigned to the event has {@code MAX} tier.</li>
 * </ul>
 *
 * <p>Null/anonymous authentication and unknown accounts are treated as denied.
 * Role comparisons use the bare role strings stored in {@code account.role}
 * (e.g. {@code "ADMIN"}, {@code "ORGANISER"}) — NOT the Spring-Security-prefixed
 * authority names ({@code ROLE_ADMIN} etc.).
 */
@Service
public class TierAccessService {

    private static final String TIER_MAX = "MAX";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_ORGANISER = "ORGANISER";

    @Autowired
    private AccountService accountService;

    @Autowired
    private EventService eventService;

    /**
     * Throws {@link ResponseStatusException} (403) if the caller lacks battle access.
     */
    public void requireBattleAccess(Authentication auth, String eventName) {
        if (!hasBattleAccess(auth, eventName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Battle features require Max tier");
        }
    }

    /**
     * @return true iff the caller may use battle features for the given event.
     */
    public boolean hasBattleAccess(Authentication auth, String eventName) {
        Account user = accountService.fromAuth(auth);
        if (user == null) {
            return false;
        }

        // Admins always have access; event context is irrelevant.
        if (isAdmin(user)) {
            return true;
        }

        // Organisers gate on their own tier; event context is irrelevant.
        if (isOrganiser(user)) {
            return TIER_MAX.equals(user.getTier());
        }

        // Emcee / Judge / Helper — resolve the event's assigned organisers.
        // Without an event we have no tier signal, so deny by default.
        if (eventName == null || eventName.isBlank()) {
            return false;
        }
        return eventService.getAssignedOrganisers(eventName).stream()
            .anyMatch(o -> TIER_MAX.equals(o.getTier()));
    }

    private boolean isAdmin(Account user) {
        return ROLE_ADMIN.equals(user.getRole());
    }

    private boolean isOrganiser(Account user) {
        return ROLE_ORGANISER.equals(user.getRole());
    }
}
