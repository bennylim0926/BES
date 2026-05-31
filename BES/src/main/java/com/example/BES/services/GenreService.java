package com.example.BES.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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

    public List<GetGenreDto> getAllGenres() {
        List<Genre> results = repo.findAll();
        List<GetGenreDto> res = new ArrayList<>();
        for (Genre g : results) {
            res.add(toDto(g));
        }
        return res;
    }

    public Genre addGenreService(AddGenreDto dto) {
        Genre genre = repo.findByGenreName(dto.getName().toLowerCase()).orElse(new Genre());
        if (genre.getGenreName() == null) {
            genre.setGenreName(dto.getName());
            genre = repo.save(genre);
        }
        return genre;
    }

    public Genre updateGenreService(UpdateGenreDto dto) {
        Genre genre = repo.findById(dto.getId()).orElse(null);
        if (genre == null) return null;
        if (dto.getNewName() != null && !dto.getNewName().isBlank()) {
            genre.setGenreName(dto.getNewName());
        }
        if (dto.getAliases() != null) {
            String normalized = normalizeAliases(dto.getAliases());
            genre.setSheetAliases(normalized.isEmpty() ? null : normalized);
        }
        return repo.save(genre);
    }

    public String deleteGenreService(DeleteGenreDto dto) {
        Genre genre = repo.findById(dto.getId()).orElse(null);
        String name = "";
        if (genre != null) {
            name = genre.getGenreName();
            repo.delete(genre);
        }
        return name;
    }

    /** Returns all match strings for a genre: its name + parsed aliases (all lowercase). */
    public List<String> getMatchStrings(Genre g) {
        List<String> all = new ArrayList<>();
        all.add(g.getGenreName().toLowerCase());
        if (g.getSheetAliases() != null && !g.getSheetAliases().isBlank()) {
            Arrays.stream(g.getSheetAliases().split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .forEach(all::add);
        }
        return all;
    }

    private GetGenreDto toDto(Genre g) {
        GetGenreDto dto = new GetGenreDto();
        dto.id = g.getGenreId();
        dto.genreName = g.getGenreName();
        dto.aliases = g.getSheetAliases() == null || g.getSheetAliases().isBlank()
            ? Collections.emptyList()
            : Arrays.stream(g.getSheetAliases().split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        return dto;
    }

    private String normalizeAliases(String raw) {
        return Arrays.stream(raw.split(","))
            .map(String::strip)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(","));
    }
}
