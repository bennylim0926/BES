package com.example.BES.respositories;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.EventCategoryParticipantMember;

public interface EventCategoryParticipantMemberRepo
        extends JpaRepository<EventCategoryParticipantMember, Long> {
    boolean existsByEventCategoryParticipantAndMemberName(EventCategoryParticipant ecp, String memberName);
    void deleteByEventCategoryParticipant(EventCategoryParticipant ecp);
}
