package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.BES.models.Account;
import com.example.BES.models.Event;
import com.example.BES.dtos.AddEventDto;
import com.example.BES.dtos.GetEventDto;
import com.example.BES.dtos.GetFeedbackEnabledDto;
import com.example.BES.dtos.GetJudgingModeDto;
import com.example.BES.respositories.AccountRepository;
import com.example.BES.respositories.EventRepo;

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

    public void createEventService(AddEventDto dto){
        if (repo.findByEventName(dto.eventName).isPresent()) return;
        Event newEvent = new Event();
        newEvent.setEventName(dto.eventName);
        newEvent.setPaymentRequired(dto.paymentRequired);
        newEvent.setFeedbackEnabled(dto.feedbackEnabled);
        if (dto.accessCode != null && dto.accessCode.matches("\\d{4}")) {
            newEvent.setAccessCode(dto.accessCode);
        } else {
            newEvent.setAccessCode(String.format("%04d", ThreadLocalRandom.current().nextInt(10000)));
        }
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
    public List<GetEventDto> getAllEvents(boolean includeAccessCode){
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
            if (includeAccessCode) {
                dto.setAccessCode(event.getAccessCode());
            }
            dtos.add(dto);
        }
        return dtos;
    }

    public boolean verifyAccessCode(Long eventId, String code){
        Event event = repo.findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        return event.getAccessCode().equals(code);
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

    public void updateAccessCode(Long eventId, String newCode){
        if (newCode == null || !newCode.matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Access code must be exactly 4 digits");
        }
        Event event = repo.findById(eventId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        event.setAccessCode(newCode);
        repo.save(event);
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
}
