-- V45__add_results_release_mode.sql
-- Replaces boolean results_released with a 4-mode VARCHAR enum.
-- NONE = nothing released, SCORE_ONLY = scores only, FEEDBACK_ONLY = feedback only, BOTH = everything.

ALTER TABLE event ADD COLUMN results_release_mode VARCHAR(20) DEFAULT 'NONE';

UPDATE event SET results_release_mode = 'BOTH' WHERE results_released = true;
UPDATE event SET results_release_mode = 'NONE' WHERE results_released = false;

ALTER TABLE event DROP COLUMN results_released;
