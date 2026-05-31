package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public class AddGenreToEventDto {
    @NotBlank @Size(max = 255)
    public String eventName;
    @NotEmpty
    public List<@NotBlank @Size(max = 255) String> genreName;
    public Map<String, String> genreFormats; // genreName -> format (e.g. "2v2")
    public Map<String, String> genreAliases; // genreName -> comma-separated sheet aliases
}
