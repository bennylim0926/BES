-- Add team_name to event_genre_participant
ALTER TABLE event_genre_participant
    ADD COLUMN team_name VARCHAR(255);

-- New table for EGP-level team members
CREATE TABLE event_genre_participant_member (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            BIGINT NOT NULL,
    genre_id            BIGINT NOT NULL,
    participant_id      BIGINT NOT NULL,
    member_name         VARCHAR(255) NOT NULL,
    CONSTRAINT fk_egpm_egp FOREIGN KEY (event_id, genre_id, participant_id)
        REFERENCES event_genre_participant (event_id, genre_id, participant_id)
        ON DELETE CASCADE
);
