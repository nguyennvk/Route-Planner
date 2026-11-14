package com.planner.route_planner_api.feature.planning.constraints;

import java.util.HashMap;
import java.util.Map;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;

// Turns the owner's choices into constraint weights. Rules missing from the map keep the weight in their
// penalize(...). The money rules (fuel, wages, overtime) are never overridden: 1 point is always 1 cent.
public final class PlanningWeights {

  public static ConstraintWeightOverrides<HardMediumSoftScore> from(PlanScorePreference preference) {
    if (preference == null) {
      return ConstraintWeightOverrides.none();
    }
    Map<String, HardMediumSoftScore> weights = new HashMap<>();
    if (preference.capacity() != null) {
      weights.put(ConstraintNames.VEHICLE_CAPACITY, toScore(preference.capacity()));
    }
    if (preference.missingOrder() != null) {
      weights.put(ConstraintNames.MISSING_ORDER, toScore(preference.missingOrder()));
    }
    if (preference.late() != null) {
      weights.put(ConstraintNames.LATE, toScore(preference.late()));
    }
    return ConstraintWeightOverrides.of(weights);
  }

  private PlanningWeights() {}

  // A switch expression over every ScoreType: adding a type without handling it here won't compile
  private static HardMediumSoftScore toScore(ScoreProps props) {
    return switch (props.type()) {
      case HARD -> HardMediumSoftScore.ofHard(props.weight());
      case MEDIUM -> HardMediumSoftScore.ofMedium(props.weight());
      case SOFT -> HardMediumSoftScore.ofSoft(props.weight());
    };
  }
}
