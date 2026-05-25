package com.example.BES.dtos;

import java.util.List;
import java.util.Map;

public class AddGenreToEventDto {
    public String eventName;
    public List<String> genreName;
    public Map<String, String> genreFormats; // genreName → format (e.g. "2v2")
}
