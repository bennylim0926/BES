-- V45__add_release_score.sql
-- Adds release_score boolean so organisers can configure whether scores appear in public results.
-- Together with feedback_enabled and results_released, these determine what the public results portal shows.

ALTER TABLE event ADD COLUMN IF NOT EXISTS release_score BOOLEAN DEFAULT FALSE;
