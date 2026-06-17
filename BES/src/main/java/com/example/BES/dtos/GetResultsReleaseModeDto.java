package com.example.BES.dtos;

public class GetResultsReleaseModeDto {
    public String eventName;
    public String mode;

    public GetResultsReleaseModeDto(String eventName, String mode) {
        this.eventName = eventName;
        this.mode = mode;
    }
}
