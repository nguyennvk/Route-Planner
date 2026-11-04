package com.planner.route_planner_api.feature.planning.distance_matrix_provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties (prefix = "routing.osrm")
public record OsrmProperties(String baseUrl) {}
