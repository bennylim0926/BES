package com.example.BES.dtos.battle;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SetBattlerPairDto {
    @NotBlank @Size(max = 255)
    private String leftBattler;
    @NotBlank @Size(max = 255)
    private String rightBattler;
    @JsonProperty("isFinal")
    private boolean isFinal;

    public String getLeftBattler() {
        return leftBattler;
    }
    public String getRightBattler() {
        return rightBattler;
    }
    public boolean isFinal() {
        return isFinal;
    }
}
