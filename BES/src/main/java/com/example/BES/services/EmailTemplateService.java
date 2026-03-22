package com.example.BES.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.GetEmailTemplateDto;
import com.example.BES.dtos.UpdateEmailTemplateDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventEmailTemplate;
import com.example.BES.respositories.EventEmailTemplateRepo;

@Service
public class EmailTemplateService {

    @Autowired
    private EventEmailTemplateRepo repo;

    public void createDefaultTemplate(Event event) {
        EventEmailTemplate template = new EventEmailTemplate();
        template.setEvent(event);
        template.setSubject("Confirmation email for " + event.getEventName());
        template.setBody(
            "Hello {name},\n\n" +
            "Thanks for registering for " + event.getEventName() + ".\n" +
            "Please show this QR code during registration to get your audition number.\n\n" +
            "Thank you."
        );
        repo.save(template);
    }

    public GetEmailTemplateDto getTemplateByEventName(String eventName) {
        EventEmailTemplate template = repo.findByEvent_EventName(eventName).orElse(null);
        if (template == null) return null;
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
}
