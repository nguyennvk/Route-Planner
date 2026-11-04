package com.planner.route_planner_api.feature.customers;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// JpaSpecificationExecutor adds findAll(Specification, Pageable): a query built from
// only the filters the client sent (see CustomerService.listCustomers)
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    // Only finds the customer if it belongs to this company
    Optional<Customer> findByIdAndCompanyId(Long id, Long companyId);

    // email is citext, so "A@x.com" and "a@x.com" count as the same email here, like in the unique index
    boolean existsByCompanyIdAndEmail(Long companyId, String email);

    // Same, ignoring one customer (the one being updated)
    boolean existsByCompanyIdAndEmailAndIdNot(Long companyId, String email, Long id);
}
