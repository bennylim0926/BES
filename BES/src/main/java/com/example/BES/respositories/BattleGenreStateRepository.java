package com.example.BES.respositories;

import com.example.BES.models.BattleGenreState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BattleGenreStateRepository extends JpaRepository<BattleGenreState, Long> {
    Optional<BattleGenreState> findByEventNameAndGenreName(String eventName, String genreName);
}
