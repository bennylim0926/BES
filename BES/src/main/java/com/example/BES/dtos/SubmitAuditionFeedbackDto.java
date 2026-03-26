package com.example.BES.dtos;

import java.util.List;

public class SubmitAuditionFeedbackDto {
    private String eventName;
    private String genreName;
    private String judgeName;
    private Integer auditionNumber;
    private List<Long> tagIds;
    private String note;

    public String getEventName() { return eventName; }
    public String getGenreName() { return genreName; }
    public String getJudgeName() { return judgeName; }
    public Integer getAuditionNumber() { return auditionNumber; }
    public List<Long> getTagIds() { return tagIds; }
    public String getNote() { return note; }

    public void setEventName(String eventName) { this.eventName = eventName; }
    public void setGenreName(String genreName) { this.genreName = genreName; }
    public void setJudgeName(String judgeName) { this.judgeName = judgeName; }
    public void setAuditionNumber(Integer auditionNumber) { this.auditionNumber = auditionNumber; }
    public void setTagIds(List<Long> tagIds) { this.tagIds = tagIds; }
    public void setNote(String note) { this.note = note; }
}
