-- V42: Rename event_participant.genre column → event_participant.category
ALTER TABLE event_participant RENAME COLUMN genre TO category;
