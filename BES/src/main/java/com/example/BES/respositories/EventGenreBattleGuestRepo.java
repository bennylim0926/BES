package com.example.BES.respositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreBattleGuest;

public interface EventGenreBattleGuestRepo extends JpaRepository<EventGenreBattleGuest, Long> {
    List<EventGenreBattleGuest> findByEventAndEventGenre(Event event, EventGenre eventGenre);
    List<EventGenreBattleGuest> findByEvent(Event event);
}
