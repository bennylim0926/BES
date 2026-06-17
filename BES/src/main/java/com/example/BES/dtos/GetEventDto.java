package com.example.BES.dtos;

public class GetEventDto {
    Long id;
    String name;
    boolean paymentRequired;
    boolean feedbackEnabled = true;
    boolean releaseScore;

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isPaymentRequired() { return paymentRequired; }
    public boolean isFeedbackEnabled() { return feedbackEnabled; }
    public boolean isReleaseScore() { return releaseScore; }
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPaymentRequired(boolean paymentRequired) { this.paymentRequired = paymentRequired; }
    public void setFeedbackEnabled(boolean feedbackEnabled) { this.feedbackEnabled = feedbackEnabled; }
    public void setReleaseScore(boolean releaseScore) { this.releaseScore = releaseScore; }
}
