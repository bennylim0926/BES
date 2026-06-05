package com.example.BES.services;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.dtos.battle.ChampionRevealDto;
import com.example.BES.dtos.battle.SetActiveGenreDto;
import com.example.BES.dtos.battle.SetBattleModeDto;
import com.example.BES.dtos.battle.SetBattlerPairDto;
import com.example.BES.dtos.battle.SetBracketStateDto;
import com.example.BES.dtos.battle.SetJudgeDto;
import com.example.BES.dtos.battle.SetOverlayConfigDto;
import com.example.BES.dtos.battle.SetSmokeBattlersDto;
import com.example.BES.dtos.battle.SetVoteDto;
import com.example.BES.dtos.battle.UpdateJudgeWeightageDto;
import com.example.BES.models.BattleActiveGenre;
import com.example.BES.models.BattleGenreState;
import com.example.BES.models.Judge;
import com.example.BES.respositories.BattleActiveGenreRepository;
import com.example.BES.respositories.BattleGenreStateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class BattleService {

    @Autowired
    JudgeService judgeService;

    @Autowired
    SimpMessagingTemplate messagingTemplate;

    @Autowired
    BattleGenreStateRepository battleGenreStateRepository;

    @Autowired
    BattleActiveGenreRepository battleActiveGenreRepository;

    @Autowired
    ObjectMapper objectMapper;

    private List<String> modes = Arrays.asList("Top32", "Top16", "7-to-Smoke");
    private String selectedMode;

    private Object bracketState = null;
    private Integer currentRoundIndex = 0;
    private List<Battler> battlers = new ArrayList<>();
    private String battlePhase = "IDLE";
    private boolean currentIsFinal = false;
    private Map<String, Object> overlayConfig = new HashMap<>(Map.of(
        "showImages", true,
        "leftColor",  "#dc2626",
        "rightColor", "#2563eb"
    ));
    private BattlePair currentPair;
    private final List<BattleJudge> judges = Collections.synchronizedList(new ArrayList<>());
    private String status;

    private String activeEventName;
    private String activeGenreName;
    private String champion = null;

    BattleService() {
        selectedMode = "";
        currentPair = new BattlePair();
        Battler left = new Battler();
        Battler right = new Battler();
        currentPair.leftBattler = left;
        currentPair.rightBattler = right;
    }

    @PostConstruct
    public void loadStateFromDb() {
        try {
            battleActiveGenreRepository.findById(1).ifPresent(active -> {
                if (active.getEventName() != null && active.getGenreName() != null) {
                    activeEventName = active.getEventName();
                    activeGenreName = active.getGenreName();
                    loadGenreStateIntoMemory(activeEventName, activeGenreName);
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to load battle state from DB on startup: " + e.getMessage());
        }
    }

    public List<String> getModes() { return modes; }

    public List<Battler> getSmokeBattlersService() { return battlers; }

    public void setSmokeBattlersService(SetSmokeBattlersDto dto) {
        battlers = new ArrayList<>();
        for (Battler battler : dto.getBattlers()) battlers.add(battler);
        messagingTemplate.convertAndSend("/topic/battle/smoke", Map.of("battlers", battlers));
        for (Battler battler : battlers) {
            if (battler.getScore() != null && battler.getScore() >= 7) {
                battlePhase = "DECIDED";
                champion = battler.getName();
                messagingTemplate.convertAndSend("/topic/battle/phase", Map.of(
                    "phase", battlePhase,
                    "genre", activeGenreName != null ? activeGenreName : "",
                    "champion", champion
                ));
                break;
            }
        }
        persistActiveState();
    }

    public void setBattlerPairService(SetBattlerPairDto dto) {
        getCurrentPair().leftBattler.setName(dto.getLeftBattler());
        getCurrentPair().leftBattler.setScore(0);
        getCurrentPair().leftBattler.setMembers(dto.getLeftMembers());
        getCurrentPair().rightBattler.setName(dto.getRightBattler());
        getCurrentPair().rightBattler.setScore(0);
        getCurrentPair().rightBattler.setMembers(dto.getRightMembers());
        currentIsFinal = dto.isFinal();
        messagingTemplate.convertAndSend("/topic/battle/battle-pair", Map.of(
            "left",         currentPair.getLeftBattler().getName(),
            "leftScore",    currentPair.getLeftBattler().getScore(),
            "leftMembers",  currentPair.getLeftBattler().getMembers(),
            "right",        currentPair.getRightBattler().getName(),
            "rightScore",   currentPair.getRightBattler().getScore(),
            "rightMembers", currentPair.getRightBattler().getMembers(),
            "isFinal",      currentIsFinal
        ));
        battlePhase = "LOCKED";
        messagingTemplate.convertAndSend("/topic/battle/phase", Map.of(
            "phase", battlePhase,
            "genre", activeGenreName != null ? activeGenreName : ""
        ));
        persistActiveState();
    }

    public void clearBattlePairService() {
        getCurrentPair().leftBattler.setName("");
        getCurrentPair().leftBattler.setScore(0);
        getCurrentPair().leftBattler.setMembers(new ArrayList<>());
        getCurrentPair().rightBattler.setName("");
        getCurrentPair().rightBattler.setScore(0);
        getCurrentPair().rightBattler.setMembers(new ArrayList<>());
        currentIsFinal = false;
        messagingTemplate.convertAndSend("/topic/battle/battle-pair", Map.of(
            "left",         "",
            "leftScore",    0,
            "leftMembers",  new ArrayList<>(),
            "right",        "",
            "rightScore",   0,
            "rightMembers", new ArrayList<>(),
            "isFinal",      false
        ));
        // Does NOT change phase — caller sets phase afterward
    }

    public Integer setScoreService(boolean isFinal) {
        int leftWeight, rightWeight;
        synchronized (judges) {
            leftWeight  = judges.stream().filter(j -> j.getVote() == 0).mapToInt(BattleJudge::getWeightage).sum();
            rightWeight = judges.stream().filter(j -> j.getVote() == 1).mapToInt(BattleJudge::getWeightage).sum();
        }
        Integer res;
        if (leftWeight == rightWeight) {
            if (isFinal) return -3;
            res = -1;
        } else if (leftWeight > rightWeight) {
            currentPair.getLeftBattler().setScore(currentPair.leftBattler.getScore() + 1);
            res = 0;
        } else {
            currentPair.getRightBattler().setScore(currentPair.rightBattler.getScore() + 1);
            res = 1;
        }
        messagingTemplate.convertAndSend("/topic/battle/score", Map.of(
            "message", res,
            "left",    currentPair.getLeftBattler().getScore(),
            "right",   currentPair.getRightBattler().getScore()
        ));
        if (res == 0 || res == 1) {
            battlePhase = "REVEALED";
            messagingTemplate.convertAndSend("/topic/battle/phase", Map.of(
                "phase", battlePhase,
                "genre", activeGenreName != null ? activeGenreName : ""
            ));
            persistActiveState();
        }
        return res;
    }

    public Integer removeBattleJudgeService(SetJudgeDto dto) {
        judges.removeIf(judge -> Objects.equals(judge.getId(), dto.getId()));
        messagingTemplate.convertAndSend("/topic/battle/judges", Map.of("judges", judges));
        persistActiveState();
        return dto.getId().intValue();
    }

    public void updateJudgeWeightageService(UpdateJudgeWeightageDto dto) {
        synchronized (judges) {
            judges.stream()
                .filter(j -> j.getId().equals(dto.getId()))
                .findFirst()
                .ifPresent(j -> j.setWeightage(Math.max(1, dto.getWeightage())));
        }
        messagingTemplate.convertAndSend("/topic/battle/judges", Map.of("judges", judges));
        persistActiveState();
    }

    public Integer setBattleJudgeService(SetJudgeDto dto) {
        Judge judge = judgeService.getJudgeById(dto.getId());
        Integer code = -50;
        if (judge != null) {
            Boolean exists = judges.stream().anyMatch(j -> j.getName().equals(judge.getName()));
            if (exists) return 0;
            BattleJudge battleJudge = new BattleJudge();
            battleJudge.setName(judge.getName());
            battleJudge.setVote(-3);
            battleJudge.setId(dto.getId());
            battleJudge.setWeightage(Math.max(1, dto.getWeightage()));
            judges.add(battleJudge);
            code = dto.getId().intValue();
        } else {
            return -1;
        }
        messagingTemplate.convertAndSend("/topic/battle/judges", Map.of("judges", judges));
        persistActiveState();
        return code;
    }

    public Integer setVoteService(SetVoteDto dto) {
        Integer code = -50;
        Optional<BattleJudge> battleJude = judges.stream()
            .filter(j -> j.getId().equals(dto.getId())).findFirst();
        if (battleJude.isPresent()) {
            battleJude.get().setVote(dto.getVote());
        } else {
            return -2;
        }
        code = dto.getVote();
        messagingTemplate.convertAndSend(
            String.format("/topic/battle/vote/%d", dto.getId()),
            Map.of("vote", code, "judge", dto.getId())
        );
        persistActiveState();
        return code;
    }

    public void resetJudgeVotesService() {
        synchronized (judges) {
            for (BattleJudge judge : judges) judge.setVote(-3);
        }
        messagingTemplate.convertAndSend("/topic/battle/judges", Map.of("judges", judges));
        persistActiveState();
    }

    public Object getBracketState() { return bracketState; }

    public void setBracketStateService(SetBracketStateDto dto) {
        Map<String, Object> state = new HashMap<>();
        state.put("topSize", dto.getTopSize());
        state.put("rounds", dto.getRounds());
        this.bracketState = state;
        if (dto.getCurrentRoundIndex() != null) {
            this.currentRoundIndex = dto.getCurrentRoundIndex();
        }
        messagingTemplate.convertAndSend("/topic/battle/bracket", state);
        persistActiveState();
    }

    public String getSelectedMode() { return selectedMode; }

    public void setSelectedMode(SetBattleModeDto dto) {
        this.selectedMode = dto.getMode();
    }

    public BattlePair getCurrentPair() { return currentPair; }

    public void setCurrentPair(BattlePair currentPair) { this.currentPair = currentPair; }

    public List<BattleJudge> getJudges() { return judges; }

    public void setJudges(List<BattleJudge> judges) {
        synchronized (this.judges) { this.judges.clear(); this.judges.addAll(judges); }
    }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public String getBattlePhase() { return battlePhase; }

    public boolean isCurrentFinal() { return currentIsFinal; }

    public void setBattlePhaseService(String phase) {
        if ("REVEALED".equals(phase)) return;
        // Prevent starting a round with zero judges
        if ("LOCKED".equals(phase)) {
            synchronized (judges) {
                if (judges.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Cannot start round: no judges assigned. Add at least one judge first.");
                }
            }
        }
        battlePhase = phase;
        messagingTemplate.convertAndSend("/topic/battle/phase", Map.of(
            "phase", battlePhase,
            "genre", activeGenreName != null ? activeGenreName : ""
        ));
        persistActiveState();
    }

    public Map<String, Object> getOverlayConfig() { return overlayConfig; }

    public void setOverlayConfigService(SetOverlayConfigDto dto) {
        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put("showImages", dto.isShowImages());
        newConfig.put("leftColor",  dto.getLeftColor());
        newConfig.put("rightColor", dto.getRightColor());
        overlayConfig = newConfig;
        messagingTemplate.convertAndSend("/topic/battle/overlay-config", newConfig);
    }

    public void broadcastChampionReveal(ChampionRevealDto dto) {
        if (dto.isDismiss()) {
            champion = null;
            persistActiveState();
            messagingTemplate.convertAndSend("/topic/battle/champion-reveal", Map.of("dismiss", true));
        } else {
            champion = dto.getChampionName() != null ? dto.getChampionName() : "";
            persistActiveState();
            messagingTemplate.convertAndSend("/topic/battle/champion-reveal", Map.of(
                "dismiss",       false,
                "genreName",     dto.getGenreName()    != null ? dto.getGenreName()    : "",
                "championName",  champion
            ));
        }
    }

    public Map<String, String> getChampionsForEvent(String eventName) {
        Map<String, String> result = new HashMap<>();
        battleGenreStateRepository.findByEventName(eventName).forEach(s -> {
            if (s.getChampion() != null && !s.getChampion().isBlank()) {
                result.put(s.getGenreName(), s.getChampion());
            }
        });
        return result;
    }

    @Transactional
    public void switchActiveGenreService(SetActiveGenreDto dto) {
        persistActiveState();
        BattleActiveGenre active = battleActiveGenreRepository.findById(1)
            .orElse(new BattleActiveGenre(1, null, null));
        active.setEventName(dto.getEventName());
        active.setGenreName(dto.getGenreName());
        battleActiveGenreRepository.save(active);
        activeEventName = dto.getEventName();
        activeGenreName = dto.getGenreName();
        loadGenreStateIntoMemory(activeEventName, activeGenreName);
        broadcastStateSnapshot();
        messagingTemplate.convertAndSend("/topic/battle/phase", Map.of(
            "phase", battlePhase,
            "genre", activeGenreName != null ? activeGenreName : ""
        ));
    }

    public Map<String, Object> getBattleStateService() {
        if (activeEventName == null || activeGenreName == null) return new HashMap<>();
        Map<String, Object> state = new HashMap<>();
        state.put("eventName", activeEventName);
        state.put("genreName", activeGenreName);
        state.put("bracket", bracketState);
        state.put("currentRoundIndex", currentRoundIndex);
        Map<String, Object> pair = new HashMap<>();
        pair.put("left",         currentPair.getLeftBattler().getName());
        pair.put("leftMembers",  currentPair.getLeftBattler().getMembers());
        pair.put("right",        currentPair.getRightBattler().getName());
        pair.put("rightMembers", currentPair.getRightBattler().getMembers());
        pair.put("isFinal",      currentIsFinal);
        state.put("currentPair", pair);
        state.put("battlePhase", battlePhase);
        state.put("champion", champion);
        synchronized (judges) {
            state.put("judges", new ArrayList<>(judges));
        }
        return state;
    }

    public String getActiveEventName() { return activeEventName; }
    public String getActiveGenreName() { return activeGenreName; }

    private void persistActiveState() {
        if (activeEventName == null || activeGenreName == null) return;
        try {
            BattleGenreState s = battleGenreStateRepository
                .findByEventNameAndGenreName(activeEventName, activeGenreName)
                .orElse(new BattleGenreState());
            s.setEventName(activeEventName);
            s.setGenreName(activeGenreName);
            s.setBracketJson(bracketState != null ? objectMapper.writeValueAsString(bracketState) : null);
            if (bracketState instanceof Map) {
                Object ts = ((Map<?, ?>) bracketState).get("topSize");
                if (ts != null) {
                    try { s.setTopSize(Integer.parseInt(ts.toString())); }
                    catch (NumberFormatException ignored) {}
                }
            }
            s.setCurrentRoundIndex(currentRoundIndex);
            s.setCurrentPairLeft(currentPair.getLeftBattler().getName());
            s.setCurrentPairLeftMembers(
                objectMapper.writeValueAsString(currentPair.getLeftBattler().getMembers()));
            s.setCurrentPairRight(currentPair.getRightBattler().getName());
            s.setCurrentPairRightMembers(
                objectMapper.writeValueAsString(currentPair.getRightBattler().getMembers()));
            s.setIsFinal(currentIsFinal);
            s.setBattlePhase(battlePhase);
            s.setChampion(champion);
            s.setSmokeListJson(objectMapper.writeValueAsString(new ArrayList<>(battlers)));
            synchronized (judges) {
                s.setJudgesJson(objectMapper.writeValueAsString(new ArrayList<>(judges)));
            }
            s.setUpdatedAt(LocalDateTime.now());
            battleGenreStateRepository.save(s);
        } catch (Exception e) {
            System.err.println("Failed to persist battle state: " + e.getMessage());
        }
    }

    private void loadGenreStateIntoMemory(String eventName, String genreName) {
        Optional<BattleGenreState> stateOpt =
            battleGenreStateRepository.findByEventNameAndGenreName(eventName, genreName);
        if (stateOpt.isEmpty()) { resetToDefaults(); return; }
        BattleGenreState s = stateOpt.get();
        try {
            bracketState = s.getBracketJson() != null
                ? objectMapper.readValue(s.getBracketJson(), Map.class) : null;
            currentRoundIndex = s.getCurrentRoundIndex() != null ? s.getCurrentRoundIndex() : 0;
            currentPair.getLeftBattler().setName(s.getCurrentPairLeft() != null ? s.getCurrentPairLeft() : "");
            currentPair.getLeftBattler().setScore(0);
            currentPair.getLeftBattler().setMembers(s.getCurrentPairLeftMembers() != null
                ? objectMapper.readValue(s.getCurrentPairLeftMembers(), new TypeReference<List<String>>(){})
                : new ArrayList<>());
            currentPair.getRightBattler().setName(s.getCurrentPairRight() != null ? s.getCurrentPairRight() : "");
            currentPair.getRightBattler().setScore(0);
            currentPair.getRightBattler().setMembers(s.getCurrentPairRightMembers() != null
                ? objectMapper.readValue(s.getCurrentPairRightMembers(), new TypeReference<List<String>>(){})
                : new ArrayList<>());
            currentIsFinal = Boolean.TRUE.equals(s.getIsFinal());
            battlePhase = s.getBattlePhase() != null ? s.getBattlePhase() : "IDLE";
            champion = s.getChampion();
            battlers = s.getSmokeListJson() != null
                ? objectMapper.readValue(s.getSmokeListJson(), new TypeReference<List<Battler>>(){})
                : new ArrayList<>();
            synchronized (judges) {
                judges.clear();
                if (s.getJudgesJson() != null) {
                    List<BattleJudge> restored =
                        objectMapper.readValue(s.getJudgesJson(), new TypeReference<List<BattleJudge>>(){});
                    judges.addAll(restored);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load genre state from DB: " + e.getMessage());
            resetToDefaults();
        }
    }

    private void resetToDefaults() {
        bracketState = null;
        currentRoundIndex = 0;
        currentPair.getLeftBattler().setName("");
        currentPair.getLeftBattler().setScore(0);
        currentPair.getLeftBattler().setMembers(new ArrayList<>());
        currentPair.getRightBattler().setName("");
        currentPair.getRightBattler().setScore(0);
        currentPair.getRightBattler().setMembers(new ArrayList<>());
        currentIsFinal = false;
        battlePhase = "IDLE";
        champion = null;
        battlers = new ArrayList<>();
        synchronized (judges) { judges.clear(); }
    }

    private void broadcastStateSnapshot() {
        messagingTemplate.convertAndSend("/topic/battle/state", getBattleStateService());
    }

    public static class BattleJudge {
        private Long id;
        private String name;
        private Integer vote;
        private Integer weightage;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getVote() { return vote; }
        public void setVote(Integer vote) { this.vote = vote; }
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public int getWeightage() { return weightage != null && weightage > 0 ? weightage : 1; }
        public void setWeightage(int weightage) { this.weightage = weightage; }
    }

    public static class Battler {
        private String name;
        private Integer score;
        private List<String> members = new ArrayList<>();
        Battler() { name = ""; score = 0; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
        public List<String> getMembers() { return members; }
        public void setMembers(List<String> members) {
            this.members = members != null ? members : new ArrayList<>();
        }
    }

    public class BattlePair {
        private Battler leftBattler;
        private Battler rightBattler;
        public Battler getLeftBattler() { return leftBattler; }
        public Battler getRightBattler() { return rightBattler; }
        public void setLeftBattler(Battler leftBattler) { this.leftBattler = leftBattler; }
        public void setRightBattler(Battler rightBattler) { this.rightBattler = rightBattler; }
    }
}
