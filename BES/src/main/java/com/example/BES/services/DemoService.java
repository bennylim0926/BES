package com.example.BES.services;

import com.example.BES.models.*;
import com.example.BES.respositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    private static final String TEMPLATE_NAME = "Kyrove Demo";
    private static final String CLONE_PREFIX = "Kyrove Demo-";
    private static final int MAX_CONCURRENT_SANDBOXES = 10;

    private final EventRepo eventRepo;
    private final EventCategoryRepo eventCategoryRepo;
    private final EventParticipantRepo eventParticipantRepo;
    private final EventCategoryParticipantRepo eventCategoryParticipantRepo;
    private final JudgeRepo judgeRepo;
    private final ScoringCriteriaRepo scoringCriteriaRepo;
    private final ScoreRepo scoreRepo;
    private final AuditionFeedbackRepository auditionFeedbackRepo;
    private final SessionTokenRepository sessionTokenRepo;

    // IP rate limiting: map of IP to list of timestamps
    private final ConcurrentHashMap<String, List<LocalDateTime>> ipRequestLog = new ConcurrentHashMap<>();

    public DemoService(EventRepo eventRepo, EventCategoryRepo eventCategoryRepo,
                       EventParticipantRepo eventParticipantRepo,
                       EventCategoryParticipantRepo eventCategoryParticipantRepo,
                       JudgeRepo judgeRepo, ScoringCriteriaRepo scoringCriteriaRepo,
                       ScoreRepo scoreRepo, AuditionFeedbackRepository auditionFeedbackRepo,
                       SessionTokenRepository sessionTokenRepo) {
        this.eventRepo = eventRepo;
        this.eventCategoryRepo = eventCategoryRepo;
        this.eventParticipantRepo = eventParticipantRepo;
        this.eventCategoryParticipantRepo = eventCategoryParticipantRepo;
        this.judgeRepo = judgeRepo;
        this.scoringCriteriaRepo = scoringCriteriaRepo;
        this.scoreRepo = scoreRepo;
        this.auditionFeedbackRepo = auditionFeedbackRepo;
        this.sessionTokenRepo = sessionTokenRepo;
    }

    @Transactional
    public CloneResult cloneTemplate(String role, String clientIp) {
        // Rate limit: max 3 per IP per 24h
        checkIpRateLimit(clientIp);

        // Limit concurrent sandboxes
        long active = eventRepo.findAllDemoEvents().size();
        if (active >= MAX_CONCURRENT_SANDBOXES) {
            throw new DemoRateLimitException("Too many demo sessions. Try again later.");
        }

        Event template = eventRepo.findByEventName(TEMPLATE_NAME)
                .orElseThrow(() -> new IllegalStateException("Demo template event not found"));

        // 1. Clone event
        String cloneName = CLONE_PREFIX + randomUuid8();
        Event clone = cloneEvent(template, cloneName);

        // 2. Clone categories + scoring criteria + judge assignments
        Map<Long, EventCategory> oldToNewCategory = new HashMap<>();
        List<EventCategory> templateCategories = eventCategoryRepo.findByEvent(template);
        for (EventCategory templateCat : templateCategories) {
            EventCategory clonedCat = cloneCategory(templateCat, clone);
            oldToNewCategory.put(templateCat.getId(), clonedCat);

            // Clone scoring criteria for this category
            List<ScoringCriteria> criteria = scoringCriteriaRepo.findByEventAndEventCategory(template, templateCat);
            for (ScoringCriteria sc : criteria) {
                cloneScoringCriteria(sc, clone, clonedCat);
            }
        }
        // Also clone event-level criteria (eventCategory = null)
        List<ScoringCriteria> eventCriteria = scoringCriteriaRepo.findByEventAndEventCategory(template, null);
        for (ScoringCriteria sc : eventCriteria) {
            cloneScoringCriteria(sc, clone, null);
        }

        // 3. Clone EventParticipants
        Map<Long, EventParticipant> oldToNewEP = new HashMap<>();
        List<EventParticipant> templateEPs = eventParticipantRepo.findByEvent(template);
        for (EventParticipant templateEP : templateEPs) {
            EventParticipant clonedEP = cloneEventParticipant(templateEP, clone);
            oldToNewEP.put(templateEP.getId(), clonedEP);
        }

        // 4. Clone EventCategoryParticipants
        Map<String, EventCategoryParticipant> compositeKeyToNewECP = new HashMap<>();
        for (EventCategory templateCat : templateCategories) {
            List<EventCategoryParticipant> templateECPs =
                    eventCategoryParticipantRepo.findByEventCategory(templateCat);
            EventCategory clonedCat = oldToNewCategory.get(templateCat.getId());
            for (EventCategoryParticipant templateECP : templateECPs) {
                EventCategoryParticipant clonedECP = cloneECP(templateECP, clone, clonedCat);
                String oldCompositeKey = compositeKey(templateECP);
                compositeKeyToNewECP.put(oldCompositeKey, clonedECP);
            }
        }

        // 5. Clone scores
        for (EventCategory templateCat : templateCategories) {
            List<EventCategoryParticipant> templateECPs =
                    eventCategoryParticipantRepo.findByEventCategory(templateCat);
            for (EventCategoryParticipant templateECP : templateECPs) {
                List<Score> scores = scoreRepo.findByEventCategoryParticipant(templateECP);
                EventCategoryParticipant clonedECP =
                        compositeKeyToNewECP.get(compositeKey(templateECP));
                for (Score s : scores) {
                    cloneScore(s, clonedECP);
                }
            }
        }

        // 6. Clone feedback
        for (EventCategory templateCat : templateCategories) {
            List<EventCategoryParticipant> templateECPs =
                    eventCategoryParticipantRepo.findByEventCategory(templateCat);
            for (EventCategoryParticipant templateECP : templateECPs) {
                List<AuditionFeedback> feedbacks = auditionFeedbackRepo.findByEventCategoryParticipant(templateECP);
                EventCategoryParticipant clonedECP =
                        compositeKeyToNewECP.get(compositeKey(templateECP));
                for (AuditionFeedback fb : feedbacks) {
                    cloneFeedback(fb, clonedECP);
                }
            }
        }

        // 7. Generate session token for the requested role
        SessionToken token = new SessionToken();
        token.setTokenId(UUID.randomUUID().toString());
        token.setRole(role);
        token.setEvent(clone);
        token.setExpiresAt(LocalDateTime.now().plusDays(1));
        if ("JUDGE".equals(role)) {
            // Pick a random judge from the clone
            List<Judge> judges = judgeRepo.findJudgesByEventId(clone.getEventId());
            if (!judges.isEmpty()) {
                token.setJudge(judges.get(new Random().nextInt(judges.size())));
            }
        }
        token = sessionTokenRepo.save(token);

        // Record this IP request
        ipRequestLog.computeIfAbsent(clientIp, k -> new CopyOnWriteArrayList<>()).add(LocalDateTime.now());

        log.info("Demo sandbox created: {} for role {}", cloneName, role);

        return new CloneResult(clone, token);
    }

    @Transactional
    public void purgeSandbox(Long eventId) {
        Event event = eventRepo.findById(eventId).orElse(null);
        if (event == null || !event.getEventName().startsWith(CLONE_PREFIX)) {
            return; // safety: only purge demo events
        }
        log.info("Purging demo sandbox: {}", event.getEventName());

        // Delete in order: feedback -> scores -> ECPs -> criteria -> EPs -> categories -> event_judge -> tokens -> event
        List<EventCategory> categories = eventCategoryRepo.findByEvent(event);
        for (EventCategory cat : categories) {
            List<EventCategoryParticipant> ecps = eventCategoryParticipantRepo.findByEventCategory(cat);
            for (EventCategoryParticipant ecp : ecps) {
                auditionFeedbackRepo.deleteAll(auditionFeedbackRepo.findByEventCategoryParticipant(ecp));
                scoreRepo.deleteAll(scoreRepo.findByEventCategoryParticipant(ecp));
            }
            eventCategoryParticipantRepo.deleteAll(ecps);
            scoringCriteriaRepo.deleteAll(scoringCriteriaRepo.findByEventAndEventCategory(event, cat));
            // Remove judge assignments from category
            cat.getJudges().clear();
            eventCategoryRepo.save(cat);
        }
        // Delete event-level criteria
        scoringCriteriaRepo.deleteAll(scoringCriteriaRepo.findByEventAndEventCategory(event, null));
        // Delete EventParticipants
        List<EventParticipant> eps = eventParticipantRepo.findByEvent(event);
        eventParticipantRepo.deleteAll(eps);
        // Remove event_judge entries
        List<Judge> eventJudges = judgeRepo.findJudgesByEventId(event.getEventId());
        for (Judge j : eventJudges) {
            judgeRepo.deleteEventJudge(event.getEventId(), j.getJudgeId());
        }
        // Delete session tokens
        List<SessionToken> tokens = sessionTokenRepo.findByEvent_EventId(event.getEventId());
        sessionTokenRepo.deleteAll(tokens);
        // Delete categories
        eventCategoryRepo.deleteAll(categories);
        // Delete event
        eventRepo.delete(event);
    }

    @Scheduled(fixedRate = 6 * 3600 * 1000) // every 6 hours
    public void purgeOrphanSandboxes() {
        List<Event> demos = eventRepo.findAllDemoEvents();
        int purged = 0;
        for (Event demo : demos) {
            // Check if there are any valid session tokens for this event
            List<SessionToken> tokens = sessionTokenRepo.findByEvent_EventId(demo.getEventId());
            boolean hasValidToken = tokens.stream().anyMatch(t ->
                    !t.isRevoked() && t.getExpiresAt().isAfter(LocalDateTime.now()));
            if (!hasValidToken) {
                purgeSandbox(demo.getEventId());
                purged++;
            }
        }
        if (purged > 0) {
            log.info("Purged {} orphan demo sandboxes", purged);
        }
    }

    public int countActiveSandboxes() {
        return eventRepo.findAllDemoEvents().size();
    }

    private void checkIpRateLimit(String ip) {
        List<LocalDateTime> timestamps = ipRequestLog.getOrDefault(ip, Collections.emptyList());
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        long recent = timestamps.stream().filter(t -> t.isAfter(cutoff)).count();
        if (recent >= 3) {
            throw new DemoRateLimitException("Demo limit reached for today. Try again later.");
        }
    }

    private Event cloneEvent(Event template, String newName) {
        Event clone = new Event();
        clone.setEventName(newName);
        clone.setPaymentRequired(template.isPaymentRequired());
        clone.setJudgingMode(template.getJudgingMode());
        clone.setFeedbackEnabled(template.isFeedbackEnabled());
        clone.setResultsReleased(false);
        clone.setReleaseScore(false);
        clone.setAnimTheme(template.getAnimTheme());
        return eventRepo.save(clone);
    }

    private EventCategory cloneCategory(EventCategory template, Event newEvent) {
        EventCategory clone = new EventCategory();
        clone.setEvent(newEvent);
        clone.setName(template.getName());
        clone.setFormat(template.getFormat());
        clone.setSoloAllowed(template.isSoloAllowed());
        clone.setRoundLabel(template.getRoundLabel());
        clone.setNumberColor(template.getNumberColor());
        clone.setSheetAliases(template.getSheetAliases());
        // Copy judge list
        clone.setJudges(new ArrayList<>(template.getJudges()));
        return eventCategoryRepo.save(clone);
    }

    private void cloneScoringCriteria(ScoringCriteria template, Event newEvent, EventCategory newCat) {
        ScoringCriteria clone = new ScoringCriteria();
        clone.setEvent(newEvent);
        clone.setEventCategory(newCat);
        clone.setName(template.getName());
        clone.setWeight(template.getWeight());
        clone.setDisplayOrder(template.getDisplayOrder());
        scoringCriteriaRepo.save(clone);
    }

    private EventParticipant cloneEventParticipant(EventParticipant template, Event newEvent) {
        EventParticipant clone = new EventParticipant();
        clone.setEvent(newEvent);
        clone.setParticipant(template.getParticipant());
        clone.setPaymentVerified(template.isPaymentVerified());
        clone.setDisplayName(template.getDisplayName());
        clone.setStageName(template.getStageName());
        clone.setTeamName(template.getTeamName());
        clone.setCategory(template.getCategory());
        clone.setResidency(template.getResidency());
        clone.setReferenceCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        return eventParticipantRepo.save(clone);
    }

    private EventCategoryParticipant cloneECP(EventCategoryParticipant template, Event newEvent, EventCategory newCat) {
        EventCategoryParticipant clone = new EventCategoryParticipant();
        EventCategoryParticipantId newId = new EventCategoryParticipantId(
                newEvent.getEventId(), newCat.getId(), template.getParticipant().getParticipantId());
        clone.setId(newId);
        clone.setEvent(newEvent);
        clone.setEventCategory(newCat);
        clone.setParticipant(template.getParticipant());
        clone.setDisplayName(template.getDisplayName());
        clone.setFormat(template.getFormat());
        clone.setAuditionNumber(template.getAuditionNumber());
        clone.setTeamName(template.getTeamName());
        return eventCategoryParticipantRepo.save(clone);
    }

    private void cloneScore(Score template, EventCategoryParticipant newECP) {
        Score clone = new Score();
        clone.setEventCategoryParticipant(newECP);
        clone.setJudge(template.getJudge());
        clone.setAspect(template.getAspect());
        clone.setValue(template.getValue());
        scoreRepo.save(clone);
    }

    private void cloneFeedback(AuditionFeedback template, EventCategoryParticipant newECP) {
        AuditionFeedback clone = new AuditionFeedback();
        clone.setEventCategoryParticipant(newECP);
        clone.setJudge(template.getJudge());
        clone.setNote(template.getNote());
        clone.setTags(new HashSet<>(template.getTags()));
        auditionFeedbackRepo.save(clone);
    }

    private String compositeKey(EventCategoryParticipant ecp) {
        return ecp.getEvent().getEventId() + "-" +
                ecp.getEventCategory().getId() + "-" +
                ecp.getParticipant().getParticipantId();
    }

    private String randomUuid8() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    // ---- Inner classes ----

    public static class CloneResult {
        public final Event event;
        public final SessionToken token;

        public CloneResult(Event event, SessionToken token) {
            this.event = event;
            this.token = token;
        }
    }

    public static class DemoRateLimitException extends RuntimeException {
        public DemoRateLimitException(String message) {
            super(message);
        }
    }
}
