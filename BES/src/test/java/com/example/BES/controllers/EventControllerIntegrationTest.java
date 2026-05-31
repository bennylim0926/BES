package com.example.BES.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;
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

import com.example.BES.dtos.AddEventDto;
import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.AddParticipantToEventGenreDto;
import com.example.BES.dtos.ImportResultDto;
import com.example.BES.dtos.GetCheckinListDto;
import com.example.BES.dtos.GetEventDto;
import com.example.BES.dtos.GetGenreDto;
import com.example.BES.dtos.GetParticipantByEventDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Genre;
import com.example.BES.models.Participant;
import com.example.BES.services.EventGenreParticpantService;
import com.example.BES.services.EventGenreService;
import com.example.BES.services.EventParticpantService;
import com.example.BES.services.EventService;
import com.example.BES.services.GenreService;
import com.example.BES.services.JudgeService;
import com.example.BES.services.ParticipantService;
import com.example.BES.services.RegistrationService;
import com.example.BES.services.ScoreService;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class EventControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;
    @MockBean
    private ParticipantService participantService;
    @MockBean
    private EventParticpantService eventParticipantService;
    @MockBean
    private EventGenreService eventGenreService;
    @MockBean
    private EventGenreParticpantService eventGenreParticipantService;
    @MockBean
    private GenreService genreService;
    @MockBean
    private RegistrationService registerService;
    @MockBean
    private JudgeService judgeService;
    @MockBean
    private ScoreService scoreService;

    @Test
    @WithMockUser
    public void testEventExistByName() throws Exception {
        when(eventService.findEventbyNameSerivce("Summer")).thenReturn(new AddEventDto());

        mockMvc.perform(get("/api/v1/event/Summer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(true));
    }

    @Test
    @WithMockUser
    public void testGetAllEvents() throws Exception {
        GetEventDto dto = new GetEventDto();
        dto.setId(1L);
        dto.setName("Mock Event");

        when(eventService.getAllEvents(false)).thenReturn(List.of(dto));
        when(eventService.getAllEvents(true)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/event/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Mock Event"));
    }

    @Test
    @WithMockUser
    public void testCreateNewEvent() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("eventName", "New Event"));

        mockMvc.perform(post("/api/v1/event")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value("Table created"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testAssignGenreToEvent() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("eventName", "Test Event", "divisions", List.of(Map.of("name", "Hip Hop"))));

        doNothing().when(eventGenreService).addGenreToEventService(any(AddGenreToEventDto.class));

        mockMvc.perform(post("/api/v1/event/genre")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value("Created event with genres"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testWalkin() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of(
                "name", "Mike",
                "eventName", "Event1",
                "genre", "Pop",
                "judgeName", "Judge A"));

        Participant p = new Participant();
        p.setParticipantId(1L);
        EventParticipant ep = new EventParticipant();
        EventGenreParticipant egp = new EventGenreParticipant();
        Event e = new Event();
        e.setEventId(1L);
        egp.setEvent(e);
        EventGenre eventGenre = new EventGenre();
        eventGenre.setId(1L);
        egp.setEventGenre(eventGenre);
        egp.setParticipant(p);

        when(participantService.addWalkInService(any())).thenReturn(p);
        when(eventParticipantService.addNewWalkInInEventService(any(), any())).thenReturn(ep);
        when(eventGenreParticipantService.addWalkInToEventGenreParticipant(any(), any(), any(), any(), any(), any(), any())).thenReturn(egp);

        mockMvc.perform(post("/api/v1/event/walkins/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value("Added walkin"));
    }

    @Test
    @WithMockUser
    public void testAddParticipantsList() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of());

        when(registerService.addParticipantToEvent(any(AddParticipantToEventDto.class)))
            .thenReturn(new ImportResultDto());

        mockMvc.perform(post("/api/v1/event/participants/")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    public void testRegisterParticipantAllGenres() throws Exception {
        doNothing().when(eventGenreParticipantService).getAllAuditionNumsViaQR(1L, 2L);

        mockMvc.perform(get("/api/v1/event/register-participant/1/2"))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$").value("registered"));
    }

    @Test
    @WithMockUser
    public void testRegisterParticipantWithGenre() throws Exception {
        doNothing().when(eventGenreParticipantService).getAuditionNumViaQR(any(AddParticipantToEventGenreDto.class));

        mockMvc.perform(get("/api/v1/event/register-participant/1/2/3"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").value("registered"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testUpdateParticipantScore() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("participantId", 1, "eventId", 2, "score", 50));

        mockMvc.perform(post("/api/v1/event/scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$").value("Score updated!"));
    }

    @Test
    @WithMockUser
    public void testGetAllVerifiedParticipant() throws Exception {
        when(eventParticipantService.getAllParticipantsByEvent(any())).thenReturn(List.of(new GetParticipantByEventDto()));
        mockMvc.perform(get("/api/v1/event/verified-participant/Summer"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    public void testGetParticipantsFromEventGenre() throws Exception {
        when(eventGenreParticipantService.getAllEventGenreParticipantByEventService(any())).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/api/v1/event/participants/Summer"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCheckinList_returnsOk() throws Exception {
        when(registerService.getCheckinList("TestEvent")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/event/TestEvent/checkin-list"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void verifyPayment_returnsOk() throws Exception {
        doNothing().when(registerService).verifyPayment(anyLong(), anyLong());

        mockMvc.perform(post("/api/v1/event/participants/verify-payment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"participantId\":1,\"eventId\":1}"))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testAddDivision() throws Exception {
        String json = objectMapper.writeValueAsString(Map.of("name", "New Division", "format", "1v1", "genreId", 1));

        doNothing().when(eventGenreService).addGenreToEventService(any(AddGenreToEventDto.class));

        mockMvc.perform(post("/api/v1/event/TestEvent/divisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$").value("Division added"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testRenameDivision() throws Exception {
        doNothing().when(eventGenreService).renameDivision(anyLong(), anyString());

        mockMvc.perform(patch("/api/v1/event/TestEvent/divisions/1/name")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Name\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value("Division renamed"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testUpdateAliases() throws Exception {
        doNothing().when(eventGenreService).updateAliases(anyLong(), anyString());

        mockMvc.perform(patch("/api/v1/event/TestEvent/divisions/1/aliases")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aliases\":\"hip-hop,popping\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value("Aliases updated"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    void testDeleteDivision() throws Exception {
        doNothing().when(eventGenreService).deleteDivision(anyLong());

        mockMvc.perform(delete("/api/v1/event/TestEvent/divisions/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value("Division deleted"));
    }
}
