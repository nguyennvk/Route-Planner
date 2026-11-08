package com.planner.route_planner_api.feature.route;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

    // Joins order and address in the same SELECT; without it, reading them is 2 extra queries per stop (N+1)
    @EntityGraph(attributePaths = {"order", "order.address"})
    List<RouteStop> findByRouteIn(List<Route> routes);
}
