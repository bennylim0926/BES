package com.example.BES.services;

import com.example.BES.dtos.GetEmailTemplateDto;
import com.example.BES.dtos.UpdateEmailTemplateDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventEmailTemplate;
import com.example.BES.respositories.EventEmailTemplateRepo;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailTemplateServiceTest {

    @Mock EventEmailTemplateRepo repo;
    @Mock EventCategoryRepo eventGenreRepo;
    @Mock EventRepo eventRepo;
    @InjectMocks EmailTemplateService service;

    @Test
    void createDefaultTemplate_savesTemplate() {
        Event e = new Event();
        e.setEventName("Fest");

        service.createDefaultTemplate(e);

        verify(repo).save(argThat(t ->
            ((EventEmailTemplate) t).getSubject().contains("Fest") &&
            ((EventEmailTemplate) t).getBody().contains("Fest")
        ));
    }

    @Test
    void getTemplateByEventName_returnsNullWhenNotFound() {
        when(repo.findByEvent_EventName("Missing")).thenReturn(Optional.empty());

        assertThat(service.getTemplateByEventName("Missing")).isNull();
    }

    @Test
    void getTemplateByEventName_returnsDto() {
        EventEmailTemplate t = new EventEmailTemplate();
        t.setSubject("Test Subject");
        t.setBody("Custom body");
        when(repo.findByEvent_EventName("Fest")).thenReturn(Optional.of(t));

        GetEmailTemplateDto result = service.getTemplateByEventName("Fest");

        assertThat(result.getSubject()).isEqualTo("Test Subject");
        assertThat(result.getBody()).isEqualTo("Custom body");
    }

    @Test
    void updateTemplate_updatesAndReturns() {
        EventEmailTemplate t = new EventEmailTemplate();
        t.setSubject("Old");
        t.setBody("Old body");
        UpdateEmailTemplateDto dto = new UpdateEmailTemplateDto();
        dto.setSubject("New Subject");
        dto.setBody("New Body");
        when(repo.findByEvent_EventName("Fest")).thenReturn(Optional.of(t));
        when(repo.save(any())).thenReturn(t);

        GetEmailTemplateDto result = service.updateTemplate("Fest", dto);

        assertThat(result.getSubject()).isEqualTo("New Subject");
    }

    @Test
    void updateTemplate_throwsWhenNotFound() {
        UpdateEmailTemplateDto dto = new UpdateEmailTemplateDto();
        dto.setSubject("X");
        dto.setBody("Y");
        when(repo.findByEvent_EventName("Missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTemplate("Missing", dto))
            .isInstanceOf(RuntimeException.class);
    }
}
