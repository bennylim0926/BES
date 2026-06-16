package com.example.BES.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public class SubmitAuditionFeedbackDto {
    @NotBlank @Size(max = 255)
    private String eventName;
    @NotBlank @Size(max = 255)
    private String categoryName;
    @NotBlank @Size(max = 255)
    private String judgeName;
    @NotNull @Min(1)
    private Integer auditionNumber;
    private List<Long> tagIds;
    private String note;

    public String getEventName() { return eventName; }
    public String getCategoryName() { return categoryName; }
    public String getJudgeName() { return judgeName; }
    public Integer getAuditionNumber() { return auditionNumber; }
    public List<Long> getTagIds() { return tagIds; }
    public String getNote() { return note; }

    public void setEventName(String eventName) { this.eventName = eventName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public void setJudgeName(String judgeName) { this.judgeName = judgeName; }
    public void setAuditionNumber(Integer auditionNumber) { this.auditionNumber = auditionNumber; }
    public void setTagIds(List<Long> tagIds) { this.tagIds = tagIds; }
    public void setNote(String note) { this.note = note; }
}
