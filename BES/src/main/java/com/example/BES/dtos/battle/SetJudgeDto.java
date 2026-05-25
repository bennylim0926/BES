package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotNull;

public class SetJudgeDto {
    @NotNull
    private Long id;

    public Long getId() {
        return id;
    }
}
