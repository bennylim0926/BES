package com.example.BES.dtos.admin;

import java.util.List;

public class FeedbackTagOverrideDto {
    private Long globalGroupId;
    private String groupName;
    private List<String> overridingEventNames;

    public FeedbackTagOverrideDto(Long globalGroupId, String groupName, List<String> overridingEventNames) {
        this.globalGroupId = globalGroupId;
        this.groupName = groupName;
        this.overridingEventNames = overridingEventNames;
    }

    public Long getGlobalGroupId() { return globalGroupId; }
    public String getGroupName() { return groupName; }
    public List<String> getOverridingEventNames() { return overridingEventNames; }
}
