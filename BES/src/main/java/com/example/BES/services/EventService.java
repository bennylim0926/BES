package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.BES.models.*;
import com.example.BES.dtos.AddEventDto;
import com.example.BES.dtos.GetEventDto;
import com.example.BES.dtos.GetFeedbackEnabledDto;
import com.example.BES.dtos.GetJudgingModeDto;
import com.example.BES.dtos.UpdateReleaseScoreDto;
import com.example.BES.respositories.*;

@Service
public class EventService {
    @Autowired
    EventRepo repo;

    @Autowired
    EmailTemplateService emailTemplateService;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    private ScoreRepo scoreRepo;

    @Autowired
    private AuditionFeedbackRepository auditionFeedbackRepository;

    @Autowired
    private EventCategoryParticipantRepo eventCategoryParticipantRepo;

    @Autowired
    private EventCategoryParticipantMemberRepo eventCategoryParticipantMemberRepo;

    @Autowired
    private EventParticipantRepo eventParticipantRepo;

    @Autowired
    private EventParticipantTeamMemberRepo eventParticipantTeamMemberRepo;

    @Autowired
    private ScoringCriteriaRepo scoringCriteriaRepo;

    @Autowired
    private FeedbackTagRepository feedbackTagRepository;

    @Autowired
    private FeedbackTagGroupRepository feedbackTagGroupRepository;

    @Autowired
    private EventCategoryBattleGuestRepo eventCategoryBattleGuestRepo;

    @Autowired
    private PickupCrewRepo pickupCrewRepo;

    @Autowired
    private SessionTokenRepository sessionTokenRepository;

    @Autowired
    private BattleCategoryStateRepository battleCategoryStateRepository;

    @Autowired
    private BattleActiveCategoryRepository battleActiveCategoryRepository;

    @Autowired
    private EventEmailTemplateRepo eventEmailTemplateRepo;

    @Autowired
    private EventCategoryRepo eventCategoryRepo;

    @Autowired
    private JudgeRepo judgeRepo;

    public void createEventService(AddEventDto dto){
        if (repo.findByEventName(dto.eventName).isPresent()) return;
        Event newEvent = new Event();
        newEvent.setEventName(dto.eventName);
        newEvent.setPaymentRequired(dto.paymentRequired);
        newEvent.setFeedbackEnabled(dto.feedbackEnabled);
        Event saved = repo.save(newEvent);
        emailTemplateService.createDefaultTemplate(saved);
    }

    public String getEventNameById(Long eventId) {
        return repo.findById(eventId)
            .map(Event::getEventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    public AddEventDto findEventbyNameSerivce(String eventName){
        Event event = repo.findByEventName(eventName).orElse(null);
        if(event != null){
            AddEventDto dto = new AddEventDto();
            dto.eventName = eventName;
            return dto;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public List<GetEventDto> getAllEvents(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<Event> events;

        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            events = repo.findAll();
        } else if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ORGANISER"))) {
            Account account = accountRepository.findByUsername(auth.getName()).orElse(null);
            events = account != null && account.getAssignedEvents() != null
                ? account.getAssignedEvents()
                : List.of();
        } else {
            events = repo.findAll();
        }

        List<GetEventDto> dtos = new ArrayList<>();
        for(Event event: events){
            GetEventDto dto = new GetEventDto();
            dto.setId(event.getEventId());
            dto.setName(event.getEventName());
            dto.setPaymentRequired(event.isPaymentRequired());
            dto.setFeedbackEnabled(event.isFeedbackEnabled());
            dto.setReleaseScore(event.isReleaseScore());
            dtos.add(dto);
        }
        return dtos;
    }

    public GetJudgingModeDto getJudgingMode(String eventName) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        return new GetJudgingModeDto(event.getEventName(), event.getJudgingMode());
    }

    public void setJudgingMode(String eventName, String mode) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        event.setJudgingMode(mode);
        repo.save(event);
        messagingTemplate.convertAndSend("/topic/judging-mode/",
            java.util.Map.of("eventName", eventName, "judgingMode", mode));
    }

    public GetFeedbackEnabledDto getFeedbackEnabled(String eventName) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        return new GetFeedbackEnabledDto(event.getEventName(), event.isFeedbackEnabled());
    }

    public void setFeedbackEnabled(String eventName, boolean enabled) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        event.setFeedbackEnabled(enabled);
        repo.save(event);
        messagingTemplate.convertAndSend("/topic/feedback-enabled/",
            java.util.Map.of("eventName", eventName, "feedbackEnabled", enabled));
    }

    public void releaseResults(String eventName, boolean released) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        event.setResultsReleased(released);
        repo.save(event);
    }

    public boolean isResultsReleased(String eventName) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        return event.isResultsReleased();
    }

    public void setReleaseScore(String eventName, boolean releaseScore) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        event.setReleaseScore(releaseScore);
        repo.save(event);
    }

    public boolean isReleaseScore(String eventName) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        return event.isReleaseScore();
    }

    /**
     * Return the list of organiser accounts assigned to the given event.
     *
     * <p>The organiser ↔ event relationship is owned by {@link Account#getAssignedEvents()}
     * (join table {@code organiser_event}); the {@link Event} side has no inverse field.
     * We therefore query all organisers and filter those whose assigned events contain
     * this event. The set of organisers is expected to be small (single digits), so an
     * in-memory filter is fine; revisit if scale becomes a concern.</p>
     *
     * @return the matching organisers, or an empty list when the event is unknown
     */
    @Transactional(readOnly = true)
    public List<Account> getAssignedOrganisers(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            return new ArrayList<>();
        }
        Event event = repo.findByEventName(eventName).orElse(null);
        if (event == null) {
            return new ArrayList<>();
        }
        List<Account> organisers = accountRepository.findByRole("ORGANISER");
        List<Account> assigned = new ArrayList<>();
        for (Account account : organisers) {
            List<Event> events = account.getAssignedEvents();
            if (events != null && events.contains(event)) {
                assigned.add(account);
            }
        }
        return assigned;
    }

    @Transactional
    public void deleteEvent(String eventName) {
        Event event = repo.findByEventName(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        Long eventId = event.getEventId();

        // 1. Categories and their children
        List<EventCategory> categories = eventCategoryRepo.findByEvent(event);
        for (EventCategory cat : categories) {
            List<EventCategoryParticipant> ecps = eventCategoryParticipantRepo.findByEventCategory(cat);
            for (EventCategoryParticipant ecp : ecps) {
                // 1a. AuditionFeedback
                auditionFeedbackRepository.deleteByEventCategoryParticipant(ecp);
                // 1b. Scores (all judges)
                scoreRepo.deleteAllByEventCategoryParticipant(ecp);
                // 1c. Category participant members
                eventCategoryParticipantMemberRepo.deleteByEventCategoryParticipant(ecp);
            }
            // 1d. EventCategoryParticipants for this category
            eventCategoryParticipantRepo.deleteByEventCategory(cat);
        }

        // 2. EventParticipants and team members
        List<EventParticipant> participants = eventParticipantRepo.findByEvent(event);
        for (EventParticipant ep : participants) {
            eventParticipantTeamMemberRepo.deleteByEventParticipant(ep);
        }
        eventParticipantRepo.deleteByEvent(event);

        // 3. ScoringCriteria (event-level and category-level)
        scoringCriteriaRepo.deleteByEvent(event);
        for (EventCategory cat : categories) {
            scoringCriteriaRepo.deleteByEventCategory(cat);
        }

        // 4. Feedback tags and groups (event-scoped)
        feedbackTagRepository.deleteAll(feedbackTagRepository.findByEventEventId(eventId));
        feedbackTagGroupRepository.deleteAll(feedbackTagGroupRepository.findByEventEventId(eventId));

        // 5. Battle guests
        eventCategoryBattleGuestRepo.deleteByEvent(event);

        // 6. Pickup crews (members cascade via orphanRemoval)
        List<PickupCrew> crews = pickupCrewRepo.findByEvent(event);
        pickupCrewRepo.deleteAll(crews);

        // 7. Session tokens
        sessionTokenRepository.deleteAll(sessionTokenRepository.findByEvent_EventId(eventId));

        // 8. Battle state
        battleCategoryStateRepository.deleteAll(battleCategoryStateRepository.findByEventName(eventName));

        // 8b. Battle active category
        battleActiveCategoryRepository.deleteByEventName(eventName);

        // 9. Clear event_judge join table
        List<Judge> eventJudges = judgeRepo.findJudgesByEventId(eventId);
        for (Judge judge : eventJudges) {
            judgeRepo.deleteEventJudge(eventId, judge.getJudgeId());
        }

        // 9b. Clear event_category_judge join table — clear each category's judges list
        for (EventCategory cat : categories) {
            cat.getJudges().clear();
            eventCategoryRepo.save(cat);
        }

        // 10. Email template
        eventEmailTemplateRepo.findByEvent_EventId(eventId)
            .ifPresent(eventEmailTemplateRepo::delete);

        // 11. EventCategories
        eventCategoryRepo.deleteByEvent(event);

        // 12. Clear organiser_event join table
        List<Account> organisers = accountRepository.findByRole("ORGANISER");
        for (Account org : organisers) {
            List<Event> assigned = org.getAssignedEvents();
            if (assigned != null && assigned.removeIf(e -> e.getEventId().equals(eventId))) {
                accountRepository.save(org);
            }
        }

        // 13. Finally
        repo.delete(event);
    }
}
