package com.example.BES.respositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.EventParticipant;
import com.example.BES.models.EventParticipantTeamMember;

@Repository
public interface EventParticipantTeamMemberRepo extends JpaRepository<EventParticipantTeamMember, Long> {
    void deleteByEventParticipant(EventParticipant eventParticipant);
}
