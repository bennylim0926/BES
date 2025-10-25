package com.example.BES.controllers;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.BES.dtos.admin.AddGenreDto;
import com.example.BES.dtos.admin.DeleteGenreDto;
import com.example.BES.dtos.admin.DeleteJudgeDto;
import com.example.BES.dtos.admin.DeleteScoreByEventDto;
import com.example.BES.dtos.admin.UpdateGenreDto;
import com.example.BES.dtos.admin.UpdateJudgeDto;
import com.example.BES.models.Genre;
import com.example.BES.models.Judge;
import com.example.BES.services.GenreService;
import com.example.BES.services.JudgeService;
import com.example.BES.services.ScoreService;

@RestController
@CrossOrigin
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Autowired
    GenreService genreService;

    @Autowired
    JudgeService judgeService;

    @Autowired
    ScoreService scoreService;

    // Create Genre
    @PostMapping("/genre")
    public ResponseEntity<?> createGenre(@RequestBody AddGenreDto dto){
        Genre genre = genreService.addGenreService(dto);
        return ResponseEntity.ok(Map.of(
            "Created genre: ", genre.getGenreName()
        ));
    }
    
    // Update Genre
    @PostMapping("/update-genre")
    public ResponseEntity<?> updateGenre(@RequestBody UpdateGenreDto dto){
        Genre genre = genreService.updateGenreService(dto);
        if(genre == null){
            return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", "Genre NotFound "));
        }
        return ResponseEntity.ok(Map.of(
            "message", "Genre updated successfully",
            "genre", genre.getGenreName()
        ));
    }
    // Delete Genre
        // It might link to some event and unable to delete
    @DeleteMapping("/genre")
    public ResponseEntity<?> deleteGenre(@RequestBody DeleteGenreDto dto){
        String deletedGenre = genreService.deleteGenreService(dto);
        if(deletedGenre == ""){
            return ResponseEntity.ok(Map.of(
                "message", "Nothing was deleted"
            ));
        }
        return ResponseEntity.ok(Map.of(
            "message", "deleted",
            "genre", deletedGenre
        ));
    }

    // Update Judge
    @PostMapping("/judge")
    public ResponseEntity<?> updateJudge(@RequestBody UpdateJudgeDto dto){
        Judge judge = judgeService.updateJudgeService(dto);
        if(judge == null){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of(
                    "message", "Judge NotFound"
                )
            );
        }
        return ResponseEntity.ok(Map.of(
            "message", "Judge updated successfully",
            "judge", judge.getName()
        ));
    }
    // Delete Judge
        // It might lnik to some event/scores and unable to delete
    @DeleteMapping("/judge")
    public ResponseEntity<?> deleteJudge(@RequestBody DeleteJudgeDto dto){
        String deletedJudge = judgeService.deleteJudgeService(dto);
        if(deletedJudge == ""){
            return ResponseEntity.ok(Map.of(
                "message", "Nothing was deleted"
            ));
        }
        return ResponseEntity.ok(Map.of(
            "message", "deleted",
            "judge", deletedJudge
        ));
    }

    // Delete Score by Event
    @DeleteMapping("/score")
    public ResponseEntity<?> deleteJudge(@RequestBody DeleteScoreByEventDto dto){
        Integer deletedRows = 0;
        deletedRows = scoreService.deleteScoreByEventService(dto);
        return ResponseEntity.ok(Map.of(
            "message", "score deleted",
            "deleted", deletedRows.toString()
        ));
    }
}
