package com.planner.route_planner_api.feature.planning.constraints;

import com.planner.route_planner_api.feature.planning.CostSettings;
import com.planner.route_planner_api.feature.planning.optimizer.PlannedVehicle;
import com.planner.route_planner_api.feature.planning.optimizer.Visit;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

// The rules that score a plan; Timefold finds this class on startup (it must be the only ConstraintProvider).
// hard = must not break, medium = deliver as many orders as possible, soft = estimated cost in cents.
// Capacity, missing orders and lateness can be re-levelled or re-weighted per solve (PlanningWeights).
public class MyConstraintProvider implements ConstraintProvider {

  // Default price of one minute late on a loose window: $1. The owner can change it with PlanScorePreference.late
  private static final long DEFAULT_LATE_CENTS_PER_MINUTE = 100;

  @Override
  public Constraint[] defineConstraints(ConstraintFactory factory) {
      return new Constraint[] {
        vehicleCapacity(factory),
        strictTimeWindow(factory),
        missingOrder(factory),
        lateOnLooseTimeWindow(factory),
        fuelCost(factory),
        shiftCost(factory),
        overtimeShiftCost(factory)
      };
  }

  // HARD by default: don't overload a vehicle; the penalty grows with each kg over
  Constraint vehicleCapacity(ConstraintFactory factory) {
      return factory.forEach(PlannedVehicle.class)
          .filter(vehicle -> vehicle.getTotalWeight() > vehicle.getCapacity())
          .penalize(HardMediumSoftScore.ONE_HARD, vehicle -> vehicle.getTotalWeight() - vehicle.getCapacity())
          .asConstraint(ConstraintNames.VEHICLE_CAPACITY);
  }

  // HARD: orders with a strict window must be finished inside it, counted in minutes late
  Constraint strictTimeWindow(ConstraintFactory factory) {
    return factory.forEach(Visit.class)
        .filter(visit -> visit.isWindowStrict() && visit.getMinutesLate() > 0)
        .penalize(HardMediumSoftScore.ONE_HARD, Visit::getMinutesLate)
        .asConstraint(ConstraintNames.STRICT_WINDOW);
  }

  // MEDIUM by default: every order left off all routes costs 1.
  // forEachIncludingUnassigned: plain forEach skips visits that aren't on a route, so it would never match
  Constraint missingOrder(ConstraintFactory factory) {
    return factory.forEachIncludingUnassigned(Visit.class)
        .filter(visit -> visit.getVehicle() == null)
        .penalize(HardMediumSoftScore.ONE_MEDIUM)
        .asConstraint(ConstraintNames.MISSING_ORDER);
  }

  // SOFT: lateness on a loose window is allowed but costs money per minute late
  Constraint lateOnLooseTimeWindow(ConstraintFactory factory) {
    return factory.forEach(Visit.class)
        .filter(visit -> !visit.isWindowStrict() && visit.getMinutesLate() > 0)
        .penalize(HardMediumSoftScore.ofSoft(DEFAULT_LATE_CENTS_PER_MINUTE), Visit::getMinutesLate)
        .asConstraint(ConstraintNames.LATE);
  }

  // Fuel for the whole route, depot to depot. Multiply before dividing so short routes don't round to 0.
  // get() can't miss: PlanningService rejects a solve unless every shift has prices
  Constraint fuelCost(ConstraintFactory factory) {
    return factory.forEach(PlannedVehicle.class)
        .filter(vehicle -> !vehicle.getVisits().isEmpty())
        .join(CostSettings.class)
        .penalize(HardMediumSoftScore.ONE_SOFT,
          (vehicle, costs) -> vehicle.getTotalMeters() * costs.fuelCentsPerKm().get(vehicle.getShiftId()) / 1000)
        .asConstraint(ConstraintNames.FUEL_COST);
  }

  // Wages for the time worked inside the shift, waiting included. Unused vehicles cost nothing here
  Constraint shiftCost(ConstraintFactory factory) {
    return factory.forEach(PlannedVehicle.class)
        .filter(vehicle -> !vehicle.getVisits().isEmpty())
        .join(CostSettings.class)
        .penalize(HardMediumSoftScore.ONE_SOFT,
          (vehicle, costs) -> hourlyCents(vehicle.getRegularSeconds(), costs.wageCentsPerHour().get(vehicle.getShiftId())))
        .asConstraint(ConstraintNames.SHIFT_COST);
  }

  // Wages for the time after the shift end, at the overtime rate
  Constraint overtimeShiftCost(ConstraintFactory factory) {
    return factory.forEach(PlannedVehicle.class)
        .filter(vehicle -> vehicle.getOvertimeSeconds() > 0)
        .join(CostSettings.class)
        .penalize(HardMediumSoftScore.ONE_SOFT,
          (vehicle, costs) -> hourlyCents(vehicle.getOvertimeSeconds(), costs.overtimeCentsPerHour().get(vehicle.getShiftId())))
        .asConstraint(ConstraintNames.SHIFT_OVERTIME);
  }

  // Cents per hour × seconds → cents. Multiply before dividing, or every stretch under an hour would round to 0
  private static long hourlyCents(long seconds, long centsPerHour) {
    return seconds * centsPerHour / 3600;
  }
}
