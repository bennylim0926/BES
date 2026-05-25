package com.example.BES.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_participant_team_member")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventParticipantTeamMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_participant_id", nullable = false)
    private EventParticipant eventParticipant;

    @Column(name = "member_name", nullable = false)
    private String memberName;

    public EventParticipantTeamMember(EventParticipant eventParticipant, String memberName) {
        this.eventParticipant = eventParticipant;
        this.memberName = memberName;
    }
}
