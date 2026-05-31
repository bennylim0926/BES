package com.example.BES.respositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantMember;

public interface EventGenreParticipantMemberRepo
        extends JpaRepository<EventGenreParticipantMember, Long> {
    boolean existsByEventGenreParticipantAndMemberName(EventGenreParticipant egp, String memberName);
}
