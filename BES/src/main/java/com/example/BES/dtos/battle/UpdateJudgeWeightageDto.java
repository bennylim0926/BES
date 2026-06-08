package com.example.BES.dtos.battle;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class UpdateJudgeWeightageDto {
    @NotNull
    private Long id;

    @Min(1)
    private int weightage;
    private String eventName;

    public String getEventName() { return eventName; }

    public Long getId() { return id; }
    public int getWeightage() { return weightage; }
}
