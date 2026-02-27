package com.example.BES.controllers;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.example.BES.dtos.GoogleDriveFileDto;
import com.example.BES.dtos.GoogleDriveFolderDto;
import com.example.BES.dtos.GoogleSheetFileDto;
import com.example.BES.services.GoogleDriveFileService;
import com.example.BES.services.GoogleDriveFolderService;
import com.example.BES.services.GoogleSheetService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GoogleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GoogleDriveFileService googleDriveFileService;

    @MockBean
    private GoogleDriveFolderService googleDriveFolderService;

    @MockBean
    private GoogleSheetService googleSheetService;

    @Test
    @WithMockUser
    public void testGetFilesInFolder() throws Exception {
        GoogleDriveFileDto fileDto = new GoogleDriveFileDto();
        fileDto.setFileId("file123");
        fileDto.setFileName("Test Sheet");

        when(googleDriveFileService.findAllSheetsInFolder(anyString())).thenReturn(List.of(fileDto));

        mockMvc.perform(get("/api/v1/files/folder123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].fileId").value("file123"));
    }

    @Test
    @WithMockUser
    public void testGetFolders() throws Exception {
        GoogleDriveFolderDto folderDto = new GoogleDriveFolderDto();
        folderDto.setFolderID("folder123");
        folderDto.setFolderName("Test Folder");

        when(googleDriveFolderService.findAll()).thenReturn(List.of(folderDto));

        mockMvc.perform(get("/api/v1/folders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].folderName").value("Test Folder"));
    }

    @Test
    @WithMockUser
    public void testGetParticipantsBreakdown() throws Exception {
        GoogleSheetFileDto sheetDto = new GoogleSheetFileDto();
        sheetDto.setOpen(20);
        sheetDto.setPopping(15);

        when(googleSheetService.getParticipantsBreakDown(anyString())).thenReturn(sheetDto);

        mockMvc.perform(get("/api/v1/sheets/participants/breakdown/sheet123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(20));
    }

    @Test
    @WithMockUser
    public void testGetSheetSize() throws Exception {
        when(googleSheetService.getSheetSizeService(anyString())).thenReturn(50);

        mockMvc.perform(get("/api/v1/sheets/participants/size/sheet123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(50));
    }

    @Test
    @WithMockUser
    public void testInsertPaymentColumn() throws Exception {
        doNothing().when(googleSheetService).insertPaymentColumn(anyString());

        String json = objectMapper.writeValueAsString(Map.of("fileId", "sheet123"));

        mockMvc.perform(post("/api/v1/sheets/payment-status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());
    }
}
