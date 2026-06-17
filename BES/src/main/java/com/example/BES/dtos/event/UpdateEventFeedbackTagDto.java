package com.example.BES.dtos.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateEventFeedbackTagDto {
    @NotBlank @Size(max = 255)
    private String label;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
