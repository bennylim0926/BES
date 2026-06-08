package com.example.BES.dtos.battle;

public class ChampionRevealDto {
    private String genreName;
    private String championName;
    private boolean dismiss;
    private String eventName;

    public String getEventName() { return eventName; }

    public String getGenreName()    { return genreName; }
    public String getChampionName() { return championName; }
    public boolean isDismiss()      { return dismiss; }
}
