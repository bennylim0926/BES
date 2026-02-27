package com.example.BES.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
public class SecurityControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    public void testGetCsrfToken() throws Exception {
        // Since CSRF is disabled in SecurityConfig (.csrf(csrf -> csrf.disable())),
        // the token attribute might be missing and return empty body or basic 200 OK.
        // We just verify the endpoint maps correctly and returns 200 OK without errors.
        mockMvc.perform(get("/api/v1/security/csrf-token"))
                .andExpect(status().isOk());
    }
}
