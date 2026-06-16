package com.example.BES.respositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.Judge;
import com.example.BES.models.Score;

import jakarta.transaction.Transactional;

@Repository
public interface ScoreRepo extends JpaRepository<Score, Long>{
    Optional<Score> findByEventCategoryParticipantAndJudge(EventCategoryParticipant eventGenreParticipant, Judge j);

    @Modifying
    @Transactional
    void deleteByEventCategoryParticipantAndJudge(EventCategoryParticipant eventGenreParticipant, Judge j);
    Optional<Score> findByEventCategoryParticipantAndJudgeAndAspect(EventCategoryParticipant eventGenreParticipant, Judge j, String aspect);
    List<Score> findByEventCategoryParticipant(EventCategoryParticipant eventGenreParticipant);
    
    @Query(value =
    """
    SELECT s
    FROM Score s
    WHERE s.eventCategoryParticipant.event.eventName = :eventName
    """)
    List<Score> findbyEvent(@Param("eventName") String eventName);

    @Modifying
    @Transactional
    @Query(value =
    """
    DELETE
    FROM Score s
    WHERE s.eventCategoryParticipant.eventCategory.id = :eventCategoryId
    AND
    s.eventCategoryParticipant.event.id = :eventId
    """)
    int deleteByEventIdAndCategoryId(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId);

    @Modifying
    @Transactional
    @Query(value =
    """
    DELETE
    FROM Score s
    WHERE
    s.eventCategoryParticipant.event.id = :eventId
    """)
    int deleteByEventId(@Param("eventId") Long eventId);

    @Modifying
    @Transactional
    @Query("""
        DELETE FROM Score s
        WHERE s.eventCategoryParticipant.event.eventName = :eventName
        AND s.eventCategoryParticipant.eventCategory.name = :categoryName
        AND s.judge.name = :judgeName
    """)
    int deleteByEventNameAndCategoryNameAndJudgeName(
        @Param("eventName") String eventName,
        @Param("categoryName") String categoryName,
        @Param("judgeName") String judgeName
    );
}
