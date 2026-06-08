package com.example.BES.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import com.example.BES.services.BattleService;

@Controller
public class BattleTimerController {

    @Autowired
    private BattleService battleService;

    @MessageMapping("/battle/timer")
    public void handleTimerState(Map<String, Object> payload) {
        String eventName = payload != null ? (String) payload.get("eventName") : null;
        if (eventName == null || eventName.isBlank()) {
            eventName = battleService.getActiveEventName();
        }
        if (eventName == null) eventName = "";
        battleService.handleTimerPayload(eventName, payload);
    }
}
