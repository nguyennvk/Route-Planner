package com.planner.route_planner_api.feature.drivers.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PositionMessage(
    Long routeId,
    Double latitude,
    Double longitude,
    BigDecimal speedKmh,
    LocalDateTime recordedAt
) {}
