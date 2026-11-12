package com.planner.route_planner_api.feature.vehicles;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VehicleLastPositionRepository extends JpaRepository<VehicleLastPosition, Long>{

    // One query for the planner's map: vans on this plan's routes that are being driven now.
    // A row points at the route it was last sent for, so a van that has moved on to another plan drops out.
    @Query("""
        select p from VehicleLastPosition p
        where p.route.plan.id = :planId
          and p.route.startedAt is not null
          and p.route.finishedAt is null
        """)
    List<VehicleLastPosition> findActiveByPlanId(Long planId);
}
