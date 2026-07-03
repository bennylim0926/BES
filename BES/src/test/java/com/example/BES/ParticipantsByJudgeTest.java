package com.example.BES;

import com.example.BES.models.*;
import com.example.BES.respositories.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ParticipantsByJudgeTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EventRepo eventRepo;
    @Autowired EventCategoryRepo categoryRepo;
    @Autowired ParticipantRepo participantRepo;
    @Autowired JudgeRepo judgeRepo;
    @Autowired EventCategoryParticipantRepo ecpRepo;

    @Test
    @WithMockUser(authorities = "ROLE_ORGANISER")
    void returns_participants_grouped_by_judge() throws Exception {
        Event event = new Event();
        event.setEventName("ByJudgeTestEvent");
        eventRepo.save(event);

        EventCategory cat = new EventCategory();
        cat.setEvent(event);
        cat.setName("Open");
        categoryRepo.save(cat);

        Judge j1 = new Judge(); j1.setName("Jay"); judgeRepo.save(j1);
        Judge j2 = new Judge(); j2.setName("Kay"); judgeRepo.save(j2);

        Participant p1 = new Participant(); p1.setParticipantName("Alice"); participantRepo.save(p1);
        Participant p2 = new Participant(); p2.setParticipantName("Bob");   participantRepo.save(p2);
        Participant p3 = new Participant(); p3.setParticipantName("Carol"); participantRepo.save(p3);

        // p1 → j1 #2, p2 → j1 #1, p3 → j2 #1
        saveEcp(event, cat, p1, j1, 2);
        saveEcp(event, cat, p2, j1, 1);
        saveEcp(event, cat, p3, j2, 1);

        String body = mockMvc.perform(get("/api/v1/event/ByJudgeTestEvent/categories/" + cat.getId() + "/participants-by-judge"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Map<String, List<Map<String, Object>>> result = objectMapper.readValue(body, new TypeReference<>() {});
        assertThat(result).containsKeys("Jay", "Kay");
        // Jay's list should be sorted by auditionNumber: Bob (#1), Alice (#2)
        assertThat(result.get("Jay")).hasSize(2);
        assertThat(result.get("Jay").get(0).get("participantName")).isEqualTo("Bob");
        assertThat(result.get("Jay").get(1).get("participantName")).isEqualTo("Alice");
        assertThat(result.get("Kay")).hasSize(1);
        assertThat(result.get("Kay").get(0).get("participantName")).isEqualTo("Carol");
    }

    private void saveEcp(Event e, EventCategory cat, Participant p, Judge j, int num) {
        EventCategoryParticipant ecp = new EventCategoryParticipant();
        ecp.setId(new EventCategoryParticipantId(e.getEventId(), cat.getId(), p.getParticipantId()));
        ecp.setEvent(e);
        ecp.setEventCategory(cat);
        ecp.setParticipant(p);
        ecp.setDisplayName(p.getParticipantName());
        ecp.setJudge(j);
        ecp.setAuditionNumber(num);
        ecpRepo.save(ecp);
    }
}
