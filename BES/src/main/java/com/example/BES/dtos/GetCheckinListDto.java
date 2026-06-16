package com.example.BES.dtos;

import java.util.List;

public class GetCheckinListDto {
    public Long participantId;
    public Long eventId;
    public String label;
    public List<String> memberNames;
    public List<CategoryStatus> categories;

    public static class CategoryStatus {
        public String categoryName;
        public Long eventCategoryId;
        public Integer auditionNumber;
    }
}
