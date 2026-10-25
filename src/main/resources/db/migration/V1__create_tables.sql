-- =============================================================================
-- Route planning & dispatch app: initial schema (PostgreSQL + PostGIS)
-- Generated from the revised DBML.
--
-- Conventions:
--   - every tenant-owned table has company_id, and it leads the indexes you filter by
--   - ids are bigint identity; times are timestamptz (stored in UTC)
--   - locations are PostGIS geography points (SRID 4326, longitude first)
--   - every foreign key column is indexed (directly or as the leading column)
--
-- Written as a Flyway-style migration (V1__init.sql): run once on an empty
-- database. It is intentionally not re-runnable.
-- =============================================================================


-- ---------- Extensions ----------
CREATE EXTENSION IF NOT EXISTS citext;      -- case-insensitive email
CREATE EXTENSION IF NOT EXISTS postgis;     -- geography(Point, 4326)
CREATE EXTENSION IF NOT EXISTS btree_gist;  -- "no overlapping shifts" constraints


-- ---------- Enums ----------
CREATE TYPE member_role  AS ENUM ('owner', 'dispatcher', 'driver');
CREATE TYPE vehicle_type AS ENUM ('motorbike', 'car', 'van', 'truck');
CREATE TYPE order_status AS ENUM ('pending', 'planned', 'out_for_delivery', 'delivered', 'failed', 'cancelled');
CREATE TYPE plan_status  AS ENUM ('draft', 'solving', 'published', 'completed');
CREATE TYPE stop_status  AS ENUM ('pending', 'arrived', 'delivered', 'failed', 'skipped');


-- =============================================================================
-- Accounts and tenancy
-- =============================================================================

CREATE TABLE users (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         citext NOT NULL UNIQUE
                  CHECK (length(email) <= 254 AND email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    password_hash text NOT NULL,          -- argon2id/bcrypt output, never the raw password
    full_name     text CHECK (length(full_name) <= 254),
    phone         text CHECK (phone ~ '^\+[1-9][0-9]{1,14}$'),   -- E.164
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE companies (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       text NOT NULL CHECK (length(name) <= 254),
    timezone   text NOT NULL,             -- IANA name, e.g. Asia/Ho_Chi_Minh; validate in app with ZoneId.of()
    created_at timestamptz NOT NULL DEFAULT now()
);

-- One user can work for several companies with a different role in each.
CREATE TABLE company_members (
    company_id bigint      NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    user_id    bigint      NOT NULL REFERENCES users (id)     ON DELETE CASCADE,
    role       member_role NOT NULL,
    active     boolean     NOT NULL DEFAULT true,
    joined_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (company_id, user_id)
);
CREATE INDEX company_members_user_id_idx         ON company_members (user_id);           -- "which companies am I in?"
CREATE INDEX company_members_company_id_role_idx ON company_members (company_id, role);  -- "list this company's drivers"


-- =============================================================================
-- Places
-- =============================================================================

CREATE TABLE addresses (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         bigint NOT NULL REFERENCES companies (id),
    raw_text           text   NOT NULL CHECK (length(raw_text) <= 512),
    normalized_text    text            CHECK (length(normalized_text) <= 512),
    location           geography(Point, 4326),
    geocode_confidence numeric(3, 2)   CHECK (geocode_confidence BETWEEN 0 AND 1),
    geocode_source     text            CHECK (geocode_source IN ('nominatim', 'google', 'manual_pin')),
    created_at         timestamptz NOT NULL DEFAULT now(),

    -- a location and its source are set together
    CHECK ((location IS NULL) = (geocode_source IS NULL))
);
CREATE INDEX addresses_company_id_normalized_text_idx ON addresses (company_id, normalized_text);  -- dedupe before geocoding
CREATE INDEX addresses_location_idx ON addresses USING gist (location);                            -- stops near a point

COMMENT ON COLUMN addresses.raw_text IS 'exactly what the user typed or imported';
COMMENT ON COLUMN addresses.location IS 'null until geocoded';

CREATE TABLE depots (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id bigint NOT NULL REFERENCES companies (id),
    name       text   NOT NULL CHECK (length(name) <= 254),
    address_id bigint NOT NULL REFERENCES addresses (id)
);
CREATE INDEX depots_company_id_idx ON depots (company_id);
CREATE INDEX depots_address_id_idx ON depots (address_id);


-- =============================================================================
-- Fleet
-- =============================================================================

CREATE TABLE vehicles (
    id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id    bigint       NOT NULL REFERENCES companies (id),
    name          text         NOT NULL CHECK (length(name) <= 254),  -- e.g. "Van 2" or a plate number
    type          vehicle_type NOT NULL,                              -- picks the OSRM routing profile
    capacity_kg   numeric(10, 2) NOT NULL CHECK (capacity_kg > 0),
    capacity_m3   numeric(10, 3)          CHECK (capacity_m3 > 0),
    home_depot_id bigint       NOT NULL REFERENCES depots (id),
    active        boolean      NOT NULL DEFAULT true
);
CREATE INDEX vehicles_company_id_active_idx ON vehicles (company_id, active);
CREATE INDEX vehicles_home_depot_id_idx     ON vehicles (home_depot_id);

-- One row per shift, with its driver.
CREATE TABLE vehicle_shifts (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    vehicle_id     bigint      NOT NULL REFERENCES vehicles (id),
    driver_user_id bigint               REFERENCES users (id),
    start_at       timestamptz NOT NULL,
    end_at         timestamptz NOT NULL,

    CHECK (end_at > start_at),
    -- a vehicle can't be on two overlapping shifts
    CONSTRAINT vehicle_shifts_vehicle_no_overlap
        EXCLUDE USING gist (vehicle_id WITH =, tstzrange(start_at, end_at) WITH &&),
    -- a driver can't be on two overlapping shifts (NULL drivers are ignored)
    CONSTRAINT vehicle_shifts_driver_no_overlap
        EXCLUDE USING gist (driver_user_id WITH =, tstzrange(start_at, end_at) WITH &&)
);
CREATE UNIQUE INDEX vehicle_shifts_vehicle_id_start_at_key ON vehicle_shifts (vehicle_id, start_at);     -- "who is on this vehicle today?"
CREATE INDEX vehicle_shifts_driver_user_id_start_at_idx    ON vehicle_shifts (driver_user_id, start_at); -- "my shifts this week"


-- =============================================================================
-- Customers, catalogue, orders
-- =============================================================================

CREATE TABLE customers (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id         bigint NOT NULL REFERENCES companies (id),
    name               text   NOT NULL CHECK (length(name) <= 254),
    phone              text   CHECK (phone ~ '^\+[1-9][0-9]{1,14}$'),   -- E.164
    email              citext CHECK (length(email) <= 254 AND email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    default_address_id bigint REFERENCES addresses (id),
    created_at         timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX customers_company_id_phone_idx        ON customers (company_id, phone);  -- look up a returning customer
CREATE UNIQUE INDEX customers_company_id_email_key ON customers (company_id, email);  -- unique per company; NULLs never clash
CREATE INDEX customers_default_address_id_idx      ON customers (default_address_id);

CREATE TABLE items (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id bigint NOT NULL REFERENCES companies (id),
    sku        text,
    name       text   NOT NULL CHECK (length(name) <= 254),
    weight_kg  numeric(10, 3) NOT NULL CHECK (weight_kg > 0),
    volume_m3  numeric(10, 4)          CHECK (volume_m3 > 0)
);
CREATE UNIQUE INDEX items_company_id_sku_key ON items (company_id, sku);

CREATE TABLE orders (
    id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id      bigint       NOT NULL REFERENCES companies (id),
    customer_id     bigint       NOT NULL REFERENCES customers (id),
    address_id      bigint       NOT NULL REFERENCES addresses (id),  -- can differ from the customer's default
    status          order_status NOT NULL DEFAULT 'pending',
    window_start    timestamptz,
    window_end      timestamptz,
    window_strict   boolean      NOT NULL DEFAULT false,              -- hard vs soft constraint in the solver
    service_minutes int          NOT NULL DEFAULT 5 CHECK (service_minutes >= 0),  -- time spent at the door
    total_weight_kg numeric(10, 2) NOT NULL CHECK (total_weight_kg >= 0),           -- sum of order_items, denormalized for the solver
    notes           text,
    created_at      timestamptz  NOT NULL DEFAULT now(),

    CHECK (window_end > window_start)   -- passes when either is NULL
);
CREATE INDEX orders_company_id_status_window_start_idx ON orders (company_id, status, window_start);  -- today's orders by status
CREATE INDEX orders_pending_company_id_window_start_idx
    ON orders (company_id, window_start) WHERE status = 'pending';                                    -- main planning query
CREATE INDEX orders_customer_id_idx ON orders (customer_id);
CREATE INDEX orders_address_id_idx  ON orders (address_id);

CREATE TABLE order_items (
    order_id bigint NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    item_id  bigint NOT NULL REFERENCES items (id),
    quantity int    NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (order_id, item_id)
);
CREATE INDEX order_items_item_id_idx ON order_items (item_id);


-- =============================================================================
-- Planning and execution
-- =============================================================================

-- One optimization run for one day. Re-optimizing creates a new plan.
CREATE TABLE plans (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id   bigint      NOT NULL REFERENCES companies (id),
    plan_date    date        NOT NULL,
    status       plan_status NOT NULL DEFAULT 'draft',
    hard_score   bigint,
    medium_score bigint,
    soft_score   bigint,
    created_by   bigint      NOT NULL REFERENCES users (id),
    solved_at    timestamptz,
    published_at timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX plans_company_id_plan_date_status_idx ON plans (company_id, plan_date, status);
CREATE INDEX plans_created_by_idx ON plans (created_by);

-- One vehicle's route inside a plan.
CREATE TABLE routes (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_id        bigint NOT NULL REFERENCES plans (id) ON DELETE CASCADE,
    vehicle_id     bigint NOT NULL REFERENCES vehicles (id),
    shift_id       bigint          REFERENCES vehicle_shifts (id),
    driver_user_id bigint          REFERENCES users (id),
    total_minutes  int             CHECK (total_minutes >= 0),
    total_km       numeric(8, 2)   CHECK (total_km >= 0),
    share_token    text UNIQUE,    -- random token for the driver link
    started_at     timestamptz,
    finished_at    timestamptz,

    CHECK (finished_at >= started_at)
);
CREATE UNIQUE INDEX routes_plan_id_vehicle_id_key ON routes (plan_id, vehicle_id);
CREATE INDEX routes_driver_user_id_started_at_idx  ON routes (driver_user_id, started_at);
CREATE INDEX routes_vehicle_id_idx ON routes (vehicle_id);
CREATE INDEX routes_shift_id_idx   ON routes (shift_id);

-- One visit on a route, in order. planned_* vs actual_* kept separate.
CREATE TABLE route_stops (
    id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_id          bigint      NOT NULL REFERENCES routes (id) ON DELETE CASCADE,
    order_id          bigint      NOT NULL REFERENCES orders (id),
    sequence          int         NOT NULL CHECK (sequence >= 0),
    planned_arrival   timestamptz,
    planned_departure timestamptz,
    actual_arrival    timestamptz,
    actual_departure  timestamptz,
    status            stop_status NOT NULL DEFAULT 'pending',
    failure_reason    text,
    proof_photo_url   text
);
CREATE UNIQUE INDEX route_stops_route_id_sequence_key ON route_stops (route_id, sequence);  -- driver page: stops in order
CREATE INDEX route_stops_order_id_idx ON route_stops (order_id);                           -- customer page: where is my order?


-- =============================================================================
-- Tracking and caching
-- =============================================================================

-- Latest position per vehicle, overwritten (UPSERT) on each GPS ping.
-- Created after routes because it references them.
CREATE TABLE vehicle_last_positions (
    vehicle_id  bigint PRIMARY KEY REFERENCES vehicles (id) ON DELETE CASCADE,
    route_id    bigint REFERENCES routes (id) ON DELETE SET NULL,
    location    geography(Point, 4326) NOT NULL,
    speed_kmh   numeric(5, 1),
    recorded_at timestamptz NOT NULL
);
CREATE INDEX vehicle_last_positions_route_id_idx ON vehicle_last_positions (route_id);

-- High-volume, append-only, partitioned by month on recorded_at.
-- bigserial instead of IDENTITY: identity columns on partitioned tables need PG 17+.
CREATE TABLE gps_points (
    id          bigserial,
    route_id    bigint      NOT NULL REFERENCES routes (id),
    vehicle_id  bigint      NOT NULL REFERENCES vehicles (id),
    recorded_at timestamptz NOT NULL,
    location    geography(Point, 4326) NOT NULL,
    speed_kmh   numeric(5, 1),
    accuracy_m  numeric(6, 1),
    PRIMARY KEY (id, recorded_at)    -- partitioned tables need the partition key in the PK
) PARTITION BY RANGE (recorded_at);

CREATE INDEX gps_points_route_id_recorded_at_idx ON gps_points (route_id, recorded_at);  -- replay one route's trail
CREATE INDEX gps_points_recorded_at_brin_idx ON gps_points USING brin (recorded_at);      -- tiny index for time ranges

-- Monthly partitions. Create future months ahead of time (a scheduled job or pg_partman);
-- the default partition catches anything that arrives before its month exists.
CREATE TABLE gps_points_2026_10 PARTITION OF gps_points
    FOR VALUES FROM ('2026-10-01 00:00+00') TO ('2026-11-01 00:00+00');
CREATE TABLE gps_points_2026_11 PARTITION OF gps_points
    FOR VALUES FROM ('2026-11-01 00:00+00') TO ('2026-12-01 00:00+00');
CREATE TABLE gps_points_default PARTITION OF gps_points DEFAULT;

-- Travel time between two geocoded addresses for one routing profile.
CREATE TABLE travel_time_cache (
    from_address_id bigint NOT NULL REFERENCES addresses (id) ON DELETE CASCADE,
    to_address_id   bigint NOT NULL REFERENCES addresses (id) ON DELETE CASCADE,
    profile         text   NOT NULL,   -- OSRM profile: car, motorbike, ...
    duration_s      int    NOT NULL CHECK (duration_s >= 0),
    distance_m      int    NOT NULL CHECK (distance_m >= 0),
    fetched_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (from_address_id, to_address_id, profile)
);
CREATE INDEX travel_time_cache_to_address_id_idx ON travel_time_cache (to_address_id);

-- v2: customer ETA messages.
CREATE TABLE notifications (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id   bigint NOT NULL REFERENCES orders (id),
    channel    text   NOT NULL CHECK (channel IN ('sms', 'zalo', 'email')),
    status     text   NOT NULL CHECK (status IN ('queued', 'sent', 'failed')),
    body       text,
    sent_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notifications_order_id_created_at_idx ON notifications (order_id, created_at);