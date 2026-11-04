package com.planner.route_planner_api.feature.vehicles;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// JpaSpecificationExecutor adds findAll(Specification, Pageable): a query built from
// only the filters the client sent (see VehicleService.listVehicles)
public interface VehicleRepository extends JpaRepository<Vehicle, Long>, JpaSpecificationExecutor<Vehicle> {

    // Only finds the vehicle if it belongs to this company
    Optional<Vehicle> findByIdAndCompanyId(Long id, Long companyId);
}
