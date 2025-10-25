package com.example.BES.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.battle.SetBattleModeDto;
import com.example.BES.dtos.battle.SetBattlerPairDto;
import com.example.BES.dtos.battle.SetJudgeDto;
import com.example.BES.dtos.battle.SetVoteDto;
import com.example.BES.models.Judge;

@Service
public class BattleService {
    @Autowired
    JudgeService judgeService;

    // top 32, top 16 or 7ts
    private List<String> modes = Arrays.asList("Top32", "Top16", "7-to-Smoke");
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

    public void setBattlerPairService(SetBattlerPairDto dto){
        getCurrentPair().leftBattler.setName(dto.getLeftBattler());
        getCurrentPair().leftBattler.setScore(0);
        getCurrentPair().rightBattler.setName(dto.getRightBattler());
        getCurrentPair().rightBattler.setScore(0);
    }

    public Integer setScoreService(){
        List<Integer> score = new ArrayList<>();
        if(judges.size() == 0){
            return -2;
        }
        for (BattleJudge judge : judges) {
            score.add(judge.getVote());
        }
        if(Collections.frequency(score, 0) == Collections.frequency(score, 1)){
            return -1;
        }else if(Collections.frequency(score, 0) > Collections.frequency(score, 1)){
            currentPair.getLeftBattler().setScore(currentPair.leftBattler.getScore() + 1);
            return 0;
        }else{
            currentPair.getRightBattler().setScore(currentPair.rightBattler.getScore() + 1);
            return 1;
        }
    }

    public Integer setBattleJudgeService(SetJudgeDto dto){
        Judge judge = judgeService.getJudgeById(dto.getId());
        if(judge != null){
            Boolean exists = judges.stream().anyMatch(j -> j.getName().equals(judge.getName()));
            if(exists) return 0;
            BattleJudge battleJudge = new BattleJudge();
            battleJudge.setName(judge.getName());
            battleJudge.setVote(-1);
            battleJudge.setId(dto.getId());
            judges.add(battleJudge);
            return dto.getId().intValue();
        }
        return -1;
    }

    public Integer setVoteService(SetVoteDto dto){
        Optional<BattleJudge> battleJude = judges.stream()
            .filter(j -> j.getId().equals(dto.getId())).findFirst();
        System.out.println(dto.getId());
        System.out.println(battleJude.isPresent());
        if(battleJude.isPresent()){
            battleJude.get().setVote(dto.getVote());
        }else{
            return -2;
        }
        return dto.getVote();
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

    public class Battler {
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
