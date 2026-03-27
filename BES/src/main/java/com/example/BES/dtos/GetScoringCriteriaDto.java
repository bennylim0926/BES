package com.example.BES.dtos;

public class GetScoringCriteriaDto {
    public Long id;
    public String name;
    public Double weight;
    public Integer displayOrder;
    public String genreName;  // null if event-level
}
