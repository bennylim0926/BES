package com.example.BES.dtos;

public class GetJudgingModeDto {
    public String eventName;
    public String judgingMode;

    public GetJudgingModeDto(String eventName, String judgingMode) {
        this.eventName = eventName;
        this.judgingMode = judgingMode;
    }
}
