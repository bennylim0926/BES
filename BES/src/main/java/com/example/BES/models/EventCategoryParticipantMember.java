package com.example.BES.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_category_participant_member")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventCategoryParticipantMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "event_id",           referencedColumnName = "event_id"),
        @JoinColumn(name = "event_category_id",  referencedColumnName = "event_category_id"),
        @JoinColumn(name = "participant_id",     referencedColumnName = "participant_id")
    })
    private EventCategoryParticipant eventCategoryParticipant;

    @Column(name = "member_name", nullable = false)
    private String memberName;

    public EventCategoryParticipantMember(EventCategoryParticipant ecp, String memberName) {
        this.eventCategoryParticipant = ecp;
        this.memberName = memberName;
    }
}
