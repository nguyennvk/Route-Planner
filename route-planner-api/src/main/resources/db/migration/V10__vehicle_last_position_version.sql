ALTER TABLE vehicle_last_positions 
  ADD COLUMN IF NOT EXISTS version int NOT NULL DEFAULT 0;
