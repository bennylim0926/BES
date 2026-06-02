package com.example.BES.controllers;
import jakarta.validation.Valid;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.AddDivisionDto;
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
import com.example.BES.dtos.AddJudgeDto;
import com.example.BES.dtos.AddParticipantToEventDto;
import com.example.BES.dtos.AddParticipantToEventGenreDto;
import com.example.BES.dtos.AddWalkInDto;
import com.example.BES.dtos.GetEventDto;
import com.example.BES.dtos.GetEventGenreParticipantDto;
import com.example.BES.dtos.GetEventDivisionDto;
import com.example.BES.dtos.GetGenreDto;
import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.dtos.GetParticipantByEventDto;
import com.example.BES.dtos.GetParticipatnScoreDto;
import com.example.BES.dtos.GetUnverifiedParticipantDto;
import com.example.BES.dtos.UpdateParticipantGenreDto;
import com.example.BES.dtos.UpdateParticipantJudgeDto;
import com.example.BES.dtos.UpdateParticipantsScoreDto;
import com.example.BES.dtos.VerifyParticipantDto;
import com.example.BES.dtos.AddBattleGuestDto;
import com.example.BES.dtos.GetBattleGuestDto;
import com.example.BES.dtos.GetCheckinListDto;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;
import com.example.BES.dtos.CreatePickupCrewDto;
import com.example.BES.dtos.GetPickupCrewDto;
import com.example.BES.services.AuditionFeedbackService;
import com.example.BES.services.BattleGuestService;
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
import com.example.BES.dtos.ImportResultDto;
import com.example.BES.dtos.GetEmailTemplateDto;
import com.example.BES.dtos.GetParticipantFeedbackDto;
import com.example.BES.dtos.SubmitAuditionFeedbackDto;
import com.example.BES.dtos.UpdateEmailTemplateDto;
import com.example.BES.dtos.CheckinPreviewDto;
import com.google.gson.Gson;
import com.google.zxing.WriterException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

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

    @Autowired
    BattleGuestService battleGuestService;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

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
    public ResponseEntity<?> verifyAccessCode(@Valid @RequestBody VerifyAccessCodeDto dto) {
        try {
            boolean valid = eventService.verifyAccessCode(dto.eventId, dto.accessCode);
            return ResponseEntity.ok(java.util.Map.of("valid", valid));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(java.util.Map.of("error", e.getReason()));
        }
    }

    @Operation(summary = "Get Judging Mode", description = "Returns the current judging mode (SOLO/PAIR) for an event")
    @GetMapping("/judging-mode/{eventName}")
    public ResponseEntity<?> getJudgingMode(@PathVariable String eventName) {
        try {
            return new ResponseEntity<>(eventService.getJudgingMode(eventName), HttpStatus.OK);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
        }
    }

    @Operation(summary = "Set Judging Mode", description = "Sets the judging mode (SOLO/PAIR) for an event (admin only)")
    @PostMapping("/judging-mode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> setJudgingMode(@Valid @RequestBody UpdateJudgingModeDto dto) {
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
    public ResponseEntity<?> updateAccessCode(@Valid @RequestBody UpdateAccessCodeDto dto) {
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

    @Operation(summary = "Get Genres by Event", description = "Returns divisions linked to a specific event")
    @GetMapping("/{eventName}/genres")
    public ResponseEntity<List<GetEventDivisionDto>> getGenresByEvent(@PathVariable String eventName) {
        return new ResponseEntity<>(eventGenreService.getGenresByEventService(eventName), HttpStatus.OK);
    }

    @Operation(summary = "Update Event Genre Format", description = "Sets the battle format for a genre in a specific event (e.g. '2v2')")
    @PostMapping("/{eventName}/genres/{eventGenreId}/format")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> updateEventGenreFormat(
            @PathVariable String eventName,
            @PathVariable Long eventGenreId,
            @Valid @RequestBody Map<String, String> body) {
        try {
            eventGenreService.updateEventGenreFormat(eventGenreId, body.get("format"));
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Create Event", description = "Creates a new event")
    @PostMapping
    public ResponseEntity<String> createNewEvent(@Valid @RequestBody AddEventDto dto) {
        eventService.createEventService(dto);
        return new ResponseEntity<>(gson.toJson("Table created"), HttpStatus.CREATED);
    }

    @Operation(summary = "Assign Genre to Event", description = "Links a genre to an existing event")
    @PostMapping("/genre")
    public ResponseEntity<String> assignGenreToEvent(@Valid @RequestBody AddGenreToEventDto dto) {
        try {
            eventGenreService.addGenreToEventService(dto);
            return new ResponseEntity<>(gson.toJson(String.format("Created event with genres")), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    // @PostMapping("/judges")
    // public ResponseEntity<String> addJudge(@Valid @RequestBody AddJudgesDto dto){
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
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Get Judges by Event", description = "Returns judges linked to a specific event")
    @GetMapping("/{eventName}/judges")
    public ResponseEntity<List<GetJudgeDto>> getJudgesByEvent(@PathVariable String eventName) {
        return ResponseEntity.ok(judgeService.getJudgesByEvent(eventName));
    }

    @Operation(summary = "Add Judge to Event", description = "Creates a new judge and links them to an event")
    @PostMapping("/{eventName}/judge")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<List<GetJudgeDto>> addJudgeToEvent(@PathVariable String eventName, @Valid @RequestBody AddJudgeDto dto) {
        judgeService.addJudgeToEvent(eventName, dto.judgeName);
        return ResponseEntity.ok(judgeService.getJudgesByEvent(eventName));
    }

    @Operation(summary = "Remove Judge from Event", description = "Removes a judge from an event")
    @DeleteMapping("/{eventName}/judge/{judgeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<List<GetJudgeDto>> removeJudgeFromEvent(@PathVariable String eventName, @PathVariable Long judgeId) {
        judgeService.removeJudgeFromEvent(eventName, judgeId);
        return ResponseEntity.ok(judgeService.getJudgesByEvent(eventName));
    }

    // ── Per-division judge endpoints ─────────────────────────────────────

    @Operation(summary = "Get Judges by Division", description = "Returns judges assigned to a specific division")
    @GetMapping("/{eventName}/divisions/{divisionId}/judges")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'JUDGE', 'EMCEE')")
    public ResponseEntity<List<GetJudgeDto>> getJudgesByDivision(@PathVariable String eventName, @PathVariable Long divisionId) {
        return ResponseEntity.ok(judgeService.getJudgesByDivision(divisionId));
    }

    @Operation(summary = "Add Judge to Division", description = "Creates a new judge and links them to a division")
    @PostMapping("/{eventName}/divisions/{divisionId}/judge")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<List<GetJudgeDto>> addJudgeToDivision(@PathVariable String eventName, @PathVariable Long divisionId, @Valid @RequestBody AddJudgeDto dto) {
        return ResponseEntity.ok(judgeService.addJudgeToDivision(divisionId, dto.judgeName));
    }

    @Operation(summary = "Remove Judge from Division", description = "Removes a judge from a division")
    @DeleteMapping("/{eventName}/divisions/{divisionId}/judge/{judgeId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<List<GetJudgeDto>> removeJudgeFromDivision(@PathVariable String eventName, @PathVariable Long divisionId, @PathVariable Long judgeId) {
        return ResponseEntity.ok(judgeService.removeJudgeFromDivision(divisionId, judgeId));
    }

    @Operation(summary = "Add Walk-in Participant", description = "Registers a new walk-in participant into an event")
    @PostMapping("/walkins/")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<String> addWalkInToSystem(@Valid @RequestBody AddWalkInDto dto) {
        try {
            Participant p = participantService.addWalkInService(dto);
            EventParticipant ep = eventParticipantService.addNewWalkInInEventService(p, dto.eventName);
            eventGenreParticipantService.addWalkInToEventGenreParticipant(
                p, dto.genre, ep, dto.judgeName, dto.entryMode, dto.teamName, dto.teamMembers);
            Map<String, Object> walkinMsg = new java.util.HashMap<>();
            walkinMsg.put("eventName", dto.eventName);
            messagingTemplate.convertAndSend("/topic/walkin/", walkinMsg);
            return new ResponseEntity<>(gson.toJson("Added walkin"), HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error adding walk-in", e);
            return new ResponseEntity<>(gson.toJson("Error adding participant"), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Add List of Participants", description = "Adds participants to an event, typically read from a Google Sheet")
    @PostMapping("/participants/")
    public ResponseEntity<?> addParticipantsToSystem(@Valid @RequestBody AddParticipantToEventDto dto)
            throws IOException, MessagingException, WriterException {
        try {
            ImportResultDto result = registerService.addParticipantToEvent(dto);
            return new ResponseEntity<>(result, HttpStatus.OK);
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

    @Operation(summary = "Get Check-in List", description = "Returns all participants for the event with their genre audition status")
    @GetMapping("/{eventName}/checkin-list")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<List<GetCheckinListDto>> getCheckinList(@PathVariable String eventName) {
        try {
            return new ResponseEntity<>(registerService.getCheckinList(eventName), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error fetching checkin list", e);
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
        }
    }

    @Operation(summary = "Verify Payment", description = "Marks a participant as payment-verified")
    @PostMapping("/participants/verify-payment")
    public ResponseEntity<String> verifyPayment(@Valid @RequestBody VerifyParticipantDto dto) {
        try {
            registerService.verifyPayment(dto.getParticipantId(), dto.getEventId());
            return new ResponseEntity<>(gson.toJson("Payment verified"), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error in verify-email", e);
            return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Verify Payment Batch", description = "Marks a batch of participants as payment-verified")
    @PostMapping("/participants/verify-payment-batch")
    public ResponseEntity<String> verifyPaymentBatch(@Valid @RequestBody List<VerifyParticipantDto> list) {
        try {
            registerService.verifyPaymentBatch(list);
            return new ResponseEntity<>(gson.toJson("Batch payment verified"), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error in verify-email-batch", e);
            return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Get Participants by Event Genre", description = "Retrieves a list of participants for a specific event based on their genre")
    @GetMapping("/participants/{eventName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE', 'JUDGE')")
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
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.NOT_FOUND);
        }
    }

    @Operation(summary = "Get All Paid Participants", description = "Gets all verified/paid participants for an event regardless of genre")
    @GetMapping("/verified-participant/{eventName}")
    public ResponseEntity<List<GetParticipantByEventDto>> getAllVerifiedParticipant(@PathVariable String eventName) {
        List<GetParticipantByEventDto> res = eventParticipantService.getAllParticipantsByEvent(eventName);
        if (res == null) {
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @Operation(summary = "Send Check-In Preview", description = "Broadcasts participant details to AuditionNumber display before generating numbers")
    @PostMapping("/{eventName}/checkin-preview")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<String> sendCheckinPreview(
            @PathVariable String eventName,
            @RequestBody CheckinPreviewDto dto) {
        try {
            messagingTemplate.convertAndSend("/topic/checkin-preview/", dto);
            return new ResponseEntity<>("ok", HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error sending checkin preview for event: {}", eventName, e);
            return new ResponseEntity<>("error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
    @GetMapping("/register-participant/{participantId}/{eventId}/{eventGenreId}")
    public ResponseEntity<String> registerParticipantWithGenre(@PathVariable Long participantId,
            @PathVariable Long eventId, @PathVariable Long eventGenreId) throws IOException {
        try {
            AddParticipantToEventGenreDto dto = new AddParticipantToEventGenreDto();
            dto.participantId = participantId;
            dto.eventId = eventId;
            dto.eventGenreId = eventGenreId;
            eventGenreParticipantService.getAuditionNumViaQR(dto);
            return new ResponseEntity<>("registered", HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>("Something is null", HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Update Participant Judge", description = "Assigns or updates a judge for a participant")
    @PostMapping("/participants-judge/")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<String> updateParticipantJudge(@Valid @RequestBody UpdateParticipantJudgeDto dto) {
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
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'JUDGE')")
    public ResponseEntity<String> updateParticipantScore(@Valid @RequestBody UpdateParticipantsScoreDto dto) {
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
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE', 'JUDGE')")
    public ResponseEntity<List<GetParticipatnScoreDto>> getParticipantScore(@PathVariable String eventName) {
        try {
            return new ResponseEntity<>(scoreService.getAllScore(eventName), HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error fetching participant scores", e);
            return new ResponseEntity<>(new ArrayList<>(), HttpStatus.BAD_REQUEST);
        }
    }

    @DeleteMapping("/scores/reset")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'JUDGE')")
    public ResponseEntity<?> resetScoresByJudge(
            @RequestParam String eventName,
            @RequestParam String genreName,
            @RequestParam String judgeName) {
        try {
            scoreService.resetScoresByJudge(eventName, genreName, judgeName);
            return ResponseEntity.ok(Map.of("message", "Scores reset"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/feedback/reset")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'JUDGE')")
    public ResponseEntity<?> resetFeedbackByJudge(
            @RequestParam String eventName,
            @RequestParam String genreName,
            @RequestParam String judgeName) {
        try {
            feedbackService.resetFeedbackByJudge(eventName, genreName, judgeName);
            return ResponseEntity.ok(Map.of("message", "Feedback reset"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Get Email Template", description = "Returns the email template for the given event")
    @GetMapping("/{eventName}/email-template")
    public ResponseEntity<?> getEmailTemplate(@PathVariable String eventName) {
        GetEmailTemplateDto dto = emailTemplateService.getTemplateByEventName(eventName);
        if (dto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Email template not found"));
        }
        return new ResponseEntity<>(dto, HttpStatus.OK);
    }

    @Operation(summary = "Update Email Template", description = "Updates the email template for the given event")
    @PostMapping("/{eventName}/email-template")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> updateEmailTemplate(
            @PathVariable String eventName,
            @Valid @RequestBody UpdateEmailTemplateDto dto) {
        try {
            return new ResponseEntity<>(emailTemplateService.updateTemplate(eventName, dto), HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Reset Email Template", description = "Regenerates the smart default email template based on current event genre formats")
    @PostMapping("/{eventName}/email-template/reset")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> resetEmailTemplate(@PathVariable String eventName) {
        try {
            return new ResponseEntity<>(emailTemplateService.resetToSmartDefault(eventName), HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Remove Participant Genre", description = "Removes a participant from a specific genre in an event")
    @DeleteMapping("/participant-genre/{participantId}/{eventId}/{eventGenreId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<Void> removeParticipantGenre(
            @PathVariable Long participantId,
            @PathVariable Long eventId,
            @PathVariable Long eventGenreId) {
        try {
            eventGenreParticipantService.removeParticipantFromGenre(participantId, eventId, eventGenreId);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (Exception e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Add Genre to Existing Participant", description = "Adds a new genre to an already-registered participant and assigns an audition number via WebSocket")
    @PostMapping("/participant-genre")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<String> addGenreToParticipant(@Valid @RequestBody UpdateParticipantGenreDto dto) {
        try {
            eventGenreParticipantService.addGenreToExistingParticipant(dto.participantId, dto.eventId, dto.genreName, dto.entryMode, dto.teamName, dto.teamMembers);
            return new ResponseEntity<>(gson.toJson("Genre added"), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Submit Audition Feedback", description = "Saves or updates judge feedback tags and note for a participant")
    @PostMapping("/feedback")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'JUDGE')")
    public ResponseEntity<?> submitFeedback(@Valid @RequestBody SubmitAuditionFeedbackDto dto) {
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
            @Valid @RequestBody Map<String, Boolean> body) {
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
            @Valid @RequestBody AddScoringCriteriaDto dto) {
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
            @Valid @RequestBody UpdateScoringCriteriaDto dto) {
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
    public ResponseEntity<?> createPickupCrew(@Valid @RequestBody CreatePickupCrewDto dto) {
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

    // ── Division endpoints ────────────────────────────────────────────────────

    @Operation(summary = "Add Division", description = "Creates a new division for an event")
    @PostMapping("/{eventName}/divisions")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<String> addDivision(
            @PathVariable String eventName,
            @Valid @RequestBody AddDivisionDto dto) {
        AddGenreToEventDto genreDto = new AddGenreToEventDto();
        genreDto.eventName = eventName;
        AddGenreToEventDto.Division div = new AddGenreToEventDto.Division();
        div.name = dto.name;
        div.format = dto.format;
        div.genreId = dto.genreId;
        genreDto.divisions = List.of(div);
        try {
            eventGenreService.addGenreToEventService(genreDto);
            return new ResponseEntity<>(gson.toJson("Division added"), HttpStatus.CREATED);
        } catch (Exception e) {
            return new ResponseEntity<>(gson.toJson(e.getMessage()), HttpStatus.BAD_REQUEST);
        }
    }

    @Operation(summary = "Rename Division", description = "Renames a division")
    @PatchMapping("/{eventName}/divisions/{id}/name")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> renameDivision(
            @PathVariable String eventName,
            @PathVariable Long id,
            @Valid @RequestBody Map<String, String> body) {
        try {
            eventGenreService.renameDivision(id, body.get("name"));
            return ResponseEntity.ok(gson.toJson("Division renamed"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Update Division Aliases", description = "Updates sheet aliases for a division")
    @PatchMapping("/{eventName}/divisions/{id}/aliases")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> updateDivisionAliases(
            @PathVariable String eventName,
            @PathVariable Long id,
            @Valid @RequestBody Map<String, String> body) {
        try {
            eventGenreService.updateAliases(id, body.get("aliases"));
            return ResponseEntity.ok(gson.toJson("Aliases updated"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Update Division Solo Allowed", description = "Sets whether solo entries are allowed for a division")
    @PatchMapping("/{eventName}/divisions/{id}/solo-allowed")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> updateDivisionSoloAllowed(
            @PathVariable String eventName,
            @PathVariable Long id,
            @Valid @RequestBody Map<String, Object> body) {
        try {
            boolean soloAllowed = Boolean.TRUE.equals(body.get("soloAllowed"));
            eventGenreService.updateSoloAllowed(id, soloAllowed);
            return ResponseEntity.ok(gson.toJson("Solo allowed updated"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Delete Division", description = "Deletes a division from an event")
    @DeleteMapping("/{eventName}/divisions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> deleteDivision(
            @PathVariable String eventName,
            @PathVariable Long id) {
        try {
            eventGenreService.deleteDivision(id);
            return ResponseEntity.ok(gson.toJson("Division deleted"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Battle Guest endpoints ─────────────────────────────────────────────────

    @Operation(summary = "Get Battle Guests", description = "Returns all battle guests for a given event and genre")
    @GetMapping("/battle-guests/{eventName}/{genreName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<List<GetBattleGuestDto>> getBattleGuests(
            @PathVariable String eventName,
            @PathVariable String genreName) {
        return ResponseEntity.ok(battleGuestService.getBattleGuests(eventName, genreName));
    }

    @Operation(summary = "Get All Battle Guests For Event", description = "Returns all battle guests across all genres for an event")
    @GetMapping("/battle-guests/{eventName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<List<GetBattleGuestDto>> getAllBattleGuests(@PathVariable String eventName) {
        return ResponseEntity.ok(battleGuestService.getAllBattleGuestsForEvent(eventName));
    }

    @Operation(summary = "Add Battle Guest", description = "Adds a battle guest to a genre in an event")
    @PostMapping("/battle-guests/{eventName}/{genreName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> addBattleGuest(
            @PathVariable String eventName,
            @PathVariable String genreName,
            @RequestBody AddBattleGuestDto dto) {
        try {
            return ResponseEntity.ok(battleGuestService.addBattleGuest(eventName, genreName, dto));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Remove Battle Guest", description = "Removes a battle guest by ID")
    @DeleteMapping("/battle-guests/{guestId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> removeBattleGuest(@PathVariable Long guestId) {
        battleGuestService.removeBattleGuest(guestId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Assign Audition Number Manually", description = "Assigns a specific available number to a participant who has none in that division")
    @PostMapping("/adjust/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> assignAuditionNumber(@RequestBody Map<String, Object> body) {
        try {
            Long eventId = ((Number) body.get("eventId")).longValue();
            Long participantId = ((Number) body.get("participantId")).longValue();
            Long eventGenreId = ((Number) body.get("eventGenreId")).longValue();
            Integer auditionNumber = ((Number) body.get("auditionNumber")).intValue();
            eventGenreParticipantService.assignAuditionNumber(eventId, participantId, eventGenreId, auditionNumber);
            return ResponseEntity.ok(Map.of("message", "Audition number assigned"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/adjust/assign-batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> assignAuditionNumbersBatch(@RequestBody Map<String, Object> body) {
        try {
            Long eventId = ((Number) body.get("eventId")).longValue();
            Long participantId = ((Number) body.get("participantId")).longValue();
            List<Map<String, Object>> assignments = (List<Map<String, Object>>) body.get("assignments");
            eventGenreParticipantService.assignAuditionNumbersBatch(eventId, participantId, assignments);
            return ResponseEntity.ok(Map.of("message", "All audition numbers assigned"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Swap Audition Numbers", description = "Swaps audition numbers between two participants in the same division")
    @PostMapping("/adjust/swap")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> swapAuditionNumbers(@RequestBody Map<String, Object> body) {
        try {
            Long eventId = ((Number) body.get("eventId")).longValue();
            Long eventGenreId = ((Number) body.get("eventGenreId")).longValue();
            Long participantId1 = ((Number) body.get("participantId1")).longValue();
            Long participantId2 = ((Number) body.get("participantId2")).longValue();
            eventGenreParticipantService.swapAuditionNumbers(eventId, eventGenreId, participantId1, participantId2);
            return ResponseEntity.ok(Map.of("message", "Audition numbers swapped"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Release All Audition Numbers", description = "Clears all audition numbers for a participant across all their divisions in this event")
    @PostMapping("/adjust/release")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> releaseAuditionNumbers(@RequestBody Map<String, Object> body) {
        try {
            Long eventId = ((Number) body.get("eventId")).longValue();
            Long participantId = ((Number) body.get("participantId")).longValue();
            eventGenreParticipantService.releaseAuditionNumbers(eventId, participantId);
            return ResponseEntity.ok(Map.of("message", "Audition numbers released"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}