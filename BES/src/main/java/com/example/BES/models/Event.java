package com.example.BES.models;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;
    private String eventName;
    private boolean paymentRequired = false;

    @Column(length = 4)
    private String accessCode = "0000";

    @Column(name = "judging_mode", length = 10)
    private String judgingMode = "SOLO";

    @Column(name = "results_released")
    private boolean resultsReleased = false;

    @Column(name = "feedback_enabled")
    private boolean feedbackEnabled = true;

    @OneToMany(mappedBy = "event")
    private List<EventGenre> eventGenres;

    @OneToMany(mappedBy = "event")
    private List<EventParticipant> eventParticipants;
}
