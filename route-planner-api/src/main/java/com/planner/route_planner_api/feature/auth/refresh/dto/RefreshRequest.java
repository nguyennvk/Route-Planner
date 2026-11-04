package com.planner.route_planner_api.feature.auth.refresh.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank
    String refreshToken
) {
    @Override
    public String toString() {                  // never print the token
        return "RefreshRequest[refreshToken=***]";
    }
}
