-- V44__add_event_scope_to_feedback_taxonomy.sql
-- Adds optional event scoping to the feedback taxonomy (#157, Task 1).
-- event_id IS NULL  → global tag/group (managed on /admin, current behaviour)
-- event_id NOT NULL → event-scoped (managed on EventDetails, future Task 4)

ALTER TABLE feedback_tag_group
    ADD COLUMN event_id BIGINT NULL REFERENCES event(event_id) ON DELETE CASCADE;

ALTER TABLE feedback_tag
    ADD COLUMN event_id BIGINT NULL REFERENCES event(event_id) ON DELETE CASCADE;

CREATE INDEX idx_feedback_tag_group_event_id ON feedback_tag_group(event_id);
CREATE INDEX idx_feedback_tag_event_id       ON feedback_tag(event_id);
