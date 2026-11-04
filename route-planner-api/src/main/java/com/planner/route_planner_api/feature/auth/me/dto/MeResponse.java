package com.planner.route_planner_api.feature.auth.me.dto;

import java.time.Instant;

import com.planner.route_planner_api.feature.auth.User;

public record MeResponse(Long id, String email, String fullName, String phone, Instant createdAt) {
    public static MeResponse from(User user) {
        return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), user.getCreatedAt());
    }
}
