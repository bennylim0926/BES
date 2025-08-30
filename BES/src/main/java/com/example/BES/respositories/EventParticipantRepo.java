package com.example.BES.respositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;
import com.example.BES.models.EventParticipant;
import com.example.BES.models.Participant;

@Repository
public interface EventParticipantRepo extends JpaRepository<EventParticipant, Long> {
    Optional<EventParticipant> findByEventAndParticipant(Event event_id, Participant participant_id);
}
