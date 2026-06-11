package com.example.BES.services;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.example.BES.dtos.AuditionDisplayStateDto;

@Service
public class EventAuditionDisplayService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private final ConcurrentHashMap<String, AuditionDisplayStateDto> stateStore = new ConcurrentHashMap<>();

    /**
     * Save state in memory and broadcast to all display clients for this event.
     */
    public void updateState(String eventName, AuditionDisplayStateDto dto) {
        dto.eventName = eventName;
        stateStore.put(eventName, dto);
        messagingTemplate.convertAndSend(
            "/topic/audition/" + eventName + "/display", dto);
    }

    /**
     * Return the current display state for an event, or null if none published yet.
     */
    public AuditionDisplayStateDto getState(String eventName) {
        return stateStore.get(eventName);
    }
}
