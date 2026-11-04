package com.planner.route_planner_api.feature.auth.logout.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
    @NotBlank 
    String refreshToken
) {
    @Override
    public String toString() {
        return "LogoutRequest[refreshToken=***]";
    }
    
}
