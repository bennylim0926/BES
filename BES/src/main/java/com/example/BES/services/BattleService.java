package com.example.BES.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.battle.SetBattleModeDto;
import com.example.BES.dtos.battle.SetBattlerPairDto;
import com.example.BES.dtos.battle.SetJudgeDto;
import com.example.BES.dtos.battle.SetSmokeBattlersDto;
import com.example.BES.dtos.battle.SetVoteDto;
import com.example.BES.models.Judge;

@Service
public class BattleService {
    @Autowired
    JudgeService judgeService;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    // top 32, top 16 or 7ts
    private List<String> modes = Arrays.asList("Top32", "Top16", "7-to-Smoke");
    private List<Battler> battlers = new ArrayList<>();
    public List<String> getModes() {
        return modes;
    }
    private String selectedMode;
    private BattlePair currentPair;
    private List<BattleJudge> judges;
    // standby or judge
    private String status;

    BattleService(){
        selectedMode = "";
        currentPair = new BattlePair();
        Battler left = new Battler();
        Battler right = new Battler();
        currentPair.leftBattler = left;
        currentPair.rightBattler = right;
        judges = new ArrayList<>();
    }

    public List<Battler> getSmokeBattlersService(){
        return battlers;
    }

    public void setSmokeBattlersService(SetSmokeBattlersDto dto){
        battlers = new ArrayList<>();
        for (Battler battler : dto.getBattlers()) {
            battlers.add(battler);
        }
        messagingTemplate.convertAndSend("/topic/battle/smoke",
            Map.of(
                "battlers", battlers
            ));
    }

    public void setBattlerPairService(SetBattlerPairDto dto){
        getCurrentPair().leftBattler.setName(dto.getLeftBattler());
        getCurrentPair().leftBattler.setScore(0);
        getCurrentPair().rightBattler.setName(dto.getRightBattler());
        getCurrentPair().rightBattler.setScore(0);
        messagingTemplate.convertAndSend("/topic/battle/battle-pair",
            Map.of(
                "left", currentPair.getLeftBattler().getName(),
                "leftScore", currentPair.getLeftBattler().getScore(),
                "right", currentPair.getRightBattler().getName(),
                "rightScore", currentPair.getRightBattler().getScore()
            ));
    }

    public Integer setScoreService(){
        // Broadcast the score here
        // This is where we reveal the judge decision on the screen
        // After that we add the point
        List<Integer> score = new ArrayList<>();
        Integer res = -100;
        if(judges.size() == 0){
            res = -2;
        }
        for (BattleJudge judge : judges) {
            score.add(judge.getVote());
        }
        if(Collections.frequency(score, 0) == Collections.frequency(score, 1)){
            res = -1;
        }else if(Collections.frequency(score, 0) > Collections.frequency(score, 1)){
            currentPair.getLeftBattler().setScore(currentPair.leftBattler.getScore() + 1);
            res = 0;
        }else{
            currentPair.getRightBattler().setScore(currentPair.rightBattler.getScore() + 1);
            res = 1;
        }
        messagingTemplate.convertAndSend("/topic/battle/score",
            Map.of(
                "message", res,
                "left", currentPair.getLeftBattler().getScore(),
                "right", currentPair.getRightBattler().getScore()
            ));
        return res;
    }

    public Integer removeBattleJudgeService(SetJudgeDto dto){
        judges.removeIf(judge -> Objects.equals(judge.getId(), dto.getId()));
        messagingTemplate.convertAndSend("/topic/battle/judges",
            Map.of(
                "judges", judges
            ));
        return dto.getId().intValue();
    }

    public Integer setBattleJudgeService(SetJudgeDto dto){
        // Need to broadcast this as well, before the battle starts the judge decision overlay will be shown to verify the name
        Judge judge = judgeService.getJudgeById(dto.getId());
        Integer code = -50;
        if(judge != null){
            Boolean exists = judges.stream().anyMatch(j -> j.getName().equals(judge.getName()));
            if(exists) return 0;
            BattleJudge battleJudge = new BattleJudge();
            battleJudge.setName(judge.getName());
            battleJudge.setVote(-1);
            battleJudge.setId(dto.getId());
            judges.add(battleJudge);
            code = dto.getId().intValue();
        }else{
            return -1;
        }
        // send all three judges to update
        messagingTemplate.convertAndSend("/topic/battle/judges",
            Map.of(
                "judges", judges
            ));
        return code;
    }

    // Might need to have an option to remove judge
    public Integer setVoteService(SetVoteDto dto){
        // This need to broadcast as well
        // To reflect on the overlay screen
        Integer code = -50;
        Optional<BattleJudge> battleJude = judges.stream()
            .filter(j -> j.getId().equals(dto.getId())).findFirst();
        if(battleJude.isPresent()){
            battleJude.get().setVote(dto.getVote());
        }else{
            return -2;
        }
        code = dto.getVote();
        // need to include the judge id
        messagingTemplate.convertAndSend(String.format("/topic/battle/vote/%d", dto.getId()),
            Map.of(
                "vote", code,
                "judge", dto.getId()
            ));
        return code;
    }

    public String getSelectedMode() {
        return selectedMode;
    }
    public void setSelectedMode(SetBattleModeDto dto) {
        // when the battle starts cannot change it
        this.selectedMode = dto.getMode();
    }
    public BattlePair getCurrentPair() {
        return currentPair;
    }
    public void setCurrentPair(BattlePair currentPair) {
        this.currentPair = currentPair;
    }
    public List<BattleJudge> getJudges() {
        return judges;
    }
    public void setJudges(List<BattleJudge> judges) {
        this.judges = judges;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    
    public class BattleJudge {
        private Long id;
        private String name;
        /*
         *  0 is for battler 1
         *  1 is for battler 2 
         *  -1 is tie
         */
        private Integer vote;
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public Integer getVote() {
            return vote;
        }
        public void setVote(Integer vote) {
            this.vote = vote;
        }
        public Long getId() {
            return id;
        }
        public void setId(Long id) {
            this.id = id;
        }
    }

    public static class Battler {
        private String name;
        private Integer score;
        Battler(){
            name = "";
            score = 0;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public Integer getScore() {
            return score;
        }
        public void setScore(Integer score) {
            this.score = score;
        }
    }
    public class BattlePair {
        
        private Battler leftBattler;
        private Battler rightBattler;
        
        public Battler getLeftBattler() {
            return leftBattler;
        }
        public Battler getRightBattler() {
            return rightBattler;
        }
        public void setLeftBattler(Battler leftBattler) {
            this.leftBattler = leftBattler;
        }
        public void setRightBattler(Battler rightBattler) {
            this.rightBattler = rightBattler;
        }
    }
}
