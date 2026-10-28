package com.planner.route_planner_api.feature.companies;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

// The ID type is CompanyMemberId, the two-column key, not Long
public interface CompanyMemberRepository extends JpaRepository<CompanyMember, CompanyMemberId> {

    List<CompanyMember> findByUserIdAndActiveTrue(Long userId);           // "which companies am I in?"

    List<CompanyMember> findByCompanyIdAndRole(Long companyId, MemberRole role);  // "list this company's drivers"

    boolean existsByCompanyIdAndUserIdAndRole(Long companyId, Long userId, MemberRole role);  // "is this user an owner?"

    // The user's membership in one company, with the company loaded in the same query
    @Query("""
            select m from CompanyMember m
            join fetch m.company
            where m.company.id = :companyId
              and m.user.id = :userId
              and m.active = true
            """)
    Optional<CompanyMember> findActiveMembership(Long companyId, Long userId);
}
