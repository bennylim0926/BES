package com.example.BES.dtos;

public class GetEventDto {
    Long id;
    String name;
    boolean paymentRequired;
    boolean feedbackEnabled = true;
    String resultsReleaseMode;

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isPaymentRequired() { return paymentRequired; }
    public boolean isFeedbackEnabled() { return feedbackEnabled; }
    public String getResultsReleaseMode() { return resultsReleaseMode; }
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPaymentRequired(boolean paymentRequired) { this.paymentRequired = paymentRequired; }
    public void setFeedbackEnabled(boolean feedbackEnabled) { this.feedbackEnabled = feedbackEnabled; }
    public void setResultsReleaseMode(String resultsReleaseMode) { this.resultsReleaseMode = resultsReleaseMode; }
}
