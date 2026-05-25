package com.example.BES.enums;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum Genre {
    POPPING("popping"),
    WAACKING("waacking", "waack", "whacking"),
    LOCKING("locking", "lock"),
    BREAKING("breaking", "break", "bboy", "bgirl", "b-boy", "b-girl"),
    HIPHOP("hip hop", "hiphop", "hip-hop"),
    OPEN("open"),
    AUDIENCE("audience"),
    ROOKIE("rookie"),
    SMOKE("7 to smoke", "smoke", "seven to smoke", "7tosmoke");

    private final String label;
    private final List<String> aliases;

    Genre(String label, String... aliases) {
        this.label = label;
        this.aliases = Arrays.asList(aliases);
    }

    public String getLabel() { return label; }

    public List<String> getAliases() { return aliases; }

    /** Returns the primary label followed by all aliases — every string that should match this genre. */
    public List<String> getAllMatchStrings() {
        List<String> all = new ArrayList<>();
        all.add(label);
        all.addAll(aliases);
        return all;
    }
}
