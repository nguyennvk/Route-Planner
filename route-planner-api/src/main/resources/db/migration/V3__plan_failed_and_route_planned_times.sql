-- A solve that threw (OSRM down, solver error) ends here instead of staying 'solving' forever
ALTER TYPE plan_status ADD VALUE 'failed';

-- What the solver planned; started_at / finished_at stay for what the driver actually did
ALTER TABLE routes
    ADD COLUMN planned_start timestamptz,
    ADD COLUMN planned_end   timestamptz,
    ADD CHECK (planned_end >= planned_start);
