package com.example.BES.dtos.battle;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SetBattleScoreDto {
    @JsonProperty("isFinal")
    private boolean isFinal;
    public boolean isFinal() { return isFinal; }
}
