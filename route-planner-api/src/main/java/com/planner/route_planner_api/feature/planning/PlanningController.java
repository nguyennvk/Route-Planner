package com.planner.route_planner_api.feature.planning;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.planning.dto.PlanPublishRequest;
import com.planner.route_planner_api.feature.planning.dto.PlanRequest;
import com.planner.route_planner_api.feature.planning.dto.PlanResponse;
import com.planner.route_planner_api.feature.vehicles.vehicle.dto.VehiclePositionResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class PlanningController {

    private final PlanningService planningService;

    // Spring passes in the PlanningService bean (which already has the SolverManager)
    public PlanningController(PlanningService planningService) {
        this.planningService = planningService;
    }


    @PostMapping("/companies/{companyId}/plans")
    public PlanResponse solve(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @Valid @RequestBody PlanRequest request
    ){
        Long userId = Long.valueOf(jwt.getSubject());
        return planningService.solve(userId, companyId, request);
    }

    // Where each van on a route in progress was last seen; empty when none has sent a position yet
    @GetMapping("/plans/{planId}/positions")
    public List<VehiclePositionResponse> activePositions(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long planId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return planningService.activePositions(userId, planId);
    }

    @PostMapping("/plans/{planId}/publish")
    public PlanResponse publish(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long planId,
        @RequestBody PlanPublishRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return planningService.publishPlan(userId, planId, request);
    }
    
}
