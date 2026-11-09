package com.planner.route_planner_api.feature.drivers;

import com.planner.route_planner_api.feature.vehicles.VehicleLastPosition;
import com.planner.route_planner_api.feature.vehicles.VehicleLastPositionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.drivers.dto.DriverRejectRequest;
import com.planner.route_planner_api.feature.drivers.dto.PositionRequest;
import com.planner.route_planner_api.feature.orders.Order;
import com.planner.route_planner_api.feature.orders.OrderStatus;
import com.planner.route_planner_api.feature.planning.PlanStatus;
import com.planner.route_planner_api.feature.planning.dto.RouteResponse;
import com.planner.route_planner_api.feature.route.Route;
import com.planner.route_planner_api.feature.route.RouteRepository;
import com.planner.route_planner_api.feature.route.RouteStop;
import com.planner.route_planner_api.feature.route.RouteStopRepository;

// Read-only by default: the transaction keeps lazy relations loadable even with open-in-view off,
// and readOnly lets Hibernate skip dirty checking at the end. Methods that change a route override it
@Service
@Transactional(readOnly = true)
public class DriverService {

    // Drafts are still being edited by the dispatcher; drivers only see plans that were sent out
    private static final List<PlanStatus> VISIBLE_TO_DRIVERS = List.of(PlanStatus.published, PlanStatus.completed);

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final VehicleLastPositionRepository vehicleLastPositionRepository;
    private final CompanyAccess companyAccess;

    public DriverService(
        RouteRepository routeRepository,
        RouteStopRepository routeStopRepository,
        VehicleLastPositionRepository vehicleLastPositionRepository,
        CompanyAccess companyAccess
    ) {
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
        this.vehicleLastPositionRepository = vehicleLastPositionRepository;
        this.companyAccess = companyAccess;
    }

    // The logged-in driver's routes, earliest first
    public List<RouteResponse> getRoutes(Long userId) {
        // Membership checked once per company, not once per route (a driver usually has one company)
        Map<Long, Boolean> activeIn = new HashMap<>();
        List<Route> routes = routeRepository
            .findByDriverIdAndPlanStatusInOrderByPlannedStartAsc(userId, VISIBLE_TO_DRIVERS).stream()
            .filter(route -> activeIn.computeIfAbsent(route.getPlan().getCompany().getId(),
                companyId -> companyAccess.findActiveMember(companyId, userId).isPresent()))
            .toList();

        // All stops of all routes in one query, then grouped, instead of one query per route
        Map<Route, List<RouteStop>> stopsByRoute = routeStopRepository.findByRouteIn(routes).stream()
            .collect(Collectors.groupingBy(RouteStop::getRoute));

        return routes.stream()
            .map(route -> toResponse(route, stopsByRoute.getOrDefault(route, List.of())))
            .toList();
    }

    public RouteResponse getRouteById(Long userId, Long routeId) {
        return toResponse(findOwnRoute(userId, routeId));
    }

    // Public link, no login: the token itself is the permission
    public RouteResponse getRouteByToken(String token) {
        return toResponse(findSharedRoute(token));
    }

    // The class default is readOnly, where Hibernate never flushes: without this the change would stay in memory.
    // The @Version column turns two answers racing each other into a 409 instead of the last one winning
    @Transactional
    public RouteResponse acceptRoute(Long userId, Long routeId) {
        Route route = findOwnRoute(userId, routeId);
        route.accept();
        return toResponse(route);
    }

    @Transactional
    public RouteResponse rejectRoute(Long userId, Long routeId, DriverRejectRequest request) {
        Route route = findOwnRoute(userId, routeId);
        route.reject(request.reason());
        return toResponse(route);
    }

    // The share link answers for the route's driver, the same way the logged-in endpoints do
    @Transactional
    public RouteResponse acceptRouteByToken(String token) {
        Route route = findSharedRoute(token);
        route.accept();
        return toResponse(route);
    }

    @Transactional
    public RouteResponse rejectRouteByToken(String token, DriverRejectRequest request) {
        Route route = findSharedRoute(token);
        route.reject(request.reason());
        return toResponse(route);
    }

    @Transactional
    public RouteResponse startRoute(Long userId, Long routeId) {
        return start(findOwnRoute(userId, routeId));
    }

    @Transactional
    public RouteResponse startRouteByToken(String token) {
        return start(findSharedRoute(token));
    }

    @Transactional
    public RouteResponse finishRoute(Long userId, Long routeId) {
        return finish(findOwnRoute(userId, routeId));
    }

    @Transactional
    public RouteResponse finishRouteByToken(String token) {
        return finish(findSharedRoute(token));
    }

    @Transactional
    public void postDriverPosition(Long userId, Long routeId, PositionRequest request) {
        Route route = findOwnRoute(userId, routeId);
        route.requireInProgress();
        vehicleLastPositionRepository.findById(route.getVehicle().getId())
            .ifPresentOrElse(
                last -> last.moveTo(route, request.latitude(), request.longitude(), request.speedKmh()),
                () -> vehicleLastPositionRepository.save(new VehicleLastPosition(route, request.latitude(), request.longitude(), request.speedKmh())));
    }

    // Orders cancelled after publishing stay cancelled; the rest are now on the road
    private RouteResponse start(Route route) {
        List<RouteStop> stops = routeStopRepository.findByRoute(route);
        route.start();
        stops.stream()
            .map(RouteStop::getOrder)
            .filter(order -> order.getStatus() == OrderStatus.planned)
            .forEach(Order::markOutForDelivery);
        return toResponse(route, stops);
    }

    // Orders stay out_for_delivery: whether each one was delivered is a per-stop answer (RouteStop.deliver / fail)
    private RouteResponse finish(Route route) {
        route.finish();
        return toResponse(route);
    }

    // Someone else's route, an unpublished one, or a company the driver left all look like "not found",
    // so a driver can't probe which route ids exist
    private Route findOwnRoute(Long userId, Long routeId) {
        return routeRepository.findByIdAndDriverId(routeId, userId)
            .filter(this::isVisible)
            .filter(r -> isActiveMember(r, userId))
            .orElseThrow(() -> new ResourceNotFoundException("Route", routeId));
    }

    private Route findSharedRoute(String token) {
        return routeRepository.findByShareToken(token)
            .filter(this::isVisible)
            .orElseThrow(() -> new ResourceNotFoundException("Route", "token"));   // never echo the token
    }

    private boolean isVisible(Route route) {
        return VISIBLE_TO_DRIVERS.contains(route.getPlan().getStatus());
    }

    // A driver removed from the company keeps driver_user_id on old routes; they shouldn't see them anymore
    private boolean isActiveMember(Route route, Long userId) {
        return companyAccess.findActiveMember(route.getPlan().getCompany().getId(), userId).isPresent();
    }

    private RouteResponse toResponse(Route route) {
        return toResponse(route, routeStopRepository.findByRoute(route));
    }

    private static RouteResponse toResponse(Route route, List<RouteStop> stops) {
        return RouteResponse.toRoute(route, stops, route.getPlan().getCompany().zoneId());
    }
}
