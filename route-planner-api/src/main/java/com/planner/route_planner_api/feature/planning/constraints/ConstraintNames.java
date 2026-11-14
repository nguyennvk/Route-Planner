package com.planner.route_planner_api.feature.planning.constraints;

// One source for constraint names: MyConstraintProvider uses them in asConstraint(...),
// PlanningWeights uses them as override keys. A mismatch would make an override apply to nothing.
public final class ConstraintNames {
  public static final String VEHICLE_CAPACITY = "Vehicle Capacity";
  public static final String MISSING_ORDER = "Missing Order";
  public static final String STRICT_WINDOW = "Strict Time Window";
  public static final String LATE = "Late Arrival";
  public static final String FUEL_COST = "Fuel Cost";
  public static final String SHIFT_COST = "Shift Cost";
  public static final String SHIFT_OVERTIME = "Shift Overtime";

  private ConstraintNames() {}
}
