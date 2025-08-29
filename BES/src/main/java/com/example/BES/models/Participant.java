package com.example.BES.models;
import java.util.List;

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
public class Participant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long participantId;
    
    private String participantName;
    private String participantEmail;

    @OneToMany(mappedBy = "participant")
    private List<EventParticipant> eventParticipants;

    @OneToMany(mappedBy = "participant")
    private List<EventGenreParticipant> eventGenreParticipants;
}
