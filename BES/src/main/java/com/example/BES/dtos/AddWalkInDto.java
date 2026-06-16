package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class AddWalkInDto {
    @NotBlank @Size(max = 255)
    public String name;
    @NotBlank @Size(max = 255)
    public String category;
    @NotBlank @Size(max = 255)
    public String eventName;
    @Size(max = 255)
    public String judgeName;
    public List<@NotBlank @Size(max = 255) String> teamMembers;
    @Size(max = 255)
    public String teamName;
    // "team" | "solo" — null treated as "team" for backwards compatibility
    public String entryMode;
}
