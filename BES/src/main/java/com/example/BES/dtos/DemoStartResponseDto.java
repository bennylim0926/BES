package com.example.BES.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DemoStartResponseDto {
    private boolean authenticated;
    private String role;
    private Long eventId;
    private String eventName;
    private Long judgeId;
    private String judgeName;
}
