package com.example.BES.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemoStartRequestDto {
    private String passcode;
    private String role; // "EMCEE", "JUDGE", or "HELPER"
}
