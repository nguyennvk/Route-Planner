package com.planner.route_planner_api.feature.drivers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.drivers.dto.DriverRejectRequest;
import com.planner.route_planner_api.feature.planning.dto.RouteResponse;

import jakarta.validation.Valid;

// Links that open without logging in; SecurityConfig permits GET /api/v1/shared/** and the four driver actions below.
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

    @PostMapping("/routes/{token}/accept")
    public RouteResponse acceptRouteByToken(@PathVariable String token) {
        return driverService.acceptRouteByToken(token);
    }

    @PostMapping("/routes/{token}/reject")
    public RouteResponse rejectRouteByToken(
        @PathVariable String token,
        @Valid @RequestBody DriverRejectRequest request
    ) {
        return driverService.rejectRouteByToken(token, request);
    }

    @PostMapping("/routes/{token}/start")
    public RouteResponse startRouteByToken(@PathVariable String token) {
        return driverService.startRouteByToken(token);
    }

    @PostMapping("/routes/{token}/finish")
    public RouteResponse finishRouteByToken(@PathVariable String token) {
        return driverService.finishRouteByToken(token);
    }
}
