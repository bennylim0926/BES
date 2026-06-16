package com.example.BES.dtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AddScoringCriteriaDto {
    @Size(max = 255)
    public String eventName;   // set from @PathVariable after @Valid — do not add @NotBlank here
    @Size(max = 255)
    public String categoryName;   // null or blank = event-level
    @NotBlank @Size(max = 255)
    public String name;
    @DecimalMin("0.0")
    public Double weight;      // optional; no upper cap (supports multipliers like 2×, 3×)
    @NotNull
    public Integer displayOrder;
}
