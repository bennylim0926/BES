package com.example.BES.services;

import com.example.BES.models.*;
import com.example.BES.respositories.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String TEMPLATE_NAME = "Kyrove Demo";

    private final EventRepo eventRepo;
    private final EventCategoryRepo eventCategoryRepo;
    private final ParticipantRepo participantRepo;
    private final EventParticipantRepo eventParticipantRepo;
    private final EventCategoryParticipantRepo eventCategoryParticipantRepo;
    private final JudgeRepo judgeRepo;
    private final ScoringCriteriaRepo scoringCriteriaRepo;
    private final ScoreRepo scoreRepo;
    private final AuditionFeedbackRepo auditionFeedbackRepo;
    private final FeedbackTagRepo feedbackTagRepo;
    private final FeedbackTagGroupRepo feedbackTagGroupRepo;

    public DemoDataSeeder(EventRepo eventRepo, EventCategoryRepo eventCategoryRepo,
                          ParticipantRepo participantRepo, EventParticipantRepo eventParticipantRepo,
                          EventCategoryParticipantRepo eventCategoryParticipantRepo,
                          JudgeRepo judgeRepo, ScoringCriteriaRepo scoringCriteriaRepo,
                          ScoreRepo scoreRepo, AuditionFeedbackRepo auditionFeedbackRepo,
                          FeedbackTagRepo feedbackTagRepo, FeedbackTagGroupRepo feedbackTagGroupRepo) {
        this.eventRepo = eventRepo;
        this.eventCategoryRepo = eventCategoryRepo;
        this.participantRepo = participantRepo;
        this.eventParticipantRepo = eventParticipantRepo;
        this.eventCategoryParticipantRepo = eventCategoryParticipantRepo;
        this.judgeRepo = judgeRepo;
        this.scoringCriteriaRepo = scoringCriteriaRepo;
        this.scoreRepo = scoreRepo;
        this.auditionFeedbackRepo = auditionFeedbackRepo;
        this.feedbackTagRepo = feedbackTagRepo;
        this.feedbackTagGroupRepo = feedbackTagGroupRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (eventRepo.findByEventName(TEMPLATE_NAME).isPresent()) {
            log.info("Demo template event already exists, skipping seed");
            return;
        }
        log.info("Seeding demo template event '{}'...", TEMPLATE_NAME);

        // 1. Create template event
        Event event = new Event();
        event.setEventName(TEMPLATE_NAME);
        event.setPaymentRequired(false);
        event.setJudgingMode("SOLO");
        event.setFeedbackEnabled(true);
        event.setResultsReleased(false);
        event.setReleaseScore(false);
        event.setAnimTheme("impact");
        event = eventRepo.save(event);

        // 2. Create categories
        EventCategory hipHop = createCategory(event, "Hip Hop", "1v1");
        EventCategory popping = createCategory(event, "Popping", "1v1");
        EventCategory[] categories = {hipHop, popping};

        // 3. Create scoring criteria for Popping (multi-criteria)
        createCriterion(event, popping, "Musicality", 1.0, 0);
        createCriterion(event, popping, "Technique", 1.0, 1);
        createCriterion(event, popping, "Originality", 1.0, 2);

        // 4. Create judges
        Judge judge1 = createJudge("DJ FLEX");
        Judge judge2 = createJudge("B-Girl RAY");
        Judge judge3 = createJudge("Kid Kazoo");
        Judge[] judges = {judge1, judge2, judge3};

        // Assign judges to event + categories
        for (Judge judge : judges) {
            judgeRepo.insertEventJudge(event.getEventId(), judge.getJudgeId());
        }
        for (EventCategory cat : categories) {
            cat.setJudges(Arrays.asList(judges));
            eventCategoryRepo.save(cat);
        }

        // 5. Create participants (30 unique, 20 per category, ~10 overlap)
        String[] allNames = {
            "B-Boy Spinz", "Poppin J", "Lil Flow", "Kid Twist", "Lady Glide",
            "Rock Steady", "Mighty Mouse", "Turbo T", "Fresh Kicks", "Smooth Move",
            "Wild Card", "Shadow Step", "Beat Breaker", "Style King", "Lock N Load",
            "Wave Rider", "Funk Master", "Cypher Queen", "Groove Theory", "Spin Doctor",
            "Hip Hop Harry", "Pop N Drop", "Freeze Frame", "Rhythm Nation", "Soul Train",
            "Electric Boogaloo", "Floor Phantom", "King Tut", "Robot X", "Moon Walker"
        };

        List<Participant> participants = new ArrayList<>();
        for (String name : allNames) {
            Participant p = participantRepo.findFirstByParticipantName(name)
                    .orElseGet(() -> {
                        Participant np = new Participant();
                        np.setParticipantName(name);
                        return participantRepo.save(np);
                    });
            participants.add(p);
        }

        // 6. Create EventParticipant links (all 30 to the event)
        List<EventParticipant> eventParticipants = new ArrayList<>();
        Random rng = new Random(42); // fixed seed for reproducibility
        for (Participant p : participants) {
            EventParticipant ep = new EventParticipant();
            ep.setEvent(event);
            ep.setParticipant(p);
            ep.setPaymentVerified(true);
            ep.setDisplayName(p.getParticipantName());
            ep.setStageName(p.getParticipantName());
            ep.setReferenceCode(generateRefCode());
            eventParticipants.add(eventParticipantRepo.save(ep));
        }

        // 7. Create EventCategoryParticipant links
        // Hip Hop: participants 0-19 (first 20)
        // Popping: participants 10-29 (last 20, overlapping 10-19)
        List<EventCategoryParticipant> hipHopECPs = new ArrayList<>();
        List<EventCategoryParticipant> poppingECPs = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            Participant p = participants.get(i);
            hipHopECPs.add(createECP(event, hipHop, p, i + 1, "1v1"));
        }
        for (int i = 10; i < 30; i++) {
            Participant p = participants.get(i);
            poppingECPs.add(createECP(event, popping, p, i - 9, "1v1"));
        }

        // 8. Pre-fill scores (~50% of participants scored per category per judge)
        // Hip Hop: judges score participants at even indices (0,2,4,...,18) = 10 scored
        // Popping: judges score participants at odd indices in the popping list = 10 scored
        for (Judge judge : judges) {
            for (int i = 0; i < hipHopECPs.size(); i += 2) {
                double scoreVal = 5.0 + rng.nextDouble() * 4.5; // 5.0-9.5
                createScore(hipHopECPs.get(i), judge, null, Math.round(scoreVal * 10.0) / 10.0);
            }
            for (int i = 1; i < poppingECPs.size(); i += 2) {
                // For Popping, create 3 aspect scores per scored participant
                EventCategoryParticipant ecp = poppingECPs.get(i);
                createScore(ecp, judge, "Musicality", 5.0 + rng.nextDouble() * 4.5);
                createScore(ecp, judge, "Technique", 5.0 + rng.nextDouble() * 4.5);
                createScore(ecp, judge, "Originality", 5.0 + rng.nextDouble() * 4.5);
            }
        }

        // 9. Pre-fill feedback on ~30% of scored participants
        List<FeedbackTag> allTags = feedbackTagRepo.findAll();
        if (!allTags.isEmpty()) {
            for (Judge judge : judges) {
                for (int i = 0; i < hipHopECPs.size(); i += 6) { // every 6th
                    createFeedback(hipHopECPs.get(i), judge, allTags, rng);
                }
            }
        }

        log.info("Demo template event seeded successfully with {} participants, {} judges",
                participants.size(), judges.length);
    }

    private EventCategory createCategory(Event event, String name, String format) {
        EventCategory cat = new EventCategory();
        cat.setEvent(event);
        cat.setName(name);
        cat.setFormat(format);
        cat.setSoloAllowed(true);
        return eventCategoryRepo.save(cat);
    }

    private void createCriterion(Event event, EventCategory cat, String name, double weight, int order) {
        ScoringCriteria sc = new ScoringCriteria();
        sc.setEvent(event);
        sc.setEventCategory(cat);
        sc.setName(name);
        sc.setWeight(weight);
        sc.setDisplayOrder(order);
        scoringCriteriaRepo.save(sc);
    }

    private Judge createJudge(String name) {
        return judgeRepo.findFirstByName(name)
                .orElseGet(() -> {
                    Judge j = new Judge();
                    j.setName(name);
                    return judgeRepo.save(j);
                });
    }

    private EventCategoryParticipant createECP(Event event, EventCategory cat,
                                                Participant p, int auditionNum, String format) {
        EventCategoryParticipant ecp = new EventCategoryParticipant();
        EventCategoryParticipantId id = new EventCategoryParticipantId(
                event.getEventId(), cat.getId(), p.getParticipantId());
        ecp.setId(id);
        ecp.setEvent(event);
        ecp.setEventCategory(cat);
        ecp.setParticipant(p);
        ecp.setAuditionNumber(auditionNum);
        ecp.setFormat(format);
        ecp.setDisplayName(p.getParticipantName());
        return eventCategoryParticipantRepo.save(ecp);
    }

    private void createScore(EventCategoryParticipant ecp, Judge judge, String aspect, double value) {
        Score score = new Score();
        score.setEventCategoryParticipant(ecp);
        score.setJudge(judge);
        score.setAspect(aspect);
        score.setValue(value);
        scoreRepo.save(score);
    }

    private void createFeedback(EventCategoryParticipant ecp, Judge judge,
                                 List<FeedbackTag> allTags, Random rng) {
        AuditionFeedback fb = new AuditionFeedback();
        fb.setEventCategoryParticipant(ecp);
        fb.setJudge(judge);
        fb.setNote("Great energy and stage presence.");
        Set<FeedbackTag> tags = new HashSet<>();
        // pick 1-2 random tags
        tags.add(allTags.get(rng.nextInt(allTags.size())));
        if (rng.nextBoolean() && allTags.size() > 1) {
            tags.add(allTags.get(rng.nextInt(allTags.size())));
        }
        fb.setTags(tags);
        auditionFeedbackRepo.save(fb);
    }

    private String generateRefCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    }
}
