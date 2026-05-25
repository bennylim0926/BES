package com.example.BES.dtos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UpdateAccessCodeDto {
    @NotNull
    public Long eventId;
    @Size(max = 255)
    public String newCode;
}
