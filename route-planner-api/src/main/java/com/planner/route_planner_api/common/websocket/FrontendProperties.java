package com.planner.route_planner_api.common.websocket;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties (prefix = "frontend")
public record FrontendProperties(String baseUrl) {}
