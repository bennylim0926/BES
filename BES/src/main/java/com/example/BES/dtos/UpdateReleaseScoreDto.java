package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;

public class UpdateReleaseScoreDto {
    @NotBlank
    public String eventName;

    public boolean releaseScore;
}
