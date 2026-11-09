package com.planner.route_planner_api.feature.drivers.dto;

import jakarta.validation.constraints.Size;

// The reason is optional, but capped so a share link can't be used to store a novel
public record DriverRejectRequest(
    @Size(max = 500) String reason
) {

}
