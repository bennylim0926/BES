package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class CreatePickupCrewDto {
    @NotBlank @Size(max = 255)
    public String eventName;
    @NotBlank @Size(max = 255)
    public String genreName;
    @NotBlank @Size(max = 255)
    public String crewName;
    @NotEmpty
    public List<Long> memberParticipantIds;
}
