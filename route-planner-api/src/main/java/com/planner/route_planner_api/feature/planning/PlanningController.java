package com.planner.route_planner_api.feature.planning;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.planner.route_planner_api.feature.planning.dto.PlanPublishRequest;
import com.planner.route_planner_api.feature.planning.dto.PlanRequest;
import com.planner.route_planner_api.common.web.PageResponse;
import com.planner.route_planner_api.feature.planning.dto.PlanResponse;
import com.planner.route_planner_api.feature.planning.dto.PlanSummaryResponse;
import com.planner.route_planner_api.feature.vehicles.vehicle.dto.VehiclePositionResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

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

    // The plan history, newest first. Optional filters: ?date= for one day, ?from= / ?to= for a range of days
    // (inclusive), ?status=published
    @GetMapping("/companies/{companyId}/plans")
    public PageResponse<PlanSummaryResponse> listPlans(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long companyId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) PlanStatus status,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return planningService.listPlans(userId, companyId, date, from, to, status, page, size);
    }

    @GetMapping("/plans/{planId}")
    public PlanResponse getPlan(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long planId
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return planningService.getPlan(userId, planId);
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
