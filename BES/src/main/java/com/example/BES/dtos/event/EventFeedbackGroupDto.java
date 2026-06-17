package com.example.BES.dtos.event;

import java.util.List;

public class EventFeedbackGroupDto {
    private Long id;
    private String name;
    private String scope; // "GLOBAL" or "EVENT"
    private List<EventFeedbackTagDto> tags;

    public EventFeedbackGroupDto(Long id, String name, String scope, List<EventFeedbackTagDto> tags) {
        this.id = id;
        this.name = name;
        this.scope = scope;
        this.tags = tags;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getScope() { return scope; }
    public List<EventFeedbackTagDto> getTags() { return tags; }
}
