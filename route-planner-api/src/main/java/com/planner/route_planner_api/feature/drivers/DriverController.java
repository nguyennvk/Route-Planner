package com.planner.route_planner_api.feature.drivers;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.planning.dto.RouteResponse;

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
}
