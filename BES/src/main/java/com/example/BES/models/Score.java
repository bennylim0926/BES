package com.example.BES.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Score {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aspect;   // e.g. "Musicality", "Creativity"
    private Double value;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToOne
    @JoinColumns({
        @JoinColumn(name = "event_id", referencedColumnName = "event_id"),
        @JoinColumn(name = "genre_id", referencedColumnName = "genre_id"),
        @JoinColumn(name = "participant_id", referencedColumnName = "participant_id")
    })
    private EventGenreParticipant eventGenreParticipant;

    @ManyToOne
    @JoinColumn(name = "judge_id")
    private Judge judge;
}
