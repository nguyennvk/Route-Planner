package com.planner.route_planner_api.feature.vehicles.vehicle_shift.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public record VehicleShiftRequest(
    @NotNull
    Long vehicleId,

    // ISO-8601 with a zone, e.g. "2026-10-26T00:30:00Z"
    @NotNull
    Instant startAt,

    @NotNull
    Instant endAt,

    // Optional: a shift can be planned before a driver is assigned
    Long driverUserId
) {
    @Override
    public String toString(){
        return "VehicleShiftRequest[vehicleId=" + vehicleId + ", startAt=" + startAt
            + ", endAt=" + endAt + ", driverUserId=" + driverUserId + "]";
    }
}
