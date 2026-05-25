package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddEventDto {
    @NotBlank
    @Size(max = 255)
    public String eventName;
    public boolean paymentRequired;
    @Size(max = 255)
    public String accessCode;
}
