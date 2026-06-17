package com.example.BES.respositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.models.ScoringCriteria;

@Repository
public interface ScoringCriteriaRepo extends JpaRepository<ScoringCriteria, Long> {

    @Query("SELECT sc FROM ScoringCriteria sc WHERE sc.event.eventName = :eventName AND sc.eventCategory IS NULL ORDER BY sc.displayOrder ASC")
    List<ScoringCriteria> findEventLevelByEventName(@Param("eventName") String eventName);

    @Query("SELECT sc FROM ScoringCriteria sc WHERE sc.event.eventName = :eventName AND sc.eventCategory.name = :categoryName ORDER BY sc.displayOrder ASC")
    List<ScoringCriteria> findByEventNameAndCategoryName(@Param("eventName") String eventName, @Param("categoryName") String categoryName);

    @Query("SELECT sc FROM ScoringCriteria sc WHERE sc.event = :event AND (sc.eventCategory = :category OR (:category IS NULL AND sc.eventCategory IS NULL))")
    List<ScoringCriteria> findByEventAndEventCategory(@Param("event") Event event, @Param("category") EventCategory category);
}
