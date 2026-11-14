package com.planner.route_planner_api.feature.planning.constraints;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// The level and weight an owner picks for a rule. Soft is money: a SOFT weight is cents per unit
// (per order left out, per kg over, per minute late), so a weight of 1 makes the rule almost free.
// 0 turns the rule off; Timefold rejects negative weights.
public record ScoreProps(
  @NotNull @PositiveOrZero Long weight,
  @NotNull ScoreType type
) {}
