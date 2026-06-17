package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;

public class UpdateResultsReleaseModeDto {
    @NotBlank
    public String eventName;

    @NotBlank
    public String mode;
}
