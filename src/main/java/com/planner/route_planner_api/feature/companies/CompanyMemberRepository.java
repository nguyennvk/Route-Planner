package com.planner.route_planner_api.feature.companies;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

// The ID type is CompanyMemberId, the two-column key, not Long
public interface CompanyMemberRepository extends JpaRepository<CompanyMember, CompanyMemberId> {

    List<CompanyMember> findByUserIdAndActiveTrue(Long userId);           // "which companies am I in?"

    List<CompanyMember> findByCompanyIdAndRole(Long companyId, MemberRole role);  // "list this company's drivers"

    boolean existsByCompanyIdAndUserIdAndRole(Long companyId, Long userId, MemberRole role);  // "is this user an owner?"
}
