package com.example.BES.models;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    private String residency;
    private String genre;
    private boolean paymentVerified = false;
    private boolean emailSent = false;
    private String screenshotUrl;

    @Column(name = "reference_code", unique = true)
    private String referenceCode;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "stage_name")
    private String stageName;

    @Column(name = "team_name")
    private String teamName;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "eventParticipant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EventParticipantTeamMember> teamMembers = new ArrayList<>();
}
