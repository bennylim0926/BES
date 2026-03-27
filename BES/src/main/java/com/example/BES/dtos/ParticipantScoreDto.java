package com.example.BES.dtos;

import java.util.List;

public class ParticipantScoreDto {
    public String participantName;
    public Double score;                  // used in single-score (legacy) mode
    public List<AspectScoreDto> aspects;  // used in multi-criteria mode; takes priority over score
}
