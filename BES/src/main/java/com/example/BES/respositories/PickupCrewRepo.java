package com.example.BES.respositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.models.PickupCrew;

@Repository
public interface PickupCrewRepo extends JpaRepository<PickupCrew, Long> {
    List<PickupCrew> findByEventAndEventCategory(Event event, EventCategory eventCategory);
    List<PickupCrew> findByEvent(Event event);

    @Query("SELECT COUNT(m) FROM PickupCrewMember m WHERE m.crew.event = :event AND m.crew.eventCategory = :eventCategory AND m.participant.participantId = :participantId")
    long countMemberInEventCategory(@Param("event") Event event, @Param("eventCategory") EventCategory eventCategory, @Param("participantId") Long participantId);

    void deleteByEvent(Event event);
}
