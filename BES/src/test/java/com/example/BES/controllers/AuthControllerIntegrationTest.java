package com.example.BES.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.dtos.LoginDto;
import com.example.BES.dtos.RedeemTokenDto;
import com.example.BES.models.Event;
import com.example.BES.models.Judge;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.JudgeRepo;
import com.example.BES.services.SessionTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepo eventRepo;

    @Autowired
    private JudgeRepo judgeRepo;

    @Autowired
    private SessionTokenService sessionTokenService;

    private Event testEvent;
    private Judge testJudge;

    @BeforeEach
    void setUp() {
        testEvent = new Event();
        testEvent.setEventName("Test Event");
        testEvent = eventRepo.save(testEvent);

        testJudge = new Judge();
        testJudge.setName("Test Judge");
        testJudge = judgeRepo.save(testJudge);

        judgeRepo.insertEventJudge(testEvent.getEventId(), testJudge.getJudgeId());
    }

    @Test
    public void testMeEndpointUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false));
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    public void testMeEndpointAuthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    public void testLoginSuccess() throws Exception {
        LoginDto loginDto = new LoginDto();
        loginDto.setUsername("admin");
        loginDto.setPassword("test-admin-password");

        mockMvc.perform(post("/api/v1/auth/login")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login Successfully"))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    public void testLoginFailure() throws Exception {
        LoginDto loginDto = new LoginDto();
        loginDto.setUsername("admin");
        loginDto.setPassword("wrong_password");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testLogout() throws Exception {
        LoginDto loginDto = new LoginDto();
        loginDto.setUsername("admin");
        loginDto.setPassword("test-admin-password");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();

        mockMvc.perform(post("/api/v1/auth/logout").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged Out "));
    }

    @Test
    public void testRedeemValidJudgeToken() throws Exception {
        String tokenId = sessionTokenService.generateToken("JUDGE", testEvent.getEventId(), testJudge.getJudgeId(), 7);

        RedeemTokenDto dto = new RedeemTokenDto();
        dto.setTokenId(tokenId);

        mockMvc.perform(post("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.role[0].authority").value("ROLE_JUDGE"))
                .andExpect(jsonPath("$.judgeId").value(testJudge.getJudgeId()))
                .andExpect(jsonPath("$.judgeName").value("Test Judge"))
                .andExpect(jsonPath("$.eventId").value(testEvent.getEventId()));
    }

    @Test
    public void testRedeemInvalidToken() throws Exception {
        RedeemTokenDto dto = new RedeemTokenDto();
        dto.setTokenId("non-existent-token-id");

        mockMvc.perform(post("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    public void testRedeemRevokedToken() throws Exception {
        String tokenId = sessionTokenService.generateToken("JUDGE", testEvent.getEventId(), testJudge.getJudgeId(), 7);
        sessionTokenService.revoke(tokenId);

        RedeemTokenDto dto = new RedeemTokenDto();
        dto.setTokenId(tokenId);

        mockMvc.perform(post("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Token has been revoked"));
    }

    @Test
    public void testMeWithSessionAttributes() throws Exception {
        String tokenId = sessionTokenService.generateToken("JUDGE", testEvent.getEventId(), testJudge.getJudgeId(), 7);

        RedeemTokenDto redeemDto = new RedeemTokenDto();
        redeemDto.setTokenId(tokenId);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(redeemDto)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession();

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.eventId").value(testEvent.getEventId()))
                .andExpect(jsonPath("$.judgeId").value(testJudge.getJudgeId()))
                .andExpect(jsonPath("$.judgeName").value("Test Judge"));
    }

    @Test
    public void testGenerateTokenAuthenticatedAdmin() throws Exception {
        LoginDto loginDto = new LoginDto();
        loginDto.setUsername("admin");
        loginDto.setPassword("test-admin-password");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        mockMvc.perform(post("/api/v1/auth/generate-token")
                .session(session)
                .param("role", "JUDGE")
                .param("eventId", String.valueOf(testEvent.getEventId()))
                .param("judgeId", String.valueOf(testJudge.getJudgeId()))
                .param("expiresInDays", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenId").exists())
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    public void testOrganiserSeesOnlyAssignedEvents() throws Exception {
        Event unassignedEvent = new Event();
        unassignedEvent.setEventName("Unassigned Event");
        eventRepo.save(unassignedEvent);

        LoginDto loginDto = new LoginDto();
        loginDto.setUsername("organiser");
        loginDto.setPassword("test-organiser-password");

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                .secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();

        mockMvc.perform(get("/api/v1/event/events").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
