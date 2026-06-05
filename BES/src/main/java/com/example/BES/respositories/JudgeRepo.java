package com.example.BES.respositories;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.models.Judge;

@Repository
public interface JudgeRepo extends JpaRepository<Judge,Long>{

    @Query("SELECT j FROM Judge j WHERE LOWER(TRIM(j.name)) = LOWER(TRIM(:name))")
    Optional<Judge> findFirstByName(@Param("name") String name);

    boolean existsByNameIgnoreCase(String name);

    // ── event_judge pool table ────────────────────────────────────────────

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO event_judge (event_id, judge_id) VALUES (:eventId, :judgeId) ON CONFLICT DO NOTHING", nativeQuery = true)
    void insertEventJudge(@Param("eventId") Long eventId, @Param("judgeId") Long judgeId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM event_judge WHERE event_id = :eventId AND judge_id = :judgeId", nativeQuery = true)
    void deleteEventJudge(@Param("eventId") Long eventId, @Param("judgeId") Long judgeId);

    @Query(value = "SELECT j.* FROM judge j INNER JOIN event_judge ej ON j.judge_id = ej.judge_id WHERE ej.event_id = :eventId", nativeQuery = true)
    List<Judge> findJudgesByEventId(@Param("eventId") Long eventId);
}
