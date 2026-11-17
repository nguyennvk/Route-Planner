ALTER TABLE route_stops
  ADD COLUMN version          int NOT NULL DEFAULT 0;

ALTER TABLE orders
  ADD COLUMN version          int NOT NULL DEFAULT 0;