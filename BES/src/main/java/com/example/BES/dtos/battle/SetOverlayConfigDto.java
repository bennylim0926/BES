package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class SetOverlayConfigDto {

    @NotNull
    private Boolean showImages;

    @NotNull
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "leftColor must be a valid 6-digit hex color")
    private String leftColor;

    @NotNull
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "rightColor must be a valid 6-digit hex color")
    private String rightColor;
    private String eventName;

    @Pattern(regexp = "^(impact|hype)$", message = "animTheme must be 'impact' or 'hype'")
    private String animTheme;

    public String getEventName() { return eventName; }

    public Boolean isShowImages() { return showImages; }
    public String getLeftColor()  { return leftColor; }
    public String getRightColor() { return rightColor; }
    public String getAnimTheme()  { return animTheme; }
}
