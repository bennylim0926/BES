CREATE TABLE event_genre_judge (
    event_genre_id BIGINT NOT NULL REFERENCES event_genre(id) ON DELETE CASCADE,
    judge_id       BIGINT NOT NULL REFERENCES judge(judge_id) ON DELETE CASCADE,
    PRIMARY KEY (event_genre_id, judge_id)
);

DROP TABLE IF EXISTS event_judge;
