package com.example.BES.controllers;

import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.battle.SetBattleModeDto;
import com.example.BES.dtos.battle.SetBattlerPairDto;
import com.example.BES.dtos.battle.SetJudgeDto;
import com.example.BES.dtos.battle.SetVoteDto;
import com.example.BES.services.BattleService;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/battle")
public class BattleController {
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
    public ResponseEntity<?> setSelectedMode(@RequestBody SetBattleModeDto dto){
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
            "left", currentPair.getLeftBattler(),
            "right", currentPair.getRightBattler()
        ));
    }

    @PostMapping("/battle-pair")
    public ResponseEntity<?> setBattlerPair(@RequestBody SetBattlerPairDto dto){
        battleService.setBattlerPairService(dto);
        return ResponseEntity.ok(Map.of(
            "message", "successfully set the battle pair",
            "left", battleService.getCurrentPair().getLeftBattler().getName(),
            "right", battleService.getCurrentPair().getRightBattler().getName()
        ));
    }

    @PostMapping("/score")
    public ResponseEntity<?> setBattleScore(){
        Integer code = battleService.setScoreService();
        if(code == -2){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of("message", "No judge found")
            );
        }else if(code == -1){
            return ResponseEntity.ok(
                Map.of("message", "Its a tie")
            );
        }
        else if(code == 0){
            return ResponseEntity.ok(
                Map.of(
                    "message", "Left side get one point",
                    "current score", battleService.getCurrentPair().getLeftBattler().getScore()
                )
            );
        }
        else {
            return ResponseEntity.ok(
                Map.of(
                    "message", "Right side get one point",
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

    @PostMapping("/judge")
    public ResponseEntity<?> setJudge(@RequestBody SetJudgeDto dto){
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
    public ResponseEntity<?> submitVote(@RequestBody SetVoteDto dto){
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
        else{
            return ResponseEntity.ok(Map.of(
            "message", "Tied"));
        }
    }
}
