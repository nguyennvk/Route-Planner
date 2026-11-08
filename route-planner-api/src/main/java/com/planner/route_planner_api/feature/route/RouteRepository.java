package com.planner.route_planner_api.feature.route;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import com.planner.route_planner_api.feature.planning.Plan;


public interface RouteRepository extends JpaRepository<Route, Long> {
    List<Route> findByPlan(Plan plan);
}
