package com.example.BES.models;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGenreId implements Serializable{
    @Column(name = "event_id")
    private Long eventId;
    @Column(name = "genre_id")
    private Long genreId;
}