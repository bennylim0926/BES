package com.example.BES.respositories;

import com.example.BES.models.AuditionFeedback;
import com.example.BES.models.EventGenreParticipant;
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
    Optional<AuditionFeedback> findByEventGenreParticipantAndJudge(EventGenreParticipant egp, Judge judge);
    List<AuditionFeedback> findByEventGenreParticipant(EventGenreParticipant egp);

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM AuditionFeedback f
        WHERE f.eventGenreParticipant IN (
            SELECT e FROM EventGenreParticipant e
            WHERE LOWER(e.event.eventName) = LOWER(:eventName)
              AND LOWER(e.eventGenre.name) = LOWER(:genreName)
        )
        AND LOWER(f.judge.name) = LOWER(:judgeName)
        """)
    void deleteByEventNameAndGenreNameAndJudgeName(
        @Param("eventName") String eventName,
        @Param("genreName") String genreName,
        @Param("judgeName") String judgeName);
}
