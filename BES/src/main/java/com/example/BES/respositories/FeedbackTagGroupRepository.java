package com.example.BES.respositories;

import com.example.BES.models.FeedbackTagGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FeedbackTagGroupRepository extends JpaRepository<FeedbackTagGroup, Long> {
}
