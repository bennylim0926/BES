package com.example.BES.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class AppConfigControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void getAppConfig_unauthenticated_returnsDefault() throws Exception {
        mockMvc.perform(get("/api/v1/config/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accentColor").value("#ffffff"));
    }

    @Test
    public void postAppConfig_unauthenticated_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/config/app")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accentColor\":\"#ff0000\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    public void postAppConfig_admin_savesColor() throws Exception {
        mockMvc.perform(post("/api/v1/config/app")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accentColor\":\"#06b6d4\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accentColor").value("#06b6d4"));

        mockMvc.perform(get("/api/v1/config/app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accentColor").value("#06b6d4"));
    }

    @Test
    @WithMockUser(username = "organiser", roles = {"ORGANISER"})
    public void postAppConfig_organiser_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/config/app")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"accentColor\":\"#ff0000\"}"))
                .andExpect(status().isForbidden());
    }
}
