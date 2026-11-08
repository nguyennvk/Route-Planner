package com.planner.route_planner_api.feature.planning;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.planning.dto.PlanPublishRequest;
import com.planner.route_planner_api.feature.planning.dto.PlanRequest;
import com.planner.route_planner_api.feature.planning.dto.PlanResponse;

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
