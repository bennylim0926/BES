package com.example.BES.dtos.admin;

import java.util.List;

public class GetOrganiserDto {
    private Long id;
    private String username;
    private List<Long> assignedEventIds;

    public GetOrganiserDto() {}

    public GetOrganiserDto(Long id, String username, List<Long> assignedEventIds) {
        this.id = id;
        this.username = username;
        this.assignedEventIds = assignedEventIds;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<Long> getAssignedEventIds() {
        return assignedEventIds;
    }

    public void setAssignedEventIds(List<Long> assignedEventIds) {
        this.assignedEventIds = assignedEventIds;
    }
}
