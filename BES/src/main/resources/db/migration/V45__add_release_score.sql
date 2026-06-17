-- V45__add_release_score.sql
-- Adds release_score boolean alongside existing results_released.
-- results_released = organiser flipped the release switch (Score.vue pill)
-- release_score = organiser enabled scores to be releasable (EventDetails toggle)
-- Together with feedback_enabled, these determine what the public results portal shows.

ALTER TABLE event ADD COLUMN release_score BOOLEAN DEFAULT FALSE;
