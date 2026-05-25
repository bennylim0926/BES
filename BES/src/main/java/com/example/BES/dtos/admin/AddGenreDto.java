package com.example.BES.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AddGenreDto {
    @NotBlank @Size(max = 255)
    private String name;

    public String getName() {
        return name;
    }
}
