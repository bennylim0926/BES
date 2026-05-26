ALTER TABLE event
  ADD COLUMN overlay_config JSONB NOT NULL DEFAULT '{"showImages": true, "leftColor": "#dc2626", "rightColor": "#2563eb"}';
