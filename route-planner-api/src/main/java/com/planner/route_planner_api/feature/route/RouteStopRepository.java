package com.planner.route_planner_api.feature.route;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.planner.route_planner_api.feature.orders.Order;
import com.planner.route_planner_api.feature.planning.PlanStatus;

public interface RouteStopRepository extends JpaRepository<RouteStop, Long> {

    // Joins order and address in the same SELECT; without it, reading them is 2 extra queries per stop (N+1)
    @EntityGraph(attributePaths = {"order", "order.address"})
    List<RouteStop> findByRouteIn(List<Route> routes);

    @EntityGraph(attributePaths = {"order", "order.address"})
    List<RouteStop> findByRoute(Route route);
    // The order's stop on the plan sent to drivers. Every solve writes stops, so an order also has rows on
    // draft plans, all 'pending': only the plan's status tells the real one apart. Newest plan first.
    // Fetches what TrackingResponse.from reads, so building the response needs no extra queries.
    @Query("""
        select s from RouteStop s
        join fetch s.route r join fetch r.plan p join fetch p.company
        join fetch s.order o join fetch o.address
        where o.id = :orderId and p.status in :statuses
        order by p.planDate desc, p.id desc
        """)
    List<RouteStop> findForTracking(Long orderId, Collection<PlanStatus> statuses);

    // Stops planned before this one that aren't finished yet (pending, arrived)
    int countByRouteIdAndSequenceLessThanAndStatusIn(Long routeId, Integer sequence,
                                                  Collection<StopStatus> statuses);
    Optional<RouteStop> findByOrder(Order order);
}
