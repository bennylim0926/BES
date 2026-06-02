package com.example.BES.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_genre_participant_member")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventGenreParticipantMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "event_id",        referencedColumnName = "event_id"),
        @JoinColumn(name = "event_genre_id",  referencedColumnName = "event_genre_id"),
        @JoinColumn(name = "participant_id",  referencedColumnName = "participant_id")
    })
    private EventGenreParticipant eventGenreParticipant;

    @Column(name = "member_name", nullable = false)
    private String memberName;

    public EventGenreParticipantMember(EventGenreParticipant egp, String memberName) {
        this.eventGenreParticipant = egp;
        this.memberName = memberName;
    }
}
