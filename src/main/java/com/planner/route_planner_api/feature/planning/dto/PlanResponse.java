package com.planner.route_planner_api.feature.planning.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.planner.route_planner_api.feature.planning.optimizer.PlannedVehicle;
import com.planner.route_planner_api.feature.planning.optimizer.Visit;
import com.planner.route_planner_api.feature.planning.optimizer.VehicleRoutePlan;

// What the API returns for a solved plan. VehicleRoutePlan can't be sent as JSON directly:
// vehicle → visits → visit.vehicle → vehicle → ... never ends
public record PlanResponse(
    String name,
    String score,                   // e.g. "0hard/0medium/-1037soft"
    List<RouteResponse> routes,
    List<Long> unassignedOrderIds   // orders that didn't fit on any route
) {

    public record RouteResponse(String vehicleId, List<StopResponse> stops) {}

    public record StopResponse(
        int sequence,               // 0 = first stop after the depot
        Long orderId,
        String name,
        LocalDateTime arrivalTime,
        LocalDateTime departureTime,
        long minutesLate
    ) {}

    public static PlanResponse from(VehicleRoutePlan plan) {
        List<RouteResponse> routes = plan.getVehicles().stream()
            .map(PlanResponse::toRoute)
            .toList();
        List<Long> unassigned = plan.getVisits().stream()
            .filter(visit -> visit.getVehicle() == null)
            .map(Visit::getOrderId)
            .toList();
        return new PlanResponse(plan.getName(), String.valueOf(plan.getScore()), routes, unassigned);
    }

    private static RouteResponse toRoute(PlannedVehicle vehicle) {
        List<Visit> visits = vehicle.getVisits();
        List<StopResponse> stops = new java.util.ArrayList<>();
        for (int i = 0; i < visits.size(); i++) {
            Visit visit = visits.get(i);
            stops.add(new StopResponse(i, visit.getOrderId(), visit.getName(),
                visit.getArrivalTime(), visit.getDepartureTime(), visit.getMinutesLate()));
        }
        return new RouteResponse(vehicle.getId(), stops);
    }
}
