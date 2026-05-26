package com.example.BES.services;

import com.example.BES.dtos.GetGenreDto;
import com.example.BES.dtos.admin.AddGenreDto;
import com.example.BES.dtos.admin.DeleteGenreDto;
import com.example.BES.dtos.admin.UpdateGenreDto;
import com.example.BES.models.Genre;
import com.example.BES.respositories.GenreRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenreServiceTest {

    @Mock GenreRepo repo;
    @InjectMocks GenreService service;

    private Genre genre(Long id, String name) {
        Genre g = new Genre();
        g.setGenreId(id);
        g.setGenreName(name);
        return g;
    }

    @Test
    void getAllGenres_mapsToDto() {
        when(repo.findAll()).thenReturn(List.of(genre(1L, "breaking")));

        List<GetGenreDto> result = service.getAllGenres();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).genreName).isEqualTo("breaking");
        assertThat(result.get(0).id).isEqualTo(1L);
    }

    @Test
    void addGenre_returnsExistingWhenGenreNameNotNull() {
        AddGenreDto dto = mock(AddGenreDto.class);
        when(dto.getName()).thenReturn("Popping");
        Genre existing = genre(1L, "popping");
        when(repo.findByGenreName("popping")).thenReturn(Optional.of(existing));

        Genre result = service.addGenreService(dto);

        verify(repo, never()).save(any());
        assertThat(result).isSameAs(existing);
    }

    @Test
    void addGenre_savesWhenGenreNameIsNull() {
        AddGenreDto dto = mock(AddGenreDto.class);
        when(dto.getName()).thenReturn("Locking");
        Genre empty = new Genre(); // genreName is null
        when(repo.findByGenreName("locking")).thenReturn(Optional.of(empty));
        when(repo.save(any())).thenReturn(empty);

        service.addGenreService(dto);

        verify(repo).save(empty);
    }

    @Test
    void updateGenre_updatesNameAndSaves() {
        Genre g = genre(1L, "breaking");
        UpdateGenreDto dto = mock(UpdateGenreDto.class);
        when(dto.getId()).thenReturn(1L);
        when(dto.getNewName()).thenReturn("b-boy");
        when(repo.findById(1L)).thenReturn(Optional.of(g));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Genre result = service.updateGenreService(dto);

        assertThat(result.getGenreName()).isEqualTo("b-boy");
    }

    @Test
    void updateGenre_returnsNullWhenNotFound() {
        UpdateGenreDto dto = mock(UpdateGenreDto.class);
        when(dto.getId()).thenReturn(99L);
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.updateGenreService(dto)).isNull();
    }

    @Test
    void deleteGenre_deletesAndReturnsName() {
        Genre g = genre(1L, "locking");
        DeleteGenreDto dto = mock(DeleteGenreDto.class);
        when(dto.getId()).thenReturn(1L);
        when(repo.findById(1L)).thenReturn(Optional.of(g));

        String name = service.deleteGenreService(dto);

        assertThat(name).isEqualTo("locking");
        verify(repo).delete(g);
    }

    @Test
    void deleteGenre_returnsEmptyStringWhenNotFound() {
        DeleteGenreDto dto = mock(DeleteGenreDto.class);
        when(dto.getId()).thenReturn(99L);
        when(repo.findById(99L)).thenReturn(Optional.empty());

        assertThat(service.deleteGenreService(dto)).isEmpty();
    }
}
