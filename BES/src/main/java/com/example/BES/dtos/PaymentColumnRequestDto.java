package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PaymentColumnRequestDto {
    @NotBlank @Size(max = 255)
    public String fileId;
}
