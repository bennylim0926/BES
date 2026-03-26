package com.example.BES.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "audition_feedback")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditionFeedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumns({
        @JoinColumn(name = "event_id", referencedColumnName = "event_id"),
        @JoinColumn(name = "genre_id", referencedColumnName = "genre_id"),
        @JoinColumn(name = "participant_id", referencedColumnName = "participant_id")
    })
    private EventGenreParticipant eventGenreParticipant;

    @ManyToOne
    @JoinColumn(name = "judge_id", nullable = false)
    private Judge judge;

    @ManyToMany
    @JoinTable(
        name = "audition_feedback_tag",
        joinColumns = @JoinColumn(name = "feedback_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<FeedbackTag> tags = new HashSet<>();

    private String note;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
