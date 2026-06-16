package com.example.BES.dtos;

import java.util.List;

public class GetEventCategoryParticipantDto {
    public String eventName;
    public String participantName;
    public String categoryName;
    public String judgeName;
    public Integer auditionNumber;
    public Boolean walkin;
    public Long participantId;
    public Long eventId;
    public Long eventCategoryId;
    public String referenceCode;
    public List<String> memberNames;
    public String format;
}
