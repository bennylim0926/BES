package com.example.BES.services;

import com.example.BES.models.*;
import com.example.BES.respositories.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DemoServiceTest {

    @Autowired
    private DemoService demoService;

    @Autowired
    private EventRepo eventRepo;

    @Autowired
    private AppConfigService appConfigService;

    @BeforeEach
    void setUp() {
        appConfigService.setDemoEnabled(true);
        appConfigService.setDemoPasscode("TEST");
        // Seed a minimal template
        if (eventRepo.findByEventName("Kyrove Demo").isEmpty()) {
            Event template = new Event();
            template.setEventName("Kyrove Demo");
            template.setJudgingMode("SOLO");
            template.setFeedbackEnabled(true);
            eventRepo.save(template);
        }
    }

    @Test
    void cloneTemplate_createsNewEventWithUuidSuffix() {
        DemoService.CloneResult result = demoService.cloneTemplate("EMCEE", "127.0.0.1");
        assertThat(result.event.getEventName()).startsWith("Kyrove Demo-");
        assertThat(result.event.getEventName()).isNotEqualTo("Kyrove Demo");
        assertThat(result.token).isNotNull();
        assertThat(result.token.getRole()).isEqualTo("EMCEE");
    }

    @Test
    void purgeSandbox_deletesEventAndChildren() {
        DemoService.CloneResult result = demoService.cloneTemplate("EMCEE", "127.0.0.2");
        Long eventId = result.event.getEventId();

        demoService.purgeSandbox(eventId);

        assertThat(eventRepo.findById(eventId)).isEmpty();
    }
}
