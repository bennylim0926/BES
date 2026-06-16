package com.example.BES.respositories;

import com.example.BES.models.BattleCategoryState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BattleCategoryStateRepository extends JpaRepository<BattleCategoryState, Long> {
    Optional<BattleCategoryState> findByEventNameAndCategoryName(String eventName, String categoryName);
    List<BattleCategoryState> findByEventName(String eventName);
}
