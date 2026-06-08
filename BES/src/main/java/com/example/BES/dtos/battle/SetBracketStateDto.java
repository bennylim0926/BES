package com.example.BES.dtos.battle;

public class SetBracketStateDto {
    private String topSize;
    private Object rounds;
    private Integer currentRoundIndex;
    private String eventName;

    public String getEventName() { return eventName; }

    public String getTopSize() {
        return topSize;
    }
    public void setTopSize(String topSize) {
        this.topSize = topSize;
    }
    public Object getRounds() {
        return rounds;
    }
    public void setRounds(Object rounds) {
        this.rounds = rounds;
    }
    public Integer getCurrentRoundIndex() {
        return currentRoundIndex;
    }
    public void setCurrentRoundIndex(Integer currentRoundIndex) {
        this.currentRoundIndex = currentRoundIndex;
    }
}
