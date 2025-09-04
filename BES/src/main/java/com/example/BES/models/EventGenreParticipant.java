package com.example.BES.models;

import java.util.List;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGenreParticipant {
    @EmbeddedId
    private EventGenreParticipantId id;
    
    @ManyToOne
    @MapsId("eventId")
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne
    @MapsId("genreId")
    @JoinColumn(name = "genre_id")
    private Genre genre;

    @ManyToOne
    @MapsId("participantId")
    @JoinColumn(name = "participant_id")
    private Participant participant;

    @ManyToOne
    @MapsId("eventGenreId")
    @JoinColumns({
        @JoinColumn(name = "event_id", referencedColumnName = "event_id"),
        @JoinColumn(name = "genre_id", referencedColumnName = "genre_id")
    })
    private EventGenre eventGenre;

    private Integer auditionNumber;
    @ManyToOne
    @JoinColumn(name = "judge_id", nullable = true)
    private Judge judge;

    // NEW: multiple aspect scores
    @OneToMany(mappedBy = "eventGenreParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Score> scores;
}