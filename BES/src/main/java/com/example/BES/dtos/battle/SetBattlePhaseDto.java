package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SetBattlePhaseDto {
    @NotBlank @Size(max = 50)
    private String phase;

    private String champion;
    private String eventName;

    public String getEventName() { return eventName; }

    public String getPhase() {
        return phase;
    }

    public String getChampion() {
        return champion;
    }

    public void setChampion(String champion) {
        this.champion = champion;
    }
}
