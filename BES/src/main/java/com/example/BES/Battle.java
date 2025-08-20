package com.example.BES;

import java.util.ArrayList;
import java.util.List;

public interface Battle {
    public String category = "";
    public List<String> battlerList = new ArrayList<>();
    
    public void setTotalBattlers();
    public void setBattlerList();
    public void updateBattlerList();

}
