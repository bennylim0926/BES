package com.example.BES.dtos.battle;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SetBattleScoreDto {
    @JsonProperty("isFinal")
    private boolean isFinal;
    private String eventName;

    public String getEventName() { return eventName; }

    public boolean isFinal() { return isFinal; }
}
