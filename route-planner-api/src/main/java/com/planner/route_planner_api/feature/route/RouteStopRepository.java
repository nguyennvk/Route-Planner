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
    // Most recently published plan first. After a release the order also has a dead (skipped) stop on the old
    // plan, but it can only be published again after the release (markPlanned needs pending), so the latest
    // publish is always its current stop. Not planDate or id: a re-plan can be for an earlier day, or a draft
    // solved before the release. Plans published before publishedAt existed have null: put those last
    @Query("""
        select s from RouteStop s
        join fetch s.route r join fetch r.plan p join fetch p.company
        join fetch s.order o join fetch o.address
        where o.id = :orderId and p.status in :statuses
        order by p.publishedAt desc nulls last, p.id desc
        limit 1
        """)
    Optional<RouteStop> findForTracking(Long orderId, Collection<PlanStatus> statuses);

    // Stops planned before this one that aren't finished yet (pending, arrived)
    int countByRouteIdAndSequenceLessThanAndStatusIn(Long routeId, Integer sequence,
                                                  Collection<StopStatus> statuses);

    // The order's live stop on a published plan. Drafts hold stops for the same order too, so a plain findByOrder
    // finds several rows and throws. Filtering by stop status matters as well: a released route leaves a skipped
    // stop on its (still published) plan, and once the order is planned again it has two published stops.
    // Only one of them is open, because release skips the old one in the same transaction
    Optional<RouteStop> findByOrderAndRoute_Plan_StatusAndStatusIn(Order order, PlanStatus planStatus,
                                                                  Collection<StopStatus> stopStatuses);
}
