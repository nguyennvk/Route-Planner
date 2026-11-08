package com.planner.route_planner_api.feature.drivers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.planning.dto.RouteResponse;

// Links that open without logging in; SecurityConfig permits GET /api/v1/shared/**.
// The token in the URL is the only permission, so treat it like a password (don't log it)
@RestController
@RequestMapping("/api/v1/shared")
public class SharedRouteController {

    private final DriverService driverService;

    public SharedRouteController(DriverService driverService) {
        this.driverService = driverService;
    }

    @GetMapping("/routes/{token}")
    public RouteResponse getRouteByToken(@PathVariable String token) {
        return driverService.getRouteByToken(token);
    }
}
