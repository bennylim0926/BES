package com.example.BES.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.security.web.session.HttpSessionDestroyedEvent;
import org.springframework.stereotype.Component;

import com.example.BES.services.ActiveSessionStore;

@Component
public class SessionExpiryListener {

    @Autowired
    private ActiveSessionStore activeSessionStore;

    @EventListener
    public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
        activeSessionStore.deregisterBySessionId(event.getId());
    }
}
