package com.planner.route_planner_api.feature.companies.member.dto;

import com.planner.route_planner_api.feature.companies.MemberRole;

// PATCH: every field is optional, null means "leave unchanged".
// No annotations needed: an unknown role like "boss" is already rejected with 400 when the JSON is read.
public record UpdateMemberRequest(
    MemberRole role,
    Boolean active          // Boolean, not boolean: a primitive can't be null, so "not sent" would become false
) {
    @Override
    public String toString(){
        return "UpdateMemberRequest[role=" + role + ", active=" + active + "]";
    }
}
