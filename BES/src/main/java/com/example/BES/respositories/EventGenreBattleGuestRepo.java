package com.example.BES.respositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.BES.models.Event;
import com.example.BES.models.EventGenreBattleGuest;
import com.example.BES.models.Genre;

public interface EventGenreBattleGuestRepo extends JpaRepository<EventGenreBattleGuest, Long> {
    List<EventGenreBattleGuest> findByEventAndGenre(Event event, Genre genre);
    List<EventGenreBattleGuest> findByEvent(Event event);
}
