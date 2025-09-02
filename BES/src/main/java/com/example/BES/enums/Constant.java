package com.example.BES.enums;

public enum Constant {
    SENDER_EMAIL("bennylim0926@gmail.com"),
    DOMAIN("localhost:5050");

    private final String label;
    Constant(String label) { this.label = label; }
    public String getLabel() { return label; }
}
