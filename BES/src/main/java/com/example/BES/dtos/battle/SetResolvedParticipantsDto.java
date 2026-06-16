package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class SetResolvedParticipantsDto {

    @NotBlank
    private String eventName;

    @NotBlank
    private String categoryName;

    private List<String> participants;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }
}
