package com.planner.route_planner_api.feature.planning;

import org.springframework.stereotype.Component;

import com.planner.route_planner_api.feature.route.RouteRepository;

// The one rule for when a published plan is over. Both a driver finishing (DriverService) and the owner releasing
// a turned-down route (PlanningService) can be the last thing a plan waits for, so both call this instead of
// keeping their own copy that could drift apart.
// No @Transactional of its own: it runs inside the caller's transaction, so plan.complete() is saved with it
@Component
public class PlanCompletion {

    private final RouteRepository routeRepository;

    public PlanCompletion(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    // Done when every route was driven to the end or released. A route nobody answered, one that's on the road,
    // or one turned down but not released yet (its orders are still planned on it) keeps the plan open
    public void completeIfDone(Plan plan) {
        if (plan.getStatus() != PlanStatus.published) return;
        boolean done = routeRepository.findByPlan(plan).stream()
            .allMatch(route -> route.getFinishedAt() != null || route.getReleasedAt() != null);
        if (done) plan.complete();
    }
}
