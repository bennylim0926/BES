package com.example.BES.dtos.battle;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class SetVoteDto {
    @NotNull
    private Long id;
    @NotNull @Min(-3) @Max(1)
    private Integer vote;
    
    public Long getId() {
        return id;
    }
    public Integer getVote() {
        return vote;
    }
}
