package com.planner.route_planner_api.feature.planning.geo_provider;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record OsrmTableResponse(
        String code,
        List<OsrmWaypoint> sources,
        List<OsrmWaypoint> destinations,
        Double[][] durations,     // seconds; null = no route (boxed so nulls survive)
        Double[][] distances,     // meters; only with annotations=duration,distance
        @JsonProperty("fallback_speed_cells") List<int[]> fallbackSpeedCells) {}
