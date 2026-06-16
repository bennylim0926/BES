package com.example.BES.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddBattleGuestDto;
import com.example.BES.dtos.GetBattleGuestDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.models.EventCategoryBattleGuest;
import com.example.BES.respositories.EventCategoryBattleGuestRepo;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;

@Service
public class BattleGuestService {

    @Autowired
    EventRepo eventRepo;

    @Autowired
    EventCategoryRepo eventCategoryRepo;

    @Autowired
    EventCategoryBattleGuestRepo battleGuestRepo;

    public List<GetBattleGuestDto> getBattleGuests(String eventName, String categoryName) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        EventCategory eventCategory = eventCategoryRepo.findByEventAndName(event, categoryName).orElse(null);
        if (event == null || eventCategory == null) return List.of();

        return battleGuestRepo.findByEventAndEventCategory(event, eventCategory).stream()
            .map(g -> {
                GetBattleGuestDto dto = new GetBattleGuestDto();
                dto.id = g.getId();
                dto.guestName = g.getGuestName();
                dto.entryRound = g.getEntryRound();
                dto.categoryName = g.getEventCategory().getName();
                dto.memberNames = g.getMemberNames();
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
                dto.categoryName = g.getEventCategory().getName();
                dto.memberNames = g.getMemberNames();
                return dto;
            }).collect(Collectors.toList());
    }

    public GetBattleGuestDto addBattleGuest(String eventName, String categoryName, AddBattleGuestDto dto) {
        Event event = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        EventCategory eventCategory = eventCategoryRepo.findByEventAndName(event, categoryName).orElse(null);
        if (event == null || eventCategory == null) throw new RuntimeException("Event or category not found");

        EventCategoryBattleGuest guest = new EventCategoryBattleGuest();
        guest.setEvent(event);
        guest.setEventCategory(eventCategory);
        guest.setGuestName(dto.guestName);
        guest.setEntryRound(dto.entryRound);
        guest.setMemberNames(dto.memberNames);
        guest = battleGuestRepo.save(guest);

        GetBattleGuestDto result = new GetBattleGuestDto();
        result.id = guest.getId();
        result.guestName = guest.getGuestName();
        result.entryRound = guest.getEntryRound();
        result.categoryName = eventCategory.getName();
        result.memberNames = guest.getMemberNames();
        return result;
    }

    public void removeBattleGuest(Long guestId) {
        battleGuestRepo.deleteById(guestId);
    }
}
