package com.example.BES.services;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

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
import com.example.BES.models.Event;
import com.example.BES.models.Judge;
import com.example.BES.respositories.BattleActiveGenreRepository;
import com.example.BES.respositories.BattleGenreStateRepository;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
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
    EventGenreRepo eventGenreRepo;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    ObjectMapper objectMapper;

    private List<String> modes = Arrays.asList("Top32", "Top16", "7-to-Smoke");
    private String selectedMode;

    private final ConcurrentHashMap<String, EventBattleState> eventStates = new ConcurrentHashMap<>();

    private String activeEventName;
    private String status;

    private EventBattleState stateFor(String eventName) {
        return eventStates.computeIfAbsent(
            eventName != null ? eventName : "", k -> new EventBattleState());
    }

    private String resolveEvent(String explicit) {
        if (explicit != null && !explicit.isBlank()) return explicit;
        return activeEventName != null ? activeEventName : "";
    }

    @PostConstruct
    public void loadStateFromDb() {
        try {
            battleActiveGenreRepository.findById(1).ifPresent(active -> {
                if (active.getEventName() != null && active.getGenreName() != null) {
                    activeEventName = active.getEventName();
                    loadGenreStateIntoMemory(activeEventName, active.getGenreName());
                }
            });
        } catch (Exception e) {
            System.err.println("Failed to load battle state from DB on startup: " + e.getMessage());
        }
    }

    public List<String> getModes() { return modes; }

    public List<Battler> getSmokeBattlersService() {
        return stateFor(resolveEvent(null)).battlers;
    }

    public void setSmokeBattlersService(String eventName, SetSmokeBattlersDto dto) {
        EventBattleState s = stateFor(eventName);
        s.battlers = new ArrayList<>(dto.getBattlers());
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/smoke",
            Map.of("battlers", s.battlers));
        for (Battler battler : s.battlers) {
            if (battler.getScore() != null && battler.getScore() >= 7) {
                s.battlePhase = "DECIDED";
                s.champion = battler.getName();
                messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/phase", Map.of(
                    "phase",    s.battlePhase,
                    "genre",    s.activeGenreName != null ? s.activeGenreName : "",
                    "champion", s.champion
                ));
                break;
            }
        }
        persistActiveState(eventName);
    }

    public void setBattlerPairService(String eventName, SetBattlerPairDto dto) {
        EventBattleState s = stateFor(eventName);
        s.currentPair.getLeftBattler().setName(dto.getLeftBattler());
        s.currentPair.getLeftBattler().setScore(0);
        s.currentPair.getLeftBattler().setMembers(dto.getLeftMembers());
        s.currentPair.getRightBattler().setName(dto.getRightBattler());
        s.currentPair.getRightBattler().setScore(0);
        s.currentPair.getRightBattler().setMembers(dto.getRightMembers());
        s.currentIsFinal = dto.isFinal();
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/battle-pair", Map.of(
            "left",         s.currentPair.getLeftBattler().getName(),
            "leftScore",    s.currentPair.getLeftBattler().getScore(),
            "leftMembers",  s.currentPair.getLeftBattler().getMembers(),
            "right",        s.currentPair.getRightBattler().getName(),
            "rightScore",   s.currentPair.getRightBattler().getScore(),
            "rightMembers", s.currentPair.getRightBattler().getMembers(),
            "isFinal",      s.currentIsFinal
        ));
        s.battlePhase = "LOCKED";
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/phase", Map.of(
            "phase", s.battlePhase,
            "genre", s.activeGenreName != null ? s.activeGenreName : ""
        ));
        persistActiveState(eventName);
    }

    public void clearBattlePairService(String eventName) {
        EventBattleState s = stateFor(eventName);
        s.currentPair.getLeftBattler().setName("");
        s.currentPair.getLeftBattler().setScore(0);
        s.currentPair.getLeftBattler().setMembers(new ArrayList<>());
        s.currentPair.getRightBattler().setName("");
        s.currentPair.getRightBattler().setScore(0);
        s.currentPair.getRightBattler().setMembers(new ArrayList<>());
        s.currentIsFinal = false;
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/battle-pair", Map.of(
            "left",         "",
            "leftScore",    0,
            "leftMembers",  new ArrayList<>(),
            "right",        "",
            "rightScore",   0,
            "rightMembers", new ArrayList<>(),
            "isFinal",      false
        ));
    }

    public Integer setScoreService(String eventName, boolean isFinal) {
        EventBattleState s = stateFor(eventName);
        int leftWeight, rightWeight;
        synchronized (s.judges) {
            leftWeight  = s.judges.stream().filter(j -> j.getVote() == 0).mapToInt(BattleJudge::getWeightage).sum();
            rightWeight = s.judges.stream().filter(j -> j.getVote() == 1).mapToInt(BattleJudge::getWeightage).sum();
        }
        Integer res;
        if (leftWeight == rightWeight) {
            if (isFinal) return -3;
            res = -1;
        } else if (leftWeight > rightWeight) {
            s.currentPair.getLeftBattler().setScore(s.currentPair.getLeftBattler().getScore() + 1);
            res = 0;
        } else {
            s.currentPair.getRightBattler().setScore(s.currentPair.getRightBattler().getScore() + 1);
            res = 1;
        }
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/score", Map.of(
            "message", res,
            "left",    s.currentPair.getLeftBattler().getScore(),
            "right",   s.currentPair.getRightBattler().getScore()
        ));
        if (res == 0 || res == 1) {
            if (res == 0 && !s.battlers.isEmpty()) {
                s.battlers.get(0).setScore(s.battlers.get(0).getScore() + 1);
            } else if (res == 1 && s.battlers.size() > 1) {
                s.battlers.get(1).setScore(s.battlers.get(1).getScore() + 1);
            }
            s.battlePhase = "REVEALED";
            messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/phase", Map.of(
                "phase", s.battlePhase,
                "genre", s.activeGenreName != null ? s.activeGenreName : ""
            ));
            persistActiveState(eventName);
        }
        return res;
    }

    public Integer removeBattleJudgeService(String eventName, SetJudgeDto dto) {
        EventBattleState s = stateFor(eventName);
        s.judges.removeIf(judge -> Objects.equals(judge.getId(), dto.getId()));
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/judges",
            Map.of("judges", s.judges));
        persistActiveState(eventName);
        return dto.getId().intValue();
    }

    public void updateJudgeWeightageService(String eventName, UpdateJudgeWeightageDto dto) {
        EventBattleState s = stateFor(eventName);
        synchronized (s.judges) {
            s.judges.stream()
                .filter(j -> j.getId().equals(dto.getId()))
                .findFirst()
                .ifPresent(j -> j.setWeightage(Math.max(1, dto.getWeightage())));
        }
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/judges",
            Map.of("judges", s.judges));
        persistActiveState(eventName);
    }

    public Integer setBattleJudgeService(String eventName, SetJudgeDto dto) {
        EventBattleState s = stateFor(eventName);
        Judge judge = judgeService.getJudgeById(dto.getId());
        if (judge == null) return -1;
        synchronized (s.judges) {
            boolean exists = s.judges.stream().anyMatch(j -> j.getName().equals(judge.getName()));
            if (exists) return 0;
            BattleJudge battleJudge = new BattleJudge();
            battleJudge.setName(judge.getName());
            battleJudge.setVote(-3);
            battleJudge.setId(dto.getId());
            battleJudge.setWeightage(Math.max(1, dto.getWeightage()));
            s.judges.add(battleJudge);
        }
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/judges",
            Map.of("judges", s.judges));
        persistActiveState(eventName);
        return dto.getId().intValue();
    }

    public Integer setVoteService(String eventName, SetVoteDto dto) {
        EventBattleState s = stateFor(eventName);
        Optional<BattleJudge> battleJudge = s.judges.stream()
            .filter(j -> j.getId().equals(dto.getId())).findFirst();
        if (battleJudge.isEmpty()) return -2;
        battleJudge.get().setVote(dto.getVote());
        Integer code = dto.getVote();
        messagingTemplate.convertAndSend(
            String.format("/topic/battle/vote/%d", dto.getId()),
            Map.of("vote", code, "judge", dto.getId())
        );
        persistActiveState(eventName);
        return code;
    }

    public void resetJudgeVotesService(String eventName) {
        EventBattleState s = stateFor(eventName);
        synchronized (s.judges) {
            for (BattleJudge judge : s.judges) judge.setVote(-3);
        }
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/judges",
            Map.of("judges", s.judges));
        persistActiveState(eventName);
    }

    public Object getBracketState() { return stateFor(resolveEvent(null)).bracketState; }

    public void setBracketStateService(String eventName, SetBracketStateDto dto) {
        EventBattleState s = stateFor(eventName);
        Map<String, Object> state = new HashMap<>();
        state.put("topSize", dto.getTopSize());
        state.put("rounds", dto.getRounds());
        s.bracketState = state;
        if (dto.getCurrentRoundIndex() != null) s.currentRoundIndex = dto.getCurrentRoundIndex();
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/bracket", state);
        persistActiveState(eventName);
        broadcastStateSnapshot(eventName);
    }

    public String getSelectedMode() { return selectedMode; }

    public void setSelectedMode(SetBattleModeDto dto) {
        this.selectedMode = dto.getMode();
    }

    public BattlePair getCurrentPair(String eventName) {
        return stateFor(eventName).currentPair;
    }

    public BattlePair getCurrentPair() {
        return stateFor(resolveEvent(null)).currentPair;
    }

    public void setCurrentPair(String eventName, BattlePair pair) {
        stateFor(eventName).currentPair = pair;
    }

    public List<BattleJudge> getJudges() {
        return stateFor(resolveEvent(null)).judges;
    }

    public List<BattleJudge> getJudges(String eventName) {
        return stateFor(eventName).judges;
    }

    public void setJudges(List<BattleJudge> judges) {
        EventBattleState s = stateFor(resolveEvent(null));
        synchronized (s.judges) { s.judges.clear(); s.judges.addAll(judges); }
    }

    public String getStatus() { return status; }

    public void setStatus(String status) { this.status = status; }

    public String getBattlePhase() {
        return stateFor(resolveEvent(null)).battlePhase;
    }

    public String getBattlePhase(String eventName) {
        return stateFor(eventName).battlePhase;
    }

    public boolean isCurrentFinal() {
        return stateFor(resolveEvent(null)).currentIsFinal;
    }

    public boolean isCurrentFinal(String eventName) {
        return stateFor(eventName).currentIsFinal;
    }

    public void setBattlePhaseService(String eventName, String phase) {
        setBattlePhaseService(eventName, phase, null);
    }

    public void setBattlePhaseService(String eventName, String phase, String championName) {
        if ("REVEALED".equals(phase)) return;
        EventBattleState s = stateFor(eventName);
        if ("LOCKED".equals(phase)) {
            synchronized (s.judges) {
                if (s.judges.isEmpty()) {
                    throw new IllegalArgumentException(
                        "Cannot start round: no judges assigned. Add at least one judge first.");
                }
            }
        }
        s.battlePhase = phase;
        if (championName != null) s.champion = championName;
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/phase", Map.of(
            "phase",    s.battlePhase,
            "genre",    s.activeGenreName != null ? s.activeGenreName : "",
            "champion", s.champion != null ? s.champion : ""
        ));
        persistActiveState(eventName);
    }

    public Map<String, Object> getOverlayConfig() {
        return getOverlayConfig(resolveEvent(null));
    }

    public Map<String, Object> getOverlayConfig(String eventName) {
        EventBattleState s = stateFor(eventName);
        Map<String, Object> cfg = new HashMap<>(s.overlayConfig);
        cfg.put("logoUrl", s.logoUrl);
        return cfg;
    }

    private void broadcastOverlayConfig(String eventName) {
        messagingTemplate.convertAndSend(
            "/topic/battle/" + eventName + "/overlay-config",
            getOverlayConfig(eventName)
        );
    }

    public void setOverlayConfigService(String eventName, SetOverlayConfigDto dto) {
        EventBattleState s = stateFor(eventName);
        Map<String, Object> newConfig = new HashMap<>();
        newConfig.put("showImages", dto.isShowImages());
        newConfig.put("leftColor",  dto.getLeftColor());
        newConfig.put("rightColor", dto.getRightColor());
        s.overlayConfig = newConfig;
        broadcastOverlayConfig(eventName);
    }

    public String uploadLogoService(String eventName, MultipartFile file) throws IOException {
        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
            ? original.substring(original.lastIndexOf('.'))
            : ".png";
        String safeEvent = eventName.replaceAll("[^a-zA-Z0-9_-]", "_");
        String filename = "__logo_" + safeEvent + ext;

        Path uploadDir = Paths.get("uploads");
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);
        Path dest = uploadDir.resolve(filename).normalize();
        if (!dest.startsWith(uploadDir.normalize())) throw new IOException("Invalid path");
        Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);

        EventBattleState s = stateFor(eventName);
        s.logoUrl = "/api/v1/battle/uploads/" + filename;
        persistActiveState(eventName);
        broadcastOverlayConfig(eventName);
        return s.logoUrl;
    }

    public void deleteLogoService(String eventName) throws IOException {
        EventBattleState s = stateFor(eventName);
        if (s.logoUrl != null) {
            String filename = s.logoUrl.substring(s.logoUrl.lastIndexOf('/') + 1);
            Path file = Paths.get("uploads").resolve(filename).normalize();
            if (file.startsWith(Paths.get("uploads").normalize())) {
                Files.deleteIfExists(file);
            }
        }
        s.logoUrl = null;
        persistActiveState(eventName);
        broadcastOverlayConfig(eventName);
    }

    public void broadcastChampionReveal(String eventName, ChampionRevealDto dto) {
        EventBattleState s = stateFor(eventName);
        if (dto.isDismiss()) {
            s.champion = null;
            persistActiveState(eventName);
            messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/champion-reveal",
                Map.of("dismiss", true));
        } else {
            s.champion = dto.getChampionName() != null ? dto.getChampionName() : "";
            persistActiveState(eventName);
            messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/champion-reveal", Map.of(
                "dismiss",      false,
                "genreName",    dto.getGenreName()    != null ? dto.getGenreName()    : "",
                "championName", s.champion
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
    public void setResolvedParticipants(String eventName, String genreName, List<String> participants) {
        try {
            BattleGenreState st = battleGenreStateRepository
                .findByEventNameAndGenreName(eventName, genreName)
                .orElse(new BattleGenreState());
            st.setEventName(eventName);
            st.setGenreName(genreName);
            st.setResolvedParticipantsJson(
                participants != null ? objectMapper.writeValueAsString(participants) : null);
            st.setUpdatedAt(LocalDateTime.now());
            battleGenreStateRepository.save(st);
            EventBattleState s = stateFor(eventName);
            if (genreName != null && genreName.equals(s.activeGenreName)) {
                broadcastStateSnapshot(eventName);
            }
        } catch (Exception e) {
            System.err.println("Failed to save resolved participants: " + e.getMessage());
        }
    }

    @Transactional
    public void switchActiveGenreService(SetActiveGenreDto dto) {
        if (activeEventName != null) persistActiveState(activeEventName);

        BattleActiveGenre active = battleActiveGenreRepository.findById(1)
            .orElse(new BattleActiveGenre(1, null, null));
        active.setEventName(dto.getEventName());
        active.setGenreName(dto.getGenreName());
        battleActiveGenreRepository.save(active);
        activeEventName = dto.getEventName();
        loadGenreStateIntoMemory(activeEventName, dto.getGenreName());
        broadcastStateSnapshot(activeEventName);

        EventBattleState s = stateFor(activeEventName);
        synchronized (s.judges) {
            messagingTemplate.convertAndSend("/topic/battle/" + activeEventName + "/judges",
                Map.of("judges", new ArrayList<>(s.judges)));
        }
        messagingTemplate.convertAndSend("/topic/battle/" + activeEventName + "/phase", Map.of(
            "phase", s.battlePhase,
            "genre", s.activeGenreName != null ? s.activeGenreName : ""
        ));
    }

    public Map<String, Object> getBattleStateService(String eventName) {
        EventBattleState s = stateFor(eventName);
        if (s.activeGenreName == null) return new HashMap<>();
        Map<String, Object> state = new HashMap<>();
        state.put("eventName",      eventName);
        state.put("genreName",      s.activeGenreName);
        state.put("genreFormat",    s.genreFormat);
        state.put("bracket",        s.bracketState);
        state.put("currentRoundIndex", s.currentRoundIndex);
        Map<String, Object> pair = new HashMap<>();
        pair.put("left",         s.currentPair.getLeftBattler().getName());
        pair.put("leftMembers",  s.currentPair.getLeftBattler().getMembers());
        pair.put("right",        s.currentPair.getRightBattler().getName());
        pair.put("rightMembers", s.currentPair.getRightBattler().getMembers());
        pair.put("isFinal",      s.currentIsFinal);
        state.put("currentPair", pair);
        state.put("battlePhase", s.battlePhase);
        state.put("champion",    s.champion);
        String resolvedJson = null;
        if (s.activeGenreName != null) {
            var st = battleGenreStateRepository
                .findByEventNameAndGenreName(eventName, s.activeGenreName).orElse(null);
            if (st != null) resolvedJson = st.getResolvedParticipantsJson();
        }
        state.put("resolvedParticipants", resolvedJson != null ? resolvedJson : "");
        synchronized (s.judges) {
            state.put("judges", new ArrayList<>(s.judges));
        }
        if (!s.battlers.isEmpty()) state.put("smokeBattlers", new ArrayList<>(s.battlers));
        if (s.lastTimerPayload != null) {
            Map<String, Object> timer = new HashMap<>(s.lastTimerPayload);
            if (Boolean.TRUE.equals(timer.get("running"))) {
                long elapsedSec = (System.currentTimeMillis() - s.timerLastUpdated) / 1000;
                int adjusted = Math.max(0, ((Number) timer.getOrDefault("timeLeft", 0)).intValue() - (int) elapsedSec);
                timer.put("timeLeft", adjusted);
                if (adjusted <= 0) { timer.put("running", false); timer.put("timeLeft", 0); }
            }
            state.put("timer", timer);
        }
        if (s.lastFormatTimerPayload != null) {
            Map<String, Object> ft = new HashMap<>(s.lastFormatTimerPayload);
            if (Boolean.TRUE.equals(ft.get("running"))) {
                long elapsedSec = (System.currentTimeMillis() - s.formatTimerLastUpdated) / 1000;
                int adjusted = Math.max(0, ((Number) ft.getOrDefault("timeLeft", 0)).intValue() - (int) elapsedSec);
                ft.put("timeLeft", adjusted);
                if (adjusted <= 0) { ft.put("running", false); ft.put("timeLeft", 0); ft.put("expired", true); }
            }
            state.put("formatTimer", ft);
        }
        return state;
    }

    public String getActiveEventName() { return activeEventName; }
    public String getActiveGenreName() {
        return stateFor(resolveEvent(null)).activeGenreName;
    }

    public Map<String, Object> getGenreStateFromDbService(String eventName, String genreName) {
        Optional<BattleGenreState> stateOpt =
            battleGenreStateRepository.findByEventNameAndGenreName(eventName, genreName);
        if (stateOpt.isEmpty()) return new HashMap<>();
        BattleGenreState db = stateOpt.get();

        String genreFormat = null;
        Event ev = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (ev != null) {
            genreFormat = eventGenreRepo.findByEventAndName(ev, genreName)
                .map(eg -> eg.getFormat()).orElse(null);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("genreName",   genreName);
        result.put("genreFormat", genreFormat);
        result.put("battlePhase", db.getBattlePhase() != null ? db.getBattlePhase() : "IDLE");
        result.put("champion",    db.getChampion());
        result.put("currentRoundIndex", db.getCurrentRoundIndex() != null ? db.getCurrentRoundIndex() : 0);

        try {
            // bracket
            Map<String, Object> bracket = new HashMap<>();
            if (db.getBracketJson() != null) {
                bracket = objectMapper.readValue(db.getBracketJson(), new TypeReference<Map<String, Object>>(){});
            }
            if (db.getTopSize() != null) bracket.put("topSize", db.getTopSize());
            result.put("bracket", bracket);

            // currentPair
            Map<String, Object> pair = new HashMap<>();
            pair.put("left",   db.getCurrentPairLeft()  != null ? db.getCurrentPairLeft()  : "");
            pair.put("right",  db.getCurrentPairRight() != null ? db.getCurrentPairRight() : "");
            pair.put("isFinal", Boolean.TRUE.equals(db.getIsFinal()));
            pair.put("leftMembers",  db.getCurrentPairLeftMembers()  != null
                ? objectMapper.readValue(db.getCurrentPairLeftMembers(),  new TypeReference<List<String>>(){})
                : new ArrayList<>());
            pair.put("rightMembers", db.getCurrentPairRightMembers() != null
                ? objectMapper.readValue(db.getCurrentPairRightMembers(), new TypeReference<List<String>>(){})
                : new ArrayList<>());
            result.put("currentPair", pair);

            // judges
            if (db.getJudgesJson() != null) {
                result.put("judges", objectMapper.readValue(db.getJudgesJson(),
                    new TypeReference<List<Object>>(){}));
            }

            // smoke battlers
            if (db.getSmokeListJson() != null) {
                result.put("smokeBattlers", objectMapper.readValue(db.getSmokeListJson(),
                    new TypeReference<List<Object>>(){}));
            }
        } catch (Exception e) {
            System.err.println("Failed to read genre state from DB: " + e.getMessage());
        }
        return result;
    }

    public void handleTimerPayload(String eventName, Map<String, Object> payload) {
        EventBattleState s = stateFor(eventName);
        s.lastTimerPayload = new HashMap<>(payload);
        s.timerLastUpdated = System.currentTimeMillis();
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/timer", payload);
    }

    public void rebroadcastTimer(String eventName, Object timerState) {
        if (timerState != null) {
            messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/timer", timerState);
        }
    }

    public void handleFormatTimerPayload(String eventName, Map<String, Object> payload) {
        EventBattleState s = stateFor(eventName);
        s.lastFormatTimerPayload = new HashMap<>(payload);
        s.formatTimerLastUpdated = System.currentTimeMillis();
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/format-timer", payload);
        persistFormatTimer(eventName);
    }

    private void persistFormatTimer(String eventName) {
        EventBattleState s = stateFor(eventName);
        if (activeEventName == null || s.activeGenreName == null || s.lastFormatTimerPayload == null) return;
        try {
            BattleGenreState st = battleGenreStateRepository
                .findByEventNameAndGenreName(eventName, s.activeGenreName).orElse(null);
            if (st == null) return;
            st.setFormatTimerJson(objectMapper.writeValueAsString(s.lastFormatTimerPayload));
            st.setUpdatedAt(LocalDateTime.now());
            battleGenreStateRepository.save(st);
        } catch (Exception e) {
            System.err.println("Failed to persist format timer: " + e.getMessage());
        }
    }

    private void persistActiveState(String eventName) {
        EventBattleState s = stateFor(eventName);
        if (s.activeGenreName == null) return;
        try {
            BattleGenreState st = battleGenreStateRepository
                .findByEventNameAndGenreName(eventName, s.activeGenreName)
                .orElse(new BattleGenreState());
            st.setEventName(eventName);
            st.setGenreName(s.activeGenreName);
            st.setBracketJson(s.bracketState != null ? objectMapper.writeValueAsString(s.bracketState) : null);
            if (s.bracketState instanceof Map) {
                Object ts = ((Map<?, ?>) s.bracketState).get("topSize");
                if (ts != null) {
                    try { st.setTopSize(Integer.parseInt(ts.toString())); }
                    catch (NumberFormatException ignored) {}
                }
            }
            st.setCurrentRoundIndex(s.currentRoundIndex);
            st.setCurrentPairLeft(s.currentPair.getLeftBattler().getName());
            st.setCurrentPairLeftMembers(
                objectMapper.writeValueAsString(s.currentPair.getLeftBattler().getMembers()));
            st.setCurrentPairRight(s.currentPair.getRightBattler().getName());
            st.setCurrentPairRightMembers(
                objectMapper.writeValueAsString(s.currentPair.getRightBattler().getMembers()));
            st.setIsFinal(s.currentIsFinal);
            st.setBattlePhase(s.battlePhase);
            st.setChampion(s.champion);
            st.setSmokeListJson(objectMapper.writeValueAsString(new ArrayList<>(s.battlers)));
            synchronized (s.judges) {
                st.setJudgesJson(objectMapper.writeValueAsString(new ArrayList<>(s.judges)));
            }
            if (s.lastFormatTimerPayload != null) {
                st.setFormatTimerJson(objectMapper.writeValueAsString(s.lastFormatTimerPayload));
            }
            st.setLogoUrl(s.logoUrl);
            st.setUpdatedAt(LocalDateTime.now());
            battleGenreStateRepository.save(st);
        } catch (Exception e) {
            System.err.println("Failed to persist battle state: " + e.getMessage());
        }
    }

    private void loadGenreStateIntoMemory(String eventName, String genreName) {
        EventBattleState s = stateFor(eventName);
        s.activeGenreName = genreName;
        s.genreFormat = null;
        if (eventName != null && genreName != null) {
            Event ev = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
            if (ev != null) {
                eventGenreRepo.findByEventAndName(ev, genreName)
                    .ifPresent(eg -> s.genreFormat = eg.getFormat());
            }
        }
        Optional<BattleGenreState> stateOpt =
            battleGenreStateRepository.findByEventNameAndGenreName(eventName, genreName);
        if (stateOpt.isEmpty()) { resetToDefaults(eventName); return; }
        BattleGenreState dbState = stateOpt.get();
        try {
            s.bracketState = dbState.getBracketJson() != null
                ? objectMapper.readValue(dbState.getBracketJson(), Map.class) : null;
            s.currentRoundIndex = dbState.getCurrentRoundIndex() != null ? dbState.getCurrentRoundIndex() : 0;
            s.currentPair.getLeftBattler().setName(
                dbState.getCurrentPairLeft() != null ? dbState.getCurrentPairLeft() : "");
            s.currentPair.getLeftBattler().setScore(0);
            s.currentPair.getLeftBattler().setMembers(dbState.getCurrentPairLeftMembers() != null
                ? objectMapper.readValue(dbState.getCurrentPairLeftMembers(), new TypeReference<List<String>>(){})
                : new ArrayList<>());
            s.currentPair.getRightBattler().setName(
                dbState.getCurrentPairRight() != null ? dbState.getCurrentPairRight() : "");
            s.currentPair.getRightBattler().setScore(0);
            s.currentPair.getRightBattler().setMembers(dbState.getCurrentPairRightMembers() != null
                ? objectMapper.readValue(dbState.getCurrentPairRightMembers(), new TypeReference<List<String>>(){})
                : new ArrayList<>());
            s.currentIsFinal = Boolean.TRUE.equals(dbState.getIsFinal());
            s.battlePhase = dbState.getBattlePhase() != null ? dbState.getBattlePhase() : "IDLE";
            s.champion = dbState.getChampion();
            s.battlers = dbState.getSmokeListJson() != null
                ? objectMapper.readValue(dbState.getSmokeListJson(), new TypeReference<List<Battler>>(){})
                : new ArrayList<>();
            synchronized (s.judges) {
                s.judges.clear();
                if (dbState.getJudgesJson() != null) {
                    List<BattleJudge> restored =
                        objectMapper.readValue(dbState.getJudgesJson(), new TypeReference<List<BattleJudge>>(){});
                    s.judges.addAll(restored);
                }
            }
            if (dbState.getFormatTimerJson() != null) {
                s.lastFormatTimerPayload = objectMapper.readValue(
                    dbState.getFormatTimerJson(), new TypeReference<Map<String, Object>>(){});
                s.formatTimerLastUpdated = System.currentTimeMillis();
            } else {
                s.lastFormatTimerPayload = null;
            }
            s.logoUrl = dbState.getLogoUrl();
        } catch (Exception e) {
            System.err.println("Failed to load genre state from DB: " + e.getMessage());
            resetToDefaults(eventName);
        }
    }

    private void resetToDefaults(String eventName) {
        EventBattleState s = stateFor(eventName);
        s.bracketState = null;
        s.currentRoundIndex = 0;
        s.currentPair.getLeftBattler().setName("");
        s.currentPair.getLeftBattler().setScore(0);
        s.currentPair.getLeftBattler().setMembers(new ArrayList<>());
        s.currentPair.getRightBattler().setName("");
        s.currentPair.getRightBattler().setScore(0);
        s.currentPair.getRightBattler().setMembers(new ArrayList<>());
        s.currentIsFinal = false;
        s.battlePhase = "IDLE";
        s.champion = null;
        s.battlers = new ArrayList<>();
        synchronized (s.judges) { s.judges.clear(); }
        s.lastFormatTimerPayload = null;
        s.formatTimerLastUpdated = 0;
    }

    private void broadcastStateSnapshot(String eventName) {
        Map<String, Object> state = getBattleStateService(eventName);
        messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/state", state);
        if (state.containsKey("timer")) {
            messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/timer", state.get("timer"));
        }
        if (state.containsKey("formatTimer")) {
            messagingTemplate.convertAndSend("/topic/battle/" + eventName + "/format-timer", state.get("formatTimer"));
        }
    }

    public static class EventBattleState {
        Object bracketState = null;
        Integer currentRoundIndex = 0;
        List<Battler> battlers = new ArrayList<>();
        String battlePhase = "IDLE";
        boolean currentIsFinal = false;
        BattlePair currentPair;
        final List<BattleJudge> judges = Collections.synchronizedList(new ArrayList<>());
        String activeGenreName;
        String genreFormat;
        String champion = null;
        Map<String, Object> lastTimerPayload = null;
        long timerLastUpdated = 0;
        Map<String, Object> lastFormatTimerPayload = null;
        long formatTimerLastUpdated = 0;
        Map<String, Object> overlayConfig = new HashMap<>(Map.of(
            "showImages", true,
            "leftColor",  "#dc2626",
            "rightColor", "#2563eb"
        ));
        String logoUrl = null;

        EventBattleState() {
            currentPair = new BattlePair();
            currentPair.setLeftBattler(new Battler());
            currentPair.setRightBattler(new Battler());
        }
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

    public static class BattlePair {
        private Battler leftBattler;
        private Battler rightBattler;
        public Battler getLeftBattler() { return leftBattler; }
        public Battler getRightBattler() { return rightBattler; }
        public void setLeftBattler(Battler leftBattler) { this.leftBattler = leftBattler; }
        public void setRightBattler(Battler rightBattler) { this.rightBattler = rightBattler; }
    }
}
