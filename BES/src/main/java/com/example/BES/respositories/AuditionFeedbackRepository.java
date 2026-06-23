package com.example.BES.respositories;

import com.example.BES.models.AuditionFeedback;
import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.Judge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuditionFeedbackRepository extends JpaRepository<AuditionFeedback, Long> {
    Optional<AuditionFeedback> findByEventCategoryParticipantAndJudge(EventCategoryParticipant egp, Judge judge);
    List<AuditionFeedback> findByEventCategoryParticipant(EventCategoryParticipant egp);

    @Modifying
    @Transactional
    void deleteByEventCategoryParticipant(EventCategoryParticipant ecp);

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM AuditionFeedback f
        WHERE f.eventCategoryParticipant IN (
            SELECT e FROM EventCategoryParticipant e
            WHERE LOWER(e.event.eventName) = LOWER(:eventName)
              AND LOWER(e.eventCategory.name) = LOWER(:categoryName)
        )
        AND LOWER(f.judge.name) = LOWER(:judgeName)
        """)
    void deleteByEventNameAndCategoryNameAndJudgeName(
        @Param("eventName") String eventName,
        @Param("categoryName") String categoryName,
        @Param("judgeName") String judgeName);

    // Use an IN (SELECT ...) subquery rather than f.eventCategoryParticipant.event.eventId.
    // Hibernate emits a separate cleanup query for the audition_feedback_tag join table,
    // and the multi-hop nested path loses its alias binding in that cleanup ("missing
    // FROM-clause entry for table ecp1_0"). The subquery form keeps the inner alias
    // scoped correctly, matching the deleteByEventNameAndCategoryNameAndJudgeName pattern
    // above which works for the same reason.
    @Modifying
    @Transactional
    @Query("""
        DELETE FROM AuditionFeedback f
        WHERE f.eventCategoryParticipant IN (
            SELECT e FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId
        )
        """)
    int deleteByEventId(@Param("eventId") Long eventId);
}
