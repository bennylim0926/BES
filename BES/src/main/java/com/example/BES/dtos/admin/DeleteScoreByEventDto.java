package com.example.BES.dtos.admin;

public class DeleteScoreByEventDto {
    private Long event_id;
    private Long genre_id;

    public Long getGenre_id() {
        return genre_id;
    }

    public Long getEvent_id() {
        return event_id;
    }
}
