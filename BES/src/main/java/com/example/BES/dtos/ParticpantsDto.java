package com.example.BES.dtos;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParticpantsDto {
    private String name;
    private String email;
    private List<String> categories;
    private Boolean paymentStatus;
    private String residency; 
}
