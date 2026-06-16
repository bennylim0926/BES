package com.example.BES.dtos.battle;

public class ChampionRevealDto {
    private String categoryName;
    private String championName;
    private boolean dismiss;
    private String eventName;

    public String getEventName() { return eventName; }

    public String getCategoryName()  { return categoryName; }
    public String getChampionName() { return championName; }
    public boolean isDismiss()      { return dismiss; }
}
