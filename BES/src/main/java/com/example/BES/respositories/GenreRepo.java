package com.example.BES.respositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Genre;

@Repository
public interface GenreRepo extends JpaRepository<Genre,Long>{

}
