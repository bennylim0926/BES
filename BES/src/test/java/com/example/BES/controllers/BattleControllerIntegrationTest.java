package com.example.BES.controllers;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.BES.models.Judge;
import com.example.BES.services.BattleService;
import com.example.BES.services.JudgeService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class BattleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BattleService battleService;

    @MockBean
    private JudgeService judgeService;

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    private final Path uploadDir = Paths.get("uploads");

    @BeforeEach
    public void setup() throws Exception {
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
    }

    @AfterEach
    public void cleanup() throws Exception {
        if (Files.exists(uploadDir)) {
            Files.list(uploadDir).forEach(f -> {
                try { Files.deleteIfExists(f); } catch (Exception ignored) {}
            });
        }
    }

    @Test
    @WithMockUser
    public void testGetAllModes() throws Exception {
        mockMvc.perform(get("/api/v1/battle/modes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modes").isArray())
                .andExpect(jsonPath("$.modes[0]").value("Top32"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testSetAndGetSelectedMode() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("mode", "Top16"));
        mockMvc.perform(post("/api/v1/battle/battle-mode")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mode set successfully"))
                .andExpect(jsonPath("$.mode").value("Top16"));

        mockMvc.perform(get("/api/v1/battle/battle-mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("Top16"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testSetAndGetBattlePair() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "leftBattler", "Alice",
                "rightBattler", "Bob"));
        mockMvc.perform(post("/api/v1/battle/battle-pair")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("successfully set the battle pair"))
                .andExpect(jsonPath("$.left").value("Alice"))
                .andExpect(jsonPath("$.right").value("Bob"));

        mockMvc.perform(get("/api/v1/battle/battle-pair"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.left").value("Alice"))
                .andExpect(jsonPath("$.right").value("Bob"))
                .andExpect(jsonPath("$.leftScore").value(0))
                .andExpect(jsonPath("$.rightScore").value(0));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testJudgeInteractionsAndScore() throws Exception {
        Judge judge1 = new Judge(1L, "Judge One", null);
        Judge judge2 = new Judge(2L, "Judge Two", null);

        when(judgeService.getJudgeById(1L)).thenReturn(judge1);
        when(judgeService.getJudgeById(2L)).thenReturn(judge2);
        when(judgeService.getJudgeById(99L)).thenReturn(null);

        // Fail to add non-existent
        mockMvc.perform(post("/api/v1/battle/judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 99))))
                .andExpect(status().isNotFound());

        // Add proper judge
        mockMvc.perform(post("/api/v1/battle/judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Added judge"));

        // Set vote
        mockMvc.perform(post("/api/v1/battle/vote")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 1, "vote", 0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Voted left"));

        // Get Judges
        mockMvc.perform(get("/api/v1/battle/judges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judges").isArray());

        // Tally Score (Left wins since vote is 0)
        mockMvc.perform(post("/api/v1/battle/score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winner").value(0));

        // Delete judge
        mockMvc.perform(delete("/api/v1/battle/judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 1))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testHandleUploadAndImages() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-image-content".getBytes());

        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/battle/upload")
                .file(file))
                .andExpect(status().isOk())
                .andReturn();

        String savedFilename = uploadResult.getResponse().getContentAsString().replace("\"", "");

        mockMvc.perform(get("/api/v1/battle/uploads/" + savedFilename))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/battle/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(delete("/api/v1/battle/image")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", savedFilename))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("File deleted successfully"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testSetAndGetSmokeList() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "battlers", List.of(
                        Map.of("name", "Alice", "score", 0),
                        Map.of("name", "Bob", "score", 0))));

        mockMvc.perform(post("/api/v1/battle/smoke")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("List updated"));

        mockMvc.perform(get("/api/v1/battle/smoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list").isArray())
                .andExpect(jsonPath("$.list[0].name").value("Alice"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testSetBattlePairWithIsFinalTrue_getPairReturnsIsFinalTrue() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "leftBattler", "W9",
                "rightBattler", "W10",
                "isFinal", true));

        mockMvc.perform(post("/api/v1/battle/battle-pair")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/battle/battle-pair"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFinal").value(true));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testSetBattlePairWithoutIsFinal_defaultsFalse() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "leftBattler", "Alice",
                "rightBattler", "Bob"));

        mockMvc.perform(post("/api/v1/battle/battle-pair")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/battle/battle-pair"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isFinal").value(false));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testFinalTie_isBlockedWith409() throws Exception {
        Judge j1 = new Judge(10L, "FinalJ1", null);
        Judge j2 = new Judge(11L, "FinalJ2", null);
        when(judgeService.getJudgeById(10L)).thenReturn(j1);
        when(judgeService.getJudgeById(11L)).thenReturn(j2);

        mockMvc.perform(post("/api/v1/battle/judge").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 10)))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/battle/judge").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 11)))).andExpect(status().isOk());

        // j1 votes left (0), j2 votes right (1) → tie
        mockMvc.perform(post("/api/v1/battle/vote").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 10, "vote", 0)))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/battle/vote").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 11, "vote", 1)))).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/battle/score")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isFinal\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.tie").value(true));

        // Cleanup
        mockMvc.perform(delete("/api/v1/battle/judge").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 10)))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/battle/judge").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 11)))).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testUpdateJudgeWeightage_setsWeightageAndReturnsOk() throws Exception {
        Judge j = new Judge();
        j.setJudgeId(42L);
        j.setName("WeightTest");
        when(judgeService.getJudgeById(42L)).thenReturn(j);

        mockMvc.perform(post("/api/v1/battle/judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 42))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/battle/judge/weightage")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 42, "weightage", 3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Weightage updated"));

        mockMvc.perform(get("/api/v1/battle/judges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.judges[?(@.id == 42)].weightage").value(3));

        mockMvc.perform(delete("/api/v1/battle/judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 42))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testRevote_resetsJudgeVotesToMinusOne() throws Exception {
        Judge j = new Judge(12L, "RevoteJ", null);
        when(judgeService.getJudgeById(12L)).thenReturn(j);

        mockMvc.perform(post("/api/v1/battle/judge").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 12)))).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/battle/vote").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 12, "vote", 0)))).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/battle/revote"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Judge votes reset"));

        // Cleanup
        mockMvc.perform(delete("/api/v1/battle/judge").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("id", 12)))).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    public void testGetOverlayConfig_returnsValidStructure() throws Exception {
        mockMvc.perform(get("/api/v1/battle/overlay-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showImages").exists())
                .andExpect(jsonPath("$.leftColor").exists())
                .andExpect(jsonPath("$.rightColor").exists());
    }

    @Test
    @WithMockUser(roles = "ORGANISER")
    public void testSetOverlayConfig_updatesAndReturns() throws Exception {
        String body = "{\"showImages\":false,\"leftColor\":\"#ff0000\",\"rightColor\":\"#0000ff\"}";

        mockMvc.perform(post("/api/v1/battle/overlay-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.showImages").value(false))
                .andExpect(jsonPath("$.leftColor").value("#ff0000"))
                .andExpect(jsonPath("$.rightColor").value("#0000ff"));
    }

    @Test
    @WithMockUser(roles = "ORGANISER")
    public void testSetOverlayConfig_rejectsInvalidHexColor() throws Exception {
        String body = "{\"showImages\":true,\"leftColor\":\"notacolor\",\"rightColor\":\"#2563eb\"}";

        mockMvc.perform(post("/api/v1/battle/overlay-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testSetOverlayConfig_requiresAuth() throws Exception {
        String body = "{\"showImages\":true,\"leftColor\":\"#dc2626\",\"rightColor\":\"#2563eb\"}";

        mockMvc.perform(post("/api/v1/battle/overlay-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testSetAndGetActiveGenre() throws Exception {
        String json = objectMapper.writeValueAsString(
            Map.of("eventName", "TestEvent", "genreName", "Breaking Top 16")
        );
        mockMvc.perform(post("/api/v1/battle/active-genre")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Active genre set"));

        mockMvc.perform(get("/api/v1/battle/active-genre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventName").value("TestEvent"))
                .andExpect(jsonPath("$.genreName").value("Breaking Top 16"));
    }

    @Test
    @WithMockUser
    public void testGetBattleStateEmptyWhenNoActiveGenre() throws Exception {
        mockMvc.perform(get("/api/v1/battle/state"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testGetBattleStateAfterSettingActiveGenre() throws Exception {
        String json = objectMapper.writeValueAsString(
            Map.of("eventName", "EventA", "genreName", "Popping")
        );
        mockMvc.perform(post("/api/v1/battle/active-genre")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/battle/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventName").value("EventA"))
                .andExpect(jsonPath("$.genreName").value("Popping"))
                .andExpect(jsonPath("$.battlePhase").exists());
    }

    // ─────────────────────────────────────────────────────────
    // Emcee Auth Tests
    // ─────────────────────────────────────────────────────────

    // === Operator-tier: Emcee SHOULD have access ===

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetBattleState() throws Exception {
        mockMvc.perform(get("/api/v1/battle/state"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetBattlePhase() throws Exception {
        mockMvc.perform(get("/api/v1/battle/phase"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetBattleJudges() throws Exception {
        mockMvc.perform(get("/api/v1/battle/judges"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetBracketState() throws Exception {
        mockMvc.perform(get("/api/v1/battle/bracket"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetOverlayConfig() throws Exception {
        mockMvc.perform(get("/api/v1/battle/overlay-config"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetActiveGenre() throws Exception {
        mockMvc.perform(get("/api/v1/battle/active-genre"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_canGetChampions() throws Exception {
        mockMvc.perform(get("/api/v1/battle/champions")
                .param("event", "test-event"))
            .andExpect(status().isOk());
    }

    // === Config-tier: Emcee should NOT have access ===

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_cannotSetBattlePair() throws Exception {
        mockMvc.perform(post("/api/v1/battle/battle-pair")
                .contentType("application/json")
                .content("{\"leftBattler\":\"A\",\"rightBattler\":\"B\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_cannotSetBracket() throws Exception {
        mockMvc.perform(post("/api/v1/battle/bracket")
                .contentType("application/json")
                .content("{\"topSize\":16,\"rounds\":{}}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_cannotSetOverlayConfig() throws Exception {
        mockMvc.perform(post("/api/v1/battle/overlay-config")
                .contentType("application/json")
                .content("{\"leftColor\":\"#ff0000\",\"rightColor\":\"#0000ff\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_cannotAddJudge() throws Exception {
        mockMvc.perform(post("/api/v1/battle/judge")
                .contentType("application/json")
                .content("{\"name\":\"Judge A\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMCEE")
    public void emcee_cannotSetSmokeList() throws Exception {
        mockMvc.perform(post("/api/v1/battle/smoke")
                .contentType("application/json")
                .content("{\"battlers\":[]}"))
            .andExpect(status().isForbidden());
    }
}
