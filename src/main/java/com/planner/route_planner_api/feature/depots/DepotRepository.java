package com.planner.route_planner_api.feature.depots;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DepotRepository extends JpaRepository<Depot, Long> {

    // Only finds the depot if it belongs to this company, so one company can't use another's depot
    Optional<Depot> findByIdAndCompanyId(Long id, Long companyId);

    // join fetch: load each depot's address in the same query, instead of one extra query per depot (N+1).
    // countQuery is needed because Spring can't build the page count from a "join fetch" query.
    @Query(value = """
            select d from Depot d
            join fetch d.address
            where d.company.id = :companyId
            """,
           countQuery = """
            select count(d) from Depot d
            where d.company.id = :companyId
            """)
    Page<Depot> findPageByCompanyId(Long companyId, Pageable pageable);
}
