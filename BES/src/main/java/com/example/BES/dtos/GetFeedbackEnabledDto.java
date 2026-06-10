package com.example.BES.dtos;

public class GetFeedbackEnabledDto {
    public String eventName;
    public boolean feedbackEnabled;

    public GetFeedbackEnabledDto(String eventName, boolean feedbackEnabled) {
        this.eventName = eventName;
        this.feedbackEnabled = feedbackEnabled;
    }
}
