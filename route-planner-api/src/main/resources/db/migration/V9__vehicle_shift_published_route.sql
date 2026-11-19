-- A shift is claimed by the route that took it when its plan was published, the same way an order becomes
-- 'planned'. Null means free: it can go into a plan that is then published. Released routes give it back.
-- ON DELETE SET NULL: deleting a plan cascades to its routes, and that must free their shifts, not fail.
-- UNIQUE: one route per claim (NULLs don't count as duplicates in PostgreSQL, so free shifts are fine).
-- version: two publishes racing for the same shift become a 409 for one of them (optimistic locking).
ALTER TABLE vehicle_shifts
    ADD COLUMN published_route_id bigint UNIQUE REFERENCES routes (id) ON DELETE SET NULL,
    ADD COLUMN version            int    NOT NULL DEFAULT 0;

-- Existing data: claim each shift for its route on a published or completed plan, unless that route was released.
-- If the bug already put one shift on two published plans, keep the most recently published one.
UPDATE vehicle_shifts s
SET published_route_id = claimed.route_id
FROM (
    SELECT DISTINCT ON (r.shift_id) r.shift_id, r.id AS route_id
    FROM routes r
    JOIN plans p ON p.id = r.plan_id
    WHERE p.status IN ('published', 'completed')
      AND r.released_at IS NULL
      AND r.shift_id IS NOT NULL
    ORDER BY r.shift_id, p.published_at DESC NULLS LAST, r.id DESC
) claimed
WHERE s.id = claimed.shift_id;
