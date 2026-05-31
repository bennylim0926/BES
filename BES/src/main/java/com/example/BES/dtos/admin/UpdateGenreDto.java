package com.example.BES.dtos.admin;

public class UpdateGenreDto {
    private Long id;
    private String newName;
    private String aliases;

    public Long getId() { return id; }
    public String getNewName() { return newName; }
    public String getAliases() { return aliases; }
}
