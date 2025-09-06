package com.example.BES.respositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.Score;

@Repository
public interface ScoreRepo extends JpaRepository<Score, Long>{
    Optional<Score> findByEventGenreParticipant(EventGenreParticipant eventGenreParticipant);
}
