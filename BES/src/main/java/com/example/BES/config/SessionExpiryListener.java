package com.example.BES.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

import com.example.BES.services.ActiveSessionStore;
import com.example.BES.services.DemoService;
import com.example.BES.services.EmceeCategoryStore;
import com.example.BES.services.JudgeActiveStore;

import jakarta.servlet.http.HttpSession;

@Component
public class SessionExpiryListener {

    @Autowired
    private ActiveSessionStore activeSessionStore;

    @Autowired
    private EmceeCategoryStore emceeCategoryStore;

    @Autowired
    private JudgeActiveStore judgeActiveStore;

    @Autowired
    private DemoService demoService;

    @EventListener
    public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
        activeSessionStore.deregisterBySessionId(event.getId());
        emceeCategoryStore.release(event.getId());
        judgeActiveStore.release(event.getId());

        // Clean up demo sandbox if this session was a demo
        try {
            HttpSession session = event.getSession();
            if (session != null) {
                String eventName = (String) session.getAttribute("eventName");
                if (eventName != null && eventName.startsWith("Kyrove Demo-")) {
                    Long eventId = (Long) session.getAttribute("eventId");
                    if (eventId != null) {
                        demoService.purgeSandbox(eventId);
                    }
                }
            }
        } catch (IllegalStateException e) {
            // Session already invalidated — cannot read attributes, skip demo cleanup
        }
    }
}
