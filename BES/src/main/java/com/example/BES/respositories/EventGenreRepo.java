package com.example.BES.respositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreId;
import com.example.BES.models.Genre;

@Repository
public interface EventGenreRepo extends JpaRepository<EventGenre, EventGenreId>{
    Optional<EventGenre> findByEventAndGenre(Event event, Genre genre);
}
