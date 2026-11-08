package com.planner.route_planner_api.feature.route;

// Matches the Postgres stop_status enum; names must equal the DB labels
public enum StopStatus {
    pending,
    arrived,
    delivered,
    failed,
    skipped
}
