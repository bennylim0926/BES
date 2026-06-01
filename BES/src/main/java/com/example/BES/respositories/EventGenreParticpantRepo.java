package com.example.BES.respositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.EventGenreParticipantId;

@Repository
public interface EventGenreParticpantRepo extends JpaRepository<EventGenreParticipant, EventGenreParticipantId> {
    List<EventGenreParticipant> findByEvent(Event event);

    @Query(value = """
       SELECT e
       FROM EventGenreParticipant e
       WHERE LOWER(e.event.eventName) = LOWER(:eventName)
         AND LOWER(e.eventGenre.name) = LOWER(:genreName)
         AND LOWER(e.displayName) = LOWER(:participantName)
       """)
    Optional<EventGenreParticipant> findByEventGenreParticipant(@Param("eventName") String eventName,
                                                              @Param("genreName") String genreName,
                                                              @Param("participantName") String participantName);

    @Query(value = """
       SELECT COUNT(e)
       FROM EventGenreParticipant e
       WHERE e.event.eventId = :eventId
         AND e.eventGenre.id = :eventGenreId
       """)
    long countByEventIdAndEventGenreId(@Param("eventId") Long eventId,
                                  @Param("eventGenreId") Long eventGenreId);

    @Query(value = """
       SELECT COUNT(e)
       FROM EventGenreParticipant e
       WHERE e.event.eventId = :eventId
         AND e.eventGenre.id = :eventGenreId
         AND e.judge.name = :name
       """)
    long countByEventIdAndEventGenreIdAndJudge(@Param("eventId") Long eventId,
                                          @Param("eventGenreId") Long eventGenreId,
                                          @Param("name") String name);

    @Query("""
        SELECT e FROM EventGenreParticipant e
        WHERE e.event.eventId = :eventId
          AND e.participant.participantId = :participantId
        """)
    List<EventGenreParticipant> findByEventIdAndParticipantId(
        @Param("eventId") Long eventId,
        @Param("participantId") Long participantId);

    @Query(value = """
       SELECT e.auditionNumber
       FROM EventGenreParticipant e
       WHERE e.event.eventId = :eventId
         AND e.eventGenre.id = :eventGenreId
         AND e.auditionNumber IS NOT NULL
       """)
    List<Integer> findAuditionNumberByEventAndEventGenre(@Param("eventId") Long eventId,
                                                    @Param("eventGenreId") Long eventGenreId);

    @Query(value = """
       SELECT e.auditionNumber
       FROM EventGenreParticipant e
       WHERE e.event.eventId = :eventId
         AND e.eventGenre.id = :eventGenreId
         AND e.judge.name = :name
         AND e.auditionNumber IS NOT NULL
       """)
    List<Integer> findAuditionNumberByEventAndEventGenreAndJudge(@Param("eventId") Long eventId,
                                                    @Param("eventGenreId") Long eventGenreId,
                                                    @Param("name") String name);

    @Query("""
       SELECT e FROM EventGenreParticipant e
       WHERE LOWER(e.event.eventName) = LOWER(:eventName)
         AND LOWER(e.eventGenre.name) = LOWER(:genreName)
         AND e.auditionNumber = :auditionNumber
       """)
    Optional<EventGenreParticipant> findByEventNameAndGenreNameAndAuditionNumber(
        @Param("eventName") String eventName,
        @Param("genreName") String genreName,
        @Param("auditionNumber") Integer auditionNumber);

    // -- Format-scoped pool queries --

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND e.format IS NULL")
    long countByEventIdAndEventGenreIdAndFormatIsNull(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId);

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND e.format = :format")
    long countByEventIdAndEventGenreIdAndFormat(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("format") String format);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND e.format IS NULL AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndFormatIsNull(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND e.format = :format AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndFormat(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("format") String format);

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND e.format IS NULL AND e.judge.name = :name")
    long countByEventIdAndEventGenreIdAndFormatIsNullAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("name") String name);

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND e.format = :format AND e.judge.name = :name")
    long countByEventIdAndEventGenreIdAndFormatAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("format") String format, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND e.format IS NULL AND e.judge.name = :name AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndFormatIsNullAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND e.format = :format AND e.judge.name = :name AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndFormatAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("format") String format, @Param("name") String name);

    // -- Solo pool --
    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND (e.format IS NULL OR LOWER(e.format) = '1v1')")
    long countByEventIdAndEventGenreIdAndSolo(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND (e.format IS NULL OR LOWER(e.format) = '1v1') AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndSolo(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId);

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND (e.format IS NULL OR LOWER(e.format) = '1v1') AND e.judge.name = :name")
    long countByEventIdAndEventGenreIdAndSoloAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.eventGenre.id = :eventGenreId AND (e.format IS NULL OR LOWER(e.format) = '1v1') AND e.judge.name = :name AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndEventGenreAndSoloAndJudge(@Param("eventId") Long eventId, @Param("eventGenreId") Long eventGenreId, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.participant.participantId = :participantId AND e.eventGenre.id != :eventGenreId AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumbersForParticipantInOtherGenres(@Param("eventId") Long eventId, @Param("participantId") Long participantId, @Param("eventGenreId") Long eventGenreId);

}
