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

    @Modifying
    @Transactional
    @Query("DELETE FROM AuditionFeedback f WHERE f.eventCategoryParticipant.event.eventId = :eventId")
    int deleteByEventId(@Param("eventId") Long eventId);
}
