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
    private final AuditionFeedbackRepository auditionFeedbackRepo;
    private final FeedbackTagRepository feedbackTagRepo;
    private final FeedbackTagGroupRepository feedbackTagGroupRepo;

    public DemoDataSeeder(EventRepo eventRepo, EventCategoryRepo eventCategoryRepo,
                          ParticipantRepo participantRepo, EventParticipantRepo eventParticipantRepo,
                          EventCategoryParticipantRepo eventCategoryParticipantRepo,
                          JudgeRepo judgeRepo, ScoringCriteriaRepo scoringCriteriaRepo,
                          ScoreRepo scoreRepo, AuditionFeedbackRepository auditionFeedbackRepo,
                          FeedbackTagRepository feedbackTagRepo, FeedbackTagGroupRepository feedbackTagGroupRepo) {
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
            cat.setJudges(new ArrayList<>(Arrays.asList(judges)));
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
        // First 15 per category get audition numbers; last 5 get null (for helper check-in)
        List<EventCategoryParticipant> hipHopECPs = new ArrayList<>();
        List<EventCategoryParticipant> poppingECPs = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            Participant p = participants.get(i);
            Integer auditionNum = i < 15 ? i + 1 : null;
            hipHopECPs.add(createECP(event, hipHop, p, auditionNum, "1v1"));
        }
        for (int i = 10; i < 30; i++) {
            Participant p = participants.get(i);
            Integer auditionNum = (i - 10) < 15 ? (i - 9) : null;
            poppingECPs.add(createECP(event, popping, p, auditionNum, "1v1"));
        }

        // 8. Pre-fill scores with intentional tie scenarios
        //
        // Hip Hop (20 participants, single-score, default judging):
        // All 3 judges give the same score per participant (so average = that score).
        // Design: 4-way tie at the Top 16 cutoff (positions 14-17 all scored 7.5),
        // and a 2-way tie at the Top 8 boundary (positions 7-8 both scored 8.3).
        double[] hipHopScores = {
            9.5, 9.3, 9.1, 8.9, 8.7,   // #1-5: clear leaders
            8.5, 8.3, 8.3,              // #6-8: tie at #7-8 (Top 8 boundary)
            8.0, 7.9, 7.8, 7.7, 7.6,   // #9-13
            7.5, 7.5, 7.5, 7.5,        // #14-17: 4-way tie (Top 16 boundary!)
            7.0, 6.5, 6.0               // #18-20
        };
        for (int i = 0; i < hipHopECPs.size(); i++) {
            double scoreVal = hipHopScores[i];
            for (Judge judge : judges) {
                createScore(hipHopECPs.get(i), judge, null, scoreVal);
            }
        }

        // Popping (20 participants, multi-criteria: Musicality, Technique, Originality):
        // Score 10 participants (odd indices in ECP list: 1,3,5,...,19).
        // Create a tie at Top 8: positions 7-9 all total ~22.5 (avg 7.5 per aspect).
        double[][] poppingTotals = {
            {9.0, 9.0, 9.0}, {8.5, 8.5, 8.5}, {8.0, 8.5, 8.0}, {8.0, 8.0, 8.0}, {7.5, 8.0, 7.5}, // scored #1-5
            {7.5, 7.5, 7.5}, {7.5, 7.5, 7.5}, {7.5, 7.5, 7.5},                                     // scored #6-8: 3-way tie
            {7.0, 7.5, 7.0}, {6.5, 7.0, 6.5}                                                         // scored #9-10
        };
        for (Judge judge : judges) {
            int scoredIdx = 0;
            for (int i = 1; i < poppingECPs.size(); i += 2) {
                if (scoredIdx < poppingTotals.length) {
                    EventCategoryParticipant ecp = poppingECPs.get(i);
                    double[] aspects = poppingTotals[scoredIdx];
                    // Add small judge-specific variation so scores differ slightly per judge
                    double judgeOffset = (judge.getJudgeId() % 3) * 0.1;
                    createScore(ecp, judge, "Musicality", Math.round((aspects[0] + judgeOffset) * 10.0) / 10.0);
                    createScore(ecp, judge, "Technique", Math.round((aspects[1] + judgeOffset) * 10.0) / 10.0);
                    createScore(ecp, judge, "Originality", Math.round((aspects[2] + judgeOffset) * 10.0) / 10.0);
                }
                scoredIdx++;
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
                                                Participant p, Integer auditionNum, String format) {
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
