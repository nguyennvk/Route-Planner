package com.planner.route_planner_api.feature.addresses;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// JpaSpecificationExecutor adds findAll(Specification, Pageable): a query built from
// only the filters the client sent (see AddressService.listAddresses)
public interface AddressRepository extends JpaRepository<Address, Long>, JpaSpecificationExecutor<Address> {

    // Only finds the address if it belongs to this company, so one company can't deliver to another's address
    Optional<Address> findByIdAndCompanyId(Long id, Long companyId);
}
