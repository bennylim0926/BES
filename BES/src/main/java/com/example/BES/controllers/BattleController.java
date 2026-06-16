package com.example.BES.controllers;
import jakarta.validation.Valid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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
import org.springframework.web.multipart.MultipartFile;

import com.example.BES.dtos.battle.ChampionRevealDto;
import com.example.BES.dtos.battle.DeleteImageDto;
import com.example.BES.dtos.battle.SetActiveCategoryDto;
import com.example.BES.dtos.battle.SetBattleModeDto;
import com.example.BES.dtos.battle.SetBattlerPairDto;
import com.example.BES.dtos.battle.SetBattlePhaseDto;
import com.example.BES.dtos.battle.SetBracketStateDto;
import com.example.BES.dtos.battle.SetJudgeDto;
import com.example.BES.dtos.battle.SetOverlayConfigDto;
import com.example.BES.dtos.battle.SetBattleScoreDto;
import com.example.BES.dtos.battle.SetResolvedParticipantsDto;
import com.example.BES.dtos.battle.SetSmokeBattlersDto;
import com.example.BES.dtos.battle.SetVoteDto;
import com.example.BES.dtos.battle.UpdateJudgeWeightageDto;
import com.example.BES.services.BattleService;
import com.example.BES.services.TierAccessService;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/battle")
public class BattleController {

    private final Path uploadDir = Paths.get("uploads");

    @Autowired
    BattleService battleService;

    @Autowired
    TierAccessService tierAccessService;

    private String resolveEvent(String dtoEventName) {
        return (dtoEventName != null && !dtoEventName.isBlank())
            ? dtoEventName
            : battleService.getActiveEventName() != null ? battleService.getActiveEventName() : "";
    }

    private void checkBattleAccess(Authentication auth, String eventName) {
        tierAccessService.requireBattleAccess(auth, eventName);
    }

    @GetMapping("/modes")
    public ResponseEntity<?> getAllModes(){
        return ResponseEntity.ok(
            Map.of(
                "modes", battleService.getModes()
            )
        );
    }

    @GetMapping("/battle-mode")
    public ResponseEntity<?> getSelectedMode(){
        return ResponseEntity.ok(
            Map.of(
                "mode", battleService.getSelectedMode()
            )
        );
    }

    @PostMapping("/battle-mode")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setSelectedMode(Authentication auth, @Valid @RequestBody SetBattleModeDto dto){
        checkBattleAccess(auth, null);
        battleService.setSelectedMode(dto);
        return ResponseEntity.ok(
            Map.of(
                "message", "Mode set successfully",
                "mode", battleService.getSelectedMode()
            )
        );
    }

    @GetMapping("/battle-pair")
    public ResponseEntity<?> getBattlePair(){
        BattleService.BattlePair currentPair = battleService.getCurrentPair();
        if(currentPair == null){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of(
                    "message", "current pair not found"
                )
            );
        }
        return ResponseEntity.ok(Map.of(
            "left", currentPair.getLeftBattler().getName(),
            "leftScore", currentPair.getLeftBattler().getScore(),
            "leftMembers", currentPair.getLeftBattler().getMembers(),
            "right", currentPair.getRightBattler().getName(),
            "rightScore", currentPair.getRightBattler().getScore(),
            "rightMembers", currentPair.getRightBattler().getMembers(),
            "isFinal", battleService.isCurrentFinal()
        ));
    }

    @PostMapping("/battle-pair")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    public ResponseEntity<?> setBattlerPair(Authentication auth, @Valid @RequestBody SetBattlerPairDto dto){
        String eName = resolveEvent(dto.getEventName());
        checkBattleAccess(auth, eName);
        battleService.setBattlerPairService(eName, dto);
        return ResponseEntity.ok(Map.of(
            "message", "successfully set the battle pair",
            "left", battleService.getCurrentPair().getLeftBattler().getName(),
            "right", battleService.getCurrentPair().getRightBattler().getName()
        ));
    }

    @DeleteMapping("/battle-pair")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    public ResponseEntity<?> clearBattlePair(Authentication auth, @RequestParam(required = false) String event){
        String eName = resolveEvent(event);
        checkBattleAccess(auth, eName);
        battleService.clearBattlePairService(eName);
        return ResponseEntity.ok(Map.of(
            "message", "battle pair cleared"
        ));
    }

    @PostMapping("/timer")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    public ResponseEntity<?> updateTimer(
            Authentication auth,
            @RequestParam(required = false) String event,
            @RequestBody Map<String, Object> payload) {
        String eName = resolveEvent(event);
        checkBattleAccess(auth, eName);
        battleService.handleTimerPayload(eName, payload);
        return ResponseEntity.ok(Map.of("message", "Timer updated"));
    }

    @PostMapping("/format-timer")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    public ResponseEntity<?> updateFormatTimer(
            Authentication auth,
            @RequestParam(required = false) String event,
            @RequestBody Map<String, Object> payload) {
        String eName = resolveEvent(event);
        checkBattleAccess(auth, eName);
        battleService.handleFormatTimerPayload(eName, payload);
        return ResponseEntity.ok(Map.of("message", "Format timer updated"));
    }

    @PostMapping("/score")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    public ResponseEntity<?> setBattleScore(Authentication auth, @RequestBody(required = false) SetBattleScoreDto dto){
        String eName = resolveEvent(dto != null ? dto.getEventName() : null);
        checkBattleAccess(auth, eName);
        boolean isFinal = dto != null && dto.isFinal();
        Integer code = battleService.setScoreService(eName, isFinal);
        if(code == -2){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of("message", "No judge found")
            );
        }else if(code == -3){
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                Map.of("message", "Tie in final match — revote required", "tie", true)
            );
        }else if(code == -1){
            return ResponseEntity.ok(Map.of("message", "Its a tie", "winner", -1));
        }else if(code == 0){
            return ResponseEntity.ok(Map.of(
                "message", "Left side get one point",
                "winner", 0,
                "current score", battleService.getCurrentPair(eName).getLeftBattler().getScore()
            ));
        }else {
            return ResponseEntity.ok(Map.of(
                "message", "Right side get one point",
                "winner", 1,
                "current score", battleService.getCurrentPair(eName).getRightBattler().getScore()
            ));
        }
    }

    @PostMapping("/revote")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    public ResponseEntity<?> revote(Authentication auth, @RequestParam(required = false) String event){
        String eName = resolveEvent(event);
        checkBattleAccess(auth, eName);
        battleService.resetJudgeVotesService(eName);
        return ResponseEntity.ok(Map.of("message", "Judge votes reset"));
    }

    @PostMapping("/champion-reveal")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    public ResponseEntity<?> championReveal(Authentication auth, @RequestBody ChampionRevealDto dto){
        String eName = resolveEvent(dto.getEventName());
        checkBattleAccess(auth, eName);
        battleService.broadcastChampionReveal(eName, dto);
        return ResponseEntity.ok(Map.of("message", "Champion reveal broadcast"));
    }

    @GetMapping("/champions")
    public ResponseEntity<?> getChampionsForEvent(@RequestParam String event){
        return ResponseEntity.ok(battleService.getChampionsForEvent(event));
    }

    @GetMapping("/judges")
    public ResponseEntity<?> getAllBattleJudges(@RequestParam(required = false) String event){
        return ResponseEntity.ok(Map.of(
            "judges", battleService.getJudges(resolveEvent(event))
        ));
    }
    
    @DeleteMapping("/judge")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> removeBattleJudge(Authentication auth, @Valid @RequestBody SetJudgeDto dto){
        String eName = resolveEvent(dto.getEventName());
        checkBattleAccess(auth, eName);
        return ResponseEntity.ok(Map.of(
            "judge removed", battleService.removeBattleJudgeService(eName, dto)
        ));
    }

    @PostMapping("/judge")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setJudge(Authentication auth, @Valid @RequestBody SetJudgeDto dto){
        String eName = resolveEvent(dto.getEventName());
        checkBattleAccess(auth, eName);
        Integer status = battleService.setBattleJudgeService(eName, dto);
        if(status == -1) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
            Map.of(
                "message", "Judge not found"
            )
        );
        if(status == 0){
            return ResponseEntity.ok(Map.of(
                "message", "judge already added"
            ));
        }
        return ResponseEntity.ok(Map.of(
            "message", "Added judge"
        ));
    }

    @PostMapping("/judge/weightage")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> updateJudgeWeightage(Authentication auth, @Valid @RequestBody UpdateJudgeWeightageDto dto) {
        String eName = resolveEvent(dto.getEventName());
        checkBattleAccess(auth, eName);
        battleService.updateJudgeWeightageService(eName, dto);
        return ResponseEntity.ok(Map.of("message", "Weightage updated"));
    }

    @PostMapping("/vote")
    public ResponseEntity<?> submitVote(@Valid @RequestBody SetVoteDto dto){
        Integer vote = battleService.setVoteService(resolveEvent(dto.getEventName()), dto);
        if(vote == -2){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of("message", "Judge not found")
            );
        }
        if(vote == 0){
            return ResponseEntity.ok(Map.of(
            "message", "Voted left"
        ));
        }
        else if(vote == 1){
            return ResponseEntity.ok(Map.of(
            "message", "Voted right"
        ));
        }
        else if(vote == -1){
            return ResponseEntity.ok(Map.of(
            "message", "Tied"));
        }else{
            return ResponseEntity.ok(Map.of(
            "message", "Reset"));
        }
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> handleUpload(Authentication auth, @RequestParam("file") MultipartFile file) throws IOException{
        checkBattleAccess(auth, null);
        if (!Files.exists(uploadDir)) {
            Files.createDirectories(uploadDir);
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return ResponseEntity.badRequest().body("Filename is required");
        }
        // Preserve original filename (so overlay can look up by participant name).
        // Strip any path separators to prevent directory traversal.
        String safeFilename = originalFilename.replaceAll("[/\\\\]", "_");
        Path dest = uploadDir.resolve(safeFilename).normalize();
        if (!dest.startsWith(uploadDir.normalize())) {
            return ResponseEntity.badRequest().body("Invalid file path");
        }
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        return ResponseEntity.ok(safeFilename);
    }

    @GetMapping("/uploads/{filename:.+}")
    public ResponseEntity<Resource> getFile(@PathVariable String filename) throws IOException {
        Path file = uploadDir.resolve(filename).normalize();
        if (!file.startsWith(uploadDir.normalize())) {
            return ResponseEntity.badRequest().build();
        }
        Resource resource = new UrlResource(file.toUri());
        return ResponseEntity.ok().body(resource);
    }

    @GetMapping("/images")
    public ResponseEntity<List<String>> getAllImages() throws IOException{
        List<String> fileNames = Files.list(uploadDir)
            .filter(Files::isRegularFile)
            .map(path -> path.getFileName().toString())
            .collect(Collectors.toList());
        return ResponseEntity.ok(fileNames);
    }

    @DeleteMapping("/image")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<String> deleteImage(Authentication auth, @Valid @RequestBody DeleteImageDto dto) throws IOException{
        checkBattleAccess(auth, null);
        Path file = uploadDir.resolve(dto.getName()).normalize();
        if (!file.startsWith(uploadDir.normalize())) {
            return ResponseEntity.badRequest().body("Invalid file path");
        }
        if (!Files.exists(file)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("File not found");
        }
        Files.delete(file);
        return ResponseEntity.ok("File deleted successfully");
    }



    @GetMapping("/smoke")
    public ResponseEntity<?> getSmokeList(){
        List<BattleService.Battler> battlers = battleService.getSmokeBattlersService();
        return ResponseEntity.ok(Map.of(
            "list", battlers
        ));
    }

    @PostMapping("/smoke")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    public ResponseEntity<?> setSmokeList(Authentication auth, @Valid @RequestBody SetSmokeBattlersDto dto){
        String eName = resolveEvent(dto.getEventName());
        checkBattleAccess(auth, eName);
        battleService.setSmokeBattlersService(eName, dto);
        return ResponseEntity.ok(Map.of(
            "message", "List updated"
        ));
    }

    @GetMapping("/phase")
    public ResponseEntity<?> getBattlePhase(){
        return ResponseEntity.ok(Map.of("phase", battleService.getBattlePhase()));
    }

    @PostMapping("/phase")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    public ResponseEntity<?> setBattlePhase(Authentication auth, @Valid @RequestBody SetBattlePhaseDto dto){
        String eName = resolveEvent(dto.getEventName());
        checkBattleAccess(auth, eName);
        battleService.setBattlePhaseService(eName, dto.getPhase(), dto.getChampion());
        return ResponseEntity.ok(Map.of("phase", battleService.getBattlePhase()));
    }

    @GetMapping("/bracket")
    public ResponseEntity<?> getBracketState(){
        Object state = battleService.getBracketState();
        if (state == null) return ResponseEntity.ok(Map.of());
        return ResponseEntity.ok(state);
    }

    @PostMapping("/bracket")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    public ResponseEntity<?> setBracketState(Authentication auth, @Valid @RequestBody SetBracketStateDto dto){
        String eName = resolveEvent(dto.getEventName());
        checkBattleAccess(auth, eName);
        battleService.setBracketStateService(eName, dto);
        return ResponseEntity.ok(Map.of("message", "Bracket state updated"));
    }

    @GetMapping("/overlay-config")
    public ResponseEntity<?> getOverlayConfig(@RequestParam(required = false) String event) {
        return ResponseEntity.ok(battleService.getOverlayConfig(resolveEvent(event)));
    }

    @PostMapping("/overlay-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setOverlayConfig(Authentication auth, @Valid @RequestBody SetOverlayConfigDto dto) {
        String eName = resolveEvent(dto.getEventName());
        checkBattleAccess(auth, eName);
        battleService.setOverlayConfigService(eName, dto);
        return ResponseEntity.ok(battleService.getOverlayConfig(eName));
    }

    @PostMapping("/active-category")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER', 'EMCEE')")
    public ResponseEntity<?> setActiveCategory(Authentication auth, @Valid @RequestBody SetActiveCategoryDto dto) {
        checkBattleAccess(auth, dto.getEventName());
        battleService.switchActiveCategoryService(dto);
        return ResponseEntity.ok(Map.of("message", "Active category set"));
    }

    @GetMapping("/active-category")
    public ResponseEntity<?> getActiveCategory() {
        return ResponseEntity.ok(Map.of(
            "eventName", battleService.getActiveEventName() != null ? battleService.getActiveEventName() : "",
            "categoryName", battleService.getActiveCategoryName() != null ? battleService.getActiveCategoryName() : ""
        ));
    }

    @GetMapping("/category-state")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> getCategoryStateFromDb(
            @RequestParam String event,
            @RequestParam String category) {
        Map<String, Object> state = battleService.getCategoryStateFromDbService(event, category);
        return ResponseEntity.ok(state);
    }

    @PostMapping("/logo-upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> uploadLogo(
            Authentication auth,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String event) throws IOException {
        String eName = resolveEvent(event);
        checkBattleAccess(auth, eName);
        String url = battleService.uploadLogoService(eName, file);
        return ResponseEntity.ok(Map.of("logoUrl", url));
    }

    @DeleteMapping("/logo")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> deleteLogo(
            Authentication auth,
            @RequestParam(required = false) String event) throws IOException {
        String eName = resolveEvent(event);
        checkBattleAccess(auth, eName);
        battleService.deleteLogoService(eName);
        return ResponseEntity.ok(Map.of("message", "Logo deleted"));
    }

    @GetMapping("/state")
    public ResponseEntity<?> getBattleState(Authentication auth, @RequestParam(required = false) String event) {
        String eName = resolveEvent(event);
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            checkBattleAccess(auth, eName);
        }
        Map<String, Object> state = battleService.getBattleStateService(eName);
        if (state.containsKey("timer")) {
            battleService.rebroadcastTimer(eName, state.get("timer"));
        }
        return ResponseEntity.ok(state);
    }

    @PostMapping("/resolved-participants")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setResolvedParticipants(Authentication auth, @Valid @RequestBody SetResolvedParticipantsDto dto) {
        checkBattleAccess(auth, dto.getEventName());
        battleService.setResolvedParticipants(
            dto.getEventName(), dto.getCategoryName(), dto.getParticipants());
        return ResponseEntity.ok(Map.of("message", "Resolved participants saved"));
    }
}
