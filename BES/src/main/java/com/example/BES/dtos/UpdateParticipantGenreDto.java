package com.example.BES.dtos;

import java.util.List;

public class UpdateParticipantGenreDto {
    public Long participantId;
    public Long eventId;
    public String genreName;
    public String entryMode;       // "team" or "solo" (optional)
    public String teamName;        // optional — required when entryMode=team
    public List<String> teamMembers; // optional — additional member names
}
