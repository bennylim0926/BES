package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class AddGenreToEventDto {
    @NotBlank @Size(max = 255)
    public String eventName;
    @NotEmpty
    public List<Division> divisions;

    public static class Division {
        @NotBlank @Size(max = 255)
        public String name;
        public String format;
        public Long genreId;
    }
}
