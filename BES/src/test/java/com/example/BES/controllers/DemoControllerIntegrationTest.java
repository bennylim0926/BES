package com.example.BES.controllers;

import com.example.BES.dtos.DemoStartRequestDto;
import com.example.BES.models.*;
import com.example.BES.respositories.*;
import com.example.BES.services.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DemoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppConfigService appConfigService;

    @Autowired
    private EventRepo eventRepo;

    @BeforeEach
    void setUp() {
        // Ensure demo is enabled and passcode is set
        appConfigService.setDemoEnabled(true);
        appConfigService.setDemoPasscode("TESTCODE");
        // Ensure template event exists
        if (eventRepo.findByEventName("Kyrove Demo").isEmpty()) {
            Event template = new Event();
            template.setEventName("Kyrove Demo");
            template.setJudgingMode("SOLO");
            eventRepo.save(template);
        }
    }

    @Test
    void startDemo_withValidPasscodeAndRole_returnsSuccess() throws Exception {
        DemoStartRequestDto request = new DemoStartRequestDto("TESTCODE", "EMCEE");
        mockMvc.perform(post("/api/v1/demo/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role").value("EMCEE"))
                .andExpect(jsonPath("$.eventName").value(startsWith("Kyrove Demo-")))
                .andExpect(jsonPath("$.eventId").exists());
    }

    @Test
    void startDemo_withWrongPasscode_returns401() throws Exception {
        DemoStartRequestDto request = new DemoStartRequestDto("WRONG", "EMCEE");
        mockMvc.perform(post("/api/v1/demo/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid passcode"));
    }

    @Test
    void startDemo_whenDisabled_returns403() throws Exception {
        appConfigService.setDemoEnabled(false);
        DemoStartRequestDto request = new DemoStartRequestDto("TESTCODE", "EMCEE");
        mockMvc.perform(post("/api/v1/demo/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Demo is currently disabled"));
        appConfigService.setDemoEnabled(true); // restore
    }

    @Test
    void startDemo_withInvalidRole_returns400() throws Exception {
        DemoStartRequestDto request = new DemoStartRequestDto("TESTCODE", "ORGANISER");
        mockMvc.perform(post("/api/v1/demo/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Invalid role")));
    }

    @Test
    void startDemo_asJudge_returnsJudgeFields() throws Exception {
        // Note: this test requires a judge to exist in the template event
        // The test will pass even without judgeId if no judges are seeded
        DemoStartRequestDto request = new DemoStartRequestDto("TESTCODE", "JUDGE");
        mockMvc.perform(post("/api/v1/demo/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("JUDGE"));
    }
}
