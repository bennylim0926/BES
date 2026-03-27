-- V12: Dynamic scoring criteria per event (optionally per genre)
-- Allows organisers to define custom scoring aspects with names, max scores, and weights.
-- genre_id NULL = applies to all genres in the event (event-level default).

CREATE TABLE scoring_criteria (
    id            BIGSERIAL PRIMARY KEY,
    event_id      BIGINT NOT NULL REFERENCES event(event_id),
    genre_id      BIGINT REFERENCES genre(genre_id),
    name          VARCHAR(255) NOT NULL,
    max_score     DOUBLE PRECISION NOT NULL DEFAULT 10,
    weight        DOUBLE PRECISION,
    display_order INTEGER NOT NULL DEFAULT 0
);
