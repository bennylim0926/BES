-- V2: Add event_email_template table for per-event email templates
CREATE TABLE IF NOT EXISTS event_email_template (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT NOT NULL UNIQUE REFERENCES event(event_id),
    subject     VARCHAR(500) NOT NULL,
    body        TEXT NOT NULL
);
