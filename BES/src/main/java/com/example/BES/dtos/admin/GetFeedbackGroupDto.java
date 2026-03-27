package com.example.BES.dtos.admin;

import java.util.List;

public class GetFeedbackGroupDto {
    private Long id;
    private String name;
    private List<GetFeedbackTagDto> tags;

    public GetFeedbackGroupDto(Long id, String name, List<GetFeedbackTagDto> tags) {
        this.id = id;
        this.name = name;
        this.tags = tags;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<GetFeedbackTagDto> getTags() { return tags; }
}
