package com.example.BES.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public class AddCategoryToEventDto {
    @NotBlank @Size(max = 255)
    public String eventName;
    @NotEmpty
    public List<Category> categories;

    public static class Category {
        @NotBlank @Size(max = 255)
        public String name;
        public String format;
        // Note: genreId removed — categories no longer link to global genre
    }
}
