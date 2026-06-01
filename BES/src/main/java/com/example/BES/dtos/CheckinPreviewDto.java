package com.example.BES.dtos;

import java.util.List;

public class CheckinPreviewDto {
    public Long participantId;
    public String name;
    public String refCode;
    public List<String> memberNames;
    public List<GenreEntry> genres;

    public static class GenreEntry {
        public String genreName;
        public Integer auditionNumber;
    }
}
