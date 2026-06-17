package com.example.BES.dtos.event;

public class EventFeedbackTagDto {
    private Long id;
    private String label;
    private Long groupId;
    private String scope; // "GLOBAL" or "EVENT"

    public EventFeedbackTagDto(Long id, String label, Long groupId, String scope) {
        this.id = id;
        this.label = label;
        this.groupId = groupId;
        this.scope = scope;
    }

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public Long getGroupId() { return groupId; }
    public String getScope() { return scope; }
}
