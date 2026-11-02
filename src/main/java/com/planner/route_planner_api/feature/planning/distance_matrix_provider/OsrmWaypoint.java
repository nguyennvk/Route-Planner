package com.planner.route_planner_api.feature.planning.distance_matrix_provider;

public record OsrmWaypoint(
        String name,
        double[] location,        // [lon, lat], snapped to the road
        String hint
    ) {}           // don't persist: only valid until the next map update