package com.example.BES.dtos;

import java.util.List;

public class GetPickupCrewDto {
    public Long id;
    public String crewName;
    public List<MemberDto> members;
    public Double avgScore;
    public Double leaderScore;  // avg score of the first (leader) member only

    public static class MemberDto {
        public Long participantId;
        public String displayName;
    }
}
