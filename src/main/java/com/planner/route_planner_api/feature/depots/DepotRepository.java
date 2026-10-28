package com.planner.route_planner_api.feature.depots;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepotRepository extends JpaRepository<Depot, Long> {

    // Only finds the depot if it belongs to this company, so one company can't use another's depot
    Optional<Depot> findByIdAndCompanyId(Long id, Long companyId);
}
