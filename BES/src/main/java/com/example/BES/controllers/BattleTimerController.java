package com.example.BES.controllers;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class BattleTimerController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/battle/timer")
    public void handleTimerState(Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/battle/timer", payload);
    }
}
