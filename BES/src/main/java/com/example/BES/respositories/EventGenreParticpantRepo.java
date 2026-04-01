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
import com.example.BES.models.Genre;

@Repository
public interface EventGenreParticpantRepo extends JpaRepository<EventGenreParticipant, EventGenreParticipantId> {
    List<EventGenreParticipant> findByEventAndGenre(Event event_id, Genre genre_id);

    List<EventGenreParticipant> findByEvent(Event event_id);

    @Query(value = """
       SELECT e
       FROM EventGenreParticipant e
       WHERE LOWER(e.event.eventName) = LOWER(:eventName)
         AND LOWER(e.genre.genreName) = LOWER(:genreName)
         AND LOWER(e.displayName) = LOWER(:participantName)
       """)
    Optional<EventGenreParticipant> findByEventGenreParticipant(@Param("eventName") String eventName, 
                                                              @Param("genreName") String genreName, 
                                                              @Param("participantName") String participantName);

    @Query(value = """
       SELECT COUNT(e)
       FROM EventGenreParticipant e
       WHERE e.event.eventId = :eventId
         AND e.genre.genreId = :genreId
       """)
    long countByEventIdAndGenreId(@Param("eventId") Long eventId,
                                  @Param("genreId") Long genreId);

    @Query(value = """
       SELECT COUNT(e)
       FROM EventGenreParticipant e
       WHERE e.event.eventId = :eventId
         AND e.genre.genreId = :genreId
         AND e.judge.name = :name
       """)
    long countByEventIdAndGenreIdAndJudge(@Param("eventId") Long eventId,
                                          @Param("genreId") Long genreId,
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
         AND e.genre.genreId = :genreId
         AND e.auditionNumber IS NOT NULL
       """)
    List<Integer> findAuditionNumberByEventAndGenre(@Param("eventId") Long eventId,
                                                    @Param("genreId") Long genreId);

    @Query(value = """
       SELECT e.auditionNumber
       FROM EventGenreParticipant e
       WHERE e.event.eventId = :eventId
         AND e.genre.genreId = :genreId
         AND e.judge.name = :name
         AND e.auditionNumber IS NOT NULL
       """)
    List<Integer> findAuditionNumberByEventAndGenreAndJudge(@Param("eventId") Long eventId,
                                                    @Param("genreId") Long genreId,
                                                    @Param("name") String name);

    @Query("""
       SELECT e FROM EventGenreParticipant e
       WHERE LOWER(e.event.eventName) = LOWER(:eventName)
         AND LOWER(e.genre.genreName) = LOWER(:genreName)
         AND e.auditionNumber = :auditionNumber
       """)
    Optional<EventGenreParticipant> findByEventNameAndGenreNameAndAuditionNumber(
        @Param("eventName") String eventName,
        @Param("genreName") String genreName,
        @Param("auditionNumber") Integer auditionNumber);

    // ── Format-scoped pool queries (for separate team / solo audition number pools) ──

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.genre.genreId = :genreId AND e.format IS NULL")
    long countByEventIdAndGenreIdAndFormatIsNull(@Param("eventId") Long eventId, @Param("genreId") Long genreId);

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.genre.genreId = :genreId AND e.format = :format")
    long countByEventIdAndGenreIdAndFormat(@Param("eventId") Long eventId, @Param("genreId") Long genreId, @Param("format") String format);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.genre.genreId = :genreId AND e.format IS NULL AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndGenreAndFormatIsNull(@Param("eventId") Long eventId, @Param("genreId") Long genreId);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.genre.genreId = :genreId AND e.format = :format AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndGenreAndFormat(@Param("eventId") Long eventId, @Param("genreId") Long genreId, @Param("format") String format);

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.genre.genreId = :genreId AND e.format IS NULL AND e.judge.name = :name")
    long countByEventIdAndGenreIdAndFormatIsNullAndJudge(@Param("eventId") Long eventId, @Param("genreId") Long genreId, @Param("name") String name);

    @Query("SELECT COUNT(e) FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.genre.genreId = :genreId AND e.format = :format AND e.judge.name = :name")
    long countByEventIdAndGenreIdAndFormatAndJudge(@Param("eventId") Long eventId, @Param("genreId") Long genreId, @Param("format") String format, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.genre.genreId = :genreId AND e.format IS NULL AND e.judge.name = :name AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndGenreAndFormatIsNullAndJudge(@Param("eventId") Long eventId, @Param("genreId") Long genreId, @Param("name") String name);

    @Query("SELECT e.auditionNumber FROM EventGenreParticipant e WHERE e.event.eventId = :eventId AND e.genre.genreId = :genreId AND e.format = :format AND e.judge.name = :name AND e.auditionNumber IS NOT NULL")
    List<Integer> findAuditionNumberByEventAndGenreAndFormatAndJudge(@Param("eventId") Long eventId, @Param("genreId") Long genreId, @Param("format") String format, @Param("name") String name);

}
