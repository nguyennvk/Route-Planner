package com.planner.route_planner_api.feature.drivers.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Why a stop couldn't be delivered. Required: the owner needs it to arrange another try
public record StopFailRequest(
    @NotBlank @Size(max = 500) String reason
) {}
