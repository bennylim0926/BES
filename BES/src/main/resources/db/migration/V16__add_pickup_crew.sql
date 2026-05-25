CREATE TABLE pickup_crew (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT NOT NULL REFERENCES event(event_id),
    genre_id    BIGINT NOT NULL REFERENCES genre(genre_id),
    crew_name   VARCHAR(255) NOT NULL
);

CREATE TABLE pickup_crew_member (
    id             BIGSERIAL PRIMARY KEY,
    crew_id        BIGINT NOT NULL REFERENCES pickup_crew(id) ON DELETE CASCADE,
    participant_id BIGINT NOT NULL REFERENCES participant(participant_id),
    UNIQUE (crew_id, participant_id)
);
