package com.example.BES.controllers;

import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.AddEventDto;
import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.AddScoringCriteriaDto;
import com.example.BES.dtos.GetScoringCriteriaDto;
import com.example.BES.dtos.UpdateScoringCriteriaDto;
import com.example.BES.dtos.GetJudgingModeDto;
import com.example.BES.dtos.UpdateJudgingModeDto;
import com.example.BES.dtos.UpdateAccessCodeDto;
import com.example.BES.dtos.VerifyAccessCodeDto;
// import com.example.BES.dtos.AddJudgesDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.AddParticipantToEventGenreDto;
import com.example.BES.dtos.AddWalkInDto;
import com.example.BES.dtos.GetEventDto;
import com.example.BES.dtos.GetEventGenreParticipantDto;
import com.example.BES.dtos.GetGenreDto;
import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.dtos.GetParticipantByEventDto;
import com.example.BES.dtos.GetParticipatnScoreDto;
import com.example.BES.dtos.GetUnverifiedParticipantDto;
import com.example.BES.dtos.UpdateParticipantGenreDto;
import com.example.BES.dtos.UpdateParticipantJudgeDto;
import com.example.BES.dtos.UpdateParticipantsScoreDto;
import com.example.BES.dtos.VerifyParticipantDto;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;
import com.example.BES.dtos.CreatePickupCrewDto;
import com.example.BES.dtos.GetPickupCrewDto;
import com.example.BES.services.AuditionFeedbackService;
import com.example.BES.services.EventGenreParticpantService;
import com.example.BES.services.PickupCrewService;
import com.example.BES.services.ScoringCriteriaService;
import com.example.BES.services.EventGenreService;
import com.example.BES.services.EventParticpantService;
import com.example.BES.services.EventService;
import com.example.BES.services.GenreService;
import com.example.BES.services.JudgeService;
import com.example.BES.services.ParticipantService;
import com.example.BES.services.RegistrationService;
import com.example.BES.services.ScoreService;
import com.example.BES.services.EmailTemplateService;
import com.example.BES.dtos.GetAuditionFeedbackDto;
import com.example.BES.dtos.GetEmailTemplateDto;
import com.example.BES.dtos.GetParticipantFeedbackDto;
import com.example.BES.dtos.SubmitAuditionFeedbackDto;
import com.example.BES.dtos.UpdateEmailTemplateDto;
import com.google.gson.Gson;
import com.google.zxing.WriterException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/event")
@Tag(name = "Event Management", description = "Endpoints for managing events, genres, participants, walk-ins, and scores")
public class EventController {
    private static final Logger log = LoggerFactory.getLogger(EventController.class);

    @Autowired
    EventService eventService;

    @Autowired
    ParticipantService participantService;

    @Autowired
    EventParticpantService eventParticipantService;

    @Autowired
    EventGenreService eventGenreService;

    @Autowired
    EventGenreParticpantService eventGenreParticipantService;

    @Autowired
    GenreService genreService;

    @Autowired
    RegistrationService registerService;

    @Autowired
    JudgeService judgeService;

    @Autowired
    ScoreService scoreService;

    @Autowired
    EmailTemplateService emailTemplateService;

    @Autowired
    AuditionFeedbackService feedbackService;

    @Autowired
    ScoringCriteriaService scoringCriteriaService;

    @Autowired
    PickupCrewService pickupCrewService;

    private static final Gson gson = new Gson();

    @Operation(summary = "Check Event Exists", description = "Returns true if an event with the given name exists")
    @GetMapping("/{eventName}")
    public ResponseEntity<Boolean> eventExistByName(@PathVariable String eventName) {
        AddEventDto event = eventService.findEventbyNameSerivce(eventName);
        return new ResponseEntity<>(event != null, HttpStatus.OK);
    }

    @Operation(summary = "Get All Events", description = "Returns a list of all events")
    @GetMapping("/events")
    public ResponseEntity<List<GetEventDto>> getAllEvents(Authentication authentication) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return new ResponseEntity<>(eventService.getAllEvents(isAdmin), HttpStatus.OK);
    }

    @Operation(summary = "Verify Event Access Code", description = "Checks if the provided code matches the event's access code")
    @PostMapping("/verify-access-code")
    public ResponseEntity<?> verifyAccessCode(@RequestBody VerifyAccessCodeDto dto) {
        try {
            boolean valid = eventService.verifyAccessCode(dto.eventId, dto.accessCode);
            return ResponseEntity.ok(java.util.Map.of("valid", valid));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("error", e.getReason()));
        }
    }

    @Operation(summary = "Get Judging Mode", description = "Returns the current judging mode (SOLO/PAIR) for an event")
    @GetMapping("/judging-mode/{eventName}")
    public ResponseEntity<GetJudgingModeDto> getJudgingMode(@PathVariable String eventName) {
        try {
            return new ResponseEntity<>(eventService.getJudgingMode(eventName), HttpStatus.OK);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return new ResponseEntity<>(null, e.getStatusCode());
        }
    }

    @Operation(summary = "Set Judging Mode", description = "Sets the judging mode (SOLO/PAIR) for an event (admin only)")
    @PostMapping("/judging-mode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> setJudgingMode(@RequestBody UpdateJudgingModeDto dto) {
        try {
            eventService.setJudgingMode(dto.eventName, dto.judgingMode);
            return ResponseEntity.ok(java.util.Map.of("message", "Judging mode updated"));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("error", e.getReason()));
        }
    }

    @Operation(summary = "Update Event Access Code", description = "Updates the access code for an event (admin only)")
    @PostMapping("/access-code")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateAccessCode(@RequestBody UpdateAccessCodeDto dto) {
        try {
            eventService.updateAccessCode(dto.eventId, dto.newCode);
            return ResponseEntity.ok(java.util.Map.of("message", "Access code updated"));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("error", e.getReason()));
        }
    }

    @Operation(summary = "Get All Genres", description = "Returns all available genres in the system")
    @GetMapping("/genre")
    public ResponseEntity<List<GetGenreDto>> getAllGenres() {
        return new ResponseEntity<>(genreService.getAllGenres(), HttpStatus.OK);
    }

    @Operation(summary = "Get Genres by Event", description = "Returns genres linked to a specific event")
    @GetMapping("/{eventName}/genres")
    public ResponseEntity<List<GetGenreDto>> getGenresByEvent(@PathVariable String eventName) {
        return new ResponseEntity<>(eventGenreService.getGenresByEventService(eventName), HttpStatus.OK);
    }

    @Operation(summary = "Update Event Genre Format", description = "Sets the battle format for a genre in a specific event (e.g. '2v2')")
    @PostMapping("/{eventName}/genres/{genreName}/format")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> updateEventGenreFormat(
            @PathVariable String eventName,
            @PathVariable String genreName,
            @RequestBody Map<String, String> body) {
        try {
            eventGenreService.updateEventGenreFormat(eventName, genreName, body.get("format"));
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Create Event", description = "Creates a new event")
    @PostMapping
    public ResponseEntity<String> createNewEvent(@RequestBody AddEventDto dto) {
        eventService.createEventService(dto);
        return new ResponseEntity<>(gson.toJson("Table created"), HttpStatus.CREATED);
    }

    @Operation(summary = "Assign Genre to Event", description = "Links a genre to an existing event")
    @PostMapping("/genre")
    public ResponseEntity<String> assignGenreToEvent(@RequestBody AddGenreToEventDto dto) {
        try {
            eventGenreService.addGenreToEventService(dto);
            return new ResponseEntity<>(gson.toJson(String.format("Created event with genres")), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    // @PostMapping("/judges")
    // public ResponseEntity<String> addJudge(@RequestBody AddJudgesDto dto){
    // try{
    // judgeService.addJudgesService(dto);
    // return new ResponseEntity<>(gson.toJson("Judges are added"),
    // HttpStatus.CREATED);
    // }catch(Exception e){
    // return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
    // }
    // }

    @Operation(summary = "Get All Judges", description = "Returns a list of all registered judges")
    @GetMapping("/judges")
    public ResponseEntity<List<GetJudgeDto>> getAllJudges() {
        try {
            return new ResponseEntity<>(judgeService.getAllJudges(), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Add Walk-in Participant", description = "Registers a new walk-in participant into an event")
    @PostMapping("/walkins/")
    public ResponseEntity<String> addWalkInToSystem(@RequestBody AddWalkInDto dto) {
        try {
            Participant p = participantService.addWalkInService(dto);
            EventParticipant ep = eventParticipantService.addNewWalkInInEventService(p, dto.eventName, dto.genre, dto.teamMembers, dto.teamName);
            EventGenreParticipant egp = eventGenreParticipantService.addWalkInToEventGenreParticipant(p, dto.genre, ep,
                    dto.judgeName);
            AddParticipantToEventGenreDto auditionDto = new AddParticipantToEventGenreDto();
            auditionDto.eventId = egp.getEvent().getEventId();
            auditionDto.genreId = egp.getGenre().getGenreId();
            auditionDto.participantId = egp.getParticipant().getParticipantId();
            eventGenreParticipantService.getAuditionNumViaQR(auditionDto);
            return new ResponseEntity<>(gson.toJson("Added walkin"), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(gson.toJson("error"), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Add List of Participants", description = "Adds participants to an event, typically read from a Google Sheet")
    @PostMapping("/participants/")
    public ResponseEntity<String> addParticipantsToSystem(@RequestBody AddParticipantToEventDto dto)
            throws IOException, MessagingException, WriterException {
        try {
            registerService.addParticipantToEvent(dto);
            return new ResponseEntity<>(gson.toJson("Participants list updated!"), HttpStatus.CREATED);
        } catch (NullPointerException e) {
            log.error("NPE in addParticipantsToSystem", e);
            return new ResponseEntity<>(
                    gson.toJson("The record is empty, please verify the payment in the google sheet"),
                    HttpStatus.NOT_FOUND);
        }
    }

    @Operation(summary = "Get Unverified Participants (DB)", description = "Returns participants with payment not yet verified, sourced from DB")
    @GetMapping("/{eventName}/unverified-participants")
    public ResponseEntity<List<GetUnverifiedParticipantDto>> getUnverifiedParticipantsDb(@PathVariable String eventName) {
        try {
            return new ResponseEntity<>(registerService.getUnverifiedParticipantsFromDb(eventName), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error fetching unverified participants", e);
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        }
    }

    @Operation(summary = "Verify and Email Participant", description = "Marks a participant as payment-verified and sends QR email")
    @PostMapping("/participants/verify-email")
    public ResponseEntity<String> verifyAndEmailParticipant(@RequestBody VerifyParticipantDto dto) {
        try {
            registerService.verifyAndEmail(dto.getParticipantId(), dto.getEventId());
            return new ResponseEntity<>(gson.toJson("Verified and email sent"), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error in verify-email", e);
            return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Verify and Email Batch", description = "Marks a batch of participants as payment-verified and sends QR emails")
    @PostMapping("/participants/verify-email-batch")
    public ResponseEntity<String> verifyAndEmailBatch(@RequestBody List<VerifyParticipantDto> list) {
        try {
            registerService.verifyAndEmailBatch(list);
            return new ResponseEntity<>(gson.toJson("Batch verified and emails sent"), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error in verify-email-batch", e);
            return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Get Participants by Event Genre", description = "Retrieves a list of participants for a specific event based on their genre")
    @GetMapping("/participants/{eventName}")
    public ResponseEntity<List<GetEventGenreParticipantDto>> getParticipantsFromEventGenre(
            @PathVariable String eventName) throws IOException, MessagingException, WriterException {
        try {
            List<GetEventGenreParticipantDto> results = eventGenreParticipantService
                    .getAllEventGenreParticipantByEventService(eventName);
            if (results.size() > 0) {
                return new ResponseEntity<>(results, HttpStatus.OK);
            }
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);

        } catch (NullPointerException e) {
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
    }

    @Operation(summary = "Get All Paid Participants", description = "Gets all verified/paid participants for an event regardless of genre")
    @GetMapping("/verified-participant/{eventName}")
    public ResponseEntity<List<GetParticipantByEventDto>> getAllVerifiedParticipant(@PathVariable String eventName) {
        List<GetParticipantByEventDto> res = eventParticipantService.getAllParticipantsByEvent(eventName);
        if (res == null) {
            return new ResponseEntity<>(res, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @Operation(summary = "Register Participant All Genres", description = "Scans one QR and assigns audition numbers for all genres the participant is enrolled in")
    @GetMapping("/register-participant/{participantId}/{eventId}")
    public ResponseEntity<String> registerParticipantAllGenres(
            @PathVariable Long participantId,
            @PathVariable Long eventId) {
        try {
            eventGenreParticipantService.getAllAuditionNumsViaQR(participantId, eventId);
            return new ResponseEntity<>("registered", HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>("Something is null", HttpStatus.BAD_REQUEST);
        }
    }

    /*
     * This is actually a POST method, and this link is send to the participants
     * When organiser scan the QR, it will give the participant audition number
     * based on genre
     * @deprecated Use /register-participant/{participantId}/{eventId} (single QR) instead
     */
    @Operation(summary = "Register Participant with Genre (Deprecated)", description = "Registers a participant and generates an audition number via QR scan for a specific genre. Deprecated: use the 2-param endpoint instead.")
    @GetMapping("/register-participant/{participantId}/{eventId}/{genreId}")
    public ResponseEntity<String> registerParticipantWithGenre(@PathVariable Long participantId,
            @PathVariable Long eventId, @PathVariable Long genreId) throws IOException {
        try {
            AddParticipantToEventGenreDto dto = new AddParticipantToEventGenreDto();
            dto.participantId = participantId;
            dto.eventId = eventId;
            dto.genreId = genreId;
            eventGenreParticipantService.getAuditionNumViaQR(dto);
            return new ResponseEntity<>("registered", HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>("Something is null", HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Update Participant Judge", description = "Assigns or updates a judge for a participant")
    @PostMapping("/participants-judge/")
    public ResponseEntity<String> updateParticipantJudge(@RequestBody UpdateParticipantJudgeDto dto) {
        try {
            // eventGenreParticipantService.addParticipantToEventGenreService(dto);
            eventGenreParticipantService.updateParticipantsJudgeService(dto);
            return new ResponseEntity<>(gson.toJson("All updated"), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(gson.toJson("Failed to update"), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Update Participant Score", description = "Updates or submits scores for participants")
    @PostMapping("/scores")
    public ResponseEntity<String> updateParticipantScore(@RequestBody UpdateParticipantsScoreDto dto) {
        try {
            // score service here
            scoreService.updateParticipantScoreService(dto);
            return new ResponseEntity<>(gson.toJson("Score updated!"), HttpStatus.ACCEPTED);
        } catch (Exception e) {
            return new ResponseEntity<>(gson.toJson("Failed to update score"), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Get Participant Scores", description = "Gets all scores for participants in a specific event")
    @GetMapping("/scores/{eventName}")
    public ResponseEntity<List<GetParticipatnScoreDto>> getParticipantScore(@PathVariable String eventName) {
        try {
            return new ResponseEntity<>(scoreService.getAllScore(eventName), HttpStatus.OK);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Get Email Template", description = "Returns the email template for the given event")
    @GetMapping("/{eventName}/email-template")
    public ResponseEntity<GetEmailTemplateDto> getEmailTemplate(@PathVariable String eventName) {
        GetEmailTemplateDto dto = emailTemplateService.getTemplateByEventName(eventName);
        if (dto == null) {
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @Operation(summary = "Update Email Template", description = "Updates the email template for the given event")
    @PostMapping("/{eventName}/email-template")
    public ResponseEntity<GetEmailTemplateDto> updateEmailTemplate(
            @PathVariable String eventName,
            @RequestBody UpdateEmailTemplateDto dto) {
        try {
            return new ResponseEntity<>(emailTemplateService.updateTemplate(eventName, dto), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Reset Email Template", description = "Regenerates the smart default email template based on current event genre formats")
    @PostMapping("/{eventName}/email-template/reset")
    public ResponseEntity<GetEmailTemplateDto> resetEmailTemplate(@PathVariable String eventName) {
        try {
            return new ResponseEntity<>(emailTemplateService.resetToSmartDefault(eventName), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Remove Participant Genre", description = "Removes a participant from a specific genre in an event")
    @DeleteMapping("/participant-genre/{participantId}/{eventId}/{genreId}")
    public ResponseEntity<Void> removeParticipantGenre(
            @PathVariable Long participantId,
            @PathVariable Long eventId,
            @PathVariable Long genreId) {
        try {
            eventGenreParticipantService.removeParticipantFromGenre(participantId, eventId, genreId);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Add Genre to Existing Participant", description = "Adds a new genre to an already-registered participant and assigns an audition number via WebSocket")
    @PostMapping("/participant-genre")
    public ResponseEntity<String> addGenreToParticipant(@RequestBody UpdateParticipantGenreDto dto) {
        try {
            eventGenreParticipantService.addGenreToExistingParticipant(dto.participantId, dto.eventId, dto.genreName);
            return new ResponseEntity<>(gson.toJson("Genre added"), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Submit Audition Feedback", description = "Saves or updates judge feedback tags and note for a participant")
    @PostMapping("/feedback")
    public ResponseEntity<?> submitFeedback(@RequestBody SubmitAuditionFeedbackDto dto) {
        feedbackService.submitFeedback(dto);
        return ResponseEntity.ok(Map.of("message", "feedback saved"));
    }

    @Operation(summary = "Get Audition Feedback", description = "Returns existing feedback tag IDs and note for a participant/judge combination")
    @GetMapping("/feedback")
    public ResponseEntity<GetAuditionFeedbackDto> getFeedback(
            @RequestParam String eventName,
            @RequestParam String genreName,
            @RequestParam String judgeName,
            @RequestParam Integer auditionNumber) {
        return ResponseEntity.ok(feedbackService.getFeedback(eventName, genreName, judgeName, auditionNumber));
    }

    @Operation(summary = "Get All Judge Feedback for Participant", description = "Returns feedback from all judges for a given participant in a specific event genre")
    @GetMapping("/feedback/participant")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<List<GetParticipantFeedbackDto>> getParticipantFeedback(
            @RequestParam String eventName,
            @RequestParam String genreName,
            @RequestParam String participantName) {
        return ResponseEntity.ok(feedbackService.getAllFeedbackForParticipant(eventName, genreName, participantName));
    }

    @Operation(summary = "Get Results Release Status", description = "Returns whether results have been released for the event")
    @GetMapping("/{eventName}/results-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> getResultsStatus(@PathVariable String eventName) {
        try {
            boolean released = eventService.isResultsReleased(eventName);
            return ResponseEntity.ok(Map.of("released", released));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        }
    }

    @Operation(summary = "Release or Retract Results", description = "Toggles whether results are visible on the public results portal")
    @PostMapping("/{eventName}/release-results")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> releaseResults(
            @PathVariable String eventName,
            @RequestBody Map<String, Boolean> body) {
        try {
            boolean released = Boolean.TRUE.equals(body.get("released"));
            eventService.releaseResults(eventName, released);
            return ResponseEntity.ok(Map.of("message", "Results release updated", "released", released));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        }
    }

    @Operation(summary = "Get Participant Reference Codes", description = "Returns a list of participant names and their reference codes for the event")
    @GetMapping("/{eventName}/participant-refs")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> getParticipantRefs(@PathVariable String eventName) {
        return ResponseEntity.ok(eventParticipantService.getParticipantRefs(eventName));
    }

    @Operation(summary = "Get Scoring Criteria", description = "Returns scoring criteria for an event and optional genre. Without strict=true, genre-specific criteria take priority and fall back to event-level.")
    @GetMapping("/{eventName}/criteria")
    public ResponseEntity<List<GetScoringCriteriaDto>> getScoringCriteria(
            @PathVariable String eventName,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false, defaultValue = "false") boolean strict) {
        if (strict) {
            return ResponseEntity.ok(scoringCriteriaService.getStrictCriteria(eventName, genre));
        }
        return ResponseEntity.ok(scoringCriteriaService.getCriteria(eventName, genre));
    }

    @Operation(summary = "Add Scoring Criterion", description = "Adds a scoring criterion to an event (admin/organiser only)")
    @PostMapping("/{eventName}/criteria")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> addScoringCriteria(
            @PathVariable String eventName,
            @RequestBody AddScoringCriteriaDto dto) {
        try {
            dto.eventName = eventName;
            return ResponseEntity.ok(scoringCriteriaService.addCriteria(dto));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        }
    }

    @Operation(summary = "Update Scoring Criterion", description = "Updates name and/or weight of a criterion (admin/organiser only)")
    @org.springframework.web.bind.annotation.PutMapping("/{eventName}/criteria/{criteriaId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> updateScoringCriteria(
            @PathVariable String eventName,
            @PathVariable Long criteriaId,
            @RequestBody UpdateScoringCriteriaDto dto) {
        try {
            return ResponseEntity.ok(scoringCriteriaService.updateCriteria(criteriaId, dto));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        }
    }

    @Operation(summary = "Delete Scoring Criterion", description = "Removes a scoring criterion by ID (admin/organiser only)")
    @DeleteMapping("/{eventName}/criteria/{criteriaId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> deleteScoringCriteria(
            @PathVariable String eventName,
            @PathVariable Long criteriaId) {
        scoringCriteriaService.removeCriteria(criteriaId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Delete All Criteria for Genre", description = "Removes all criteria for a specific genre (or event-level if no genre) (admin/organiser only)")
    @DeleteMapping("/{eventName}/criteria")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> deleteAllCriteriaForGenre(
            @PathVariable String eventName,
            @RequestParam(required = false) String genre) {
        scoringCriteriaService.deleteAllCriteria(eventName, genre);
        return ResponseEntity.noContent().build();
    }

    // ── Pickup Crew endpoints ──────────────────────────────────────────────────

    @Operation(summary = "Get Pickup Crews", description = "Returns all pickup crews for a given event and genre")
    @GetMapping("/crews/{eventName}/{genreName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<List<GetPickupCrewDto>> getPickupCrews(
            @PathVariable String eventName,
            @PathVariable String genreName) {
        return ResponseEntity.ok(pickupCrewService.getCrewsForEventGenre(eventName, genreName));
    }

    @Operation(summary = "Create Pickup Crew", description = "Forms a new pickup crew from solo participants")
    @PostMapping("/crews")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> createPickupCrew(@RequestBody CreatePickupCrewDto dto) {
        try {
            return ResponseEntity.ok(pickupCrewService.createCrew(dto));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Delete Pickup Crew", description = "Deletes a pickup crew by ID")
    @DeleteMapping("/crews/{crewId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> deletePickupCrew(@PathVariable Long crewId) {
        pickupCrewService.deleteCrew(crewId);
        return ResponseEntity.noContent().build();
    }
}