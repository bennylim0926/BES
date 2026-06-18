package com.example.BES.dtos.battle;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class SetTieBreakerStateDto {

    @NotBlank
    private String eventName;

    @NotBlank
    private String categoryName;

    private String tabulation;

    private String topN;

    private List<String> winners;

    private boolean confirmed;

    private List<String> addedToPool;

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getTabulation() { return tabulation; }
    public void setTabulation(String tabulation) { this.tabulation = tabulation; }
    public String getTopN() { return topN; }
    public void setTopN(String topN) { this.topN = topN; }
    public List<String> getWinners() { return winners; }
    public void setWinners(List<String> winners) { this.winners = winners; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public List<String> getAddedToPool() { return addedToPool; }
    public void setAddedToPool(List<String> addedToPool) { this.addedToPool = addedToPool; }
}
