package com.planner.route_planner_api.feature.planning;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// JpaSpecificationExecutor adds findAll(Specification, Pageable): the plan list is a query built from
// only the filters the client sent (see PlanningService.listPlans)
public interface PlanRepository extends JpaRepository<Plan, Long>, JpaSpecificationExecutor<Plan> {
}
