package com.example.BES.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddBattleGuestDto;
import com.example.BES.dtos.GetBattleGuestDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenreBattleGuest;
import com.example.BES.models.Genre;
import com.example.BES.respositories.EventGenreBattleGuestRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.GenreRepo;

@Service
public class BattleGuestService {

    @Autowired
    EventRepo eventRepo;

    @Autowired
    GenreRepo genreRepo;

    @Autowired
    EventGenreBattleGuestRepo battleGuestRepo;

    public List<GetBattleGuestDto> getBattleGuests(String eventName, String genreName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        Genre genre = genreRepo.findByGenreName(genreName.toLowerCase()).orElse(null);
        if (event == null || genre == null) return List.of();

        return battleGuestRepo.findByEventAndGenre(event, genre).stream()
            .map(g -> {
                GetBattleGuestDto dto = new GetBattleGuestDto();
                dto.id = g.getId();
                dto.guestName = g.getGuestName();
                dto.entryRound = g.getEntryRound();
                dto.genreName = g.getGenre().getGenreName();
                return dto;
            }).collect(Collectors.toList());
    }

    public List<GetBattleGuestDto> getAllBattleGuestsForEvent(String eventName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (event == null) return List.of();

        return battleGuestRepo.findByEvent(event).stream()
            .map(g -> {
                GetBattleGuestDto dto = new GetBattleGuestDto();
                dto.id = g.getId();
                dto.guestName = g.getGuestName();
                dto.entryRound = g.getEntryRound();
                dto.genreName = g.getGenre().getGenreName();
                return dto;
            }).collect(Collectors.toList());
    }

    public GetBattleGuestDto addBattleGuest(String eventName, String genreName, AddBattleGuestDto dto) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        Genre genre = genreRepo.findByGenreName(genreName.toLowerCase()).orElse(null);
        if (event == null || genre == null) throw new RuntimeException("Event or genre not found");

        EventGenreBattleGuest guest = new EventGenreBattleGuest();
        guest.setEvent(event);
        guest.setGenre(genre);
        guest.setGuestName(dto.guestName);
        guest.setEntryRound(dto.entryRound);
        guest = battleGuestRepo.save(guest);

        GetBattleGuestDto result = new GetBattleGuestDto();
        result.id = guest.getId();
        result.guestName = guest.getGuestName();
        result.entryRound = guest.getEntryRound();
        result.genreName = genre.getGenreName();
        return result;
    }

    public void removeBattleGuest(Long guestId) {
        battleGuestRepo.deleteById(guestId);
    }
}
