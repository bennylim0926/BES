package com.example.BES.enums;

public enum Genre {
    POPPING("popping"),
    WAACKING("waacking"),
    LOCKING("locking"),
    BREAKING("breaking"),
    HIPHOP("hip hop"),
    OPEN("open"),
    AUDIENCE("audience"),
    ROOKIE("rookie"),
    SMOKE("7 to smoke");

    private final String label;
    Genre(String label) { this.label = label; }
    public String getLabel() { return label; }
}
