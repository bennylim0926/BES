package com.example.BES.dtos.admin;

import jakarta.validation.constraints.NotNull;

public class AssignOrganiserDto {
    @NotNull
    private Long accountId;

    @NotNull
    private Long eventId;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }
}
