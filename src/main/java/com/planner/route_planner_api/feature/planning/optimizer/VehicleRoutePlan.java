package com.planner.route_planner_api.feature.planning.optimizer;

import java.time.LocalDateTime;
import java.util.List;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.HardMediumSoftScore;
import ai.timefold.solver.core.api.solver.SolverStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@PlanningSolution
@Getter
@Setter
@NoArgsConstructor      // Timefold copies the solution while solving and needs an empty constructor
public class VehicleRoutePlan {
    private String name;

    @PlanningEntityCollectionProperty
    private List<PlannedVehicle> vehicles;

    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    private List<Visit> visits;

    @PlanningScore
    private HardMediumSoftScore score;
    
    private LocalDateTime startDateTime;

    private LocalDateTime endDateTime;


    private SolverStatus solverStatus;

    public VehicleRoutePlan(String name, HardMediumSoftScore score, SolverStatus solverStatus) {
        this.name = name;
        this.score = score;
        this.solverStatus = solverStatus;
    }

    public VehicleRoutePlan(
        String name,
        LocalDateTime startDateTime,
        LocalDateTime endDateTime,
        List<PlannedVehicle> vehicles,
        List<Visit> visits
    ) {
        this.name = name;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.vehicles = vehicles;
        this.visits = visits;
    }

    // A readable, multi-line summary for debugging: System.out.println(plan).
    // Written by hand on purpose: Lombok's @ToString would loop forever (vehicle → visit → vehicle → ...)
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("VehicleRoutePlan \"").append(name).append("\"\n");
        sb.append("  period: ").append(startDateTime).append(" → ").append(endDateTime).append('\n');
        sb.append("  score:  ").append(score).append("   status: ").append(solverStatus).append('\n');

        List<PlannedVehicle> vehicleList = vehicles == null ? List.of() : vehicles;
        sb.append("  vehicles (").append(vehicleList.size()).append("):\n");
        for (PlannedVehicle vehicle : vehicleList) {
            sb.append("    shift ").append(vehicle.getShiftId())
                .append("  vehicle ").append(vehicle.getVehicleId())
                .append("  capacity ").append(vehicle.getCapacity()).append(" kg")
                .append("  home ").append(format(vehicle.getHomeLocation()))
                .append("  leaves ").append(vehicle.getDepartureTime()).append('\n');

            List<Visit> route = vehicle.getVisits() == null ? List.of() : vehicle.getVisits();
            if (route.isEmpty()) {
                sb.append("      (no visits)\n");
            }
            for (int i = 0; i < route.size(); i++) {
                sb.append("      ").append(i + 1).append(". ").append(format(route.get(i))).append('\n');
            }
        }

        // Before solving, every visit is here; after solving, only the ones that didn't fit
        List<Visit> unassigned = visits == null
            ? List.of()
            : visits.stream().filter(visit -> visit.getVehicle() == null).toList();
        sb.append("  unassigned visits (").append(unassigned.size()).append("):\n");
        for (Visit visit : unassigned) {
            sb.append("      - ").append(format(visit)).append('\n');
        }
        return sb.toString();
    }

    private static String format(Visit visit) {
        return String.format("[order %d] %s  %s  %d kg  window %s–%s %s  arrive %s  leave %s%s",
            visit.getOrderId(), visit.getName(), format(visit.getLocation()),
            visit.getWeightKg(),
            time(visit.getMinStartTime()), time(visit.getMaxEndTime()),
            visit.isWindowStrict() ? "strict" : "loose",
            time(visit.getArrivalTime()), time(visit.getDepartureTime()),
            visit.getMinutesLate() > 0 ? "  LATE " + visit.getMinutesLate() + " min" : "");
    }

    private static String format(Location location) {
        return location == null
            ? "(no location)"
            : String.format("(%.6f, %.6f)", location.getLattitude(), location.getLongitude());
    }

    // "08:07", or "-" when not set (no window, or not on a route yet)
    private static String time(LocalDateTime dateTime) {
        return dateTime == null ? "-" : dateTime.toLocalTime().toString();
    }
}
