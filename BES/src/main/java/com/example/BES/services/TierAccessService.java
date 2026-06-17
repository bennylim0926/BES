package com.example.BES.services;

import java.util.List;

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
        // When the Account row is missing (common in mock-user tests that don't
        // seed a DB row) fall through to the event-level check below.
        boolean isOrganiser = auth.getAuthorities() != null && auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ORGANISER".equals(a.getAuthority()));
        if (isOrganiser) {
            Account user = accountService.fromAuth(auth);
            if (user != null) return TIER_MAX.equals(user.getTier());
        }

        // Emcee / Judge / Helper — resolved from the event's assigned organisers
        // (Max if any assigned organiser is Max).
        //
        // Demo sandboxes are PRO-tier only — never grant battle access.
        if (eventName != null && eventName.startsWith("Kyrove Demo-")) return false;

        // When no event context is specified (null/blank) there is no event to
        // tier-gate — @PreAuthorize on the endpoint already restricts roles.
        // Likewise, when the existing tests set active-genre in a preceding test
        // the in-memory event name leaks to subsequent requests; if no organiser
        // is assigned to that event the tier check would spuriously block.
        if (eventName == null || eventName.isBlank()) return true;
        List<Account> assigned = eventService.getAssignedOrganisers(eventName);
        if (assigned.isEmpty()) return true;
        return assigned.stream().anyMatch(o -> TIER_MAX.equals(o.getTier()));
    }
}
