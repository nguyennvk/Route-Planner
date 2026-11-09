package com.planner.route_planner_api.feature.planning.dto;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import com.planner.route_planner_api.common.time.ZonedTime;
import com.planner.route_planner_api.feature.planning.dto.PlanResponse.StopResponse;
import com.planner.route_planner_api.feature.route.AssignementStatus;
import com.planner.route_planner_api.feature.route.Route;
import com.planner.route_planner_api.feature.route.RouteStop;

public record RouteResponse(
    Long routeId, 
    Long shiftId, 
    Long vehicleId, 
    List<StopResponse> stops, 
    String geometry,
    AssignementStatus assignmentStatus,     // the driver's answer: pending until they accept or reject
    String rejectionReason,
    boolean answerOpen,                     // false once the route started or finished, or the plan is completed
    LocalDateTime startedAt,                // when the driver started / finished, on the company's clock; null until then
    LocalDateTime finishedAt,
    String shareToken                       // the driver's no-login link; only the owner, the driver and the link's holder get routes
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
            route.getGeometry(),
            route.getAssignmentStatus(),
            route.getRejectionReason(),
            route.isAnswerOpen(),
            ZonedTime.toLocalDateTime(route.getStartedAt(), zone),
            ZonedTime.toLocalDateTime(route.getFinishedAt(), zone),
            route.getShareToken()
        );
    }

    
}
