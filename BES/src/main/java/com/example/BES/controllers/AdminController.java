package com.example.BES.controllers;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.AddJudgeDto;
import com.example.BES.dtos.GetJudgeDto;
import com.example.BES.dtos.admin.AddFeedbackGroupDto;
import com.example.BES.dtos.admin.AddFeedbackTagDto;
import com.example.BES.dtos.admin.AssignOrganiserDto;
import com.example.BES.dtos.admin.CreateOrganiserDto;
import com.example.BES.dtos.admin.DeleteFeedbackGroupDto;
import com.example.BES.dtos.admin.DeleteFeedbackTagDto;
import com.example.BES.dtos.admin.GetFeedbackGroupDto;
import com.example.BES.dtos.admin.DeleteJudgeDto;
import com.example.BES.dtos.admin.DeleteScoreByEventDto;
import com.example.BES.dtos.admin.GetOrganiserDto;
import com.example.BES.dtos.admin.UpdateJudgeDto;
import com.example.BES.dtos.admin.UpdateOrganiserTierDto;
import com.example.BES.models.Account;
import com.example.BES.models.Judge;
import com.example.BES.dtos.admin.FeedbackTagOverrideDto;
import com.example.BES.dtos.DemoConfigDto;
import com.example.BES.services.AccountService;
import com.example.BES.services.AppConfigService;
import com.example.BES.services.AuditionFeedbackService;
import com.example.BES.services.DemoService;
import com.example.BES.services.EventFeedbackTagService;
import com.example.BES.services.JudgeService;
import com.example.BES.services.ScoreService;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    JudgeService judgeService;

    @Autowired
    ScoreService scoreService;

    @Autowired
    AuditionFeedbackService feedbackService;

    @Autowired
    EventFeedbackTagService eventFeedbackTagService;

    @Autowired
    AccountService accountService;

    @Autowired
    AppConfigService appConfigService;

    @Autowired
    private SimpMessagingTemplate messaging;

    @Autowired
    private DemoService demoService;

    // ── Feedback Tag Groups ──────────────────────────────────────────────────

    @GetMapping("/feedback-groups")
    public ResponseEntity<List<GetFeedbackGroupDto>> getFeedbackGroups() {
        return ResponseEntity.ok(feedbackService.getAllFeedbackGroups());
    }

    @PostMapping("/feedback-group")
    public ResponseEntity<List<GetFeedbackGroupDto>> addFeedbackGroup(@Valid @RequestBody AddFeedbackGroupDto dto) {
        return ResponseEntity.ok(feedbackService.addFeedbackGroup(dto.getName()));
    }

    @DeleteMapping("/feedback-group")
    public ResponseEntity<?> deleteFeedbackGroup(@Valid @RequestBody DeleteFeedbackGroupDto dto) {
        feedbackService.deleteFeedbackGroup(dto.getId());
        return ResponseEntity.ok(Map.of("message", "deleted"));
    }

    @PostMapping("/feedback-tag")
    public ResponseEntity<List<GetFeedbackGroupDto>> addFeedbackTag(@Valid @RequestBody AddFeedbackTagDto dto) {
        return ResponseEntity.ok(feedbackService.addFeedbackTag(dto.getGroupId(), dto.getLabel()));
    }

    @DeleteMapping("/feedback-tag")
    public ResponseEntity<?> deleteFeedbackTag(@Valid @RequestBody DeleteFeedbackTagDto dto) {
        feedbackService.deleteFeedbackTag(dto.getId());
        return ResponseEntity.ok(Map.of("message", "deleted"));
    }

    @GetMapping("/feedback-tags/overrides")
    public ResponseEntity<List<FeedbackTagOverrideDto>> getFeedbackTagOverrides() {
        return ResponseEntity.ok(eventFeedbackTagService.getOverrides());
    }

    @PostMapping("/judge")
    public ResponseEntity<List<GetJudgeDto>> addJudge(@Valid @RequestBody AddJudgeDto dto){
        judgeService.addJudgeService(dto);
        return new ResponseEntity<>(judgeService.getAllJudges(), HttpStatus.OK);
    }

    // Update Judge
    @PostMapping("/update-judge")
    public ResponseEntity<?> updateJudge(@Valid @RequestBody UpdateJudgeDto dto){
        Judge judge = judgeService.updateJudgeService(dto);
        if(judge == null){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of(
                    "message", "Judge NotFound"
                )
            );
        }
        return ResponseEntity.ok(Map.of(
            "message", "Judge updated successfully",
            "id", judge.getJudgeId(),
            "judge", judge.getName()
        ));
    }
    // Delete Judge
        // It might lnik to some event/scores and unable to delete
    @DeleteMapping("/judge")
    public ResponseEntity<?> deleteJudge(@Valid @RequestBody DeleteJudgeDto dto){
        String deletedJudge = judgeService.deleteJudgeService(dto);
        if(deletedJudge.isEmpty()){
            return ResponseEntity.ok(Map.of(
                "message", "Nothing was deleted"
            ));
        }
        return ResponseEntity.ok(Map.of(
            "message", "deleted",
            "judge", deletedJudge
        ));
    }

    // ── Organisers ────────────────────────────────────────────────────────────

    @GetMapping("/organisers")
    public ResponseEntity<List<GetOrganiserDto>> getOrganisers() {
        return ResponseEntity.ok(accountService.getAllOrganisers());
    }

    @PostMapping("/organisers/assign")
    public ResponseEntity<?> assignOrganiser(@Valid @RequestBody AssignOrganiserDto dto) {
        accountService.assignEvent(dto.getAccountId(), dto.getEventId());
        return ResponseEntity.ok(Map.of("message", "assigned"));
    }

    @PostMapping("/organisers")
    public ResponseEntity<?> createOrganiser(@Valid @RequestBody CreateOrganiserDto dto) {
        accountService.createOrganiser(dto.getUsername(), dto.getPassword());
        return ResponseEntity.ok(Map.of("message", "created"));
    }

    @DeleteMapping("/organisers/{accountId}")
    public ResponseEntity<?> deleteOrganiser(@PathVariable Long accountId) {
        accountService.deleteOrganiser(accountId);
        return ResponseEntity.ok(Map.of("message", "deleted"));
    }

    @PostMapping("/organisers/tier")
    public ResponseEntity<?> setOrganiserTier(@Valid @RequestBody UpdateOrganiserTierDto dto) {
        Account account = accountService.setOrganiserTier(dto.getAccountId(), dto.getTier());
        return ResponseEntity.ok(Map.of(
            "accountId", account.getAccountId(),
            "username", account.getUsername(),
            "tier", account.getTier()
        ));
    }

    @DeleteMapping("/organisers/assign")
    public ResponseEntity<?> removeOrganiser(@Valid @RequestBody AssignOrganiserDto dto) {
        accountService.removeEvent(dto.getAccountId(), dto.getEventId());
        return ResponseEntity.ok(Map.of("message", "removed"));
    }

    // Delete Score by Event
    @DeleteMapping("/score")
    public ResponseEntity<?> deleteScoreByEvent(@Valid @RequestBody DeleteScoreByEventDto dto){
        Integer deletedRows = 0;
        deletedRows = scoreService.deleteScoreByEventService(dto);
        return ResponseEntity.ok(Map.of(
            "message", "score deleted",
            "deleted", deletedRows.toString()
        ));
    }

    // ── Demo Config ────────────────────────────────────────────────────────────

    @GetMapping("/demo/config")
    public ResponseEntity<DemoConfigDto> getDemoConfig() {
        return ResponseEntity.ok(new DemoConfigDto(
                appConfigService.isDemoEnabled(),
                appConfigService.getDemoPasscode(),
                null,
                demoService.countActiveSandboxes()
        ));
    }

    @PostMapping("/demo/config")
    public ResponseEntity<DemoConfigDto> updateDemoConfig(@RequestBody DemoConfigDto dto) {
        if (dto.getRegeneratePasscode() != null && dto.getRegeneratePasscode()) {
            String newPasscode = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            appConfigService.setDemoPasscode(newPasscode);
        }
        appConfigService.setDemoEnabled(dto.isDemoEnabled());

        DemoConfigDto result = new DemoConfigDto(
                appConfigService.isDemoEnabled(),
                appConfigService.getDemoPasscode(),
                null,
                demoService.countActiveSandboxes()
        );

        messaging.convertAndSend("/topic/app-config", Map.of(
                "accentColor", appConfigService.getAccentColor(),
                "demoEnabled", result.isDemoEnabled()
        ));

        return ResponseEntity.ok(result);
    }
}
