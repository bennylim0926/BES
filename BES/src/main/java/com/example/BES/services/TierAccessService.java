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
 * Admin role is resolved from Spring Security authorities ({@code ROLE_ADMIN})
 * without requiring a database {@code Account} row. Organiser role is also
 * resolved from Spring authorities ({@code ROLE_ORGANISER}) but still consults
 * the Account row for the tier field.
 */
@Service
public class TierAccessService {

    private static final String TIER_MAX = "MAX";

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
        if (auth == null || !auth.isAuthenticated()) return false;

        // Admin path — resolved purely from Spring authorities so it works for
        // mock users (tests) and for any future admin shape that lacks a DB row.
        boolean isAdmin = auth.getAuthorities() != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) return true;

        // Organiser path — needs the Account row for the tier field.
        boolean isOrganiser = auth.getAuthorities() != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ORGANISER".equals(a.getAuthority()));
        if (isOrganiser) {
            Account user = accountService.fromAuth(auth);
            return user != null && TIER_MAX.equals(user.getTier());
        }

        // Emcee / Judge / Helper — resolved from the event's assigned organisers
        // (Max if any assigned organiser is Max).
        if (eventName == null || eventName.isBlank()) return false;
        return eventService.getAssignedOrganisers(eventName).stream()
            .anyMatch(o -> TIER_MAX.equals(o.getTier()));
    }
}
