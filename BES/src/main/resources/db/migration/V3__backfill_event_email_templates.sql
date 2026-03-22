-- V3: Backfill default email templates for existing events
INSERT INTO event_email_template (event_id, subject, body)
SELECT
    e.event_id,
    'Confirmation email for ' || e.event_name,
    'Hello {name},' || chr(10) || chr(10) ||
    'Thanks for registering for ' || e.event_name || '.' || chr(10) ||
    'Please show this QR code during registration to get your audition number.' || chr(10) || chr(10) ||
    'Thank you.'
FROM event e
WHERE NOT EXISTS (
    SELECT 1 FROM event_email_template t WHERE t.event_id = e.event_id
);
