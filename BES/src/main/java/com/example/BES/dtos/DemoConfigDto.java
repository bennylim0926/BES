package com.example.BES.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemoConfigDto {
    private boolean demoEnabled;
    private String passcode;
    private Boolean regeneratePasscode;  // set to true to trigger regeneration on POST
    private int activeSandboxes;
}
