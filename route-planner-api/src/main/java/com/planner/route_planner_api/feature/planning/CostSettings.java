package com.planner.route_planner_api.feature.planning;

import java.util.Map;

// Per-shift prices in cents, keyed by shiftId. A problem fact: constraints read it with .join(CostSettings.class)
public record CostSettings(
  Map<Long, Long> fuelCentsPerKm,
  Map<Long, Long> wageCentsPerHour,
  Map<Long, Long> overtimeCentsPerHour
) {}
