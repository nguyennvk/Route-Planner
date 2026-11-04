package com.planner.route_planner_api.feature.companies.member.dto;

import com.planner.route_planner_api.feature.companies.MemberRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MemberRequest(
    @NotBlank @Email @Size(max=254)
    String email,

    @NotNull  
    MemberRole role
) {
    @Override
    public String toString(){
        return "MemberRequest[email="+email+", role="+role+"]";
    }
}
