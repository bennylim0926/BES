package com.example.BES;

import com.example.BES.models.*;
import com.example.BES.respositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CheckinJudgeModeValidationTest {

    @Autowired MockMvc mockMvc;
    @Autowired EventRepo eventRepo;
    @Autowired EventCategoryRepo categoryRepo;
    @Autowired ParticipantRepo participantRepo;
    @Autowired EventParticipantRepo eventParticipantRepo;
    @Autowired EventCategoryParticipantRepo ecpRepo;

    @Test
    @WithMockUser(authorities = "ROLE_HELPER")
    void checkin_blocked_when_mode_on_and_no_judge_assigned() throws Exception {
        Event event = new Event();
        event.setEventName("CheckinTestEvent");
        eventRepo.save(event);

        EventCategory cat = new EventCategory();
        cat.setEvent(event);
        cat.setName("Open");
        cat.setJudgeAssignmentMode(true);
        categoryRepo.save(cat);

        Participant p = new Participant();
        p.setParticipantName("TestDancer");
        participantRepo.save(p);

        EventParticipant ep = new EventParticipant();
        ep.setEvent(event);
        ep.setParticipant(p);
        eventParticipantRepo.save(ep);

        EventCategoryParticipant ecp = new EventCategoryParticipant();
        ecp.setId(new EventCategoryParticipantId(event.getEventId(), cat.getId(), p.getParticipantId()));
        ecp.setEvent(event);
        ecp.setEventCategory(cat);
        ecp.setParticipant(p);
        // judge intentionally NOT set
        ecpRepo.save(ecp);

        mockMvc.perform(get("/api/v1/event/register-participant/"
                + p.getParticipantId() + "/" + event.getEventId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().string("no_judge_assigned"));
    }
}
