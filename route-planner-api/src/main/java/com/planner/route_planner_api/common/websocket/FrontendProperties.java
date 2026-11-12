package com.planner.route_planner_api.common.websocket;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties (prefix = "frontend")
public record FrontendProperties(String baseUrl, List<String> allowedOrigins) {}
