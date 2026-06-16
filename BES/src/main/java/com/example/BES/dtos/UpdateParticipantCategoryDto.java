package com.example.BES.dtos;

import java.util.List;

public class UpdateParticipantCategoryDto {
    public Long participantId;
    public Long eventId;
    public String categoryName;
    public String entryMode;       // "team" or "solo" (optional)
    public String teamName;        // optional — required when entryMode=team
    public List<String> teamMembers; // optional — additional member names
}
