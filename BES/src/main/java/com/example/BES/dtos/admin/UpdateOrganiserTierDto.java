package com.example.BES.dtos.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public class UpdateOrganiserTierDto {
    @NotNull
    private Long accountId;

    @NotNull
    @Pattern(regexp = "^(PRO|MAX)$", message = "Tier must be PRO or MAX")
    private String tier;

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
}
