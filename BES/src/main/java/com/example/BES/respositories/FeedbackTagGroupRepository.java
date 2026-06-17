package com.example.BES.respositories;

import com.example.BES.models.FeedbackTagGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackTagGroupRepository extends JpaRepository<FeedbackTagGroup, Long> {

    List<FeedbackTagGroup> findByEventIsNull();

    List<FeedbackTagGroup> findByEventEventId(Long eventId);

    List<FeedbackTagGroup> findByEventEventIdOrEventIsNull(Long eventId);
}
