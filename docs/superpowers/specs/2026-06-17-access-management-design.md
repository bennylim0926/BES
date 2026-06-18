# Access Management — Helper Score Access, Hide AuditionList from Organiser, Admin Delete Event

**Date:** 2026-06-17
**Branch:** (to be created)

## 1. Helper Score Access (Read-only + Tie-breaker + Category Filter)

Helper gains read-only access to the Score page. Score modification stays restricted to AuditionList for all roles — Score.vue is a display/announce surface, not an entry surface.

### Changes

| Layer | File | Change |
|-------|------|--------|
| Router | `BES-frontend/src/router/index.js` | Add `ROLE_HELPER` to Score route `allowedRoles` |
| Backend | `BES/src/main/java/com/example/BES/controllers/EventController.java` | Add `HELPER` to `@PreAuthorize` on `GET /scores/{eventName}` |
| Backend | `EventController.java` | Add `HELPER` to any other Score-dependent read endpoints: `GET /{eventName}/criteria`, `GET /{eventName}/categories`, `GET /{eventName}/results-status`, `GET /{eventName}/feedback-tags` (verify each — some may already be public) |
| EventPanel | `BES-frontend/src/components/EventPanel.vue` | Add `ROLE_HELPER` to the Score tile's `roles` array |
| MainMenu | `BES-frontend/src/views/MainMenu.vue` | Show Score quick-action card for Helper (currently gated by a role check that excludes Helper) |
| Score.vue | `BES-frontend/src/views/Score.vue` | Add `isHelper` computed; Helper defaults to Broadcast mode but can switch to Control mode; tie-breaker panel and category filter accessible; results-release toggle stays Admin/Organiser-only |
| HelperSessionView | `BES-frontend/src/views/HelperSessionView.vue` | Add a Score navigation link |

### Access After Change

| Role | View Scores | Category Filter | Tie-breaker | Release Results | Modify Scores |
|------|:--:|:--:|:--:|:--:|:--:|
| Admin | ✅ | ✅ | ✅ | ✅ | ❌ (AuditionList only) |
| Organiser | ✅ | ✅ | ✅ | ✅ | ❌ (AuditionList only) |
| Emcee | ✅ | ✅ | ✅ | ❌ | ❌ |
| Helper | ✅ | ✅ | ✅ | ❌ | ❌ |
| Judge | ❌ | ❌ | ❌ | ❌ | ❌ |

## 2. Hide AuditionList UI from Organiser

Organiser keeps backend/route access but the Audition List is removed from all navigation surfaces. They can still reach it via direct URL if needed.

### Changes

| Surface | File | Change |
|---------|------|--------|
| EventPanel tile | `BES-frontend/src/components/EventPanel.vue` | Remove `ROLE_ORGANISER` from audition tile `roles` |
| MainMenu card | `BES-frontend/src/views/MainMenu.vue` | Add `role !== 'ROLE_ORGANISER'` to the Audition card guard |
| Router | No change | Organiser keeps route access |
| Backend | No change | Organiser keeps all AuditionList API access |

## 3. Admin Delete Event

Admin can delete an entire event from the Events list page. Two-step confirmation (warning → type event name to confirm). All related data is cleaned up in dependency order within a single transaction.

### Frontend

| File | Change |
|------|--------|
| `BES-frontend/src/utils/api.js` or `adminApi.js` | New `deleteEvent(eventName)` function → `DELETE /api/v1/event/{eventName}` |
| `BES-frontend/src/views/Events.vue` | Delete button on each EventCard; two-step confirmation modal (inline or Teleport) |
| `BES-frontend/src/components/EventCard.vue` | Expose delete trigger (via emit or slot) alongside existing action buttons |

**Confirmation flow:**

1. **Step 1 — Warning modal:** Red-accented, lists event name, warns that ALL data will be permanently deleted (participants, categories, scores, feedback, battle state, session tokens). Two buttons: "Cancel" / "Delete Event".
2. **Step 2 — Type to confirm:** Admin must type the event name exactly to enable the final "Delete" button. This prevents accidental deletion.

### Backend

**New endpoint:** `DELETE /api/v1/event/{eventName}`
- `@PreAuthorize("hasRole('ADMIN')")`
- Returns `200` on success, `404` if event not found

**New service method:** `EventService.deleteEvent(String eventName)`
- `@Transactional` — all-or-nothing rollback
- Deletion order (respecting foreign-key dependencies):

```
1. AuditionFeedback (by EventCategoryParticipant → EventCategory → Event)
2. Scores (by EventCategoryParticipant → EventCategory → Event)
3. EventCategoryParticipantMember (by EventCategoryParticipant)
4. EventCategoryParticipant (by EventCategory)
5. EventParticipantTeamMember (by EventParticipant)
6. EventParticipant (by Event)
7. ScoringCriteria (by Event + EventCategory)
8. FeedbackTag (by eventId)
9. FeedbackTagGroup (by eventId)
10. EventCategoryBattleGuest (by Event)
11. PickupCrewMember (by PickupCrew)
12. PickupCrew (by Event)
13. SessionToken (by eventId)
14. BattleCategoryState (by eventId + categoryId)
15. BattleActiveCategory (by eventName)
16. event_category_judge join table entries
17. event_judge join table entries
18. EventEmailTemplate (by Event)
19. EventCategory (by Event)
20. organiser_event join table entries
21. Event (final delete)
```

**New repository queries needed:**
- `ScoreRepo`: delete by EventCategoryParticipant IDs
- `AuditionFeedbackRepo`: delete by EventCategoryParticipant IDs
- `EventCategoryParticipantRepo`: delete by EventCategory IDs
- `EventParticipantRepo`: delete by Event
- `ScoringCriteriaRepo`: delete by Event + EventCategory
- `FeedbackTagRepo`: delete by eventId
- `FeedbackTagGroupRepo`: delete by eventId
- `EventCategoryBattleGuestRepo`: delete by Event
- `PickupCrewRepo`: delete by eventId
- `SessionTokenRepo`: delete by eventId
- `BattleCategoryStateRepo`: delete by eventId
- `BattleActiveCategoryRepo`: delete by eventName
- `EventEmailTemplateRepo`: delete by eventId
- `EventCategoryRepo`: delete by Event
- `EventRepo`: delete by eventId

## 4. Out of Scope

- Score modification from Score.vue (stays in AuditionList only)
- Organiser backend access changes for AuditionList
- Event deletion from EventDetails page (Events list only)
- Event archival / soft-delete
- Cascade configuration on JPA entities (manual cleanup preferred for explicitness)
