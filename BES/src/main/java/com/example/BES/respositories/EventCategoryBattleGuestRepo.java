package com.example.BES.respositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.models.EventCategoryBattleGuest;

public interface EventCategoryBattleGuestRepo extends JpaRepository<EventCategoryBattleGuest, Long> {
    List<EventCategoryBattleGuest> findByEventAndEventCategory(Event event, EventCategory eventCategory);
    List<EventCategoryBattleGuest> findByEvent(Event event);
}
