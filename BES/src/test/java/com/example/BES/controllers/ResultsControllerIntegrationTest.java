package com.example.BES.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResultsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getResults_returnsNotFoundForUnknownRef() throws Exception {
        mockMvc.perform(get("/api/v1/results").param("ref", "NONEXISTENT-REF"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void getResults_isPublicNoAuthRequired() throws Exception {
        // Endpoint must be reachable without authentication
        mockMvc.perform(get("/api/v1/results").param("ref", "ANY"))
            .andExpect(status().isNotFound()); // not 403
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void getResultsQr_returnsImageForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/results/qr").param("ref", "TEST-REF"))
            .andExpect(status().isOk());
    }

    @Test
    void getResultsQr_returns403WithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/results/qr").param("ref", "TEST-REF"))
            .andExpect(status().isForbidden());
    }
}
