package com.example.BES;

import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JudgeAssignmentModeToggleTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EventRepo eventRepo;
    @Autowired EventCategoryRepo categoryRepo;

    private Long categoryId;

    @BeforeEach
    void setup() {
        Event event = new Event();
        event.setEventName("ToggleTestEvent");
        eventRepo.save(event);
        EventCategory cat = new EventCategory();
        cat.setEvent(event);
        cat.setName("HipHop");
        categoryId = categoryRepo.save(cat).getId();
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void toggleOn_sets_judgeAssignmentMode_true() throws Exception {
        mockMvc.perform(post("/api/v1/event/ToggleTestEvent/categories/" + categoryId + "/judge-assignment-mode")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("enabled", true))))
                .andExpect(status().isOk());

        assertThat(categoryRepo.findById(categoryId).get().isJudgeAssignmentMode()).isTrue();
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void toggleOff_sets_judgeAssignmentMode_false() throws Exception {
        EventCategory cat = categoryRepo.findById(categoryId).get();
        cat.setJudgeAssignmentMode(true);
        categoryRepo.save(cat);

        mockMvc.perform(post("/api/v1/event/ToggleTestEvent/categories/" + categoryId + "/judge-assignment-mode")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("enabled", false))))
                .andExpect(status().isOk());

        assertThat(categoryRepo.findById(categoryId).get().isJudgeAssignmentMode()).isFalse();
    }
}
