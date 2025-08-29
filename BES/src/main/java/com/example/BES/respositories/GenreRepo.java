package com.example.BES.respositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Genre;

@Repository
public interface GenreRepo extends JpaRepository<Genre,Long>{
    Optional<Genre> findByGenreName(String genreName);
}
