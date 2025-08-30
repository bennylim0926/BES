package com.example.BES.respositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;

@Repository
public interface EventGenreParticpantRepo extends JpaRepository<EventGenreParticipant, EventGenreParticipantId> {

}
