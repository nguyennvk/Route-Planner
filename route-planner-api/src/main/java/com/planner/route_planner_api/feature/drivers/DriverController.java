package com.planner.route_planner_api.feature.drivers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.drivers.dto.DriverRejectRequest;
import com.planner.route_planner_api.feature.drivers.dto.PositionRequest;
import com.planner.route_planner_api.feature.drivers.dto.StopFailRequest;
import com.planner.route_planner_api.feature.planning.dto.RouteResponse;

import jakarta.validation.Valid;

// The logged-in driver's own routes. The no-login share link lives in SharedRouteController
@RestController
@RequestMapping("/api/v1/drivers/me/routes")
public class DriverController {

    private final DriverService driverService;

    public DriverController(DriverService driverService) {
        this.driverService = driverService;
    }

    // No path: "/" would only match a trailing slash since Spring 6
    @GetMapping
    public List<RouteResponse> getRoutes(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return driverService.getRoutes(userId);
    }

    @GetMapping("/{routeId}")
    public RouteResponse getRouteById(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long routeId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return driverService.getRouteById(userId, routeId);
    }

    // Returns the route so the app shows the new status without fetching it again
    @PostMapping("/{routeId}/accept")
    public RouteResponse acceptRoute(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long routeId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return driverService.acceptRoute(userId, routeId);
    }

    @PostMapping("/{routeId}/reject")
    public RouteResponse rejectRoute(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long routeId,
        @Valid @RequestBody DriverRejectRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return driverService.rejectRoute(userId, routeId, request);
    }

    @PostMapping("/{routeId}/start")
    public RouteResponse startRoute(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long routeId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return driverService.startRoute(userId, routeId);
    }

    @PostMapping("/{routeId}/finish")
    public RouteResponse finishRoute(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long routeId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return driverService.finishRoute(userId, routeId);
    }

    // The driver's answer for one stop while the route is in progress
    @PostMapping("/{routeId}/stops/{stopId}/arrive")
    public RouteResponse arriveStop(@AuthenticationPrincipal Jwt jwt, @PathVariable Long routeId, @PathVariable Long stopId) {
        return driverService.arriveStop(Long.valueOf(jwt.getSubject()), routeId, stopId);
    }

    @PostMapping("/{routeId}/stops/{stopId}/deliver")
    public RouteResponse deliverStop(@AuthenticationPrincipal Jwt jwt, @PathVariable Long routeId, @PathVariable Long stopId) {
        return driverService.deliverStop(Long.valueOf(jwt.getSubject()), routeId, stopId);
    }

    @PostMapping("/{routeId}/stops/{stopId}/fail")
    public RouteResponse failStop(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long routeId,
        @PathVariable Long stopId,
        @Valid @RequestBody StopFailRequest request
    ) {
        return driverService.failStop(Long.valueOf(jwt.getSubject()), routeId, stopId, request);
    }

    @PostMapping("/{routeId}/position")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void postDriverPosition(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long routeId,
        @Valid @RequestBody PositionRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());       
        driverService.postDriverPosition(userId, routeId, request);
    }
    
}
