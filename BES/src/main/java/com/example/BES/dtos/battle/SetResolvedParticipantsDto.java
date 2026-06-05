package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class SetResolvedParticipantsDto {

    @NotBlank
    private String eventName;

    @NotBlank
    private String genreName;

    private List<String> participants;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getGenreName() { return genreName; }
    public void setGenreName(String genreName) { this.genreName = genreName; }
    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }
}
