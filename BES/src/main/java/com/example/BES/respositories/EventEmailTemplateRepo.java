package com.example.BES.respositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.BES.models.EventEmailTemplate;

@Repository
public interface EventEmailTemplateRepo extends JpaRepository<EventEmailTemplate, Long> {
    Optional<EventEmailTemplate> findByEvent_EventName(String eventName);
    Optional<EventEmailTemplate> findByEvent_EventId(Long eventId);
}
