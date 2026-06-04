package com.example.BES.dtos.admin;

import jakarta.validation.constraints.NotBlank;

public class CreateOrganiserDto {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
