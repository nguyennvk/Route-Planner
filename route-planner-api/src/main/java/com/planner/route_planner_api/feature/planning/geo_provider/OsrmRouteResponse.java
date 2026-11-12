package com.planner.route_planner_api.feature.planning.geo_provider;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Only the road path and its driving time are kept; waypoints, legs and the rest are skipped
@JsonIgnoreProperties(ignoreUnknown = true)
public record OsrmRouteResponse(
        String code,
        List<Route> routes
) {
        // duration: seconds of driving, no traffic, parking or stops
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Route(String geometry, Double duration){}
}
