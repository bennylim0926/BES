-- Drop unique index on participant_email before dropping the column
ALTER TABLE participant DROP CONSTRAINT IF EXISTS participant_participant_email_key;
ALTER TABLE participant DROP COLUMN IF EXISTS participant_email;

ALTER TABLE event_participant DROP COLUMN IF EXISTS email_sent;
