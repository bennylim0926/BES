package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.respositories.GenreRepo;
import com.example.BES.dtos.GetGenreDto;
import com.example.BES.models.Genre;

@Service
public class GenreService {
    @Autowired
    GenreRepo repo;

    public List<GetGenreDto> getAllGenres(){
        List<Genre> results = repo.findAll();
        List<GetGenreDto> res = new ArrayList<>();
        for(Genre g : results){
            GetGenreDto dto = new GetGenreDto();
            dto.genreName = g.getGenreName();
            res.add(dto);
        }
        return res;
    }
}
