package com.example.BES.dtos;

public class JudgeDivisionDto {
    public String divisionName;
    public String format;
    public boolean isAudition;
    public boolean isBattle;

    public JudgeDivisionDto() {}

    public JudgeDivisionDto(String divisionName, String format) {
        this.divisionName = divisionName;
        this.format = format;
    }

    public JudgeDivisionDto(String divisionName, String format, boolean isAudition, boolean isBattle) {
        this.divisionName = divisionName;
        this.format = format;
        this.isAudition = isAudition;
        this.isBattle = isBattle;
    }
}
