package com.planner.route_planner_api.feature.planning.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.planner.route_planner_api.feature.planning.constraints.PlanPreference;
import com.planner.route_planner_api.feature.planning.constraints.PlanScorePreference;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;


public record PlanRequest(
    @NotNull
    LocalDate planDate,
    @NotNull
    List<Long> orderIds,
    @NotNull
    List<Long> shiftIds,
    // Prices per shift, keyed by shiftId; every shift being solved needs an entry
    @NotNull
    Map<Long, @Valid PlanPreference> planPreferences,
    // Optional: null keeps every rule's default level and weight
    @Valid
    PlanScorePreference planScorePreference
) {}
