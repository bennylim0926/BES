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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "event_category_participant")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventCategoryParticipant {
    @EmbeddedId
    private EventCategoryParticipantId id;

    @ManyToOne
    @MapsId("eventId")
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne
    @MapsId("eventCategoryId")
    @JoinColumn(name = "event_category_id")
    private EventCategory eventCategory;

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
    @OneToMany(mappedBy = "eventCategoryParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Score> scores;

    @Column(name = "team_name")
    private String teamName;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "eventCategoryParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventCategoryParticipantMember> members = new ArrayList<>();
}
