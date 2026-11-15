package com.planner.route_planner_api.feature.planning.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.planner.route_planner_api.common.time.ZonedTime;
import com.planner.route_planner_api.feature.planning.Plan;
import com.planner.route_planner_api.feature.planning.PlanStatus;

// One line of the plan list: enough to choose a plan without loading its routes. GET /plans/{id} has the rest.
// Times are on the company's wall clock, like every other plan time
public record PlanSummaryResponse(
    Long planId,
    LocalDate planDate,
    PlanStatus status,
    String score,                   // e.g. "0hard/0medium/-1037soft"; null until solved
    String createdByName,
    LocalDateTime createdAt,
    LocalDateTime solvedAt,
    LocalDateTime publishedAt,
    long routeCount,
    long stopCount
) {
    public static PlanSummaryResponse from(Plan plan, ZoneId zone, long routeCount, long stopCount) {
        return new PlanSummaryResponse(
            plan.getId(),
            plan.getPlanDate(),
            plan.getStatus(),
            PlanResponse.scoreOf(plan),
            plan.getCreatedBy().getFullName(),
            ZonedTime.toLocalDateTime(plan.getCreatedAt(), zone),
            ZonedTime.toLocalDateTime(plan.getSolvedAt(), zone),
            ZonedTime.toLocalDateTime(plan.getPublishedAt(), zone),
            routeCount,
            stopCount
        );
    }
}
