package com.planner.route_planner_api.feature.planning.constraints;

import jakarta.validation.Valid;

// Optional overrides for the non-money rules; a null field keeps that rule's default weight
public record PlanScorePreference(
  @Valid ScoreProps capacity,
  @Valid ScoreProps missingOrder,
  @Valid ScoreProps late
) {}
