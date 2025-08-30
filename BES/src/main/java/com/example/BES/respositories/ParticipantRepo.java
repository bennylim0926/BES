package com.example.BES.respositories;

import org.springframework.stereotype.Repository;
import com.example.BES.models.Participant;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface ParticipantRepo extends JpaRepository<Participant, Long>{
    Optional<Participant> findByParticipantName(String participantName);
}
