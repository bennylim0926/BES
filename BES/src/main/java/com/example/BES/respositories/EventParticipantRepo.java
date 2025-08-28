package com.example.BES.respositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.EventParticipant;

@Repository
public interface EventParticipantRepo extends JpaRepository<EventParticipant, Long> {

}
