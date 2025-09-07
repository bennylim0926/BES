package com.example.BES.respositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.Judge;
import com.example.BES.models.Score;

@Repository
public interface ScoreRepo extends JpaRepository<Score, Long>{
    Optional<Score> findByEventGenreParticipantAndJudge(EventGenreParticipant eventGenreParticipant, Judge j);
    
    @Query(value =
    """
    SELECT s
    FROM Score s
    WHERE s.eventGenreParticipant.event.eventName = :eventName
    """)
    List<Score> findbyEvent(@Param("eventName") String eventName);
}
