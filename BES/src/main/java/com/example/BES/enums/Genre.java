package com.example.BES.enums;

public enum Genre {
    POPPING("Popping"),
    WAACKING("Waacking"),
    LOCKING("Locking"),
    BREAKING("Breaking"),
    HIPHOP("Hiphop"),
    OPEN("Open"),
    AUDIENCE("Audience"),
    ROOKIE("Rookie");

    private final String label;
    Genre(String label) { this.label = label; }
    public String getLabel() { return label; }
}
