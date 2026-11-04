package com.planner.route_planner_api.feature.companies;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


public interface CompanyRepository extends JpaRepository<Company, Long> {
    @Query("""
            select m.company from CompanyMember m
            where m.company.id = :companyId
              and m.user.id = :userId
              and m.active = true
            """)
    Optional<Company> findByIdForMember(Long companyId, Long userId);
    
    @Query("""
            select m.company from CompanyMember m
            where m.user.id = :userId
            and m.active = true
            """)
    List<Company> findAllByUserId(Long userId);
}
