-- A driver drives one van at a time, and a van is on one route at a time. DriverService checks this on start
-- with a readable 409; these indexes are the guarantee when two starts race each other.

-- Existing data can already break the rule (routes started and never finished). Keep the most recently
-- started route open and finish the older ones, the same as if their drivers had pressed Finish now.
WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY driver_user_id ORDER BY started_at DESC, id DESC) AS n
    FROM routes
    WHERE started_at IS NOT NULL AND finished_at IS NULL AND driver_user_id IS NOT NULL
)
UPDATE routes SET finished_at = now()
FROM ranked
WHERE routes.id = ranked.id AND ranked.n > 1;

WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY vehicle_id ORDER BY started_at DESC, id DESC) AS n
    FROM routes
    WHERE started_at IS NOT NULL AND finished_at IS NULL
)
UPDATE routes SET finished_at = now()
FROM ranked
WHERE routes.id = ranked.id AND ranked.n > 1;

-- Partial unique indexes: uniqueness only among routes that are in progress
CREATE UNIQUE INDEX routes_one_active_per_driver ON routes (driver_user_id)
    WHERE started_at IS NOT NULL AND finished_at IS NULL;
CREATE UNIQUE INDEX routes_one_active_per_vehicle ON routes (vehicle_id)
    WHERE started_at IS NOT NULL AND finished_at IS NULL;
