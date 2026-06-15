package com.example.BES.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.models.Account;
import com.example.BES.respositories.AccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class BattleControllerTierTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
    }

    private Account createOrganiser(String username, String tier) {
        Account account = new Account();
        account.setUsername(username);
        account.setPasswordHash("password-hash");
        account.setRole("ORGANISER");
        account.setTier(tier);
        account.setReferralCode("T" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase());
        account.setCreatedAt(LocalDateTime.now());
        return accountRepository.save(account);
    }

    @Test
    @WithMockUser(username = "pro_org", roles = {"ORGANISER"})
    void proOrganiser_isForbiddenFromBattleScore() throws Exception {
        createOrganiser("pro_org", "PRO");

        mockMvc.perform(post("/api/v1/battle/score")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "max_org", roles = {"ORGANISER"})
    void maxOrganiser_canPostBattleScore() throws Exception {
        createOrganiser("max_org", "MAX");

        mockMvc.perform(post("/api/v1/battle/score")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, s,
                        "MAX organiser should not be forbidden by tier gate");
                });
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void admin_alwaysHasBattleAccess_evenWithoutAccountRow() throws Exception {
        mockMvc.perform(post("/api/v1/battle/score")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(result -> {
                    int s = result.getResponse().getStatus();
                    org.junit.jupiter.api.Assertions.assertNotEquals(403, s,
                        "Admin should not be forbidden even without a DB Account row");
                });
    }

    @Test
    @WithMockUser(username = "emcee_user", roles = {"EMCEE"})
    void emcee_isForbiddenWhenNoMaxOrganiserForEvent() throws Exception {
        mockMvc.perform(post("/api/v1/battle/score")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "helper_user", roles = {"HELPER"})
    void helper_isForbiddenWhenNoMaxOrganiserForEvent() throws Exception {
        mockMvc.perform(post("/api/v1/battle/score")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isForbidden());
    }
}
