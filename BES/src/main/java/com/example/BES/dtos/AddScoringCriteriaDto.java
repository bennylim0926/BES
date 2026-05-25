package com.example.BES.dtos;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AddScoringCriteriaDto {
    @NotBlank @Size(max = 255)
    public String eventName;
    @Size(max = 255)
    public String genreName;  // null or blank = event-level
    @NotBlank @Size(max = 255)
    public String name;
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0")
    public Double weight;
    @NotNull
    public Integer displayOrder;
}
