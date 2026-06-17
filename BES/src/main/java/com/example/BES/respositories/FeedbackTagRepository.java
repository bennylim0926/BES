package com.example.BES.respositories;

import com.example.BES.models.FeedbackTag;
import com.example.BES.models.FeedbackTagGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackTagRepository extends JpaRepository<FeedbackTag, Long> {
    List<FeedbackTag> findByGroup(FeedbackTagGroup group);

    List<FeedbackTag> findByEventIsNull();

    List<FeedbackTag> findByEventEventId(Long eventId);

    List<FeedbackTag> findByEventEventIdOrEventIsNull(Long eventId);
}
