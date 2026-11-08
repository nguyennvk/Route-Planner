package com.planner.route_planner_api.feature.planning.dto;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import com.planner.route_planner_api.feature.planning.dto.PlanResponse.StopResponse;
import com.planner.route_planner_api.feature.route.Route;
import com.planner.route_planner_api.feature.route.RouteStop;

public record RouteResponse(
    Long routeId, 
    Long shiftId, 
    Long vehicleId, 
    List<StopResponse> stops, 
    String geometry
) {
    public static RouteResponse toRoute(Route route, List<RouteStop> stops, ZoneId zone) {
        List<StopResponse> stopResponses = stops.stream()
            .sorted(Comparator.comparing(RouteStop::getSequence))
            .map(stop -> PlanResponse.toStop(stop, zone))
            .toList();
        return new RouteResponse(
            route.getId(),
            route.getVehicleShift().getId(),    // getId() on a lazy proxy doesn't hit the database
            route.getVehicle().getId(),
            stopResponses,
            route.getGeometry()
        );
    }

    
}
