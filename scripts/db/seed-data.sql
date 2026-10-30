-- Demo data for local development. Generated once; safe to read and edit by hand.
-- Run after reset-data.sql (or use reset-and-seed.sh). Every user's password is Password123.
--
-- Addresses are real public places (city halls, libraries, malls, universities), geocoded once with
-- Geocodio, so no API calls are needed to re-run this. Names, emails (example.com) and phone numbers
-- (555-01xx, reserved for fiction) are made up.
--
-- Ids are written out (OVERRIDING SYSTEM VALUE) so rows can reference each other; the sequences are
-- moved past them at the end, otherwise the app's next insert would reuse id 1 and fail.

BEGIN;

-- 10 users: 3 owners (ids 1-3), 7 drivers (ids 4-10)
INSERT INTO users (id, email, password_hash, full_name, phone) OVERRIDING SYSTEM VALUE VALUES
    (1, 'olivia.chen@example.com', '{bcrypt}$2a$10$Ff9y2EA9D.Mif641Magj5.7Ktd.Cl3uDwAXVt00CkudGZUR0wirvC', 'Olivia Chen', '+14165550101'),
    (2, 'marcus.patel@example.com', '{bcrypt}$2a$10$Ff9y2EA9D.Mif641Magj5.7Ktd.Cl3uDwAXVt00CkudGZUR0wirvC', 'Marcus Patel', '+14165550102'),
    (3, 'sofia.rossi@example.com', '{bcrypt}$2a$10$Ff9y2EA9D.Mif641Magj5.7Ktd.Cl3uDwAXVt00CkudGZUR0wirvC', 'Sofia Rossi', '+14165550103'),
    (4, 'liam.tremblay@example.com', '{bcrypt}$2a$10$Ff9y2EA9D.Mif641Magj5.7Ktd.Cl3uDwAXVt00CkudGZUR0wirvC', 'Liam Tremblay', '+14165550104'),
    (5, 'aisha.mohammed@example.com', '{bcrypt}$2a$10$Ff9y2EA9D.Mif641Magj5.7Ktd.Cl3uDwAXVt00CkudGZUR0wirvC', 'Aisha Mohammed', '+14165550105'),
    (6, 'noah.nguyen@example.com', '{bcrypt}$2a$10$Ff9y2EA9D.Mif641Magj5.7Ktd.Cl3uDwAXVt00CkudGZUR0wirvC', 'Noah Nguyen', '+14165550106'),
    (7, 'emma.wilson@example.com', '{bcrypt}$2a$10$Ff9y2EA9D.Mif641Magj5.7Ktd.Cl3uDwAXVt00CkudGZUR0wirvC', 'Emma Wilson', '+14165550107'),
    (8, 'daniel.kim@example.com', '{bcrypt}$2a$10$Ff9y2EA9D.Mif641Magj5.7Ktd.Cl3uDwAXVt00CkudGZUR0wirvC', 'Daniel Kim', '+14165550108'),
    (9, 'priya.sharma@example.com', '{bcrypt}$2a$10$Ff9y2EA9D.Mif641Magj5.7Ktd.Cl3uDwAXVt00CkudGZUR0wirvC', 'Priya Sharma', '+14165550109'),
    (10, 'lucas.martin@example.com', '{bcrypt}$2a$10$Ff9y2EA9D.Mif641Magj5.7Ktd.Cl3uDwAXVt00CkudGZUR0wirvC', 'Lucas Martin', '+16045550110');

-- Owner 1 and owner 2 have one company each; owner 3 (Sofia Rossi) has two
INSERT INTO companies (id, name, timezone) OVERRIDING SYSTEM VALUE VALUES
    (1, 'Maple Water Co.', 'America/Toronto'),
    (2, 'Lakeshore Bakery Supply', 'America/Toronto'),
    (3, 'Northern Office Supplies', 'America/Toronto'),
    (4, 'Pacific Fresh Produce', 'America/Vancouver');

INSERT INTO company_members (company_id, user_id, role) VALUES
    (1, 1, 'owner'),
    (1, 4, 'driver'),
    (1, 5, 'driver'),
    (2, 2, 'owner'),
    (2, 6, 'driver'),
    (2, 7, 'driver'),
    (3, 3, 'owner'),
    (3, 8, 'driver'),
    (3, 9, 'driver'),
    (4, 3, 'owner'),
    (4, 10, 'driver');

-- 6 addresses per company: the first is the depot, the other 5 are customers
INSERT INTO addresses (id, company_id, raw_address, normalized_address, location, geocode_confidence, geocode_source) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, '40 Bay St, Toronto, ON M5J 2X2', '40 Bay St, Toronto, ON M5J 2X2', ST_SetSRID(ST_MakePoint(-79.377975, 43.643878), 4326)::geography, 1.00, 'geocodio'),
    (2, 1, '100 Queen St W, Toronto, ON M5H 2N2', '100 Queen St W, Toronto, ON M5H 2N2', ST_SetSRID(ST_MakePoint(-79.382497, 43.651504), 4326)::geography, 1.00, 'geocodio'),
    (3, 1, '789 Yonge St, Toronto, ON M4W 2G8', '789 Yonge St, Toronto, ON M4W 2G8', ST_SetSRID(ST_MakePoint(-79.38667, 43.671879), 4326)::geography, 1.00, 'geocodio'),
    (4, 1, '317 Dundas St W, Toronto, ON M5T 1G4', '317 Dundas St W, Toronto, ON M5T 1G4', ST_SetSRID(ST_MakePoint(-79.391454, 43.654328), 4326)::geography, 1.00, 'geocodio'),
    (5, 1, '100 Queens Park, Toronto, ON M5S 2C6', '100 Queen''s Park, Toronto, ON M5S 2C6', ST_SetSRID(ST_MakePoint(-79.394212, 43.667795), 4326)::geography, 1.00, 'geocodio'),
    (6, 1, '55 Mill St, Toronto, ON M5A 3C4', '55 Mill St, Toronto, ON M5A 3C4', ST_SetSRID(ST_MakePoint(-79.359662, 43.650639), 4326)::geography, 1.00, 'geocodio'),
    (7, 2, '5100 Erin Mills Pkwy, Mississauga, ON L5M 4Z5', '5100 Erin Mills Pkwy, Mississauga, ON L5M 4Z5', ST_SetSRID(ST_MakePoint(-79.710937, 43.561559), 4326)::geography, 1.00, 'geocodio'),
    (8, 2, '300 City Centre Dr, Mississauga, ON L5B 3C1', '300 City Centre Dr, Mississauga, ON L5B 3C1', ST_SetSRID(ST_MakePoint(-79.644302, 43.588768), 4326)::geography, 1.00, 'geocodio'),
    (9, 2, '301 Burnhamthorpe Rd W, Mississauga, ON L5B 3Y3', '301 Burnhamthorpe Rd W, Mississauga, ON L5B 3Y3', ST_SetSRID(ST_MakePoint(-79.643642, 43.587565), 4326)::geography, 1.00, 'geocodio'),
    (10, 2, '100 City Centre Dr, Mississauga, ON L5B 2C9', '100 City Centre Dr, Mississauga, ON L5B 2C9', ST_SetSRID(ST_MakePoint(-79.639494, 43.592121), 4326)::geography, 1.00, 'geocodio'),
    (11, 2, '3359 Mississauga Rd, Mississauga, ON L5L 1C6', '3359 Mississauga Rd, Mississauga, ON L5L 1C6', ST_SetSRID(ST_MakePoint(-79.668305, 43.548706), 4326)::geography, 1.00, 'geocodio'),
    (12, 2, '1 Port St E, Mississauga, ON L5G 4N1', '1 Port St E, Mississauga, ON L5G 4N1', ST_SetSRID(ST_MakePoint(-79.584021, 43.551299), 4326)::geography, 1.00, 'geocodio'),
    (13, 3, '3401 Dufferin St, Toronto, ON M6A 2T9', '3401 Dufferin St, North York, ON M6A 2T9', ST_SetSRID(ST_MakePoint(-79.451664, 43.726016), 4326)::geography, 1.00, 'geocodio'),
    (14, 3, '5100 Yonge St, Toronto, ON M2N 5V7', '5100 Yonge St, North York, ON M2N 5V7', ST_SetSRID(ST_MakePoint(-79.414889, 43.767268), 4326)::geography, 1.00, 'geocodio'),
    (15, 3, '4700 Keele St, Toronto, ON M3J 1P3', '4700 Keele St, North York, ON M3J 1P3', ST_SetSRID(ST_MakePoint(-79.502533, 43.773355), 4326)::geography, 1.00, 'geocodio'),
    (16, 3, '1800 Sheppard Ave E, Toronto, ON M2J 5A7', '1800 Sheppard Ave E, North York, ON M2J 5A7', ST_SetSRID(ST_MakePoint(-79.346705, 43.77525), 4326)::geography, 1.00, 'geocodio'),
    (17, 3, '2075 Bayview Ave, Toronto, ON M4N 3M5', '2075 Bayview Ave, Toronto, ON M4N 3M5', ST_SetSRID(ST_MakePoint(-79.377875, 43.721938), 4326)::geography, 1.00, 'geocodio'),
    (18, 3, '2300 Yonge St, Toronto, ON M4P 1E4', '2300 Yonge St, Toronto, ON M4P 1E4', ST_SetSRID(ST_MakePoint(-79.398597, 43.707503), 4326)::geography, 1.00, 'geocodio'),
    (19, 4, '1055 Canada Pl, Vancouver, BC V6C 0C3', '1055 Canada Pl, Vancouver, BC V6C 0C3', ST_SetSRID(ST_MakePoint(-123.116032, 49.288419), 4326)::geography, 1.00, 'geocodio'),
    (20, 4, '453 W 12th Ave, Vancouver, BC V5Y 1V4', '453 12th Ave W, Vancouver, BC V5Y 1V4', ST_SetSRID(ST_MakePoint(-123.113949, 49.261315), 4326)::geography, 0.92, 'geocodio'),
    (21, 4, '350 W Georgia St, Vancouver, BC V6B 6B1', '350 W Georgia St, Vancouver, BC V6B 6B1', ST_SetSRID(ST_MakePoint(-123.115556, 49.279698), 4326)::geography, 1.00, 'geocodio'),
    (22, 4, '750 Hornby St, Vancouver, BC V6Z 2H7', '750 Hornby St, Vancouver, BC V6Z 2H7', ST_SetSRID(ST_MakePoint(-123.120085, 49.2836), 4326)::geography, 1.00, 'geocodio'),
    (23, 4, '701 W Georgia St, Vancouver, BC V7Y 1G5', '701 Georgia St W, Vancouver, BC V7Y 1G5', ST_SetSRID(ST_MakePoint(-123.118227, 49.282358), 4326)::geography, 0.91, 'geocodio'),
    (24, 4, '1661 Duranleau St, Vancouver, BC V6H 3S3', '1661 Duranleau St, Vancouver, BC V6H 3S3', ST_SetSRID(ST_MakePoint(-123.136106, 49.272008), 4326)::geography, 1.00, 'geocodio');

INSERT INTO depots (id, company_id, name, address_id) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 'Downtown Toronto depot', 1),
    (2, 2, 'Erin Mills depot', 7),
    (3, 3, 'North York depot', 13),
    (4, 4, 'Vancouver waterfront depot', 19);

-- 5 customers per company, each with a default address
INSERT INTO customers (id, company_id, name, phone, email, default_address_id) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 'Hannah Lee', '+14165550201', 'hannah.lee@example.com', 2),
    (2, 1, 'Omar Haddad', '+14165550202', 'omar.haddad@example.com', 3),
    (3, 1, 'Grace Thompson', '+14165550203', 'grace.thompson@example.com', 4),
    (4, 1, 'Mateo Silva', '+14165550204', 'mateo.silva@example.com', 5),
    (5, 1, 'Chloe Dubois', '+14165550205', 'chloe.dubois@example.com', 6),
    (6, 2, 'Ethan Brooks', '+14165550206', 'ethan.brooks@example.com', 8),
    (7, 2, 'Fatima Khan', '+14165550207', 'fatima.khan@example.com', 9),
    (8, 2, 'Jack O''Connor', '+14165550208', 'jack.oconnor@example.com', 10),
    (9, 2, 'Mei Wong', '+14165550209', 'mei.wong@example.com', 11),
    (10, 2, 'Samuel Adeyemi', '+14165550210', 'samuel.adeyemi@example.com', 12),
    (11, 3, 'Isabella Moreau', '+14165550211', 'isabella.moreau@example.com', 14),
    (12, 3, 'Ravi Gupta', '+14165550212', 'ravi.gupta@example.com', 15),
    (13, 3, 'Zoe Campbell', '+14165550213', 'zoe.campbell@example.com', 16),
    (14, 3, 'Arjun Mehta', '+14165550214', 'arjun.mehta@example.com', 17),
    (15, 3, 'Lily Zhang', '+14165550215', 'lily.zhang@example.com', 18),
    (16, 4, 'Benjamin Clarke', '+16045550216', 'benjamin.clarke@example.com', 20),
    (17, 4, 'Yuki Tanaka', '+16045550217', 'yuki.tanaka@example.com', 21),
    (18, 4, 'Ava Robinson', '+16045550218', 'ava.robinson@example.com', 22),
    (19, 4, 'Diego Alvarez', '+16045550219', 'diego.alvarez@example.com', 23),
    (20, 4, 'Nora Fraser', '+16045550220', 'nora.fraser@example.com', 24);

INSERT INTO items (id, company_id, sku, name, weight_kg, volume_m3) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 'WATER-20L', '20L water bottle', 20.500, 0.0210),
    (2, 1, 'WATER-5L', '5L water jug', 5.200, 0.0060),
    (3, 1, 'DISP-01', 'Water dispenser', 12.000, 0.0800),
    (4, 2, 'FLOUR-20', 'Flour, 20 kg bag', 20.000, 0.0300),
    (5, 2, 'SUGAR-10', 'Sugar, 10 kg bag', 10.000, 0.0120),
    (6, 2, 'BUTTER-CS', 'Butter, 25 lb case', 11.340, 0.0150),
    (7, 3, 'PAPER-CASE', 'Copy paper, case of 10 reams', 22.700, 0.0300),
    (8, 3, 'TONER-01', 'Toner cartridge', 1.200, 0.0040),
    (9, 3, 'CHAIR-01', 'Office chair', 15.000, 0.2500),
    (10, 4, 'APPLE-CR', 'Apples, crate', 18.000, 0.0400),
    (11, 4, 'LETTUCE-CS', 'Lettuce, case', 9.000, 0.0300),
    (12, 4, 'BERRY-FL', 'Berries, flat', 4.500, 0.0100);

-- 6 orders per company: 5 pending (Mon 2 and Tue 3 Nov 2026, one with no window) and 1 cancelled.
-- Windows are 9:00-16:00 local time, stored in UTC. total_weight_kg is filled in from the lines below.
INSERT INTO orders (id, company_id, customer_id, address_id, status, window_start, window_end, window_strict, service_minutes, total_weight_kg, notes) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, 2, 'pending', '2026-11-02T14:00:00Z', '2026-11-02T16:00:00Z', true, 5, 0, 'Call before arriving'),
    (2, 1, 2, 3, 'pending', '2026-11-02T15:00:00Z', '2026-11-02T17:00:00Z', false, 5, 0, NULL),
    (3, 1, 3, 4, 'pending', '2026-11-02T18:00:00Z', '2026-11-02T20:00:00Z', true, 10, 0, 'Loading dock at the back'),
    (4, 1, 4, 5, 'pending', NULL, NULL, false, 5, 0, 'Any time during the day'),
    (5, 1, 5, 6, 'pending', '2026-11-03T14:00:00Z', '2026-11-03T17:00:00Z', false, 8, 0, NULL),
    (6, 1, 1, 2, 'cancelled', '2026-11-03T19:00:00Z', '2026-11-03T21:00:00Z', false, 5, 0, 'Customer cancelled by phone'),
    (7, 2, 6, 8, 'pending', '2026-11-02T14:00:00Z', '2026-11-02T16:00:00Z', true, 5, 0, 'Call before arriving'),
    (8, 2, 7, 9, 'pending', '2026-11-02T15:00:00Z', '2026-11-02T17:00:00Z', false, 5, 0, NULL),
    (9, 2, 8, 10, 'pending', '2026-11-02T18:00:00Z', '2026-11-02T20:00:00Z', true, 10, 0, 'Loading dock at the back'),
    (10, 2, 9, 11, 'pending', NULL, NULL, false, 5, 0, 'Any time during the day'),
    (11, 2, 10, 12, 'pending', '2026-11-03T14:00:00Z', '2026-11-03T17:00:00Z', false, 8, 0, NULL),
    (12, 2, 6, 8, 'cancelled', '2026-11-03T19:00:00Z', '2026-11-03T21:00:00Z', false, 5, 0, 'Customer cancelled by phone'),
    (13, 3, 11, 14, 'pending', '2026-11-02T14:00:00Z', '2026-11-02T16:00:00Z', true, 5, 0, 'Call before arriving'),
    (14, 3, 12, 15, 'pending', '2026-11-02T15:00:00Z', '2026-11-02T17:00:00Z', false, 5, 0, NULL),
    (15, 3, 13, 16, 'pending', '2026-11-02T18:00:00Z', '2026-11-02T20:00:00Z', true, 10, 0, 'Loading dock at the back'),
    (16, 3, 14, 17, 'pending', NULL, NULL, false, 5, 0, 'Any time during the day'),
    (17, 3, 15, 18, 'pending', '2026-11-03T14:00:00Z', '2026-11-03T17:00:00Z', false, 8, 0, NULL),
    (18, 3, 11, 14, 'cancelled', '2026-11-03T19:00:00Z', '2026-11-03T21:00:00Z', false, 5, 0, 'Customer cancelled by phone'),
    (19, 4, 16, 20, 'pending', '2026-11-02T17:00:00Z', '2026-11-02T19:00:00Z', true, 5, 0, 'Call before arriving'),
    (20, 4, 17, 21, 'pending', '2026-11-02T18:00:00Z', '2026-11-02T20:00:00Z', false, 5, 0, NULL),
    (21, 4, 18, 22, 'pending', '2026-11-02T21:00:00Z', '2026-11-02T23:00:00Z', true, 10, 0, 'Loading dock at the back'),
    (22, 4, 19, 23, 'pending', NULL, NULL, false, 5, 0, 'Any time during the day'),
    (23, 4, 20, 24, 'pending', '2026-11-03T17:00:00Z', '2026-11-03T20:00:00Z', false, 8, 0, NULL),
    (24, 4, 16, 20, 'cancelled', '2026-11-03T22:00:00Z', '2026-11-04T00:00:00Z', false, 5, 0, 'Customer cancelled by phone');

INSERT INTO order_items (order_id, item_id, quantity) VALUES
    (1, 1, 3),
    (2, 1, 2),
    (2, 2, 4),
    (3, 3, 1),
    (4, 2, 6),
    (5, 1, 5),
    (5, 3, 1),
    (6, 2, 2),
    (7, 4, 3),
    (8, 4, 2),
    (8, 5, 4),
    (9, 6, 1),
    (10, 5, 6),
    (11, 4, 5),
    (11, 6, 1),
    (12, 5, 2),
    (13, 7, 3),
    (14, 7, 2),
    (14, 8, 4),
    (15, 9, 1),
    (16, 8, 6),
    (17, 7, 5),
    (17, 9, 1),
    (18, 8, 2),
    (19, 10, 3),
    (20, 10, 2),
    (20, 11, 4),
    (21, 12, 1),
    (22, 11, 6),
    (23, 10, 5),
    (23, 12, 1),
    (24, 11, 2);

-- Same calculation as Order.recalculateTotalWeight(): sum of unit weight * quantity, 2 decimals
UPDATE orders o SET total_weight_kg = (
    SELECT round(sum(i.weight_kg * oi.quantity), 2)
    FROM order_items oi JOIN items i ON i.id = oi.item_id
    WHERE oi.order_id = o.id
);

-- Move each id sequence past the ids written above
SELECT setval(pg_get_serial_sequence('users', 'id'), (SELECT max(id) FROM users));
SELECT setval(pg_get_serial_sequence('companies', 'id'), (SELECT max(id) FROM companies));
SELECT setval(pg_get_serial_sequence('addresses', 'id'), (SELECT max(id) FROM addresses));
SELECT setval(pg_get_serial_sequence('depots', 'id'), (SELECT max(id) FROM depots));
SELECT setval(pg_get_serial_sequence('customers', 'id'), (SELECT max(id) FROM customers));
SELECT setval(pg_get_serial_sequence('items', 'id'), (SELECT max(id) FROM items));
SELECT setval(pg_get_serial_sequence('orders', 'id'), (SELECT max(id) FROM orders));

COMMIT;
