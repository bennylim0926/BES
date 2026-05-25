package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SetBattlePhaseDto {
    @NotBlank @Size(max = 50)
    private String phase;

    public String getPhase() {
        return phase;
    }
}
