-- Deletes every row of the app's tables but keeps the tables themselves.
-- Local development only: there is no undo.
--
-- Not touched on purpose:
--   flyway_schema_history  Flyway's record of migrations; emptying it makes Flyway try to re-run V1
--   spatial_ref_sys        PostGIS's coordinate systems; geography columns stop working without it
--
-- RESTART IDENTITY: ids start from 1 again. CASCADE: also empties tables that reference these
-- (all of them are listed anyway, so nothing unexpected is emptied).

TRUNCATE TABLE
    notifications,
    travel_time_cache,
    gps_points,
    vehicle_last_positions,
    route_stops,
    routes,
    plans,
    order_items,
    orders,
    items,
    customers,
    vehicle_shifts,
    vehicles,
    depots,
    addresses,
    refresh_tokens,
    company_members,
    companies,
    users
RESTART IDENTITY CASCADE;
