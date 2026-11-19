-- Enable route release after the driver reject the route
ALTER TABLE routes
  ADD COLUMN released_at timestamptz;