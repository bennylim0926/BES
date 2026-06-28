package com.example.BES.dtos;

import java.util.List;
import java.util.Map;

public class AuditionDisplayStateDto {

    public String eventName;
    public String categoryName;
    public String mode;          // "SOLO" or "PAIR"
    public int currentRound;
    public int totalRounds;
    public List<Slot> currentSlots;
    public List<Slot> nextSlots;

    // Timer fields — null/0 when timer is not running
    public Long timerStartedAt;  // epoch ms
    public Integer timerDuration; // seconds
    public Boolean timerRunning;

    // Sticky baseline duration the emcee last picked (45/60/90).
    // Persists across round changes, timer reset, and timer expiry so the
    // OBS audition display can fall back to it instead of going blank.
    // Per (event, category), like the rest of this state.
    public Integer baselineDuration;

    // Judge assignment mode fields — only populated when judgeMode = true
    public boolean judgeMode;
    public int judgeCurrentRound;
    // Key = judge name, Value = the slot this judge is currently on (null if judge has no participant at this round)
    public Map<String, Slot> judgeSlots;

    // Display customisation (set per genre in EventDetails)
    public String roundLabel;   // e.g. "Preliminary Round"
    public String numberColor;  // hex color for audition number, e.g. "#f59e0b"
    public String pairSubMode;  // "SHOWCASE" or "BATTLE"

    // Default constructor for Jackson
    public AuditionDisplayStateDto() {}

    public static class Slot {
        public int auditionNumber;
        public String participantName;
        public List<String> memberNames;
        public boolean placeholder;

        public Slot() {}
    }
}
