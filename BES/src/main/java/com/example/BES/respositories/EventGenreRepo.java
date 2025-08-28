package com.example.BES.respositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreId;

@Repository
public interface EventGenreRepo extends JpaRepository<EventGenre, EventGenreId>{

}
