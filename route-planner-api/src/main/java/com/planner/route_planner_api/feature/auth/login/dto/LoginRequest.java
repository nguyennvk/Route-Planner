package com.planner.route_planner_api.feature.auth.login.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank 
    String email, 
    @NotBlank 
    String password
) {
    @Override 
    public String toString(){
        return "LoginRequest[email=" + email + ", password=***]";
    }
}

