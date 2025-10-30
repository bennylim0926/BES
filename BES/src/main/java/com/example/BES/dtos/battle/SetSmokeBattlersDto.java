package com.example.BES.dtos.battle;

import java.util.List;

import com.example.BES.services.BattleService;

public class SetSmokeBattlersDto {
    private List<BattleService.Battler> battlers;

    public List<BattleService.Battler> getBattlers() {
        return battlers;
    }
}
