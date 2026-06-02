package com.example.BES.services;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class CheckinPreviewService {

    private final Map<String, Set<Long>> activePreviews = new ConcurrentHashMap<>();

    public void setPreview(String eventName, Long participantId) {
        activePreviews
            .computeIfAbsent(eventName, k -> ConcurrentHashMap.newKeySet())
            .add(participantId);
    }

    public void clearPreview(String eventName, Long participantId) {
        Set<Long> previews = activePreviews.get(eventName);
        if (previews != null) {
            previews.remove(participantId);
            if (previews.isEmpty()) {
                activePreviews.remove(eventName);
            }
        }
    }

    public Set<Long> getActivePreviews(String eventName) {
        return activePreviews.getOrDefault(eventName, Collections.emptySet());
    }
}
