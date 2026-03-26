package com.example.BES.respositories;

import com.example.BES.models.AuditionFeedback;
import com.example.BES.models.EventGenreParticipant;
import com.example.BES.models.Judge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AuditionFeedbackRepository extends JpaRepository<AuditionFeedback, Long> {
    Optional<AuditionFeedback> findByEventGenreParticipantAndJudge(EventGenreParticipant egp, Judge judge);
    List<AuditionFeedback> findByEventGenreParticipant(EventGenreParticipant egp);
}
