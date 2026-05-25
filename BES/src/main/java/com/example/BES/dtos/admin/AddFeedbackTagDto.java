package com.example.BES.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AddFeedbackTagDto {
    @NotNull
    private Long groupId;
    @NotBlank @Size(max = 255)
    private String label;

    public Long getGroupId() { return groupId; }
    public String getLabel() { return label; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public void setLabel(String label) { this.label = label; }
}
