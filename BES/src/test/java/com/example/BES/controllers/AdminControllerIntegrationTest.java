package com.example.BES.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.dtos.admin.DeleteJudgeDto;
import com.example.BES.dtos.admin.DeleteScoreByEventDto;
import com.example.BES.dtos.admin.UpdateJudgeDto;
import com.example.BES.models.Judge;
import com.example.BES.services.JudgeService;
import com.example.BES.services.ScoreService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JudgeService judgeService;

    @MockBean
    private ScoreService scoreService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testAddJudge_Success() throws Exception {
        GetJudgeDto responseDto = new GetJudgeDto();
        responseDto.judgeId = 1L;
        responseDto.judgeName = "Judge Dredd";

        when(judgeService.getAllJudges()).thenReturn(List.of(responseDto));

        String json = objectMapper.writeValueAsString(Map.of("judgeName", "Judge Dredd"));

        mockMvc.perform(post("/api/v1/admin/judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].judgeName").value("Judge Dredd"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUpdateJudge_Success() throws Exception {
        Judge updatedJudge = new Judge(1L, "Judge Judy", null);

        when(judgeService.updateJudgeService(any(UpdateJudgeDto.class))).thenReturn(updatedJudge);

        String json = objectMapper.writeValueAsString(Map.of(
                "id", 1,
                "newName", "Judge Judy"));

        mockMvc.perform(post("/api/v1/admin/update-judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Judge updated successfully"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.judge").value("Judge Judy"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUpdateJudge_NotFound() throws Exception {
        when(judgeService.updateJudgeService(any(UpdateJudgeDto.class))).thenReturn(null);

        String json = objectMapper.writeValueAsString(Map.of(
                "id", 99,
                "newName", "Unknown"
        ));

        mockMvc.perform(post("/api/v1/admin/update-judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Judge NotFound"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDeleteJudge_Success() throws Exception {
        when(judgeService.deleteJudgeService(any(DeleteJudgeDto.class))).thenReturn("Judge Judy");

        String json = objectMapper.writeValueAsString(Map.of("id", 1));

        mockMvc.perform(delete("/api/v1/admin/judge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("deleted"))
                .andExpect(jsonPath("$.judge").value("Judge Judy"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDeleteScoreByEvent_Success() throws Exception {
        when(scoreService.deleteScoreByEventService(any(DeleteScoreByEventDto.class))).thenReturn(15);

        String json = objectMapper.writeValueAsString(Map.of("event_id", 1));

        mockMvc.perform(delete("/api/v1/admin/score")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("score deleted"))
                .andExpect(jsonPath("$.deleted").value("15"));
    }
}
