package com.example.BES.respositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;
import com.example.BES.models.Genre;
import com.example.BES.models.PickupCrew;

@Repository
public interface PickupCrewRepo extends JpaRepository<PickupCrew, Long> {
    List<PickupCrew> findByEventAndGenre(Event event, Genre genre);

    @Query("SELECT COUNT(m) FROM PickupCrewMember m WHERE m.crew.event = :event AND m.crew.genre = :genre AND m.participant.participantId = :participantId")
    long countMemberInEventGenre(@Param("event") Event event, @Param("genre") Genre genre, @Param("participantId") Long participantId);
}
