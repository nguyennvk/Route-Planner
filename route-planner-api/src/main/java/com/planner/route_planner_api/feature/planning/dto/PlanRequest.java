package com.planner.route_planner_api.feature.planning.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record PlanRequest(
    @NotNull 
    LocalDate planDate,
    @NotNull 
    List<Long> orderIds,
    @NotNull
    List<Long> shiftIds,
    Long timeLimitSeconds,
    Long lateWeight
) {}
