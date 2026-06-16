package com.example.BES.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateParticipantScoreDto {
    @NotBlank @Size(max = 255)
    public String eventName;
    @NotBlank @Size(max = 255)
    public String categoryName;
    @NotBlank @Size(max = 255)
    public String judgeName;
    @NotBlank @Size(max = 255)
    public String participantName;
    @NotNull @DecimalMin("0.0") @DecimalMax("100.0")
    public Double score;
}
