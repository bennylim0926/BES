package com.example.BES.dtos;

import java.util.List;

public class GetParticipantFeedbackDto {
    private String judgeName;
    private List<TagEntry> tags;
    private String note;

    public GetParticipantFeedbackDto(String judgeName, List<TagEntry> tags, String note) {
        this.judgeName = judgeName;
        this.tags = tags;
        this.note = note;
    }

    public String getJudgeName() { return judgeName; }
    public List<TagEntry> getTags() { return tags; }
    public String getNote() { return note; }

    public static class TagEntry {
        private String label;
        private String groupName;

        public TagEntry(String label, String groupName) {
            this.label = label;
            this.groupName = groupName;
        }

        public String getLabel() { return label; }
        public String getGroupName() { return groupName; }
    }
}
