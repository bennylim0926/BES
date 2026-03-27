-- V11: Store event-specific display name on event_participant and event_genre_participant
-- This allows the same person (same email) to register under different names across events.

ALTER TABLE event_participant ADD COLUMN display_name VARCHAR(255);
UPDATE event_participant ep
SET display_name = (SELECT p.participant_name FROM participant p WHERE p.participant_id = ep.participant_id);
ALTER TABLE event_participant ALTER COLUMN display_name SET NOT NULL;

ALTER TABLE event_genre_participant ADD COLUMN display_name VARCHAR(255);
UPDATE event_genre_participant egp
SET display_name = (SELECT p.participant_name FROM participant p WHERE p.participant_id = egp.participant_id);
ALTER TABLE event_genre_participant ALTER COLUMN display_name SET NOT NULL;
