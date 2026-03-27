package com.example.BES.dtos;

import java.util.List;

public class GetEventGenreParticipantDto {
    public String eventName;
    public String participantName;
    public String genreName;
    public String judgeName;
    public Integer auditionNumber;
    public Boolean walkin;
    public Long participantId;
    public Long eventId;
    public Long genreId;
    public boolean emailSent;
    public String referenceCode;
    public List<String> memberNames; // non-null only for team-format EGPs
}
