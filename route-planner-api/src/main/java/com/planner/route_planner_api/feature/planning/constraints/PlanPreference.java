package com.planner.route_planner_api.feature.planning.constraints;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// Prices for one shift. In cents so values like $0.18/km or $17.50/h fit in whole numbers,
// and so they add up directly with the soft score (1 point = 1 cent)
public record PlanPreference(
  @NotNull @PositiveOrZero Long fuelCentsPerKm,
  @NotNull @PositiveOrZero Long wageCentsPerHour,
  @NotNull @PositiveOrZero Long overtimeCentsPerHour
) {}
