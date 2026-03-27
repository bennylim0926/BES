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

}
