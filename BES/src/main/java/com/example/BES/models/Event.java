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

    @Column(name = "judging_mode", length = 10)
    private String judgingMode = "SOLO";

    @Column(name = "results_released")
    private boolean resultsReleased = false;

    @Column(name = "feedback_enabled")
    private boolean feedbackEnabled = true;

    @Column(name = "anim_theme", length = 32)
    private String animTheme = "impact";

    @OneToMany(mappedBy = "event")
    private List<EventCategory> eventCategories;

    @OneToMany(mappedBy = "event")
    private List<EventParticipant> eventParticipants;
}
