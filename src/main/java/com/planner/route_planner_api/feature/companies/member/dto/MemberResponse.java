package com.planner.route_planner_api.feature.companies.member.dto;

import java.time.Instant;

import com.planner.route_planner_api.feature.auth.User;
import com.planner.route_planner_api.feature.companies.CompanyMember;
import com.planner.route_planner_api.feature.companies.MemberRole;

public record MemberResponse(
    Long userId,
    String fullName,
    String email,
    MemberRole role,
    boolean active,
    Instant joinedAt
) {

    public static MemberResponse from(CompanyMember companyMember){
        User user = companyMember.getUser();
        return new MemberResponse(user.getId(), user.getFullName(), user.getEmail(), companyMember.getRole(), companyMember.isActive(), companyMember.getJoinedAt());
    }

}
