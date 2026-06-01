CREATE TABLE IF NOT EXISTS event_judge (
    event_id BIGINT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    judge_id BIGINT NOT NULL REFERENCES judge(judge_id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, judge_id)
);
