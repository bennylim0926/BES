package com.example.BES.enums;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "emails")
public class EmailTemplates {

    private Map<String, Template> events = new HashMap<>();

    public Map<String, Template> getEvents() {
        return events;
    }

    public void setEvents(Map<String, Template> events) {
        this.events = events;
    }

    public static class Template {
        private String subject;
        private String body;

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }
    }
}
