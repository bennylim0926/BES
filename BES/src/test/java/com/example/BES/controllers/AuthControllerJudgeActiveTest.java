package com.example.BES.controllers;

import com.example.BES.services.JudgeActiveStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthControllerJudgeActiveTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JudgeActiveStore judgeActiveStore;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        // Clear store between tests
        judgeActiveStore.getActiveSessions(42L, "Battle 2026").forEach(judgeActiveStore::release);
    }

    private MockHttpSession judgeSession(Long judgeId, String eventName) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("judgeId", judgeId);
        session.setAttribute("eventName", eventName);
        return session;
    }

    @Test
    @WithMockUser(roles = "JUDGE")
    void activeElsewhereFalseForFreshSession() throws Exception {
        MockHttpSession session = judgeSession(42L, "Battle 2026");

        mockMvc.perform(get("/api/v1/auth/judge/active-elsewhere")
                .param("judgeId", "42")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeElsewhere").value(false));
    }

    @Test
    @WithMockUser(roles = "JUDGE")
    void activeElsewhereTrueWhenAnotherSessionClaimed() throws Exception {
        MockHttpSession sessionA = judgeSession(42L, "Battle 2026");
        mockMvc.perform(post("/api/v1/auth/judge/claim").session(sessionA))
            .andExpect(status().isOk());

        MockHttpSession sessionB = judgeSession(42L, "Battle 2026");
        mockMvc.perform(get("/api/v1/auth/judge/active-elsewhere")
                .param("judgeId", "42")
                .session(sessionB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeElsewhere").value(true));
    }

    @Test
    @WithMockUser(roles = "JUDGE")
    void releaseClearsActiveSession() throws Exception {
        MockHttpSession sessionA = judgeSession(42L, "Battle 2026");
        mockMvc.perform(post("/api/v1/auth/judge/claim").session(sessionA))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/auth/judge/release").session(sessionA))
            .andExpect(status().isOk());

        MockHttpSession sessionB = judgeSession(42L, "Battle 2026");
        mockMvc.perform(get("/api/v1/auth/judge/active-elsewhere")
                .param("judgeId", "42")
                .session(sessionB))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeElsewhere").value(false));
    }

    @Test
    @WithMockUser(roles = "JUDGE")
    void claimReturns400WhenSessionMissingJudgeId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("eventName", "Battle 2026");

        mockMvc.perform(post("/api/v1/auth/judge/claim").session(session))
            .andExpect(status().isBadRequest());
    }
}
