package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RedeemTokenDto {
    @NotBlank
    private String tokenId;
}
