package com.example.BES.dtos;

public class GetParticipatnScoreDto {
    public String participantName;
    public String eventName;
    public String genreName;
    public String judgeName;
    public Double score;
    public String aspect;  // empty string for legacy single scores; criterion name for multi-criteria
}
