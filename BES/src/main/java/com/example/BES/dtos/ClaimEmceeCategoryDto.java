package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;

public class ClaimEmceeCategoryDto {

    @NotBlank
    private String eventName;

    @NotBlank
    private String categoryName;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
}
