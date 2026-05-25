package com.example.BES.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.GetEmailTemplateDto;
import com.example.BES.dtos.UpdateEmailTemplateDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventEmailTemplate;
import com.example.BES.models.EventGenre;
import com.example.BES.respositories.EventEmailTemplateRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;

@Service
public class EmailTemplateService {

    @Autowired
    private EventEmailTemplateRepo repo;

    @Autowired
    private EventGenreRepo eventGenreRepo;

    @Autowired
    private EventRepo eventRepo;

    // Marker text present in the original generic default — used to detect un-customized templates
    private static final String GENERIC_MARKER = "Please show this QR code during registration to get your audition number.";

    public void createDefaultTemplate(Event event) {
        EventEmailTemplate template = new EventEmailTemplate();
        template.setEvent(event);
        template.setSubject("Confirmation email for " + event.getEventName());
        template.setBody(
            "Hello {name},\n\n" +
            "Thanks for registering for " + event.getEventName() + ".\n" +
            GENERIC_MARKER + "\n\n" +
            "Thank you."
        );
        repo.save(template);
    }

    public GetEmailTemplateDto getTemplateByEventName(String eventName) {
        EventEmailTemplate template = repo.findByEvent_EventName(eventName).orElse(null);
        if (template == null) return null;

        // Auto-upgrade generic default to smart template on first open after genres are set
        if (isGenericDefault(template.getBody())) {
            String smart = buildSmartBody(eventName);
            if (smart != null) {
                template.setBody(smart);
                repo.save(template);
            }
        }

        GetEmailTemplateDto dto = new GetEmailTemplateDto();
        dto.setSubject(template.getSubject());
        dto.setBody(template.getBody());
        return dto;
    }

    public GetEmailTemplateDto updateTemplate(String eventName, UpdateEmailTemplateDto dto) {
        EventEmailTemplate template = repo.findByEvent_EventName(eventName)
            .orElseThrow(() -> new RuntimeException("No email template found for event: " + eventName));
        template.setSubject(dto.getSubject());
        template.setBody(dto.getBody());
        repo.save(template);
        GetEmailTemplateDto result = new GetEmailTemplateDto();
        result.setSubject(template.getSubject());
        result.setBody(template.getBody());
        return result;
    }

    /** Regenerates the smart default based on current genre formats and saves it. */
    public GetEmailTemplateDto resetToSmartDefault(String eventName) {
        EventEmailTemplate template = repo.findByEvent_EventName(eventName)
            .orElseThrow(() -> new RuntimeException("No email template found for event: " + eventName));
        String smart = buildSmartBody(eventName);
        if (smart != null) {
            template.setBody(smart);
            repo.save(template);
        }
        GetEmailTemplateDto result = new GetEmailTemplateDto();
        result.setSubject(template.getSubject());
        result.setBody(template.getBody());
        return result;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private boolean isGenericDefault(String body) {
        return body != null && body.contains(GENERIC_MARKER);
    }

    private boolean isTeamFormat(String format) {
        return format != null && !format.equalsIgnoreCase("1v1");
    }

    /**
     * Determines event type from its genres and returns the appropriate template body.
     * Returns null if the event has no genres yet (can't determine type).
     */
    private String buildSmartBody(String eventName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return null;

        List<EventGenre> genres = eventGenreRepo.findByEvent(event);
        if (genres.isEmpty()) return null;

        boolean hasSolo = genres.stream().anyMatch(eg -> !isTeamFormat(eg.getFormat()));
        boolean hasTeam = genres.stream().anyMatch(eg -> isTeamFormat(eg.getFormat()));

        if (hasTeam && hasSolo) return buildMixedTemplate(event.getEventName());
        if (hasTeam)            return buildTeamTemplate(event.getEventName());
        return                         buildSoloTemplate(event.getEventName());
    }

    private String buildSoloTemplate(String eventName) {
        return
            "Hello {name},\n\n" +
            "Thanks for registering for " + eventName + "!\n" +
            "Please show this QR code during check-in to receive your audition number.\n\n" +
            "Category: {soloCategories}\n\n" +
            "Reference code: {refCode}\n\n" +
            "Thank you and see you on the floor!";
    }

    private String buildTeamTemplate(String eventName) {
        return
            "Hello {teamName},\n\n" +
            "Thanks for registering for " + eventName + "!\n" +
            "Please show this QR code during check-in to receive your audition number.\n\n" +
            "Team: {teamName}\n" +
            "Members: {stageName}, {members}\n" +
            "Category: {teamCategories}\n\n" +
            "Reference code: {refCode}\n\n" +
            "Thank you and see you on the floor!";
    }

    private String buildMixedTemplate(String eventName) {
        return
            "Hello {name},\n\n" +
            "Thanks for registering for " + eventName + "!\n" +
            "Please show this QR code during check-in to receive your audition number.\n\n" +
            "{if:solo}" +
            "Solo category: {soloCategories}\n" +
            "Stage name: {stageName}\n" +
            "{endif:solo}" +
            "{if:team}" +
            "Team: {teamName}\n" +
            "Members: {stageName}, {members}\n" +
            "Team category: {teamCategories}\n" +
            "{endif:team}" +
            "\nReference code: {refCode}\n\n" +
            "Thank you and see you on the floor!";
    }
}
