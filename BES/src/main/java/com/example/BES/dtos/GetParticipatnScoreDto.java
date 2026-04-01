package com.example.BES.dtos;

public class GetParticipatnScoreDto {
    public Long participantId;
    public String participantName;
    public String eventName;
    public String genreName;
    public String judgeName;
    public Double score;
    public String aspect;  // empty string for legacy single scores; criterion name for multi-criteria
    public String format;  // "2v2"/"3v3" for team entries; null for solo pickup
}
