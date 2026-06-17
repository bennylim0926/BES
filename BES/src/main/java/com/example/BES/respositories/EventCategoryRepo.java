package com.example.BES.respositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.Event;
import com.example.BES.models.EventCategory;

@Repository
public interface EventCategoryRepo extends JpaRepository<EventCategory, Long>{
    Optional<EventCategory> findByEventAndName(Event event, String name);
    List<EventCategory> findByEvent(Event event);
    void deleteByEvent(Event event);
}
