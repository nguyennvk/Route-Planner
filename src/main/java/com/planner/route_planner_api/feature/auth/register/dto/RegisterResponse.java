package com.planner.route_planner_api.feature.auth.register.dto;

import java.time.Instant;

import com.planner.route_planner_api.feature.auth.User;

public record RegisterResponse(Long id, String email, String fullName, Instant createdAt) {
    public static RegisterResponse from(User user) {
        return new RegisterResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt());
    }

}
