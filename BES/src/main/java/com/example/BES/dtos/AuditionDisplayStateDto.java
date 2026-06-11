package com.example.BES.dtos;

import java.util.List;

public class AuditionDisplayStateDto {

    public String eventName;
    public String genreName;
    public String mode;          // "SOLO" or "PAIR"
    public int currentRound;
    public int totalRounds;
    public List<Slot> currentSlots;
    public List<Slot> nextSlots;

    // Timer fields — null/0 when timer is not running
    public Long timerStartedAt;  // epoch ms
    public Integer timerDuration; // seconds
    public Boolean timerRunning;

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
