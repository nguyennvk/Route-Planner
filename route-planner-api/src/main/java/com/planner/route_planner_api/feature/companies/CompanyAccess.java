package com.planner.route_planner_api.feature.companies;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.planner.route_planner_api.common.error.ForbiddenException;
import com.planner.route_planner_api.common.error.ResourceNotFoundException;

// Membership and role checks for features that live under a company (vehicles, shifts, ...),
// so each feature doesn't repeat them.
@Service
public class CompanyAccess {

    private final CompanyMemberRepository companyMemberRepository;

    public CompanyAccess(CompanyMemberRepository companyMemberRepository) {
        this.companyMemberRepository = companyMemberRepository;
    }

    // The user's active membership in the company, with the company loaded
    public Optional<CompanyMember> findActiveMember(Long companyId, Long userId) {
        return companyMemberRepository.findActiveMembership(companyId, userId);
    }

    // Not an active member -> 404, so outsiders can't tell which company ids exist
    public CompanyMember requireMember(Long companyId, Long userId) {
        return findActiveMember(companyId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Company", companyId));
    }

    // Owner-only actions -> 403 for other members
    public void requireOwner(CompanyMember membership) {
        if (membership.getRole() != MemberRole.owner) {
            throw new ForbiddenException("Only the owner can do this");
        }
    }
}
