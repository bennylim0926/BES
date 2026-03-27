-- V10: Add reference_code to event_participant and results_released to event

ALTER TABLE event_participant ADD COLUMN reference_code VARCHAR(9);
ALTER TABLE event ADD COLUMN results_released BOOLEAN NOT NULL DEFAULT FALSE;

-- Backfill reference codes for existing participants using md5 hash of their id
UPDATE event_participant
SET reference_code =
    upper(substring(md5(id::text), 1, 4)) || '-' ||
    upper(substring(md5(id::text), 5, 4))
WHERE reference_code IS NULL;

ALTER TABLE event_participant ALTER COLUMN reference_code SET NOT NULL;
CREATE UNIQUE INDEX uq_ep_reference_code ON event_participant(reference_code);
