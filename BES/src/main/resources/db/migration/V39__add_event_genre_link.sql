-- Persistent "this event uses this genre" link, independent of categories (event_genre rows).
-- Allows an event to declare a genre with zero categories so the UI can show empty group headers.
CREATE TABLE event_genre_link (
    event_id BIGINT NOT NULL REFERENCES event(event_id) ON DELETE CASCADE,
    genre_id BIGINT NOT NULL REFERENCES genre(genre_id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, genre_id)
);

CREATE INDEX idx_event_genre_link_event ON event_genre_link(event_id);
CREATE INDEX idx_event_genre_link_genre ON event_genre_link(genre_id);

-- Backfill: every distinct (event, genre) already represented by an event_genre row.
INSERT INTO event_genre_link (event_id, genre_id)
SELECT DISTINCT event_id, genre_id
FROM event_genre
WHERE genre_id IS NOT NULL
ON CONFLICT DO NOTHING;
