package com.planner.route_planner_api.feature.drivers.dto;

import java.math.BigDecimal;
import java.time.LocalTime;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record PositionRequest(
    @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude,
    @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
    BigDecimal speedKmh
) {}
