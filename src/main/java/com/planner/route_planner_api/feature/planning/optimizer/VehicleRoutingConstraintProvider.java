package com.planner.route_planner_api.feature.planning.optimizer;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

// The rules that score a plan. Timefold finds this class on startup (it must be the only ConstraintProvider).
// hard = must not break, medium = deliver as many orders as possible, soft = cost to minimise
public class VehicleRoutingConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
            vehicleCapacity(factory),
            strictTimeWindow(factory),
            unassignedVisit(factory),
            lateOnLooseTimeWindow(factory),
            drivingTime(factory)
        };
    }

    // HARD: don't overload a vehicle; the penalty grows with how many kg over it is
    Constraint vehicleCapacity(ConstraintFactory factory) {
        return factory.forEach(PlannedVehicle.class)
            .filter(vehicle -> totalWeightKg(vehicle) > vehicle.getCapacity())
            .penalize(HardMediumSoftScore.ONE_HARD,
                vehicle -> totalWeightKg(vehicle) - vehicle.getCapacity())
            .asConstraint("vehicleCapacity");
    }

    // HARD: orders with a strict window must be finished inside it
    Constraint strictTimeWindow(ConstraintFactory factory) {
        return factory.forEach(Visit.class)                 // forEach skips visits not on any route
            .filter(visit -> visit.isWindowStrict() && visit.getMinutesLate() > 0)
            .penalize(HardMediumSoftScore.ONE_HARD, Visit::getMinutesLate)
            .asConstraint("strictTimeWindow");
    }

    // MEDIUM: every order left off all routes costs 1
    Constraint unassignedVisit(ConstraintFactory factory) {
        return factory.forEachIncludingUnassigned(Visit.class)
            .filter(visit -> visit.getVehicle() == null)
            .penalize(HardMediumSoftScore.ONE_MEDIUM)
            .asConstraint("unassignedVisit");
    }

    // SOFT: lateness on a loose window is allowed but costly: 1 minute late = 5 minutes of driving
    Constraint lateOnLooseTimeWindow(ConstraintFactory factory) {
        return factory.forEach(Visit.class)
            .filter(visit -> !visit.isWindowStrict() && visit.getMinutesLate() > 0)
            .penalize(HardMediumSoftScore.ONE_SOFT, visit -> visit.getMinutesLate() * 60 * 5)
            .asConstraint("lateOnLooseTimeWindow");
    }

    // SOFT: less driving is better (seconds, depot → stops → back to the depot)
    Constraint drivingTime(ConstraintFactory factory) {
        return factory.forEach(PlannedVehicle.class)
            .filter(vehicle -> !vehicle.getVisits().isEmpty())
            .penalize(HardMediumSoftScore.ONE_SOFT, VehicleRoutingConstraintProvider::totalDrivingSeconds)
            .asConstraint("drivingTime");
    }

    private static long totalWeightKg(PlannedVehicle vehicle) {
        return vehicle.getVisits().stream().mapToLong(Visit::getWeightKg).sum();
    }

    private static long totalDrivingSeconds(PlannedVehicle vehicle) {
        long total = 0;
        Location previous = vehicle.getHomeLocation();
        for (Visit visit : vehicle.getVisits()) {
            total += previous.getDrivingTimeTo(visit.getLocation());
            previous = visit.getLocation();
        }
        return total + previous.getDrivingTimeTo(vehicle.getHomeLocation());
    }
}
