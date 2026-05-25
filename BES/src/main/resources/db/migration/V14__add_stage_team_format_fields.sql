-- Individual stage name (display name for 1v1 categories)
ALTER TABLE event_participant ADD COLUMN stage_name VARCHAR(255);

-- Team name (display name for team categories: 2v2, 3v3, 4v4, 5v5)
ALTER TABLE event_participant ADD COLUMN team_name VARCHAR(255);

-- Variable-size team member list (beyond the team leader)
CREATE TABLE event_participant_team_member (
    id                   BIGSERIAL PRIMARY KEY,
    event_participant_id BIGINT NOT NULL REFERENCES event_participant(id) ON DELETE CASCADE,
    member_name          VARCHAR(255) NOT NULL
);

-- Battle format per genre (e.g. "1v1", "2v2", "3v3", null = unspecified)
ALTER TABLE event_genre_participant ADD COLUMN format VARCHAR(20);
