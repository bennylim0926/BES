package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotBlank;

public class SetActiveGenreDto {

    @NotBlank
    private String eventName;

    @NotBlank
    private String genreName;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getGenreName() { return genreName; }
    public void setGenreName(String genreName) { this.genreName = genreName; }
}
