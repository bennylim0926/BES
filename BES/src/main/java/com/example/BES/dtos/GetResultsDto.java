package com.example.BES.dtos;

import java.util.List;

public class GetResultsDto {
    private String participantName;
    private String eventName;
    private List<GenreResult> genres;

    public GetResultsDto(String participantName, String eventName, List<GenreResult> genres) {
        this.participantName = participantName;
        this.eventName = eventName;
        this.genres = genres;
    }

    public String getParticipantName() { return participantName; }
    public String getEventName() { return eventName; }
    public List<GenreResult> getGenres() { return genres; }

    public static class GenreResult {
        private String genreName;
        private String format;
        private Integer auditionNumber;
        private List<ScoreEntry> scores;
        private List<FeedbackEntry> feedback;

        public GenreResult(String genreName, String format, Integer auditionNumber,
                           List<ScoreEntry> scores, List<FeedbackEntry> feedback) {
            this.genreName = genreName;
            this.format = format;
            this.auditionNumber = auditionNumber;
            this.scores = scores;
            this.feedback = feedback;
        }

        public String getGenreName() { return genreName; }
        public String getFormat() { return format; }
        public Integer getAuditionNumber() { return auditionNumber; }
        public List<ScoreEntry> getScores() { return scores; }
        public List<FeedbackEntry> getFeedback() { return feedback; }
    }

    public static class ScoreEntry {
        private String judgeName;
        private Double score;
        private String aspect;  // empty string for legacy; criterion name for multi-criteria

        public ScoreEntry(String judgeName, Double score, String aspect) {
            this.judgeName = judgeName;
            this.score = score;
            this.aspect = aspect;
        }

        public String getJudgeName() { return judgeName; }
        public Double getScore() { return score; }
        public String getAspect() { return aspect; }
    }

    public static class FeedbackEntry {
        private String judgeName;
        private List<TagEntry> tags;
        private String note;

        public FeedbackEntry(String judgeName, List<TagEntry> tags, String note) {
            this.judgeName = judgeName;
            this.tags = tags;
            this.note = note;
        }

        public String getJudgeName() { return judgeName; }
        public List<TagEntry> getTags() { return tags; }
        public String getNote() { return note; }
    }

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
