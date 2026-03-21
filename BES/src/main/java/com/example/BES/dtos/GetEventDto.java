package com.example.BES.dtos;

public class GetEventDto {
    Long id;
    String name;
    boolean paymentRequired;

    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isPaymentRequired() { return paymentRequired; }
    public void setId(Long id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPaymentRequired(boolean paymentRequired) { this.paymentRequired = paymentRequired; }
}
