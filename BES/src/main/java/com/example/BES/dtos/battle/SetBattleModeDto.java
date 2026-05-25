package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SetBattleModeDto {
    @NotBlank @Size(max = 50)
    private String mode;

    public String getMode() {
        return mode;
    }
}
