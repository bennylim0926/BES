package com.example.BES.respositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;

@Repository
public interface EventGenreRepo extends JpaRepository<EventGenre, Long>{
    Optional<EventGenre> findByEventAndName(Event event, String name);
    List<EventGenre> findByEvent(Event event);
}
