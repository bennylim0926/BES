package com.example.BES.dtos.admin;

public class GetFeedbackTagDto {
    private Long id;
    private String label;
    private Long groupId;

    public GetFeedbackTagDto(Long id, String label, Long groupId) {
        this.id = id;
        this.label = label;
        this.groupId = groupId;
    }

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public Long getGroupId() { return groupId; }
}
