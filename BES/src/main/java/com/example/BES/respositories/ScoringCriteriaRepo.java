package com.example.BES.respositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BES.models.ScoringCriteria;

@Repository
public interface ScoringCriteriaRepo extends JpaRepository<ScoringCriteria, Long> {

    @Query("SELECT sc FROM ScoringCriteria sc WHERE sc.event.eventName = :eventName AND sc.genre IS NULL ORDER BY sc.displayOrder ASC")
    List<ScoringCriteria> findEventLevelByEventName(@Param("eventName") String eventName);

    @Query("SELECT sc FROM ScoringCriteria sc WHERE sc.event.eventName = :eventName AND sc.genre.genreName = :genreName ORDER BY sc.displayOrder ASC")
    List<ScoringCriteria> findByEventNameAndGenreName(@Param("eventName") String eventName, @Param("genreName") String genreName);
}
