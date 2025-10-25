package com.example.BES.services;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.respositories.GenreRepo;
import com.example.BES.dtos.GetGenreDto;
import com.example.BES.dtos.admin.AddGenreDto;
import com.example.BES.dtos.admin.DeleteGenreDto;
import com.example.BES.dtos.admin.UpdateGenreDto;
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
            dto.id = g.getGenreId();
            dto.genreName = g.getGenreName();
            res.add(dto);
        }
        return res;
    }

    public Genre addGenreService(AddGenreDto dto){
        Genre genre = repo.findByGenreName(dto.getName().toLowerCase()).orElse(new Genre());
        if(genre.getGenreName() == null){
            genre.setGenreName(dto.getName());
            genre = repo.save(genre);
        }
        return genre;
    }

    public Genre updateGenreService(UpdateGenreDto dto){
        Genre genre = repo.findById(dto.getId()).orElse(null);
        if(genre != null){
            genre.setGenreName(dto.getNewName());
            genre = repo.save(genre);
        }
        return genre;
    }

    public String deleteGenreService(DeleteGenreDto dto){
        Genre genre = repo.findById(dto.getId()).orElse(null);
        String name = "";
        if(genre != null){
            name = genre.getGenreName();
            repo.delete(genre);
        }
        return name;
    }
}
