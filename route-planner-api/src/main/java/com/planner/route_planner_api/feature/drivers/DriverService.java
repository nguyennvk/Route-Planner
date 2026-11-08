package com.planner.route_planner_api.feature.drivers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.planner.route_planner_api.common.error.ResourceNotFoundException;
import com.planner.route_planner_api.feature.companies.CompanyAccess;
import com.planner.route_planner_api.feature.planning.PlanStatus;
import com.planner.route_planner_api.feature.planning.dto.RouteResponse;
import com.planner.route_planner_api.feature.route.Route;
import com.planner.route_planner_api.feature.route.RouteRepository;
import com.planner.route_planner_api.feature.route.RouteStop;
import com.planner.route_planner_api.feature.route.RouteStopRepository;

// Read-only: the transaction keeps lazy relations loadable even with open-in-view off,
// and readOnly lets Hibernate skip dirty checking at the end
@Service
@Transactional(readOnly = true)
public class DriverService {

    // Drafts are still being edited by the dispatcher; drivers only see plans that were sent out
    private static final List<PlanStatus> VISIBLE_TO_DRIVERS = List.of(PlanStatus.published, PlanStatus.completed);

    private final RouteRepository routeRepository;
    private final RouteStopRepository routeStopRepository;
    private final CompanyAccess companyAccess;

    public DriverService(
        RouteRepository routeRepository,
        RouteStopRepository routeStopRepository,
        CompanyAccess companyAccess
    ) {
        this.routeRepository = routeRepository;
        this.routeStopRepository = routeStopRepository;
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

    // Someone else's route, an unpublished one, or a company the driver left all look like "not found",
    // so a driver can't probe which route ids exist
    public RouteResponse getRouteById(Long userId, Long routeId) {
        Route route = routeRepository.findByIdAndDriverId(routeId, userId)
            .filter(this::isVisible)
            .filter(r -> isActiveMember(r, userId))
            .orElseThrow(() -> new ResourceNotFoundException("Route", routeId));
        return toResponse(route, routeStopRepository.findByRoute(route));
    }

    // Public link, no login: the token itself is the permission
    public RouteResponse getRouteByToken(String token) {
        Route route = routeRepository.findByShareToken(token)
            .filter(this::isVisible)
            .orElseThrow(() -> new ResourceNotFoundException("Route", "token"));   // never echo the token
        return toResponse(route, routeStopRepository.findByRoute(route));
    }

    private boolean isVisible(Route route) {
        return VISIBLE_TO_DRIVERS.contains(route.getPlan().getStatus());
    }

    // A driver removed from the company keeps driver_user_id on old routes; they shouldn't see them anymore
    private boolean isActiveMember(Route route, Long userId) {
        return companyAccess.findActiveMember(route.getPlan().getCompany().getId(), userId).isPresent();
    }

    private static RouteResponse toResponse(Route route, List<RouteStop> stops) {
        return RouteResponse.toRoute(route, stops, route.getPlan().getCompany().zoneId());
    }
}
