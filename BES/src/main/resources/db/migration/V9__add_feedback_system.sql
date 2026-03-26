-- V9__add_feedback_system.sql
-- Adds audition feedback system: configurable tag groups, tags, and per-judge feedback per participant

CREATE TABLE feedback_tag_group (
    id    BIGSERIAL PRIMARY KEY,
    name  VARCHAR(255) NOT NULL
);

CREATE TABLE feedback_tag (
    id        BIGSERIAL PRIMARY KEY,
    label     VARCHAR(255) NOT NULL,
    group_id  BIGINT NOT NULL REFERENCES feedback_tag_group(id) ON DELETE CASCADE
);

CREATE TABLE audition_feedback (
    id              BIGSERIAL PRIMARY KEY,
    event_id        BIGINT NOT NULL,
    genre_id        BIGINT NOT NULL,
    participant_id  BIGINT NOT NULL,
    judge_id        BIGINT NOT NULL REFERENCES judge(judge_id) ON DELETE CASCADE,
    note            TEXT,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (event_id, genre_id, participant_id)
        REFERENCES event_genre_participant(event_id, genre_id, participant_id) ON DELETE CASCADE,
    UNIQUE (event_id, genre_id, participant_id, judge_id)
);

CREATE TABLE audition_feedback_tag (
    feedback_id  BIGINT NOT NULL REFERENCES audition_feedback(id) ON DELETE CASCADE,
    tag_id       BIGINT NOT NULL REFERENCES feedback_tag(id) ON DELETE CASCADE,
    PRIMARY KEY (feedback_id, tag_id)
);

-- Seed two default groups
INSERT INTO feedback_tag_group (name) VALUES ('Strengths'), ('Areas to Improve');
