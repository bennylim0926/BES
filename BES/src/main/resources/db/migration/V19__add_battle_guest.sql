CREATE TABLE event_genre_battle_guest (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    genre_id BIGINT NOT NULL REFERENCES genre(genre_id) ON DELETE CASCADE,
    guest_name VARCHAR(255) NOT NULL,
    entry_round VARCHAR(20) NOT NULL
);
