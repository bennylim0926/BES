package com.example.BES.dtos;

public class AddScoringCriteriaDto {
    public String eventName;
    public String genreName;  // null or blank = event-level
    public String name;
    public Double weight;
    public Integer displayOrder;
}
