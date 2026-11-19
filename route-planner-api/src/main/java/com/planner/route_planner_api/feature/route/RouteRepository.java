package com.planner.route_planner_api.feature.route;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.planner.route_planner_api.feature.planning.Plan;
import com.planner.route_planner_api.feature.planning.PlanStatus;
import com.planner.route_planner_api.feature.vehicles.Vehicle;
import com.planner.route_planner_api.feature.vehicles.VehicleShift;

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

    // Another route this driver / vehicle has started and not finished. Plan and vehicle are loaded for the 409 message.
    // The partial unique indexes from V5 guarantee there is at most one.
    @EntityGraph(attributePaths = {"plan", "vehicle"})
    Optional<Route> findFirstByDriverIdAndIdNotAndStartedAtIsNotNullAndFinishedAtIsNull(Long driverId, Long routeId);

    @EntityGraph(attributePaths = {"plan", "vehicle"})
    Optional<Route> findFirstByVehicleIdAndIdNotAndStartedAtIsNotNullAndFinishedAtIsNull(Long vehicleId, Long routeId);

    // How many routes and stops each plan has, for the plan list: one grouped query for a whole page
    // instead of loading every route and stop. A plan with no routes simply has no row
    @Query("""
        select r.plan.id as planId, count(distinct r.id) as routes, count(s.id) as stops
        from Route r left join RouteStop s on s.route = r
        where r.plan.id in :planIds
        group by r.plan.id
        """)
    List<PlanCounts> countByPlanIds(@Param("planIds") Collection<Long> planIds);

    boolean existsByVehicleShift(VehicleShift shift);
    // Spring Data fills this interface from the query's column aliases
    interface PlanCounts {
        Long getPlanId();
        long getRoutes();
        long getStops();
    }
}
