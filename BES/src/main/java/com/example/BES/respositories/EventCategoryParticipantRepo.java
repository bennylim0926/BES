package com.example.BES.respositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.models.EventCategoryParticipant;
import com.example.BES.models.EventCategoryParticipantId;

@Repository
public interface EventCategoryParticipantRepo extends JpaRepository<EventCategoryParticipant, EventCategoryParticipantId> {
    List<EventCategoryParticipant> findByEvent(Event event);
    List<EventCategoryParticipant> findByEventCategory(EventCategory eventCategory);

    @Query("SELECT DISTINCT e FROM EventCategoryParticipant e LEFT JOIN FETCH e.judge WHERE e.event = :event")
    List<EventCategoryParticipant> findByEventWithJudge(@Param("event") Event event);

    @Query(value = """
       SELECT e
       FROM EventCategoryParticipant e
       WHERE LOWER(e.event.eventName) = LOWER(:eventName)
         AND LOWER(e.eventCategory.name) = LOWER(:categoryName)
         AND LOWER(e.displayName) = LOWER(:participantName)
       """)
    Optional<EventCategoryParticipant> findByEventCategoryParticipant(@Param("eventName") String eventName,
                                                              @Param("categoryName") String categoryName,
                                                              @Param("participantName") String participantName);

    @Query(value = """
       SELECT COUNT(e)
       FROM EventCategoryParticipant e
       WHERE e.event.eventId = :eventId
         AND e.eventCategory.id = :eventCategoryId
       """)
    long countByEventIdAndEventCategoryId(@Param("eventId") Long eventId,
                                  @Param("eventCategoryId") Long eventCategoryId);

    @Query(value = """
       SELECT COUNT(e)
       FROM EventCategoryParticipant e
       WHERE e.event.eventId = :eventId
         AND e.eventCategory.id = :eventCategoryId
         AND e.judge.name = :name
       """)
    long countByEventIdAndEventCategoryIdAndJudge(@Param("eventId") Long eventId,
                                          @Param("eventCategoryId") Long eventCategoryId,
                                          @Param("name") String name);

    @Query("""
        SELECT e FROM EventCategoryParticipant e
        WHERE e.event.eventId = :eventId
          AND e.participant.participantId = :participantId
        """)
    List<EventCategoryParticipant> findByEventIdAndParticipantId(
        @Param("eventId") Long eventId,
        @Param("participantId") Long participantId);

    @Query(value = """
       SELECT e.auditionNumber
       FROM EventCategoryParticipant e
       WHERE e.event.eventId = :eventId
         AND e.eventCategory.id = :eventCategoryId
         AND e.auditionNumber IS NOT NULL
       """)
    List<Integer> findAuditionNumberByEventAndEventCategory(@Param("eventId") Long eventId,
                                                    @Param("eventCategoryId") Long eventCategoryId);

    @Query(value = """
       SELECT e.auditionNumber
       FROM EventCategoryParticipant e
       WHERE e.event.eventId = :eventId
         AND e.eventCategory.id = :eventCategoryId
         AND e.judge.name = :name
         AND e.auditionNumber IS NOT NULL
       """)
    List<Integer> findAuditionNumberByEventAndEventCategoryAndJudge(@Param("eventId") Long eventId,
                                                    @Param("eventCategoryId") Long eventCategoryId,
                                                    @Param("name") String name);

    @Query("""
       SELECT e FROM EventCategoryParticipant e
       WHERE LOWER(e.event.eventName) = LOWER(:eventName)
         AND LOWER(e.eventCategory.name) = LOWER(:categoryName)
         AND e.auditionNumber = :auditionNumber
       """)
    Optional<EventCategoryParticipant> findByEventNameAndCategoryNameAndAuditionNumber(
        @Param("eventName") String eventName,
        @Param("categoryName") String categoryName,
        @Param("auditionNumber") Integer auditionNumber);

    // -- Format-scoped pool queries --

    @Query("SELECT COUNT(e) FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND e.format IS NULL")
    long countByEventIdAndEventCategoryIdAndFormatIsNull(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId);

    @Query("SELECT COUNT(e) FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND e.format = :format")
    long countByEventIdAndEventCategoryIdAndFormat(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId, @Param("format") String format);

    @Query("SELECT e.auditionNumber FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND e.format IS NULL AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventCategoryAndFormatIsNull(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId);

    @Query("SELECT e.auditionNumber FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND e.format = :format AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventCategoryAndFormat(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId, @Param("format") String format);

    @Query("SELECT COUNT(e) FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND e.format IS NULL AND e.judge.name = :name")
    long countByEventIdAndEventCategoryIdAndFormatIsNullAndJudge(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId, @Param("name") String name);

    @Query("SELECT COUNT(e) FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND e.format = :format AND e.judge.name = :name")
    long countByEventIdAndEventCategoryIdAndFormatAndJudge(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId, @Param("format") String format, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND e.format IS NULL AND e.judge.name = :name AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventCategoryAndFormatIsNullAndJudge(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND e.format = :format AND e.judge.name = :name AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventCategoryAndFormatAndJudge(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId, @Param("format") String format, @Param("name") String name);

    // -- Solo pool --
    @Query("SELECT COUNT(e) FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND (e.format IS NULL OR LOWER(e.format) = '1v1')")
    long countByEventIdAndEventCategoryIdAndSolo(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId);

    @Query("SELECT e.auditionNumber FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND (e.format IS NULL OR LOWER(e.format) = '1v1') AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventCategoryAndSolo(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId);

    @Query("SELECT COUNT(e) FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND (e.format IS NULL OR LOWER(e.format) = '1v1') AND e.judge.name = :name")
    long countByEventIdAndEventCategoryIdAndSoloAndJudge(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.eventCategory.id = :eventCategoryId AND (e.format IS NULL OR LOWER(e.format) = '1v1') AND e.judge.name = :name AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventCategoryAndSoloAndJudge(@Param("eventId") Long eventId, @Param("eventCategoryId") Long eventCategoryId, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventCategoryParticipant e WHERE e.event.eventId = :eventId AND e.participant.participantId = :participantId AND e.eventCategory.id != :eventCategoryId AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumbersForParticipantInOtherCategories(@Param("eventId") Long eventId, @Param("participantId") Long participantId, @Param("eventCategoryId") Long eventCategoryId);

    @Query("""
        SELECT COUNT(e) FROM EventCategoryParticipant e
        WHERE e.event.eventId = :eventId
          AND e.eventCategory.id = :eventCategoryId
          AND LOWER(e.displayName) = LOWER(:displayName)
          AND e.participant.participantId != :excludeParticipantId
        """)
    long countByDisplayNameForOtherParticipant(
        @Param("eventId") Long eventId,
        @Param("eventCategoryId") Long eventCategoryId,
        @Param("displayName") String displayName,
        @Param("excludeParticipantId") Long excludeParticipantId);

}
