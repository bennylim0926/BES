package com.example.BES.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.example.BES.dtos.AddScoringCriteriaDto;
import com.example.BES.dtos.GetScoringCriteriaDto;
import com.example.BES.dtos.UpdateScoringCriteriaDto;
import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;
import com.example.BES.models.ScoringCriteria;
import com.example.BES.respositories.EventCategoryRepo;
import com.example.BES.respositories.EventRepo;
import com.example.BES.respositories.ScoringCriteriaRepo;

@Service
public class ScoringCriteriaService {

    @Autowired
    ScoringCriteriaRepo repo;

    @Autowired
    EventRepo eventRepo;

    @Autowired
    EventCategoryRepo eventCategoryRepo;

    public List<GetScoringCriteriaDto> getCriteria(String eventName, String categoryName) {
        List<ScoringCriteria> criteria;
        if (categoryName != null && !categoryName.isBlank()) {
            criteria = repo.findByEventNameAndCategoryName(eventName, categoryName);
            if (criteria.isEmpty()) {
                criteria = repo.findEventLevelByEventName(eventName);
            }
        } else {
            criteria = repo.findEventLevelByEventName(eventName);
        }
        return criteria.stream().map(this::toDto).toList();
    }

    public List<GetScoringCriteriaDto> getStrictCriteria(String eventName, String categoryName) {
        List<ScoringCriteria> criteria;
        if (categoryName != null && !categoryName.isBlank()) {
            criteria = repo.findByEventNameAndCategoryName(eventName, categoryName);
        } else {
            criteria = repo.findEventLevelByEventName(eventName);
        }
        return criteria.stream().map(this::toDto).toList();
    }

    public GetScoringCriteriaDto addCriteria(AddScoringCriteriaDto dto) {
        Event event = eventRepo.findByEventName(dto.eventName)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        ScoringCriteria sc = new ScoringCriteria();
        sc.setEvent(event);
        if (dto.categoryName != null && !dto.categoryName.isBlank()) {
            EventCategory eventCategory = eventCategoryRepo.findByEventAndName(event, dto.categoryName)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event category not found"));
            sc.setEventCategory(eventCategory);
        }
        sc.setName(dto.name);
        sc.setWeight(dto.weight);
        sc.setDisplayOrder(dto.displayOrder != null ? dto.displayOrder : 0);
        return toDto(repo.save(sc));
    }

    public GetScoringCriteriaDto updateCriteria(Long id, UpdateScoringCriteriaDto dto) {
        ScoringCriteria sc = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Criterion not found"));
        if (dto.name != null && !dto.name.isBlank()) sc.setName(dto.name);
        sc.setWeight(dto.weight);
        return toDto(repo.save(sc));
    }

    public void removeCriteria(Long id) {
        repo.deleteById(id);
    }

    public void deleteAllCriteria(String eventName, String categoryName) {
        List<ScoringCriteria> criteria;
        if (categoryName != null && !categoryName.isBlank()) {
            criteria = repo.findByEventNameAndCategoryName(eventName, categoryName);
        } else {
            criteria = repo.findEventLevelByEventName(eventName);
        }
        repo.deleteAll(criteria);
    }

    private GetScoringCriteriaDto toDto(ScoringCriteria sc) {
        GetScoringCriteriaDto dto = new GetScoringCriteriaDto();
        dto.id = sc.getId();
        dto.name = sc.getName();
        dto.weight = sc.getWeight();
        dto.displayOrder = sc.getDisplayOrder();
        dto.categoryName = sc.getEventCategory() != null ? sc.getEventCategory().getName() : null;
        return dto;
    }
}
