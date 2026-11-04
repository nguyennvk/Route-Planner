package com.planner.route_planner_api.feature.planning.geo_provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties (prefix = "routing.osrm")
public record OsrmProperties(String baseUrl) {}
