ALTER TABLE event
    ADD COLUMN overlay_accent_color VARCHAR(7),
    ADD COLUMN show_round_card BOOLEAN DEFAULT TRUE NOT NULL;
