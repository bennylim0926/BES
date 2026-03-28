-- Battle format at the event-genre level (e.g. "1v1", "2v2", "3v3")
ALTER TABLE event_genre ADD COLUMN IF NOT EXISTS format VARCHAR(20);

-- Backfill from existing event_genre_participant data (most common format per event-genre)
UPDATE event_genre eg
SET format = (
    SELECT egp.format
    FROM event_genre_participant egp
    WHERE egp.event_id = eg.event_id
      AND egp.genre_id = eg.genre_id
      AND egp.format IS NOT NULL
    GROUP BY egp.format
    ORDER BY COUNT(*) DESC
    LIMIT 1
);
