package com.example.BES.controllers;

import static org.mockito.ArgumentMatchers.any;
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
        Path testFile = uploadDir.resolve("test-image.jpg");
        if (Files.exists(testFile)) {
            Files.delete(testFile);
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
    @WithMockUser
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
    @WithMockUser
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
    @WithMockUser
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
    @WithMockUser
    public void testHandleUploadAndImages() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test-image.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "fake-image-content".getBytes());

        mockMvc.perform(multipart("/api/v1/battle/upload")
                .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("test-image.jpg"));

        mockMvc.perform(get("/api/v1/battle/uploads/test-image.jpg"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/battle/images"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(delete("/api/v1/battle/image")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "test-image.jpg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("File deleted successfully"));
    }

    @Test
    @WithMockUser
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
}
