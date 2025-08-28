package com.example.BES.models;

import java.io.Serializable;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGenreParticipantId implements Serializable {
    private Long eventId;
    private Long genreId;
    private Long participantId;
}