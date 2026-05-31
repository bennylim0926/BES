package com.example.BES.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddBattleGuestDto;
import com.example.BES.dtos.GetBattleGuestDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventGenre;
import com.example.BES.models.EventGenreBattleGuest;
import com.example.BES.respositories.EventGenreBattleGuestRepo;
import com.example.BES.respositories.EventGenreRepo;
import com.example.BES.respositories.EventRepo;

@Service
public class BattleGuestService {

    @Autowired
    EventRepo eventRepo;

    @Autowired
    EventGenreRepo eventGenreRepo;

    @Autowired
    EventGenreBattleGuestRepo battleGuestRepo;

    public List<GetBattleGuestDto> getBattleGuests(String eventName, String genreName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        EventGenre eventGenre = eventGenreRepo.findByEventAndName(event, genreName).orElse(null);
        if (event == null || eventGenre == null) return List.of();

        return battleGuestRepo.findByEventAndEventGenre(event, eventGenre).stream()
            .map(g -> {
                GetBattleGuestDto dto = new GetBattleGuestDto();
                dto.id = g.getId();
                dto.guestName = g.getGuestName();
                dto.entryRound = g.getEntryRound();
                dto.genreName = g.getEventGenre().getName();
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
                dto.genreName = g.getEventGenre().getName();
                return dto;
            }).collect(Collectors.toList());
    }

    public GetBattleGuestDto addBattleGuest(String eventName, String genreName, AddBattleGuestDto dto) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        EventGenre eventGenre = eventGenreRepo.findByEventAndName(event, genreName).orElse(null);
        if (event == null || eventGenre == null) throw new RuntimeException("Event or genre not found");

        EventGenreBattleGuest guest = new EventGenreBattleGuest();
        guest.setEvent(event);
        guest.setEventGenre(eventGenre);
        guest.setGuestName(dto.guestName);
        guest.setEntryRound(dto.entryRound);
        guest = battleGuestRepo.save(guest);

        GetBattleGuestDto result = new GetBattleGuestDto();
        result.id = guest.getId();
        result.guestName = guest.getGuestName();
        result.entryRound = guest.getEntryRound();
        result.genreName = eventGenre.getName();
        return result;
    }

    public void removeBattleGuest(Long guestId) {
        battleGuestRepo.deleteById(guestId);
    }
}
