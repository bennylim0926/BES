package com.example.BES.dtos;

import java.util.List;

public class CheckinPreviewDto {
    public Long participantId;
    public String name;
    public String refCode;
    public List<String> memberNames;
    public List<CategoryEntry> categories;
    public Boolean cancelled;
    public String eventName;

    public static class CategoryEntry {
        public String categoryName;
        public Integer auditionNumber;
    }
}
