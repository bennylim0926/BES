package com.example.BES.respositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.BES.models.BattleActiveCategory;

public interface BattleActiveCategoryRepository extends JpaRepository<BattleActiveCategory, Integer> {
    Optional<BattleActiveCategory> findByEventName(String eventName);

    @Modifying
    @Transactional
    @Query("DELETE FROM BattleActiveCategory b WHERE b.eventName = :eventName")
    void deleteByEventName(@Param("eventName") String eventName);
}
