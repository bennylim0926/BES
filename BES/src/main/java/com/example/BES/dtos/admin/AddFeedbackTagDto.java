package com.example.BES.dtos.admin;

public class AddFeedbackTagDto {
    private Long groupId;
    private String label;

    public Long getGroupId() { return groupId; }
    public String getLabel() { return label; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public void setLabel(String label) { this.label = label; }
}
