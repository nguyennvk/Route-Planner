package com.planner.route_planner_api.feature.planning.geo_provider;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Only the road path is kept; waypoints, legs, durations and the rest are skipped
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsrmRouteResponse(
        String code,
        List<Route> routes
) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Route(String geometry){}
}
