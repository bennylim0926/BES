package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddDivisionDto {
    @NotBlank @Size(max = 255)
    public String name;
    public String format;
    public Long genreId;
}
