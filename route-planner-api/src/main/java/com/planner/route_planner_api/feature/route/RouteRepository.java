package com.planner.route_planner_api.feature.route;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.planner.route_planner_api.feature.planning.Plan;
import com.planner.route_planner_api.feature.planning.PlanStatus;

// The driver queries load plan + company in the same SELECT: the response needs the company's timezone,
// and loading it lazily would be one extra query per route
public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByPlan(Plan plan);

    // driver.id and plan.status: Spring Data follows the relations from the method name
    @EntityGraph(attributePaths = {"plan", "plan.company"})
    List<Route> findByDriverIdAndPlanStatusInOrderByPlannedStartAsc(Long driverId, Collection<PlanStatus> statuses);

    @EntityGraph(attributePaths = {"plan", "plan.company"})
    Optional<Route> findByIdAndDriverId(Long id, Long driverId);

    @EntityGraph(attributePaths = {"plan", "plan.company"})
    Optional<Route> findByShareToken(String shareToken);
}
