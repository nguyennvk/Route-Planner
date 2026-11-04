package com.planner.route_planner_api.feature.planning.dto;

import java.time.Instant;
import java.util.List;

import io.smallrye.common.constraint.NotNull;
import jakarta.validation.constraints.NotBlank;

public record PlanRequest(
    @NotNull 
    String planDate,
    @NotNull 
    List<Long> orderIds,
    @NotNull
    List<Long> shiftIds,
    Long timeLimitSeconds,
    Long lateWeight
) {}
