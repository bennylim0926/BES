package com.example.BES.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AddCategoryToEventDto;
import com.example.BES.dtos.GetEventCategoryDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.respositories.EventCategoryParticipantRepo;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;

@Service
public class EventCategoryService {
    @Autowired
    EventCategoryRepo eventCategoryRepo;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    EventCategoryParticipantRepo eventCategoryParticipantRepo;

    public List<GetEventCategoryDto> getCategoriesByEventService(String eventName) {
        Event e = eventRepo.findByEventNameIgnoreCase(eventName).orElse(null);
        if (e == null) return new ArrayList<>();
        List<GetEventCategoryDto> dtos = new ArrayList<>();
        for (EventCategory ec : eventCategoryRepo.findByEvent(e)) {
            GetEventCategoryDto dto = new GetEventCategoryDto();
            dto.eventCategoryId = ec.getId();
            dto.name = ec.getName();
            dto.format = ec.getFormat();
            dto.roundLabel = ec.getRoundLabel();
            dto.numberColor = ec.getNumberColor();
            dto.sheetAliases = ec.getSheetAliases();
            dto.soloAllowed = ec.isSoloAllowed();
            dto.participantCount = eventCategoryParticipantRepo.countByEventIdAndEventCategoryId(e.getEventId(), ec.getId());
            dtos.add(dto);
        }
        return dtos;
    }

    public void addCategoryToEventService(AddCategoryToEventDto dto) {
        Event e = eventRepo.findByEventName(dto.eventName).orElse(null);
        for (AddCategoryToEventDto.Category cat : dto.categories) {
            if (eventCategoryRepo.findByEventAndName(e, cat.name).isPresent()) {
                throw new DataIntegrityViolationException("This event already has a category named: " + cat.name);
            }
            EventCategory eventCategory = new EventCategory();
            eventCategory.setEvent(e);
            eventCategory.setName(cat.name.trim());
            if (cat.format != null && !cat.format.isBlank()) {
                eventCategory.setFormat(cat.format.trim());
            }
            eventCategoryRepo.save(eventCategory);
        }
    }

    public void updateEventCategoryFormat(Long eventCategoryId, String format) {
        EventCategory ec = eventCategoryRepo.findById(eventCategoryId).orElse(null);
        if (ec == null) throw new RuntimeException("Event category not found");
        ec.setFormat(format == null || format.isBlank() ? null : format.trim());
        eventCategoryRepo.save(ec);
    }

    public void renameDivision(Long id, String name) {
        EventCategory ec = eventCategoryRepo.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        if (name == null || name.isBlank()) throw new RuntimeException("Category name must not be blank");
        ec.setName(name.trim());
        eventCategoryRepo.save(ec);
    }

    public void updateAliases(Long id, String aliases) {
        EventCategory ec = eventCategoryRepo.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        ec.setSheetAliases(aliases == null || aliases.isBlank() ? null : aliases.trim());
        eventCategoryRepo.save(ec);
    }

    public void updateSoloAllowed(Long id, boolean soloAllowed) {
        EventCategory ec = eventCategoryRepo.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        ec.setSoloAllowed(soloAllowed);
        eventCategoryRepo.save(ec);
    }

    public void updateRoundLabel(Long id, String roundLabel) {
        EventCategory ec = eventCategoryRepo.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        ec.setRoundLabel(roundLabel == null || roundLabel.isBlank() ? null : roundLabel.trim());
        eventCategoryRepo.save(ec);
    }

    public void updateNumberColor(Long id, String color) {
        EventCategory ec = eventCategoryRepo.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        ec.setNumberColor(color == null || color.isBlank() ? null : color.trim());
        eventCategoryRepo.save(ec);
    }

    public void deleteDivision(Long id) {
        EventCategory ec = eventCategoryRepo.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        eventCategoryRepo.deleteById(ec.getId());
    }
}
