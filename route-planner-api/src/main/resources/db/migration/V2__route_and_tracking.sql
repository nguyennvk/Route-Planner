CREATE TYPE assignment_status AS ENUM ('pending', 'accepted', 'rejected');

ALTER TABLE routes
    ADD COLUMN assignment_status assignment_status,
    ADD COLUMN responded_at      timestamptz,
    ADD COLUMN rejection_reason  text,
    ADD COLUMN geometry          text,                -- the OSRM polyline you now return
    ADD COLUMN version           int NOT NULL DEFAULT 0;

ALTER TABLE route_stops ADD COLUMN estimated_arrival timestamptz;   -- updated live while driving
ALTER TABLE orders      ADD COLUMN tracking_token    text UNIQUE;   -- for the customer's link
