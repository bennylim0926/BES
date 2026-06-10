package com.example.BES.dtos;

public class GetEventDto {
    Long id;
    String name;
    boolean paymentRequired;
    String accessCode;
    boolean feedbackEnabled = true;

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isPaymentRequired() { return paymentRequired; }
    public String getAccessCode() { return accessCode; }
    public boolean isFeedbackEnabled() { return feedbackEnabled; }
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPaymentRequired(boolean paymentRequired) { this.paymentRequired = paymentRequired; }
    public void setAccessCode(String accessCode) { this.accessCode = accessCode; }
    public void setFeedbackEnabled(boolean feedbackEnabled) { this.feedbackEnabled = feedbackEnabled; }
}
