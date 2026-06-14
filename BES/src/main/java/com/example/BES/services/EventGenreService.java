package com.example.BES.services;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.BES.dtos.AddGenreToEventDto;
import com.example.BES.dtos.GetEventDivisionDto;
import com.example.BES.dtos.GetGenreDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.Genre;
import com.example.BES.respositories.EventGenreParticpantRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;

@Service
public class EventGenreService {
    @Autowired
    EventGenreRepo eventGenreRepo;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    GenreRepo genreRepo;

    @Autowired
    EventGenreParticpantRepo eventGenreParticipantRepo;

    public List<GetEventDivisionDto> getGenresByEventService(String eventName) {
        Event e = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (e == null) return new ArrayList<>();
        List<GetEventDivisionDto> dtos = new ArrayList<>();
        for (EventGenre eg : eventGenreRepo.findByEvent(e)) {
            GetEventDivisionDto dto = new GetEventDivisionDto();
            dto.eventGenreId = eg.getId();
            dto.name = eg.getName();
            dto.format = eg.getFormat();
            dto.roundLabel = eg.getRoundLabel();
            dto.numberColor = eg.getNumberColor();
            dto.sheetAliases = eg.getSheetAliases();
            dto.genreId = eg.getGenre() != null ? eg.getGenre().getGenreId() : null;
            dto.soloAllowed = eg.isSoloAllowed();
            dto.participantCount = eventGenreParticipantRepo.countByEventIdAndEventGenreId(e.getEventId(), eg.getId());
            dtos.add(dto);
        }
        return dtos;
    }

    public void updateEventGenreFormat(Long eventGenreId, String format) {
        EventGenre eg = eventGenreRepo.findById(eventGenreId).orElse(null);
        if (eg == null) throw new RuntimeException("Event genre not found");
        eg.setFormat(format == null || format.isBlank() ? null : format.trim());
        eventGenreRepo.save(eg);
    }

    public void renameDivision(Long id, String name) {
        EventGenre eg = eventGenreRepo.findById(id).orElseThrow(() -> new RuntimeException("Division not found"));
        if (name == null || name.isBlank()) throw new RuntimeException("Division name must not be blank");
        eg.setName(name.trim());
        eventGenreRepo.save(eg);
    }

    public void updateAliases(Long id, String aliases) {
        EventGenre eg = eventGenreRepo.findById(id).orElseThrow(() -> new RuntimeException("Division not found"));
        eg.setSheetAliases(aliases == null || aliases.isBlank() ? null : aliases.trim());
        eventGenreRepo.save(eg);
    }

    public void updateSoloAllowed(Long id, boolean soloAllowed) {
        EventGenre eg = eventGenreRepo.findById(id).orElseThrow(() -> new RuntimeException("Division not found"));
        eg.setSoloAllowed(soloAllowed);
        eventGenreRepo.save(eg);
    }

    public void updateRoundLabel(Long id, String roundLabel) {
        EventGenre eg = eventGenreRepo.findById(id).orElseThrow(() -> new RuntimeException("Division not found"));
        eg.setRoundLabel(roundLabel == null || roundLabel.isBlank() ? null : roundLabel.trim());
        eventGenreRepo.save(eg);
    }

    public void updateNumberColor(Long id, String color) {
        EventGenre eg = eventGenreRepo.findById(id).orElseThrow(() -> new RuntimeException("Division not found"));
        eg.setNumberColor(color == null || color.isBlank() ? null : color.trim());
        eventGenreRepo.save(eg);
    }

    public void deleteDivision(Long id) {
        EventGenre eg = eventGenreRepo.findById(id).orElseThrow(() -> new RuntimeException("Division not found"));
        eventGenreRepo.deleteById(eg.getId());
    }

    public void addGenreToEventService(AddGenreToEventDto dto){
        Event e = eventRepo.findByEventName(dto.eventName).orElse(null);
        for (AddGenreToEventDto.Division div : dto.divisions) {
            if (eventGenreRepo.findByEventAndName(e, div.name).isPresent()) {
                throw new DataIntegrityViolationException("This event already has a division named: " + div.name);
            }
            EventGenre eventGenre = new EventGenre();
            eventGenre.setEvent(e);
            eventGenre.setName(div.name.trim());
            if (div.genreId != null) {
                Genre g = genreRepo.findById(div.genreId).orElse(null);
                eventGenre.setGenre(g);
            }
            if (div.format != null && !div.format.isBlank()) {
                eventGenre.setFormat(div.format.trim());
            }
            // Auto-link this genre to the event so empty-category genres still appear as groups.
            if (eventGenre.getGenre() != null) {
                ensureGenreLinked(e, eventGenre.getGenre());
            }
            eventGenreRepo.save(eventGenre);
        }
    }

    @Transactional
    public void linkGenresToEvent(String eventName, List<Long> genreIds) {
        Event e = eventRepo.findByEventNameIgnoreCase(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        if (e.getLinkedGenres() == null) e.setLinkedGenres(new ArrayList<>());
        Set<Long> existing = new HashSet<>();
        for (Genre g : e.getLinkedGenres()) existing.add(g.getGenreId());
        for (Long id : genreIds) {
            if (id == null || existing.contains(id)) continue;
            Genre g = genreRepo.findById(id).orElse(null);
            if (g == null) continue;
            e.getLinkedGenres().add(g);
            existing.add(id);
        }
        eventRepo.save(e);
    }

    @Transactional(readOnly = true)
    public List<GetGenreDto> getLinkedGenresService(String eventName) {
        Event e = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (e == null || e.getLinkedGenres() == null) return new ArrayList<>();
        List<GetGenreDto> out = new ArrayList<>();
        for (Genre g : e.getLinkedGenres()) {
            GetGenreDto dto = new GetGenreDto();
            dto.id = g.getGenreId();
            dto.genreName = g.getGenreName();
            out.add(dto);
        }
        return out;
    }

    @Transactional
    public void unlinkGenreFromEvent(String eventName, Long genreId) {
        Event e = eventRepo.findByEventNameIgnoreCase(eventName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        if (e.getLinkedGenres() == null) return;
        e.getLinkedGenres().removeIf(g -> g.getGenreId().equals(genreId));
        eventRepo.save(e);
    }

    private void ensureGenreLinked(Event e, Genre g) {
        if (e.getLinkedGenres() == null) {
            e.setLinkedGenres(new ArrayList<>());
        }
        boolean alreadyLinked = e.getLinkedGenres().stream()
            .anyMatch(existing -> existing.getGenreId().equals(g.getGenreId()));
        if (!alreadyLinked) {
            e.getLinkedGenres().add(g);
            eventRepo.save(e);
        }
    }
}
