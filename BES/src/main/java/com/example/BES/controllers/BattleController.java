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

import com.example.BES.dtos.battle.DeleteImageDto;
import com.example.BES.dtos.battle.SetBattleModeDto;
import com.example.BES.dtos.battle.SetBattlerPairDto;
import com.example.BES.dtos.battle.SetBattlePhaseDto;
import com.example.BES.dtos.battle.SetBracketStateDto;
import com.example.BES.dtos.battle.SetJudgeDto;
import com.example.BES.dtos.battle.SetOverlayConfigDto;
import com.example.BES.dtos.battle.SetSmokeBattlersDto;
import com.example.BES.dtos.battle.SetVoteDto;
import com.example.BES.services.BattleService;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/battle")
public class BattleController {

    private final Path uploadDir = Paths.get("uploads");

    @Autowired
    BattleService battleService;

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
    public ResponseEntity<?> setSelectedMode(@Valid @RequestBody SetBattleModeDto dto){
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
            "right", currentPair.getRightBattler().getName(),
            "rightScore", currentPair.getRightBattler().getScore(),
            "leftScore", currentPair.getLeftBattler().getScore()
        ));
    }

    @PostMapping("/battle-pair")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setBattlerPair(@Valid @RequestBody SetBattlerPairDto dto){
        battleService.setBattlerPairService(dto);
        return ResponseEntity.ok(Map.of(
            "message", "successfully set the battle pair",
            "left", battleService.getCurrentPair().getLeftBattler().getName(),
            "right", battleService.getCurrentPair().getRightBattler().getName()
        ));
    }

    @PostMapping("/score")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setBattleScore(){
        Integer code = battleService.setScoreService();
        if(code == -2){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of("message", "No judge found")
            );
        }else if(code == -1){
            return ResponseEntity.ok(
                Map.of("message", "Its a tie",
                "winner", -1)
            );
        }
        else if(code == 0){
            return ResponseEntity.ok(
                Map.of(
                    "message", "Left side get one point",
                    "winner", 0,
                    "current score", battleService.getCurrentPair().getLeftBattler().getScore()
                )
            );
        }
        else {
            return ResponseEntity.ok(
                Map.of(
                    "message", "Right side get one point",
                    "winner", 1,
                    "current score", battleService.getCurrentPair().getRightBattler().getScore())
            );
        }
    }

    @GetMapping("/judges")
    public ResponseEntity<?> getAllBattleJudges(){
        return ResponseEntity.ok(Map.of(
            "judges",battleService.getJudges()
        ));
    }
    
    @DeleteMapping("/judge")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> removeBattleJudge(@Valid @RequestBody SetJudgeDto dto){
        return ResponseEntity.ok(Map.of(
            "judge removed",battleService.removeBattleJudgeService(dto)
        ));
    }

    @PostMapping("/judge")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setJudge(@Valid @RequestBody SetJudgeDto dto){
        Integer status = battleService.setBattleJudgeService(dto);
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

    @PostMapping("/vote")
    public ResponseEntity<?> submitVote(@Valid @RequestBody SetVoteDto dto){
        Integer vote = battleService.setVoteService(dto);
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
    public ResponseEntity<?> handleUpload(@RequestParam("file") MultipartFile file) throws IOException{
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
    public ResponseEntity<String> deleteImage(@Valid @RequestBody DeleteImageDto dto) throws IOException{
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
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setSmokeList(@Valid @RequestBody SetSmokeBattlersDto dto){
        battleService.setSmokeBattlersService(dto);
        return ResponseEntity.ok(Map.of(
            "message", "List updated"
        ));
    }

    @GetMapping("/phase")
    public ResponseEntity<?> getBattlePhase(){
        return ResponseEntity.ok(Map.of("phase", battleService.getBattlePhase()));
    }

    @PostMapping("/phase")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setBattlePhase(@Valid @RequestBody SetBattlePhaseDto dto){
        battleService.setBattlePhaseService(dto.getPhase());
        return ResponseEntity.ok(Map.of("phase", battleService.getBattlePhase()));
    }

    @GetMapping("/bracket")
    public ResponseEntity<?> getBracketState(){
        Object state = battleService.getBracketState();
        if (state == null) return ResponseEntity.ok(Map.of());
        return ResponseEntity.ok(state);
    }

    @PostMapping("/bracket")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setBracketState(@Valid @RequestBody SetBracketStateDto dto){
        battleService.setBracketStateService(dto);
        return ResponseEntity.ok(Map.of("message", "Bracket state updated"));
    }

    @GetMapping("/overlay-config")
    public ResponseEntity<?> getOverlayConfig() {
        return ResponseEntity.ok(battleService.getOverlayConfig());
    }

    @PostMapping("/overlay-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'ORGANISER')")
    public ResponseEntity<?> setOverlayConfig(@Valid @RequestBody SetOverlayConfigDto dto) {
        battleService.setOverlayConfigService(dto);
        return ResponseEntity.ok(battleService.getOverlayConfig());
    }
}
