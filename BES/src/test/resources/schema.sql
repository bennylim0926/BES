CREATE TABLE IF NOT EXISTS event_judge (
    event_id BIGINT NOT NULL,
    judge_id BIGINT NOT NULL,
    PRIMARY KEY (event_id, judge_id)
);
