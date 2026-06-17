INSERT INTO app_config (key, value)
VALUES ('demo_passcode', 'CHANGEME')
ON CONFLICT (key) DO NOTHING;

INSERT INTO app_config (key, value)
VALUES ('demo_enabled', 'true')
ON CONFLICT (key) DO NOTHING;
