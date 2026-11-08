package com.planner.route_planner_api.feature.planning.dto;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.planner.route_planner_api.common.time.ZonedTime;
import com.planner.route_planner_api.feature.orders.Order;
import com.planner.route_planner_api.feature.planning.Plan;
import com.planner.route_planner_api.feature.planning.PlanStatus;
import com.planner.route_planner_api.feature.route.Route;
import com.planner.route_planner_api.feature.route.RouteStop;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;

// What the API returns for a plan, built from the saved rows so solve, publish and GET all answer the same way.
// Times are stored as Instant and shown in the company's wall-clock time
public record PlanResponse(
    Long planId,
    String name,
    PlanStatus status,
    LocalDate planDate,
    String score,                   // e.g. "0hard/0medium/-1037soft"; null until solved
    List<RouteResponse> routes,
    List<Long> unassignedOrderIds   // orders that didn't fit on any route; only known right after solving
) {

    public record RouteResponse(Long routeId, Long shiftId, Long vehicleId, List<StopResponse> stops, String geometry) {}

    public record StopResponse(
        Long stopId,
        int sequence,               // 0 = first stop after the depot
        Long orderId,
        String name,
        LocalDateTime arrivalTime,
        LocalDateTime departureTime,
        long minutesLate
    ) {}

    public static PlanResponse from(Plan plan, List<Route> routes, List<RouteStop> stops, List<Long> unassignedOrderIds) {
        ZoneId zone = plan.getCompany().zoneId();

        // One pass to group, instead of filtering all stops once per route
        Map<Route, List<RouteStop>> stopsByRoute = stops.stream()
            .collect(Collectors.groupingBy(RouteStop::getRoute));

        List<RouteResponse> routeResponses = routes.stream()
            .map(route -> toRoute(route, stopsByRoute.getOrDefault(route, List.of()), zone))
            .toList();

        return new PlanResponse(
            plan.getId(),
            "Route plan for " + plan.getPlanDate(),
            plan.getStatus(),
            plan.getPlanDate(),
            scoreOf(plan),
            routeResponses,
            unassignedOrderIds
        );
    }

    private static RouteResponse toRoute(Route route, List<RouteStop> stops, ZoneId zone) {
        List<StopResponse> stopResponses = stops.stream()
            .sorted(Comparator.comparing(RouteStop::getSequence))
            .map(stop -> toStop(stop, zone))
            .toList();
        return new RouteResponse(
            route.getId(),
            route.getVehicleShift().getId(),    // getId() on a lazy proxy doesn't hit the database
            route.getVehicle().getId(),
            stopResponses,
            route.getGeometry()
        );
    }

    private static StopResponse toStop(RouteStop stop, ZoneId zone) {
        Order order = stop.getOrder();
        return new StopResponse(
            stop.getId(),
            stop.getSequence(),
            order.getId(),
            order.getAddress().getRawAddress(),
            ZonedTime.toLocalDateTime(stop.getPlannedArrival(), zone),
            ZonedTime.toLocalDateTime(stop.getPlannedDeparture(), zone),
            minutesLate(stop.getPlannedDeparture(), order.getWindowEnd())
        );
    }

    // Same rule as Visit.getMinutesLate: service must finish by the window end; partial minutes round up
    private static long minutesLate(Instant departure, Instant windowEnd) {
        if (departure == null || windowEnd == null || !departure.isAfter(windowEnd)) {
            return 0;
        }
        long seconds = Duration.between(windowEnd, departure).toSeconds();
        return (seconds + 59) / 60;
    }

    private static String scoreOf(Plan plan) {
        if (plan.getHardScore() == null) {
            return null;
        }
        return HardMediumSoftScore.of(plan.getHardScore(), plan.getMediumScore(), plan.getSoftScore()).toString();
    }
}
