-- V2 added assignment_status without a default, so a route inserted outside JPA (or before V2) had NULL,
-- and Route.accept()/reject() can't tell "not answered yet" from "missing"
UPDATE routes SET assignment_status = 'pending' WHERE assignment_status IS NULL;

ALTER TABLE routes
    ALTER COLUMN assignment_status SET DEFAULT 'pending',
    ALTER COLUMN assignment_status SET NOT NULL,
    -- Only a turned-down route keeps a reason; accepting clears it
    ADD CHECK (rejection_reason IS NULL OR assignment_status = 'rejected');
