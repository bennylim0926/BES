package com.example.BES.dtos;

import java.util.List;

public class GetAuditionFeedbackDto {
    private List<Long> tagIds;
    private String note;

    public GetAuditionFeedbackDto(List<Long> tagIds, String note) {
        this.tagIds = tagIds;
        this.note = note;
    }

    public List<Long> getTagIds() { return tagIds; }
    public String getNote() { return note; }
}
