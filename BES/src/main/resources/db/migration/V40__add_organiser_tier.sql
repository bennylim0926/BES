-- V40__add_organiser_tier.sql
-- Add tier column to account table for Pro/Max tier system.
-- Backfill all existing organisers to MAX to preserve current functionality.
ALTER TABLE account
  ADD COLUMN tier VARCHAR(10) NOT NULL DEFAULT 'PRO'
  CHECK (tier IN ('PRO', 'MAX'));

UPDATE account
  SET tier = 'MAX'
  WHERE role = 'ORGANISER';
