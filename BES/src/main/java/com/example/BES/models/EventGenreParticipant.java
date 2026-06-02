package com.example.BES.models;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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
    @MapsId("eventGenreId")
    @JoinColumn(name = "event_genre_id")
    private EventGenre eventGenre;

    @ManyToOne
    @MapsId("participantId")
    @JoinColumn(name = "participant_id")
    private Participant participant;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "format")
    private String format;

    private Integer auditionNumber;

    @ManyToOne
    @JoinColumn(name = "judge_id", nullable = true)
    private Judge judge;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "eventGenreParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Score> scores;

    @Column(name = "team_name")
    private String teamName;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "eventGenreParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventGenreParticipantMember> members = new ArrayList<>();
}
