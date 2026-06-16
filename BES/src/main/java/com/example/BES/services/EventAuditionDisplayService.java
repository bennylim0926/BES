package com.example.BES.services;

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

    private String stateKey(String eventName, String categoryName) {
        return eventName + ":" + categoryName;
    }

    public void updateState(String eventName, AuditionDisplayStateDto dto) {
        String key = stateKey(eventName, dto.categoryName);
        stateStore.put(key, dto);
        messagingTemplate.convertAndSend(
            "/topic/audition/" + eventName + "/" + dto.categoryName + "/display", dto);
    }

    public AuditionDisplayStateDto getState(String eventName, String categoryName) {
        return stateStore.get(stateKey(eventName, categoryName));
    }

    /**
     * Backward compatibility method for single-argument calls.
     * Returns the first state found for this event across all categories.
     */
    public AuditionDisplayStateDto getState(String eventName) {
        return stateStore.values().stream()
            .filter(dto -> dto.eventName.equals(eventName))
            .findFirst()
            .orElse(null);
    }
}
