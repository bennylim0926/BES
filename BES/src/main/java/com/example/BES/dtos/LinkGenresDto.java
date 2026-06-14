package com.example.BES.dtos;

import java.util.List;

import jakarta.validation.constraints.NotNull;

public class LinkGenresDto {
    @NotNull
    public List<Long> genreIds;
}
