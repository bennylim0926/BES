package com.example.BES.dtos;

import java.util.List;

public class GetCheckinListDto {
    public Long participantId;
    public Long eventId;
    public String label;
    public List<GenreStatus> genres;

    public static class GenreStatus {
        public String genreName;
        public Integer auditionNumber;
    }
}
